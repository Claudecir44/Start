package com.cjstudio.start.data.model

import android.net.Uri

// O que o motorista pode mudar no próprio cadastro (CPF, e-mail e sexo não
// mudam). Foto nula = mantém a atual. Ver IMotoristaRepository.atualizarPerfil.
data class EdicaoMotorista(
    val nomeCompleto: String,
    val telefone: String,
    val dataNascimento: String,
    val cnhNumero: String,
    val cnhCategoria: String,
    val cnhValidade: String,
    val veiculo: Veiculo,
    val novaFoto: Uri? = null,
    val novaFotoCnh: Uri? = null,
    val novaFotoCrlv: Uri? = null
) {
    // Mudou algo que o admin conferiu na aprovação? (foto de perfil e
    // celular não contam).
    fun exigeNovaAnalise(atual: Motorista): Boolean =
        nomeCompleto != atual.nomeCompleto ||
            dataNascimento != atual.dataNascimento ||
            cnhNumero != atual.cnhNumero ||
            cnhCategoria != atual.cnhCategoria ||
            cnhValidade != atual.cnhValidade ||
            veiculo != atual.veiculo ||
            novaFotoCnh != null ||
            novaFotoCrlv != null
}
