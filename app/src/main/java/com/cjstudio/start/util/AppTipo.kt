package com.cjstudio.start.util

import com.cjstudio.start.BuildConfig

// Qual dos três apps está rodando (flavor) — único lugar que lê BuildConfig.TIPO.
object AppTipo {
    const val PASSAGEIRO = "passageiro"
    const val MOTORISTA = "motorista"
    const val ADMIN = "admin"

    val atual: String = BuildConfig.TIPO

    val ehPassageiro get() = atual == PASSAGEIRO
    val ehMotorista get() = atual == MOTORISTA
    val ehAdmin get() = atual == ADMIN
}
