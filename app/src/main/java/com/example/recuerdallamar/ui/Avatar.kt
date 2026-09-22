package com.example.recuerdallamar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/** Foto de la agenda recortada en circulo o, si no hay, la inicial del nombre. */
@Composable
fun Avatar(nombre: String, foto: ImageBitmap?, tamano: Dp, estilo: TextStyle) {
    val modificador = Modifier
        .size(tamano)
        .clip(CircleShape)
    if (foto != null) {
        Image(foto, contentDescription = null, contentScale = ContentScale.Crop, modifier = modificador)
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modificador.background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Text(
                nombre.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                style = estilo,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
