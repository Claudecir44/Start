package com.cjstudio.start.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.R
import com.cjstudio.start.data.model.EmailNaoVerificadoException
import com.cjstudio.start.data.repository.IAdminRepository
import com.cjstudio.start.ui.common.UiTexto
import com.cjstudio.start.ui.common.paraUiTexto
import com.cjstudio.start.ui.common.texto
import com.cjstudio.start.util.Validacao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginAdminViewModel @Inject constructor(
    private val admins: IAdminRepository
) : ViewModel() {

    sealed interface Evento {
        data object IrParaPainel : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    private val _carregando = MutableStateFlow(false)
    val carregando: StateFlow<Boolean> = _carregando

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    fun entrar(email: String, senha: String) {
        if (_carregando.value) return
        if (!Validacao.email(email) || senha.isEmpty()) {
            _eventos.trySend(Evento.Mensagem(texto(R.string.login_erro_campos)))
            return
        }
        _carregando.value = true
        viewModelScope.launch {
            val resultado = admins.entrar(email, senha)
            _carregando.value = false
            _eventos.send(
                resultado.fold(
                    { Evento.IrParaPainel },
                    { e ->
                        Evento.Mensagem(
                            if (e is EmailNaoVerificadoException) texto(R.string.verificar_email_login, email) else e.paraUiTexto()
                        )
                    }
                )
            )
        }
    }
}
