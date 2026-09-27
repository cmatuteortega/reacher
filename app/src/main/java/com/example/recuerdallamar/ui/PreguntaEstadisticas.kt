package com.example.recuerdallamar.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.example.recuerdallamar.R
import com.example.recuerdallamar.datos.Ajustes
import kotlinx.coroutines.delay

/** Lo que tarda en salir: que antes se vea la pantalla (y acabe el sol del arranque). */
private const val ESPERA_MS = 1_500L

/**
 * Una sola vez: si se pueden mandar estadisticas de uso anonimas. Como el aviso
 * de bateria, nunca en la misma sesion que la bienvenida. Cerrarlo sin elegir
 * cuenta como no; despues se cambia en Ajustes > Privacidad.
 */
@Composable
fun PreguntaEstadisticas(toca: Boolean, onCambiar: ((Ajustes) -> Ajustes) -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(toca) {
        if (toca) {
            delay(ESPERA_MS)
            visible = true
        }
    }
    if (!visible) return
    val responder = { si: Boolean ->
        onCambiar { it.copy(estadisticas = si) }
        visible = false
    }
    AlertDialog(
        onDismissRequest = { responder(false) },
        title = { Text(stringResource(R.string.estadisticas_pregunta_titulo)) },
        text = { Text(stringResource(R.string.estadisticas_pregunta_texto)) },
        confirmButton = {
            TextButton(onClick = { responder(true) }) { Text(stringResource(R.string.estadisticas_si)) }
        },
        dismissButton = {
            TextButton(onClick = { responder(false) }) { Text(stringResource(R.string.estadisticas_no)) }
        },
    )
}
