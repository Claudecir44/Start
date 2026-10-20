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

// Lado do passageiro: procurando → motorista a caminho → em viagem → fim.
@AndroidEntryPoint
class CorridaPassageiroActivity : CorridaBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.btnCancelar.setOnClickListener { confirmarCancelamento() }
        binding.btnPrincipal.setOnClickListener { finish() }
        binding.btnLigar.setOnClickListener { MapaUtil.ligar(this, viewModel.corrida.value?.motoristaTelefone) }
    }

    override fun mostrar(corrida: Corrida) {
        val (titulo, detalhe) = when (corrida.status) {
            StatusCorrida.PROCURANDO -> R.string.corrida_p_procurando to getString(R.string.corrida_p_procurando_msg)
            StatusCorrida.ACEITA -> R.string.corrida_p_a_caminho to getString(R.string.corrida_p_a_caminho_msg)
            StatusCorrida.EM_ANDAMENTO -> R.string.corrida_p_em_viagem to getString(R.string.corrida_p_em_viagem_msg)
            StatusCorrida.FINALIZADA -> R.string.corrida_p_finalizada to getString(R.string.corrida_p_finalizada_msg, MapaUtil.valor(corrida.valor))
            else -> R.string.corrida_cancelada to getString(
                if (corrida.canceladaPor == "motorista") R.string.corrida_cancelada_pelo_motorista else R.string.corrida_cancelada_por_voce
            )
        }
        binding.tvSituacao.setText(titulo)
        binding.tvDetalhe.text = detalhe

        val temMotorista = corrida.motoristaId != null && corrida.ativa
        binding.blocoPessoa.visibility = if (temMotorista) View.VISIBLE else View.GONE
        if (temMotorista) {
            binding.ivPessoa.carregarFotoRedonda(corrida.motoristaFotoUrl)
            binding.tvPessoaNome.text = corrida.motoristaNome
            binding.tvPessoaInfo.text = corrida.veiculo?.let { "${it.descricao()}  ·  ${it.placa.orEmpty()}" }
        }
        binding.btnCancelar.visibility =
            if (corrida.status in setOf(StatusCorrida.PROCURANDO, StatusCorrida.ACEITA)) View.VISIBLE else View.GONE
        // Fim (finalizada/cancelada): só o OK pra voltar ao início.
        binding.btnPrincipal.visibility = if (corrida.ativa) View.GONE else View.VISIBLE
        binding.btnPrincipal.setText(R.string.ok)
        binding.btnSecundario.visibility = View.GONE
    }

    private fun confirmarCancelamento() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.corrida_cancelar_titulo)
            .setMessage(R.string.corrida_cancelar_msg)
            .setPositiveButton(R.string.corrida_cancelar) { _, _ -> viewModel.cancelar() }
            .setNegativeButton(R.string.corrida_manter, null)
            .show()
    }
}
