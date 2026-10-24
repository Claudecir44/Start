package com.cjstudio.start.data.repository

import com.cjstudio.start.data.model.Corrida
import com.cjstudio.start.data.model.EstimativaCorrida
import com.cjstudio.start.data.model.Local
import com.cjstudio.start.data.model.StatusCorrida
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class CorridaRepository @Inject constructor(
    private val db: Provider<FirebaseFirestore>,
    private val functions: Provider<FirebaseFunctions>
) : ICorridaRepository {

    private fun colecao() = db.get().collection(COLECAO)

    // Chama a Cloud Function e repassa a mensagem dela ("Esta corrida já foi
    // aceita por outro motorista") em vez do texto genérico.
    private suspend fun chamar(nome: String, dados: Map<String, Any?>): Map<*, *> = etapa(nome, limiteMs = 30_000) {
        try {
            functions.get().getHttpsCallable(nome).call(dados).await().getData() as? Map<*, *> ?: emptyMap<Any, Any>()
        } catch (e: FirebaseFunctionsException) {
            throw IllegalStateException(e.message ?: "Não foi possível concluir. Tente de novo.")
        }
    }

    private fun Local.paraMapa() = mapOf("endereco" to endereco, "lat" to lat, "lng" to lng)

    override suspend fun estimar(origem: Local, destino: Local, quartoPassageiro: Boolean): Result<EstimativaCorrida> = runCatching {
        val r = chamar(
            "estimarCorrida",
            mapOf("origem" to origem.paraMapa(), "destino" to destino.paraMapa(), "quartoPassageiro" to quartoPassageiro)
        )
        EstimativaCorrida(
            distanciaMetros = (r["distanciaMetros"] as Number).toLong(),
            duracaoSegundos = (r["duracaoSegundos"] as Number).toLong(),
            polyline = r["polyline"] as? String ?: "",
            valor = (r["valor"] as Number).toDouble(),
            quartoPassageiro = quartoPassageiro
        )
    }

    override suspend fun pedir(origem: Local, destino: Local, quartoPassageiro: Boolean): Result<String> = runCatching {
        val r = chamar(
            "pedirCorrida",
            mapOf("origem" to origem.paraMapa(), "destino" to destino.paraMapa(), "quartoPassageiro" to quartoPassageiro)
        )
        r["corridaId"] as String
    }

    override suspend fun adicionarParada(corridaId: String, parada: Local): Result<Unit> = runCatching {
        chamar("adicionarParada", mapOf("corridaId" to corridaId, "parada" to parada.paraMapa()))
        Unit
    }

    override suspend fun avancarBusca(corridaId: String): Result<Unit> = runCatching {
        chamar("avancarBusca", mapOf("corridaId" to corridaId))
        Unit
    }

    override suspend fun responderBusca(corridaId: String, continuar: Boolean): Result<Unit> = runCatching {
        chamar("responderBusca", mapOf("corridaId" to corridaId, "continuar" to continuar))
        Unit
    }

    override suspend fun aceitar(corridaId: String): Result<Unit> = runCatching {
        chamar("aceitarCorrida", mapOf("corridaId" to corridaId))
        Unit
    }

    private suspend fun acao(corridaId: String, acao: String): Result<Unit> = runCatching {
        chamar("acaoCorrida", mapOf("corridaId" to corridaId, "acao" to acao))
        Unit
    }

    override suspend fun embarcou(corridaId: String) = acao(corridaId, "embarcou")
    override suspend fun finalizar(corridaId: String) = acao(corridaId, "finalizar")
    override suspend fun cancelar(corridaId: String) = acao(corridaId, "cancelar")

    override fun escutar(corridaId: String): Flow<Corrida?> = callbackFlow {
        val registro = colecao().document(corridaId).addSnapshotListener { doc, erro ->
            if (erro != null) {
                close(erro)
                return@addSnapshotListener
            }
            trySend(doc?.toObject(Corrida::class.java)?.also { it.id = doc.id })
        }
        awaitClose { registro.remove() }
    }

    override fun escutarDisponiveis(): Flow<List<Corrida>> = callbackFlow {
        val registro = colecao().whereEqualTo("status", StatusCorrida.PROCURANDO)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null) {
                    close(erro)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { doc ->
                    doc.toObject(Corrida::class.java)?.also { it.id = doc.id }
                })
            }
        awaitClose { registro.remove() }
    }

    override suspend fun enviarPosicaoMotorista(corridaId: String, local: Local): Result<Unit> = runCatching {
        colecao().document(corridaId).update("motoristaLocal", local).await()
        Unit
    }

    override suspend fun listarTodas(limite: Long): Result<List<Corrida>> = runCatching {
        colecao().orderBy("criadaEm", Query.Direction.DESCENDING).limit(limite).get().await()
            .documents.mapNotNull { doc -> doc.toObject(Corrida::class.java)?.also { it.id = doc.id } }
    }

    private companion object {
        const val COLECAO = "corridas"
    }
}
