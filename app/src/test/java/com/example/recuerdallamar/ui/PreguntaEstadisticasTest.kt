package com.example.recuerdallamar.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.example.recuerdallamar.R
import com.example.recuerdallamar.datos.Ajustes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreguntaEstadisticasTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var ajustes = Ajustes()

    private fun mostrar(toca: Boolean) {
        compose.setContent {
            PreguntaEstadisticas(toca = toca, onCambiar = { cambio -> ajustes = cambio(ajustes) })
        }
        // Sale pasado un momento, no nada mas abrir.
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    @Test
    fun aceptar() {
        mostrar(toca = true)
        compose.onNodeWithText(context.getString(R.string.estadisticas_si)).performClick()
        assertEquals(true, ajustes.estadisticas)
        compose.onNodeWithText(context.getString(R.string.estadisticas_pregunta_titulo)).assertDoesNotExist()
    }

    @Test
    fun rechazar() {
        mostrar(toca = true)
        compose.onNodeWithText(context.getString(R.string.estadisticas_no)).performClick()
        assertEquals(false, ajustes.estadisticas)
    }

    @Test
    fun siNoTocaNoSale() {
        mostrar(toca = false)
        compose.onNodeWithText(context.getString(R.string.estadisticas_pregunta_titulo)).assertDoesNotExist()
        assertEquals(null, ajustes.estadisticas)
    }
}
