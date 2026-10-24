package com.cjstudio.start.ui.passageiro

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Corrida
import com.cjstudio.start.databinding.ActivityHomePassageiroBinding
import com.cjstudio.start.ui.chat.AtalhoChatViewModel
import com.cjstudio.start.ui.chat.ChatActivity
import com.cjstudio.start.ui.chat.mostrarBotaoChat
import com.cjstudio.start.ui.common.Navegacao
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.ui.common.lerLocal
import com.cjstudio.start.ui.common.mostrarMensagem
import com.cjstudio.start.ui.common.pedirLocalizacao
import com.cjstudio.start.ui.common.pedirNotificacaoSeNecessario
import com.cjstudio.start.ui.common.registrarPermissaoLocalizacao
import com.cjstudio.start.ui.common.registrarPermissaoNotificacao
import com.cjstudio.start.ui.common.temPermissaoLocalizacao
import com.cjstudio.start.ui.corrida.CorridaViewModel
import com.cjstudio.start.ui.corrida.PainelCorrida
import com.cjstudio.start.ui.passageiro.HomePassageiroViewModel.Painel
import com.cjstudio.start.util.MapaUtil
import com.cjstudio.start.util.MapaUtil.latLng
import com.cjstudio.start.util.carregarFotoRedonda
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import dagger.hilt.android.AndroidEntryPoint

// Tudo do passageiro numa tela só: pedido (Para onde vamos? → estimativa →
// Pedir) e, depois, a corrida em andamento — no mesmo mapa entre as linhas
// e no mesmo espaço de baixo (PainelCorrida).
@AndroidEntryPoint
class HomePassageiroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomePassageiroBinding
    private val viewModel: HomePassageiroViewModel by viewModels()
    private val corridaViewModel: CorridaViewModel by viewModels()
    private val atalhoChat: AtalhoChatViewModel by viewModels()
    private lateinit var painelCorrida: PainelCorrida
    private var mapa: GoogleMap? = null

    private val permissaoNotificacao = registrarPermissaoNotificacao()

    private val permissao = registrarPermissaoLocalizacao { concedida ->
        if (concedida) {
            ativarMinhaLocalizacao()
            viewModel.atualizarLocalizacao()
        }
    }

    private val escolherDestino = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val dados = r.data ?: return@registerForActivityResult
        val partida = dados.lerLocal("partida")
        val destino = dados.lerLocal("destino")
        if (r.resultCode == RESULT_OK && partida != null && destino != null) viewModel.definirRota(partida, destino)
    }

    // Parada extra durante a corrida (mesma busca de endereço, só o destino).
    private val escolherParada = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val parada = r.data?.lerLocal("destino")
        if (r.resultCode == RESULT_OK && parada != null) corridaViewModel.adicionarParada(parada)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomePassageiroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        pedirNotificacaoSeNecessario(permissaoNotificacao)

        painelCorrida = PainelCorrida(this, binding.painelCorrida, corridaViewModel) {
            escolherParada.launch(
                Intent(this, EscolherDestinoActivity::class.java).putExtra(EscolherDestinoActivity.EXTRA_MODO_PARADA, true)
            )
        }

        val abrirPerfil = View.OnClickListener { startActivity(Intent(this, EditarPassageiroActivity::class.java)) }
        binding.cabecalho.ivFoto.setOnClickListener(abrirPerfil)
        binding.cabecalho.tvMeuPerfil.setOnClickListener(abrirPerfil)
        binding.cabecalho.btnSair.setOnClickListener { viewModel.sair() }
        // Sempre à vista embaixo do Sair; só habilita no prazo do chat.
        binding.cabecalho.btnChat.visibility = View.VISIBLE
        binding.cabecalho.btnChat.setOnClickListener { atalhoChat.chat.value?.id?.let { ChatActivity.abrir(this, it) } }
        binding.cardParaOnde.setOnClickListener { escolherDestino.launch(Intent(this, EscolherDestinoActivity::class.java)) }
        binding.btnPedir.setOnClickListener { viewModel.pedir() }
        binding.cbQuartoPassageiro.setOnCheckedChangeListener { botao, marcado ->
            if (botao.isPressed) viewModel.alternarQuartoPassageiro(marcado)
        }
        binding.tvTrocarDestino.setOnClickListener {
            viewModel.limpar()
            escolherDestino.launch(Intent(this, EscolherDestinoActivity::class.java))
        }

        (supportFragmentManager.findFragmentById(R.id.mapa) as SupportMapFragment).getMapAsync { m ->
            mapa = m
            MapaUtil.configurar(this, m)
            ativarMinhaLocalizacao()
            viewModel.aqui.value?.let { m.moveCamera(CameraUpdateFactory.newLatLngZoom(it.latLng(), 15f)) }
            atualizarMapa()
        }
        if (temPermissaoLocalizacao()) viewModel.atualizarLocalizacao() else permissao.pedirLocalizacao()

        coletar(viewModel.passageiro) { p ->
            if (p == null) return@coletar
            binding.cabecalho.tvOla.text = p.nomeCompleto.orEmpty().substringBefore(" ")
            binding.cabecalho.tvPapel.setText(R.string.papel_passageiro)
            binding.cabecalho.ivFoto.carregarFotoRedonda(p.fotoUrl)
            corridaViewModel.acompanhar(p.corridaAtualId)
            atalhoChat.acompanhar(p.ultimoChatId)
        }
        // Chat com o motorista, embaixo do Sair.
        coletar(atalhoChat.chat) { mostrarBotaoChat() }
        coletar(atalhoChat.habilitado) { mostrarBotaoChat() }
        coletar(viewModel.aqui) { local ->
            if (local != null && viewModel.painel.value is Painel.Inicial && corridaViewModel.corrida.value == null) {
                mapa?.animateCamera(CameraUpdateFactory.newLatLngZoom(local.latLng(), 15f))
            }
        }
        coletar(viewModel.painel) { mostrarPedido(it) }
        coletar(corridaViewModel.corrida) { mostrarCorrida(it) }
        coletar(corridaViewModel.enviando) { painelCorrida.habilitar(!it) }
        coletar(corridaViewModel.mensagens) { mostrarMensagem(it) }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                HomePassageiroViewModel.Evento.Saiu -> Navegacao.irLimpando(this, Navegacao.telaLogin())
                is HomePassageiroViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    private fun mostrarBotaoChat() {
        binding.cabecalho.btnChat.mostrarBotaoChat(this, atalhoChat.chat.value, atalhoChat.habilitado.value, atalhoChat.meuId)
    }

    @SuppressLint("MissingPermission")
    private fun ativarMinhaLocalizacao() {
        if (temPermissaoLocalizacao()) mapa?.isMyLocationEnabled = true
    }

    // Com corrida, o espaço de baixo é dela; sem corrida, do pedido.
    private fun mostrarCorrida(corrida: Corrida?) {
        binding.painelCorrida.root.visibility = if (corrida != null) View.VISIBLE else View.GONE
        binding.painelPedido.visibility = if (corrida != null) View.GONE else View.VISIBLE
        if (corrida != null) painelCorrida.mostrar(corrida) else painelCorrida.limparDesenho()
        atualizarMapa()
    }

    private fun mostrarPedido(painel: Painel) {
        val estimativa = when (painel) {
            is Painel.Estimativa -> painel
            is Painel.Pedindo -> painel.anterior
            else -> null
        }
        binding.cardParaOnde.visibility = if (painel is Painel.Inicial) View.VISIBLE else View.GONE
        binding.painelEstimativa.visibility = if (estimativa != null) View.VISIBLE else View.GONE
        binding.progressBar.visibility = if (painel is Painel.Estimando || painel is Painel.Pedindo) View.VISIBLE else View.GONE
        binding.btnPedir.isEnabled = painel is Painel.Estimativa
        binding.cbQuartoPassageiro.isEnabled = painel is Painel.Estimativa
        binding.btnPedir.setText(if (painel is Painel.Pedindo) R.string.corrida_pedindo else R.string.corrida_pedir)
        if (estimativa != null) {
            val e = estimativa.estimativa
            binding.tvRotaPartida.text = estimativa.partida.endereco
            binding.tvRotaDestino.text = estimativa.destino.endereco
            binding.tvRotaResumo.text = getString(R.string.corrida_resumo, MapaUtil.distancia(e.distanciaMetros), MapaUtil.duracao(e.duracaoSegundos))
            binding.tvValor.text = MapaUtil.valor(e.valor)
            binding.cbQuartoPassageiro.isChecked = e.quartoPassageiro
        } else if (painel is Painel.Inicial) {
            binding.cbQuartoPassageiro.isChecked = false
        }
        atualizarMapa()
    }

    // Um mapa só: a corrida desenha quando existe; senão, a estimativa.
    private fun atualizarMapa() {
        val m = mapa ?: return
        val folga = resources.getDimensionPixelSize(R.dimen.folga_enquadrar)
        corridaViewModel.corrida.value?.let {
            painelCorrida.desenhar(m, it, folga)
            return
        }
        m.clear()
        val painel = viewModel.painel.value
        val est = (painel as? Painel.Estimativa) ?: (painel as? Painel.Pedindo)?.anterior ?: return
        MapaUtil.desenharRota(this, m, est.estimativa.polyline)
        MapaUtil.marcadorPartida(m, est.partida)
        MapaUtil.marcadorDestino(m, est.destino)
        MapaUtil.enquadrar(m, listOf(est.partida, est.destino), folga)
    }
}
