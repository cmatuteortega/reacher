package com.example.recuerdallamar.ui

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.recuerdallamar.App
import com.example.recuerdallamar.MainActivity
import com.example.recuerdallamar.R
import com.example.recuerdallamar.datos.AlmacenAjustes
import com.example.recuerdallamar.datos.BaseDatos
import com.example.recuerdallamar.datos.Contacto
import com.example.recuerdallamar.datos.VistaPersonas
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * La app entera, de MainActivity para abajo, con Robolectric. Las animaciones
 * sin fin (caras, burbujas, orbitas) usan withInfiniteAnimationFrameNanos:
 * en las pruebas se paran solas y la pantalla se queda quieta.
 */
@RunWith(AndroidJUnit4::class)
class FlujosTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: App = ApplicationProvider.getApplicationContext()
    private val dao = BaseDatos.de(app).contactos()
    private var escenario: ActivityScenario<MainActivity>? = null

    private fun texto(@StringRes id: Int): String = app.getString(id)

    private fun abrir() {
        escenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    /** Alguien ya guardado y la bienvenida hecha, en la vista de lista (la que no se mueve). */
    private fun conGente(vararg gente: Contacto): List<Long> {
        AlmacenAjustes.de(app).cambiar { it.copy(bienvenidaHecha = true, vista = VistaPersonas.LISTA) }
        return runBlocking { gente.map { dao.insertar(it) } }
    }

    @After
    fun cerrar() {
        escenario?.close()
    }

    @Test
    fun bienvenidaSinAnadirANadie() {
        abrir()
        compose.onNodeWithText(texto(R.string.empezar)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(texto(R.string.ahora_no)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(texto(R.string.entendido)).performClick()
        compose.waitForIdle()

        assertTrue(AlmacenAjustes.de(app).ajustes.value.bienvenidaHecha)
        compose.onNodeWithContentDescription(texto(R.string.ajustes)).assertIsDisplayed()
    }

    @Test
    fun quienYaTeniaGenteNoVeLaBienvenida() {
        conGente(Contacto(nombre = "Ana", telefono = "600", frecuenciaDias = 7))
        AlmacenAjustes.de(app).cambiar { it.copy(bienvenidaHecha = false) }
        abrir()
        compose.onNodeWithText(texto(R.string.empezar)).assertDoesNotExist()
        assertTrue(AlmacenAjustes.de(app).ajustes.value.bienvenidaHecha)
    }

    @Test
    fun abrirFichaYAnotarLaLlamada() {
        val (id) = conGente(
            Contacto(nombre = "Ana", telefono = "600", frecuenciaDias = 7, ultimoContacto = LocalDate.now().minusDays(10)),
        )
        abrir()
        compose.onNodeWithText("Ana").performClick()
        compose.waitForIdle()

        // En la ficha: con lector de pantalla, "He llamado hoy" es un toque (sin mantener).
        compose.onNode(hasContentDescription(texto(R.string.he_llamado_hoy)))
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { runBlocking { dao.buscar(id) }!!.ultimoContacto == LocalDate.now() }
    }

    @Test
    fun ajustesDePrivacidad() {
        conGente(Contacto(nombre = "Ana", telefono = "600", frecuenciaDias = 7))
        abrir()
        compose.onNodeWithContentDescription(texto(R.string.ajustes)).performClick()
        compose.waitForIdle()

        // Cada fila es un solo interruptor: el nodo con su titulo es la fila entera.
        val filaFallos = compose.onNodeWithText(texto(R.string.privacidad_fallos))
        val filaEstadisticas = compose.onNodeWithText(texto(R.string.privacidad_estadisticas))
        filaEstadisticas.performScrollTo()
        filaFallos.assertIsOn()
        filaEstadisticas.assertIsOff()

        filaEstadisticas.performClick()
        filaFallos.performClick()
        compose.waitForIdle()

        val ajustes = AlmacenAjustes.de(app).ajustes.value
        assertEquals(true, ajustes.estadisticas)
        assertFalse(ajustes.informesFallos)
        filaEstadisticas.assertIsOn()
        filaFallos.assertIsOff()
    }
}
