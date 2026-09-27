package com.example.recuerdallamar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.recuerdallamar.datos.TemaElegido

// Paleta de la app, de oscuro a claro. La misma del icono.
private val Noche = Color(0xFF0D2B45) // azul noche: texto y fondo oscuro
private val Marino = Color(0xFF203C56) // azul marino: color principal
private val Ciruela = Color(0xFF544E68) // ciruela: secundario, burbujas en oscuro
private val Malva = Color(0xFF8D697A) // malva: bordes
private val Teja = Color(0xFFD08159) // teja: acentos en claro
private val Naranja = Color(0xFFFFAA5E) // naranja: acentos en oscuro
private val Melocoton = Color(0xFFFFD4A3) // melocoton: burbujas en claro
private val Crema = Color(0xFFFFECD6) // crema: tarjetas y texto sobre oscuro

/*
 * Los tonos intermedios salen de mezclar la paleta entre si. Sobre la teja y
 * el naranja el texto va en Noche y no en crema: la crema no llega al
 * contraste minimo.
 */
internal val claro = lightColorScheme(
    primary = Marino,
    onPrimary = Crema,
    primaryContainer = Melocoton,
    onPrimaryContainer = Noche,
    secondary = Ciruela,
    onSecondary = Crema,
    secondaryContainer = Color(0xFFF2DCD2),
    onSecondaryContainer = Noche,
    tertiary = Teja,
    onTertiary = Noche,
    tertiaryContainer = Color(0xFFFFC08A),
    onTertiaryContainer = Noche,
    background = Color(0xFFFFF6EC),
    onBackground = Noche,
    surface = Color(0xFFFFF6EC),
    onSurface = Noche,
    surfaceVariant = Crema,
    onSurfaceVariant = Ciruela,
    surfaceContainerLowest = Color(0xFFFFFBF6),
    surfaceContainerLow = Color(0xFFFFF1E2),
    surfaceContainer = Crema,
    surfaceContainerHigh = Color(0xFFFBE4CB),
    surfaceContainerHighest = Color(0xFFF6DCC0),
    outline = Malva,
    outlineVariant = Color(0xFFE2C8BA),
)

internal val oscuro = darkColorScheme(
    primary = Melocoton,
    onPrimary = Noche,
    primaryContainer = Ciruela,
    onPrimaryContainer = Crema,
    secondary = Color(0xFFD9B4C0),
    onSecondary = Noche,
    secondaryContainer = Malva,
    onSecondaryContainer = Crema,
    tertiary = Naranja,
    onTertiary = Noche,
    tertiaryContainer = Teja,
    onTertiaryContainer = Noche,
    background = Noche,
    onBackground = Crema,
    surface = Noche,
    onSurface = Crema,
    surfaceVariant = Marino,
    onSurfaceVariant = Color(0xFFE6CDBD),
    surfaceContainerLowest = Color(0xFF08203A),
    surfaceContainerLow = Color(0xFF12314C),
    surfaceContainer = Color(0xFF183651),
    surfaceContainerHigh = Marino,
    surfaceContainerHighest = Color(0xFF2B4661),
    outline = Color(0xFFA88C99),
    outlineVariant = Color(0xFF4A4A66),
)

/** Si toca pintar en oscuro: lo elegido en ajustes, o lo del sistema. */
@Composable
fun TemaElegido.esOscuro(): Boolean = when (this) {
    TemaElegido.SISTEMA -> isSystemInDarkTheme()
    TemaElegido.CLARO -> false
    TemaElegido.OSCURO -> true
}

/**
 * Material 3 con la paleta propia en claro y oscuro. Sin color dinamico: en
 * Android 12+ taparia la paleta con los colores del fondo de pantalla.
 */
@Composable
fun TemaApp(modoOscuro: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (modoOscuro) oscuro else claro, content = content)
}
