package com.cjstudio.start.ui.motorista

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.R
import com.cjstudio.start.data.model.EdicaoMotorista
import com.cjstudio.start.data.model.Motorista
import com.cjstudio.start.data.model.StatusMotorista
import com.cjstudio.start.data.repository.IMotoristaRepository
import com.cjstudio.start.ui.common.UiTexto
import com.cjstudio.start.ui.common.paraUiTexto
import com.cjstudio.start.ui.common.texto
import com.cjstudio.start.ui.motorista.CadastroMotoristaViewModel.Foto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// "Meu cadastro" do motorista: troca de foto e celular na hora; nome,
// nascimento, CNH ou carro voltam o cadastro pra análise (com aviso antes,
// se ele estava aprovado).
@HiltViewModel
class EditarMotoristaViewModel @Inject constructor(
    private val motoristas: IMotoristaRepository
) : ViewModel() {

    sealed interface Evento {
        data class Mensagem(val texto: UiTexto) : Evento
        // Aprovado mexendo em dado conferido: confirmar antes de sair do ar.
        data class ConfirmarNovaAnalise(val dados: ValidacaoMotorista.Dados) : Evento
        data object Salvo : Evento
    }

    private val _motorista = MutableStateFlow<Motorista?>(null)
    val motorista: StateFlow<Motorista?> = _motorista

    private val _fotos = MutableStateFlow<Map<Foto, Uri>>(emptyMap())
    val fotos: StateFlow<Map<Foto, Uri>> = _fotos

    private val _salvando = MutableStateFlow(false)
    val salvando: StateFlow<Boolean> = _salvando

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    init {
        viewModelScope.launch {
            motoristas.buscarLogado()
                .onSuccess { _motorista.value = it }
                .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }

    fun definirFoto(tipo: Foto, uri: Uri) {
        _fotos.value = _fotos.value + (tipo to uri)
    }

    fun salvar(dados: ValidacaoMotorista.Dados, confirmado: Boolean = false) {
        val atual = _motorista.value ?: return
        if (_salvando.value) return
        // Fotos dos documentos já existem no cadastro: trocar é opcional.
        ValidacaoMotorista.validar(dados, temFotoCnh = true, temFotoCrlv = true)?.let {
            _eventos.trySend(Evento.Mensagem(it))
            return
        }
        val fotos = _fotos.value
        val edicao = EdicaoMotorista(
            nomeCompleto = dados.nomeCompleto.trim(),
            telefone = dados.telefone,
            dataNascimento = dados.dataNascimento,
            cnhNumero = dados.cnhNumero.filter { it.isDigit() },
            cnhCategoria = dados.cnhCategoria,
            cnhValidade = dados.cnhValidade,
            veiculo = dados.veiculo(),
            novaFoto = fotos[Foto.PERFIL],
            novaFotoCnh = fotos[Foto.CNH],
            novaFotoCrlv = fotos[Foto.CRLV]
        )
        if (!confirmado && atual.status == StatusMotorista.APROVADO && edicao.exigeNovaAnalise(atual)) {
            _eventos.trySend(Evento.ConfirmarNovaAnalise(dados))
            return
        }
        _salvando.value = true
        viewModelScope.launch {
            val resultado = motoristas.atualizarPerfil(edicao)
            _salvando.value = false
            resultado.onSuccess { voltouParaAnalise ->
                _eventos.send(
                    Evento.Mensagem(texto(if (voltouParaAnalise) R.string.editar_salvo_em_analise else R.string.editar_salvo))
                )
                _eventos.send(Evento.Salvo)
            }.onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }
}
