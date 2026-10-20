package com.cjstudio.start.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Admin
import com.cjstudio.start.data.model.Corrida
import com.cjstudio.start.data.model.StatusCorrida
import com.cjstudio.start.data.model.Tarifa
import com.cjstudio.start.data.repository.ICorridaRepository
import com.cjstudio.start.data.repository.ITarifaRepository
import com.cjstudio.start.util.MapaUtil
import java.text.SimpleDateFormat
import java.util.Locale
import com.cjstudio.start.data.model.Motorista
import com.cjstudio.start.data.model.Passageiro
import com.cjstudio.start.data.model.StatusMotorista
import com.cjstudio.start.data.repository.IAdminRepository
import com.cjstudio.start.data.repository.IAuthRepository
import com.cjstudio.start.data.repository.IFalhasRepository
import com.cjstudio.start.data.repository.IMotoristaRepository
import com.cjstudio.start.data.repository.IPassageiroRepository
import com.cjstudio.start.ui.common.UiTexto
import com.cjstudio.start.ui.common.paraUiTexto
import com.cjstudio.start.ui.common.texto
import com.cjstudio.start.util.CpfUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Painel do admin. Igual ao Caronas: cada seção (Motoristas, Passageiros,
// Administradores) abre NA MESMA TELA, logo abaixo dos botões — nunca numa
// Activity separada. Só o detalhe de um motorista (documentos pra aprovar)
// abre à parte.
@HiltViewModel
class PainelAdminViewModel @Inject constructor(
    private val auth: IAuthRepository,
    private val admins: IAdminRepository,
    private val motoristas: IMotoristaRepository,
    private val passageiros: IPassageiroRepository,
    private val corridas: ICorridaRepository,
    private val tarifas: ITarifaRepository,
    private val falhas: IFalhasRepository
) : ViewModel() {

    enum class Secao { MOTORISTAS, PASSAGEIROS, ADMINISTRADORES, CORRIDAS, TARIFAS }

    // Filtro da seção Motoristas — null = todos.
    enum class FiltroMotorista(val status: String?) {
        EM_ANALISE(StatusMotorista.EM_ANALISE),
        APROVADOS(StatusMotorista.APROVADO),
        REPROVADOS(StatusMotorista.REPROVADO),
        BLOQUEADOS(StatusMotorista.BLOQUEADO),
        TODOS(null)
    }

    data class Estado(
        val admin: Admin? = null,
        val secao: Secao? = null,
        val filtroMotorista: FiltroMotorista = FiltroMotorista.EM_ANALISE,
        val itens: List<ItemPessoa> = emptyList(),
        val carregando: Boolean = false,
        val motoristasEmAnalise: Int = 0,
        // Seção Tarifas: valores atuais (null = ainda carregando).
        val tarifa: Tarifa? = null,
        val salvandoTarifa: Boolean = false
    )

    sealed interface Evento {
        data object Saiu : Evento
        data class Mensagem(val texto: UiTexto) : Evento
        // Toque num passageiro: confirmar bloqueio/desbloqueio.
        data class ConfirmarBloqueio(val passageiro: Passageiro) : Evento
        // Toque numa corrida: detalhes e o descritivo do valor.
        data class MostrarCorrida(val corrida: Corrida) : Evento
    }

    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    private var cacheMotoristas: List<Motorista> = emptyList()
    private var cacheCorridas: List<Corrida> = emptyList()
    private var cachePassageiros: List<Passageiro> = emptyList()

    init {
        falhas.definirUsuario(auth.uidLogado())
    }

    // Chamado no onResume: volta da análise de um motorista ou do cadastro
    // de admin já com a lista atualizada.
    fun atualizar() {
        viewModelScope.launch {
            admins.buscarLogado()
                .onSuccess { admin -> _estado.update { it.copy(admin = admin) } }
                .onFailure {
                    // Acesso de admin revogado com o app aberto.
                    sair()
                    return@launch
                }
            contarMotoristasEmAnalise()
            _estado.value.secao?.let { abrir(it) }
        }
    }

    private suspend fun contarMotoristasEmAnalise() {
        if (_estado.value.admin?.temPermissao("motoristas") != true) return
        motoristas.listarTodos().onSuccess { lista ->
            cacheMotoristas = lista
            _estado.update { e -> e.copy(motoristasEmAnalise = lista.count { it.status == StatusMotorista.EM_ANALISE }) }
        }
    }

    fun abrir(secao: Secao) {
        _estado.update { it.copy(secao = secao, carregando = true, itens = emptyList()) }
        viewModelScope.launch {
            val itens: Result<List<ItemPessoa>> = when (secao) {
                Secao.MOTORISTAS -> motoristas.listarTodos().map { lista ->
                    cacheMotoristas = lista
                    _estado.update { e -> e.copy(motoristasEmAnalise = lista.count { it.status == StatusMotorista.EM_ANALISE }) }
                    filtrarMotoristas()
                }
                Secao.PASSAGEIROS -> passageiros.listarTodos().map { lista ->
                    cachePassageiros = lista
                    lista.map { it.paraItem() }
                }
                Secao.ADMINISTRADORES -> admins.listarTodos().map { lista -> lista.map { it.paraItem() } }
                Secao.CORRIDAS -> corridas.listarTodas().map { lista ->
                    cacheCorridas = lista
                    lista.map { it.paraItem() }
                }
                Secao.TARIFAS -> tarifas.buscar().map { t ->
                    _estado.update { it.copy(tarifa = t) }
                    emptyList()
                }
            }
            itens.onSuccess { lista -> _estado.update { it.copy(itens = lista, carregando = false) } }
                .onFailure { e ->
                    _estado.update { it.copy(carregando = false) }
                    _eventos.send(Evento.Mensagem(e.paraUiTexto()))
                }
        }
    }

    fun filtrarMotoristas(filtro: FiltroMotorista) {
        _estado.update { it.copy(filtroMotorista = filtro) }
        _estado.update { it.copy(itens = filtrarMotoristas()) }
    }

    private fun filtrarMotoristas(): List<ItemPessoa> {
        val status = _estado.value.filtroMotorista.status
        return cacheMotoristas.filter { status == null || it.status == status }.map { it.paraItem() }
    }

    fun tocouPassageiro(id: String) {
        cachePassageiros.firstOrNull { it.id == id }?.let { _eventos.trySend(Evento.ConfirmarBloqueio(it)) }
    }

    fun alternarBloqueio(passageiro: Passageiro) {
        val uid = passageiro.id ?: return
        viewModelScope.launch {
            passageiros.definirBloqueio(uid, !passageiro.bloqueado)
                .onSuccess {
                    _eventos.send(Evento.Mensagem(texto(if (passageiro.bloqueado) R.string.admin_passageiro_desbloqueado else R.string.admin_passageiro_bloqueado)))
                    abrir(Secao.PASSAGEIROS)
                }
                .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }

    // Campos da seção Tarifas como digitados ("4,50"; percentuais em %, "15").
    data class FormTarifa(
        val minimoMotorista: String,
        val kmFaixa: String,
        val bandeirada: String,
        val porKm: String,
        val porMinuto: String,
        val taxaPlataformaPct: String,
        val quartoPassageiroPct: String,
        val porParada: String,
        val raioKm: String
    )

    // Mínimo, faixa e raio precisam ser > 0; os demais podem ser 0.
    fun salvarTarifa(form: FormTarifa) {
        fun n(v: String) = v.trim().replace(",", ".").toDoubleOrNull()
        val positivos = listOf(n(form.minimoMotorista), n(form.kmFaixa), n(form.raioKm))
        val livres = listOf(n(form.bandeirada), n(form.porKm), n(form.porMinuto), n(form.taxaPlataformaPct), n(form.quartoPassageiroPct), n(form.porParada))
        if (positivos.any { it == null || it <= 0.0 } || livres.any { it == null || it < 0.0 }) {
            _eventos.trySend(Evento.Mensagem(texto(R.string.tarifa_erro_valores)))
            return
        }
        val nova = Tarifa(
            bandeirada = n(form.bandeirada)!!,
            porKm = n(form.porKm)!!,
            porMinuto = n(form.porMinuto)!!,
            valorMinimoMotorista = n(form.minimoMotorista)!!,
            kmFaixaMinima = n(form.kmFaixa)!!,
            taxaPlataforma = n(form.taxaPlataformaPct)!! / 100,
            acrescimoQuartoPassageiro = n(form.quartoPassageiroPct)!! / 100,
            valorPorParada = n(form.porParada)!!,
            raioBuscaKm = n(form.raioKm)!!
        )
        _estado.update { it.copy(salvandoTarifa = true) }
        viewModelScope.launch {
            val resultado = tarifas.salvar(nova)
            _estado.update { it.copy(salvandoTarifa = false, tarifa = if (resultado.isSuccess) nova else it.tarifa) }
            _eventos.send(Evento.Mensagem(resultado.fold({ texto(R.string.tarifa_salva) }, { it.paraUiTexto() })))
        }
    }

    fun tocouCorrida(id: String) {
        cacheCorridas.firstOrNull { it.id == id }?.let { _eventos.trySend(Evento.MostrarCorrida(it)) }
    }

    fun trocarMinhaFoto(uri: android.net.Uri) {
        viewModelScope.launch {
            admins.atualizarMinhaFoto(uri)
                .onSuccess { url -> _estado.update { it.copy(admin = it.admin?.copy(fotoUrl = url)) } }
                .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }

    fun sair() {
        viewModelScope.launch {
            auth.sair()
            _eventos.send(Evento.Saiu)
        }
    }

    private fun Motorista.paraItem(): ItemPessoa {
        val (selo, cor) = when (status) {
            StatusMotorista.APROVADO -> R.string.status_aprovado to R.color.verde
            StatusMotorista.REPROVADO -> R.string.status_reprovado to R.color.vermelho
            StatusMotorista.BLOQUEADO -> R.string.status_bloqueado to R.color.vermelho
            else -> R.string.status_em_analise to R.color.amarelo
        }
        return ItemPessoa(
            id = id.orEmpty(),
            nome = nomeCompleto.orEmpty(),
            linha2 = listOfNotNull(telefone, email).joinToString("  ·  "),
            linha3 = veiculo?.let { "${it.descricao()}  ·  ${it.placa.orEmpty()}" }.orEmpty(),
            fotoUrl = fotoUrl,
            selo = selo,
            corSelo = cor
        )
    }

    private fun Passageiro.paraItem() = ItemPessoa(
        id = id.orEmpty(),
        nome = nomeCompleto.orEmpty(),
        linha2 = listOfNotNull(telefone, email).joinToString("  ·  "),
        linha3 = "CPF ${CpfUtil.formatar(cpf)}",
        fotoUrl = fotoUrl,
        selo = if (bloqueado) R.string.status_bloqueado else null,
        corSelo = if (bloqueado) R.color.vermelho else null
    )

    private fun Corrida.paraItem(): ItemPessoa {
        val (selo, cor) = when (status) {
            StatusCorrida.PROCURANDO -> R.string.situacao_corrida_procurando to R.color.amarelo
            StatusCorrida.ACEITA -> R.string.situacao_corrida_aceita to R.color.amarelo
            StatusCorrida.EM_ANDAMENTO -> R.string.situacao_corrida_em_andamento to R.color.verde
            StatusCorrida.FINALIZADA -> R.string.situacao_corrida_finalizada to R.color.verde
            else -> R.string.situacao_corrida_cancelada to R.color.vermelho
        }
        val quando = criadaEm?.let { DATA_HORA.format(it) }.orEmpty()
        return ItemPessoa(
            id = id.orEmpty(),
            nome = "${MapaUtil.valor(valor)}  ·  ${passageiroNome.orEmpty()}",
            linha2 = "${origem?.endereco.orEmpty()}  →  ${destino?.endereco.orEmpty()}",
            linha3 = listOfNotNull(quando, motoristaNome?.let { "Motorista: $it" }).joinToString("  ·  "),
            fotoUrl = passageiroFotoUrl,
            selo = selo,
            corSelo = cor
        )
    }

    private fun Admin.paraItem() = ItemPessoa(
        id = id.orEmpty(),
        nome = nomeCompleto,
        linha2 = listOfNotNull(telefone, email).joinToString("  ·  "),
        linha3 = "CPF ${CpfUtil.formatar(cpf)}",
        fotoUrl = fotoUrl,
        selo = when {
            ehMaster -> R.string.admin_selo_master
            ehColaborador -> R.string.admin_selo_colaborador
            else -> R.string.admin_selo_admin
        },
        corSelo = R.color.amarelo
    )

    private companion object {
        val DATA_HORA = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))
    }
}
