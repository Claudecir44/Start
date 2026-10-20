package com.cjstudio.start.data.repository

import android.net.Uri
import com.cjstudio.start.data.model.Admin

interface IAdminRepository {
    // Login do app Admin: senha certa + entrada em admins/{uid}. Conta comum
    // (passageiro/motorista) é barrada com AcessoAdminRestritoException e a
    // sessão é encerrada. Devolve o uid.
    suspend fun entrar(email: String, senha: String): Result<String>

    suspend fun souAdmin(): Result<Boolean>

    suspend fun buscarLogado(): Result<Admin>

    suspend fun listarTodos(): Result<List<Admin>>

    // Cria outro administrador pela Cloud Function "cadastrarAdmin" — exige a
    // senha do administrador master (fica só em functions/.env) e, quando já
    // existe algum admin, também a permissão "administradores" de quem chama.
    // O primeiro admin de todos é criado só com a senha master, sem sessão.
    // A conta já nasce com o e-mail confirmado (quem criou é o master).
    suspend fun cadastrarAdmin(
        nome: String,
        sobrenome: String,
        email: String,
        telefone: String,
        cpf: String,
        senha: String,
        role: String,
        permissoes: Map<String, Boolean>,
        senhaAutorizacao: String
    ): Result<String>

    // O próprio admin troca a foto dele (firestore.rules só libera fotoUrl
    // pro dono do documento).
    suspend fun atualizarMinhaFoto(uri: Uri): Result<String>
}
