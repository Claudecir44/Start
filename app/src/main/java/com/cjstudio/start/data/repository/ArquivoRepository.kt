package com.cjstudio.start.data.repository

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ArquivoRepository @Inject constructor(
    private val storage: Provider<FirebaseStorage>
) : IArquivoRepository {

    override suspend fun enviarImagem(pasta: String, uri: Uri): Result<String> = runCatching {
        val ref = storage.get().reference.child("$pasta/${UUID.randomUUID()}.jpg")
        // storage.rules só aceita image/* — o content type precisa ir explícito
        // (arquivo temporário local não tem tipo).
        val metadados = StorageMetadata.Builder().setContentType("image/jpeg").build()
        ref.putFile(uri, metadados).await()
        ref.downloadUrl.await().toString()
    }
}
