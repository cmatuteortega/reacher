package com.example.recuerdallamar.datos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class AjustesTest {
    private val hoy = LocalDate.of(2026, 9, 27)

    @Test
    fun franjaNormal() {
        val ajustes = Ajustes(horaDesde = 9, horaHasta = 21)
        assertFalse(ajustes.dentroDeFranja(8))
        assertTrue(ajustes.dentroDeFranja(9))
        assertTrue(ajustes.dentroDeFranja(20))
        // "Hasta" no se incluye.
        assertFalse(ajustes.dentroDeFranja(21))
    }

    @Test
    fun franjaQueCruzaLaMedianoche() {
        val ajustes = Ajustes(horaDesde = 20, horaHasta = 8)
        assertTrue(ajustes.dentroDeFranja(20))
        assertTrue(ajustes.dentroDeFranja(23))
        assertTrue(ajustes.dentroDeFranja(0))
        assertTrue(ajustes.dentroDeFranja(7))
        assertFalse(ajustes.dentroDeFranja(8))
        assertFalse(ajustes.dentroDeFranja(12))
        assertFalse(ajustes.dentroDeFranja(19))
    }

    @Test
    fun desdeIgualAHastaEsTodoElDia() {
        val ajustes = Ajustes(horaDesde = 10, horaHasta = 10)
        (0..23).forEach { assertTrue("hora $it", ajustes.dentroDeFranja(it)) }
        assertEquals(Duration.ZERO, ajustes.esperaHastaFranja(hoy.atTime(3, 0)))
    }

    @Test
    fun esperaHastaFranjaMismoDiaYDiaSiguiente() {
        val ajustes = Ajustes(horaDesde = 9, horaHasta = 21)
        assertEquals(Duration.ZERO, ajustes.esperaHastaFranja(hoy.atTime(12, 0)))
        // Antes de empezar: hasta las 9 de hoy.
        assertEquals(Duration.ofMinutes(90), ajustes.esperaHastaFranja(hoy.atTime(7, 30)))
        // Pasada la franja: hasta las 9 de manana.
        assertEquals(Duration.ofHours(12), ajustes.esperaHastaFranja(hoy.atTime(21, 0)))
    }

    @Test
    fun esperaConFranjaNocturna() {
        val ajustes = Ajustes(horaDesde = 20, horaHasta = 8)
        assertEquals(Duration.ZERO, ajustes.esperaHastaFranja(hoy.atTime(2, 0)))
        assertEquals(Duration.ofHours(8), ajustes.esperaHastaFranja(hoy.atTime(12, 0)))
        assertEquals(
            Duration.between(LocalDateTime.of(2026, 9, 27, 8, 0), LocalDateTime.of(2026, 9, 27, 20, 0)),
            ajustes.esperaHastaFranja(hoy.atTime(8, 0)),
        )
    }

    @Test
    fun avisosApagadosEnPausaVuelvenSolos() {
        assertTrue(Ajustes().avisosEncendidos(hoy))
        // Apagados sin fecha: hasta que se reactiven.
        assertFalse(Ajustes(avisosActivos = false).avisosEncendidos(hoy))
        val pausa = Ajustes(avisosActivos = false, pausaHasta = hoy.plusDays(3))
        assertFalse(pausa.avisosEncendidos(hoy))
        assertFalse(pausa.avisosEncendidos(hoy.plusDays(2)))
        assertTrue(pausa.avisosEncendidos(hoy.plusDays(3)))
    }

    @Test
    fun estadisticasSinPreguntarYFallosActivadosPorDefecto() {
        assertEquals(null, Ajustes().estadisticas)
        assertTrue(Ajustes().informesFallos)
    }
}
