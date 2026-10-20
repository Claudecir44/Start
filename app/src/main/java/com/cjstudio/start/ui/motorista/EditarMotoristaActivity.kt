package com.cjstudio.start.ui.motorista

import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Motorista
import com.cjstudio.start.databinding.ActivityEditarMotoristaBinding
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.ui.common.mostrarMensagem
import com.cjstudio.start.ui.motorista.CadastroMotoristaViewModel.Foto
import com.cjstudio.start.util.CpfUtil
import com.cjstudio.start.util.FotoUtil
import com.cjstudio.start.util.SexoUtil
import com.cjstudio.start.util.ajustarPaddingParaTeclado
import com.cjstudio.start.util.carregarFotoRedonda
import com.cjstudio.start.util.mascaraTelefone
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EditarMotoristaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditarMotoristaBinding
    private val viewModel: EditarMotoristaViewModel by viewModels()
    private var fotoPedida = Foto.PERFIL
    private var preenchido = false

    private val seletorFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.definirFoto(fotoPedida, FotoUtil.prepararParaEnvio(this, uri) ?: uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditarMotoristaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.scrollRoot.ajustarPaddingParaTeclado()
        binding.etTelefone.mascaraTelefone()
        binding.motorista.configurar()
        // Formulário já preenchido sobrevive à rotação da tela.
        preenchido = savedInstanceState != null

        binding.topo.tvTitulo.setText(R.string.editar_titulo)
        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.foto.tvEscolherFoto.setText(R.string.editar_trocar_foto)
        binding.foto.ivFoto.setOnClickListener { escolher(Foto.PERFIL) }
        binding.foto.tvEscolherFoto.setOnClickListener { escolher(Foto.PERFIL) }
        binding.motorista.fotoCnh.root.setOnClickListener { escolher(Foto.CNH) }
        binding.motorista.fotoCrlv.root.setOnClickListener { escolher(Foto.CRLV) }
        binding.btnSalvar.setOnClickListener { viewModel.salvar(dadosDaTela()) }

        coletar(viewModel.motorista) { m -> if (m != null) mostrar(m) }
        coletar(viewModel.fotos) { fotos ->
            fotos[Foto.PERFIL]?.let { binding.foto.ivFoto.carregarFotoRedonda(it.toString()) }
            binding.motorista.mostrarFotoNova(fotos[Foto.CNH], fotos[Foto.CRLV])
        }
        coletar(viewModel.salvando) { salvando ->
            binding.progressBar.visibility = if (salvando) View.VISIBLE else View.GONE
            binding.btnSalvar.isEnabled = !salvando
        }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                is EditarMotoristaViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
                is EditarMotoristaViewModel.Evento.ConfirmarNovaAnalise -> confirmarNovaAnalise(evento.dados)
                EditarMotoristaViewModel.Evento.Salvo -> finish()
            }
        }
    }

    private fun mostrar(m: Motorista) {
        if (viewModel.fotos.value[Foto.PERFIL] == null) binding.foto.ivFoto.carregarFotoRedonda(m.fotoUrl)
        binding.tvFixos.text = getString(
            R.string.editar_dados_fixos, CpfUtil.formatar(m.cpf), m.email.orEmpty(), SexoUtil.rotulo(this, m.sexo)
        )
        if (preenchido) return
        preenchido = true
        binding.etNome.setText(m.nomeCompleto)
        binding.etTelefone.setText(m.telefone)
        binding.motorista.preencher(m)
    }

    private fun dadosDaTela() = binding.motorista.dados(binding.etNome.text.toString(), binding.etTelefone.text.toString())

    private fun escolher(foto: Foto) {
        fotoPedida = foto
        seletorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun confirmarNovaAnalise(dados: ValidacaoMotorista.Dados) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.editar_nova_analise_titulo)
            .setMessage(R.string.editar_nova_analise_msg)
            .setPositiveButton(R.string.editar_nova_analise_sim) { _, _ -> viewModel.salvar(dados, confirmado = true) }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }
}
