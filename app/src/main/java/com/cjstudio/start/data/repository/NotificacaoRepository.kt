package com.cjstudio.start.data.repository

import com.cjstudio.start.util.AppTipo
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

// Token de push do aparelho, gravado no perfil de quem está logado
// (passageiros/{uid}.fcmToken ou motoristas/{uid}.fcmToken). O servidor
// usa pra avisar "Nova corrida", "Motorista a caminho" etc. (notificacoes.js).
interface INotificacaoRepository {
    suspend fun registrarToken(): Result<Unit>
    suspend fun salvarToken(token: String): Result<Unit>
}

@Singleton
class NotificacaoRepository @Inject constructor(
    private val db: Provider<FirebaseFirestore>,
    private val messaging: Provider<FirebaseMessaging>,
    private val auth: IAuthRepository
) : INotificacaoRepository {

    override suspend fun registrarToken(): Result<Unit> = runCatching {
        salvarToken(messaging.get().token.await()).getOrThrow()
    }

    override suspend fun salvarToken(token: String): Result<Unit> = runCatching {
        val uid = auth.uidLogado() ?: return@runCatching
        val colecao = when {
            AppTipo.ehMotorista -> "motoristas"
            AppTipo.ehPassageiro -> "passageiros"
            else -> return@runCatching // admin ainda não recebe push
        }
        db.get().collection(colecao).document(uid).update("fcmToken", token).await()
        Unit
    }
}
