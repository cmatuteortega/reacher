package com.example.recuerdallamar.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.recuerdallamar.Bateria
import com.example.recuerdallamar.R
import kotlinx.coroutines.delay

/** Lo que tarda en salir: que antes se vea la pantalla (y acabe el sol del arranque). */
private const val ESPERA_MS = 1_500L

/**
 * Una sola vez: si el ahorro de bateria de este telefono puede comerse los
 * avisos, se dice y se ofrece ir a arreglarlo. Solo cuando ya hay alguien de
 * quien avisar y nunca en la misma sesion que la bienvenida, que ya pide
 * bastantes cosas. Despues sigue en Ajustes > Avisos en segundo plano.
 */
@Composable
fun AvisoBateria(hayGente: Boolean, bienvenidaYaHecha: Boolean) {
    val context = LocalContext.current
    val toca = remember { !Bateria.avisado(context) && Bateria.conviene(context) }
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(toca, hayGente, bienvenidaYaHecha) {
        if (toca && hayGente && bienvenidaYaHecha) {
            delay(ESPERA_MS)
            visible = true
        }
    }
    if (!visible) return
    val cerrar = {
        Bateria.marcarAvisado(context)
        visible = false
    }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text(stringResource(R.string.bateria_aviso_titulo)) },
        text = { Text(stringResource(R.string.bateria_aviso_texto, Bateria.nombreFabricante)) },
        confirmButton = {
            TextButton(
                onClick = {
                    cerrar()
                    Bateria.abrirAjustes(context)
                },
            ) { Text(stringResource(R.string.bateria_ajustar)) }
        },
        dismissButton = {
            TextButton(onClick = cerrar) { Text(stringResource(R.string.ahora_no)) }
        },
    )
}
