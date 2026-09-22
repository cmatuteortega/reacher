package com.example.recuerdallamar.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.recuerdallamar.Contactar
import com.example.recuerdallamar.R
import com.example.recuerdallamar.datos.MedioContacto

/**
 * Desplegable de forma de contacto, con un icono por opcion.
 *
 * Llamar sin pasar por el marcador pide CALL_PHONE en el momento de elegirlo;
 * si se deniega, se vuelve al marcador en vez de dejar una opcion que no hara nada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorMedio(
    medio: MedioContacto,
    etiqueta: String,
    onCambio: (MedioContacto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pedirLlamadas = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        if (!concedido) {
            onCambio(MedioContacto.MARCADOR)
            Toast.makeText(context, "Sin permiso de llamadas se abrirá el marcador", Toast.LENGTH_LONG).show()
        }
    }

    var desplegado by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = desplegado,
        onExpandedChange = { desplegado = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = medio.etiqueta,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(etiqueta) },
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
                        onCambio(opcion)
                        if (opcion == MedioContacto.LLAMADA && !Contactar.puedeLlamar(context)) {
                            pedirLlamadas.launch(Manifest.permission.CALL_PHONE)
                        }
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
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
