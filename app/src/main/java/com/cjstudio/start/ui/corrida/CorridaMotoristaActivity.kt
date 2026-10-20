package com.cjstudio.start.ui.corrida

import android.os.Bundle
import android.view.View
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Corrida
import com.cjstudio.start.data.model.StatusCorrida
import com.cjstudio.start.util.MapaUtil
import com.cjstudio.start.util.carregarFotoRedonda
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

// Lado do motorista: buscar passageiro → embarcou → levar → finalizar e
// receber (dinheiro/Pix direto do passageiro).
@AndroidEntryPoint
class CorridaMotoristaActivity : CorridaBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.btnCancelar.setOnClickListener { confirmarCancelamento() }
        binding.btnLigar.setOnClickListener { MapaUtil.ligar(this, viewModel.corrida.value?.passageiroTelefone) }
        // Navegar: até o passageiro antes do embarque, até o destino depois.
        binding.btnSecundario.setOnClickListener {
            val c = viewModel.corrida.value ?: return@setOnClickListener
            val alvo = if (c.status == StatusCorrida.ACEITA) c.origem else c.destino
            alvo?.let { MapaUtil.abrirNavegacao(this, it) }
        }
        binding.btnPrincipal.setOnClickListener {
            when (viewModel.corrida.value?.status) {
                StatusCorrida.ACEITA -> viewModel.embarcou()
                StatusCorrida.EM_ANDAMENTO -> confirmarFinalizar()
                else -> finish()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.iniciarEnvioPosicao()
    }

    override fun onStop() {
        super.onStop()
        viewModel.pararEnvioPosicao()
    }

    override fun mostrar(corrida: Corrida) {
        val (titulo, detalhe) = when (corrida.status) {
            StatusCorrida.ACEITA -> R.string.corrida_m_buscar to getString(R.string.corrida_m_buscar_msg)
            StatusCorrida.EM_ANDAMENTO -> R.string.corrida_m_em_viagem to getString(R.string.corrida_m_em_viagem_msg)
            StatusCorrida.FINALIZADA -> R.string.corrida_m_finalizada to getString(R.string.corrida_m_finalizada_msg, MapaUtil.valor(corrida.valor))
            StatusCorrida.CANCELADA -> R.string.corrida_cancelada to getString(
                if (corrida.canceladaPor == "passageiro") R.string.corrida_cancelada_pelo_passageiro else R.string.corrida_cancelada_por_voce
            )
            else -> R.string.corrida_m_buscar to ""
        }
        binding.tvSituacao.setText(titulo)
        binding.tvDetalhe.text = detalhe

        binding.blocoPessoa.visibility = if (corrida.ativa) View.VISIBLE else View.GONE
        binding.ivPessoa.carregarFotoRedonda(corrida.passageiroFotoUrl)
        binding.tvPessoaNome.text = corrida.passageiroNome
        binding.tvPessoaInfo.setText(R.string.corrida_passageiro)

        val emCurso = corrida.status in setOf(StatusCorrida.ACEITA, StatusCorrida.EM_ANDAMENTO)
        binding.btnSecundario.visibility = if (emCurso) View.VISIBLE else View.GONE
        binding.btnSecundario.setText(
            if (corrida.status == StatusCorrida.ACEITA) R.string.corrida_m_navegar_partida else R.string.corrida_m_navegar_destino
        )
        binding.btnPrincipal.visibility = View.VISIBLE
        binding.btnPrincipal.setText(
            when (corrida.status) {
                StatusCorrida.ACEITA -> R.string.corrida_m_embarcou
                StatusCorrida.EM_ANDAMENTO -> R.string.corrida_m_finalizar
                else -> R.string.ok
            }
        )
        binding.btnCancelar.visibility = if (corrida.status == StatusCorrida.ACEITA) View.VISIBLE else View.GONE
    }

    private fun confirmarFinalizar() {
        val valor = MapaUtil.valor(viewModel.corrida.value?.valor ?: 0.0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.corrida_m_finalizar)
            .setMessage(getString(R.string.corrida_m_finalizar_msg, valor))
            .setPositiveButton(R.string.corrida_m_finalizar) { _, _ -> viewModel.finalizar() }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun confirmarCancelamento() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.corrida_cancelar_titulo)
            .setMessage(R.string.corrida_m_cancelar_msg)
            .setPositiveButton(R.string.corrida_cancelar) { _, _ -> viewModel.cancelar() }
            .setNegativeButton(R.string.corrida_manter, null)
            .show()
    }
}
