package com.cjstudio.start.ui.motorista

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Motorista
import com.cjstudio.start.data.model.Veiculo
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
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CadastroMotoristaViewModel @Inject constructor(
    private val motoristas: IMotoristaRepository,
    estado: SavedStateHandle
) : ViewModel() {

    val completandoCadastro: Boolean = estado[LoginActivity.EXTRA_COMPLETAR_CADASTRO] ?: false

    enum class Foto { PERFIL, CNH, CRLV }

    data class Formulario(
        val nomeCompleto: String,
        val cpf: String,
        val dataNascimento: String,
        val telefone: String,
        val email: String,
        val senha: String,
        val confirmarSenha: String,
        val sexo: String?,
        val cnhNumero: String,
        val cnhCategoria: String,
        val cnhValidade: String,
        val marca: String,
        val modelo: String,
        val ano: String,
        val cor: String,
        val placa: String,
        val aceitouTermos: Boolean
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
        val motorista = Motorista(
            nomeCompleto = form.nomeCompleto.trim(),
            cpf = CpfUtil.somenteDigitos(form.cpf),
            dataNascimento = form.dataNascimento,
            email = form.email.trim().takeIf { !completandoCadastro },
            telefone = form.telefone,
            sexo = form.sexo,
            cnhNumero = form.cnhNumero.filter { it.isDigit() },
            cnhCategoria = form.cnhCategoria,
            cnhValidade = form.cnhValidade,
            veiculo = Veiculo(
                marca = form.marca.trim(),
                modelo = form.modelo.trim(),
                ano = form.ano.toInt(),
                cor = form.cor.trim(),
                placa = form.placa.uppercase(Locale.ROOT)
            )
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

    private fun validar(form: Formulario): UiTexto? {
        val fotos = _fotos.value
        val ano = form.ano.toIntOrNull()
        val anoAtual = Calendar.getInstance().get(Calendar.YEAR)
        val idade = Validacao.idade(form.dataNascimento)
        return when {
            Foto.PERFIL !in fotos -> texto(R.string.cadastro_erro_foto)
            !Validacao.nomeCompleto(form.nomeCompleto) -> texto(R.string.cadastro_erro_nome)
            !CpfUtil.valido(form.cpf) -> texto(R.string.cadastro_erro_cpf)
            idade == null -> texto(R.string.motorista_erro_nascimento)
            idade < IDADE_MINIMA -> texto(R.string.motorista_erro_idade, IDADE_MINIMA)
            !Validacao.telefone(form.telefone) -> texto(R.string.cadastro_erro_telefone)
            form.sexo == null -> texto(R.string.cadastro_erro_sexo)
            !completandoCadastro && !Validacao.email(form.email.trim()) -> texto(R.string.cadastro_erro_email)
            !completandoCadastro && !Validacao.senha(form.senha) -> texto(R.string.cadastro_erro_senha)
            !completandoCadastro && form.senha != form.confirmarSenha -> texto(R.string.cadastro_erro_senhas_diferentes)
            !Validacao.cnh(form.cnhNumero) -> texto(R.string.motorista_erro_cnh)
            form.cnhCategoria.isBlank() -> texto(R.string.motorista_erro_cnh_categoria)
            !Validacao.dataFutura(form.cnhValidade) -> texto(R.string.motorista_erro_cnh_validade)
            Foto.CNH !in fotos -> texto(R.string.motorista_erro_foto_cnh)
            form.marca.isBlank() || form.modelo.isBlank() || form.cor.isBlank() -> texto(R.string.motorista_erro_veiculo)
            ano == null || ano > anoAtual + 1 -> texto(R.string.motorista_erro_ano)
            ano < anoAtual - IDADE_MAXIMA_VEICULO -> texto(R.string.motorista_erro_ano_antigo, anoAtual - IDADE_MAXIMA_VEICULO)
            !Validacao.placa(form.placa) -> texto(R.string.motorista_erro_placa)
            Foto.CRLV !in fotos -> texto(R.string.motorista_erro_foto_crlv)
            !form.aceitouTermos -> texto(R.string.cadastro_erro_termos)
            else -> null
        }
    }

    companion object {
        const val IDADE_MINIMA = 21
        const val IDADE_MAXIMA_VEICULO = 15

        // Categorias da CNH que permitem dirigir carro de passeio.
        val CATEGORIAS_CNH = listOf("B", "AB", "C", "AC", "D", "AD", "E", "AE")
    }
}
