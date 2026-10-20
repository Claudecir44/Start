package com.cjstudio.start.ui.motorista

import com.cjstudio.start.R
import com.cjstudio.start.ui.common.UiTexto
import com.cjstudio.start.ui.common.texto
import com.cjstudio.start.util.Validacao
import java.util.Calendar

// Regras dos dados de motorista, iguais no cadastro e na edição.
object ValidacaoMotorista {
    const val IDADE_MINIMA = 21
    const val IDADE_MAXIMA_VEICULO = 15

    // Categorias da CNH que permitem dirigir carro de passeio.
    val CATEGORIAS_CNH = listOf("B", "AB", "C", "AC", "D", "AD", "E", "AE")

    data class Dados(
        val nomeCompleto: String,
        val dataNascimento: String,
        val telefone: String,
        val cnhNumero: String,
        val cnhCategoria: String,
        val cnhValidade: String,
        val marca: String,
        val modelo: String,
        val ano: String,
        val cor: String,
        val placa: String
    )

    // Primeira regra quebrada (null = tudo certo). Fotos são conferidas por
    // quem chama (no cadastro são obrigatórias; na edição, opcionais).
    fun validar(d: Dados, temFotoCnh: Boolean, temFotoCrlv: Boolean): UiTexto? {
        val idade = Validacao.idade(d.dataNascimento)
        val ano = d.ano.toIntOrNull()
        val anoAtual = Calendar.getInstance().get(Calendar.YEAR)
        return when {
            !Validacao.nomeCompleto(d.nomeCompleto) -> texto(R.string.cadastro_erro_nome)
            idade == null -> texto(R.string.motorista_erro_nascimento)
            idade < IDADE_MINIMA -> texto(R.string.motorista_erro_idade, IDADE_MINIMA)
            !Validacao.telefone(d.telefone) -> texto(R.string.cadastro_erro_telefone)
            !Validacao.cnh(d.cnhNumero) -> texto(R.string.motorista_erro_cnh)
            d.cnhCategoria.isBlank() -> texto(R.string.motorista_erro_cnh_categoria)
            !Validacao.dataFutura(d.cnhValidade) -> texto(R.string.motorista_erro_cnh_validade)
            !temFotoCnh -> texto(R.string.motorista_erro_foto_cnh)
            d.marca.isBlank() || d.modelo.isBlank() || d.cor.isBlank() -> texto(R.string.motorista_erro_veiculo)
            ano == null || ano > anoAtual + 1 -> texto(R.string.motorista_erro_ano)
            ano < anoAtual - IDADE_MAXIMA_VEICULO -> texto(R.string.motorista_erro_ano_antigo, anoAtual - IDADE_MAXIMA_VEICULO)
            !Validacao.placa(d.placa) -> texto(R.string.motorista_erro_placa)
            !temFotoCrlv -> texto(R.string.motorista_erro_foto_crlv)
            else -> null
        }
    }
}
