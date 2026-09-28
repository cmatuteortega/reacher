package com.example.recuerdallamar.ui

import android.Manifest
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Lo que necesita quien usa TalkBack, letra grande o sin animaciones: cada
 * cosa pulsable con nombre y de al menos 48dp, las burbujas como un solo
 * elemento con sus acciones, y las pantallas enteras al 200% de letra.
 * La ventana es muy alta para que todo quepa sin desplazar: lo que queda
 * fuera de la vista no tiene medidas que comprobar.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h6000dp")
class AccesibilidadTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: App = ApplicationProvider.getApplicationContext()
    private val dao = BaseDatos.de(app).contactos()
    private var escenario: ActivityScenario<MainActivity>? = null

    private fun texto(@StringRes id: Int): String = app.getString(id)

    private val gente = arrayOf(
        Contacto(nombre = "Ana", telefono = "600", frecuenciaDias = 7, ultimoContacto = LocalDate.now().minusDays(10)),
        Contacto(nombre = "Bruno", telefono = "601", frecuenciaDias = 10, ultimoContacto = LocalDate.now().minusDays(8)),
        Contacto(nombre = "Carmen", telefono = "602", frecuenciaDias = 30, ultimoContacto = LocalDate.now().minusDays(2)),
    )

    private fun abrirCon(vista: VistaPersonas): List<Long> {
        AlmacenAjustes.de(app).cambiar { it.copy(bienvenidaHecha = true, vista = vista) }
        val ids = runBlocking { gente.map { dao.insertar(it) } }
        escenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        return ids
    }

    @After
    fun cerrar() {
        escenario?.close()
        RuntimeEnvironment.setFontScale(1f)
    }

    /** Todo lo pulsable de la pantalla tiene nombre y mide al menos 48dp de lado. */
    private fun revisarPulsables(pantalla: String) {
        compose.waitForIdle()
        val densidad = app.resources.displayMetrics.density
        val fallos = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().mapNotNull { nodo ->
            val c = nodo.config
            val nombre = c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                ?: c.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            val toque = nodo.touchBoundsInRoot
            val ancho = toque.width / densidad
            val alto = toque.height / densidad
            when {
                nombre.isNullOrBlank() -> "$pantalla: pulsable sin nombre en $toque"
                ancho < 47.5f || alto < 47.5f -> "$pantalla: '$nombre' mide %.0fx%.0fdp".format(ancho, alto)
                else -> null
            }
        }
        assertTrue(fallos.joinToString("\n"), fallos.isEmpty())
    }

    private fun recorrer() {
        revisarPulsables("personas")
        compose.onNodeWithText("Ana").performClick()
        revisarPulsables("ficha")
        compose.onNodeWithContentDescription(texto(R.string.editar)).performClick()
        revisarPulsables("ficha, editar")
        repeat(2) {
            escenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
        }
        compose.onNodeWithContentDescription(texto(R.string.ajustes)).performClick()
        revisarPulsables("ajustes")
    }

    @Test
    fun pulsablesConNombreYTamano() {
        abrirCon(VistaPersonas.LISTA)
        recorrer()
    }

    @Test
    fun letraAl200() {
        RuntimeEnvironment.setFontScale(2f)
        abrirCon(VistaPersonas.LISTA)
        recorrer()
    }

    @Test
    fun bienvenidaAl200() {
        RuntimeEnvironment.setFontScale(2f)
        // Con los avisos ya dados, para ver tambien el paso de las horas.
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        escenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        revisarPulsables("bienvenida")
        compose.onNodeWithText(texto(R.string.empezar)).performClick()
        revisarPulsables("bienvenida, cuando y como")
        compose.onNodeWithText(texto(R.string.continuar)).performClick()
        revisarPulsables("bienvenida, anadir")
    }

    private val conAcciones = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)

    /** Nombres de las burbujas en el orden en que las recorre TalkBack. */
    private fun ordenTalkBack(): List<String> =
        compose.onAllNodes(conAcciones and hasClickAction()).fetchSemanticsNodes()
            .sortedBy { it.config.getOrNull(SemanticsProperties.TraversalIndex) ?: 0f }
            .map { it.config[SemanticsProperties.ContentDescription].joinToString() }

    private fun burbujasParaTalkBack(vista: VistaPersonas) {
        val (ana) = abrirCon(vista)
        revisarPulsables(vista.name)

        // Un nodo por persona, en orden de urgencia; el nombre de debajo no es otro nodo.
        val orden = ordenTalkBack()
        assertEquals(3, orden.size)
        assertEquals(app.getString(R.string.burbuja_toca, "Ana"), orden[0])
        assertTrue(orden[1], orden[1].startsWith("Bruno"))
        assertTrue(orden[2], orden[2].startsWith("Carmen"))
        compose.onNodeWithText("Ana").assertDoesNotExist()

        // "He llamado hoy" desde la burbuja, sin abrir la ficha.
        val burbuja = compose.onNode(hasContentDescription(app.getString(R.string.burbuja_toca, "Ana")))
        val accion = burbuja.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == texto(R.string.he_llamado_hoy) }
        compose.runOnUiThread { accion.action() }
        compose.waitUntil(5_000) { runBlocking { dao.buscar(ana) }!!.ultimoContacto == LocalDate.now() }
    }

    @Test
    fun burbujasParaTalkBack() = burbujasParaTalkBack(VistaPersonas.BURBUJAS)

    @Test
    fun orbitasParaTalkBack() = burbujasParaTalkBack(VistaPersonas.ORBITAS)
}
