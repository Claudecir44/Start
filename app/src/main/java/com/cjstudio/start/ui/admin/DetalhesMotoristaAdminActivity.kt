package com.cjstudio.start.ui.admin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import coil3.load
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Motorista
import com.cjstudio.start.data.model.StatusMotorista
import com.cjstudio.start.databinding.ActivityDetalhesMotoristaAdminBinding
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.ui.common.mostrarMensagem
import com.cjstudio.start.util.CpfUtil
import com.cjstudio.start.util.SexoUtil
import com.cjstudio.start.util.Validacao
import com.cjstudio.start.util.carregarFotoRedonda
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DetalhesMotoristaAdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalhesMotoristaAdminBinding
    private val viewModel: DetalhesMotoristaAdminViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalhesMotoristaAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topo.tvTitulo.setText(R.string.admin_analise_titulo)
        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.btnAprovar.setOnClickListener { viewModel.definirStatus(StatusMotorista.APROVADO, null) }
        binding.btnReprovar.setOnClickListener { pedirMotivo(StatusMotorista.REPROVADO, R.string.admin_reprovar) }
        binding.btnBloquear.setOnClickListener { pedirMotivo(StatusMotorista.BLOQUEADO, R.string.admin_bloquear) }

        coletar(viewModel.motorista) { motorista -> if (motorista != null) mostrar(motorista) }
        coletar(viewModel.salvando) { salvando ->
            binding.progressBar.visibility = if (salvando) View.VISIBLE else View.GONE
            listOf(binding.btnAprovar, binding.btnReprovar, binding.btnBloquear).forEach { it.isEnabled = !salvando }
        }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                is DetalhesMotoristaAdminViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
                DetalhesMotoristaAdminViewModel.Evento.Fechar -> finish()
            }
        }
    }

    private fun mostrar(m: Motorista) {
        binding.ivFoto.carregarFotoRedonda(m.fotoUrl)
        binding.tvNome.text = m.nomeCompleto
        val idade = m.dataNascimento?.let { Validacao.idade(it) }
        binding.tvDadosPessoais.text = getString(
            R.string.admin_detalhe_pessoal,
            CpfUtil.formatar(m.cpf),
            m.dataNascimento.orEmpty(),
            idade?.toString() ?: "?",
            SexoUtil.rotulo(this, m.sexo),
            m.telefone.orEmpty(),
            m.email.orEmpty()
        )
        binding.tvCnh.text = getString(R.string.admin_detalhe_cnh, m.cnhNumero.orEmpty(), m.cnhCategoria.orEmpty(), m.cnhValidade.orEmpty())
        val v = m.veiculo
        binding.tvVeiculo.text = getString(
            R.string.admin_detalhe_veiculo,
            v?.marca.orEmpty(), v?.modelo.orEmpty(), v?.ano?.toString().orEmpty(), v?.cor.orEmpty(), v?.placa.orEmpty()
        )
        mostrarDocumento(binding.ivCnh, m.cnhFotoUrl)
        mostrarDocumento(binding.ivCrlv, m.crlvFotoUrl)

        binding.tvStatusAtual.text = getString(
            R.string.admin_status_atual,
            getString(
                when (m.status) {
                    StatusMotorista.APROVADO -> R.string.status_aprovado
                    StatusMotorista.REPROVADO -> R.string.status_reprovado
                    StatusMotorista.BLOQUEADO -> R.string.status_bloqueado
                    else -> R.string.status_em_analise
                }
            )
        ) + (m.motivoStatus?.let { "\n" + getString(R.string.status_motivo, it) } ?: "")

        binding.btnAprovar.setText(if (m.status == StatusMotorista.BLOQUEADO) R.string.admin_desbloquear else R.string.admin_aprovar)
        binding.btnAprovar.visibility = if (m.status == StatusMotorista.APROVADO) View.GONE else View.VISIBLE
        binding.btnReprovar.visibility = if (m.status == StatusMotorista.EM_ANALISE) View.VISIBLE else View.GONE
        binding.btnBloquear.visibility = if (m.status == StatusMotorista.BLOQUEADO) View.GONE else View.VISIBLE
    }

    // Tocar no documento abre a imagem inteira (navegador/galeria) pra conferir.
    private fun mostrarDocumento(view: android.widget.ImageView, url: String?) {
        if (url.isNullOrEmpty()) return
        view.load(url)
        view.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun pedirMotivo(status: String, titulo: Int) {
        val campo = EditText(this).apply {
            setHint(R.string.admin_motivo_hint)
            minLines = 2
        }
        val margem = (20 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(margem, margem / 2, margem, 0)
            addView(campo)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setView(container)
            .setPositiveButton(titulo) { _, _ -> viewModel.definirStatus(status, campo.text.toString()) }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    companion object {
        const val EXTRA_UID = "uid"
    }
}
