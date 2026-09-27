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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import kotlin.math.roundToInt

/*
 * El sol en la pantalla principal: metido en la esquina de arriba a la
 * izquierda, como el sol que se dibuja de pequeno, con sus rayos ondulados
 * girando alrededor del cuarto que asoma. Los ojos van sobre la esfera
 * entera, con la cabeza girada hacia abajo a la derecha: asi se deslizan y
 * se estrechan hacia el borde como en las burbujas.
 * Abajo, en el centro, el + para anadir a alguien: al pulsarlo el sol lo
 * mira, se encoge y se rie. Al acabar la bienvenida llega desde donde estaba
 * alli: sube y crece hasta la esquina.
 */

/** Largo de cada onda del borde en la esquina, y cuanto sobresale. */
private val ONDA = 44.dp
internal val PUNTA_ESQUINA = 16.dp

/**
 * Grados que gira la cabeza para asomar la cara por la esquina: a la derecha
 * y hacia abajo. Los ojos son [OJOS] de los de siempre, que la esfera es
 * mucho mayor, y sus gestos se quedan en [GESTOS] de lo normal para que no
 * se salgan por arriba ni por la izquierda ni se peguen al borde.
 */
private const val GIRO_DERECHA = 24f
private const val GIRO_ABAJO = 30f
private const val OJOS = 0.5f
private const val GESTOS = 0.25f

/** Grados de mas hacia el + cuando se pulsa. */
private const val MIRA_MAS = 14f

private val TAMANO_MAS = 44.dp
private val TOQUE_MAS = 64.dp
private val ENCOGERSE = 8.dp

/** Lo que el + ocupa abajo, sobre la barra de navegacion. */
internal val HUECO_MAS = 88.dp

private const val LLEGADA_MS = 1100

/** Radio del sol de la esquina, sin contar la barra de estado, segun el ancho de la pantalla. */
internal fun radioSolEsquina(anchoPantalla: Dp): Dp = (anchoPantalla * 0.3f).coerceIn(100.dp, 140.dp)

/**
 * El sol de la esquina y el + de abajo, a pantalla completa por encima del
 * contenido; solo recogen toques el propio sol y el +. Tocar el sol lo hace
 * reir; el + ademas llama a [onAnadir]. Cuando [cuantos] sube, saluda.
 */
@Composable
fun SolEsquina(
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
    // Se aprieta el sol o el +: en los dos casos el sol cierra los ojos y se encoge.
    var solPulsado by remember { mutableStateOf(false) }
    var masPulsado by remember { mutableStateOf(false) }
    val pulsada = solPulsado || masPulsado
    LaunchedEffect(pulsada) {
        when {
            pulsada -> cara.poner(Expresiones.apretada, 100, Transicion.SECA)
            cara.animacion == null -> cara.reproducir(volverA)
        }
    }
    val encogido by animateFloatAsState(
        if (pulsada) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "encogido",
    )
    // Mientras se pulsa el +, lo mira.
    val haciaMas by animateFloatAsState(if (masPulsado) 1f else 0f, label = "haciaMas")
    val escalaMas by animateFloatAsState(
        if (masPulsado) 0.82f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "escalaMas",
    )
    val reir = { cara.reproducir(Animaciones.risa) { cara.reproducir(volverA) } }
    val alcance = rememberCoroutineScope()
    var origen by remember { mutableStateOf(Offset.Zero) }
    val esquema = MaterialTheme.colorScheme
    val estado = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barra = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { origen = it.positionInWindow() }) {
        val radioSol = radioSolEsquina(maxWidth) + estado
        Canvas(Modifier.fillMaxSize()) {
            val t = cara.ahora / 1000f
            val e = llegada.value
            val radioFin = radioSol.toPx() - ENCOGERSE.toPx() * encogido
            // En la misma esquina de la ventana, salga donde salga este lienzo.
            val centroFin = -origen
            val honduraFin = PUNTA_ESQUINA.toPx() / radioFin
            // Entero: el corte no se ve ni aunque asome el sol entero.
            val puntasFin = (2f * PI.toFloat() * radioSol.toPx() / ONDA.toPx()).roundToInt().toFloat()

            var centro = centroFin
            var radio = radioFin
            var hondura = honduraFin
            var puntas = puntasFin
            if (desde != null && e < 1f) {
                val inicio = desde.centro - origen
                centro = lerp(inicio, centroFin, e)
                radio = lerp(desde.radio, radioFin, e)
                hondura = lerp(HONDURA, honduraFin, e)
                // Las ondas se multiplican ya en la esquina, con el corte fuera de la pantalla.
                puntas = lerp(PUNTAS.toFloat(), puntasFin, ((e - 0.55f) / 0.45f).coerceIn(0f, 1f))
            }
            dibujarSol(centro, radio, respirar(hondura, t), puntas, t, esquema.tertiary)

            // La cara gira hacia la esquina a la vez que el sol llega; si se pulsa
            // el +, un poco mas hacia el.
            val mas = Offset(size.width / 2, size.height - barra.toPx() - HUECO_MAS.toPx() / 2)
            val d = (mas - centro).let { it / hypot(it.x, it.y).coerceAtLeast(1f) }
            val mirada = Offset(GIRO_DERECHA, GIRO_ABAJO) * e + d * (MIRA_MAS * haciaMas)
            val gestos = lerp(1f, GESTOS, e)
            val m = cara.muestra()
            val cabeza = m.girada(x = m.cabezaX * gestos, y = m.cabezaY * gestos, z = m.cabezaZ * gestos)
                .conOjosDe(lerp(1f, OJOS, e))
            dibujarOjos(cabeza, cara.parpadeo(), centro, radio, esquema.onTertiary, mirada)
        }

        // El sol recoge los toques del cuarto que asoma.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .size(radioSol * 0.8f)
                .pointerInput(cara) {
                    detectTapGestures(
                        onPress = {
                            solPulsado = true
                            tryAwaitRelease()
                            solPulsado = false
                        },
                        onTap = { reir() },
                    )
                }
                .semantics { contentDescription = "Tú, el sol de tu sistema" },
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = (HUECO_MAS - TOQUE_MAS) / 2)
                .size(TOQUE_MAS)
                .graphicsLayer {
                    // Aparece cuando el sol ya ha llegado.
                    alpha = ((llegada.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
                    scaleX = escalaMas
                    scaleY = escalaMas
                }
                .semantics {
                    contentDescription = "Añadir persona"
                    role = Role.Button
                    onClick {
                        reir()
                        alAnadir()
                        true
                    }
                }
                .pointerInput(cara) {
                    detectTapGestures(
                        onPress = {
                            masPulsado = true
                            tryAwaitRelease()
                            masPulsado = false
                        },
                        onTap = {
                            reir()
                            // Que se le vea reir antes de que se abra la agenda.
                            alcance.launch {
                                delay(220)
                                alAnadir()
                            }
                        },
                    )
                },
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = esquema.tertiary,
                modifier = Modifier.size(TAMANO_MAS),
            )
        }
    }
}

/** La misma cara con los ojos (y lo que los separa) a [k] de su tamano. */
private fun Expresion.conOjosDe(k: Float): Expresion {
    fun Ojo.menor() = copy(ancho = ancho * k, alto = alto * k, x = x * k, y = y * k)
    return copy(izquierdo = izquierdo.menor(), derecho = derecho.menor(), separacion = separacion * k)
}
