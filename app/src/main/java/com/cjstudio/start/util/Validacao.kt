package com.cjstudio.start.util

import java.util.Calendar
import java.util.Locale

// Regras de formulário que não dependem de Android — testáveis em teste
// unitário puro (ver app/src/test).
object Validacao {

    private val REGEX_EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    // Placa antiga (ABC1234) ou Mercosul (ABC1D23).
    private val REGEX_PLACA = Regex("^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$")

    fun email(valor: String) = REGEX_EMAIL.matches(valor)

    fun senha(valor: String) = valor.length in 6..20

    fun telefone(valor: String) = valor.filter { it.isDigit() }.length in 10..11

    fun placa(valor: String) = REGEX_PLACA.matches(valor.uppercase(Locale.ROOT))

    fun cnh(valor: String) = valor.filter { it.isDigit() }.length == 11

    fun nomeCompleto(valor: String) = valor.trim().split(Regex("\\s+")).size >= 2

    // Idade completa em anos a partir de dd/MM/aaaa (null se a data for inválida).
    fun idade(dataNascimento: String, hoje: Calendar = Calendar.getInstance()): Int? {
        if (!Mascaras.dataValida(dataNascimento)) return null
        val (dia, mes, ano) = dataNascimento.split("/").map { it.toInt() }
        var idade = hoje.get(Calendar.YEAR) - ano
        val mesHoje = hoje.get(Calendar.MONTH) + 1
        if (mesHoje < mes || (mesHoje == mes && hoje.get(Calendar.DAY_OF_MONTH) < dia)) idade--
        return idade
    }

    // Data dd/MM/aaaa ainda não passou (validade da CNH).
    fun dataFutura(data: String, hoje: Calendar = Calendar.getInstance()): Boolean {
        if (!Mascaras.dataValida(data)) return false
        val (dia, mes, ano) = data.split("/").map { it.toInt() }
        val alvo = Calendar.getInstance().apply {
            clear()
            set(ano, mes - 1, dia)
        }
        val inicioHoje = (hoje.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return !alvo.before(inicioHoje)
    }
}
