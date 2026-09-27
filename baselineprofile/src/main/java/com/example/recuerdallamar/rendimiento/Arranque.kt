package com.example.recuerdallamar.rendimiento

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Arranque en frio y fotogramas del recorrido habitual, sin perfil y con el
 * perfil de referencia: la diferencia es lo que gana el perfil.
 */
@RunWith(AndroidJUnit4::class)
class Arranque {
    @get:Rule
    val regla = MacrobenchmarkRule()

    @Test
    fun arranqueSinPerfil() = arranque(CompilationMode.None())

    @Test
    fun arranqueConPerfil() = arranque(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun fotogramasConPerfil() = regla.measureRepeated(
        packageName = PAQUETE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        recorridoHabitual()
    }

    private fun arranque(modo: CompilationMode) = regla.measureRepeated(
        packageName = PAQUETE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = modo,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }
}
