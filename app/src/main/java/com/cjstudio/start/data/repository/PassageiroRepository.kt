package com.cjstudio.start.data.repository

import android.net.Uri
import com.cjstudio.start.data.model.Passageiro
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class PassageiroRepository @Inject constructor(
    private val db: Provider<FirebaseFirestore>,
    private val auth: IAuthRepository,
    private val arquivos: IArquivoRepository
) : IPassageiroRepository {

    private fun colecao() = db.get().collection(COLECAO)

    override suspend fun cadastrar(passageiro: Passageiro, senha: String?, foto: Uri): Result<Unit> {
        var contaCriadaAgora = false
        return try {
            val cpf = passageiro.cpf ?: throw IllegalArgumentException("CPF obrigatório.")
            val uid = if (senha != null) {
                auth.criarConta(passageiro.email.orEmpty(), senha).getOrThrow().also { contaCriadaAgora = true }
            } else {
                // Conta já existente: o e-mail do perfil é o da conta.
                passageiro.email = auth.emailLogado()
                auth.uidLogado() ?: throw IllegalStateException("Sessão expirada. Entre de novo.")
            }

            ReservaCpf.garantirLivre(db.get(), TIPO, cpf, uid)
            passageiro.fotoUrl = arquivos.enviarImagem("fotos_perfil/$uid", foto).getOrThrow()

            db.get().batch()
                .set(db.get().collection(ReservaCpf.COLECAO).document(ReservaCpf.idDocumento(TIPO, cpf)), mapOf("uid" to uid, "tipo" to TIPO))
                .set(colecao().document(uid), passageiro)
                .commit().await()

            if (contaCriadaAgora) {
                // Conta nova só entra depois de confirmar o e-mail (ver
                // AuthRepository.entrar) — falha no envio não desfaz o
                // cadastro, o login reenvia.
                auth.enviarVerificacaoEmail()
                auth.sair()
            } else {
                auth.lembrarSessao(uid)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (contaCriadaAgora) auth.excluirContaAtual()
            Result.failure(e)
        }
    }

    override suspend fun buscar(uid: String): Result<Passageiro?> = runCatching {
        val doc = colecao().document(uid).get().await()
        doc.toObject(Passageiro::class.java)?.also { it.id = doc.id }
    }

    override suspend fun buscarLogado(): Result<Passageiro?> {
        val uid = auth.uidLogado() ?: return Result.failure(IllegalStateException("Não há sessão ativa."))
        return buscar(uid)
    }

    override suspend fun listarTodos(): Result<List<Passageiro>> = runCatching {
        colecao().orderBy("criadoEm", Query.Direction.DESCENDING).get().await()
            .documents.mapNotNull { doc -> doc.toObject(Passageiro::class.java)?.also { it.id = doc.id } }
    }

    override suspend fun definirBloqueio(uid: String, bloqueado: Boolean): Result<Unit> = runCatching {
        colecao().document(uid).update("bloqueado", bloqueado).await()
        Unit
    }

    private companion object {
        const val COLECAO = "passageiros"
        const val TIPO = "passageiro"
    }
}
