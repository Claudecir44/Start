package com.cjstudio.start.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ValidacaoTest {

    private fun dia(ano: Int, mes: Int, dia: Int) = Calendar.getInstance().apply {
        clear()
        set(ano, mes - 1, dia)
    }

    @Test
    fun cpf() {
        assertTrue(CpfUtil.valido("529.982.247-25"))
        assertFalse(CpfUtil.valido("529.982.247-24"))
        assertFalse(CpfUtil.valido("111.111.111-11"))
        assertEquals("529.982.247-25", CpfUtil.formatar("52998224725"))
    }

    @Test
    fun placa() {
        assertTrue(Validacao.placa("ABC1D23"))
        assertTrue(Validacao.placa("abc1234"))
        assertFalse(Validacao.placa("AB1C234"))
    }

    @Test
    fun idade() {
        val hoje = dia(2026, 10, 20)
        assertEquals(21, Validacao.idade("20/10/2005", hoje))
        assertEquals(20, Validacao.idade("21/10/2005", hoje))
        assertNull(Validacao.idade("31/02/2000", hoje))
    }

    @Test
    fun validadeCnh() {
        val hoje = dia(2026, 10, 20)
        assertTrue(Validacao.dataFutura("20/10/2026", hoje))
        assertFalse(Validacao.dataFutura("19/10/2026", hoje))
    }

    @Test
    fun telefoneENome() {
        assertTrue(Validacao.telefone("(51) 99999-9999"))
        assertFalse(Validacao.telefone("9999-9999"))
        assertTrue(Validacao.nomeCompleto("Ana Souza"))
        assertFalse(Validacao.nomeCompleto("Ana"))
        assertEquals("(51) 99999-9999", Mascaras.telefone("51999999999"))
    }
}
