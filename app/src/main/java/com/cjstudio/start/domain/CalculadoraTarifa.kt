package com.cjstudio.start.domain

import com.cjstudio.start.data.model.CalculoCorrida
import com.cjstudio.start.data.model.Tarifa
import kotlin.math.max
import kotlin.math.roundToLong

// Mesmas regras de functions/tarifa.js (quem decide o valor é o servidor).
// No app serve pra PRÉVIA: o motorista vê quanto deve receber antes de
// aceitar, com o deslocamento estimado em linha reta (estimarDeslocamento).
object CalculadoraTarifa {

    // Linha reta × fator de ruas; velocidade média urbana pra estimar o tempo.
    private const val FATOR_RUAS = 1.3
    private const val VELOCIDADE_MEDIA_M_POR_S = 30_000.0 / 3600

    fun calcular(
        tarifa: Tarifa,
        deslocamentoMetros: Long,
        deslocamentoSegundos: Long,
        viagemMetros: Long,
        viagemSegundos: Long,
        quartoPassageiro: Boolean,
        paradas: Int
    ): CalculoCorrida {
        val totalMetros = deslocamentoMetros + viagemMetros
        val totalSegundos = deslocamentoSegundos + viagemSegundos
        val totalKm = totalMetros / 1000.0
        val totalMin = totalSegundos / 60.0

        val faixaMinima = totalKm <= tarifa.kmFaixaMinima
        val pelaTarifa = tarifa.bandeirada + tarifa.porKm * totalKm + tarifa.porMinuto * totalMin
        val valorBase = centavos(if (faixaMinima) tarifa.valorMinimoMotorista else max(pelaTarifa, tarifa.valorMinimoMotorista))
        val acrescimo = if (quartoPassageiro) centavos(valorBase * tarifa.acrescimoQuartoPassageiro) else 0.0
        val valorParadas = centavos(paradas * tarifa.valorPorParada)
        val valorMotorista = centavos(valorBase + acrescimo + valorParadas)
        val taxa = centavos(valorMotorista * tarifa.taxaPlataforma)

        return CalculoCorrida(
            deslocamentoMetros = deslocamentoMetros,
            deslocamentoSegundos = deslocamentoSegundos,
            viagemMetros = viagemMetros,
            viagemSegundos = viagemSegundos,
            totalMetros = totalMetros,
            totalSegundos = totalSegundos,
            regra = if (faixaMinima) "minimo" else "tarifa",
            bandeirada = tarifa.bandeirada,
            porKm = tarifa.porKm,
            porMinuto = tarifa.porMinuto,
            kmFaixaMinima = tarifa.kmFaixaMinima,
            valorBase = valorBase,
            quartoPassageiro = quartoPassageiro,
            percentualQuartoPassageiro = tarifa.acrescimoQuartoPassageiro,
            acrescimoQuartoPassageiro = acrescimo,
            paradas = paradas,
            valorPorParada = tarifa.valorPorParada,
            valorParadas = valorParadas,
            valorMotorista = valorMotorista,
            percentualPlataforma = tarifa.taxaPlataforma,
            taxaPlataforma = taxa,
            valorTotal = centavos(valorMotorista + taxa)
        )
    }

    // Deslocamento até o passageiro sem chamar o Google (só pra prévia).
    fun estimarDeslocamento(linhaRetaMetros: Double): Pair<Long, Long> {
        val metros = linhaRetaMetros * FATOR_RUAS
        return metros.roundToLong() to (metros / VELOCIDADE_MEDIA_M_POR_S).roundToLong()
    }

    private fun centavos(v: Double) = Math.round(v * 100) / 100.0
}
