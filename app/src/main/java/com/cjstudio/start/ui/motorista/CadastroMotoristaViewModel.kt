package com.cjstudio.start.ui.motorista

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Motorista
import com.cjstudio.start.data.repository.IMotoristaRepository
import com.cjstudio.start.ui.auth.LoginActivity
import com.cjstudio.start.ui.common.UiTexto
import com.cjstudio.start.ui.common.paraUiTexto
import com.cjstudio.start.ui.common.texto
import com.cjstudio.start.util.CpfUtil
import com.cjstudio.start.util.Validacao
import com.cjstudio.start.data.repository.ResultadoCadastro
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CadastroMotoristaViewModel @Inject constructor(
    private val motoristas: IMotoristaRepository,
    estado: SavedStateHandle
) : ViewModel() {

    val completandoCadastro: Boolean = estado[LoginActivity.EXTRA_COMPLETAR_CADASTRO] ?: false

    enum class Foto { PERFIL, CNH, CRLV }

    // Conta, CPF, sexo e termos + os dados de motorista (os mesmos da edição).
    data class Formulario(
        val cpf: String,
        val email: String,
        val senha: String,
        val confirmarSenha: String,
        val sexo: String?,
        val aceitouTermos: Boolean,
        val dados: ValidacaoMotorista.Dados
    )

    sealed interface Evento {
        data class ConfirmarEmail(val email: String) : Evento
        data object IrParaInicio : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    private val _fotos = MutableStateFlow<Map<Foto, Uri>>(emptyMap())
    val fotos: StateFlow<Map<Foto, Uri>> = _fotos

    private val _enviando = MutableStateFlow(false)
    val enviando: StateFlow<Boolean> = _enviando

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    fun definirFoto(tipo: Foto, uri: Uri) {
        _fotos.value = _fotos.value + (tipo to uri)
    }

    fun cadastrar(form: Formulario) {
        if (_enviando.value) return
        validar(form)?.let {
            _eventos.trySend(Evento.Mensagem(it))
            return
        }
        val d = form.dados
        val motorista = Motorista(
            nomeCompleto = d.nomeCompleto.trim(),
            cpf = CpfUtil.somenteDigitos(form.cpf),
            dataNascimento = d.dataNascimento,
            email = form.email.trim().takeIf { !completandoCadastro },
            telefone = d.telefone,
            sexo = form.sexo,
            cnhNumero = d.cnhNumero.filter { it.isDigit() },
            cnhCategoria = d.cnhCategoria,
            cnhValidade = d.cnhValidade,
            veiculo = d.veiculo()
        )
        val fotos = _fotos.value
        _enviando.value = true
        viewModelScope.launch {
            val resultado = motoristas.cadastrar(
                motorista,
                form.senha.takeIf { !completandoCadastro },
                fotos.getValue(Foto.PERFIL),
                fotos.getValue(Foto.CNH),
                fotos.getValue(Foto.CRLV)
            )
            _enviando.value = false
            _eventos.send(
                resultado.fold(
                    { fim -> if (fim == ResultadoCadastro.PRONTO) Evento.IrParaInicio else Evento.ConfirmarEmail(form.email.trim().ifBlank { "seu e-mail" }) },
                    { Evento.Mensagem(it.paraUiTexto()) }
                )
            )
        }
    }

    // Conta (e-mail/senha), CPF, sexo, fotos e termos aqui; o resto é regra
    // de motorista, igual na edição (ValidacaoMotorista).
    private fun validar(form: Formulario): UiTexto? {
        val fotos = _fotos.value
        return when {
            Foto.PERFIL !in fotos -> texto(R.string.cadastro_erro_foto)
            !CpfUtil.valido(form.cpf) -> texto(R.string.cadastro_erro_cpf)
            form.sexo == null -> texto(R.string.cadastro_erro_sexo)
            !completandoCadastro && !Validacao.email(form.email.trim()) -> texto(R.string.cadastro_erro_email)
            !completandoCadastro && !Validacao.senha(form.senha) -> texto(R.string.cadastro_erro_senha)
            !completandoCadastro && form.senha != form.confirmarSenha -> texto(R.string.cadastro_erro_senhas_diferentes)
            else -> ValidacaoMotorista.validar(form.dados, Foto.CNH in fotos, Foto.CRLV in fotos)
                ?: if (!form.aceitouTermos) texto(R.string.cadastro_erro_termos) else null
        }
    }
}
