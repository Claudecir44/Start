package com.cjstudio.start.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Corrida
import com.cjstudio.start.data.model.Passageiro
import com.cjstudio.start.ui.corrida.DescritivoCorrida
import com.cjstudio.start.data.model.Tarifa
import java.util.Locale
import com.cjstudio.start.databinding.ActivityPainelAdminBinding
import com.cjstudio.start.ui.admin.PainelAdminViewModel.FiltroMotorista
import com.cjstudio.start.ui.admin.PainelAdminViewModel.Secao
import com.cjstudio.start.ui.common.Navegacao
import com.cjstudio.start.ui.common.coletar
import com.cjstudio.start.ui.common.mostrarMensagem
import com.cjstudio.start.util.FotoUtil
import com.cjstudio.start.util.carregarFotoRedonda
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PainelAdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPainelAdminBinding
    private val viewModel: PainelAdminViewModel by viewModels()
    private val adapter = ItemPessoaAdapter { item -> tocou(item) }

    private val seletorFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.trocarMinhaFoto(FotoUtil.prepararParaEnvio(this, uri) ?: uri)
    }

    private val chipsFiltro by lazy {
        mapOf(
            binding.chipEmAnalise.id to FiltroMotorista.EM_ANALISE,
            binding.chipAprovados.id to FiltroMotorista.APROVADOS,
            binding.chipReprovados.id to FiltroMotorista.REPROVADOS,
            binding.chipBloqueados.id to FiltroMotorista.BLOQUEADOS,
            binding.chipTodos.id to FiltroMotorista.TODOS
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPainelAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvItens.layoutManager = LinearLayoutManager(this)
        binding.rvItens.adapter = adapter

        binding.ivFoto.setOnClickListener {
            seletorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnSair.setOnClickListener { viewModel.sair() }
        binding.btnMotoristas.setOnClickListener { viewModel.abrir(Secao.MOTORISTAS) }
        binding.btnPassageiros.setOnClickListener { viewModel.abrir(Secao.PASSAGEIROS) }
        binding.btnAdministradores.setOnClickListener { viewModel.abrir(Secao.ADMINISTRADORES) }
        binding.btnCorridas.setOnClickListener { viewModel.abrir(Secao.CORRIDAS) }
        binding.btnTarifas.setOnClickListener { viewModel.abrir(Secao.TARIFAS) }
        binding.btnSalvarTarifa.setOnClickListener {
            viewModel.salvarTarifa(
                PainelAdminViewModel.FormTarifa(
                    minimoMotorista = binding.etMinimoMotorista.text.toString(),
                    kmFaixa = binding.etKmFaixa.text.toString(),
                    bandeirada = binding.etBandeirada.text.toString(),
                    porKm = binding.etPorKm.text.toString(),
                    porMinuto = binding.etPorMinuto.text.toString(),
                    taxaPlataformaPct = binding.etTaxaPlataforma.text.toString(),
                    quartoPassageiroPct = binding.etQuartoPassageiro.text.toString(),
                    porParada = binding.etPorParada.text.toString(),
                    raioKm = binding.etRaio.text.toString()
                )
            )
        }
        binding.btnNovoAdmin.setOnClickListener { startActivity(Intent(this, CadastroAdminActivity::class.java)) }
        binding.chipsFiltro.setOnCheckedStateChangeListener { _, ids ->
            ids.firstOrNull()?.let { id -> chipsFiltro[id]?.let { viewModel.filtrarMotoristas(it) } }
        }

        coletar(viewModel.estado) { mostrar(it) }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                PainelAdminViewModel.Evento.Saiu -> Navegacao.irLimpando(this, Navegacao.telaLogin())
                is PainelAdminViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
                is PainelAdminViewModel.Evento.ConfirmarBloqueio -> confirmarBloqueio(evento.passageiro)
                is PainelAdminViewModel.Evento.MostrarCorrida -> mostrarCorrida(evento.corrida)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.atualizar()
    }

    private fun mostrar(estado: PainelAdminViewModel.Estado) {
        estado.admin?.let { admin ->
            binding.tvNome.text = admin.nome?.ifBlank { null } ?: admin.email.orEmpty()
            binding.tvPapel.setText(
                when {
                    admin.ehMaster -> R.string.admin_selo_master
                    admin.ehColaborador -> R.string.admin_selo_colaborador
                    else -> R.string.admin_selo_admin
                }
            )
            binding.ivFoto.carregarFotoRedonda(admin.fotoUrl)
            // Botão só aparece com a permissão (a trava de verdade é das regras).
            binding.btnMotoristas.visibility = visivel(admin.temPermissao("motoristas"))
            binding.btnPassageiros.visibility = visivel(admin.temPermissao("passageiros"))
            binding.btnAdministradores.visibility = visivel(admin.temPermissao("administradores"))
            binding.btnCorridas.visibility = visivel(admin.temPermissao("corridas"))
            binding.btnTarifas.visibility = visivel(admin.temPermissao("financeiro"))
        }

        binding.tvBadgeMotoristas.visibility = visivel(estado.motoristasEmAnalise > 0)
        binding.tvBadgeMotoristas.text = estado.motoristasEmAnalise.toString()

        val secao = estado.secao
        binding.areaSecao.visibility = visivel(secao != null)
        binding.tvDica.visibility = visivel(secao == null)
        if (secao == null) return

        binding.tvTituloSecao.setText(
            when (secao) {
                Secao.MOTORISTAS -> R.string.admin_motoristas
                Secao.PASSAGEIROS -> R.string.admin_passageiros
                Secao.ADMINISTRADORES -> R.string.admin_administradores
                Secao.CORRIDAS -> R.string.admin_corridas
                Secao.TARIFAS -> R.string.admin_tarifas
            }
        )
        binding.tvContador.text = resources.getQuantityString(R.plurals.admin_contador, estado.itens.size, estado.itens.size)
        binding.chipsFiltro.visibility = visivel(secao == Secao.MOTORISTAS)
        chipsFiltro.entries.firstOrNull { it.value == estado.filtroMotorista }?.let { binding.chipsFiltro.check(it.key) }
        binding.btnNovoAdmin.visibility = visivel(secao == Secao.ADMINISTRADORES)
        binding.progressBar.visibility = visivel(estado.carregando)
        val tarifas = secao == Secao.TARIFAS
        binding.formTarifa.visibility = visivel(tarifas && estado.tarifa != null)
        binding.rvItens.visibility = visivel(!tarifas)
        binding.tvContador.visibility = visivel(!tarifas)
        binding.tvVazio.visibility = visivel(!tarifas && !estado.carregando && estado.itens.isEmpty())
        binding.btnSalvarTarifa.isEnabled = !estado.salvandoTarifa
        estado.tarifa?.let { preencherTarifa(it) }
        adapter.submitList(estado.itens)
    }

    private fun tocou(item: ItemPessoa) {
        when (viewModel.estado.value.secao) {
            Secao.MOTORISTAS -> startActivity(
                Intent(this, DetalhesMotoristaAdminActivity::class.java).putExtra(DetalhesMotoristaAdminActivity.EXTRA_UID, item.id)
            )
            Secao.PASSAGEIROS -> viewModel.tocouPassageiro(item.id)
            Secao.CORRIDAS -> viewModel.tocouCorrida(item.id)
            else -> Unit
        }
    }

    // Corrida: endereços, pessoas e o descritivo de como o valor foi calculado.
    private fun mostrarCorrida(c: Corrida) {
        val linhas = mutableListOf<String>()
        linhas += getString(R.string.admin_corrida_partida, c.origem?.endereco.orEmpty())
        c.paradas.forEachIndexed { i, p -> linhas += getString(R.string.corrida_parada_n, i + 1, p.endereco.orEmpty()) }
        linhas += getString(R.string.admin_corrida_destino, c.destino?.endereco.orEmpty())
        linhas += getString(R.string.admin_corrida_pessoas, c.passageiroNome.orEmpty(), c.motoristaNome ?: "—")
        if (c.quartoPassageiro) linhas += getString(R.string.corrida_quatro_passageiros)
        linhas += ""
        c.calculo?.let { linhas += DescritivoCorrida.texto(this, it, paraMotorista = false) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.admin_corrida_detalhe)
            .setMessage(linhas.joinToString("\n"))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun confirmarBloqueio(passageiro: Passageiro) {
        val nome = passageiro.nomeCompleto.orEmpty()
        MaterialAlertDialogBuilder(this)
            .setTitle(nome)
            .setMessage(
                getString(if (passageiro.bloqueado) R.string.admin_desbloquear_passageiro_msg else R.string.admin_bloquear_passageiro_msg, nome)
            )
            .setPositiveButton(if (passageiro.bloqueado) R.string.admin_desbloquear else R.string.admin_bloquear) { _, _ ->
                viewModel.alternarBloqueio(passageiro)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    // Preenche só uma vez por tarifa carregada (não apaga o que está sendo digitado).
    private var tarifaMostrada: Tarifa? = null

    private fun preencherTarifa(t: Tarifa) {
        if (t == tarifaMostrada) return
        tarifaMostrada = t
        fun num(v: Double) = String.format(Locale("pt", "BR"), "%.2f", v)
        binding.etMinimoMotorista.setText(num(t.valorMinimoMotorista))
        binding.etKmFaixa.setText(num(t.kmFaixaMinima))
        binding.etBandeirada.setText(num(t.bandeirada))
        binding.etPorKm.setText(num(t.porKm))
        binding.etPorMinuto.setText(num(t.porMinuto))
        binding.etTaxaPlataforma.setText(num(t.taxaPlataforma * 100))
        binding.etQuartoPassageiro.setText(num(t.acrescimoQuartoPassageiro * 100))
        binding.etPorParada.setText(num(t.valorPorParada))
        binding.etRaio.setText(num(t.raioBuscaKm))
    }

    private fun visivel(mostrar: Boolean) = if (mostrar) View.VISIBLE else View.GONE
}
