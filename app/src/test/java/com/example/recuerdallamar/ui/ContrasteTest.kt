package com.example.recuerdallamar.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * La paleta contra WCAG AA, en claro y en oscuro: 4.5:1 para texto y 3:1
 * para iconos y bordes que dicen algo. El sol, los halos y los bordes
 * decorativos (outlineVariant) no cuentan.
 */
class ContrasteTest {
    private fun contraste(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    private fun ColorScheme.textos() = listOf(
        "onPrimary/primary" to (onPrimary to primary),
        "onPrimaryContainer/primaryContainer" to (onPrimaryContainer to primaryContainer),
        "onSecondary/secondary" to (onSecondary to secondary),
        "onSecondaryContainer/secondaryContainer" to (onSecondaryContainer to secondaryContainer),
        "onTertiary/tertiary" to (onTertiary to tertiary),
        "onTertiaryContainer/tertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
        "onSurface/surface" to (onSurface to surface),
        "onSurfaceVariant/surface" to (onSurfaceVariant to surface),
        "onSurfaceVariant/surfaceVariant" to (onSurfaceVariant to surfaceVariant),
        "onSurfaceVariant/surfaceContainerHighest" to (onSurfaceVariant to surfaceContainerHighest),
        "primary/surface" to (primary to surface),
        "primary/surfaceContainerHigh" to (primary to surfaceContainerHigh),
        "secondary/surface" to (secondary to surface),
        "acentoLegible/surface" to (acentoLegible to surface),
        "acentoLegible/surfaceContainer" to (acentoLegible to surfaceContainer),
    )

    private fun ColorScheme.graficos() = listOf(
        "outline/surface" to (outline to surface),
        "acentoLegible/primaryContainer" to (acentoLegible to primaryContainer),
        "primary/primaryContainer" to (primary to primaryContainer),
    )

    private fun comprobar(nombre: String, esquema: ColorScheme) {
        val fallos = esquema.textos().filter { (_, p) -> contraste(p.first, p.second) < 4.5f } +
            esquema.graficos().filter { (_, p) -> contraste(p.first, p.second) < 3f }
        assertTrue(
            "$nombre: " + fallos.joinToString { (n, p) -> "$n %.2f".format(contraste(p.first, p.second)) },
            fallos.isEmpty(),
        )
    }

    @Test
    fun claroPasaAA() = comprobar("claro", claro)

    @Test
    fun oscuroPasaAA() = comprobar("oscuro", oscuro)
}
