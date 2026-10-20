package com.cjstudio.start.data.repository

import android.net.Uri
import com.cjstudio.start.data.model.Passageiro

interface IPassageiroRepository {
    // Cadastro completo: cria a conta (quando "senha" vem preenchida; null =
    // conta já existe e está logada, só falta o perfil de passageiro), sobe a
    // foto, grava perfil + reserva do CPF juntos e manda o e-mail de
    // confirmação. Qualquer falha no caminho desfaz a conta recém-criada.
    suspend fun cadastrar(passageiro: Passageiro, senha: String?, foto: Uri): Result<ResultadoCadastro>

    suspend fun buscar(uid: String): Result<Passageiro?>

    suspend fun buscarLogado(): Result<Passageiro?>

    // O passageiro logado edita nome, celular e foto (null = mantém). CPF,
    // e-mail e sexo não mudam (firestore.rules).
    suspend fun atualizarPerfil(nomeCompleto: String, telefone: String, novaFoto: Uri?): Result<Unit>

    // ===== Admin =====
    suspend fun listarTodos(): Result<List<Passageiro>>

    suspend fun definirBloqueio(uid: String, bloqueado: Boolean): Result<Unit>
}
