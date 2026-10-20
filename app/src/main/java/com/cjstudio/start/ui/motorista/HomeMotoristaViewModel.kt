package com.cjstudio.start.ui.motorista

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.data.model.CalculoCorrida
import com.cjstudio.start.data.model.Corrida
import com.cjstudio.start.domain.CalculadoraTarifa
import com.cjstudio.start.data.model.Local
import com.cjstudio.start.data.model.Motorista
import com.cjstudio.start.data.model.Tarifa
import com.cjstudio.start.data.repository.IAuthRepository
import com.cjstudio.start.data.repository.ICorridaRepository
import com.cjstudio.start.data.repository.IFalhasRepository
import com.cjstudio.start.data.repository.ILocalizacaoRepository
import com.cjstudio.start.data.repository.INotificacaoRepository
import com.cjstudio.start.data.repository.IMotoristaRepository
import com.cjstudio.start.data.repository.ITarifaRepository
import com.cjstudio.start.ui.common.UiTexto
import com.cjstudio.start.ui.common.paraUiTexto
import com.cjstudio.start.util.MapaUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Tela inicial do motorista. Online e aprovado (com a tela aberta): envia a
// posição e mostra a corrida esperando motorista mais perto dele (dentro do
// raio da tarifa). Aceitar passa pelo servidor; quando ele grava
// corridaAtualId no perfil, a corrida aparece na mesma tela (CorridaViewModel).
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeMotoristaViewModel @Inject constructor(
    private val auth: IAuthRepository,
    private val motoristas: IMotoristaRepository,
    private val corridas: ICorridaRepository,
    private val localizacao: ILocalizacaoRepository,
    private val notificacoes: INotificacaoRepository,
    private val tarifas: ITarifaRepository,
    private val falhas: IFalhasRepository
) : ViewModel() {

    sealed interface Evento {
        data object Saiu : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    // Corrida oferecida + distância até a partida (linha reta) + prévia do
    // valor com o deslocamento estimado (o valor final sai no aceite).
    data class Oferta(val corrida: Corrida, val metrosAtePartida: Double, val previa: CalculoCorrida)

    val motorista: StateFlow<Motorista?> = motoristas.escutarLogado()
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val telaVisivel = MutableStateFlow(false)
    private val _aqui = MutableStateFlow<Local?>(null)
    val aqui: StateFlow<Local?> = _aqui
    private val recusadas = MutableStateFlow<Set<String>>(emptySet())
    private var tarifa = Tarifa()

    private val _aceitando = MutableStateFlow(false)
    val aceitando: StateFlow<Boolean> = _aceitando

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    // Pronto pra receber: tela aberta + aprovado + online + sem corrida.
    private val recebendo: Flow<Boolean> = combine(telaVisivel, motorista) { visivel, m ->
        visivel && m != null && m.aprovado && m.online && m.corridaAtualId == null
    }.distinctUntilChanged()

    val oferta: StateFlow<Oferta?> = recebendo.flatMapLatest { ativo ->
        if (!ativo) flowOf(emptyList()) else corridas.escutarDisponiveis().catch { emit(emptyList()) }
    }.let { lista ->
        combine(lista, _aqui, recusadas) { disponiveis, aqui, recusadasAgora ->
            if (aqui == null) return@combine null
            val uid = auth.uidLogado()
            disponiveis.asSequence()
                .filter { it.id !in recusadasAgora && it.passageiroId != uid }
                .mapNotNull { c -> c.origem?.let { ofertaDe(c, MapaUtil.distanciaMetros(aqui, it)) } }
                .filter { it.metrosAtePartida <= tarifa.raioBuscaKm * 1000 }
                .minByOrNull { it.metrosAtePartida }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        falhas.definirUsuario(auth.uidLogado())
        // Token de push no perfil (avisos de corrida).
        viewModelScope.launch { notificacoes.registrarToken() }
        viewModelScope.launch { tarifas.buscar().onSuccess { tarifa = it } }
        // Posição: a cada 10 s enquanto recebendo corridas.
        viewModelScope.launch {
            recebendo.flatMapLatest { ativo ->
                if (ativo) localizacao.acompanhar(INTERVALO_POSICAO_MS).catch { } else emptyFlow()
            }.collect { local ->
                _aqui.value = local
                motoristas.atualizarLocalizacao(local)
            }
        }
    }

    private fun ofertaDe(c: Corrida, metrosAtePartida: Double): Oferta {
        val (deslocMetros, deslocSegundos) = CalculadoraTarifa.estimarDeslocamento(metrosAtePartida)
        val previa = CalculadoraTarifa.calcular(
            tarifa, deslocMetros, deslocSegundos, c.distanciaMetros, c.duracaoSegundos, c.quartoPassageiro, paradas = 0
        )
        return Oferta(c, metrosAtePartida, previa)
    }

    fun telaVisivel(visivel: Boolean) {
        telaVisivel.value = visivel
        if (visivel) viewModelScope.launch { localizacao.atual()?.let { _aqui.value = it } }
    }

    fun alternarOnline() {
        val atual = motorista.value ?: return
        if (!atual.aprovado) return
        viewModelScope.launch {
            motoristas.definirOnline(!atual.online).onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }

    fun aceitar(oferta: Oferta) {
        val id = oferta.corrida.id ?: return
        if (_aceitando.value) return
        _aceitando.value = true
        viewModelScope.launch {
            // Sucesso: o perfil recebe corridaAtualId e o init abre a corrida.
            corridas.aceitar(id).onFailure {
                recusadas.value = recusadas.value + id
                _eventos.send(Evento.Mensagem(it.paraUiTexto()))
            }
            _aceitando.value = false
        }
    }

    fun recusar(oferta: Oferta) {
        oferta.corrida.id?.let { recusadas.value = recusadas.value + it }
    }

    fun sair() {
        viewModelScope.launch {
            // Ninguém fica "online" depois de sair do app.
            if (motorista.value?.online == true) motoristas.definirOnline(false)
            auth.sair()
            _eventos.send(Evento.Saiu)
        }
    }

    private companion object {
        const val INTERVALO_POSICAO_MS = 10_000L
    }
}
