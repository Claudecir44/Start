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

    // Corrida em andamento (procurando/aceita/em_andamento) — só o servidor
    // grava (pedirCorrida/acaoCorrida); o app escuta pra abrir a corrida.
    var corridaAtualId: String? = null,
    // Chat da última corrida aceita (chats/{id}) — só o servidor grava.
    var ultimoChatId: String? = null,

    // Mantidos pelo servidor.
    var totalCorridas: Int = 0,
    var avaliacaoMedia: Double = 0.0,

    @ServerTimestamp
    var criadoEm: Date? = null
)
