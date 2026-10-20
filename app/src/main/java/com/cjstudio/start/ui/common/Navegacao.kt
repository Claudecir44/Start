package com.cjstudio.start.ui.common

import android.app.Activity
import android.content.Intent
import com.cjstudio.start.ui.admin.LoginAdminActivity
import com.cjstudio.start.ui.admin.PainelAdminActivity
import com.cjstudio.start.ui.auth.LoginActivity
import com.cjstudio.start.ui.motorista.CadastroMotoristaActivity
import com.cjstudio.start.ui.motorista.HomeMotoristaActivity
import com.cjstudio.start.ui.passageiro.CadastroPassageiroActivity
import com.cjstudio.start.ui.passageiro.HomePassageiroActivity
import com.cjstudio.start.util.AppTipo

// Telas de entrada de cada app num lugar só — Splash, Login e Cadastro usam
// isto em vez de cada um decidir por conta própria qual é o flavor.
object Navegacao {

    fun telaLogin(): Class<out Activity> = if (AppTipo.ehAdmin) LoginAdminActivity::class.java else LoginActivity::class.java

    fun telaInicial(): Class<out Activity> = when {
        AppTipo.ehAdmin -> PainelAdminActivity::class.java
        AppTipo.ehMotorista -> HomeMotoristaActivity::class.java
        else -> HomePassageiroActivity::class.java
    }

    fun telaCadastro(): Class<out Activity> =
        if (AppTipo.ehMotorista) CadastroMotoristaActivity::class.java else CadastroPassageiroActivity::class.java

    // Abre a tela e limpa a pilha (depois do login/logout não tem "voltar").
    fun irLimpando(origem: Activity, destino: Class<out Activity>) {
        origem.startActivity(Intent(origem, destino).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        origem.finish()
    }
}
