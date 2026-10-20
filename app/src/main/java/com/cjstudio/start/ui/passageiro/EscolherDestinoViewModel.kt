package com.cjstudio.start.ui.passageiro

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Local
import com.cjstudio.start.data.repository.IEnderecoRepository
import com.cjstudio.start.data.repository.ILocalizacaoRepository
import com.cjstudio.start.data.repository.SugestaoEndereco
import com.cjstudio.start.ui.common.UiTexto
import com.cjstudio.start.ui.common.paraUiTexto
import com.cjstudio.start.ui.common.texto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Partida + destino. A partida começa como a localização atual; tocar num
// campo e digitar mostra as sugestões do Google pra aquele campo.
@HiltViewModel
class EscolherDestinoViewModel @Inject constructor(
    private val enderecos: IEnderecoRepository,
    private val localizacao: ILocalizacaoRepository,
    estado: SavedStateHandle
) : ViewModel() {

    // Parada extra durante a corrida: só o campo de destino (a partida é a
    // corrida que já está acontecendo).
    val modoParada: Boolean = estado[EscolherDestinoActivity.EXTRA_MODO_PARADA] ?: false

    enum class Campo { PARTIDA, DESTINO }

    sealed interface Evento {
        data class Pronto(val partida: Local, val destino: Local) : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    private val _partida = MutableStateFlow<Local?>(null)
    val partida: StateFlow<Local?> = _partida

    private val _destino = MutableStateFlow<Local?>(null)
    val destino: StateFlow<Local?> = _destino

    private val _sugestoes = MutableStateFlow<List<SugestaoEndereco>>(emptyList())
    val sugestoes: StateFlow<List<SugestaoEndereco>> = _sugestoes

    private val _buscando = MutableStateFlow(false)
    val buscando: StateFlow<Boolean> = _buscando

    var campoAtivo = Campo.DESTINO
        private set

    private val textoBusca = MutableStateFlow("")

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    // Posição atual, usada também pra priorizar sugestões perto.
    private var aqui: Local? = null

    init {
        usarMinhaLocalizacao()
        observarBusca()
    }

    // Espera a pessoa parar de digitar (menos chamadas pagas ao Google).
    @OptIn(FlowPreview::class)
    private fun observarBusca() {
        viewModelScope.launch {
            textoBusca.debounce(350).collect { texto ->
                if (texto.trim().length < 3) {
                    _sugestoes.value = emptyList()
                    return@collect
                }
                _buscando.value = true
                enderecos.sugerir(texto, aqui)
                    .onSuccess { _sugestoes.value = it }
                    .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
                _buscando.value = false
            }
        }
    }

    fun focar(campo: Campo) {
        campoAtivo = campo
        _sugestoes.value = emptyList()
    }

    fun digitar(texto: String) {
        textoBusca.value = texto
    }

    fun usarMinhaLocalizacao() {
        viewModelScope.launch {
            val atual = localizacao.atual()
            if (atual == null) {
                _eventos.send(Evento.Mensagem(texto(R.string.corrida_sem_localizacao)))
                return@launch
            }
            aqui = atual
            if (modoParada) return@launch
            _partida.value = atual.copy(endereco = localizacao.endereco(atual.lat, atual.lng) ?: "Minha localização")
            conferirPronto()
        }
    }

    fun escolher(sugestao: SugestaoEndereco) {
        val campo = campoAtivo
        viewModelScope.launch {
            enderecos.detalhar(sugestao)
                .onSuccess { local ->
                    _sugestoes.value = emptyList()
                    if (campo == Campo.PARTIDA) _partida.value = local else _destino.value = local
                    conferirPronto()
                }
                .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }

    private suspend fun conferirPronto() {
        val d = _destino.value ?: return
        if (modoParada) {
            _eventos.send(Evento.Pronto(d, d))
            return
        }
        val p = _partida.value ?: return
        _eventos.send(Evento.Pronto(p, d))
    }
}
