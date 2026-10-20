package com.cjstudio.start.util

import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.widget.EditText
import java.text.SimpleDateFormat
import java.util.Locale

// Máscaras dos campos de formulário ("#" = um dígito). Formata enquanto a
// pessoa digita, sem mexer no que ela apagou.
object Mascaras {
    const val CPF = "###.###.###-##"
    const val DATA = "##/##/####"

    fun aplicar(digitos: String, mascara: String): String {
        val sb = StringBuilder()
        var i = 0
        for (c in mascara) {
            if (i >= digitos.length) break
            if (c == '#') sb.append(digitos[i++]) else sb.append(c)
        }
        return sb.toString()
    }

    // (51) 99999-9999 — celular com 11 dígitos; fixo com 10 vira (51) 9999-9999.
    fun telefone(digitos: String): String =
        if (digitos.length <= 10) aplicar(digitos, "(##) ####-####") else aplicar(digitos, "(##) #####-####")

    // dd/MM/aaaa existente de verdade (recusa 31/02).
    fun dataValida(data: String): Boolean {
        if (data.length != 10) return false
        return try {
            SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply { isLenient = false }.parse(data) != null
        } catch (_: Exception) {
            false
        }
    }
}

private fun EditText.formatarAoDigitar(maxDigitos: Int, formatar: (String) -> String) {
    addTextChangedListener(object : TextWatcher {
        private var atualizando = false
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (atualizando) return
            val formatado = formatar(s.toString().filter { it.isDigit() }.take(maxDigitos))
            if (formatado == s.toString()) return
            atualizando = true
            setText(formatado)
            setSelection(formatado.length)
            atualizando = false
        }
    })
}

fun EditText.mascaraCpf() = formatarAoDigitar(11) { Mascaras.aplicar(it, Mascaras.CPF) }

fun EditText.mascaraTelefone() = formatarAoDigitar(11) { Mascaras.telefone(it) }

fun EditText.mascaraData() = formatarAoDigitar(8) { Mascaras.aplicar(it, Mascaras.DATA) }

// Placa em maiúsculas, sem espaço nem traço (ABC1D23 / ABC1234).
fun EditText.mascaraPlaca() {
    filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(7), InputFilter { origem, _, _, _, _, _ ->
        origem.filter { it.isLetterOrDigit() }
    })
}
