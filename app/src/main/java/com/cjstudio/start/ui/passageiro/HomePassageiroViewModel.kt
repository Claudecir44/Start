package com.cjstudio.start.ui.passageiro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.data.model.EstimativaCorrida
import com.cjstudio.start.data.model.Local
import com.cjstudio.start.data.model.Passageiro
import com.cjstudio.start.data.repository.IAuthRepository
import com.cjstudio.start.data.repository.ICorridaRepository
import com.cjstudio.start.data.repository.IFalhasRepository
import com.cjstudio.start.data.repository.ILocalizacaoRepository
import com.cjstudio.start.data.repository.INotificacaoRepository
import com.cjstudio.start.data.repository.IPassageiroRepository
import com.cjstudio.start.ui.common.UiTexto
import com.cjstudio.start.ui.common.paraUiTexto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Tela inicial do passageiro: mapa, "Para onde vamos?", estimativa e pedido.
// A corrida em si (depois de pedida) fica na MESMA tela, com CorridaViewModel
// — a tela passa a ele o corridaAtualId deste perfil.
@HiltViewModel
class HomePassageiroViewModel @Inject constructor(
    private val auth: IAuthRepository,
    private val passageiros: IPassageiroRepository,
    private val corridas: ICorridaRepository,
    private val localizacao: ILocalizacaoRepository,
    private val notificacoes: INotificacaoRepository,
    private val falhas: IFalhasRepository
) : ViewModel() {

    sealed interface Painel {
        data object Inicial : Painel
        data object Estimando : Painel
        data class Estimativa(val partida: Local, val destino: Local, val estimativa: EstimativaCorrida) : Painel
        data class Pedindo(val anterior: Estimativa) : Painel
    }

    sealed interface Evento {
        data object Saiu : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    private val _passageiro = MutableStateFlow<Passageiro?>(null)
    val passageiro: StateFlow<Passageiro?> = _passageiro

    private val _painel = MutableStateFlow<Painel>(Painel.Inicial)
    val painel: StateFlow<Painel> = _painel

    private val _aqui = MutableStateFlow<Local?>(null)
    val aqui: StateFlow<Local?> = _aqui

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    init {
        falhas.definirUsuario(auth.uidLogado())
        // Token de push no perfil (avisos de corrida).
        viewModelScope.launch { notificacoes.registrarToken() }
        viewModelScope.launch {
            passageiros.escutarLogado().catch { }.collect { atual ->
                // Perfil sumiu ou foi bloqueado enquanto o app estava aberto.
                if (atual == null || atual.bloqueado) {
                    sair()
                    return@collect
                }
                // Corrida pedida: o painel de pedido volta ao início (a
                // corrida aparece no lugar dele).
                if (atual.corridaAtualId != null) _painel.value = Painel.Inicial
                _passageiro.value = atual
            }
        }
    }

    fun atualizarLocalizacao() {
        viewModelScope.launch { localizacao.atual()?.let { _aqui.value = it } }
    }

    fun definirRota(partida: Local, destino: Local) = estimar(partida, destino, quartoPassageiro = false)

    // Marcar/desmarcar o 4º passageiro recalcula o "a partir de" (+20%).
    fun alternarQuartoPassageiro(marcado: Boolean) {
        val atual = _painel.value as? Painel.Estimativa ?: return
        if (atual.estimativa.quartoPassageiro == marcado) return
        estimar(atual.partida, atual.destino, marcado)
    }

    private fun estimar(partida: Local, destino: Local, quartoPassageiro: Boolean) {
        _painel.value = Painel.Estimando
        viewModelScope.launch {
            corridas.estimar(partida, destino, quartoPassageiro)
                .onSuccess { _painel.value = Painel.Estimativa(partida, destino, it) }
                .onFailure {
                    _painel.value = Painel.Inicial
                    _eventos.send(Evento.Mensagem(it.paraUiTexto()))
                }
        }
    }

    fun pedir() {
        val estimativa = _painel.value as? Painel.Estimativa ?: return
        _painel.value = Painel.Pedindo(estimativa)
        viewModelScope.launch {
            // Sucesso: o perfil recebe corridaAtualId e a corrida aparece na tela.
            corridas.pedir(estimativa.partida, estimativa.destino, estimativa.estimativa.quartoPassageiro).onFailure {
                _painel.value = estimativa
                _eventos.send(Evento.Mensagem(it.paraUiTexto()))
            }
        }
    }

    fun limpar() {
        _painel.value = Painel.Inicial
    }

    fun sair() {
        viewModelScope.launch {
            auth.sair()
            _eventos.send(Evento.Saiu)
        }
    }
}
