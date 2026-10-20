package com.cjstudio.start.ui.corrida

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.data.model.Corrida
import com.cjstudio.start.data.model.Local
import com.cjstudio.start.data.model.StatusCorrida
import com.cjstudio.start.data.model.Tarifa
import com.cjstudio.start.data.repository.ICorridaRepository
import com.cjstudio.start.data.repository.ILocalizacaoRepository
import com.cjstudio.start.data.repository.IMotoristaRepository
import com.cjstudio.start.data.repository.ITarifaRepository
import com.cjstudio.start.ui.common.UiTexto
import com.cjstudio.start.ui.common.paraUiTexto
import com.cjstudio.start.util.AppTipo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Corrida em andamento DENTRO da tela inicial (passageiro e motorista): a
// tela diz qual corrida acompanhar (corridaAtualId do perfil) e este
// ViewModel escuta, executa as ações e — no motorista — envia a posição.
// Depois de finalizada/cancelada a corrida continua aqui até o OK (fechar),
// pra mostrar o descritivo do valor.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CorridaViewModel @Inject constructor(
    private val corridas: ICorridaRepository,
    private val motoristas: IMotoristaRepository,
    private val localizacao: ILocalizacaoRepository,
    private val tarifas: ITarifaRepository
) : ViewModel() {

    private val corridaId = MutableStateFlow<String?>(null)

    val corrida: StateFlow<Corrida?> = corridaId.flatMapLatest { id ->
        if (id == null) flowOf(null) else corridas.escutar(id).catch { emit(null) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _enviando = MutableStateFlow(false)
    val enviando: StateFlow<Boolean> = _enviando

    private val _mensagens = Channel<UiTexto>(Channel.BUFFERED)
    val mensagens: Flow<UiTexto> = _mensagens.receiveAsFlow()

    // Valor da parada extra mostrado no botão do passageiro.
    private val _tarifa = MutableStateFlow(Tarifa())
    val tarifa: StateFlow<Tarifa> = _tarifa

    private var envioPosicao: Job? = null

    init {
        viewModelScope.launch { tarifas.buscar().onSuccess { _tarifa.value = it } }
    }

    // Corrida nova (ou a mesma) pra acompanhar. null não fecha a que está na
    // tela: o fim só some no OK (fechar).
    fun acompanhar(id: String?) {
        if (id != null && id != corridaId.value) corridaId.value = id
    }

    fun fechar() {
        corridaId.value = null
    }

    // Motorista: posição a cada 5 s com a tela aberta e a corrida em curso
    // (o passageiro vê o carro chegando e o mapa do motorista o acompanha).
    fun iniciarEnvioPosicao() {
        if (!AppTipo.ehMotorista || envioPosicao?.isActive == true) return
        envioPosicao = viewModelScope.launch {
            localizacao.acompanhar(INTERVALO_POSICAO_MS).catch { }.collect { local ->
                val atual = corrida.value ?: return@collect
                val id = atual.id ?: return@collect
                if (atual.status in setOf(StatusCorrida.ACEITA, StatusCorrida.EM_ANDAMENTO)) {
                    corridas.enviarPosicaoMotorista(id, local)
                    motoristas.atualizarLocalizacao(local)
                }
            }
        }
    }

    fun pararEnvioPosicao() {
        envioPosicao?.cancel()
    }

    fun embarcou() = executar { corridas.embarcou(it) }
    fun finalizar() = executar { corridas.finalizar(it) }
    fun cancelar() = executar { corridas.cancelar(it) }
    fun adicionarParada(parada: Local) = executar { corridas.adicionarParada(it, parada) }

    private fun executar(acao: suspend (String) -> Result<Unit>) {
        val id = corrida.value?.id ?: return
        if (_enviando.value) return
        _enviando.value = true
        viewModelScope.launch {
            acao(id).onFailure { _mensagens.send(it.paraUiTexto()) }
            _enviando.value = false
        }
    }

    private companion object {
        const val INTERVALO_POSICAO_MS = 5_000L
    }
}
