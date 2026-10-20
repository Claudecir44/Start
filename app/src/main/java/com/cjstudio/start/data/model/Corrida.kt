package com.cjstudio.start.data.model

import com.google.firebase.firestore.Exclude
import java.util.Date

// Ponto de partida/chegada de uma corrida (ou posição do motorista).
data class Local(
    var endereco: String? = null,
    var lat: Double = 0.0,
    var lng: Double = 0.0
)

// Situação da corrida (Corrida.status) — mesmos textos em functions/index.js.
//   procurando → aceita (motorista indo buscar) → em_andamento (embarcou)
//   → finalizada; procurando/aceita → cancelada.
object StatusCorrida {
    const val PROCURANDO = "procurando"
    const val ACEITA = "aceita"
    const val EM_ANDAMENTO = "em_andamento"
    const val FINALIZADA = "finalizada"
    const val CANCELADA = "cancelada"

    val ATIVAS = setOf(PROCURANDO, ACEITA, EM_ANDAMENTO)
}

// Documento corridas/{id}. Criado e mudado de situação SÓ pelas Cloud
// Functions (pedirCorrida/aceitarCorrida/acaoCorrida): preço e aceite nunca
// dependem do celular. O app só grava motoristaLocal (posição ao vivo).
// Dados de passageiro e motorista vêm copiados aqui pra cada lado ver o
// outro sem precisar ler o perfil alheio.
data class Corrida(
    @get:Exclude @set:Exclude
    var id: String? = null,

    var status: String = StatusCorrida.PROCURANDO,

    var passageiroId: String? = null,
    var passageiroNome: String? = null,
    var passageiroFotoUrl: String? = null,
    var passageiroTelefone: String? = null,

    var origem: Local? = null,
    var destino: Local? = null,
    // Paradas extras pedidas pelo passageiro durante a corrida (adicionarParada).
    var paradas: List<Local> = emptyList(),
    // 4º passageiro no banco da frente (+20%, o motorista decide se aceita).
    var quartoPassageiro: Boolean = false,
    var distanciaMetros: Long = 0,
    var duracaoSegundos: Long = 0,
    var polyline: String? = null,
    // Caminho do motorista até a partida (gravado no aceite).
    var polylineDeslocamento: String? = null,

    // Total que o passageiro paga (com a taxa da plataforma), calculado no
    // servidor. Antes do aceite é a prévia sem o deslocamento do motorista
    // (valorEstimado); no aceite vira o valor final. Pago direto ao motorista
    // (dinheiro ou Pix) nesta versão.
    var valor: Double = 0.0,
    var valorEstimado: Double = 0.0,
    var calculo: CalculoCorrida? = null,
    var formaPagamento: String = "direto",

    var motoristaId: String? = null,
    var motoristaNome: String? = null,
    var motoristaFotoUrl: String? = null,
    var motoristaTelefone: String? = null,
    var veiculo: Veiculo? = null,
    var motoristaLocal: Local? = null,

    var canceladaPor: String? = null,

    var criadaEm: Date? = null,
    var aceitaEm: Date? = null,
    var embarqueEm: Date? = null,
    var finalizadaEm: Date? = null,
    var canceladaEm: Date? = null
) {
    @get:Exclude
    val ativa: Boolean
        get() = status in StatusCorrida.ATIVAS
}

// Resposta de estimarCorrida (antes de pedir): rota e preço.
data class EstimativaCorrida(
    val distanciaMetros: Long,
    val duracaoSegundos: Long,
    val polyline: String,
    // "A partir de": ainda sem o deslocamento do motorista.
    val valor: Double,
    val quartoPassageiro: Boolean
)
