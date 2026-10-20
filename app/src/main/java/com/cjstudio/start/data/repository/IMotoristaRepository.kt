package com.cjstudio.start.data.repository

import android.net.Uri
import com.cjstudio.start.data.model.EdicaoMotorista
import com.cjstudio.start.data.model.Motorista
import kotlinx.coroutines.flow.Flow

interface IMotoristaRepository {
    // Mesmo fluxo do passageiro, com os documentos: foto de perfil, foto da
    // CNH e foto do CRLV (documento do carro). Todo cadastro nasce
    // StatusMotorista.EM_ANALISE e espera a aprovação de um admin.
    suspend fun cadastrar(motorista: Motorista, senha: String?, foto: Uri, fotoCnh: Uri, fotoCrlv: Uri): Result<ResultadoCadastro>

    suspend fun buscar(uid: String): Result<Motorista?>

    suspend fun buscarLogado(): Result<Motorista?>

    // Perfil do motorista logado em tempo real — a tela inicial muda sozinha
    // quando o admin aprova/reprova/bloqueia.
    fun escutarLogado(): Flow<Motorista?>

    // O motorista logado edita o próprio cadastro (sobe só as fotos trocadas).
    // Mudança em dado conferido pelo admin (EdicaoMotorista.exigeNovaAnalise)
    // volta o cadastro pra EM_ANALISE e tira do ar. Sucesso com true = voltou
    // pra análise.
    suspend fun atualizarPerfil(edicao: EdicaoMotorista): Result<Boolean>

    // Disponível (ou não) pra receber corridas. firestore.rules só aceita
    // "true" com o cadastro aprovado.
    suspend fun definirOnline(online: Boolean): Result<Unit>

    // ===== Admin =====
    suspend fun listarTodos(): Result<List<Motorista>>

    // Aprovar/reprovar/bloquear (StatusMotorista). Motivo é obrigatório pra
    // reprovar ou bloquear — o motorista vê o texto na tela dele.
    suspend fun definirStatus(uid: String, status: String, motivo: String?): Result<Unit>
}
