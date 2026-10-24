package com.cjstudio.start.data.repository

import com.cjstudio.start.data.model.Chat
import com.cjstudio.start.data.model.MensagemChat
import com.cjstudio.start.util.ChatCrypto
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

// Chat da corrida (só texto). O documento chats/{id} nasce no servidor
// (aceite); o app escuta, marca lida e apaga; enviar passa pela function. Contador de não lidas
// do outro lado e push: functions/chat.js. Aberto/fechado: firestore.rules.
interface IChatRepository {
    fun escutarChat(chatId: String): Flow<Chat?>

    // Mensagens em ordem de envio, com o texto já decifrado.
    fun escutarMensagens(chatId: String): Flow<List<MensagemChat>>

    suspend fun enviar(chatId: String, texto: String): Result<Unit>

    // Zera o meu contador e marca como lidas as mensagens que recebi.
    suspend fun marcarLidas(chat: Chat, naoLidas: List<MensagemChat>): Result<Unit>

    suspend fun apagarParaMim(chatId: String, mensagem: MensagemChat): Result<Unit>
    suspend fun apagarParaTodos(chatId: String, mensagem: MensagemChat): Result<Unit>
}

@Singleton
class ChatRepository @Inject constructor(
    private val db: Provider<FirebaseFirestore>,
    private val functions: Provider<FirebaseFunctions>,
    private val auth: IAuthRepository
) : IChatRepository {

    private fun chats() = db.get().collection("chats")
    private fun mensagens(chatId: String) = chats().document(chatId).collection("mensagens")

    override fun escutarChat(chatId: String): Flow<Chat?> = callbackFlow {
        val registro = chats().document(chatId).addSnapshotListener { doc, erro ->
            if (erro != null) {
                close(erro)
                return@addSnapshotListener
            }
            trySend(doc?.toObject(Chat::class.java)?.also { it.id = doc.id })
        }
        awaitClose { registro.remove() }
    }

    override fun escutarMensagens(chatId: String): Flow<List<MensagemChat>> = callbackFlow {
        val registro = mensagens(chatId).orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null) {
                    close(erro)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { doc ->
                    doc.toObject(MensagemChat::class.java)?.also {
                        it.id = doc.id
                        it.conteudo = ChatCrypto.decifrar(it.conteudo)
                    }
                })
            }
        awaitClose { registro.remove() }
    }

    // Pela Cloud Function enviarMensagemChat: ela grava, soma as não lidas do
    // outro lado e manda o push (o texto já vai cifrado).
    override suspend fun enviar(chatId: String, texto: String): Result<Unit> = runCatching {
        etapa("enviarMensagemChat", limiteMs = 30_000) {
            try {
                functions.get().getHttpsCallable("enviarMensagemChat")
                    .call(mapOf("chatId" to chatId, "conteudo" to ChatCrypto.cifrar(texto))).await()
            } catch (e: FirebaseFunctionsException) {
                throw IllegalStateException(e.message ?: "Não foi possível enviar.")
            }
        }
        Unit
    }

    override suspend fun marcarLidas(chat: Chat, naoLidas: List<MensagemChat>): Result<Unit> = runCatching {
        val uid = auth.uidLogado() ?: return@runCatching
        val id = chat.id ?: return@runCatching
        val lote = db.get().batch()
        naoLidas.forEach { m -> m.id?.let { lote.update(mensagens(id).document(it), "lida", true) } }
        if (chat.naoLidasPraMim(uid) > 0) {
            lote.update(chats().document(id), if (chat.souMotorista(uid)) "naoLidasMotorista" else "naoLidasPassageiro", 0)
        }
        lote.commit().await()
        Unit
    }

    override suspend fun apagarParaMim(chatId: String, mensagem: MensagemChat): Result<Unit> = runCatching {
        val campo = if (mensagem.remetenteId == auth.uidLogado()) "deletadoParaRemetente" else "deletadoParaDestinatario"
        mensagens(chatId).document(mensagem.id!!).update(campo, true).await()
        Unit
    }

    override suspend fun apagarParaTodos(chatId: String, mensagem: MensagemChat): Result<Unit> = runCatching {
        mensagens(chatId).document(mensagem.id!!).update(mapOf("deletadoParaTodos" to true, "conteudo" to "")).await()
        Unit
    }
}
