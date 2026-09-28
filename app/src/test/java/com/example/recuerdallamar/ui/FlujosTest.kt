package com.example.recuerdallamar.ui

import android.Manifest
import android.content.pm.PackageManager
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
import com.example.recuerdallamar.datos.MedioContacto
import com.example.recuerdallamar.datos.VistaPersonas
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
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
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        abrir()
        compose.onNodeWithText(texto(R.string.empezar)).performClick()
        compose.waitForIdle()

        // Cuando y como: la forma de contacto elegida se guarda al momento
        // como la de los que se anadan.
        compose.onNodeWithText(texto(R.string.whatsapp)).performClick()
        compose.waitForIdle()
        assertEquals(MedioContacto.WHATSAPP, AlmacenAjustes.de(app).ajustes.value.medioPorDefecto)
        compose.onNodeWithText(texto(R.string.continuar)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(texto(R.string.ahora_no)).performClick()
        compose.waitForIdle()

        // Sin mas pasos: ya en Personas, con el recado de que ya esta.
        assertTrue(AlmacenAjustes.de(app).ajustes.value.bienvenidaHecha)
        assertEquals(VistaPersonas.ORBITAS, AlmacenAjustes.de(app).ajustes.value.vista)
        compose.onNodeWithContentDescription(texto(R.string.ajustes)).assertIsDisplayed()
        compose.onNodeWithText(texto(R.string.listo_sin_gente)).assertIsDisplayed()
        compose.onNodeWithText(texto(R.string.entendido)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(texto(R.string.listo_sin_gente)).assertDoesNotExist()
    }

    @Test
    fun sinAvisosLaBienvenidaSeSaltaCuandoYComo() {
        abrir()
        compose.onNodeWithText(texto(R.string.empezar)).performClick()
        compose.waitForIdle()

        // Al empezar se piden los avisos, y solo ellos. Se niegan.
        escenario!!.onActivity { actividad ->
            val pedido = shadowOf(actividad).lastRequestedPermission
            assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), pedido.requestedPermissions.toList())
            actividad.onRequestPermissionsResult(
                pedido.requestCode,
                pedido.requestedPermissions,
                intArrayOf(PackageManager.PERMISSION_DENIED),
            )
        }
        compose.waitForIdle()

        // Derecho a anadir gente, con los ajustes de siempre.
        compose.onNodeWithText(texto(R.string.cuando_y_como)).assertDoesNotExist()
        compose.onNodeWithText(texto(R.string.elegir_de_la_agenda)).assertIsDisplayed()

        // Atras vuelve al sol, no a las horas.
        escenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText(texto(R.string.empezar)).assertIsDisplayed()
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
    fun laFichaSoloSeEditaTrasElLapiz() {
        val (id) = conGente(Contacto(nombre = "Ana", telefono = "600", frecuenciaDias = 7))
        abrir()
        compose.onNodeWithText("Ana").performClick()
        compose.waitForIdle()

        // A la vista, solo para leer: nada que cambiar la frecuencia.
        compose.onNodeWithContentDescription(texto(R.string.un_dia_mas)).assertDoesNotExist()
        compose.onNodeWithContentDescription(texto(R.string.editar)).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription(texto(R.string.un_dia_mas)).performSemanticsAction(SemanticsActions.OnClick)
        // El guardado sale de un LaunchedEffect: hace falta recomponer antes de esperar a la fila.
        compose.waitForIdle()
        compose.waitUntil(5_000) { runBlocking { dao.buscar(id) }!!.frecuenciaDias == 8 }

        // Atras cierra la edicion, no la ficha.
        escenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(texto(R.string.editar)).assertIsDisplayed()
        compose.onNodeWithContentDescription(texto(R.string.un_dia_mas)).assertDoesNotExist()
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
