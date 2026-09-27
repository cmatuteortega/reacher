package com.example.recuerdallamar.datos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ContactoTest {
    private val hoy = LocalDate.of(2026, 9, 27)
    private val ahora = LocalDateTime.of(2026, 9, 27, 12, 0)

    private fun persona(nombre: String = "Ana", haceDias: Long, cada: Int) =
        Contacto(nombre = nombre, telefono = "600", frecuenciaDias = cada, ultimoContacto = hoy.minusDays(haceDias))

    @Test
    fun urgenciaEsLaParteConsumidaDeLaCadencia() {
        assertEquals(0f, persona(haceDias = 0, cada = 7).urgencia(hoy), 0.0001f)
        assertEquals(0.5f, persona(haceDias = 7, cada = 14).urgencia(hoy), 0.0001f)
        assertEquals(1f, persona(haceDias = 7, cada = 7).urgencia(hoy), 0.0001f)
        assertEquals(2f, persona(haceDias = 14, cada = 7).urgencia(hoy), 0.0001f)
    }

    @Test
    fun urgenciaRelativaACadaPersona() {
        // 10 dias es poco para quien va cada mes y mucho para quien va cada semana.
        assertTrue(persona(haceDias = 10, cada = 7).urgencia(hoy) > persona(haceDias = 10, cada = 30).urgencia(hoy))
    }

    @Test
    fun urgenciaNuncaNegativaNiDivideEntreCero() {
        // Ultimo contacto en el futuro (reloj cambiado) y frecuencia 0 de una copia rara.
        assertEquals(0f, persona(haceDias = -3, cada = 7).urgencia(hoy), 0.0001f)
        assertEquals(5f, persona(haceDias = 5, cada = 0).urgencia(hoy), 0.0001f)
    }

    @Test
    fun tocaLlamarDesdeElDiaQueSeCumpleLaFrecuencia() {
        assertFalse(persona(haceDias = 6, cada = 7).tocaLlamar(hoy))
        assertTrue(persona(haceDias = 7, cada = 7).tocaLlamar(hoy))
        assertTrue(persona(haceDias = 30, cada = 7).tocaLlamar(hoy))
        assertEquals(hoy.plusDays(1), persona(haceDias = 6, cada = 7).proximoAviso())
    }

    @Test
    fun porUrgenciaPrimeroQuienMasLeTocaYLuegoPorNombre() {
        val gente = listOf(
            persona("carla", haceDias = 1, cada = 7),
            persona("Bea", haceDias = 14, cada = 7),
            persona("alba", haceDias = 14, cada = 7),
            persona("Dani", haceDias = 7, cada = 7),
        )
        assertEquals(listOf("alba", "Bea", "Dani", "carla"), gente.porUrgencia(hoy).map { it.nombre })
    }

    @Test
    fun pausadoHastaLaFechaSinIncluirla() {
        val ana = persona(haceDias = 0, cada = 7)
        assertFalse(ana.pausado(ahora))
        assertTrue(ana.copy(pausadoHasta = ahora.plusDays(3)).pausado(ahora))
        assertFalse(ana.copy(pausadoHasta = ahora).pausado(ahora))
        assertFalse(ana.copy(pausadoHasta = ahora.minusHours(1)).pausado(ahora))
    }

    @Test
    fun pospuestoConUnMinutoDeMargen() {
        val ana = persona(haceDias = 7, cada = 7)
        assertFalse(ana.pospuesto(ahora))
        assertTrue(ana.copy(pospuestoHasta = ahora.plusHours(2)).pospuesto(ahora))
        // El trabajo del aplazamiento puede arrancar un poco antes de la hora: ya no cuenta como pospuesto.
        assertFalse(ana.copy(pospuestoHasta = ahora.plusSeconds(30)).pospuesto(ahora))
        assertFalse(ana.copy(pospuestoHasta = ahora.plusMinutes(1)).pospuesto(ahora))
        assertTrue(ana.copy(pospuestoHasta = ahora.plusMinutes(2)).pospuesto(ahora))
    }
}
