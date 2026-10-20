package com.cjstudio.start.domain

import com.cjstudio.start.data.model.Tarifa
import org.junit.Assert.assertEquals
import org.junit.Test

// Mesmos casos de functions/tarifa.test.js — app e servidor têm que bater.
class CalculadoraTarifaTest {

    private val tarifa = Tarifa()

    @Test
    fun ateCincoKmMotoristaRecebeDezEPassageiroPagaOnzeECinquenta() {
        val c = CalculadoraTarifa.calcular(tarifa, 2000, 300, 3000, 480, quartoPassageiro = false, paradas = 0)
        assertEquals("minimo", c.regra)
        assertEquals(10.0, c.valorMotorista, 0.001)
        assertEquals(1.5, c.taxaPlataforma, 0.001)
        assertEquals(11.5, c.valorTotal, 0.001)
    }

    @Test
    fun acimaDeCincoKmUsaTarifaSobreOTotal() {
        val c = CalculadoraTarifa.calcular(tarifa, 2000, 300, 10000, 1200, quartoPassageiro = false, paradas = 0)
        assertEquals("tarifa", c.regra)
        assertEquals(33.1, c.valorBase, 0.001)
        assertEquals(4.97, c.taxaPlataforma, 0.001)
        assertEquals(38.07, c.valorTotal, 0.001)
    }

    @Test
    fun quartoPassageiroEParadas() {
        val c = CalculadoraTarifa.calcular(tarifa, 0, 0, 4000, 600, quartoPassageiro = true, paradas = 2)
        assertEquals(2.0, c.acrescimoQuartoPassageiro, 0.001)
        assertEquals(6.0, c.valorParadas, 0.001)
        assertEquals(18.0, c.valorMotorista, 0.001)
        assertEquals(20.7, c.valorTotal, 0.001)
    }

    @Test
    fun nuncaAbaixoDoMinimoDoMotorista() {
        val barata = Tarifa(bandeirada = 0.0, porKm = 0.5, porMinuto = 0.0)
        val c = CalculadoraTarifa.calcular(barata, 0, 0, 6000, 600, quartoPassageiro = false, paradas = 0)
        assertEquals(10.0, c.valorMotorista, 0.001)
    }
}
