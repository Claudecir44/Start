package com.cjstudio.start.ui.motorista

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Corrida
import com.cjstudio.start.data.model.Motorista
import com.cjstudio.start.data.model.StatusMotorista
import com.cjstudio.start.databinding.ActivityHomeMotoristaBinding
import com.cjstudio.start.ui.common.Navegacao
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.ui.common.mostrarMensagem
import com.cjstudio.start.ui.common.pedirLocalizacao
import com.cjstudio.start.ui.common.pedirNotificacaoSeNecessario
import com.cjstudio.start.ui.common.registrarPermissaoLocalizacao
import com.cjstudio.start.ui.common.registrarPermissaoNotificacao
import com.cjstudio.start.ui.common.temPermissaoLocalizacao
import com.cjstudio.start.ui.common.texto
import com.cjstudio.start.ui.corrida.CorridaViewModel
import com.cjstudio.start.ui.corrida.PainelCorrida
import com.cjstudio.start.util.MapaUtil
import com.cjstudio.start.util.MapaUtil.latLng
import com.cjstudio.start.util.carregarFotoRedonda
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import dagger.hilt.android.AndroidEntryPoint

// Tudo do motorista numa tela só: online/offline, corrida oferecida e, depois
// do aceite, a corrida inteira — no mesmo mapa entre as linhas (que segue o
// carro, sem abrir app de mapa externo) e no espaço de baixo (PainelCorrida).
@AndroidEntryPoint
class HomeMotoristaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeMotoristaBinding
    private val viewModel: HomeMotoristaViewModel by viewModels()
    private val corridaViewModel: CorridaViewModel by viewModels()
    private lateinit var painelCorrida: PainelCorrida
    private var mapa: GoogleMap? = null
    private var centralizado = false

    private val permissaoNotificacao = registrarPermissaoNotificacao()

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
        pedirNotificacaoSeNecessario(permissaoNotificacao)
        painelCorrida = PainelCorrida(this, binding.painelCorrida, corridaViewModel) { }

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
            atualizarMapa()
        }

        coletar(viewModel.motorista) { motorista ->
            if (motorista == null) return@coletar
            mostrar(motorista)
            corridaViewModel.acompanhar(motorista.corridaAtualId)
        }
        coletar(viewModel.aqui) { local ->
            if (local != null && !centralizado && viewModel.oferta.value == null && corridaViewModel.corrida.value == null) {
                centralizado = true
                mapa?.animateCamera(CameraUpdateFactory.newLatLngZoom(local.latLng(), 15f))
            }
        }
        coletar(viewModel.oferta) { oferta ->
            binding.cardOferta.visibility = if (oferta != null && corridaViewModel.corrida.value == null) View.VISIBLE else View.GONE
            if (oferta != null) mostrarOferta(oferta)
            atualizarMapa()
        }
        coletar(viewModel.aceitando) { aceitando ->
            binding.btnAceitar.isEnabled = !aceitando
            binding.btnAceitar.setText(if (aceitando) R.string.oferta_aceitando else R.string.oferta_aceitar)
        }
        coletar(corridaViewModel.corrida) { mostrarCorrida(it) }
        coletar(corridaViewModel.enviando) { painelCorrida.habilitar(!it) }
        coletar(corridaViewModel.mensagens) { mostrarMensagem(it) }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                HomeMotoristaViewModel.Evento.Saiu -> Navegacao.irLimpando(this, Navegacao.telaLogin())
                is HomeMotoristaViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    // Recebe corridas e envia posição com a tela aberta (nesta versão).
    override fun onStart() {
        super.onStart()
        viewModel.telaVisivel(true)
        corridaViewModel.iniciarEnvioPosicao()
    }

    override fun onStop() {
        super.onStop()
        viewModel.telaVisivel(false)
        corridaViewModel.pararEnvioPosicao()
    }

    @SuppressLint("MissingPermission")
    private fun ativarMinhaLocalizacao() {
        if (temPermissaoLocalizacao()) mapa?.isMyLocationEnabled = true
    }

    private fun mostrarOferta(oferta: HomeMotoristaViewModel.Oferta) {
        val c = oferta.corrida
        val previa = oferta.previa
        binding.tvOfertaValor.text = getString(R.string.oferta_valor_aprox, MapaUtil.valor(previa.valorTotal))
        binding.tvOfertaQuarto.visibility = if (c.quartoPassageiro) View.VISIBLE else View.GONE
        binding.tvOfertaResumo.text = getString(
            R.string.oferta_resumo,
            MapaUtil.distancia(oferta.metrosAtePartida.toLong()),
            MapaUtil.distancia(c.distanciaMetros),
            MapaUtil.duracao(c.duracaoSegundos)
        ) + "\n" + getString(R.string.oferta_voce_recebe, MapaUtil.valor(previa.valorMotorista))
        binding.tvOfertaPartida.text = c.origem?.endereco
        binding.tvOfertaDestino.text = c.destino?.endereco
    }

    // Com corrida, o espaço de baixo é dela (some o botão online e a oferta).
    private fun mostrarCorrida(corrida: Corrida?) {
        binding.painelCorrida.root.visibility = if (corrida != null) View.VISIBLE else View.GONE
        binding.rodapeOnline.visibility = if (corrida != null) View.GONE else View.VISIBLE
        if (corrida != null) {
            binding.cardOferta.visibility = View.GONE
            painelCorrida.mostrar(corrida)
        } else {
            painelCorrida.limparDesenho()
            binding.cardOferta.visibility = if (viewModel.oferta.value != null) View.VISIBLE else View.GONE
        }
        atualizarMapa()
    }

    // Um mapa só: corrida > oferta > só a posição do motorista.
    private fun atualizarMapa() {
        val m = mapa ?: return
        val folga = resources.getDimensionPixelSize(R.dimen.folga_enquadrar)
        corridaViewModel.corrida.value?.let {
            m.setPadding(0, 0, 0, 0)
            painelCorrida.desenhar(m, it, folga)
            return
        }
        val oferta = viewModel.oferta.value
        // Com a oferta por cima do mapa, a rota é enquadrada acima dela.
        m.setPadding(0, 0, 0, if (oferta != null) resources.getDimensionPixelSize(R.dimen.folga_painel_corrida) else 0)
        m.clear()
        val c = oferta?.corrida ?: return
        val origem = c.origem ?: return
        val destino = c.destino ?: return
        MapaUtil.desenharRota(this, m, c.polyline)
        MapaUtil.marcadorPartida(m, origem)
        MapaUtil.marcadorDestino(m, destino)
        MapaUtil.enquadrar(m, listOfNotNull(viewModel.aqui.value, origem, destino), folga)
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
