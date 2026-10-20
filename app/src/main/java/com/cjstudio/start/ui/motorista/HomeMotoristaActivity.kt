package com.cjstudio.start.ui.motorista

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Motorista
import com.cjstudio.start.data.model.StatusMotorista
import com.cjstudio.start.databinding.ActivityHomeMotoristaBinding
import com.cjstudio.start.ui.common.Navegacao
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.ui.common.mostrarMensagem
import com.cjstudio.start.util.carregarFotoRedonda
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeMotoristaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeMotoristaBinding
    private val viewModel: HomeMotoristaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeMotoristaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cabecalho.btnSair.setOnClickListener { viewModel.sair() }
        binding.btnOnline.setOnClickListener { viewModel.alternarOnline() }

        coletar(viewModel.motorista) { motorista -> if (motorista != null) mostrar(motorista) }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                HomeMotoristaViewModel.Evento.Saiu -> Navegacao.irLimpando(this, Navegacao.telaLogin())
                is HomeMotoristaViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    private fun mostrar(motorista: Motorista) {
        binding.cabecalho.tvOla.text = getString(R.string.home_ola, motorista.nomeCompleto.orEmpty().substringBefore(" "))
        binding.cabecalho.ivFoto.carregarFotoRedonda(motorista.fotoUrl)
        binding.cabecalho.tvLinha2.text = motorista.veiculo?.let { "${it.descricao()}  ·  ${it.placa.orEmpty()}" }.orEmpty()

        val (titulo, mensagem, cor) = when (motorista.status) {
            StatusMotorista.APROVADO -> Triple(R.string.status_aprovado, getString(R.string.status_aprovado_msg), R.color.verde)
            StatusMotorista.REPROVADO -> Triple(R.string.status_reprovado, motivo(motorista, R.string.status_reprovado_msg), R.color.vermelho)
            StatusMotorista.BLOQUEADO -> Triple(R.string.status_bloqueado, motivo(motorista, R.string.status_bloqueado_msg), R.color.vermelho)
            else -> Triple(R.string.status_em_analise, getString(R.string.status_em_analise_msg), R.color.amarelo)
        }
        binding.tvStatusTitulo.setText(titulo)
        binding.tvStatusTitulo.setTextColor(ContextCompat.getColor(this, cor))
        binding.tvStatusMensagem.text = mensagem

        binding.btnOnline.visibility = if (motorista.aprovado) View.VISIBLE else View.GONE
        binding.btnOnline.setText(if (motorista.online) R.string.motorista_ficar_offline else R.string.motorista_ficar_online)
        binding.btnOnline.setBackgroundResource(if (motorista.online) R.drawable.bg_botao_contorno else R.drawable.bg_botao_primario)
        binding.btnOnline.setTextColor(ContextCompat.getColor(this, if (motorista.online) R.color.amarelo else R.color.preto))
        binding.tvSituacaoOnline.visibility = if (motorista.aprovado) View.VISIBLE else View.GONE
        binding.tvSituacaoOnline.setText(if (motorista.online) R.string.motorista_online_msg else R.string.motorista_offline_msg)
    }

    private fun motivo(motorista: Motorista, padrao: Int): String =
        motorista.motivoStatus?.takeIf { it.isNotBlank() }?.let { getString(R.string.status_motivo, it) } ?: getString(padrao)
}
