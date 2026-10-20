package com.cjstudio.start.ui.common

import android.content.Intent
import com.cjstudio.start.data.model.Local

// Passa um Local entre telas pelo Intent (prefixo separa partida/destino).
fun Intent.colocarLocal(prefixo: String, local: Local): Intent = apply {
    putExtra("${prefixo}_endereco", local.endereco)
    putExtra("${prefixo}_lat", local.lat)
    putExtra("${prefixo}_lng", local.lng)
}

fun Intent.lerLocal(prefixo: String): Local? {
    if (!hasExtra("${prefixo}_lat")) return null
    return Local(
        endereco = getStringExtra("${prefixo}_endereco"),
        lat = getDoubleExtra("${prefixo}_lat", 0.0),
        lng = getDoubleExtra("${prefixo}_lng", 0.0)
    )
}
