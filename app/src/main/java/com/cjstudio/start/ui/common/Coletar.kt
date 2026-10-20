package com.cjstudio.start.ui.common

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// Coleta um Flow do ViewModel só enquanto a tela está visível (padrão
// recomendado — evita atualizar view de tela parada em segundo plano).
fun <T> AppCompatActivity.coletar(flow: Flow<T>, acao: suspend (T) -> Unit) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { acao(it) }
        }
    }
}

fun AppCompatActivity.mostrarMensagem(texto: UiTexto, longa: Boolean = true) {
    Toast.makeText(this, texto.resolver(this), if (longa) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}
