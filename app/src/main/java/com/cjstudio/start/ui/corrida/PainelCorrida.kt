package com.cjstudio.start.ui.corrida

import android.content.Context
import android.view.View
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Corrida
import com.cjstudio.start.data.model.Local
import com.cjstudio.start.data.model.StatusCorrida
import com.cjstudio.start.databinding.IncludePainelCorridaBinding
import com.cjstudio.start.util.AppTipo
import com.cjstudio.start.util.MapaUtil
import com.cjstudio.start.util.MapaUtil.latLng
import com.cjstudio.start.util.carregarFotoRedonda
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// Corrida em andamento dentro da tela inicial (nunca em outra tela nem em
// app de mapa externo): preenche o painel de baixo e desenha no mapa entre
// as linhas. Passageiro: acompanha, pede parada e cancela. Motorista: o
// mapa segue o carro, mostrando o caminho até o passageiro e depois até o
// destino; embarcou → finalizar.
class PainelCorrida(
    private val context: Context,
    private val painel: IncludePainelCorridaBinding,
    private val viewModel: CorridaViewModel,
    private val aoPedirParada: () -> Unit
) {
    private val ehMotorista = AppTipo.ehMotorista
    private var carro: Marker? = null
    private var desenhoAtual: String? = null
    private var enquadradoEm: String? = null

    init {
        painel.btnLigar.setOnClickListener {
            val c = viewModel.corrida.value ?: return@setOnClickListener
            MapaUtil.ligar(context, if (ehMotorista) c.passageiroTelefone else c.motoristaTelefone)
        }
        painel.btnCancelar.setOnClickListener { confirmarCancelamento() }
        painel.btnSecundario.setOnClickListener { aoPedirParada() } // só o passageiro usa
        painel.btnPrincipal.setOnClickListener {
            val c = viewModel.corrida.value ?: return@setOnClickListener
            when {
                !c.ativa -> viewModel.fechar()
                ehMotorista && c.status == StatusCorrida.ACEITA -> viewModel.embarcou()
                ehMotorista && c.status == StatusCorrida.EM_ANDAMENTO -> confirmarFinalizar(c)
            }
        }
    }

    fun habilitar(ativo: Boolean) {
        listOf(painel.btnPrincipal, painel.btnSecundario, painel.btnCancelar).forEach { it.isEnabled = ativo }
    }

    // ===================== Painel =====================

    fun mostrar(c: Corrida) {
        val (titulo, detalhe) = if (ehMotorista) textosMotorista(c) else textosPassageiro(c)
        painel.tvSituacao.setText(titulo)
        painel.tvDetalhe.text = detalhe
        painel.progressoBusca.visibility = if (c.status == StatusCorrida.PROCURANDO) View.VISIBLE else View.GONE

        // A outra pessoa (só enquanto a corrida está em curso).
        val emCurso = c.status in setOf(StatusCorrida.ACEITA, StatusCorrida.EM_ANDAMENTO)
        painel.blocoPessoa.visibility = if (emCurso) View.VISIBLE else View.GONE
        if (ehMotorista) {
            painel.ivPessoa.carregarFotoRedonda(c.passageiroFotoUrl)
            painel.tvPessoaNome.text = c.passageiroNome
            painel.tvPessoaInfo.text = context.getString(
                if (c.quartoPassageiro) R.string.corrida_passageiro_quarto else R.string.corrida_passageiro
            )
        } else {
            painel.ivPessoa.carregarFotoRedonda(c.motoristaFotoUrl)
            painel.tvPessoaNome.text = c.motoristaNome
            painel.tvPessoaInfo.text = c.veiculo?.let { "${it.descricao()}  ·  ${it.placa.orEmpty()}" }
        }

        painel.tvPartida.text = c.origem?.endereco
        painel.tvDestino.text = c.destino?.endereco
        painel.tvParadas.visibility = if (c.paradas.isEmpty()) View.GONE else View.VISIBLE
        painel.tvParadas.text = c.paradas.mapIndexed { i, p -> context.getString(R.string.corrida_parada_n, i + 1, p.endereco.orEmpty()) }
            .joinToString("\n")

        val resumo = context.getString(R.string.corrida_resumo, MapaUtil.distancia(c.distanciaMetros), MapaUtil.duracao(c.duracaoSegundos))
        painel.tvResumo.text = if (c.quartoPassageiro) resumo + "\n" + context.getString(R.string.corrida_quatro_passageiros) else resumo
        // Antes do aceite o valor ainda não tem o deslocamento do motorista.
        painel.tvValorRotulo.setText(
            when {
                c.status == StatusCorrida.PROCURANDO -> R.string.corrida_valor_a_partir
                ehMotorista -> R.string.corrida_valor_receber
                else -> R.string.corrida_valor_pagar
            }
        )
        painel.tvValor.text = MapaUtil.valor(c.valor)

        // Descritivo do cálculo no fim da corrida.
        val calculo = c.calculo
        val mostrarCalculo = c.status == StatusCorrida.FINALIZADA && calculo != null
        painel.tvCalculo.visibility = if (mostrarCalculo) View.VISIBLE else View.GONE
        if (mostrarCalculo && calculo != null) painel.tvCalculo.text = DescritivoCorrida.texto(context, calculo, ehMotorista)

        // Botões.
        val podeParada = !ehMotorista && emCurso && c.paradas.size < MAX_PARADAS
        painel.btnSecundario.visibility = if (podeParada) View.VISIBLE else View.GONE
        if (podeParada) {
            painel.btnSecundario.text = context.getString(R.string.corrida_adicionar_parada, MapaUtil.valor(viewModel.tarifa.value.valorPorParada))
        }
        val (principalVisivel, principalTexto) = when {
            !c.ativa -> true to R.string.ok
            ehMotorista && c.status == StatusCorrida.ACEITA -> true to R.string.corrida_m_embarcou
            ehMotorista && c.status == StatusCorrida.EM_ANDAMENTO -> true to R.string.corrida_m_finalizar
            else -> false to R.string.ok
        }
        painel.btnPrincipal.visibility = if (principalVisivel) View.VISIBLE else View.GONE
        painel.btnPrincipal.setText(principalTexto)
        val podeCancelar = if (ehMotorista) c.status == StatusCorrida.ACEITA else c.status in setOf(StatusCorrida.PROCURANDO, StatusCorrida.ACEITA)
        painel.btnCancelar.visibility = if (podeCancelar) View.VISIBLE else View.GONE
    }

    private fun textosPassageiro(c: Corrida): Pair<Int, String> = when (c.status) {
        StatusCorrida.PROCURANDO -> R.string.corrida_p_procurando to context.getString(R.string.corrida_p_procurando_msg)
        StatusCorrida.ACEITA -> R.string.corrida_p_a_caminho to context.getString(R.string.corrida_p_a_caminho_msg)
        StatusCorrida.EM_ANDAMENTO -> R.string.corrida_p_em_viagem to context.getString(R.string.corrida_p_em_viagem_msg)
        StatusCorrida.FINALIZADA -> R.string.corrida_p_finalizada to context.getString(R.string.corrida_p_finalizada_msg, MapaUtil.valor(c.valor))
        else -> R.string.corrida_cancelada to context.getString(
            if (c.canceladaPor == "motorista") R.string.corrida_cancelada_pelo_motorista else R.string.corrida_cancelada_por_voce
        )
    }

    private fun textosMotorista(c: Corrida): Pair<Int, String> = when (c.status) {
        StatusCorrida.ACEITA -> R.string.corrida_m_buscar to context.getString(R.string.corrida_m_buscar_msg)
        StatusCorrida.EM_ANDAMENTO -> R.string.corrida_m_em_viagem to context.getString(R.string.corrida_m_em_viagem_msg)
        StatusCorrida.FINALIZADA -> R.string.corrida_m_finalizada to context.getString(R.string.corrida_m_finalizada_msg, MapaUtil.valor(c.valor))
        StatusCorrida.CANCELADA -> R.string.corrida_cancelada to context.getString(
            if (c.canceladaPor == "passageiro") R.string.corrida_cancelada_pelo_passageiro else R.string.corrida_cancelada_por_voce
        )
        else -> R.string.corrida_m_buscar to ""
    }

    // ===================== Mapa =====================

    // Rota certa pra cada momento: até o passageiro (aceita) ou a viagem.
    // Pontos fixos só redesenham quando algo muda; o carro só se move.
    fun desenhar(mapa: GoogleMap, c: Corrida, folgaPx: Int) {
        val origem = c.origem ?: return
        val destino = c.destino ?: return
        val rota = if (c.status == StatusCorrida.ACEITA && !c.polylineDeslocamento.isNullOrBlank()) c.polylineDeslocamento else c.polyline
        val chave = "${c.id}|$rota|${c.paradas.size}"
        if (desenhoAtual != chave) {
            mapa.clear()
            carro = null
            MapaUtil.desenharRota(context, mapa, rota)
            MapaUtil.marcadorPartida(mapa, origem)
            c.paradas.forEach { MapaUtil.marcadorParada(mapa, it) }
            MapaUtil.marcadorDestino(mapa, destino)
            desenhoAtual = chave
        }

        val posicaoCarro = c.motoristaLocal?.takeIf { c.status in setOf(StatusCorrida.ACEITA, StatusCorrida.EM_ANDAMENTO) }
        if (posicaoCarro != null) {
            if (carro == null) carro = MapaUtil.marcadorCarro(mapa, posicaoCarro) else carro?.position = posicaoCarro.latLng()
        } else {
            carro?.remove()
            carro = null
        }

        // Motorista em curso: câmera acompanha o carro (navegação dentro do app).
        if (ehMotorista && posicaoCarro != null) {
            mapa.animateCamera(CameraUpdateFactory.newLatLngZoom(posicaoCarro.latLng(), ZOOM_NAVEGACAO))
            enquadradoEm = c.status
            return
        }
        if (enquadradoEm != c.status) {
            enquadradoEm = c.status
            val pontos: List<Local> = when (c.status) {
                StatusCorrida.ACEITA -> listOfNotNull(posicaoCarro, origem)
                StatusCorrida.EM_ANDAMENTO -> listOfNotNull(posicaoCarro, destino) + c.paradas
                else -> listOf(origem, destino) + c.paradas
            }
            MapaUtil.enquadrar(mapa, pontos, folgaPx)
        }
    }

    // A tela volta a desenhar o próprio mapa (corrida fechada).
    fun limparDesenho() {
        desenhoAtual = null
        enquadradoEm = null
        carro = null
    }

    // ===================== Confirmações =====================

    private fun confirmarFinalizar(c: Corrida) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.corrida_m_finalizar)
            .setMessage(context.getString(R.string.corrida_m_finalizar_msg, MapaUtil.valor(c.valor)))
            .setPositiveButton(R.string.corrida_m_finalizar) { _, _ -> viewModel.finalizar() }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun confirmarCancelamento() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.corrida_cancelar_titulo)
            .setMessage(if (ehMotorista) R.string.corrida_m_cancelar_msg else R.string.corrida_cancelar_msg)
            .setPositiveButton(R.string.corrida_cancelar) { _, _ -> viewModel.cancelar() }
            .setNegativeButton(R.string.corrida_manter, null)
            .show()
    }

    private companion object {
        // Precisa bater com MAX_PARADAS em functions/corridas.js.
        const val MAX_PARADAS = 3
        const val ZOOM_NAVEGACAO = 16.5f
    }
}
