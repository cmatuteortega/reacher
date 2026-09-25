package com.example.recuerdallamar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.recuerdallamar.FotoContacto
import com.example.recuerdallamar.datos.Contacto
import com.example.recuerdallamar.datos.MedioContacto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaFicha(
    borrador: Contacto,
    observar: (Long) -> Flow<Contacto?>,
    fotosPermitidas: Boolean,
    onPedirFotos: () -> Unit,
    onGuardar: (Contacto, Int, MedioContacto) -> Unit,
    onContactar: (String, MedioContacto) -> Unit,
    onLlamadoHoy: (Long) -> Unit,
    onPausar: (Long, Int) -> Unit,
    onReanudar: (Long) -> Unit,
    onEliminar: (Long) -> Unit,
    onForzarNotificacion: (Long) -> Unit,
    onVolver: () -> Unit,
) {
    val guardado = borrador.id != 0L

    // Un contacto ya guardado se lee en vivo: si se pulsa su notificacion con
    // la ficha abierta, la marca aparece sin salir y volver a entrar.
    val flujo = remember(borrador.id) { if (guardado) observar(borrador.id) else flowOf(borrador) }
    val actual = flujo.collectAsState(initial = borrador).value ?: borrador

    var texto by rememberSaveable(borrador.id) { mutableStateOf(borrador.frecuenciaDias.toString()) }
    val dias = texto.toIntOrNull()?.takeIf { it >= 1 }

    var medio by rememberSaveable(borrador.id) { mutableStateOf(borrador.medio) }

    val context = LocalContext.current

    // Sin permiso de contactos no hay foto: se ve la inicial y un boton para pedirlo.
    var foto by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(actual.telefono, fotosPermitidas) {
        foto = FotoContacto.cargar(context, actual.telefono)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (guardado) "Ficha" else "Nuevo contacto") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { relleno ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Avatar(actual.nombre, foto, 72.dp, MaterialTheme.typography.headlineMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(actual.nombre, style = MaterialTheme.typography.headlineSmall)
                        Text(actual.telefono, style = MaterialTheme.typography.bodyLarge)
                        if (!fotosPermitidas) {
                            TextButton(onClick = onPedirFotos) {
                                Text("Mostrar foto de la agenda")
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = texto,
                // Solo cifras, y un tope para que no desborde un Int.
                onValueChange = { nuevo -> texto = nuevo.filter(Char::isDigit).take(4) },
                label = { Text("Frecuencia de contacto (días)") },
                singleLine = true,
                isError = dias == null,
                supportingText = { if (dias == null) Text("Pon un número de días, 1 o más") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            SelectorMedio(
                medio = medio,
                etiqueta = "Al tocar el aviso",
                onCambio = { medio = it },
                modifier = Modifier.fillMaxWidth(),
            )

            if (guardado) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Último contacto: ${actual.ultimoContacto.bonita()}")
                    Text("Próximo aviso: ${actual.proximoAviso().bonita()}")
                    val pulsada = actual.fechaPulsacion
                    Text(
                        if (actual.notificacionPulsada && pulsada != null) {
                            "Notificación pulsada el ${pulsada.bonito()}"
                        } else {
                            "Notificación aún no pulsada"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (actual.descartes > 0) {
                        Text(
                            "Aviso quitado sin contactar: " +
                                if (actual.descartes == 1) "1 vez" else "${actual.descartes} veces",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    "El último contacto se guardará con la fecha de hoy.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { dias?.let { onGuardar(actual, it, medio) } },
                    enabled = dias != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Guardar") }

                // Prueba lo elegido aunque aun no se haya guardado.
                FilledTonalButton(
                    onClick = { onContactar(actual.telefono, medio) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(medio.icono(), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(medio.boton)
                }
            }

            // Solo tiene sentido con el contacto guardado, y se apaga cuando ya
            // pone hoy: pulsarlo otra vez no cambiaria nada.
            if (guardado) {
                val yaHoy = actual.ultimoContacto == LocalDate.now()
                OutlinedButton(
                    onClick = { onLlamadoHoy(actual.id) },
                    enabled = !yaHoy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(if (yaHoy) "Último contacto: hoy" else "He llamado hoy")
                }

                PausaYBorrado(
                    contacto = actual,
                    onPausar = { dias -> onPausar(actual.id, dias) },
                    onReanudar = { onReanudar(actual.id) },
                    onEliminar = { onEliminar(actual.id) },
                )
            }

            HorizontalDivider()

            Text("Depuración", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(
                onClick = { onForzarNotificacion(actual.id) },
                enabled = guardado,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Forzar notificación ahora")
            }
            Text(
                if (guardado) {
                    "Lanza el aviso aunque no toque todavía, por el mismo camino que el diario."
                } else {
                    "Guarda el contacto primero para poder probar su notificación."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Dias de pausa que se proponen al pausar a alguien. */
private const val PAUSA_INICIAL = 7

/**
 * Pausar los avisos de esta persona (sin tocar su ultimo contacto) o
 * eliminarla. Junto a "He llamado hoy", es adonde lleva "Mas opciones" del aviso.
 */
@Composable
private fun PausaYBorrado(
    contacto: Contacto,
    onPausar: (Int) -> Unit,
    onReanudar: () -> Unit,
    onEliminar: () -> Unit,
) {
    var pidiendoDias by rememberSaveable { mutableStateOf(false) }
    var confirmandoBorrado by rememberSaveable { mutableStateOf(false) }

    val pausa = contacto.pausadoHasta?.takeIf { contacto.pausado() }
    if (pausa != null) {
        Text(
            "Avisos en pausa: vuelven el ${pausa.toLocalDate().bonita()}. " +
                "Mientras, la burbuja sigue creciendo.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onReanudar, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Reanudar avisos")
        }
    } else {
        OutlinedButton(onClick = { pidiendoDias = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Pausar avisos")
        }
    }

    OutlinedButton(
        onClick = { confirmandoBorrado = true },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text("Eliminar contacto")
    }

    if (pidiendoDias) {
        var texto by rememberSaveable { mutableStateOf(PAUSA_INICIAL.toString()) }
        val dias = texto.toIntOrNull()?.takeIf { it >= 1 }
        AlertDialog(
            onDismissRequest = { pidiendoDias = false },
            title = { Text("Pausar avisos") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sin avisos de ${contacto.nombre} durante estos días. Se sigue contando el tiempo desde el último contacto.")
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { nuevo -> texto = nuevo.filter(Char::isDigit).take(3) },
                        label = { Text("Días") },
                        singleLine = true,
                        isError = dias == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dias?.let(onPausar)
                        pidiendoDias = false
                    },
                    enabled = dias != null,
                ) { Text("Pausar") }
            },
            dismissButton = { TextButton(onClick = { pidiendoDias = false }) { Text("Cancelar") } },
        )
    }

    if (confirmandoBorrado) {
        AlertDialog(
            onDismissRequest = { confirmandoBorrado = false },
            title = { Text("Eliminar a ${contacto.nombre}") },
            text = { Text("Dejará de salir en la app y no habrá más avisos. En la agenda del teléfono no se borra nada.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmandoBorrado = false
                        onEliminar()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmandoBorrado = false }) { Text("Cancelar") } },
        )
    }
}
