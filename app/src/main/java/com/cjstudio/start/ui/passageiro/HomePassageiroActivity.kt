package com.cjstudio.start.ui.passageiro

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.start.R
import com.cjstudio.start.databinding.ActivityHomePassageiroBinding
import com.cjstudio.start.ui.common.Navegacao
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.ui.common.lerLocal
import com.cjstudio.start.ui.common.mostrarMensagem
import com.cjstudio.start.ui.common.pedirLocalizacao
import com.cjstudio.start.ui.common.registrarPermissaoLocalizacao
import com.cjstudio.start.ui.common.temPermissaoLocalizacao
import com.cjstudio.start.ui.corrida.CorridaBaseActivity
import com.cjstudio.start.ui.corrida.CorridaPassageiroActivity
import com.cjstudio.start.ui.passageiro.HomePassageiroViewModel.Painel
import com.cjstudio.start.util.MapaUtil
import com.cjstudio.start.util.MapaUtil.latLng
import com.cjstudio.start.util.carregarFotoRedonda
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomePassageiroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomePassageiroBinding
    private val viewModel: HomePassageiroViewModel by viewModels()
    private var mapa: GoogleMap? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomePassageiroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val abrirPerfil = View.OnClickListener { startActivity(Intent(this, EditarPassageiroActivity::class.java)) }
        binding.cabecalho.ivFoto.setOnClickListener(abrirPerfil)
        binding.cabecalho.tvMeuPerfil.setOnClickListener(abrirPerfil)
        binding.cabecalho.btnSair.setOnClickListener { viewModel.sair() }
        binding.cardParaOnde.setOnClickListener { escolherDestino.launch(Intent(this, EscolherDestinoActivity::class.java)) }
        binding.btnPedir.setOnClickListener { viewModel.pedir() }
        binding.tvTrocarDestino.setOnClickListener {
            viewModel.limpar()
            escolherDestino.launch(Intent(this, EscolherDestinoActivity::class.java))
        }

        (supportFragmentManager.findFragmentById(R.id.mapa) as SupportMapFragment).getMapAsync { m ->
            mapa = m
            MapaUtil.configurar(this, m)
            m.setPadding(0, resources.getDimensionPixelSize(R.dimen.folga_topo_mapa), 0, resources.getDimensionPixelSize(R.dimen.folga_painel_mapa))
            ativarMinhaLocalizacao()
            viewModel.aqui.value?.let { m.moveCamera(CameraUpdateFactory.newLatLngZoom(it.latLng(), 15f)) }
            desenhar(viewModel.painel.value)
        }
        if (temPermissaoLocalizacao()) viewModel.atualizarLocalizacao() else permissao.pedirLocalizacao()

        coletar(viewModel.passageiro) { p ->
            if (p == null) return@coletar
            binding.cabecalho.tvOla.text = getString(R.string.home_ola, p.nomeCompleto.orEmpty().substringBefore(" "))
            binding.cabecalho.ivFoto.carregarFotoRedonda(p.fotoUrl)
        }
        coletar(viewModel.aqui) { local ->
            if (local != null && viewModel.painel.value is Painel.Inicial) {
                mapa?.animateCamera(CameraUpdateFactory.newLatLngZoom(local.latLng(), 15f))
            }
        }
        coletar(viewModel.painel) { mostrar(it) }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                HomePassageiroViewModel.Evento.Saiu -> Navegacao.irLimpando(this, Navegacao.telaLogin())
                is HomePassageiroViewModel.Evento.AbrirCorrida -> abrirCorrida(evento.corridaId)
                is HomePassageiroViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    // Voltou pra cá com a corrida ainda em andamento (ex.: botão Voltar):
    // reabre a corrida em vez de deixar pedir outra.
    override fun onResume() {
        super.onResume()
        viewModel.passageiro.value?.corridaAtualId?.let { abrirCorrida(it) }
    }

    private fun abrirCorrida(corridaId: String) {
        startActivity(
            Intent(this, CorridaPassageiroActivity::class.java)
                .putExtra(CorridaBaseActivity.EXTRA_CORRIDA, corridaId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    @SuppressLint("MissingPermission")
    private fun ativarMinhaLocalizacao() {
        if (temPermissaoLocalizacao()) mapa?.isMyLocationEnabled = true
    }

    private fun mostrar(painel: Painel) {
        val estimativa = when (painel) {
            is Painel.Estimativa -> painel
            is Painel.Pedindo -> painel.anterior
            else -> null
        }
        binding.cardParaOnde.visibility = if (painel is Painel.Inicial) View.VISIBLE else View.GONE
        binding.painelEstimativa.visibility = if (estimativa != null) View.VISIBLE else View.GONE
        binding.progressBar.visibility = if (painel is Painel.Estimando || painel is Painel.Pedindo) View.VISIBLE else View.GONE
        binding.btnPedir.isEnabled = painel is Painel.Estimativa
        binding.btnPedir.setText(if (painel is Painel.Pedindo) R.string.corrida_pedindo else R.string.corrida_pedir)
        if (estimativa != null) {
            val e = estimativa.estimativa
            binding.tvRotaPartida.text = estimativa.partida.endereco
            binding.tvRotaDestino.text = estimativa.destino.endereco
            binding.tvRotaResumo.text = getString(R.string.corrida_resumo, MapaUtil.distancia(e.distanciaMetros), MapaUtil.duracao(e.duracaoSegundos))
            binding.tvValor.text = MapaUtil.valor(e.valor)
        }
        desenhar(painel)
    }

    // Mapa limpo no início; com estimativa, rota + partida/destino enquadrados.
    private fun desenhar(painel: Painel) {
        val m = mapa ?: return
        m.clear()
        val est = (painel as? Painel.Estimativa) ?: (painel as? Painel.Pedindo)?.anterior ?: return
        MapaUtil.desenharRota(this, m, est.estimativa.polyline)
        MapaUtil.marcadorPartida(m, est.partida)
        MapaUtil.marcadorDestino(m, est.destino)
        MapaUtil.enquadrar(m, listOf(est.partida, est.destino), resources.getDimensionPixelSize(R.dimen.folga_enquadrar))
    }
}
