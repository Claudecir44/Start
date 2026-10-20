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
// Escuta o próprio perfil: quando o servidor grava corridaAtualId (pediu,
// ou já tinha uma corrida aberta), manda abrir a tela da corrida.
@HiltViewModel
class HomePassageiroViewModel @Inject constructor(
    private val auth: IAuthRepository,
    private val passageiros: IPassageiroRepository,
    private val corridas: ICorridaRepository,
    private val localizacao: ILocalizacaoRepository,
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
        data class AbrirCorrida(val corridaId: String) : Evento
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

    private var corridaAberta: String? = null

    init {
        falhas.definirUsuario(auth.uidLogado())
        viewModelScope.launch {
            passageiros.escutarLogado().catch { }.collect { atual ->
                // Perfil sumiu ou foi bloqueado enquanto o app estava aberto.
                if (atual == null || atual.bloqueado) {
                    sair()
                    return@collect
                }
                _passageiro.value = atual
                val corridaId = atual.corridaAtualId
                if (corridaId != null && corridaId != corridaAberta) {
                    corridaAberta = corridaId
                    _painel.value = Painel.Inicial
                    _eventos.send(Evento.AbrirCorrida(corridaId))
                } else if (corridaId == null) {
                    corridaAberta = null
                }
            }
        }
    }

    fun atualizarLocalizacao() {
        viewModelScope.launch { localizacao.atual()?.let { _aqui.value = it } }
    }

    fun definirRota(partida: Local, destino: Local) {
        _painel.value = Painel.Estimando
        viewModelScope.launch {
            corridas.estimar(partida, destino)
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
            // Sucesso: o perfil recebe corridaAtualId e o init abre a corrida.
            corridas.pedir(estimativa.partida, estimativa.destino).onFailure {
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
