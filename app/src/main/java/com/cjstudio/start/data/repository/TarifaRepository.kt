package com.cjstudio.start.data.repository

import com.cjstudio.start.data.model.Tarifa
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

// Valores da tarifa (config/tarifa). Sem documento = valores padrão.
interface ITarifaRepository {
    suspend fun buscar(): Result<Tarifa>

    // Só admin com permissão "financeiro" (firestore.rules).
    suspend fun salvar(tarifa: Tarifa): Result<Unit>
}

@Singleton
class TarifaRepository @Inject constructor(
    private val db: Provider<FirebaseFirestore>
) : ITarifaRepository {

    private fun documento() = db.get().collection("config").document("tarifa")

    override suspend fun buscar(): Result<Tarifa> = runCatching {
        etapa("ler tarifa") { documento().get().await() }.toObject(Tarifa::class.java) ?: Tarifa()
    }

    override suspend fun salvar(tarifa: Tarifa): Result<Unit> = runCatching {
        etapa("salvar tarifa") { documento().set(tarifa).await() }
        Unit
    }
}
