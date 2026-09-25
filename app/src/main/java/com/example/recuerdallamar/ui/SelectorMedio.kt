package com.example.recuerdallamar.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.recuerdallamar.Contactar
import com.example.recuerdallamar.R
import com.example.recuerdallamar.datos.MedioContacto

/**
 * Formas de contacto como fila de baldosas con su icono: se ven todas de un
 * vistazo y se elige con un toque.
 */
@Composable
fun SelectorMedio(
    medio: MedioContacto,
    onCambio: (MedioContacto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vista = LocalView.current
    val elegir = rememberElegirMedio(onCambio)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
    ) {
        MedioContacto.entries.forEach { opcion ->
            Baldosa(
                elegida = medio == opcion,
                onElegir = {
                    vista.toque()
                    elegir(opcion)
                },
                modifier = Modifier.weight(1f),
            ) { color ->
                Icon(opcion.icono(), contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
                Text(
                    opcion.corto(),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Nombre corto para que quepa bajo el icono. */
private fun MedioContacto.corto(): String = when (this) {
    MedioContacto.MARCADOR -> "Marcador"
    MedioContacto.LLAMADA -> "Llamada"
    MedioContacto.WHATSAPP -> "WhatsApp"
    MedioContacto.SMS -> "SMS"
    MedioContacto.TELEGRAM -> "Telegram"
}

/**
 * Elegir una forma de contacto. Llamar sin pasar por el marcador pide
 * CALL_PHONE en el momento; si se deniega, se vuelve al marcador.
 */
@Composable
fun rememberElegirMedio(onCambio: (MedioContacto) -> Unit): (MedioContacto) -> Unit {
    val context = LocalContext.current
    val cambio by rememberUpdatedState(onCambio)
    val pedirLlamadas = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        if (!concedido) {
            cambio(MedioContacto.MARCADOR)
            Toast.makeText(context, "Sin permiso de llamadas se abrirá el marcador", Toast.LENGTH_LONG).show()
        }
    }
    return { opcion ->
        cambio(opcion)
        if (opcion == MedioContacto.LLAMADA && !Contactar.puedeLlamar(context)) {
            pedirLlamadas.launch(Manifest.permission.CALL_PHONE)
        }
    }
}

// Material no trae logos de marcas: WhatsApp y Telegram van como vectores
// propios en res/drawable, junto al de SMS (que solo esta en el paquete extendido).
@Composable
fun MedioContacto.icono(): Painter = when (this) {
    MedioContacto.MARCADOR -> rememberVectorPainter(Icons.Filled.Phone)
    MedioContacto.LLAMADA -> rememberVectorPainter(Icons.Filled.Call)
    MedioContacto.SMS -> painterResource(R.drawable.ic_sms)
    MedioContacto.WHATSAPP -> painterResource(R.drawable.ic_whatsapp)
    MedioContacto.TELEGRAM -> painterResource(R.drawable.ic_telegram)
}
