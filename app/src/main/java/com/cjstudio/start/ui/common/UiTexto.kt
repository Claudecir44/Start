package com.cjstudio.start.ui.common

import android.content.Context
import androidx.annotation.StringRes
import com.cjstudio.start.R
import com.cjstudio.start.data.model.AcessoAdminRestritoException
import com.cjstudio.start.data.model.ContaBloqueadaException
import com.cjstudio.start.data.model.CpfJaCadastradoException
import com.cjstudio.start.data.model.EmailJaCadastradoException
import com.cjstudio.start.data.model.EmailNaoVerificadoException
import com.cjstudio.start.data.model.PerfilJaExisteException
import com.cjstudio.start.data.model.PerfilNaoEncontradoException
import com.cjstudio.start.data.repository.TempoEsgotadoException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

// Texto de tela vindo do ViewModel sem o ViewModel precisar de Context
// (recurso de string ou texto pronto, ex.: mensagem do servidor).
sealed interface UiTexto {
    data class Recurso(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiTexto
    data class Livre(val texto: String) : UiTexto

    fun resolver(context: Context): String = when (this) {
        is Recurso -> context.getString(id, *args.toTypedArray())
        is Livre -> texto
    }
}

fun texto(@StringRes id: Int, vararg args: Any): UiTexto = UiTexto.Recurso(id, args.toList())

// Erros conhecidos viram mensagem própria; o resto mostra o texto da exceção.
fun Throwable.paraUiTexto(): UiTexto = when (this) {
    is EmailNaoVerificadoException -> texto(R.string.erro_email_nao_verificado)
    is PerfilNaoEncontradoException -> texto(R.string.erro_perfil_nao_encontrado)
    is ContaBloqueadaException -> texto(R.string.erro_conta_bloqueada)
    is AcessoAdminRestritoException -> texto(R.string.erro_acesso_restrito_admin)
    is CpfJaCadastradoException -> texto(R.string.erro_cpf_ja_cadastrado)
    is EmailJaCadastradoException -> texto(R.string.erro_email_ja_cadastrado)
    is PerfilJaExisteException -> texto(R.string.erro_perfil_ja_existe)
    is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> texto(R.string.erro_login_invalido)
    is FirebaseNetworkException -> texto(R.string.erro_sem_internet)
    is TempoEsgotadoException -> UiTexto.Livre(message.orEmpty())
    is IllegalStateException -> if (message?.contains("FirebaseApp") == true) {
        texto(R.string.erro_firebase_nao_configurado)
    } else {
        UiTexto.Livre(message ?: "")
    }
    else -> texto(R.string.erro_generico, message ?: javaClass.simpleName)
}
