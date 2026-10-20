package com.cjstudio.start.ui.passageiro

import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.start.R
import com.cjstudio.start.databinding.ActivityCadastroPassageiroBinding
import com.cjstudio.start.ui.common.Navegacao
import com.cjstudio.start.ui.common.abrirTermos
import com.cjstudio.start.ui.common.avisarConfirmacaoEmail
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.ui.common.configurar
import com.cjstudio.start.ui.common.mostrarMensagem
import com.cjstudio.start.ui.common.sexoSelecionado
import com.cjstudio.start.util.FotoUtil
import com.cjstudio.start.util.ajustarPaddingParaTeclado
import com.cjstudio.start.util.carregarFotoRedonda
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CadastroPassageiroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCadastroPassageiroBinding
    private val viewModel: CadastroPassageiroViewModel by viewModels()

    private val seletorFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.definirFoto(FotoUtil.copiarParaTemporario(this, uri) ?: uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroPassageiroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.scrollRoot.ajustarPaddingParaTeclado()
        binding.dados.configurar(viewModel.completandoCadastro)

        binding.topo.tvTitulo.setText(R.string.cadastro_titulo_passageiro)
        binding.topo.btnVoltar.setOnClickListener { finish() }
        val escolherFoto = View.OnClickListener {
            seletorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.foto.ivFoto.setOnClickListener(escolherFoto)
        binding.foto.tvEscolherFoto.setOnClickListener(escolherFoto)
        binding.envio.tvTermos.setOnClickListener { abrirTermos() }
        binding.envio.btnCadastrar.setOnClickListener { enviar() }

        coletar(viewModel.foto) { uri ->
            if (uri != null) binding.foto.ivFoto.carregarFotoRedonda(uri.toString())
        }
        coletar(viewModel.enviando) { enviando ->
            binding.envio.progressBar.visibility = if (enviando) View.VISIBLE else View.GONE
            binding.envio.btnCadastrar.isEnabled = !enviando
            binding.envio.btnCadastrar.setText(if (enviando) R.string.cadastro_enviando else R.string.cadastro_botao)
        }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                is CadastroPassageiroViewModel.Evento.ConfirmarEmail ->
                    avisarConfirmacaoEmail(evento.email) { Navegacao.irLimpando(this, Navegacao.telaLogin()) }
                CadastroPassageiroViewModel.Evento.IrParaInicio -> Navegacao.irLimpando(this, Navegacao.telaInicial())
                is CadastroPassageiroViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    private fun enviar() {
        val d = binding.dados
        viewModel.cadastrar(
            CadastroPassageiroViewModel.Formulario(
                nomeCompleto = d.etNome.text.toString(),
                cpf = d.etCpf.text.toString(),
                telefone = d.etTelefone.text.toString(),
                email = d.etEmail.text.toString(),
                senha = d.etSenha.text.toString(),
                confirmarSenha = d.etConfirmarSenha.text.toString(),
                sexo = d.sexoSelecionado(),
                aceitouTermos = binding.envio.cbTermos.isChecked
            )
        )
    }
}
