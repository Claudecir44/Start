package com.cjstudio.start.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Cifra (AES-256-GCM) o texto das mensagens do chat da corrida, como no
// Match (MensagemCryptoUtil): quem olha o Firestore direto (um export, um
// admin) não lê a conversa. A chave fica no app porque os dois lados
// precisam ler em tempo real — não protege contra quem descompila o APK.
// Chave própria do Start (não é a do Match).
object ChatCrypto {

    private const val CHAVE_BASE64 = "W25jb12GzM/RSJE5klGmBqRXk2e7PlNygcHRbfUzM1g="
    private const val PREFIXO_CIFRADO = "ENC1:"
    private const val TAMANHO_IV = 12
    private const val TAMANHO_TAG_BITS = 128

    private val chave by lazy { SecretKeySpec(Base64.decode(CHAVE_BASE64, Base64.NO_WRAP), "AES") }

    fun cifrar(texto: String): String {
        val iv = ByteArray(TAMANHO_IV).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, chave, GCMParameterSpec(TAMANHO_TAG_BITS, iv))
        return PREFIXO_CIFRADO + Base64.encodeToString(iv + cipher.doFinal(texto.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    // Texto sem o prefixo (ou que não decifra) volta como veio.
    fun decifrar(armazenado: String?): String {
        if (armazenado.isNullOrEmpty() || !armazenado.startsWith(PREFIXO_CIFRADO)) return armazenado.orEmpty()
        return try {
            val dados = Base64.decode(armazenado.removePrefix(PREFIXO_CIFRADO), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, chave, GCMParameterSpec(TAMANHO_TAG_BITS, dados.copyOfRange(0, TAMANHO_IV)))
            String(cipher.doFinal(dados.copyOfRange(TAMANHO_IV, dados.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            armazenado
        }
    }
}
