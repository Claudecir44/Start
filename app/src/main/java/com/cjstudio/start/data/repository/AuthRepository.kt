package com.cjstudio.start.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.cjstudio.start.data.model.EmailJaCadastradoException
import com.cjstudio.start.data.model.EmailNaoVerificadoException
import com.cjstudio.start.di.KEY_UID
import com.cjstudio.start.di.KEY_ULTIMO_REENVIO_VERIFICACAO
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: Provider<FirebaseAuth>,
    private val prefs: DataStore<Preferences>
) : IAuthRepository {

    override fun uidLogado(): String? = try {
        auth.get().currentUser?.uid
    } catch (_: Exception) {
        null
    }

    override fun emailLogado(): String? = try {
        auth.get().currentUser?.email
    } catch (_: Exception) {
        null
    }

    override suspend fun sessaoValida(): Boolean =
        uidLogado() != null && !prefs.data.first()[KEY_UID].isNullOrEmpty()

    override suspend fun entrar(email: String, senha: String): Result<String> = runCatching {
        val usuario = auth.get().signInWithEmailAndPassword(email, senha).await().user
            ?: throw IllegalStateException("Falha ao entrar.")

        // reload(): a confirmação acontece fora do app (no navegador) e o
        // token do signIn nem sempre já traz isEmailVerified atualizado.
        usuario.reload().await()
        if (!usuario.isEmailVerified) {
            reenviarVerificacaoComIntervalo()
            auth.get().signOut()
            throw EmailNaoVerificadoException()
        }
        lembrarSessao(usuario.uid)
        usuario.uid
    }

    private suspend fun reenviarVerificacaoComIntervalo() {
        val agora = System.currentTimeMillis()
        val ultimo = prefs.data.first()[KEY_ULTIMO_REENVIO_VERIFICACAO] ?: 0L
        if (agora - ultimo < INTERVALO_REENVIO_MS) return
        try {
            auth.get().currentUser?.sendEmailVerification()?.await()
            prefs.edit { it[KEY_ULTIMO_REENVIO_VERIFICACAO] = agora }
        } catch (_: Exception) {
            // Best-effort: o bloqueio do login continua valendo de qualquer jeito.
        }
    }

    override suspend fun criarOuEntrarConta(email: String, senha: String): Result<ContaCadastro> = runCatching {
        try {
            val uid = auth.get().createUserWithEmailAndPassword(email, senha).await().user?.uid
                ?: throw IllegalStateException("Falha ao criar a conta.")
            ContaCadastro(uid, nova = true)
        } catch (e: FirebaseAuthUserCollisionException) {
            // A mesma pessoa pode ser admin, motorista e passageiro com UMA conta
            // só: e-mail já existente + a senha dessa conta = usa a conta.
            val usuario = try {
                auth.get().signInWithEmailAndPassword(email, senha).await().user
            } catch (_: FirebaseAuthInvalidCredentialsException) {
                null
            } ?: throw EmailJaCadastradoException()
            usuario.reload().await()
            ContaCadastro(usuario.uid, nova = false)
        }
    }

    override fun emailVerificado(): Boolean = try {
        auth.get().currentUser?.isEmailVerified == true
    } catch (_: Exception) {
        false
    }

    override suspend fun enviarVerificacaoEmail(): Result<Unit> = runCatching {
        auth.get().currentUser?.sendEmailVerification()?.await()
        prefs.edit { it[KEY_ULTIMO_REENVIO_VERIFICACAO] = System.currentTimeMillis() }
        Unit
    }

    override suspend fun enviarRedefinicaoSenha(email: String): Result<Unit> = runCatching {
        auth.get().sendPasswordResetEmail(email).await()
        Unit
    }

    override suspend fun excluirContaAtual() {
        try {
            auth.get().currentUser?.delete()?.await()
        } catch (_: Exception) {
        }
        sair()
    }

    override suspend fun lembrarSessao(uid: String) {
        prefs.edit { it[KEY_UID] = uid }
    }

    override suspend fun sair() {
        try {
            auth.get().signOut()
        } catch (_: Exception) {
        }
        prefs.edit { it.remove(KEY_UID) }
    }

    private companion object {
        const val INTERVALO_REENVIO_MS = 60_000L
    }
}
