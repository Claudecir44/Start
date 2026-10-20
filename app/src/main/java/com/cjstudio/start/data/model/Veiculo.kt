package com.cjstudio.start.data.model

// Carro do motorista (só existe dentro de Motorista).
data class Veiculo(
    var marca: String? = null,
    var modelo: String? = null,
    var ano: Int? = null,
    var cor: String? = null,
    var placa: String? = null,
    // Critérios do Start: hatch ou sedã, 4 portas e ar-condicionado
    // funcionando (ver ValidacaoMotorista).
    var tipo: String? = null,
    var quatroPortas: Boolean = false,
    var arCondicionado: Boolean = false
) {
    fun descricao(): String = listOfNotNull(marca, modelo, cor, ano?.toString())
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}
