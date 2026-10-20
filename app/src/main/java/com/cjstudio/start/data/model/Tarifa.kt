package com.cjstudio.start.data.model

// Valores do preço da corrida — documento config/tarifa, editado pelo admin
// (seção Tarifas). Quem calcula de verdade é o servidor
// (functions/tarifa.js); o app usa os mesmos campos só pra mostrar prévias
// (CalculadoraTarifa). Percentuais em fração: 0.15 = 15%.
data class Tarifa(
    var bandeirada: Double = PADRAO.bandeiradaPadrao,
    var porKm: Double = PADRAO.porKmPadrao,
    var porMinuto: Double = PADRAO.porMinutoPadrao,
    // Até kmFaixaMinima no total (deslocamento + viagem), o motorista recebe
    // valorMinimoMotorista; acima disso a tarifa, nunca abaixo do mínimo.
    var valorMinimoMotorista: Double = PADRAO.minimoMotoristaPadrao,
    var kmFaixaMinima: Double = PADRAO.kmFaixaMinimaPadrao,
    var taxaPlataforma: Double = PADRAO.taxaPlataformaPadrao,
    var acrescimoQuartoPassageiro: Double = PADRAO.acrescimoQuartoPadrao,
    var valorPorParada: Double = PADRAO.porParadaPadrao,
    // Motorista só vê/recebe aviso de corridas com partida até este raio dele.
    var raioBuscaKm: Double = PADRAO.raioPadrao
) {
    companion object PADRAO {
        // Precisam bater com TARIFA_PADRAO em functions/tarifa.js.
        const val bandeiradaPadrao = 4.0
        const val porKmPadrao = 1.8
        const val porMinutoPadrao = 0.3
        const val minimoMotoristaPadrao = 10.0
        const val kmFaixaMinimaPadrao = 5.0
        const val taxaPlataformaPadrao = 0.15
        const val acrescimoQuartoPadrao = 0.20
        const val porParadaPadrao = 3.0
        const val raioPadrao = 10.0
    }
}

// Descritivo do preço gravado em cada corrida (corridas/{id}.calculo) —
// mostrado no fim pro passageiro, pro motorista e no admin. Mesmos campos de
// calcularCorrida em functions/tarifa.js.
data class CalculoCorrida(
    var deslocamentoMetros: Long = 0,
    var deslocamentoSegundos: Long = 0,
    var viagemMetros: Long = 0,
    var viagemSegundos: Long = 0,
    var totalMetros: Long = 0,
    var totalSegundos: Long = 0,
    // "minimo" (até a faixa mínima) ou "tarifa".
    var regra: String = "minimo",
    var bandeirada: Double = 0.0,
    var porKm: Double = 0.0,
    var porMinuto: Double = 0.0,
    var kmFaixaMinima: Double = 0.0,
    var valorBase: Double = 0.0,
    var quartoPassageiro: Boolean = false,
    var percentualQuartoPassageiro: Double = 0.0,
    var acrescimoQuartoPassageiro: Double = 0.0,
    var paradas: Int = 0,
    var valorPorParada: Double = 0.0,
    var valorParadas: Double = 0.0,
    var valorMotorista: Double = 0.0,
    var percentualPlataforma: Double = 0.0,
    var taxaPlataforma: Double = 0.0,
    var valorTotal: Double = 0.0
)
