package com.example.recuerdallamar.ui

import android.provider.Settings
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.recuerdallamar.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Con "Quitar animaciones" las caras, el sol y las burbujas se quedan quietos. */
@RunWith(AndroidJUnit4::class)
class MovimientoReducidoTest {
    private val app: App = ApplicationProvider.getApplicationContext()

    @Test
    fun quitarAnimacionesSeLee() {
        assertFalse(sinAnimaciones(app))
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertTrue(sinAnimaciones(app))
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Test
    fun enCalmaLasBurbujasNoSeMecen() {
        val sim = Simulacion().apply {
            ancho = 1000f
            alto = 2000f
            calma = true
        }
        sim.colocar(listOf(Sitio(1L, 300f, 400f, 50f)))
        val cuerpo = sim.cuerpos.getValue(1L)
        // Nace ya en su sitio y ahi se queda.
        repeat(600) {
            sim.paso(1f / 60f)
            assertEquals(300f, cuerpo.x, 0.01f)
            assertEquals(400f, cuerpo.y, 0.01f)
        }
    }

    @Test
    fun caraQuietaNoParpadeaNiSeMece() {
        // La cara guarda su reloj en estado de Compose: en una instantanea
        // propia, para no dejar cambios sin aplicar a la siguiente prueba.
        val instantanea = Snapshot.takeMutableSnapshot()
        try {
            instantanea.enter { caraQuieta() }
        } finally {
            instantanea.dispose()
        }
    }

    private fun caraQuieta() {
        val cara = EstadoCara(Expresiones.contenta, semilla = 3)
        cara.quieta = true
        cara.reproducir(Animaciones.mirarAlrededor)
        var t = 1L
        repeat(600) {
            cara.avanzar(t)
            assertEquals(1f, cara.parpadeo())
            assertEquals(0f, cara.reloj)
            t += 16
        }
        val quieta = cara.muestra()
        cara.avanzar(t + 5_000)
        assertEquals(quieta, cara.muestra())
    }
}
