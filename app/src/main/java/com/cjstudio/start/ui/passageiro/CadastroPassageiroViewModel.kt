package com.cjstudio.start.ui.passageiro

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Passageiro
import com.cjstudio.start.data.repository.IPassageiroRepository
import com.cjstudio.start.ui.auth.LoginActivity
import com.cjstudio.start.ui.common.UiTexto
import com.cjstudio.start.ui.common.paraUiTexto
import com.cjstudio.start.ui.common.texto
import com.cjstudio.start.util.CpfUtil
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
class CadastroPassageiroViewModel @Inject constructor(
    private val passageiros: IPassageiroRepository,
    estado: SavedStateHandle
) : ViewModel() {

    // Conta já logada (veio do login): sem e-mail/senha no formulário.
    val completandoCadastro: Boolean = estado[LoginActivity.EXTRA_COMPLETAR_CADASTRO] ?: false

    data class Formulario(
        val nomeCompleto: String,
        val cpf: String,
        val telefone: String,
        val email: String,
        val senha: String,
        val confirmarSenha: String,
        val sexo: String?,
        val aceitouTermos: Boolean
    )

    sealed interface Evento {
        // Conta nova: precisa confirmar o e-mail antes de entrar.
        data class ConfirmarEmail(val email: String) : Evento
        // Completou o cadastro de uma conta já logada: entra direto.
        data object IrParaInicio : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    // Guardada aqui (não na Activity) pra não se perder ao girar a tela.
    private val _foto = MutableStateFlow<Uri?>(null)
    val foto: StateFlow<Uri?> = _foto

    private val _enviando = MutableStateFlow(false)
    val enviando: StateFlow<Boolean> = _enviando

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    fun definirFoto(uri: Uri?) {
        _foto.value = uri
    }

    fun cadastrar(form: Formulario) {
        if (_enviando.value) return
        validar(form)?.let {
            _eventos.trySend(Evento.Mensagem(it))
            return
        }
        val passageiro = Passageiro(
            nomeCompleto = form.nomeCompleto.trim(),
            cpf = CpfUtil.somenteDigitos(form.cpf),
            email = form.email.trim().takeIf { !completandoCadastro },
            telefone = form.telefone,
            sexo = form.sexo
        )
        _enviando.value = true
        viewModelScope.launch {
            val resultado = passageiros.cadastrar(passageiro, form.senha.takeIf { !completandoCadastro }, _foto.value!!)
            _enviando.value = false
            _eventos.send(
                resultado.fold(
                    { if (completandoCadastro) Evento.IrParaInicio else Evento.ConfirmarEmail(form.email.trim()) },
                    { Evento.Mensagem(it.paraUiTexto()) }
                )
            )
        }
    }

    // Primeira regra quebrada (ou null se está tudo certo).
    private fun validar(form: Formulario): UiTexto? = when {
        _foto.value == null -> texto(R.string.cadastro_erro_foto)
        !Validacao.nomeCompleto(form.nomeCompleto) -> texto(R.string.cadastro_erro_nome)
        !CpfUtil.valido(form.cpf) -> texto(R.string.cadastro_erro_cpf)
        !Validacao.telefone(form.telefone) -> texto(R.string.cadastro_erro_telefone)
        form.sexo == null -> texto(R.string.cadastro_erro_sexo)
        !completandoCadastro && !Validacao.email(form.email.trim()) -> texto(R.string.cadastro_erro_email)
        !completandoCadastro && !Validacao.senha(form.senha) -> texto(R.string.cadastro_erro_senha)
        !completandoCadastro && form.senha != form.confirmarSenha -> texto(R.string.cadastro_erro_senhas_diferentes)
        !form.aceitouTermos -> texto(R.string.cadastro_erro_termos)
        else -> null
    }
}
