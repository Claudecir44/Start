package com.cjstudio.start.data.repository

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

// Provider: sem google-services.json (projeto Firebase ainda não criado) o
// Crashlytics não existe; assim o app não fecha só por iniciar.
@Singleton
class FalhasRepository @Inject constructor(
    private val crashlytics: Provider<FirebaseCrashlytics>
) : IFalhasRepository {

    private fun seguro(acao: (FirebaseCrashlytics) -> Unit) {
        try {
            acao(crashlytics.get())
        } catch (_: Exception) {
        }
    }

    override fun definirChave(chave: String, valor: String) = seguro { it.setCustomKey(chave, valor) }

    override fun definirUsuario(uid: String?) = seguro { it.setUserId(uid.orEmpty()) }

    override fun registrar(erro: Throwable) = seguro { it.recordException(erro) }
}
