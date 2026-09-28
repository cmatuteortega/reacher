package com.example.recuerdallamar

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.recuerdallamar.Actualizacion.Paso
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class ActualizacionTest {

    private fun paso(
        disponible: Boolean = true,
        enCurso: Boolean = false,
        version: Int = 20,
        prioridad: Int = 0,
        dias: Int? = 0,
        flexible: Boolean = true,
        inmediata: Boolean = true,
        ofrecida: Int = 0,
    ) = Actualizacion.paso(disponible, enCurso, version, prioridad, dias, flexible, inmediata, ofrecida)

    @Test
    fun sinVersionNuevaNoHaceNada() {
        assertEquals(Paso.NADA, paso(disponible = false, dias = 30, prioridad = 3))
    }

    @Test
    fun unaVersionRecienSalidaEspera() {
        assertEquals(Paso.NADA, paso(dias = Actualizacion.DIAS_PARA_OFRECER - 1))
        assertEquals(Paso.NADA, paso(dias = null))
        assertEquals(Paso.FLEXIBLE, paso(dias = Actualizacion.DIAS_PARA_OFRECER))
    }

    @Test
    fun conPrioridadSeOfreceSinEsperar() {
        assertEquals(Paso.FLEXIBLE, paso(prioridad = Actualizacion.PRIORIDAD_SIN_ESPERA))
    }

    @Test
    fun cadaVersionSeOfreceUnaSolaVez() {
        assertEquals(Paso.NADA, paso(dias = 10, version = 20, ofrecida = 20))
        assertEquals(Paso.FLEXIBLE, paso(dias = 10, version = 21, ofrecida = 20))
    }

    @Test
    fun laUrgenteEsInmediataYSeRetoma() {
        assertEquals(Paso.INMEDIATA, paso(prioridad = Actualizacion.PRIORIDAD_INMEDIATA))
        // Aunque ya se ofreciera en flexible: una urgente no se puede saltar.
        assertEquals(Paso.INMEDIATA, paso(prioridad = 5, ofrecida = 20))
        // Se cerro la app a medias.
        assertEquals(Paso.INMEDIATA, paso(disponible = false, enCurso = true, prioridad = 5))
        // Una flexible a medias no se convierte en inmediata.
        assertEquals(Paso.NADA, paso(disponible = false, enCurso = true, prioridad = 0))
    }

    @Test
    fun soloLoQuePlayPermite() {
        assertEquals(Paso.FLEXIBLE, paso(prioridad = 5, inmediata = false))
        assertEquals(Paso.NADA, paso(dias = 10, flexible = false))
    }

    @Test
    fun seBajaUsandolaYSeInstalaAlSalir() {
        val play = FakeAppUpdateManager(org.robolectric.RuntimeEnvironment.getApplication())
        play.setUpdateAvailable(20, AppUpdateType.FLEXIBLE)
        play.setClientVersionStalenessDays(Actualizacion.DIAS_PARA_OFRECER)
        val controlador = Robolectric.buildActivity(ComponentActivity::class.java).create()
        Actualizacion(controlador.get(), crearGestor = { play }, activa = true)

        controlador.start().resume()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Play ofrece la version", play.isConfirmationDialogVisible)

        play.userAcceptsUpdate()
        play.downloadStarts()
        play.downloadCompletes()
        shadowOf(Looper.getMainLooper()).idle()
        // Con la app delante no se corta a nadie.
        assertFalse(play.isInstallSplashScreenVisible)

        controlador.pause().stop()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Se instala al salir", play.isInstallSplashScreenVisible)
    }

    @Test
    fun siDiceQueNoNoSeVuelveAOfrecer() {
        val play = FakeAppUpdateManager(org.robolectric.RuntimeEnvironment.getApplication())
        play.setUpdateAvailable(20, AppUpdateType.FLEXIBLE)
        play.setClientVersionStalenessDays(10)
        val controlador = Robolectric.buildActivity(ComponentActivity::class.java).create()
        Actualizacion(controlador.get(), crearGestor = { play }, activa = true)

        controlador.start().resume()
        shadowOf(Looper.getMainLooper()).idle()
        play.userRejectsUpdate()

        controlador.pause().resume()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(play.isConfirmationDialogVisible)
    }
}
