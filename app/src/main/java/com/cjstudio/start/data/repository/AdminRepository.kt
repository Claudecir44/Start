package com.cjstudio.start.data.repository

import android.net.Uri
import com.cjstudio.start.data.model.AcessoAdminRestritoException
import com.cjstudio.start.data.model.Admin
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val db: Provider<FirebaseFirestore>,
    private val functions: Provider<FirebaseFunctions>,
    private val auth: IAuthRepository,
    private val arquivos: IArquivoRepository
) : IAdminRepository {

    private fun colecao() = db.get().collection(COLECAO)

    override suspend fun entrar(email: String, senha: String): Result<String> = runCatching {
        val uid = auth.entrar(email, senha).getOrThrow()
        if (!souAdmin().getOrDefault(false)) {
            auth.sair()
            throw AcessoAdminRestritoException()
        }
        uid
    }

    override suspend fun souAdmin(): Result<Boolean> = runCatching {
        val uid = auth.uidLogado() ?: return@runCatching false
        colecao().document(uid).get().await().exists()
    }

    override suspend fun buscarLogado(): Result<Admin> = runCatching {
        val uid = auth.uidLogado() ?: throw IllegalStateException("Não há sessão ativa.")
        val doc = colecao().document(uid).get().await()
        doc.toObject(Admin::class.java)?.also { it.id = doc.id } ?: throw AcessoAdminRestritoException()
    }

    override suspend fun listarTodos(): Result<List<Admin>> = runCatching {
        colecao().orderBy("criadoEm", Query.Direction.DESCENDING).get().await()
            .documents.mapNotNull { doc -> doc.toObject(Admin::class.java)?.also { it.id = doc.id } }
    }

    override suspend fun cadastrarAdmin(
        nome: String,
        sobrenome: String,
        email: String,
        telefone: String,
        cpf: String,
        senha: String,
        role: String,
        permissoes: Map<String, Boolean>,
        senhaAutorizacao: String
    ): Result<String> = runCatching {
        val dados = mapOf(
            "nome" to nome,
            "sobrenome" to sobrenome,
            "email" to email,
            "telefone" to telefone,
            "cpf" to cpf,
            "senha" to senha,
            "role" to role,
            "permissoes" to permissoes,
            "senhaAutorizacao" to senhaAutorizacao
        )
        try {
            val resposta = functions.get().getHttpsCallable("cadastrarAdmin").call(dados).await()
            (resposta.getData() as? Map<*, *>)?.get("uid") as? String
                ?: throw IllegalStateException("Resposta inesperada do servidor.")
        } catch (e: FirebaseFunctionsException) {
            // Repassa a mensagem do servidor ("Senha de autorização incorreta.")
            // em vez do texto genérico da exceção.
            throw IllegalStateException(e.message ?: "Não foi possível cadastrar o administrador.")
        }
    }

    override suspend fun atualizarMinhaFoto(uri: Uri): Result<String> = runCatching {
        val uid = auth.uidLogado() ?: throw IllegalStateException("Não há sessão ativa.")
        val url = arquivos.enviarImagem("fotos_admin/$uid", uri).getOrThrow()
        colecao().document(uid).update("fotoUrl", url).await()
        url
    }

    private companion object {
        const val COLECAO = "admins"
    }
}
