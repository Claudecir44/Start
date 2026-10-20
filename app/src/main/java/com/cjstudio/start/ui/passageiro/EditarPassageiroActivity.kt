package com.cjstudio.start.ui.passageiro

import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.start.R
import com.cjstudio.start.databinding.ActivityEditarPassageiroBinding
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.ui.common.mostrarMensagem
import com.cjstudio.start.util.CpfUtil
import com.cjstudio.start.util.FotoUtil
import com.cjstudio.start.util.SexoUtil
import com.cjstudio.start.util.ajustarPaddingParaTeclado
import com.cjstudio.start.util.carregarFotoRedonda
import com.cjstudio.start.util.mascaraTelefone
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EditarPassageiroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditarPassageiroBinding
    private val viewModel: EditarPassageiroViewModel by viewModels()
    private var preenchido = false

    private val seletorFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.definirFoto(FotoUtil.prepararParaEnvio(this, uri) ?: uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditarPassageiroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.scrollRoot.ajustarPaddingParaTeclado()
        binding.etTelefone.mascaraTelefone()
        // Formulário já preenchido sobrevive à rotação da tela.
        preenchido = savedInstanceState != null

        binding.topo.tvTitulo.setText(R.string.editar_titulo)
        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.foto.tvEscolherFoto.setText(R.string.editar_trocar_foto)
        val escolherFoto = View.OnClickListener {
            seletorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.foto.ivFoto.setOnClickListener(escolherFoto)
        binding.foto.tvEscolherFoto.setOnClickListener(escolherFoto)
        binding.btnSalvar.setOnClickListener {
            viewModel.salvar(binding.etNome.text.toString(), binding.etTelefone.text.toString())
        }

        coletar(viewModel.passageiro) { p ->
            if (p == null) return@coletar
            if (viewModel.novaFoto.value == null) binding.foto.ivFoto.carregarFotoRedonda(p.fotoUrl)
            binding.tvFixos.text = getString(
                R.string.editar_dados_fixos, CpfUtil.formatar(p.cpf), p.email.orEmpty(), SexoUtil.rotulo(this, p.sexo)
            )
            if (!preenchido) {
                preenchido = true
                binding.etNome.setText(p.nomeCompleto)
                binding.etTelefone.setText(p.telefone)
            }
        }
        coletar(viewModel.novaFoto) { uri -> if (uri != null) binding.foto.ivFoto.carregarFotoRedonda(uri.toString()) }
        coletar(viewModel.salvando) { salvando ->
            binding.progressBar.visibility = if (salvando) View.VISIBLE else View.GONE
            binding.btnSalvar.isEnabled = !salvando
        }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                is EditarPassageiroViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
                EditarPassageiroViewModel.Evento.Salvo -> finish()
            }
        }
    }
}
