package com.cjstudio.start.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlin.math.max

// Prepara a imagem escolhida para envio: lê do seletor do sistema (a
// permissão do content:// pode ser revogada antes do upload terminar),
// endireita pela orientação EXIF e reduz para no máximo LADO_MAXIMO px em
// JPEG. Foto de câmera vinha com 3–8 MB e deixava o cadastro lento (o do
// motorista sobe três); reduzida fica ~150–300 KB e continua legível para
// conferir CNH/CRLV.
object FotoUtil {
    private const val LADO_MAXIMO = 1280
    private const val QUALIDADE_JPEG = 82

    fun prepararParaEnvio(context: Context, uri: Uri): Uri? = try {
        val resolver = context.contentResolver

        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        val maiorLado = max(limites.outWidth, limites.outHeight)
        if (maiorLado <= 0) throw IllegalArgumentException("Imagem inválida.")

        // Decodifica já reduzida (potência de 2) pra não estourar memória
        // com foto de 50 MP; o ajuste fino vem no createScaledBitmap.
        var amostra = 1
        while (maiorLado / (amostra * 2) >= LADO_MAXIMO) amostra *= 2
        val opcoes = BitmapFactory.Options().apply { inSampleSize = amostra }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opcoes) }
            ?: throw IllegalArgumentException("Imagem inválida.")

        val escala = LADO_MAXIMO.toFloat() / max(bitmap.width, bitmap.height)
        if (escala < 1f) {
            bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * escala).toInt(), (bitmap.height * escala).toInt(), true)
        }

        val graus = resolver.openInputStream(uri)?.use { grausExif(ExifInterface(it)) } ?: 0
        if (graus != 0) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(graus.toFloat()) }, true)
        }

        val arquivo = File(context.cacheDir, "foto_${UUID.randomUUID()}.jpg")
        arquivo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALIDADE_JPEG, it) }
        Uri.fromFile(arquivo)
    } catch (_: Exception) {
        null
    }

    private fun grausExif(exif: ExifInterface): Int = when (
        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}
