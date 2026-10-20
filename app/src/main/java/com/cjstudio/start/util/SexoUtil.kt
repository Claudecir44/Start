package com.cjstudio.start.util

import android.content.Context
import com.cjstudio.start.R

// Valores gravados no Firestore — firestore.rules confere os mesmos.
// Pensado pra fase 2: passageira poder escolher motorista mulher.
object SexoUtil {
    const val HOMEM = "homem"
    const val MULHER = "mulher"

    fun rotulo(context: Context, sexo: String?): String = when (sexo) {
        HOMEM -> context.getString(R.string.sexo_homem)
        MULHER -> context.getString(R.string.sexo_mulher)
        else -> context.getString(R.string.sexo_nao_informado)
    }
}
