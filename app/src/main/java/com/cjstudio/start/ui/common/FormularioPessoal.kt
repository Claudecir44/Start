package com.cjstudio.start.ui.common

import android.app.Activity
import android.content.Intent
import android.view.View
import com.cjstudio.start.R
import com.cjstudio.start.databinding.IncludeDadosPessoaisBinding
import com.cjstudio.start.util.SexoUtil
import com.cjstudio.start.util.habilitarToggleSenha
import com.cjstudio.start.util.mascaraCpf
import com.cjstudio.start.util.mascaraTelefone
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// Bloco de dados pessoais repetido nos cadastros de passageiro e motorista
// (include_dados_pessoais.xml): máscaras, campos de conta e leitura do sexo.
fun IncludeDadosPessoaisBinding.configurar(completandoCadastro: Boolean) {
    etCpf.mascaraCpf()
    etTelefone.mascaraTelefone()
    etSenha.habilitarToggleSenha()
    etConfirmarSenha.habilitarToggleSenha()
    // Conta já existe e está logada: e-mail e senha são os dela.
    blocoConta.visibility = if (completandoCadastro) View.GONE else View.VISIBLE
}

fun IncludeDadosPessoaisBinding.sexoSelecionado(): String? = when (rgSexo.checkedRadioButtonId) {
    R.id.rbHomem -> SexoUtil.HOMEM
    R.id.rbMulher -> SexoUtil.MULHER
    else -> null
}

fun Activity.abrirTermos() {
    startActivity(Intent(this, TermosActivity::class.java))
}

// Aviso depois do cadastro de conta nova: confirmar o e-mail antes de entrar.
fun Activity.avisarConfirmacaoEmail(email: String, aoFechar: () -> Unit) {
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.verificar_email_titulo)
        .setMessage(getString(R.string.verificar_email_cadastro, email))
        .setPositiveButton(R.string.ok) { _, _ -> aoFechar() }
        .setCancelable(false)
        .show()
}
