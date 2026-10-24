package com.cjstudio.start.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Chat
import com.cjstudio.start.data.model.MensagemChat
import com.cjstudio.start.data.repository.IAuthRepository
import com.cjstudio.start.data.repository.IChatRepository
import com.cjstudio.start.ui.common.UiTexto
import com.cjstudio.start.ui.common.texto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Chat da corrida (só texto). Aberto do aceite até 1 hora depois do fim
// da corrida; depois as mensagens ficam só pra leitura. O relógio do
// "fecha às..." roda aqui com a tela aberta, e as regras do Firestore
// recusam o que chegar depois do prazo.
@HiltViewModel
class ChatViewModel @Inject constructor(
    salvo: SavedStateHandle,
    private val chats: IChatRepository,
    auth: IAuthRepository
) : ViewModel() {

    val meuId: String? = auth.uidLogado()
    private val chatId: String = salvo.get<String>(EXTRA_CHAT_ID).orEmpty()

    val chat: StateFlow<Chat?> = chats.escutarChat(chatId).catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Sem as que eu apaguei só pra mim.
    val mensagens: StateFlow<List<MensagemChat>> = chats.escutarMensagens(chatId).catch { emit(emptyList()) }
        .map { lista -> lista.filterNot { it.escondidaPara(meuId) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _aberto = MutableStateFlow(true)
    val aberto: StateFlow<Boolean> = _aberto

    private val _avisos = Channel<UiTexto>(Channel.BUFFERED)
    val avisos: Flow<UiTexto> = _avisos.receiveAsFlow()

    private val telaVisivel = MutableStateFlow(false)

    init {
        // Aberto/fechado ao vivo: recalcula na hora do fechaEm.
        viewModelScope.launch {
            chat.collectLatest { c ->
                if (c == null) return@collectLatest
                _aberto.value = c.aberto()
                val fecha = c.fechaEm?.time ?: return@collectLatest
                val falta = fecha - System.currentTimeMillis()
                if (falta > 0) {
                    delay(falta)
                    _aberto.value = false
                }
            }
        }
        // Com a tela na frente, o que chega pra mim já fica como lido.
        viewModelScope.launch {
            combine(chat, mensagens, telaVisivel) { c, lista, visivel -> Triple(c, lista, visivel) }
                .collectLatest { (c, lista, visivel) ->
                    if (c == null || !visivel) return@collectLatest
                    val naoLidas = lista.filter { it.destinatarioId == meuId && !it.lida && !it.deletadoParaTodos }
                    if (naoLidas.isNotEmpty() || c.naoLidasPraMim(meuId) > 0) chats.marcarLidas(c, naoLidas)
                }
        }
    }

    fun telaVisivel(visivel: Boolean) {
        telaVisivel.value = visivel
    }

    fun enviar(texto: String): Boolean {
        val limpo = texto.trim()
        val c = chat.value ?: return false
        if (limpo.isEmpty()) return false
        if (!c.aberto()) {
            _aberto.value = false
            return false
        }
        viewModelScope.launch {
            chats.enviar(chatId, limpo).onFailure { _avisos.send(texto(R.string.chat_erro_enviar)) }
        }
        return true
    }

    fun apagarParaMim(m: MensagemChat) = viewModelScope.launch {
        chats.apagarParaMim(chatId, m).onFailure { _avisos.send(texto(R.string.chat_erro_apagar)) }
    }

    fun apagarParaTodos(m: MensagemChat) = viewModelScope.launch {
        chats.apagarParaTodos(chatId, m).onFailure { _avisos.send(texto(R.string.chat_erro_apagar)) }
    }

    companion object {
        const val EXTRA_CHAT_ID = "chatId"
    }
}
