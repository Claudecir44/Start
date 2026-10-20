package com.cjstudio.start.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.start.R
import com.cjstudio.start.databinding.ActivityLoginBinding
import com.cjstudio.start.ui.common.Navegacao
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.ui.common.mostrarMensagem
import com.cjstudio.start.util.AppTipo
import com.cjstudio.start.util.ajustarPaddingParaTeclado
import com.cjstudio.start.util.habilitarToggleSenha
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.scrollRoot.ajustarPaddingParaTeclado()
        binding.etSenha.habilitarToggleSenha()

        binding.tvSubtitulo.setText(if (AppTipo.ehMotorista) R.string.login_subtitulo_motorista else R.string.login_subtitulo_passageiro)

        binding.btnEntrar.setOnClickListener {
            viewModel.entrar(binding.etEmail.text.toString().trim(), binding.etSenha.text.toString())
        }
        binding.tvCriarConta.setOnClickListener {
            startActivity(Intent(this, Navegacao.telaCadastro()))
        }
        binding.tvEsqueciSenha.setOnClickListener {
            viewModel.recuperarSenha(binding.etEmail.text.toString().trim())
        }

        coletar(viewModel.carregando) { carregando ->
            binding.progressBar.visibility = if (carregando) View.VISIBLE else View.GONE
            binding.btnEntrar.isEnabled = !carregando
        }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                LoginViewModel.Evento.IrParaInicio -> Navegacao.irLimpando(this, Navegacao.telaInicial())
                LoginViewModel.Evento.CompletarCadastro -> perguntarCompletarCadastro()
                is LoginViewModel.Evento.EmailNaoConfirmado -> avisarEmailNaoConfirmado(evento.email)
                is LoginViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    // A conta existe (ex.: já é passageiro) mas ainda não tem perfil neste app.
    private fun perguntarCompletarCadastro() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.login_completar_titulo)
            .setMessage(if (AppTipo.ehMotorista) R.string.login_completar_motorista else R.string.login_completar_passageiro)
            .setPositiveButton(R.string.login_completar_sim) { _, _ ->
                startActivity(Intent(this, Navegacao.telaCadastro()).putExtra(EXTRA_COMPLETAR_CADASTRO, true))
            }
            .setNegativeButton(R.string.cancelar) { _, _ -> viewModel.sairDaConta() }
            .setCancelable(false)
            .show()
    }

    private fun avisarEmailNaoConfirmado(email: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.verificar_email_titulo)
            .setMessage(getString(R.string.verificar_email_login, email))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    companion object {
        // Cadastro aberto com a conta já logada: esconde e-mail/senha.
        const val EXTRA_COMPLETAR_CADASTRO = "completarCadastro"
    }
}
