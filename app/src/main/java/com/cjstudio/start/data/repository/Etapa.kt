package com.cjstudio.start.data.repository

import android.util.Log
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

// Chamada de rede com nome, tempo no log (tag "StartRede") e limite de
// tempo: rede ruim vira uma mensagem clara em vez de carregamento infinito.
class TempoEsgotadoException(etapa: String) :
    Exception("A conexão está demorando demais ($etapa). Confira a internet e tente de novo.")

internal suspend fun <T> etapa(nome: String, limiteMs: Long = 20_000, bloco: suspend () -> T): T {
    val inicio = System.currentTimeMillis()
    Log.d("StartRede", "$nome: começou")
    return try {
        withTimeout(limiteMs) { bloco() }.also {
            Log.d("StartRede", "$nome: ok em ${System.currentTimeMillis() - inicio} ms")
        }
    } catch (e: TimeoutCancellationException) {
        Log.w("StartRede", "$nome: TEMPO ESGOTADO após $limiteMs ms")
        throw TempoEsgotadoException(nome)
    } catch (e: Exception) {
        Log.w("StartRede", "$nome: falhou em ${System.currentTimeMillis() - inicio} ms: $e")
        throw e
    }
}
