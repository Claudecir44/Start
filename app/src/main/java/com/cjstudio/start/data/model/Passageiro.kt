package com.cjstudio.start.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Perfil de quem pede corrida — documento passageiros/{uid}. "id" nunca é
// gravado (é o próprio nome do documento); é preenchido depois da leitura.
data class Passageiro(
    @get:Exclude @set:Exclude
    var id: String? = null,

    var nomeCompleto: String? = null,
    var cpf: String? = null,
    var email: String? = null,
    var telefone: String? = null,
    var sexo: String? = null,
    var fotoUrl: String? = null,

    // Só o admin muda (firestore.rules). Passageiro bloqueado não entra.
    var bloqueado: Boolean = false,

    // Mantidos pelo servidor quando as corridas existirem (fase 2).
    var totalCorridas: Int = 0,
    var avaliacaoMedia: Double = 0.0,

    @ServerTimestamp
    var criadoEm: Date? = null
)
