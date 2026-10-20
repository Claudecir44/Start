package com.cjstudio.start.data.model

// Valores do preço da corrida — documento config/tarifa, editado pelo admin
// (seção Tarifas). Quem calcula de verdade é o servidor
// (functions/index.js: calcularValor), com estes mesmos campos. Regras de
// cobrança novas entram lá, sem mudar o app.
data class Tarifa(
    var bandeirada: Double = PADRAO.bandeiradaPadrao,
    var porKm: Double = PADRAO.porKmPadrao,
    var porMinuto: Double = PADRAO.porMinutoPadrao,
    var minimo: Double = PADRAO.minimoPadrao,
    // Motorista só vê corridas com partida até este raio dele.
    var raioBuscaKm: Double = PADRAO.raioPadrao
) {
    companion object PADRAO {
        // Precisam bater com TARIFA_PADRAO em functions/index.js.
        const val bandeiradaPadrao = 4.0
        const val porKmPadrao = 1.8
        const val porMinutoPadrao = 0.3
        const val minimoPadrao = 8.0
        const val raioPadrao = 10.0
    }
}
