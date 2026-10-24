package com.cjstudio.start.ui.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cjstudio.start.R
import com.cjstudio.start.data.model.MensagemChat
import com.cjstudio.start.databinding.ItemMensagemChatBinding
import java.text.SimpleDateFormat
import java.util.Locale

// Balões do chat. As minhas mostram "Lida"/"Não lida" (igual Match e
// Caronas). Toque longo numa mensagem abre "apagar" (só com o chat aberto).
class MensagemChatAdapter(
    private val meuId: String?,
    private val aoSegurar: (MensagemChat) -> Unit
) : ListAdapter<MensagemChat, MensagemChatAdapter.Holder>(DIFF) {

    private val formatoHora = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    class Holder(val b: ItemMensagemChatBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemMensagemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val m = getItem(position)
        val b = holder.b
        val ctx = b.root.context
        val minha = m.remetenteId == meuId

        (b.balao.layoutParams as LinearLayout.LayoutParams).gravity = if (minha) Gravity.END else Gravity.START
        b.balao.setBackgroundResource(if (minha) R.drawable.bg_balao_meu else R.drawable.bg_balao_outro)
        val corTexto = ContextCompat.getColor(ctx, if (minha) R.color.preto else R.color.texto)
        b.tvTexto.setTextColor(corTexto)
        b.tvHora.setTextColor(corTexto)
        b.tvHora.alpha = 0.7f

        if (m.deletadoParaTodos) {
            b.tvTexto.setText(R.string.chat_mensagem_apagada)
            b.tvTexto.alpha = 0.6f
        } else {
            b.tvTexto.text = m.conteudo
            b.tvTexto.alpha = 1f
        }
        b.tvHora.text = m.timestamp?.let { formatoHora.format(it) }.orEmpty()

        b.tvLeitura.visibility = if (minha && !m.deletadoParaTodos) View.VISIBLE else View.GONE
        if (minha) {
            b.tvLeitura.setText(if (m.lida) R.string.chat_lida else R.string.chat_nao_lida)
            b.tvLeitura.setTextColor(ContextCompat.getColor(ctx, if (m.lida) R.color.verde_escuro else R.color.vermelho_escuro))
        }

        b.balao.setOnLongClickListener {
            if (!m.deletadoParaTodos) aoSegurar(m)
            true
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<MensagemChat>() {
            override fun areItemsTheSame(a: MensagemChat, b: MensagemChat) = a.id == b.id
            override fun areContentsTheSame(a: MensagemChat, b: MensagemChat) = a == b
        }
    }
}
