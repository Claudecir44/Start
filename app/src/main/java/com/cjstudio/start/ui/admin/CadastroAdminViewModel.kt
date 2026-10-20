package com.cjstudio.start.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Admin
import com.cjstudio.start.data.repository.IAdminRepository
import com.cjstudio.start.ui.common.UiTexto
import com.cjstudio.start.ui.common.paraUiTexto
import com.cjstudio.start.ui.common.texto
import com.cjstudio.start.util.CpfUtil
import com.cjstudio.start.util.Validacao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Novo administrador/colaborador. A trava de verdade (senha do master,
// permissão "administradores" de quem chama) é da Cloud Function.
@HiltViewModel
class CadastroAdminViewModel @Inject constructor(
    private val admins: IAdminRepository
) : ViewModel() {

    data class Formulario(
        val nome: String,
        val sobrenome: String,
        val email: String,
        val telefone: String,
        val cpf: String,
        val senha: String,
        val confirmarSenha: String,
        val colaborador: Boolean,
        val permissoes: Map<String, Boolean>,
        val senhaAutorizacao: String
    )

    sealed interface Evento {
        data object Cadastrado : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    private val _enviando = MutableStateFlow(false)
    val enviando: StateFlow<Boolean> = _enviando

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    fun cadastrar(form: Formulario) {
        if (_enviando.value) return
        validar(form)?.let {
            _eventos.trySend(Evento.Mensagem(it))
            return
        }
        _enviando.value = true
        viewModelScope.launch {
            val resultado = admins.cadastrarAdmin(
                nome = form.nome.trim(),
                sobrenome = form.sobrenome.trim(),
                email = form.email.trim(),
                telefone = form.telefone,
                cpf = CpfUtil.somenteDigitos(form.cpf),
                senha = form.senha,
                role = if (form.colaborador) "colaborador" else "admin",
                // Admin (não colaborador) tem tudo liberado.
                permissoes = if (form.colaborador) form.permissoes else Admin.CHAVES_PERMISSOES.associate { it.first to true },
                senhaAutorizacao = form.senhaAutorizacao
            )
            _enviando.value = false
            _eventos.send(resultado.fold({ Evento.Cadastrado }, { Evento.Mensagem(it.paraUiTexto()) }))
        }
    }

    private fun validar(form: Formulario): UiTexto? = when {
        form.nome.isBlank() || form.sobrenome.isBlank() -> texto(R.string.cadastro_erro_nome)
        !Validacao.email(form.email.trim()) -> texto(R.string.cadastro_erro_email)
        !Validacao.telefone(form.telefone) -> texto(R.string.cadastro_erro_telefone)
        !CpfUtil.valido(form.cpf) -> texto(R.string.cadastro_erro_cpf)
        !Validacao.senha(form.senha) -> texto(R.string.cadastro_erro_senha)
        form.senha != form.confirmarSenha -> texto(R.string.cadastro_erro_senhas_diferentes)
        form.colaborador && form.permissoes.values.none { it } -> texto(R.string.admin_erro_permissoes)
        form.senhaAutorizacao.isBlank() -> texto(R.string.admin_erro_senha_master)
        else -> null
    }
}
