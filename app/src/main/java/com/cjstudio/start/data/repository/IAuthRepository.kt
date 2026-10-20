package com.cjstudio.start.data.repository

// Sessão (Firebase Auth) — as telas nunca falam com o FirebaseAuth direto.
interface IAuthRepository {
    fun uidLogado(): String?

    fun emailLogado(): String?

    // Sessão do Firebase E uid salvo no DataStore.
    suspend fun sessaoValida(): Boolean

    // Login completo: senha certa + e-mail confirmado. Com e-mail ainda não
    // confirmado, reenvia o link (com intervalo mínimo), sai da conta e falha
    // com EmailNaoVerificadoException. Devolve o uid.
    suspend fun entrar(email: String, senha: String): Result<String>

    // Cria a conta e deixa ela logada (o cadastro ainda precisa dela pra subir
    // as fotos e gravar o perfil). E-mail repetido = EmailJaCadastradoException.
    suspend fun criarConta(email: String, senha: String): Result<String>

    suspend fun enviarVerificacaoEmail(): Result<Unit>

    suspend fun enviarRedefinicaoSenha(email: String): Result<Unit>

    // Desfaz um cadastro que falhou no meio (conta recém-criada e logada).
    suspend fun excluirContaAtual()

    suspend fun lembrarSessao(uid: String)

    suspend fun sair()
}
