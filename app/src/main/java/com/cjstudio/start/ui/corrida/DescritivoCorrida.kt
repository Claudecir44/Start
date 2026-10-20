package com.cjstudio.start.ui.corrida

import android.content.Context
import com.cjstudio.start.R
import com.cjstudio.start.data.model.CalculoCorrida
import com.cjstudio.start.util.MapaUtil
import kotlin.math.roundToInt

// Texto do "como foi calculado" mostrado no fim da corrida (passageiro e
// motorista) e no admin. paraMotorista = mostra quanto fica com ele e quanto
// repassa à plataforma (o pagamento é direto ao motorista nesta versão).
object DescritivoCorrida {

    fun texto(context: Context, c: CalculoCorrida, paraMotorista: Boolean): String {
        val linhas = mutableListOf<String>()
        fun s(id: Int, vararg args: Any) = context.getString(id, *args)
        fun pct(v: Double) = "${(v * 100).roundToInt()}%"

        if (c.deslocamentoMetros > 0) {
            linhas += s(R.string.calc_deslocamento, MapaUtil.distancia(c.deslocamentoMetros), MapaUtil.duracao(c.deslocamentoSegundos))
        }
        linhas += s(R.string.calc_viagem, MapaUtil.distancia(c.viagemMetros), MapaUtil.duracao(c.viagemSegundos))
        linhas += s(R.string.calc_total, MapaUtil.distancia(c.totalMetros), MapaUtil.duracao(c.totalSegundos))
        linhas += ""
        linhas += if (c.regra == "minimo") {
            s(R.string.calc_regra_minimo, km(c.kmFaixaMinima), MapaUtil.valor(c.valorBase))
        } else {
            s(R.string.calc_regra_tarifa, MapaUtil.valor(c.bandeirada), MapaUtil.valor(c.porKm), MapaUtil.valor(c.porMinuto), MapaUtil.valor(c.valorBase))
        }
        if (c.quartoPassageiro) {
            linhas += s(R.string.calc_quarto, pct(c.percentualQuartoPassageiro), MapaUtil.valor(c.acrescimoQuartoPassageiro))
        }
        if (c.paradas > 0) {
            linhas += s(R.string.calc_paradas, c.paradas, MapaUtil.valor(c.valorPorParada), MapaUtil.valor(c.valorParadas))
        }
        linhas += s(R.string.calc_motorista, MapaUtil.valor(c.valorMotorista))
        linhas += s(R.string.calc_plataforma, pct(c.percentualPlataforma), MapaUtil.valor(c.taxaPlataforma))
        linhas += s(R.string.calc_total_pago, MapaUtil.valor(c.valorTotal))
        if (paraMotorista) {
            linhas += ""
            linhas += s(R.string.calc_repasse, MapaUtil.valor(c.valorTotal), MapaUtil.valor(c.valorMotorista), MapaUtil.valor(c.taxaPlataforma))
        }
        return linhas.joinToString("\n")
    }

    private fun km(v: Double) = if (v % 1.0 == 0.0) "${v.toInt()} km" else "$v km"
}
