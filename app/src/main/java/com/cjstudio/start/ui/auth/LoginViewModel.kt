package com.cjstudio.start.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.R
import com.cjstudio.start.data.model.ContaBloqueadaException
import com.cjstudio.start.data.model.EmailNaoVerificadoException
import com.cjstudio.start.data.repository.IAuthRepository
import com.cjstudio.start.data.repository.IMotoristaRepository
import com.cjstudio.start.data.repository.IPassageiroRepository
import com.cjstudio.start.ui.common.UiTexto
import com.cjstudio.start.ui.common.paraUiTexto
import com.cjstudio.start.ui.common.texto
import com.cjstudio.start.util.AppTipo
import com.cjstudio.start.util.Validacao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Login dos apps Passageiro e Motorista (o Admin tem o seu, ver
// LoginAdminViewModel). Depois da senha, confere se a conta tem perfil
// NESTE app: conta só de passageiro entrando no app do motorista (ou o
// contrário) vai completar o cadastro com a mesma conta, sem criar outra.
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: IAuthRepository,
    private val passageiros: IPassageiroRepository,
    private val motoristas: IMotoristaRepository
) : ViewModel() {

    sealed interface Evento {
        data object IrParaInicio : Evento
        data object CompletarCadastro : Evento
        data class EmailNaoConfirmado(val email: String) : Evento
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
            val evento = try {
                auth.entrar(email, senha).getOrThrow()
                if (temPerfilNesteApp()) Evento.IrParaInicio else Evento.CompletarCadastro
            } catch (e: EmailNaoVerificadoException) {
                Evento.EmailNaoConfirmado(email)
            } catch (e: Exception) {
                if (e is ContaBloqueadaException) auth.sair()
                Evento.Mensagem(e.paraUiTexto())
            }
            _carregando.value = false
            _eventos.send(evento)
        }
    }

    // Passageiro bloqueado pelo admin não entra (lança ContaBloqueadaException).
    // Motorista entra com qualquer status: a tela inicial dele mostra se está
    // em análise, reprovado ou bloqueado.
    private suspend fun temPerfilNesteApp(): Boolean = if (AppTipo.ehMotorista) {
        motoristas.buscarLogado().getOrThrow() != null
    } else {
        val passageiro = passageiros.buscarLogado().getOrThrow() ?: return false
        if (passageiro.bloqueado) throw ContaBloqueadaException()
        true
    }

    fun recuperarSenha(email: String) {
        if (!Validacao.email(email)) {
            _eventos.trySend(Evento.Mensagem(texto(R.string.login_erro_email_recuperar)))
            return
        }
        viewModelScope.launch {
            val resultado = auth.enviarRedefinicaoSenha(email)
            _eventos.send(
                Evento.Mensagem(
                    resultado.fold({ texto(R.string.login_redefinicao_enviada, email) }, { it.paraUiTexto() })
                )
            )
        }
    }

    fun sairDaConta() {
        viewModelScope.launch { auth.sair() }
    }
}
