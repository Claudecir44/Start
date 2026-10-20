package com.cjstudio.start.ui.passageiro

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cjstudio.start.R
import com.cjstudio.start.data.repository.SugestaoEndereco
import com.cjstudio.start.databinding.ActivityEscolherDestinoBinding
import com.cjstudio.start.databinding.ItemSugestaoEnderecoBinding
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.ui.common.colocarLocal
import com.cjstudio.start.ui.common.mostrarMensagem
import com.cjstudio.start.ui.passageiro.EscolherDestinoViewModel.Campo
import dagger.hilt.android.AndroidEntryPoint

// Devolve partida e destino pra tela inicial do passageiro (RESULT_OK).
@AndroidEntryPoint
class EscolherDestinoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEscolherDestinoBinding
    private val viewModel: EscolherDestinoViewModel by viewModels()
    private val adapter = SugestaoAdapter { viewModel.escolher(it) }

    // Evita disparar busca quando o próprio app preenche o campo.
    private var preenchendo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEscolherDestinoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topo.tvTitulo.setText(if (viewModel.modoParada) R.string.corrida_parada_titulo else R.string.home_para_onde)
        if (viewModel.modoParada) {
            binding.etPartida.visibility = View.GONE
            binding.tvMinhaLocalizacao.visibility = View.GONE
            binding.etDestino.setHint(R.string.corrida_parada_hint)
        }
        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.rvSugestoes.layoutManager = LinearLayoutManager(this)
        binding.rvSugestoes.adapter = adapter

        binding.etPartida.setOnFocusChangeListener { _, foco -> if (foco) viewModel.focar(Campo.PARTIDA) }
        binding.etDestino.setOnFocusChangeListener { _, foco -> if (foco) viewModel.focar(Campo.DESTINO) }
        binding.etPartida.doAfterTextChanged { if (!preenchendo && binding.etPartida.hasFocus()) viewModel.digitar(it.toString()) }
        binding.etDestino.doAfterTextChanged { if (!preenchendo && binding.etDestino.hasFocus()) viewModel.digitar(it.toString()) }
        binding.tvMinhaLocalizacao.setOnClickListener { viewModel.usarMinhaLocalizacao() }
        binding.etDestino.requestFocus()

        coletar(viewModel.partida) { local -> preencher(binding.etPartida, local?.endereco) }
        coletar(viewModel.destino) { local -> preencher(binding.etDestino, local?.endereco) }
        coletar(viewModel.sugestoes) { adapter.lista = it }
        coletar(viewModel.buscando) { binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                is EscolherDestinoViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
                is EscolherDestinoViewModel.Evento.Pronto -> {
                    setResult(RESULT_OK, Intent().colocarLocal("partida", evento.partida).colocarLocal("destino", evento.destino))
                    finish()
                }
            }
        }
    }

    private fun preencher(campo: android.widget.EditText, texto: String?) {
        if (texto == null || campo.text.toString() == texto) return
        preenchendo = true
        campo.setText(texto)
        preenchendo = false
    }

    private class SugestaoAdapter(private val aoTocar: (SugestaoEndereco) -> Unit) :
        RecyclerView.Adapter<SugestaoAdapter.Holder>() {

        var lista: List<SugestaoEndereco> = emptyList()
            set(valor) {
                field = valor
                @Suppress("NotifyDataSetChanged")
                notifyDataSetChanged()
            }

        class Holder(val binding: ItemSugestaoEnderecoBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemSugestaoEnderecoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = lista.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val s = lista[position]
            holder.binding.tvPrincipal.text = s.principal
            holder.binding.tvSecundario.text = s.secundario
            holder.binding.root.setOnClickListener { aoTocar(s) }
        }
    }

    companion object {
        // Abre só com o campo de destino, pra adicionar uma parada na corrida.
        const val EXTRA_MODO_PARADA = "modoParada"
    }
}
