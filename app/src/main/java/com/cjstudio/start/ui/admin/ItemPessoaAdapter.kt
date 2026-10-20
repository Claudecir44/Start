package com.cjstudio.start.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cjstudio.start.databinding.ItemPessoaAdminBinding
import com.cjstudio.start.util.carregarFotoRedonda

// Linha de lista do painel (motorista, passageiro ou administrador): o
// ViewModel já entrega pronto o que mostrar, o adapter só desenha.
data class ItemPessoa(
    val id: String,
    val nome: String,
    val linha2: String,
    val linha3: String,
    val fotoUrl: String?,
    @StringRes val selo: Int? = null,
    @ColorRes val corSelo: Int? = null
)

class ItemPessoaAdapter(
    private val aoTocar: (ItemPessoa) -> Unit
) : ListAdapter<ItemPessoa, ItemPessoaAdapter.Holder>(Diferenca) {

    class Holder(val binding: ItemPessoaAdminBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemPessoaAdminBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvNome.text = item.nome
            tvLinha2.text = item.linha2
            tvLinha3.text = item.linha3
            tvLinha3.visibility = if (item.linha3.isBlank()) View.GONE else View.VISIBLE
            ivFoto.carregarFotoRedonda(item.fotoUrl)
            if (item.selo != null) {
                tvSelo.visibility = View.VISIBLE
                tvSelo.setText(item.selo)
                item.corSelo?.let { tvSelo.setTextColor(ContextCompat.getColor(root.context, it)) }
            } else {
                tvSelo.visibility = View.GONE
            }
            root.setOnClickListener { aoTocar(item) }
        }
    }

    private object Diferenca : DiffUtil.ItemCallback<ItemPessoa>() {
        override fun areItemsTheSame(a: ItemPessoa, b: ItemPessoa) = a.id == b.id
        override fun areContentsTheSame(a: ItemPessoa, b: ItemPessoa) = a == b
    }
}
