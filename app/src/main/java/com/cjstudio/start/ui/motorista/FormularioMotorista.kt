package com.cjstudio.start.ui.motorista

import android.net.Uri
import android.widget.ArrayAdapter
import coil3.load
import com.cjstudio.start.R
import com.cjstudio.start.data.model.Motorista
import com.cjstudio.start.data.model.Veiculo
import com.cjstudio.start.databinding.IncludeDadosMotoristaBinding
import com.cjstudio.start.util.mascaraData
import com.cjstudio.start.util.mascaraPlaca
import java.util.Locale

// Bloco nascimento + CNH + carro (include_dados_motorista.xml), usado no
// cadastro e na edição do motorista.
fun IncludeDadosMotoristaBinding.configurar() {
    etNascimento.mascaraData()
    etCnhValidade.mascaraData()
    etPlaca.mascaraPlaca()
    val contexto = root.context
    spCnhCategoria.adapter = ArrayAdapter(
        contexto, R.layout.item_spinner,
        listOf(contexto.getString(R.string.motorista_cnh_categoria_hint)) + ValidacaoMotorista.CATEGORIAS_CNH
    ).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }
    fotoCnh.tvDocumento.setText(R.string.motorista_foto_cnh)
    fotoCrlv.tvDocumento.setText(R.string.motorista_foto_crlv)
}

// Posição 0 do spinner é o texto "Categoria".
fun IncludeDadosMotoristaBinding.categoriaSelecionada(): String =
    if (spCnhCategoria.selectedItemPosition > 0) spCnhCategoria.selectedItem.toString() else ""

fun IncludeDadosMotoristaBinding.dados(nomeCompleto: String, telefone: String) = ValidacaoMotorista.Dados(
    nomeCompleto = nomeCompleto,
    dataNascimento = etNascimento.text.toString(),
    telefone = telefone,
    cnhNumero = etCnhNumero.text.toString(),
    cnhCategoria = categoriaSelecionada(),
    cnhValidade = etCnhValidade.text.toString(),
    marca = etMarca.text.toString(),
    modelo = etModelo.text.toString(),
    ano = etAno.text.toString(),
    cor = etCor.text.toString(),
    placa = etPlaca.text.toString(),
    tipoVeiculo = when (rgTipoVeiculo.checkedRadioButtonId) {
        R.id.rbHatch -> ValidacaoMotorista.TIPO_HATCH
        R.id.rbSedan -> ValidacaoMotorista.TIPO_SEDAN
        else -> null
    },
    quatroPortas = cbQuatroPortas.isChecked,
    arCondicionado = cbArCondicionado.isChecked
)

fun IncludeDadosMotoristaBinding.preencher(m: Motorista) {
    etNascimento.setText(m.dataNascimento)
    etCnhNumero.setText(m.cnhNumero)
    etCnhValidade.setText(m.cnhValidade)
    val posicao = ValidacaoMotorista.CATEGORIAS_CNH.indexOf(m.cnhCategoria)
    if (posicao >= 0) spCnhCategoria.setSelection(posicao + 1)
    m.veiculo?.let { v ->
        etMarca.setText(v.marca)
        etModelo.setText(v.modelo)
        etAno.setText(v.ano?.toString())
        etCor.setText(v.cor)
        etPlaca.setText(v.placa)
        when (v.tipo) {
            ValidacaoMotorista.TIPO_HATCH -> rbHatch.isChecked = true
            ValidacaoMotorista.TIPO_SEDAN -> rbSedan.isChecked = true
        }
        cbQuatroPortas.isChecked = v.quatroPortas
        cbArCondicionado.isChecked = v.arCondicionado
    }
    mostrarDocumento(fotoCnh.ivDocumento, m.cnhFotoUrl)
    mostrarDocumento(fotoCrlv.ivDocumento, m.crlvFotoUrl)
}

fun IncludeDadosMotoristaBinding.mostrarFotoNova(cnh: Uri?, crlv: Uri?) {
    cnh?.let {
        fotoCnh.ivDocumento.load(it)
        fotoCnh.tvDocumento.setText(R.string.motorista_foto_trocar)
    }
    crlv?.let {
        fotoCrlv.ivDocumento.load(it)
        fotoCrlv.tvDocumento.setText(R.string.motorista_foto_trocar)
    }
}

private fun mostrarDocumento(view: android.widget.ImageView, url: String?) {
    if (!url.isNullOrEmpty()) view.load(url)
}

fun ValidacaoMotorista.Dados.veiculo() = Veiculo(
    marca = marca.trim(),
    modelo = modelo.trim(),
    ano = ano.toIntOrNull(),
    cor = cor.trim(),
    placa = placa.uppercase(Locale.ROOT),
    tipo = tipoVeiculo,
    quatroPortas = quatroPortas,
    arCondicionado = arCondicionado
)
