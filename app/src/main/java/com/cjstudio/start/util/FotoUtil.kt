package com.cjstudio.start.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

// Copia a imagem escolhida no seletor do sistema pra um arquivo temporário —
// a permissão de leitura do content:// original pode ser revogada antes do
// upload terminar (mesmo padrão do Match/Caronas).
object FotoUtil {
    fun copiarParaTemporario(context: Context, uri: Uri): Uri? = try {
        val arquivo = File(context.cacheDir, "foto_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { entrada ->
            arquivo.outputStream().use { saida -> entrada.copyTo(saida) }
        }
        Uri.fromFile(arquivo)
    } catch (_: Exception) {
        null
    }
}
