package com.example.recuerdallamar.ui

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Si el sistema tiene "Quitar animaciones" (escala de animaciones a 0). Las
 * animaciones de Compose ya lo respetan solas; lo que va con su propio reloj
 * (caras, sol, fisica de las burbujas, orbitas) lo lee de aqui y se calma:
 * sin vaiven, sin parpadeos, sin giros. Lo que mueve el dedo se sigue moviendo.
 */
val LocalMovimientoReducido = compositionLocalOf { false }

/** Lee el ajuste del sistema y lo sigue si cambia con la app abierta. */
@Composable
fun rememberMovimientoReducido(): Boolean {
    val context = LocalContext.current
    var reducido by remember { mutableStateOf(sinAnimaciones(context)) }
    DisposableEffect(context) {
        val observador = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reducido = sinAnimaciones(context)
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.registerContentObserver(uri, false, observador)
        reducido = sinAnimaciones(context)
        onDispose { context.contentResolver.unregisterContentObserver(observador) }
    }
    return reducido
}

internal fun sinAnimaciones(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
