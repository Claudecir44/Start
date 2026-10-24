package com.cjstudio.start.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Perfil de quem dirige — documento motoristas/{uid}. Documentos (CNH, CRLV)
// ficam no Storage em documentos_motorista/{uid}/ e só o próprio motorista
// e os admins leem (storage.rules).
data class Motorista(
    @get:Exclude @set:Exclude
    var id: String? = null,

    var nomeCompleto: String? = null,
    var cpf: String? = null,
    var dataNascimento: String? = null, // dd/MM/aaaa
    var email: String? = null,
    var telefone: String? = null,
    var sexo: String? = null,
    var fotoUrl: String? = null,

    var cnhNumero: String? = null,
    var cnhCategoria: String? = null,
    var cnhValidade: String? = null, // dd/MM/aaaa
    var cnhFotoUrl: String? = null,

    var veiculo: Veiculo? = null,
    var crlvFotoUrl: String? = null,

    // StatusMotorista — só o admin muda depois do cadastro (firestore.rules).
    var status: String = StatusMotorista.EM_ANALISE,
    var motivoStatus: String? = null,
    var analisadoPor: String? = null,
    var analisadoEm: Date? = null,

    // Disponível pra receber corrida agora. Só tem efeito com status APROVADO.
    var online: Boolean = false,

    // Última posição enviada pelo app enquanto online (corridas próximas).
    var localizacao: Local? = null,

    // Corrida aceita em andamento — só o servidor grava.
    var corridaAtualId: String? = null,
    // Chat da última corrida aceita (chats/{id}) — só o servidor grava.
    var ultimoChatId: String? = null,

    var totalCorridas: Int = 0,
    var avaliacaoMedia: Double = 0.0,

    @ServerTimestamp
    var criadoEm: Date? = null
) {
    @get:Exclude
    val aprovado: Boolean
        get() = status == StatusMotorista.APROVADO
}
