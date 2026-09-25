package com.example.recuerdallamar.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Golpecito corto, como el diente de una rueda: para cada paso al arrastrar. */
fun View.tic() {
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}

/** Toque algo mas marcado: para interruptores y elecciones con un toque. */
fun View.toque() {
    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}

/** Confirmacion firme: cuando algo que se mantenia pulsado se completa. */
fun View.confirmar() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
    )
}

/** Escala para que lo que se pulsa se hunda un poco y rebote al soltar. */
@Composable
fun escalaAlPulsar(interaccion: InteractionSource, hundido: Float = 0.95f): Float {
    val pulsado by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (pulsado) hundido else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pulsar",
    )
    return escala
}

/**
 * Fila de opciones con una pastilla que se desliza a la elegida. Se puede
 * tocar una o arrastrar el dedo por encima: cada opcion que se cruza da un tic.
 * Si [elegida] no esta en [opciones] no se marca ninguna.
 */
@Composable
fun <T> Deslizador(
    opciones: List<T>,
    elegida: T,
    texto: (T) -> String,
    descripcion: (T) -> String,
    onElegir: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vista = LocalView.current
    val indice = opciones.indexOf(elegida)
    val elegidaActual by rememberUpdatedState(elegida)
    val elegirActual by rememberUpdatedState(onElegir)
    val elegir = { opcion: T ->
        if (opcion != elegidaActual) {
            vista.tic()
            elegirActual(opcion)
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        val ancho = maxWidth / opciones.size
        val desplazamiento by animateDpAsState(
            targetValue = ancho * indice.coerceAtLeast(0),
            animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
            label = "pastilla",
        )
        val visible by animateFloatAsState(if (indice >= 0) 1f else 0f, label = "pastillaVisible")
        Box(
            Modifier
                .offset(x = desplazamiento)
                .width(ancho)
                .fillMaxHeight()
                .padding(4.dp)
                .graphicsLayer { alpha = visible }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Row(
            Modifier
                .fillMaxSize()
                .selectableGroup()
                .pointerInput(opciones) {
                    val enX = { x: Float ->
                        elegir(opciones[(x / (size.width.toFloat() / opciones.size)).toInt().coerceIn(opciones.indices)])
                    }
                    detectHorizontalDragGestures(onDragStart = { enX(it.x) }) { cambio, _ ->
                        cambio.consume()
                        enX(cambio.position.x)
                    }
                },
        ) {
            opciones.forEachIndexed { i, opcion ->
                val color by animateColorAsState(
                    if (i == indice) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "textoOpcion",
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = i == indice,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { elegir(opcion) },
                        )
                        .semantics { contentDescription = descripcion(opcion) },
                ) {
                    Text(texto(opcion), style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Casilla elegible que se hunde al pulsarla; la elegida se tine y crece un
 * poco con un rebote.
 */
@Composable
fun Baldosa(
    elegida: Boolean,
    onElegir: () -> Unit,
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.(color: Color) -> Unit,
) {
    val interaccion = remember { MutableInteractionSource() }
    val pulsada = escalaAlPulsar(interaccion, hundido = 0.9f)
    val crecida by animateFloatAsState(
        if (elegida) 1f else 0.94f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "baldosa",
    )
    val colores = MaterialTheme.colorScheme
    val fondo by animateColorAsState(if (elegida) colores.primaryContainer else colores.surfaceContainerHighest, label = "fondoBaldosa")
    val tinta by animateColorAsState(if (elegida) colores.onPrimaryContainer else colores.onSurfaceVariant, label = "tintaBaldosa")
    val borde by animateColorAsState(if (elegida) colores.primary else Color.Transparent, label = "bordeBaldosa")

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = fondo,
        border = BorderStroke(2.dp, borde),
        modifier = modifier
            .graphicsLayer {
                val escala = pulsada * crecida
                scaleX = escala
                scaleY = escala
            }
            .clip(RoundedCornerShape(20.dp))
            .selectable(
                selected = elegida,
                interactionSource = interaccion,
                indication = ripple(),
                role = Role.RadioButton,
                onClick = onElegir,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
        ) { contenido(tinta) }
    }
}
