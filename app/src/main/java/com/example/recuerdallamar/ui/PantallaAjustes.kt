package com.example.recuerdallamar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.recuerdallamar.R
import com.example.recuerdallamar.datos.Ajustes
import com.example.recuerdallamar.datos.IdiomaElegido
import com.example.recuerdallamar.datos.TemaElegido
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A partir de este ancho (tablet, plegable abierto, movil apaisado) van dos columnas. */
private val ANCHO_DOS_COLUMNAS = 600.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAjustes(
    ajustes: Ajustes,
    onCambiar: ((Ajustes) -> Ajustes) -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plegado = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.ajustes)) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.volver))
                    }
                },
                scrollBehavior = plegado,
            )
        },
        modifier = modifier.nestedScroll(plegado.nestedScrollConnection),
    ) { relleno ->
        BoxWithConstraints(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno),
        ) {
            val dosColumnas = maxWidth >= ANCHO_DOS_COLUMNAS
            Column(
                Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (dosColumnas) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            TarjetaAvisos(ajustes, onCambiar)
                            TarjetaHorario(ajustes, onCambiar)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            TarjetaPosponer(ajustes, onCambiar)
                            TarjetaMedio(ajustes, onCambiar)
                            TarjetaApariencia(ajustes, onCambiar)
                            TarjetaDepuracion(ajustes, onCambiar)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TarjetaAvisos(ajustes, onCambiar)
                        TarjetaHorario(ajustes, onCambiar)
                        TarjetaPosponer(ajustes, onCambiar)
                        TarjetaMedio(ajustes, onCambiar)
                        TarjetaApariencia(ajustes, onCambiar)
                        TarjetaDepuracion(ajustes, onCambiar)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.app_name) + " · " + stringResource(R.string.subtitulo),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Interruptor grande de recordatorios: toda la tarjeta se pulsa y cambia de
 * color. Apagados, se elige hasta cuando deslizando el dedo por los plazos;
 * pasada la pausa vuelven solos.
 */
@Composable
private fun TarjetaAvisos(ajustes: Ajustes, onCambiar: ((Ajustes) -> Ajustes) -> Unit) {
    val vista = LocalView.current
    val hoy = LocalDate.now()
    val encendidos = ajustes.avisosEncendidos(hoy)
    val pausa = ajustes.pausaHasta.takeIf { !encendidos }
    val diasPausa: Int? = pausa?.let { ChronoUnit.DAYS.between(hoy, it).toInt() }
    val recursos = LocalContext.current.resources
    val hastaReactivar = stringResource(R.string.hasta_reactivar)

    val interaccion = remember { MutableInteractionSource() }
    val escala = escalaAlPulsar(interaccion, hundido = 0.97f)
    val fondo by animateColorAsState(
        if (encendidos) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "fondoAvisos",
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = fondo,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = escala
                scaleY = escala
            },
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = encendidos,
                        interactionSource = interaccion,
                        indication = ripple(),
                        role = Role.Switch,
                        onValueChange = { activar ->
                            vista.toque()
                            onCambiar { it.copy(avisosActivos = activar, pausaHasta = null) }
                        },
                    )
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.recordatorios), style = MaterialTheme.typography.titleLarge)
                    Explicacion(
                        when {
                            encendidos -> stringResource(R.string.activados)
                            pausa != null -> stringResource(R.string.en_pausa_vuelven, pausa.bonita())
                            else -> stringResource(R.string.desactivados)
                        },
                    )
                }
                Switch(checked = encendidos, onCheckedChange = null)
            }
            AnimatedVisibility(visible = !encendidos) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    Text(stringResource(R.string.pausa), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Deslizador(
                        opciones = PLAZOS_PAUSA,
                        elegida = diasPausa,
                        texto = { d -> d?.let { recursos.getString(R.string.dias_corto, it) } ?: "∞" },
                        descripcion = { d -> d?.let { recursos.dias(it) } ?: hastaReactivar },
                        onElegir = { d ->
                            onCambiar { it.copy(avisosActivos = false, pausaHasta = d?.let { n -> hoy.plusDays(n.toLong()) }) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaHorario(ajustes: Ajustes, onCambiar: ((Ajustes) -> Ajustes) -> Unit) {
    Tarjeta(stringResource(R.string.horario_avisos)) {
        DialFranja(
            desde = ajustes.horaDesde,
            hasta = ajustes.horaHasta,
            onCambio = { desde, hasta -> onCambiar { it.copy(horaDesde = desde, horaHasta = hasta) } },
            modifier = Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
        )
        Explicacion(
            if (ajustes.horaDesde == ajustes.horaHasta) {
                stringResource(R.string.horario_juntas)
            } else {
                stringResource(R.string.horario_fuera, hora(ajustes.horaDesde))
            },
        )
    }
}

@Composable
private fun TarjetaPosponer(ajustes: Ajustes, onCambiar: ((Ajustes) -> Ajustes) -> Unit) {
    val recursos = LocalContext.current.resources
    Tarjeta(stringResource(R.string.boton_mas_tarde_titulo)) {
        Deslizador(
            opciones = HORAS_POSPONER,
            elegida = ajustes.horasPosponer,
            texto = { recursos.getString(R.string.horas_corto, it) },
            descripcion = { recursos.horas(it) },
            onElegir = { h -> onCambiar { it.copy(horasPosponer = h) } },
        )
        Explicacion(
            stringResource(R.string.posponer_explicacion, recursos.horas(ajustes.horasPosponer), hora(ajustes.horaDesde)),
        )
    }
}

@Composable
private fun TarjetaMedio(ajustes: Ajustes, onCambiar: ((Ajustes) -> Ajustes) -> Unit) {
    Tarjeta(stringResource(R.string.contactos_nuevos)) {
        SelectorMedio(
            medio = ajustes.medioPorDefecto,
            onCambio = { medio -> onCambiar { it.copy(medioPorDefecto = medio) } },
        )
        Explicacion(stringResource(R.string.medio_explicacion, stringResource(ajustes.medioPorDefecto.etiqueta)))
    }
}

@Composable
private fun TarjetaApariencia(ajustes: Ajustes, onCambiar: ((Ajustes) -> Ajustes) -> Unit) {
    val vista = LocalView.current
    Tarjeta(stringResource(R.string.apariencia)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
        ) {
            TemaElegido.entries.forEach { tema ->
                Baldosa(
                    elegida = ajustes.tema == tema,
                    onElegir = {
                        vista.toque()
                        onCambiar { it.copy(tema = tema) }
                    },
                    modifier = Modifier.weight(1f),
                ) { color ->
                    MuestraTema(
                        tema,
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    )
                    Text(stringResource(tema.etiqueta), style = MaterialTheme.typography.labelLarge, color = color)
                }
            }
        }
    }
}

/**
 * Idioma de la app, para probar las traducciones sin cambiar el del telefono.
 * Al elegir otro, MainActivity se rehace ya en ese idioma.
 */
@Composable
private fun TarjetaDepuracion(ajustes: Ajustes, onCambiar: ((Ajustes) -> Ajustes) -> Unit) {
    val vista = LocalView.current
    // El del telefono: el de la aplicacion, que no lleva el elegido aqui (solo lo llevan las actividades).
    val delTelefono = LocalContext.current.applicationContext.resources.configuration.locales[0]
    Tarjeta(stringResource(R.string.depuracion)) {
        Text(
            stringResource(R.string.idioma_app),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Column(Modifier.selectableGroup()) {
            IdiomaElegido.entries.forEach { idioma ->
                val elegido = ajustes.idioma == idioma
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(
                            selected = elegido,
                            role = Role.RadioButton,
                            onClick = {
                                if (!elegido) {
                                    vista.toque()
                                    onCambiar { it.copy(idioma = idioma) }
                                }
                            },
                        ),
                ) {
                    RadioButton(selected = elegido, onClick = null, modifier = Modifier.padding(horizontal = 12.dp))
                    Text(
                        idioma.nombre
                            ?: stringResource(R.string.idioma_sistema, delTelefono.nombreEnSuIdioma()),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        Explicacion(stringResource(R.string.idioma_explicacion))
    }
}

/** "Español", "English"...: cada idioma con su propio nombre. */
private fun Locale.nombreEnSuIdioma(): String =
    getDisplayLanguage(this).replaceFirstChar { it.titlecase(this) }

/** Pantalla en miniatura con los colores del tema; la del sistema, partida en diagonal. */
@Composable
private fun MuestraTema(tema: TemaElegido, modifier: Modifier = Modifier) {
    Canvas(modifier.padding(horizontal = 8.dp)) {
        val esquinas = CornerRadius(10.dp.toPx())
        val pintar = { esquema: ColorScheme ->
            drawRoundRect(esquema.background, cornerRadius = esquinas)
            val margen = 6.dp.toPx()
            val alto = (size.height - margen * 3) / 2
            drawRoundRect(
                esquema.surfaceContainerHigh,
                topLeft = Offset(margen, margen),
                size = Size(size.width - margen * 2, alto),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawRoundRect(
                esquema.primary,
                topLeft = Offset(margen, margen * 2 + alto),
                size = Size((size.width - margen * 2) * 0.55f, alto),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawCircle(esquema.tertiary, alto / 2, Offset(size.width - margen - alto / 2, margen * 2 + alto * 1.5f))
        }
        when (tema) {
            TemaElegido.CLARO -> pintar(claro)
            TemaElegido.OSCURO -> pintar(oscuro)
            TemaElegido.SISTEMA -> {
                pintar(claro)
                val mitad = Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                clipPath(mitad) { pintar(oscuro) }
            }
        }
        drawRoundRect(Color.Black.copy(alpha = 0.12f), cornerRadius = esquinas, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun Tarjeta(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
            contenido()
        }
    }
}

@Composable
private fun Explicacion(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

private val HORAS_POSPONER = listOf(1, 2, 3, 4, 6, 8, 12)

/** Plazos de pausa en dias; null es hasta volver a activarlos. */
private val PLAZOS_PAUSA: List<Int?> = listOf(1, 3, 7, 14, 30, null)
