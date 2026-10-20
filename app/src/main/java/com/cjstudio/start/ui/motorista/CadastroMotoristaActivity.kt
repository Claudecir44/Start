package com.cjstudio.start.ui.motorista

import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.start.R
import com.cjstudio.start.databinding.ActivityCadastroMotoristaBinding
import com.cjstudio.start.ui.common.Navegacao
import com.cjstudio.start.ui.common.abrirTermos
import com.cjstudio.start.ui.common.avisarConfirmacaoEmail
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.ui.common.configurar
import com.cjstudio.start.ui.common.mostrarMensagem
import com.cjstudio.start.ui.common.sexoSelecionado
import com.cjstudio.start.ui.motorista.CadastroMotoristaViewModel.Foto
import com.cjstudio.start.util.FotoUtil
import com.cjstudio.start.util.ajustarPaddingParaTeclado
import com.cjstudio.start.util.carregarFotoRedonda
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CadastroMotoristaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCadastroMotoristaBinding
    private val viewModel: CadastroMotoristaViewModel by viewModels()

    // Qual foto o seletor aberto vai preencher.
    private var fotoPedida = Foto.PERFIL

    private val seletorFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.definirFoto(fotoPedida, FotoUtil.prepararParaEnvio(this, uri) ?: uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroMotoristaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.scrollRoot.ajustarPaddingParaTeclado()
        binding.dados.configurar(viewModel.completandoCadastro)
        binding.motorista.configurar()

        binding.topo.tvTitulo.setText(R.string.cadastro_titulo_motorista)
        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.foto.ivFoto.setOnClickListener { escolher(Foto.PERFIL) }
        binding.foto.tvEscolherFoto.setOnClickListener { escolher(Foto.PERFIL) }
        binding.motorista.fotoCnh.root.setOnClickListener { escolher(Foto.CNH) }
        binding.motorista.fotoCrlv.root.setOnClickListener { escolher(Foto.CRLV) }
        binding.envio.tvTermos.setOnClickListener { abrirTermos() }
        binding.envio.btnCadastrar.setOnClickListener { enviar() }

        coletar(viewModel.fotos) { fotos ->
            fotos[Foto.PERFIL]?.let { binding.foto.ivFoto.carregarFotoRedonda(it.toString()) }
            binding.motorista.mostrarFotoNova(fotos[Foto.CNH], fotos[Foto.CRLV])
        }
        coletar(viewModel.enviando) { enviando ->
            binding.envio.progressBar.visibility = if (enviando) View.VISIBLE else View.GONE
            binding.envio.btnCadastrar.isEnabled = !enviando
            binding.envio.btnCadastrar.setText(if (enviando) R.string.cadastro_enviando else R.string.motorista_botao_enviar)
        }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                is CadastroMotoristaViewModel.Evento.ConfirmarEmail ->
                    avisarConfirmacaoEmail(evento.email) { Navegacao.irLimpando(this, Navegacao.telaLogin()) }
                CadastroMotoristaViewModel.Evento.IrParaInicio -> Navegacao.irLimpando(this, Navegacao.telaInicial())
                is CadastroMotoristaViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    private fun escolher(foto: Foto) {
        fotoPedida = foto
        seletorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun enviar() {
        val d = binding.dados
        val m = binding.motorista
        viewModel.cadastrar(
            CadastroMotoristaViewModel.Formulario(
                nomeCompleto = d.etNome.text.toString(),
                cpf = d.etCpf.text.toString(),
                dataNascimento = m.etNascimento.text.toString(),
                telefone = d.etTelefone.text.toString(),
                email = d.etEmail.text.toString(),
                senha = d.etSenha.text.toString(),
                confirmarSenha = d.etConfirmarSenha.text.toString(),
                sexo = d.sexoSelecionado(),
                cnhNumero = m.etCnhNumero.text.toString(),
                cnhCategoria = m.categoriaSelecionada(),
                cnhValidade = m.etCnhValidade.text.toString(),
                marca = m.etMarca.text.toString(),
                modelo = m.etModelo.text.toString(),
                ano = m.etAno.text.toString(),
                cor = m.etCor.text.toString(),
                placa = m.etPlaca.text.toString(),
                aceitouTermos = binding.envio.cbTermos.isChecked
            )
        )
    }
}
