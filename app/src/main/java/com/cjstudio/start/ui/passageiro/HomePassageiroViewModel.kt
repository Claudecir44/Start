package com.cjstudio.start.ui.passageiro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.data.model.Passageiro
import com.cjstudio.start.data.repository.IAuthRepository
import com.cjstudio.start.data.repository.IFalhasRepository
import com.cjstudio.start.data.repository.IPassageiroRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Tela inicial do passageiro. Fase 1: perfil + sair. Pedir corrida (mapa,
// origem/destino, preço, chamar motorista) entra na fase 2.
@HiltViewModel
class HomePassageiroViewModel @Inject constructor(
    private val auth: IAuthRepository,
    private val passageiros: IPassageiroRepository,
    private val falhas: IFalhasRepository
) : ViewModel() {

    private val _passageiro = MutableStateFlow<Passageiro?>(null)
    val passageiro: StateFlow<Passageiro?> = _passageiro

    private val _saiu = Channel<Unit>(Channel.CONFLATED)
    val saiu: Flow<Unit> = _saiu.receiveAsFlow()

    init {
        falhas.definirUsuario(auth.uidLogado())
    }

    fun carregar() {
        viewModelScope.launch {
            // Falha de rede mantém a tela como está; só sai se o perfil
            // sumiu ou foi bloqueado enquanto o app estava aberto.
            passageiros.buscarLogado().onSuccess { atual ->
                if (atual == null || atual.bloqueado) sair() else _passageiro.value = atual
            }
        }
    }

    fun sair() {
        viewModelScope.launch {
            auth.sair()
            _saiu.send(Unit)
        }
    }
}
