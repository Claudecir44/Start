package com.cjstudio.start.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Admin
import com.cjstudio.start.databinding.ActivityCadastroAdminBinding
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.ui.common.mostrarMensagem
import com.cjstudio.start.ui.common.texto
import com.cjstudio.start.util.ajustarPaddingParaTeclado
import com.cjstudio.start.util.habilitarToggleSenha
import com.cjstudio.start.util.mascaraCpf
import com.cjstudio.start.util.mascaraTelefone
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CadastroAdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCadastroAdminBinding
    private val viewModel: CadastroAdminViewModel by viewModels()
    private val checkboxesPermissao = mutableMapOf<String, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.scrollRoot.ajustarPaddingParaTeclado()
        binding.etCpf.mascaraCpf()
        binding.etTelefone.mascaraTelefone()
        binding.etSenha.habilitarToggleSenha()
        binding.etConfirmarSenha.habilitarToggleSenha()
        binding.etSenhaMaster.habilitarToggleSenha()

        // Uma caixa por permissão — só aparecem para "Colaborador".
        Admin.CHAVES_PERMISSOES.forEach { (chave, rotulo) ->
            val cb = CheckBox(this).apply {
                text = rotulo
                setTextColor(ContextCompat.getColor(context, R.color.texto))
                buttonTintList = ContextCompat.getColorStateList(context, R.color.amarelo)
            }
            checkboxesPermissao[chave] = cb
            binding.containerPermissoes.addView(cb)
        }
        binding.rgPapel.setOnCheckedChangeListener { _, id ->
            binding.containerPermissoes.visibility = if (id == R.id.rbColaborador) View.VISIBLE else View.GONE
        }

        binding.topo.tvTitulo.setText(R.string.admin_novo_titulo)
        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.btnCadastrar.setOnClickListener { enviar() }

        coletar(viewModel.enviando) { enviando ->
            binding.progressBar.visibility = if (enviando) View.VISIBLE else View.GONE
            binding.btnCadastrar.isEnabled = !enviando
        }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                CadastroAdminViewModel.Evento.Cadastrado -> {
                    mostrarMensagem(texto(R.string.admin_cadastrado))
                    finish()
                }
                is CadastroAdminViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    private fun enviar() {
        viewModel.cadastrar(
            CadastroAdminViewModel.Formulario(
                nome = binding.etNome.text.toString(),
                sobrenome = binding.etSobrenome.text.toString(),
                email = binding.etEmail.text.toString(),
                telefone = binding.etTelefone.text.toString(),
                cpf = binding.etCpf.text.toString(),
                senha = binding.etSenha.text.toString(),
                confirmarSenha = binding.etConfirmarSenha.text.toString(),
                colaborador = binding.rgPapel.checkedRadioButtonId == R.id.rbColaborador,
                permissoes = checkboxesPermissao.mapValues { it.value.isChecked },
                senhaAutorizacao = binding.etSenhaMaster.text.toString()
            )
        )
    }
}
