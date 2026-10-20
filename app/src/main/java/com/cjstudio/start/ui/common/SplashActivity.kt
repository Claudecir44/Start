package com.cjstudio.start.ui.common

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.start.databinding.ActivitySplashBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private val viewModel: SplashViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivitySplashBinding.inflate(layoutInflater).root)

        coletar(viewModel.destino.filterNotNull()) { destino ->
            val tela = if (destino == SplashViewModel.Destino.INICIAL) Navegacao.telaInicial() else Navegacao.telaLogin()
            Navegacao.irLimpando(this, tela)
        }
    }
}
