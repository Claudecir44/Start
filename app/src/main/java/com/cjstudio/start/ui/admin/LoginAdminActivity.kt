package com.cjstudio.start.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.start.databinding.ActivityLoginAdminBinding
import com.cjstudio.start.ui.common.Navegacao
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.ui.common.mostrarMensagem
import com.cjstudio.start.util.ajustarPaddingParaTeclado
import com.cjstudio.start.util.habilitarToggleSenha
import dagger.hilt.android.AndroidEntryPoint

// Login do app Admin Start — mesmo Firebase Auth, mas só entra quem tem
// documento em admins/{uid}.
@AndroidEntryPoint
class LoginAdminActivity : AppCompatActivity() {

    private val viewModel: LoginAdminViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLoginAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.scrollRoot.ajustarPaddingParaTeclado()
        binding.etSenha.habilitarToggleSenha()

        binding.btnEntrar.setOnClickListener {
            viewModel.entrar(binding.etEmail.text.toString().trim(), binding.etSenha.text.toString())
        }
        // Primeiro admin de todos (ou novo admin sem estar logado): só com a
        // senha do master.
        binding.tvCadastrarAdmin.setOnClickListener {
            startActivity(Intent(this, CadastroAdminActivity::class.java))
        }

        coletar(viewModel.carregando) { carregando ->
            binding.progressBar.visibility = if (carregando) View.VISIBLE else View.GONE
            binding.btnEntrar.isEnabled = !carregando
        }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                LoginAdminViewModel.Evento.IrParaPainel -> Navegacao.irLimpando(this, PainelAdminActivity::class.java)
                is LoginAdminViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }
}
