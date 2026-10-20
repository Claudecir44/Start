package com.cjstudio.start.ui.admin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Motorista
import com.cjstudio.start.data.model.StatusMotorista
import com.cjstudio.start.data.repository.IMotoristaRepository
import com.cjstudio.start.ui.common.UiTexto
import com.cjstudio.start.ui.common.paraUiTexto
import com.cjstudio.start.ui.common.texto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Análise do cadastro de um motorista: dados, CNH, carro e fotos dos
// documentos, com Aprovar / Reprovar / Bloquear.
@HiltViewModel
class DetalhesMotoristaAdminViewModel @Inject constructor(
    private val motoristas: IMotoristaRepository,
    estado: SavedStateHandle
) : ViewModel() {

    private val uid: String = estado[DetalhesMotoristaAdminActivity.EXTRA_UID] ?: ""

    sealed interface Evento {
        data class Mensagem(val texto: UiTexto) : Evento
        data object Fechar : Evento
    }

    private val _motorista = MutableStateFlow<Motorista?>(null)
    val motorista: StateFlow<Motorista?> = _motorista

    private val _salvando = MutableStateFlow(false)
    val salvando: StateFlow<Boolean> = _salvando

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    init {
        viewModelScope.launch {
            motoristas.buscar(uid)
                .onSuccess { m ->
                    if (m == null) {
                        _eventos.send(Evento.Mensagem(texto(R.string.admin_motorista_nao_encontrado)))
                        _eventos.send(Evento.Fechar)
                    } else {
                        _motorista.value = m
                    }
                }
                .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }

    // Reprovar e bloquear exigem o motivo (o motorista vê na tela dele).
    fun definirStatus(status: String, motivo: String?) {
        if (_salvando.value) return
        val motivoLimpo = motivo?.trim()?.ifBlank { null }
        if (status != StatusMotorista.APROVADO && motivoLimpo == null) {
            _eventos.trySend(Evento.Mensagem(texto(R.string.admin_erro_motivo)))
            return
        }
        _salvando.value = true
        viewModelScope.launch {
            val resultado = motoristas.definirStatus(uid, status, if (status == StatusMotorista.APROVADO) null else motivoLimpo)
            _salvando.value = false
            resultado.onSuccess {
                _eventos.send(Evento.Mensagem(texto(R.string.admin_status_salvo)))
                _eventos.send(Evento.Fechar)
            }.onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }
}
