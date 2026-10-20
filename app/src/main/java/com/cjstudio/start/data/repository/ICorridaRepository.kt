package com.cjstudio.start.data.repository

import com.cjstudio.start.data.model.Corrida
import com.cjstudio.start.data.model.EstimativaCorrida
import com.cjstudio.start.data.model.Local
import kotlinx.coroutines.flow.Flow

// Corridas. Tudo que muda situação ou preço passa pelas Cloud Functions
// (functions/corridas.js); o app só lê e o motorista envia a posição.
interface ICorridaRepository {
    // Rota e preço antes de pedir.
    suspend fun estimar(origem: Local, destino: Local): Result<EstimativaCorrida>

    // Cria a corrida (procurando motorista). Devolve o id.
    suspend fun pedir(origem: Local, destino: Local): Result<String>

    // Motorista aceita (só um consegue, o servidor garante).
    suspend fun aceitar(corridaId: String): Result<Unit>

    suspend fun embarcou(corridaId: String): Result<Unit>
    suspend fun finalizar(corridaId: String): Result<Unit>
    suspend fun cancelar(corridaId: String): Result<Unit>

    // Uma corrida em tempo real (null = não existe/sem acesso).
    fun escutar(corridaId: String): Flow<Corrida?>

    // Corridas esperando motorista (pra quem está online e aprovado).
    fun escutarDisponiveis(): Flow<List<Corrida>>

    // Posição do motorista durante a corrida (o passageiro vê no mapa).
    suspend fun enviarPosicaoMotorista(corridaId: String, local: Local): Result<Unit>

    // ===== Admin =====
    suspend fun listarTodas(limite: Long = 200): Result<List<Corrida>>
}
