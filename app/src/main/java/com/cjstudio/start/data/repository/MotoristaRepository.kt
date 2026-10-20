package com.cjstudio.start.data.repository

import android.net.Uri
import com.cjstudio.start.data.model.Motorista
import com.cjstudio.start.data.model.StatusMotorista
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class MotoristaRepository @Inject constructor(
    private val db: Provider<FirebaseFirestore>,
    private val auth: IAuthRepository,
    private val arquivos: IArquivoRepository
) : IMotoristaRepository {

    private fun colecao() = db.get().collection(COLECAO)

    override suspend fun cadastrar(motorista: Motorista, senha: String?, foto: Uri, fotoCnh: Uri, fotoCrlv: Uri): Result<ResultadoCadastro> {
        var conta: ContaCadastro? = null
        return try {
            val cpf = motorista.cpf ?: throw IllegalArgumentException("CPF obrigatório.")
            conta = FluxoCadastro.obterConta(auth, motorista.email, senha)
            val uid = conta.uid
            // O e-mail do perfil é sempre o da conta.
            motorista.email = auth.emailLogado()
            FluxoCadastro.garantirSemPerfil(colecao().document(uid))

            ReservaCpf.garantirLivre(db.get(), TIPO, cpf, uid)
            motorista.fotoUrl = arquivos.enviarImagem("fotos_perfil/$uid", foto).getOrThrow()
            motorista.cnhFotoUrl = arquivos.enviarImagem("documentos_motorista/$uid", fotoCnh).getOrThrow()
            motorista.crlvFotoUrl = arquivos.enviarImagem("documentos_motorista/$uid", fotoCrlv).getOrThrow()
            motorista.status = StatusMotorista.EM_ANALISE
            motorista.online = false

            db.get().batch()
                .set(db.get().collection(ReservaCpf.COLECAO).document(ReservaCpf.idDocumento(TIPO, cpf)), mapOf("uid" to uid, "tipo" to TIPO))
                .set(colecao().document(uid), motorista)
                .commit().await()

            Result.success(FluxoCadastro.concluir(auth, conta))
        } catch (e: Exception) {
            FluxoCadastro.desfazer(auth, conta, veioDoCadastro = senha != null)
            Result.failure(e)
        }
    }

    override suspend fun buscar(uid: String): Result<Motorista?> = runCatching {
        val doc = etapa("ler perfil") { colecao().document(uid).get().await() }
        doc.toObject(Motorista::class.java)?.also { it.id = doc.id }
    }

    override suspend fun buscarLogado(): Result<Motorista?> {
        val uid = auth.uidLogado() ?: return Result.failure(IllegalStateException("Não há sessão ativa."))
        return buscar(uid)
    }

    override fun escutarLogado(): Flow<Motorista?> = callbackFlow {
        val uid = auth.uidLogado()
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val registro = colecao().document(uid).addSnapshotListener { doc, erro ->
            if (erro != null) {
                close(erro)
                return@addSnapshotListener
            }
            trySend(doc?.toObject(Motorista::class.java)?.also { it.id = doc.id })
        }
        awaitClose { registro.remove() }
    }

    override suspend fun definirOnline(online: Boolean): Result<Unit> = runCatching {
        val uid = auth.uidLogado() ?: throw IllegalStateException("Não há sessão ativa.")
        colecao().document(uid).update("online", online).await()
        Unit
    }

    override suspend fun listarTodos(): Result<List<Motorista>> = runCatching {
        colecao().orderBy("criadoEm", Query.Direction.DESCENDING).get().await()
            .documents.mapNotNull { doc -> doc.toObject(Motorista::class.java)?.also { it.id = doc.id } }
    }

    override suspend fun definirStatus(uid: String, status: String, motivo: String?): Result<Unit> = runCatching {
        val dados = mutableMapOf<String, Any?>(
            "status" to status,
            "motivoStatus" to motivo,
            "analisadoPor" to auth.uidLogado(),
            "analisadoEm" to FieldValue.serverTimestamp()
        )
        // Motorista que deixa de estar aprovado sai do ar na hora.
        if (status != StatusMotorista.APROVADO) dados["online"] = false
        colecao().document(uid).update(dados).await()
        Unit
    }

    private companion object {
        const val COLECAO = "motoristas"
        const val TIPO = "motorista"
    }
}
