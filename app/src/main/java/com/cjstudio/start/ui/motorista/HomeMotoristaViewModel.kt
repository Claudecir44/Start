package com.cjstudio.start.ui.motorista

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.data.model.Motorista
import com.cjstudio.start.data.repository.IAuthRepository
import com.cjstudio.start.data.repository.IFalhasRepository
import com.cjstudio.start.data.repository.IMotoristaRepository
import com.cjstudio.start.ui.common.UiTexto
import com.cjstudio.start.ui.common.paraUiTexto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Tela inicial do motorista. Mostra a situação do cadastro em tempo real
// (em análise → aprovado aparece sozinho quando o admin aprova) e, aprovado,
// o botão de ficar online. Receber/aceitar corridas entra na fase 2.
@HiltViewModel
class HomeMotoristaViewModel @Inject constructor(
    private val auth: IAuthRepository,
    private val motoristas: IMotoristaRepository,
    private val falhas: IFalhasRepository
) : ViewModel() {

    sealed interface Evento {
        data object Saiu : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    val motorista: StateFlow<Motorista?> = motoristas.escutarLogado()
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    init {
        falhas.definirUsuario(auth.uidLogado())
    }

    fun alternarOnline() {
        val atual = motorista.value ?: return
        if (!atual.aprovado) return
        viewModelScope.launch {
            motoristas.definirOnline(!atual.online).onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }

    fun sair() {
        viewModelScope.launch {
            // Ninguém fica "online" depois de sair do app.
            if (motorista.value?.online == true) motoristas.definirOnline(false)
            auth.sair()
            _eventos.send(Evento.Saiu)
        }
    }
}
