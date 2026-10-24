package com.cjstudio.start.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.start.R
import com.cjstudio.start.data.model.MensagemChat
import com.cjstudio.start.databinding.ActivityChatBinding
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.ui.common.mostrarMensagem
import com.cjstudio.start.util.carregarFotoRedonda
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Locale

// Chat entre passageiro e motorista (só texto). Aberto do aceite até 1 hora
// depois do fim da corrida; depois fica só pra leitura, com o aviso no
// lugar do campo de mensagem.
@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: MensagemChatAdapter
    private val formatoHora = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = MensagemChatAdapter(viewModel.meuId) { opcoesDaMensagem(it) }
        binding.rvMensagens.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvMensagens.adapter = adapter
        binding.btnVoltar.setOnClickListener { finish() }
        binding.btnEnviar.setOnClickListener {
            if (viewModel.enviar(binding.etMensagem.text.toString())) binding.etMensagem.setText("")
        }

        coletar(viewModel.chat) { c ->
            if (c == null) return@coletar
            val meuId = viewModel.meuId
            binding.tvNomeOutro.text = c.nomeDoOutro(meuId)
            binding.ivFotoOutro.carregarFotoRedonda(c.fotoDoOutro(meuId))
            mostrarSituacao()
        }
        coletar(viewModel.mensagens) { lista ->
            binding.tvVazio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
            adapter.submitList(lista) { if (lista.isNotEmpty()) binding.rvMensagens.scrollToPosition(lista.size - 1) }
        }
        coletar(viewModel.aberto) { aberto ->
            binding.entrada.visibility = if (aberto) View.VISIBLE else View.GONE
            binding.tvChatFechado.visibility = if (aberto) View.GONE else View.VISIBLE
            mostrarSituacao()
        }
        coletar(viewModel.avisos) { mostrarMensagem(it) }
    }

    override fun onStart() {
        super.onStart()
        viewModel.telaVisivel(true)
    }

    override fun onStop() {
        viewModel.telaVisivel(false)
        super.onStop()
    }

    // "Chat aberto até 15:40" depois do fim da corrida; "Chat encerrado".
    private fun mostrarSituacao() {
        val c = viewModel.chat.value ?: return
        binding.tvSituacaoChat.text = when {
            !viewModel.aberto.value -> getString(R.string.chat_situacao_fechado)
            c.fechaEm != null -> getString(R.string.chat_situacao_fecha_as, formatoHora.format(c.fechaEm!!))
            else -> getString(R.string.chat_situacao_corrida)
        }
    }

    private fun opcoesDaMensagem(m: MensagemChat) {
        if (!viewModel.aberto.value) return
        val minha = m.remetenteId == viewModel.meuId
        val opcoes = if (minha) {
            arrayOf(getString(R.string.chat_apagar_para_mim), getString(R.string.chat_apagar_para_todos))
        } else {
            arrayOf(getString(R.string.chat_apagar_para_mim))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.chat_apagar_titulo)
            .setItems(opcoes) { _, qual ->
                if (qual == 0) viewModel.apagarParaMim(m) else viewModel.apagarParaTodos(m)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    companion object {
        fun abrir(context: Context, chatId: String) {
            context.startActivity(Intent(context, ChatActivity::class.java).putExtra(ChatViewModel.EXTRA_CHAT_ID, chatId))
        }
    }
}
