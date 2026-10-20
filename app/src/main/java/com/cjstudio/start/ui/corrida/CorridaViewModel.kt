package com.cjstudio.start.ui.corrida

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.data.model.Corrida
import com.cjstudio.start.data.model.StatusCorrida
import com.cjstudio.start.data.repository.ICorridaRepository
import com.cjstudio.start.data.repository.ILocalizacaoRepository
import com.cjstudio.start.data.repository.IMotoristaRepository
import com.cjstudio.start.ui.common.UiTexto
import com.cjstudio.start.ui.common.paraUiTexto
import com.cjstudio.start.util.AppTipo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Corrida em andamento, dos dois lados. O passageiro só acompanha e pode
// cancelar; o motorista também envia a posição e avança a corrida
// (embarcou → finalizar). Quem muda a situação é o servidor.
@HiltViewModel
class CorridaViewModel @Inject constructor(
    private val corridas: ICorridaRepository,
    private val motoristas: IMotoristaRepository,
    private val localizacao: ILocalizacaoRepository,
    estado: SavedStateHandle
) : ViewModel() {

    val corridaId: String = estado[CorridaBaseActivity.EXTRA_CORRIDA] ?: ""

    val corrida: StateFlow<Corrida?> = corridas.escutar(corridaId)
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _enviando = MutableStateFlow(false)
    val enviando: StateFlow<Boolean> = _enviando

    private val _mensagens = Channel<UiTexto>(Channel.BUFFERED)
    val mensagens: Flow<UiTexto> = _mensagens.receiveAsFlow()

    private var envioPosicao: Job? = null

    // Motorista: posição a cada 5 s enquanto a corrida está ativa e a tela
    // aberta (o passageiro vê o carro chegando).
    fun iniciarEnvioPosicao() {
        if (!AppTipo.ehMotorista || envioPosicao?.isActive == true) return
        envioPosicao = viewModelScope.launch {
            localizacao.acompanhar(INTERVALO_POSICAO_MS).catch { }.collect { local ->
                val atual = corrida.value
                if (atual != null && atual.status in setOf(StatusCorrida.ACEITA, StatusCorrida.EM_ANDAMENTO)) {
                    corridas.enviarPosicaoMotorista(corridaId, local)
                    motoristas.atualizarLocalizacao(local)
                }
            }
        }
    }

    fun pararEnvioPosicao() {
        envioPosicao?.cancel()
    }

    fun embarcou() = executar { corridas.embarcou(corridaId) }
    fun finalizar() = executar { corridas.finalizar(corridaId) }
    fun cancelar() = executar { corridas.cancelar(corridaId) }

    private fun executar(acao: suspend () -> Result<Unit>) {
        if (_enviando.value) return
        _enviando.value = true
        viewModelScope.launch {
            acao().onFailure { _mensagens.send(it.paraUiTexto()) }
            _enviando.value = false
        }
    }

    private companion object {
        const val INTERVALO_POSICAO_MS = 5_000L
    }
}
