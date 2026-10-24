package com.cjstudio.start.ui.chat

import android.content.Context
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Chat
import com.cjstudio.start.data.repository.IAuthRepository
import com.cjstudio.start.data.repository.IChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Botão "Chat" das telas iniciais (passageiro: embaixo do Sair; motorista:
// canto inferior esquerdo, na linha do Ficar online). Sempre visível, mas
// só HABILITADO da hora em que a corrida é aceita até 1 hora depois do fim
// (perfil.ultimoChatId + chat.fechaEm); fora disso fica apagado.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AtalhoChatViewModel @Inject constructor(
    private val chats: IChatRepository,
    auth: IAuthRepository
) : ViewModel() {

    val meuId: String? = auth.uidLogado()
    private val chatId = MutableStateFlow<String?>(null)

    val chat: StateFlow<Chat?> = chatId.flatMapLatest { id ->
        if (id.isNullOrBlank()) flowOf(null) else chats.escutarChat(id).catch { emit(null) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Aberto agora? Vira false sozinho na hora do fechaEm.
    private val _habilitado = MutableStateFlow(false)
    val habilitado: StateFlow<Boolean> = _habilitado

    init {
        viewModelScope.launch {
            chat.collectLatest { c ->
                _habilitado.value = c != null && c.aberto()
                val fecha = c?.fechaEm?.time ?: return@collectLatest
                val falta = fecha - System.currentTimeMillis()
                if (falta > 0) {
                    delay(falta)
                    _habilitado.value = false
                }
            }
        }
    }

    fun acompanhar(id: String?) {
        chatId.value = id
    }
}

// Aparência do botão: "💬 Chat (2)" com as não lidas quando habilitado;
// apagado e sem número quando fechado.
fun TextView.mostrarBotaoChat(context: Context, chat: Chat?, habilitado: Boolean, meuId: String?) {
    val naoLidas = if (habilitado) chat?.naoLidasPraMim(meuId) ?: 0 else 0
    text = if (naoLidas > 0) context.getString(R.string.chat_botao_nao_lidas, naoLidas) else context.getString(R.string.chat_botao)
    isEnabled = habilitado
    alpha = if (habilitado) 1f else 0.35f
}
