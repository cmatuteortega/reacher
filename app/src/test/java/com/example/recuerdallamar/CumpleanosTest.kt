package com.example.recuerdallamar

import com.example.recuerdallamar.datos.Contacto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.MonthDay

class CumpleanosTest {

    @Test
    fun interpretaLosFormatosDeLaAgenda() {
        assertEquals(MonthDay.of(3, 12), CumpleanosAgenda.interpretar("1990-03-12"))
        assertEquals(MonthDay.of(3, 12), CumpleanosAgenda.interpretar("--03-12"))
        assertEquals(MonthDay.of(3, 12), CumpleanosAgenda.interpretar("19900312"))
        assertEquals(MonthDay.of(3, 12), CumpleanosAgenda.interpretar("12.03.1990"))
        assertEquals(MonthDay.of(2, 29), CumpleanosAgenda.interpretar("--02-29"))
    }

    @Test
    fun descartaFechasImposibles() {
        assertNull(CumpleanosAgenda.interpretar("1990-13-40"))
        assertNull(CumpleanosAgenda.interpretar("pronto"))
        assertNull(CumpleanosAgenda.interpretar(""))
    }

    private fun conCumple(dia: MonthDay?) = Contacto(nombre = "Ana", telefono = "600", frecuenciaDias = 7, cumpleanos = dia)

    @Test
    fun esCumpleanosSoloEseDia() {
        val ana = conCumple(MonthDay.of(3, 12))
        assertTrue(ana.esCumpleanos(LocalDate.of(2025, 3, 12)))
        assertFalse(ana.esCumpleanos(LocalDate.of(2025, 3, 13)))
        assertFalse(conCumple(null).esCumpleanos(LocalDate.of(2025, 3, 12)))
    }

    @Test
    fun el29DeFebreroSeCelebraEl28LosAnosNoBisiestos() {
        val ana = conCumple(MonthDay.of(2, 29))
        assertTrue(ana.esCumpleanos(LocalDate.of(2025, 2, 28)))
        assertTrue(ana.esCumpleanos(LocalDate.of(2024, 2, 29)))
        assertFalse(ana.esCumpleanos(LocalDate.of(2024, 2, 28)))
    }

    @Test
    fun cuentaLosDiasHastaElProximo() {
        val ana = conCumple(MonthDay.of(3, 12))
        assertEquals(0L, ana.diasHastaCumpleanos(LocalDate.of(2025, 3, 12)))
        assertEquals(2L, ana.diasHastaCumpleanos(LocalDate.of(2025, 3, 10)))
        // Ya paso este ano: cuenta hasta el que viene.
        assertEquals(364L, ana.diasHastaCumpleanos(LocalDate.of(2025, 3, 13)))
        assertNull(conCumple(null).diasHastaCumpleanos(LocalDate.of(2025, 3, 12)))
    }
}
