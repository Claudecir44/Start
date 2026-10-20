package com.cjstudio.start.ui.passageiro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.start.R
import com.cjstudio.start.databinding.ActivityHomePassageiroBinding
import com.cjstudio.start.ui.common.Navegacao
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.util.carregarFotoRedonda
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomePassageiroActivity : AppCompatActivity() {

    private val viewModel: HomePassageiroViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityHomePassageiroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cabecalho.btnSair.setOnClickListener { viewModel.sair() }
        val abrirCadastro = View.OnClickListener { startActivity(Intent(this, EditarPassageiroActivity::class.java)) }
        binding.cabecalho.tvMeuPerfil.setOnClickListener(abrirCadastro)
        binding.cabecalho.ivFoto.setOnClickListener(abrirCadastro)
        binding.cardParaOnde.setOnClickListener {
            Toast.makeText(this, R.string.em_breve_corridas, Toast.LENGTH_LONG).show()
        }

        coletar(viewModel.passageiro) { passageiro ->
            if (passageiro == null) return@coletar
            binding.cabecalho.tvOla.text = getString(R.string.home_ola, passageiro.nomeCompleto.orEmpty().substringBefore(" "))
            binding.cabecalho.ivFoto.carregarFotoRedonda(passageiro.fotoUrl)
        }
        coletar(viewModel.saiu) { Navegacao.irLimpando(this, Navegacao.telaLogin()) }
    }

    // Volta do "Meu cadastro" já com nome/foto novos.
    override fun onResume() {
        super.onResume()
        viewModel.carregar()
    }
}
