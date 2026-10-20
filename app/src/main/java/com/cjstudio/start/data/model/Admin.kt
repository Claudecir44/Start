package com.cjstudio.start.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Identidade de um administrador — separada de passageiro/motorista. Criado
// só pela Cloud Function "cadastrarAdmin" (senha do master), nunca por um
// create direto do cliente (firestore.rules). Mesmo modelo do Caronas.
data class Admin(
    @get:Exclude @set:Exclude
    var id: String? = null,

    var nome: String? = null,
    var sobrenome: String? = null,
    var email: String? = null,
    var telefone: String? = null,
    var cpf: String? = null,
    var fotoUrl: String? = null,

    // "admin" | "colaborador" — só rótulo; quem manda é o mapa permissoes.
    var role: String? = "admin",

    // Ausente = acesso total (mesma regra do servidor).
    var permissoes: Map<String, Boolean>? = null,

    @ServerTimestamp
    var criadoEm: Date? = null
) {
    @get:Exclude
    val nomeCompleto: String
        get() = listOfNotNull(nome, sobrenome).joinToString(" ").trim()

    @get:Exclude
    val ehColaborador: Boolean
        get() = role == "colaborador"

    @get:Exclude
    val ehMaster: Boolean
        get() = cpf == CPF_ADMIN_MASTER

    fun temPermissao(chave: String): Boolean {
        if (ehMaster) return true
        val mapa = permissoes ?: return true
        return mapa[chave] == true
    }

    companion object {
        // Mesmo CPF master do Match/Caronas — precisa bater com functions/index.js.
        const val CPF_ADMIN_MASTER = "56413025034"

        // Precisa bater com CHAVES_PERMISSOES em functions/index.js.
        val CHAVES_PERMISSOES = listOf(
            "motoristas" to "Motoristas",
            "passageiros" to "Passageiros",
            "corridas" to "Corridas",
            "financeiro" to "Financeiro",
            "administradores" to "Administradores"
        )
    }
}
