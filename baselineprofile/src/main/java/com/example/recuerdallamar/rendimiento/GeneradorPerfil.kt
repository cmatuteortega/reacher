package com.example.recuerdallamar.rendimiento

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** El perfil: lo que se ejecuta al arrancar y en el recorrido habitual. */
@RunWith(AndroidJUnit4::class)
class GeneradorPerfil {
    @get:Rule
    val regla = BaselineProfileRule()

    @Test
    fun generar() = regla.collect(packageName = PAQUETE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        recorridoHabitual()
    }
}
