package com.cjstudio.start.data.repository

import android.net.Uri

// Upload de imagens (foto de perfil, CNH, CRLV) para o Storage.
interface IArquivoRepository {
    // Sobe a imagem em "pasta/arquivo-aleatório.jpg" e devolve a URL de download.
    suspend fun enviarImagem(pasta: String, uri: Uri): Result<String>
}
