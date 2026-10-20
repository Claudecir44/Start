package com.cjstudio.start.ui.motorista

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import coil3.load
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
import com.cjstudio.start.util.mascaraData
import com.cjstudio.start.util.mascaraPlaca
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CadastroMotoristaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCadastroMotoristaBinding
    private val viewModel: CadastroMotoristaViewModel by viewModels()

    // Qual foto o seletor aberto vai preencher.
    private var fotoPedida = Foto.PERFIL

    private val seletorFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.definirFoto(fotoPedida, FotoUtil.copiarParaTemporario(this, uri) ?: uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroMotoristaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.scrollRoot.ajustarPaddingParaTeclado()
        binding.dados.configurar(viewModel.completandoCadastro)
        binding.etNascimento.mascaraData()
        binding.etCnhValidade.mascaraData()
        binding.etPlaca.mascaraPlaca()
        binding.spCnhCategoria.adapter = ArrayAdapter(
            this, R.layout.item_spinner, listOf(getString(R.string.motorista_cnh_categoria_hint)) + CadastroMotoristaViewModel.CATEGORIAS_CNH
        ).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }

        binding.topo.tvTitulo.setText(R.string.cadastro_titulo_motorista)
        binding.fotoCnh.tvDocumento.setText(R.string.motorista_foto_cnh)
        binding.fotoCrlv.tvDocumento.setText(R.string.motorista_foto_crlv)
        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.foto.ivFoto.setOnClickListener { escolher(Foto.PERFIL) }
        binding.foto.tvEscolherFoto.setOnClickListener { escolher(Foto.PERFIL) }
        binding.fotoCnh.root.setOnClickListener { escolher(Foto.CNH) }
        binding.fotoCrlv.root.setOnClickListener { escolher(Foto.CRLV) }
        binding.envio.tvTermos.setOnClickListener { abrirTermos() }
        binding.envio.btnCadastrar.setOnClickListener { enviar() }

        coletar(viewModel.fotos) { fotos ->
            fotos[Foto.PERFIL]?.let { binding.foto.ivFoto.carregarFotoRedonda(it.toString()) }
            fotos[Foto.CNH]?.let {
                binding.fotoCnh.ivDocumento.load(it)
                binding.fotoCnh.tvDocumento.setText(R.string.motorista_foto_trocar)
            }
            fotos[Foto.CRLV]?.let {
                binding.fotoCrlv.ivDocumento.load(it)
                binding.fotoCrlv.tvDocumento.setText(R.string.motorista_foto_trocar)
            }
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
        // Posição 0 do spinner é o texto "Categoria".
        val categoria = if (binding.spCnhCategoria.selectedItemPosition > 0) binding.spCnhCategoria.selectedItem.toString() else ""
        viewModel.cadastrar(
            CadastroMotoristaViewModel.Formulario(
                nomeCompleto = d.etNome.text.toString(),
                cpf = d.etCpf.text.toString(),
                dataNascimento = binding.etNascimento.text.toString(),
                telefone = d.etTelefone.text.toString(),
                email = d.etEmail.text.toString(),
                senha = d.etSenha.text.toString(),
                confirmarSenha = d.etConfirmarSenha.text.toString(),
                sexo = d.sexoSelecionado(),
                cnhNumero = binding.etCnhNumero.text.toString(),
                cnhCategoria = categoria,
                cnhValidade = binding.etCnhValidade.text.toString(),
                marca = binding.etMarca.text.toString(),
                modelo = binding.etModelo.text.toString(),
                ano = binding.etAno.text.toString(),
                cor = binding.etCor.text.toString(),
                placa = binding.etPlaca.text.toString(),
                aceitouTermos = binding.envio.cbTermos.isChecked
            )
        )
    }
}
