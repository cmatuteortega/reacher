package com.example.recuerdallamar.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.recuerdallamar.Contactar
import com.example.recuerdallamar.FotoContacto
import com.example.recuerdallamar.R
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

    // La foto se busca en la agenda con el permiso de contactos; si no esta
    // concedido, la ficha ofrece pedirlo (una vez por ficha si se deniega).
    var fotoPermitida by remember { mutableStateOf(FotoContacto.permitida(context)) }
    var fotoDenegada by rememberSaveable(borrador.id) { mutableStateOf(false) }
    val pedirContactos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        fotoPermitida = concedido
        fotoDenegada = !concedido
    }
    var foto by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(actual.telefono, fotoPermitida) {
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
                    Avatar(actual.nombre, foto)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(actual.nombre, style = MaterialTheme.typography.headlineSmall)
                        Text(actual.telefono, style = MaterialTheme.typography.bodyLarge)
                        if (!fotoPermitida && !fotoDenegada) {
                            TextButton(onClick = { pedirContactos.launch(Manifest.permission.READ_CONTACTS) }) {
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

            var desplegado by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = desplegado,
                onExpandedChange = { desplegado = it },
            ) {
                OutlinedTextField(
                    value = medio.etiqueta,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text("Al tocar el aviso") },
                    leadingIcon = { Icon(medio.icono(), contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = desplegado) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = desplegado,
                    onDismissRequest = { desplegado = false },
                ) {
                    MedioContacto.entries.forEach { opcion ->
                        DropdownMenuItem(
                            text = { Text(opcion.etiqueta) },
                            leadingIcon = { Icon(opcion.icono(), contentDescription = null) },
                            onClick = {
                                desplegado = false
                                elegirMedio(opcion)
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
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

/** Foto de la agenda recortada en circulo o, si no hay, la inicial del nombre. */
@Composable
private fun Avatar(nombre: String, foto: ImageBitmap?) {
    val modificador = Modifier
        .size(72.dp)
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
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// Material no trae logos de marcas: WhatsApp y Telegram van como vectores
// propios en res/drawable, junto al de SMS (que solo esta en el paquete extendido).
@Composable
private fun MedioContacto.icono(): Painter = when (this) {
    MedioContacto.MARCADOR -> rememberVectorPainter(Icons.Filled.Phone)
    MedioContacto.LLAMADA -> rememberVectorPainter(Icons.Filled.Call)
    MedioContacto.SMS -> painterResource(R.drawable.ic_sms)
    MedioContacto.WHATSAPP -> painterResource(R.drawable.ic_whatsapp)
    MedioContacto.TELEGRAM -> painterResource(R.drawable.ic_telegram)
}
