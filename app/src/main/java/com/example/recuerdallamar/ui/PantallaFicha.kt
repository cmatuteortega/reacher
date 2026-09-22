package com.example.recuerdallamar.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.recuerdallamar.Contactar
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
    onGuardar: (Contacto, Int, MedioContacto) -> Unit,
    onContactar: (String, MedioContacto) -> Unit,
    onLlamadoHoy: (Long) -> Unit,
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

    // Llamar sin pasar por el marcador pide CALL_PHONE en el momento de elegirlo;
    // si se deniega, se vuelve al marcador en vez de dejar una opcion que no hara nada.
    val context = LocalContext.current
    val pedirLlamadas = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        if (!concedido) {
            medio = MedioContacto.MARCADOR
            Toast.makeText(context, "Sin permiso de llamadas se abrirá el marcador", Toast.LENGTH_LONG).show()
        }
    }
    val elegirMedio = { nuevo: MedioContacto ->
        medio = nuevo
        if (nuevo == MedioContacto.LLAMADA && !Contactar.puedeLlamar(context)) {
            pedirLlamadas.launch(Manifest.permission.CALL_PHONE)
        }
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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(actual.nombre, style = MaterialTheme.typography.headlineSmall)
                    Text(actual.telefono, style = MaterialTheme.typography.bodyLarge)
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

            Column(Modifier.selectableGroup()) {
                Text("Al tocar el aviso", style = MaterialTheme.typography.labelLarge)
                MedioContacto.entries.forEach { opcion ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = medio == opcion,
                                onClick = { elegirMedio(opcion) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = medio == opcion, onClick = null)
                        Spacer(Modifier.size(12.dp))
                        Text(opcion.etiqueta)
                    }
                }
            }

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
                    Icon(
                        if (medio.esLlamada) Icons.Filled.Call else Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
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
