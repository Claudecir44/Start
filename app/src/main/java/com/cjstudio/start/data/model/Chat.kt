package com.cjstudio.start.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Chat entre passageiro e motorista de uma corrida — chats/{corridaId}.
// Criado pelo servidor no aceite; fechaEm = 1 hora depois do fim da
// corrida (finalizada ou cancelada). Fechado: as mensagens continuam
// visíveis pros dois, só não dá mais pra enviar nem apagar (firestore.rules).
data class Chat(
    @get:Exclude @set:Exclude
    var id: String? = null,

    var corridaId: String? = null,
    var passageiroId: String? = null,
    var passageiroNome: String? = null,
    var passageiroFotoUrl: String? = null,
    var motoristaId: String? = null,
    var motoristaNome: String? = null,
    var motoristaFotoUrl: String? = null,

    var abertoEm: Date? = null,
    var fechaEm: Date? = null,
    var naoLidasPassageiro: Int = 0,
    var naoLidasMotorista: Int = 0,
    var ultimaMensagemEm: Date? = null
) {
    fun aberto(agoraMs: Long = System.currentTimeMillis()): Boolean = fechaEm?.let { agoraMs < it.time } ?: true

    fun souMotorista(meuId: String?) = motoristaId == meuId
    fun nomeDoOutro(meuId: String?) = if (souMotorista(meuId)) passageiroNome else motoristaNome
    fun fotoDoOutro(meuId: String?) = if (souMotorista(meuId)) passageiroFotoUrl else motoristaFotoUrl
    fun idDoOutro(meuId: String?) = if (souMotorista(meuId)) passageiroId else motoristaId
    fun naoLidasPraMim(meuId: String?) = if (souMotorista(meuId)) naoLidasMotorista else naoLidasPassageiro
}

// chats/{id}/mensagens/{id} — só texto (pedido do dono). "conteudo" vai
// cifrado (ChatCrypto). Apagar pra todos troca o texto por "" e marca
// deletadoParaTodos; apagar pra mim só esconde do meu lado.
data class MensagemChat(
    @get:Exclude @set:Exclude
    var id: String? = null,

    var remetenteId: String? = null,
    var destinatarioId: String? = null,
    var conteudo: String? = null,
    @ServerTimestamp
    var timestamp: Date? = null,
    var lida: Boolean = false,
    var deletadoParaTodos: Boolean = false,
    var deletadoParaRemetente: Boolean = false,
    var deletadoParaDestinatario: Boolean = false
) {
    fun escondidaPara(meuId: String?) =
        (remetenteId == meuId && deletadoParaRemetente) || (destinatarioId == meuId && deletadoParaDestinatario)
}
