package com.cjstudio.start.ui.passageiro

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Passageiro
import com.cjstudio.start.data.repository.IPassageiroRepository
import com.cjstudio.start.ui.common.UiTexto
import com.cjstudio.start.ui.common.paraUiTexto
import com.cjstudio.start.ui.common.texto
import com.cjstudio.start.util.Validacao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// "Meu cadastro" do passageiro: nome, celular e foto.
@HiltViewModel
class EditarPassageiroViewModel @Inject constructor(
    private val passageiros: IPassageiroRepository
) : ViewModel() {

    sealed interface Evento {
        data class Mensagem(val texto: UiTexto) : Evento
        data object Salvo : Evento
    }

    private val _passageiro = MutableStateFlow<Passageiro?>(null)
    val passageiro: StateFlow<Passageiro?> = _passageiro

    private val _novaFoto = MutableStateFlow<Uri?>(null)
    val novaFoto: StateFlow<Uri?> = _novaFoto

    private val _salvando = MutableStateFlow(false)
    val salvando: StateFlow<Boolean> = _salvando

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    init {
        viewModelScope.launch {
            passageiros.buscarLogado()
                .onSuccess { _passageiro.value = it }
                .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }

    fun definirFoto(uri: Uri) {
        _novaFoto.value = uri
    }

    fun salvar(nomeCompleto: String, telefone: String) {
        if (_salvando.value || _passageiro.value == null) return
        val erro = when {
            !Validacao.nomeCompleto(nomeCompleto) -> texto(R.string.cadastro_erro_nome)
            !Validacao.telefone(telefone) -> texto(R.string.cadastro_erro_telefone)
            else -> null
        }
        if (erro != null) {
            _eventos.trySend(Evento.Mensagem(erro))
            return
        }
        _salvando.value = true
        viewModelScope.launch {
            val resultado = passageiros.atualizarPerfil(nomeCompleto.trim(), telefone, _novaFoto.value)
            _salvando.value = false
            resultado.onSuccess {
                _eventos.send(Evento.Mensagem(texto(R.string.editar_salvo)))
                _eventos.send(Evento.Salvo)
            }.onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }
}
