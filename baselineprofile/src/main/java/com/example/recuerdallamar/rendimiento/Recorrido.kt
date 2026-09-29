package com.example.recuerdallamar.rendimiento

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** El paquete de la version publicada (applicationId de :app). */
const val PAQUETE = "com.cmatuteortega.contacto"

private const val ESPERA_MS = 5_000L

/**
 * Lo que hace casi todo el mundo al abrir la app: pasar la bienvenida si sale,
 * ver a su gente y entrar en Ajustes. Busca los textos en ingles: el emulador
 * de pruebas esta en ingles.
 */
fun MacrobenchmarkScope.recorridoHabitual() {
    device.wait(Until.hasObject(By.pkg(PAQUETE).depth(0)), ESPERA_MS)
    // Bienvenida (solo la primera vez tras instalar).
    device.findObject(By.text("Get started"))?.let { empezar ->
        empezar.click()
        // Con los avisos concedidos sale "Cuando y como"; sin ellos se salta.
        device.wait(Until.findObject(By.text("Continue")), ESPERA_MS)?.click()
        device.wait(Until.findObject(By.text("Not now")), ESPERA_MS)?.click()
        device.wait(Until.findObject(By.text("Got it")), ESPERA_MS)?.click()
    }
    // Personas y Ajustes, y de vuelta.
    device.wait(Until.findObject(By.desc("Settings")), ESPERA_MS)?.click()
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
}
