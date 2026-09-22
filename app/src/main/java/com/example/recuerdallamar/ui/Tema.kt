package com.example.recuerdallamar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta de la app, la misma del icono.
private val Tinta = Color(0xFF001524) // azul noche: texto y fondo oscuro
private val Petroleo = Color(0xFF15616D) // verde azulado: color principal
private val Crema = Color(0xFFFFECD1) // crema: tarjetas y texto sobre oscuro
private val Naranja = Color(0xFFFF7D00) // naranja: acentos (anadir, toca llamar)

/*
 * Los tonos intermedios salen de mezclar la paleta entre si. Sobre el naranja
 * el texto va en Tinta y no en blanco: el blanco no llega al contraste minimo.
 */
private val claro = lightColorScheme(
    primary = Petroleo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4E7EC),
    onPrimaryContainer = Tinta,
    secondary = Color(0xFF8A4A00),
    onSecondary = Color.White,
    secondaryContainer = Crema,
    onSecondaryContainer = Tinta,
    tertiary = Naranja,
    onTertiary = Tinta,
    tertiaryContainer = Color(0xFFFFD6AD),
    onTertiaryContainer = Tinta,
    background = Color(0xFFFFFBF6),
    onBackground = Tinta,
    surface = Color(0xFFFFFBF6),
    onSurface = Tinta,
    surfaceVariant = Crema,
    onSurfaceVariant = Color(0xFF3B4A52),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF7EC),
    surfaceContainer = Color(0xFFFFF3E3),
    surfaceContainerHigh = Color(0xFFFFEFDA),
    surfaceContainerHighest = Crema,
    outline = Color(0xFF6F7F86),
    outlineVariant = Color(0xFFD9CBB8),
)

private val oscuro = darkColorScheme(
    primary = Color(0xFF86D0DB),
    onPrimary = Tinta,
    primaryContainer = Petroleo,
    onPrimaryContainer = Crema,
    secondary = Color(0xFFFFB877),
    onSecondary = Tinta,
    secondaryContainer = Color(0xFF3A3024),
    onSecondaryContainer = Crema,
    tertiary = Naranja,
    onTertiary = Tinta,
    tertiaryContainer = Color(0xFF7A3C00),
    onTertiaryContainer = Crema,
    background = Tinta,
    onBackground = Crema,
    surface = Tinta,
    onSurface = Crema,
    surfaceVariant = Color(0xFF1F3A49),
    onSurfaceVariant = Color(0xFFCFC3B2),
    surfaceContainerLowest = Color(0xFF000D16),
    surfaceContainerLow = Color(0xFF071C2A),
    surfaceContainer = Color(0xFF0C2331),
    surfaceContainerHigh = Color(0xFF142C3B),
    surfaceContainerHighest = Color(0xFF1C3646),
    outline = Color(0xFF8A969C),
    outlineVariant = Color(0xFF34495A),
)

/**
 * Material 3 con la paleta propia en claro y oscuro. Sin color dinamico: en
 * Android 12+ taparia la paleta con los colores del fondo de pantalla.
 */
@Composable
fun TemaApp(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) oscuro else claro, content = content)
}
