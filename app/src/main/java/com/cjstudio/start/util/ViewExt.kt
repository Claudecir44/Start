package com.cjstudio.start.util

import android.graphics.Rect
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import com.cjstudio.start.R

// Ícone de olho à direita do campo de senha: tocar alterna mostrar/ocultar
// sem mexer no texto (mesmo padrão do Match/Caronas).
fun EditText.habilitarToggleSenha() {
    val tipoOculto = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    val tipoVisivel = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    inputType = tipoOculto
    aplicarIconeSenha(oculto = true)

    setOnTouchListener { v, event ->
        if (event.action == MotionEvent.ACTION_UP) {
            val icone = compoundDrawables[2]
            if (icone != null) {
                val area = icone.bounds.width() + paddingEnd + (16 * resources.displayMetrics.density).toInt()
                if (event.x >= v.width - area) {
                    val estaOculto = inputType == tipoOculto
                    inputType = if (estaOculto) tipoVisivel else tipoOculto
                    setSelection(text.length)
                    aplicarIconeSenha(oculto = !estaOculto)
                    v.performClick()
                    return@setOnTouchListener true
                }
            }
        }
        false
    }
}

private fun EditText.aplicarIconeSenha(oculto: Boolean) {
    val icone = ContextCompat.getDrawable(context, if (oculto) R.drawable.ic_visibility else R.drawable.ic_visibility_off)
    setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, icone, null)
}

// Com targetSdk 35+ o adjustResize não empurra mais o formulário acima do
// teclado de forma confiável: soma ao padding inferior só o que o teclado
// ocupa além da barra de navegação (as barras já são reservadas pra toda
// Activity em StartApplication) e rola até o campo em foco.
fun View.ajustarPaddingParaTeclado() {
    val paddingOriginal = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val teclado = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        val extra = maxOf(teclado - barras, 0)
        view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, paddingOriginal + extra)
        if (extra > 0 && view is ScrollView) {
            view.findFocus()?.let { campo ->
                view.post {
                    val r = Rect()
                    campo.getDrawingRect(r)
                    view.offsetDescendantRectToMyCoords(campo, r)
                    view.smoothScrollTo(0, r.bottom - view.height + extra)
                }
            }
        }
        insets
    }
}

fun ImageView.carregarFotoRedonda(url: String?) {
    if (url.isNullOrEmpty()) {
        setImageResource(R.drawable.ic_pessoa)
        return
    }
    load(url) {
        transformations(CircleCropTransformation())
        placeholder(R.drawable.ic_pessoa)
        error(R.drawable.ic_pessoa)
    }
}
