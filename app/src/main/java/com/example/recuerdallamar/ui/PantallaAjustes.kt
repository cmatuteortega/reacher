package com.example.recuerdallamar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.recuerdallamar.datos.Ajustes
import com.example.recuerdallamar.datos.TemaElegido
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Dias de pausa que se proponen al elegir "durante unos dias". */
private const val PAUSA_INICIAL = 7

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAjustes(
    ajustes: Ajustes,
    onCambiar: ((Ajustes) -> Ajustes) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Ajustes") }) },
        modifier = modifier,
    ) { relleno ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SeccionAvisos(ajustes, onCambiar)

            HorizontalDivider()

            Titulo("Horario de avisos")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectorHora(
                    etiqueta = "Desde",
                    hora = ajustes.horaDesde,
                    onCambio = { hora -> onCambiar { it.copy(horaDesde = hora) } },
                    modifier = Modifier.weight(1f),
                )
                SelectorHora(
                    etiqueta = "Hasta",
                    hora = ajustes.horaHasta,
                    onCambio = { hora -> onCambiar { it.copy(horaHasta = hora) } },
                    modifier = Modifier.weight(1f),
                )
            }
            Explicacion(
                if (ajustes.horaDesde == ajustes.horaHasta) {
                    "Misma hora de inicio y fin: se avisa a cualquier hora."
                } else {
                    "Solo se avisa entre las ${hora(ajustes.horaDesde)} y las ${hora(ajustes.horaHasta)}. " +
                        "Un aviso que caiga fuera espera a las ${hora(ajustes.horaDesde)}."
                },
            )

            HorizontalDivider()

            Titulo("Contactos nuevos")
            SelectorMedio(
                medio = ajustes.medioPorDefecto,
                etiqueta = "Forma de contacto por defecto",
                onCambio = { medio -> onCambiar { it.copy(medioPorDefecto = medio) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Explicacion("Se propone al añadir a alguien; en su ficha se puede cambiar.")

            HorizontalDivider()

            Titulo("Apariencia")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TemaElegido.entries.forEachIndexed { i, tema ->
                    SegmentedButton(
                        selected = ajustes.tema == tema,
                        onClick = { onCambiar { it.copy(tema = tema) } },
                        shape = SegmentedButtonDefaults.itemShape(i, TemaElegido.entries.size),
                    ) { Text(tema.etiqueta) }
                }
            }
        }
    }
}

/**
 * Interruptor de recordatorios. Al apagarlo se elige si es hasta volver a
 * encenderlo o solo durante unos dias; pasada la pausa vuelven solos.
 */
@Composable
private fun SeccionAvisos(ajustes: Ajustes, onCambiar: ((Ajustes) -> Ajustes) -> Unit) {
    val hoy = LocalDate.now()
    val encendidos = ajustes.avisosEncendidos(hoy)
    val pausa = ajustes.pausaHasta.takeIf { !encendidos }

    var texto by rememberSaveable {
        mutableStateOf((pausa?.let { ChronoUnit.DAYS.between(hoy, it).toInt() } ?: PAUSA_INICIAL).toString())
    }
    val dias = texto.toIntOrNull()?.takeIf { it >= 1 }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = encendidos,
                role = Role.Switch,
                onValueChange = { activar -> onCambiar { it.copy(avisosActivos = activar, pausaHasta = null) } },
            ),
    ) {
        Column(Modifier.weight(1f)) {
            Titulo("Recordatorios")
            Explicacion(
                when {
                    encendidos -> "Activados"
                    pausa != null -> "En pausa: vuelven el ${pausa.bonita()}"
                    else -> "Desactivados hasta que los vuelvas a activar"
                },
            )
        }
        Switch(checked = encendidos, onCheckedChange = null)
    }

    if (!encendidos) {
        Column(Modifier.selectableGroup()) {
            OpcionRadio(
                texto = "Hasta que los vuelva a activar",
                elegida = pausa == null,
                onElegir = { onCambiar { it.copy(avisosActivos = false, pausaHasta = null) } },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OpcionRadio(
                    texto = "Durante",
                    elegida = pausa != null,
                    onElegir = {
                        val n = (dias ?: PAUSA_INICIAL).toLong()
                        onCambiar { it.copy(avisosActivos = false, pausaHasta = hoy.plusDays(n)) }
                    },
                )
                OutlinedTextField(
                    value = texto,
                    onValueChange = { nuevo ->
                        texto = nuevo.filter(Char::isDigit).take(3)
                        // Cambiar los dias ya elige la pausa: es lo que se esta pidiendo.
                        texto.toIntOrNull()?.takeIf { it >= 1 }?.let { n ->
                            onCambiar { it.copy(avisosActivos = false, pausaHasta = hoy.plusDays(n.toLong())) }
                        }
                    },
                    singleLine = true,
                    isError = dias == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.width(88.dp),
                )
                Text("días", modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun OpcionRadio(texto: String, elegida: Boolean, onElegir: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .selectable(selected = elegida, onClick = onElegir, role = Role.RadioButton)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = elegida, onClick = null)
        Text(texto, modifier = Modifier.padding(start = 12.dp, end = 12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorHora(
    etiqueta: String,
    hora: Int,
    onCambio: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var desplegado by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = desplegado,
        onExpandedChange = { desplegado = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = hora(hora),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(etiqueta) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = desplegado) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = desplegado,
            onDismissRequest = { desplegado = false },
        ) {
            (0..23).forEach { h ->
                DropdownMenuItem(
                    text = { Text(hora(h)) },
                    onClick = {
                        desplegado = false
                        onCambio(h)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Explicacion(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun hora(h: Int): String = h.toString().padStart(2, '0') + ":00"
