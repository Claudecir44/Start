package com.cjstudio.start.ui.corrida

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Corrida
import com.cjstudio.start.data.model.Local
import com.cjstudio.start.data.model.StatusCorrida
import com.cjstudio.start.databinding.ActivityCorridaBinding
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.ui.common.mostrarMensagem
import com.cjstudio.start.util.MapaUtil
import com.cjstudio.start.util.MapaUtil.latLng
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Marker

// Base das telas de corrida (passageiro e motorista): mapa com rota,
// partida/destino e o carro andando; endereços e valor. Cada tela filha
// decide o texto da situação e os botões (mostrar).
abstract class CorridaBaseActivity : AppCompatActivity() {

    protected lateinit var binding: ActivityCorridaBinding
    protected val viewModel: CorridaViewModel by viewModels()

    private var mapa: GoogleMap? = null
    private var carro: Marker? = null
    private var rotaDesenhada: String? = null
    private var enquadradoEm: String? = null

    protected abstract fun mostrar(corrida: Corrida)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCorridaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        (supportFragmentManager.findFragmentById(R.id.mapa) as SupportMapFragment).getMapAsync { m ->
            mapa = m
            MapaUtil.configurar(this, m)
            m.setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.folga_painel_corrida))
            viewModel.corrida.value?.let { desenhar(it) }
        }

        coletar(viewModel.corrida) { corrida ->
            if (corrida == null) return@coletar
            mostrarComum(corrida)
            mostrar(corrida)
            desenhar(corrida)
        }
        coletar(viewModel.enviando) { enviando ->
            listOf(binding.btnPrincipal, binding.btnSecundario, binding.btnCancelar).forEach { it.isEnabled = !enviando }
        }
        coletar(viewModel.mensagens) { mostrarMensagem(it) }
    }

    private fun mostrarComum(c: Corrida) {
        binding.tvPartida.text = c.origem?.endereco
        binding.tvDestino.text = c.destino?.endereco
        binding.tvResumo.text = getString(R.string.corrida_resumo, MapaUtil.distancia(c.distanciaMetros), MapaUtil.duracao(c.duracaoSegundos))
        binding.tvValor.text = MapaUtil.valor(c.valor)
        binding.progressoBusca.visibility = if (c.status == StatusCorrida.PROCURANDO) View.VISIBLE else View.GONE
    }

    // Rota e pontos fixos só redesenham se mudarem; o carro só se move. A
    // câmera reenquadra a cada mudança de situação (buscar → levar).
    private fun desenhar(c: Corrida) {
        val m = mapa ?: return
        val origem = c.origem ?: return
        val destino = c.destino ?: return
        if (rotaDesenhada != c.polyline) {
            m.clear()
            carro = null
            MapaUtil.desenharRota(this, m, c.polyline)
            MapaUtil.marcadorPartida(m, origem)
            MapaUtil.marcadorDestino(m, destino)
            rotaDesenhada = c.polyline
        }
        val posicaoCarro = c.motoristaLocal?.takeIf { c.status in setOf(StatusCorrida.ACEITA, StatusCorrida.EM_ANDAMENTO) }
        if (posicaoCarro != null) {
            if (carro == null) carro = MapaUtil.marcadorCarro(m, posicaoCarro) else carro?.position = posicaoCarro.latLng()
        } else {
            carro?.remove()
            carro = null
        }
        if (enquadradoEm != c.status) {
            enquadradoEm = c.status
            val pontos: List<Local> = when (c.status) {
                StatusCorrida.ACEITA -> listOfNotNull(posicaoCarro, origem)
                StatusCorrida.EM_ANDAMENTO -> listOfNotNull(posicaoCarro, destino)
                else -> listOf(origem, destino)
            }
            MapaUtil.enquadrar(m, pontos, resources.getDimensionPixelSize(R.dimen.folga_enquadrar))
        }
    }

    companion object {
        const val EXTRA_CORRIDA = "corridaId"
    }
}
