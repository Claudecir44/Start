package com.cjstudio.start.util

// Validação dos dígitos verificadores do CPF (mesma regra do Caronas). Só
// avisa rápido na tela; a unicidade é garantida no Firestore (ReservaCpf).
object CpfUtil {

    fun somenteDigitos(valor: String?): String = valor.orEmpty().filter { it.isDigit() }

    // Confere os dois dígitos verificadores (e recusa 111.111.111-11 e afins).
    fun valido(cpf: String?): Boolean {
        val d = somenteDigitos(cpf)
        if (d.length != 11 || d.all { it == d[0] }) return false
        for (tamanho in intArrayOf(9, 10)) {
            var soma = 0
            for (i in 0 until tamanho) soma += (d[i] - '0') * (tamanho + 1 - i)
            val digito = ((soma * 10) % 11) % 10
            if (digito != d[tamanho] - '0') return false
        }
        return true
    }

    // 12345678909 -> 123.456.789-09 (parcial enquanto digita).
    fun formatar(cpf: String?): String = Mascaras.aplicar(somenteDigitos(cpf), "###.###.###-##")
}
