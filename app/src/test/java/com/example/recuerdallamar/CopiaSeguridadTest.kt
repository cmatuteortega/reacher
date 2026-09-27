package com.example.recuerdallamar

import com.example.recuerdallamar.datos.Ajustes
import com.example.recuerdallamar.datos.Contacto
import com.example.recuerdallamar.datos.CopiaSeguridad
import com.example.recuerdallamar.datos.MedioContacto
import com.example.recuerdallamar.datos.TemaElegido
import com.example.recuerdallamar.datos.VistaPersonas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay

class CopiaSeguridadTest {
    private val ana = Contacto(
        id = 12,
        nombre = "Ana",
        telefono = "+34 600 11 22 33",
        frecuenciaDias = 10,
        ultimoContacto = LocalDate.of(2026, 9, 1),
        medio = MedioContacto.WHATSAPP,
        descartes = 3,
        pospuestoHasta = LocalDateTime.of(2026, 9, 20, 18, 0),
        pausadoHasta = LocalDateTime.of(2026, 10, 1, 0, 0),
        notas = "Hijos: Leo y \"Mia\"",
        cumpleanos = MonthDay.of(2, 29),
        circulo = "Familia",
        cumpleanosManual = true,
    )

    @Test
    fun idaYVuelta() {
        val texto = CopiaSeguridad.escribir(listOf(ana), Ajustes())
        val leida = CopiaSeguridad.leer(texto).personas.single()
        // Lo que es de un aviso en curso no viaja; el id lo pone la base de datos nueva.
        assertEquals(ana.copy(id = 0, descartes = 0, pospuestoHasta = null), leida)
    }

    @Test
    fun ajustesViajanSalvoLoDeEsteTelefono() {
        val origen = Ajustes(
            avisosActivos = false,
            pausaHasta = LocalDate.of(2026, 10, 5),
            horaDesde = 20,
            horaHasta = 8,
            horasPosponer = 4,
            medioPorDefecto = MedioContacto.SMS,
            tema = TemaElegido.OSCURO,
            vista = VistaPersonas.LISTA,
        )
        val texto = CopiaSeguridad.escribir(emptyList(), origen)
        val destino = CopiaSeguridad.leer(texto).aplicarA(Ajustes(bienvenidaHecha = true))
        assertEquals(origen.copy(bienvenidaHecha = true), destino)
    }

    @Test
    fun camposQueFaltanOSobran() {
        val texto = """
            {
              "app": "contacto",
              "formato": 1,
              "algo_nuevo": [1, 2, 3],
              "personas": [
                { "nombre": "Luis", "telefono": "612345678", "color": "azul" },
                { "nombre": "", "telefono": "" },
                { "nombre": "Eva", "telefono": "1", "frecuencia_dias": 0, "medio": "PALOMA", "cumpleanos": "31 de junio" }
              ]
            }
        """.trimIndent()
        val leido = CopiaSeguridad.leer(texto)
        assertEquals(listOf("Luis", "Eva"), leido.personas.map { it.nombre })
        val luis = leido.personas[0]
        assertEquals(7, luis.frecuenciaDias)
        assertEquals(MedioContacto.MARCADOR, luis.medio)
        val eva = leido.personas[1]
        assertEquals(1, eva.frecuenciaDias)
        assertEquals(MedioContacto.MARCADOR, eva.medio)
        assertNull(eva.cumpleanos)
        // Sin ajustes en el archivo, se quedan los de aqui.
        val aqui = Ajustes(horaDesde = 7)
        assertEquals(aqui, leido.aplicarA(aqui))
    }

    @Test(expected = CopiaSeguridad.ArchivoNoValido::class)
    fun noEsJson() {
        CopiaSeguridad.leer("hola")
    }

    @Test(expected = CopiaSeguridad.ArchivoNoValido::class)
    fun esDeOtraApp() {
        CopiaSeguridad.leer("""{"app": "otra", "formato": 1}""")
    }

    @Test(expected = CopiaSeguridad.ArchivoNoValido::class)
    fun formatoMasNuevo() {
        CopiaSeguridad.leer("""{"app": "contacto", "formato": ${CopiaSeguridad.FORMATO + 1}}""")
    }

    @Test
    fun mismoTelefono() {
        assertTrue(CopiaSeguridad.mismoTelefono("+34 600-11-22-33", "+34600112233"))
        assertTrue(CopiaSeguridad.mismoTelefono("(612) 34 56 78", "612345678"))
        assertFalse(CopiaSeguridad.mismoTelefono("+34600112233", "34600112233"))
        assertFalse(CopiaSeguridad.mismoTelefono("", ""))
    }
}
