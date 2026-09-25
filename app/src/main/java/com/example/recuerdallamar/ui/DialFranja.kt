package com.example.recuerdallamar.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class Asa { DESDE, HASTA }

/** Medidas del anillo, las mismas para pintar y para saber que se toca. */
private class Anillo(lado: Float, densidad: Density) {
    val grosor = with(densidad) { 28.dp.toPx() }
    val radio = lado / 2f - with(densidad) { 18.dp.toPx() } - grosor / 2f
    val radioAsa = grosor * 0.72f
}

/**
 * Esfera de 24 horas con la franja de avisos pintada como un arco. Las dos asas
 * (inicio en el color principal, fin en naranja) se arrastran alrededor y saltan
 * de hora en hora con un tic, como una rueda con dientes. Cruzar la medianoche
 * es solo seguir girando. El punto de fuera marca la hora de ahora.
 */
@Composable
fun DialFranja(
    desde: Int,
    hasta: Int,
    onCambio: (desde: Int, hasta: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vista = LocalView.current
    val medidor = rememberTextMeasurer()
    val colores = MaterialTheme.colorScheme
    val estiloMarcas = MaterialTheme.typography.labelSmall.copy(color = colores.onSurfaceVariant)
    val estiloAsa = MaterialTheme.typography.labelMedium

    val desdeActual by rememberUpdatedState(desde)
    val hastaActual by rememberUpdatedState(hasta)
    val cambio by rememberUpdatedState(onCambio)

    var agarrada by remember { mutableStateOf<Asa?>(null) }
    val muelle = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    val escalaDesde by animateFloatAsState(if (agarrada == Asa.DESDE) 1.25f else 1f, muelle, label = "asaDesde")
    val escalaHasta by animateFloatAsState(if (agarrada == Asa.HASTA) 1.25f else 1f, muelle, label = "asaHasta")

    val ahora = LocalTime.now().let { it.hour + it.minute / 60f }
    val todoElDia = desde == hasta
    val horasAvisando = if (todoElDia) 24 else (hasta - desde + 24) % 24

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .semantics {
                contentDescription = if (todoElDia) {
                    "Horario de avisos: todo el día"
                } else {
                    "Horario de avisos: de ${hora(desde)} a ${hora(hasta)}"
                }
                customActions = listOf(
                    CustomAccessibilityAction("Empezar una hora antes") { cambio((desde + 23) % 24, hasta); true },
                    CustomAccessibilityAction("Empezar una hora después") { cambio((desde + 1) % 24, hasta); true },
                    CustomAccessibilityAction("Terminar una hora antes") { cambio(desde, (hasta + 23) % 24); true },
                    CustomAccessibilityAction("Terminar una hora después") { cambio(desde, (hasta + 1) % 24); true },
                )
            },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val abajo = awaitFirstDown(requireUnconsumed = false)
                        val anillo = Anillo(min(size.width, size.height).toFloat(), this)
                        val centro = Offset(size.width / 2f, size.height / 2f)
                        // Solo el anillo agarra; el resto de la esfera deja desplazar la pantalla.
                        if (abs((abajo.position - centro).getDistance() - anillo.radio) > anillo.grosor * 1.2f) {
                            return@awaitEachGesture
                        }
                        val tocada = horaFraccion(abajo.position, centro)
                        val asa = if (distanciaHoras(tocada, desdeActual.toFloat()) <= distanciaHoras(tocada, hastaActual.toFloat())) {
                            Asa.DESDE
                        } else {
                            Asa.HASTA
                        }
                        var ultima = if (asa == Asa.DESDE) desdeActual else hastaActual
                        val mover = { p: Offset ->
                            val nueva = horaFraccion(p, centro).roundToInt() % 24
                            if (nueva != ultima) {
                                ultima = nueva
                                vista.tic()
                                if (asa == Asa.DESDE) cambio(nueva, hastaActual) else cambio(desdeActual, nueva)
                            }
                        }
                        agarrada = asa
                        vista.toque()
                        abajo.consume()
                        mover(abajo.position)
                        drag(abajo.id) {
                            it.consume()
                            mover(it.position)
                        }
                        agarrada = null
                    }
                },
        ) {
            val anillo = Anillo(size.minDimension, this)
            val centro = center
            val esquina = Offset(centro.x - anillo.radio, centro.y - anillo.radio)
            val caja = Size(anillo.radio * 2, anillo.radio * 2)

            drawCircle(colores.surfaceContainerHighest, anillo.radio, centro, style = Stroke(anillo.grosor))
            if (todoElDia) {
                drawCircle(colores.primary, anillo.radio, centro, style = Stroke(anillo.grosor))
            } else {
                drawArc(
                    color = colores.primary,
                    startAngle = desde * 15f - 90f,
                    sweepAngle = horasAvisando * 15f,
                    useCenter = false,
                    topLeft = esquina,
                    size = caja,
                    style = Stroke(anillo.grosor, cap = StrokeCap.Round),
                )
            }

            // Una marca por hora por dentro del anillo; numeros cada seis.
            val radioMarcas = anillo.radio - anillo.grosor / 2f - 8.dp.toPx()
            for (h in 0 until 24) {
                val principal = h % 6 == 0
                if (principal) {
                    val p = punto(centro, radioMarcas - 10.dp.toPx(), h.toFloat())
                    texto(medidor, h.toString(), estiloMarcas, p)
                } else {
                    drawCircle(colores.outlineVariant, 1.5.dp.toPx(), punto(centro, radioMarcas, h.toFloat()))
                }
            }

            drawCircle(colores.tertiary, 3.dp.toPx(), punto(centro, anillo.radio + anillo.grosor / 2f + 8.dp.toPx(), ahora))

            val asaDesde = { asa(medidor, centro, anillo, desde, escalaDesde, colores.primary, colores.onPrimary, colores.surface, estiloAsa) }
            val asaHasta = { asa(medidor, centro, anillo, hasta, escalaHasta, colores.tertiary, colores.onTertiary, colores.surface, estiloAsa) }
            // La que se arrastra va encima.
            if (agarrada == Asa.HASTA) {
                asaDesde()
                asaHasta()
            } else {
                asaHasta()
                asaDesde()
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (todoElDia) "24 h" else "$horasAvisando h",
                style = MaterialTheme.typography.displaySmall,
                color = colores.onSurface,
            )
            Text(
                if (todoElDia) "todo el día" else "${hora(desde)} – ${hora(hasta)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.onSurfaceVariant,
            )
        }
    }
}

private fun DrawScope.asa(
    medidor: TextMeasurer,
    centro: Offset,
    anillo: Anillo,
    hora: Int,
    escala: Float,
    relleno: Color,
    tinta: Color,
    borde: Color,
    estilo: TextStyle,
) {
    val p = punto(centro, anillo.radio, hora.toFloat())
    val radio = anillo.radioAsa * escala
    drawCircle(borde, radio + 3.dp.toPx(), p)
    drawCircle(relleno, radio, p)
    texto(medidor, hora.toString(), estilo.copy(color = tinta), p)
}

private fun DrawScope.texto(medidor: TextMeasurer, texto: String, estilo: TextStyle, centro: Offset) {
    val medida = medidor.measure(texto, estilo)
    drawText(medida, topLeft = Offset(centro.x - medida.size.width / 2f, centro.y - medida.size.height / 2f))
}

/** Punto del circulo para una hora (0 arriba, en el sentido del reloj). */
private fun punto(centro: Offset, radio: Float, hora: Float): Offset {
    val angulo = Math.toRadians((hora * 15f - 90f).toDouble())
    return Offset(centro.x + radio * cos(angulo).toFloat(), centro.y + radio * sin(angulo).toFloat())
}

/** Hora, con decimales, que queda bajo un punto de la esfera. */
private fun horaFraccion(p: Offset, centro: Offset): Float {
    val grados = Math.toDegrees(atan2((p.y - centro.y).toDouble(), (p.x - centro.x).toDouble())) + 90.0
    return (((grados + 360.0) % 360.0) / 15.0).toFloat()
}

private fun distanciaHoras(a: Float, b: Float): Float {
    val d = abs(a - b) % 24f
    return min(d, 24f - d)
}

internal fun hora(h: Int): String = h.toString().padStart(2, '0') + ":00"
