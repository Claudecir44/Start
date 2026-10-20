package com.cjstudio.start.ui.motorista

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Motorista
import com.cjstudio.start.data.model.StatusMotorista
import com.cjstudio.start.databinding.ActivityHomeMotoristaBinding
import com.cjstudio.start.ui.common.Navegacao
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.ui.common.mostrarMensagem
import com.cjstudio.start.ui.common.pedirLocalizacao
import com.cjstudio.start.ui.common.registrarPermissaoLocalizacao
import com.cjstudio.start.ui.common.temPermissaoLocalizacao
import com.cjstudio.start.ui.common.texto
import com.cjstudio.start.ui.corrida.CorridaBaseActivity
import com.cjstudio.start.ui.corrida.CorridaMotoristaActivity
import com.cjstudio.start.util.MapaUtil
import com.cjstudio.start.util.MapaUtil.latLng
import com.cjstudio.start.util.carregarFotoRedonda
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeMotoristaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeMotoristaBinding
    private val viewModel: HomeMotoristaViewModel by viewModels()
    private var mapa: GoogleMap? = null
    private var centralizado = false

    // Ficar online sem localização não serve (corridas são por proximidade).
    private val permissao = registrarPermissaoLocalizacao { concedida ->
        if (concedida) {
            ativarMinhaLocalizacao()
            viewModel.telaVisivel(true)
            if (viewModel.motorista.value?.online == false) viewModel.alternarOnline()
        } else {
            mostrarMensagem(texto(R.string.corrida_sem_localizacao))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeMotoristaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cabecalho.btnSair.setOnClickListener { viewModel.sair() }
        binding.btnOnline.setOnClickListener {
            val ficandoOnline = viewModel.motorista.value?.online == false
            if (ficandoOnline && !temPermissaoLocalizacao()) permissao.pedirLocalizacao() else viewModel.alternarOnline()
        }
        val abrirCadastro = View.OnClickListener { startActivity(Intent(this, EditarMotoristaActivity::class.java)) }
        binding.cabecalho.tvMeuPerfil.setOnClickListener(abrirCadastro)
        binding.cabecalho.ivFoto.setOnClickListener(abrirCadastro)
        binding.btnAceitar.setOnClickListener { viewModel.oferta.value?.let { viewModel.aceitar(it) } }
        binding.btnRecusar.setOnClickListener { viewModel.oferta.value?.let { viewModel.recusar(it) } }

        (supportFragmentManager.findFragmentById(R.id.mapa) as SupportMapFragment).getMapAsync { m ->
            mapa = m
            MapaUtil.configurar(this, m)
            ativarMinhaLocalizacao()
            viewModel.oferta.value?.let { desenharOferta(it) }
        }

        coletar(viewModel.motorista) { motorista -> if (motorista != null) mostrar(motorista) }
        coletar(viewModel.aqui) { local ->
            if (local != null && !centralizado && viewModel.oferta.value == null) {
                centralizado = true
                mapa?.animateCamera(CameraUpdateFactory.newLatLngZoom(local.latLng(), 15f))
            }
        }
        coletar(viewModel.oferta) { oferta ->
            binding.cardOferta.visibility = if (oferta != null) View.VISIBLE else View.GONE
            // Com a oferta por cima do mapa, a rota é enquadrada acima dela.
            mapa?.setPadding(0, 0, 0, if (oferta != null) resources.getDimensionPixelSize(R.dimen.folga_painel_corrida) else 0)
            if (oferta != null) {
                val c = oferta.corrida
                binding.tvOfertaValor.text = MapaUtil.valor(c.valor)
                binding.tvOfertaResumo.text = getString(
                    R.string.oferta_resumo,
                    MapaUtil.distancia(oferta.metrosAtePartida.toLong()),
                    MapaUtil.distancia(c.distanciaMetros),
                    MapaUtil.duracao(c.duracaoSegundos)
                )
                binding.tvOfertaPartida.text = c.origem?.endereco
                binding.tvOfertaDestino.text = c.destino?.endereco
            }
            desenharOferta(oferta)
        }
        coletar(viewModel.aceitando) { aceitando ->
            binding.btnAceitar.isEnabled = !aceitando
            binding.btnAceitar.setText(if (aceitando) R.string.oferta_aceitando else R.string.oferta_aceitar)
        }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                HomeMotoristaViewModel.Evento.Saiu -> Navegacao.irLimpando(this, Navegacao.telaLogin())
                is HomeMotoristaViewModel.Evento.AbrirCorrida -> abrirCorrida(evento.corridaId)
                is HomeMotoristaViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    // Voltou pra cá com a corrida ainda em andamento: reabre a corrida.
    override fun onResume() {
        super.onResume()
        viewModel.motorista.value?.corridaAtualId?.let { abrirCorrida(it) }
    }

    private fun abrirCorrida(corridaId: String) {
        startActivity(
            Intent(this, CorridaMotoristaActivity::class.java)
                .putExtra(CorridaBaseActivity.EXTRA_CORRIDA, corridaId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    // Só recebe corridas e envia posição com a tela aberta (nesta versão).
    override fun onStart() {
        super.onStart()
        viewModel.telaVisivel(true)
    }

    override fun onStop() {
        super.onStop()
        viewModel.telaVisivel(false)
    }

    @SuppressLint("MissingPermission")
    private fun ativarMinhaLocalizacao() {
        if (temPermissaoLocalizacao()) mapa?.isMyLocationEnabled = true
    }

    // Oferta no mapa: partida, destino e rota; sem oferta, mapa limpo.
    private fun desenharOferta(oferta: HomeMotoristaViewModel.Oferta?) {
        val m = mapa ?: return
        m.clear()
        val c = oferta?.corrida ?: return
        val origem = c.origem ?: return
        val destino = c.destino ?: return
        MapaUtil.desenharRota(this, m, c.polyline)
        MapaUtil.marcadorPartida(m, origem)
        MapaUtil.marcadorDestino(m, destino)
        MapaUtil.enquadrar(m, listOfNotNull(viewModel.aqui.value, origem, destino), resources.getDimensionPixelSize(R.dimen.folga_enquadrar))
    }

    private fun mostrar(motorista: Motorista) {
        binding.cabecalho.tvOla.text = getString(R.string.home_ola, motorista.nomeCompleto.orEmpty().substringBefore(" "))
        binding.cabecalho.ivFoto.carregarFotoRedonda(motorista.fotoUrl)
        binding.cabecalho.tvLinha2.text = motorista.veiculo?.let { "${it.descricao()}  ·  ${it.placa.orEmpty()}" }.orEmpty()

        val (titulo, mensagem, cor) = when (motorista.status) {
            StatusMotorista.APROVADO -> Triple(R.string.status_aprovado, getString(R.string.status_aprovado_msg), R.color.verde)
            StatusMotorista.REPROVADO -> Triple(R.string.status_reprovado, motivo(motorista, R.string.status_reprovado_msg), R.color.vermelho)
            StatusMotorista.BLOQUEADO -> Triple(R.string.status_bloqueado, motivo(motorista, R.string.status_bloqueado_msg), R.color.vermelho)
            else -> Triple(R.string.status_em_analise, getString(R.string.status_em_analise_msg), R.color.amarelo)
        }
        binding.cabecalho.tvSituacao.visibility = View.VISIBLE
        binding.cabecalho.tvSituacao.setText(titulo)
        binding.cabecalho.tvSituacao.setTextColor(ContextCompat.getColor(this, cor))
        // Aprovado não precisa de explicação: o cartão some e sobra o "Ficar online".
        binding.cardSituacao.visibility = if (motorista.aprovado) View.GONE else View.VISIBLE
        binding.tvStatusMensagem.text = mensagem

        binding.btnOnline.visibility = if (motorista.aprovado) View.VISIBLE else View.GONE
        binding.btnOnline.setText(if (motorista.online) R.string.motorista_ficar_offline else R.string.motorista_ficar_online)
        binding.btnOnline.setBackgroundResource(if (motorista.online) R.drawable.bg_botao_contorno else R.drawable.bg_botao_primario)
        binding.btnOnline.setTextColor(ContextCompat.getColor(this, if (motorista.online) R.color.amarelo else R.color.preto))
    }

    private fun motivo(motorista: Motorista, padrao: Int): String =
        motorista.motivoStatus?.takeIf { it.isNotBlank() }?.let { getString(R.string.status_motivo, it) } ?: getString(padrao)
}
