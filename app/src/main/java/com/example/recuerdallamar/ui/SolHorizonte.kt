package com.example.recuerdallamar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.hypot

/*
 * El sol en la pantalla principal: tan grande que solo asoma un arco de
 * esquina a esquina por el borde de abajo, como un amanecer. Conserva los
 * ojos, que miran alrededor sin salirse del arco, y encima lleva el + para
 * anadir gente. Al acabar la bienvenida llega desde donde estaba alli: baja
 * y crece hasta el horizonte.
 */

/** Largo de cada onda del borde en el horizonte, y cuanto sobresale. */
private val ONDA = 56.dp
internal val PUNTA_HORIZONTE = 12.dp

/** Donde van los ojos y el +, en partes de lo que asoma el sol (desde abajo), y el tamano de la cara. */
private const val ALTURA_OJOS = 0.64f
private const val RADIO_OJOS = 0.36f
private const val ALTURA_MAS = 0.27f

/** Grados que levanta la mirada: desde abajo mira a su gente. */
private const val MIRA_ARRIBA = 12f

private val TAMANO_MAS = 40.dp
private val TOQUE_MAS = 36.dp // radio del circulo que cuenta como pulsar el +
private val HUNDIRSE = 8.dp

private const val LLEGADA_MS = 1100

/** Cuanto asoma el cuerpo del sol sobre la barra de navegacion, segun el alto de la pantalla. */
internal fun asomaSol(altoPantalla: Dp): Dp = (altoPantalla * 0.19f).coerceIn(120.dp, 156.dp)

/**
 * El sol del horizonte, a pantalla completa por encima del contenido: pinta
 * el arco y recoge los toques solo en la franja de abajo. Tocarlo lo hace
 * reir; el + ademas llama a [onAnadir]. Cuando [cuantos] sube, saluda.
 */
@Composable
fun SolHorizonte(
    relevo: Relevo?,
    cuantos: Int,
    onAnadir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alAnadir by rememberUpdatedState(onAnadir)
    val desde = remember { relevo?.tomarSol() }
    val llegada = remember { Animatable(if (desde == null) 1f else 0f) }
    LaunchedEffect(llegada) { llegada.animateTo(1f, tween(LLEGADA_MS, easing = FastOutSlowInEasing)) }

    val cara = rememberEstadoCara(Expresiones.contenta)
    val volverA = Animaciones.mirarAlrededor
    LaunchedEffect(cara) { cara.reproducir(Animaciones.saludo) { cara.reproducir(volverA) } }
    // Cuando llega alguien, se alegra.
    var vistos by remember { mutableIntStateOf(cuantos) }
    LaunchedEffect(cuantos) {
        if (cuantos > vistos) cara.reproducir(Animaciones.saludo) { cara.reproducir(volverA) }
        vistos = cuantos
    }
    var pulsada by remember { mutableStateOf(false) }
    LaunchedEffect(pulsada) {
        when {
            pulsada -> cara.poner(Expresiones.apretada, 100, Transicion.SECA)
            cara.animacion == null -> cara.reproducir(volverA)
        }
    }
    // Al apretarlo se hunde un poco y al soltarlo rebota.
    val hundido by animateFloatAsState(
        if (pulsada) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "hundido",
    )
    val alcance = rememberCoroutineScope()
    var origen by remember { mutableStateOf(Offset.Zero) }
    val esquema = MaterialTheme.colorScheme
    val barra = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { origen = it.positionInWindow() }) {
        val asoma = asomaSol(maxHeight)
        Canvas(Modifier.fillMaxSize()) {
            val t = cara.ahora / 1000f
            val e = llegada.value
            val ancho = size.width
            val alto = size.height
            val base = alto - barra.toPx()
            val a = asoma.toPx()
            val arco = a + barra.toPx()
            // El circulo que pasa por las dos esquinas de abajo y asoma [arco] en el centro.
            val radioFin = (ancho * ancho / 4 + arco * arco) / (2 * arco)
            val bajar = HUNDIRSE.toPx() * hundido
            val centroFin = Offset(ancho / 2, alto - arco + radioFin + bajar)
            val ojosFin = Offset(ancho / 2, base - a * ALTURA_OJOS + bajar)
            val radioOjosFin = a * RADIO_OJOS
            val honduraFin = PUNTA_HORIZONTE.toPx() / radioFin
            val puntasFin = 2f * PI.toFloat() * radioFin / ONDA.toPx()

            var centro = centroFin
            var radio = radioFin
            var hondura = honduraFin
            var puntas = puntasFin
            var ojos = ojosFin
            var radioOjos = radioOjosFin
            if (desde != null && e < 1f) {
                val inicio = desde.centro - origen
                centro = lerp(inicio, centroFin, e)
                radio = lerp(desde.radio, radioFin, e)
                hondura = lerp(HONDURA, honduraFin, e)
                // Las ondas se multiplican ya abajo, con el corte fuera de la pantalla.
                puntas = lerp(PUNTAS.toFloat(), puntasFin, ((e - 0.55f) / 0.45f).coerceIn(0f, 1f))
                ojos = lerp(inicio, ojosFin, e)
                radioOjos = lerp(desde.radio, radioOjosFin, e)
            }
            dibujarSol(centro, radio, respirar(hondura, t), puntas, t, esquema.tertiary)
            dibujarOjos(cara.muestra(), cara.parpadeo(), ojos, radioOjos, esquema.onTertiary, Offset(0f, -MIRA_ARRIBA * e))
        }

        // La franja donde asoma: recoge los toques para que no pasen a lo de debajo.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(asoma + barra)
                .pointerInput(cara) {
                    detectTapGestures(
                        onPress = {
                            pulsada = true
                            tryAwaitRelease()
                            pulsada = false
                        },
                        onTap = { toque ->
                            cara.reproducir(Animaciones.risa) { cara.reproducir(volverA) }
                            val mas = Offset(size.width / 2f, size.height - (barra + asoma * ALTURA_MAS).toPx())
                            if (hypot(toque.x - mas.x, toque.y - mas.y) <= TOQUE_MAS.toPx()) {
                                // Que se le vea reir antes de que se abra la agenda.
                                alcance.launch {
                                    delay(220)
                                    alAnadir()
                                }
                            }
                        },
                    )
                },
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = esquema.onTertiary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -(barra + asoma * ALTURA_MAS) + TAMANO_MAS / 2)
                    .size(TAMANO_MAS)
                    .graphicsLayer {
                        // Aparece cuando el sol ya ha llegado, y se hunde con el.
                        alpha = ((llegada.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
                        translationY = HUNDIRSE.toPx() * hundido
                    }
                    .semantics {
                        contentDescription = "Añadir persona"
                        role = Role.Button
                        onClick {
                            cara.reproducir(Animaciones.risa) { cara.reproducir(volverA) }
                            alAnadir()
                            true
                        }
                    },
            )
        }
    }
}
