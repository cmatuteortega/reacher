package com.example.recuerdallamar.rendimiento

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val ESPERA_MS = 10_000L

/**
 * Prueba de humo de la version minimizada con R8 (la variante benchmarkRelease
 * es la de publicacion firmada con la clave de depuracion): lo que se rompe
 * con R8 (Room, WorkManager, las rutas de Navigation, la serializacion) casca
 * al arrancar o al navegar, y las pruebas con Robolectric no lo ven porque
 * corren sin minimizar.
 *
 * Solo corre con el argumento humo=true (flujo "Humo" de GitHub Actions); al
 * generar el perfil o medir el arranque se salta:
 *
 *   ./gradlew :baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.example.recuerdallamar.rendimiento.Humo \
 *     -Pandroid.testInstrumentationRunnerArguments.humo=true
 *
 * Busca los textos en ingles: el emulador de pruebas esta en ingles.
 */
@RunWith(AndroidJUnit4::class)
class Humo {
    private val instrumentacion = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentacion)

    @Before
    fun desdeCero() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("humo") == "true")
        // Como recien instalada: sale la bienvenida. Las notificaciones, ya
        // concedidas, para que el dialogo del sistema no tape nada.
        device.executeShellCommand("pm clear $PAQUETE")
        device.executeShellCommand("pm grant $PAQUETE android.permission.POST_NOTIFICATIONS")
        device.executeShellCommand("logcat -b crash -c")
        device.pressHome()
    }

    @Test
    fun recorridoPrincipal() {
        abrir()

        // Bienvenida sin anadir a nadie.
        pulsar(By.text("Get started"))
        pulsar(By.text("Not now"))
        pulsar(By.text("Got it"))
        cerrarDialogos()

        // Las tres vistas: burbujas, orbitas y lista (el boton pasa por todas).
        repeat(3) {
            val vista = esperar(By.descStartsWith("Show as"))
            vista.click()
            device.waitForIdle()
            sigueViva()
        }

        // Ajustes de arriba abajo, cambiando el tema y dejandolo como estaba.
        pulsar(By.desc("Settings"))
        esperar(By.text("Reminders"))
        bajarHasta(By.text("Dark")).click()
        device.waitForIdle()
        bajarHasta(By.text("System")).click()
        device.waitForIdle()
        bajarHasta(By.text("Backup"))
        sigueViva()
        device.pressBack()
        esperar(By.desc("Settings"))

        // Fuera y dentro otra vez: vuelve a leer la base de datos y los ajustes.
        device.pressHome()
        abrir()
        esperar(By.desc("Settings"))
        sigueViva()
    }

    private fun abrir() {
        val context = instrumentacion.context
        val intent = context.packageManager.getLaunchIntentForPackage(PAQUETE)
        assertNotNull("$PAQUETE no esta instalada", intent)
        context.startActivity(intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue("No se abre", device.wait(Until.hasObject(By.pkg(PAQUETE).depth(0)), ESPERA_MS))
    }

    /** La pregunta de estadisticas y el aviso de bateria salen segun el telefono: fuera si estan. */
    private fun cerrarDialogos() {
        device.waitForIdle()
        listOf("No thanks", "Not now").forEach { texto ->
            device.wait(Until.findObject(By.text(texto)), 1_500L)?.click()
        }
    }

    private fun esperar(selector: BySelector) =
        device.wait(Until.findObject(selector), ESPERA_MS)
            ?: throw AssertionError("No aparece $selector\n${fallos()}")

    /** Baja por la pantalla hasta que aparece [selector]. */
    private fun bajarHasta(selector: BySelector): UiObject2 {
        repeat(10) {
            device.findObject(selector)?.let { return it }
            val lista = device.findObject(By.scrollable(true)) ?: return esperar(selector)
            lista.scroll(Direction.DOWN, 0.5f)
            device.waitForIdle()
        }
        return esperar(selector)
    }

    private fun pulsar(selector: BySelector) {
        esperar(selector).click()
        device.waitForIdle()
    }

    /** Ni se ha cerrado ni hay un fallo suyo en el registro. */
    private fun sigueViva() {
        val fallos = fallos()
        assertFalse("La app ha fallado:\n$fallos", fallos.contains(PAQUETE))
        assertTrue("La app ya no esta en pantalla", device.hasObject(By.pkg(PAQUETE)))
    }

    private fun fallos(): String = device.executeShellCommand("logcat -b crash -d")
}
