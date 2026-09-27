package com.example.recuerdallamar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * Caras con caracter, sin dibujos hechos a mano: una expresion es un punado
 * de numeros (ojos, boca, cejas...), una animacion es una lista de pasos que
 * van de una expresion a otra, y [EstadoCara] las reproduce. La cara se pinta
 * en la fase de dibujo, asi que animarla no recompone nada.
 */

/** Rasgos de una cara. Todo es relativo al radio, asi vale para cualquier tamano. */
@Immutable
data class Expresion(
    /** 0 cerrados … 1 abiertos; algo mas de 1, muy abiertos. */
    val ojos: Float = 1f,
    /** A donde mira, de -1 a 1. */
    val miradaX: Float = 0f,
    val miradaY: Float = 0f,
    /** -1 triste … 1 sonrisa. */
    val boca: Float = 0.35f,
    /** 0 cerrada … 1 una "o". */
    val bocaAbierta: Float = 0f,
    /** -1 fruncidas … 1 levantadas; en 0 no se ven. */
    val cejas: Float = 0f,
    val rubor: Float = 0f,
    /** Por donde van las zetas al dormir, de 0 a 1. */
    val sueno: Float = 0f,
    /** Salto de la cara: -1 arriba … 1 abajo. */
    val salto: Float = 0f,
    /** Grados. */
    val inclinacion: Float = 0f,
    /** Mas de 0 la alarga, menos de 0 la aplasta. */
    val estirar: Float = 0f,
)

private fun mezcla(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun mezcla(a: Expresion, b: Expresion, t: Float) = Expresion(
    ojos = mezcla(a.ojos, b.ojos, t),
    miradaX = mezcla(a.miradaX, b.miradaX, t),
    miradaY = mezcla(a.miradaY, b.miradaY, t),
    boca = mezcla(a.boca, b.boca, t),
    bocaAbierta = mezcla(a.bocaAbierta, b.bocaAbierta, t),
    cejas = mezcla(a.cejas, b.cejas, t),
    rubor = mezcla(a.rubor, b.rubor, t),
    sueno = mezcla(a.sueno, b.sueno, t),
    salto = mezcla(a.salto, b.salto, t),
    inclinacion = mezcla(a.inclinacion, b.inclinacion, t),
    estirar = mezcla(a.estirar, b.estirar, t),
)

object Expresiones {
    val neutral = Expresion()
    val contenta = Expresion(ojos = 0.75f, boca = 1f, rubor = 0.8f, cejas = 0.2f)
    val atenta = Expresion(ojos = 1.15f, cejas = 0.5f, boca = 0.2f)
    val sorpresa = Expresion(ojos = 1.35f, cejas = 1f, boca = 0f, bocaAbierta = 0.8f)
    val dormida = Expresion(ojos = 0f, boca = 0.15f, miradaY = 0.3f)

    /** Al apretarla: ojos entornados y risita. */
    val apretada = Expresion(ojos = 0.35f, boca = 0.8f, rubor = 1f, estirar = -0.12f)
}

/** Ir a [expresion] en [ms]; con 0, de golpe. */
data class Paso(val expresion: Expresion, val ms: Int, val curva: Easing = FastOutSlowInEasing)

data class Animacion(val pasos: List<Paso>, val bucle: Boolean = true)

object Animaciones {
    /** Respira despacio y le salen zetas. */
    val durmiendo = Animacion(
        listOf(
            Paso(Expresiones.dormida, 0),
            Paso(Expresiones.dormida.copy(sueno = 0.5f, salto = 0.15f, bocaAbierta = 0.25f), 1300, LinearEasing),
            Paso(Expresiones.dormida.copy(sueno = 1f), 1300, LinearEasing),
        ),
    )

    /** En reposo: de vez en cuando mira a un lado y al otro. */
    val mirarAlrededor = Animacion(
        listOf(
            Paso(Expresiones.neutral, 400),
            Paso(Expresiones.neutral, 2200),
            Paso(Expresiones.neutral.copy(miradaX = -0.8f), 350),
            Paso(Expresiones.neutral.copy(miradaX = -0.8f), 900),
            Paso(Expresiones.neutral.copy(miradaX = 0.8f, miradaY = -0.3f), 450),
            Paso(Expresiones.neutral.copy(miradaX = 0.8f, miradaY = -0.3f), 900),
        ),
    )

    /** Se le acerca la fecha: despierta y pendiente, echa miradas hacia arriba. */
    val atenta = Animacion(
        listOf(
            Paso(Expresiones.atenta, 300),
            Paso(Expresiones.atenta, 2000),
            Paso(Expresiones.atenta.copy(miradaX = 0.5f, miradaY = -0.6f), 300),
            Paso(Expresiones.atenta.copy(miradaX = 0.5f, miradaY = -0.6f), 800),
        ),
    )

    /** Ya toca llamar: da saltitos y se contonea para que la veas. */
    val ilusionada = Animacion(
        listOf(
            Paso(Expresiones.contenta, 250),
            Paso(Expresiones.contenta.copy(salto = -1f, estirar = 0.12f, bocaAbierta = 0.4f), 240),
            Paso(Expresiones.contenta.copy(estirar = -0.12f), 200),
            Paso(Expresiones.contenta, 180),
            Paso(Expresiones.contenta.copy(inclinacion = -10f), 220),
            Paso(Expresiones.contenta.copy(inclinacion = 10f), 320),
            Paso(Expresiones.contenta, 220),
            Paso(Expresiones.contenta, 1100),
        ),
    )

    /** Una vez: se sorprende y sonrie. */
    val saludo = Animacion(
        listOf(
            Paso(Expresiones.sorpresa, 160),
            Paso(Expresiones.contenta.copy(salto = -0.8f, inclinacion = -8f), 260),
            Paso(Expresiones.contenta, 320),
        ),
        bucle = false,
    )

    /** Una vez: risa con temblor. */
    val risa = Animacion(
        listOf(
            Paso(Expresiones.apretada.copy(bocaAbierta = 0.6f), 120),
            Paso(Expresiones.apretada.copy(bocaAbierta = 0.3f, salto = -0.3f, inclinacion = 6f), 110),
            Paso(Expresiones.apretada.copy(bocaAbierta = 0.6f, inclinacion = -6f), 110),
            Paso(Expresiones.apretada.copy(bocaAbierta = 0.3f, salto = -0.3f, inclinacion = 6f), 110),
            Paso(Expresiones.contenta, 300),
        ),
        bucle = false,
    )
}

/**
 * Reproduce animaciones y expresiones en una cara, y parpadea sola. Se crea
 * con [rememberEstadoCara]. Lo que se ve se lee en [actual], solo al dibujar.
 */
@Stable
class EstadoCara internal constructor(inicial: Expresion, private val scope: CoroutineScope) {
    private var desde by mutableStateOf(inicial)
    private var hacia by mutableStateOf(inicial)
    private val progreso = Animatable(1f)
    private val parpadeo = Animatable(1f)
    private var trabajo: Job? = null
    private var paso = 0

    /** La animacion en curso (o en pausa); null si solo hay una expresion. */
    var animacion by mutableStateOf<Animacion?>(null)
        private set
    var enPausa by mutableStateOf(false)
        private set

    /** La expresion hacia la que va: la que "tiene", aunque aun este llegando. */
    val expresion: Expresion get() = hacia

    /** Lo que se ve en este instante, parpadeo incluido. */
    val actual: Expresion
        get() {
            val e = mezcla(desde, hacia, progreso.value)
            return if (parpadeo.value < 1f) e.copy(ojos = e.ojos * parpadeo.value) else e
        }

    /**
     * Empieza [anim], o la reanuda si es la que estaba en pausa. [empezarEn]
     * permite que varias caras con la misma animacion no vayan al unisono.
     */
    fun reproducir(anim: Animacion, empezarEn: Int = 0, alAcabar: () -> Unit = {}) {
        if (anim != animacion || !enPausa) paso = empezarEn.coerceIn(0, anim.pasos.lastIndex)
        trabajo?.cancel()
        animacion = anim
        enPausa = false
        trabajo = scope.launch {
            while (true) {
                val inicio = System.nanoTime()
                while (paso < anim.pasos.size) {
                    val p = anim.pasos[paso]
                    ir(p.expresion, p.ms, p.curva)
                    paso++
                }
                paso = 0
                // Con las animaciones del sistema desactivadas cada paso dura
                // nada: se queda en el ultimo en vez de dar vueltas sin parar.
                if (!anim.bucle || System.nanoTime() - inicio < 50_000_000L) break
            }
            animacion = null
            alAcabar()
        }
    }

    /** Congela la animacion donde esta; [reproducir] con la misma la sigue. */
    fun pausar() {
        trabajo?.cancel()
        enPausa = animacion != null
    }

    /** Pasa a [e] sin animacion en curso. */
    fun poner(e: Expresion, ms: Int = 250) {
        trabajo?.cancel()
        animacion = null
        enPausa = false
        trabajo = scope.launch { ir(e, ms, FastOutSlowInEasing) }
    }

    /** Deja la animacion y vuelve a la cara neutra. */
    fun parar() = poner(Expresiones.neutral)

    private suspend fun ir(e: Expresion, ms: Int, curva: Easing) {
        desde = mezcla(desde, hacia, progreso.value)
        hacia = e
        progreso.snapTo(0f)
        if (ms <= 0) progreso.snapTo(1f) else progreso.animateTo(1f, tween(ms, easing = curva))
    }

    internal suspend fun parpadear() {
        while (true) {
            delay(Random.nextLong(2500, 6000))
            if (hacia.ojos < 0.3f) continue // dormida o riendo: ya los tiene cerrados
            parpadeo.animateTo(0f, tween(70))
            parpadeo.animateTo(1f, tween(120))
        }
    }
}

@Composable
fun rememberEstadoCara(inicial: Expresion = Expresiones.neutral): EstadoCara {
    val scope = rememberCoroutineScope()
    val estado = remember { EstadoCara(inicial, scope) }
    LaunchedEffect(estado) { estado.parpadear() }
    return estado
}

/**
 * Pinta la cara de [estado] encima del contenido. [mirar] se suma a la
 * mirada de la expresion (por ejemplo, hacia donde se mueve); se lee al
 * dibujar, asi que puede cambiar en cada fotograma.
 */
fun Modifier.cara(
    estado: EstadoCara,
    rasgos: Color,
    rubor: Color,
    brillo: Color,
    mirar: Density.() -> Offset = { Offset.Zero },
): Modifier = drawWithContent {
    drawContent()
    dibujarCara(estado.actual, size.minDimension / 2, rasgos, rubor, brillo, mirar())
}

private fun DrawScope.dibujarCara(
    e: Expresion,
    r: Float,
    rasgos: Color,
    rubor: Color,
    brillo: Color,
    mirarExtra: Offset,
    cuerpo: Color? = null,
) {
    val c = center
    withTransform({
        translate(0f, e.salto * r * 0.15f)
        rotate(e.inclinacion, c)
        scale(1f - e.estirar * 0.5f, 1f + e.estirar, Offset(c.x, c.y + r))
    }) {
        if (cuerpo != null) drawCircle(cuerpo, r, c)

        val mx = (e.miradaX + mirarExtra.x).coerceIn(-1f, 1f)
        val my = (e.miradaY + mirarExtra.y).coerceIn(-1f, 1f)
        val mirada = Offset(mx * r * 0.08f, my * r * 0.06f)
        val ojoY = c.y - r * 0.12f
        val anchoOjo = r * 0.17f
        val altoOjo = (r * 0.26f * e.ojos).coerceAtLeast(r * 0.025f)

        for (lado in floatArrayOf(-1f, 1f)) {
            val ojo = Offset(c.x + lado * r * 0.3f, ojoY) + mirada
            drawOval(rasgos, ojo - Offset(anchoOjo / 2, altoOjo / 2), Size(anchoOjo, altoOjo))
            if (e.ojos > 0.5f) {
                drawCircle(brillo, r * 0.034f, ojo + Offset(-anchoOjo * 0.18f, -altoOjo * 0.2f))
            }

            if (abs(e.cejas) > 0.02f) {
                val y = ojoY - r * (0.2f + 0.07f * e.cejas)
                // Fruncidas, la punta de dentro baja; levantadas, se arquean.
                val dentro = -lado * e.cejas.coerceAtMost(0f) * r * 0.06f
                drawLine(
                    rasgos.copy(alpha = min(1f, abs(e.cejas) * 2f)),
                    Offset(ojo.x - lado * r * 0.1f, y + dentro),
                    Offset(ojo.x + lado * r * 0.1f, y),
                    strokeWidth = r * 0.045f,
                    cap = StrokeCap.Round,
                )
            }

            if (e.rubor > 0f) {
                drawOval(
                    rubor.copy(alpha = 0.4f * e.rubor),
                    Offset(c.x + lado * r * 0.42f - r * 0.1f, ojoY + r * 0.18f),
                    Size(r * 0.2f, r * 0.11f),
                )
            }
        }

        val semiancho = r * 0.22f * (1f - 0.5f * e.bocaAbierta)
        val bocaY = c.y + r * 0.28f
        val curva = r * 0.22f * e.boca
        val boca = Path().apply {
            moveTo(c.x - semiancho, bocaY)
            // Al abrirla el labio de arriba sube: una "o", no una "D".
            quadraticTo(c.x, bocaY + curva - r * 0.2f * e.bocaAbierta, c.x + semiancho, bocaY)
            if (e.bocaAbierta > 0.02f) {
                quadraticTo(c.x, bocaY + curva + r * 0.4f * e.bocaAbierta, c.x - semiancho, bocaY)
                close()
            }
        }
        if (e.bocaAbierta > 0.02f) {
            drawPath(boca, rasgos)
        } else {
            drawPath(boca, rasgos, style = Stroke(r * 0.05f, cap = StrokeCap.Round))
        }

        // Dos zetas que suben y se desvanecen; se apagan al abrir los ojos.
        val zetas = (1f - e.ojos).coerceIn(0f, 1f)
        if (e.sueno > 0f && zetas > 0f) {
            for (i in 0..1) {
                val p = (e.sueno + i * 0.5f) % 1f
                val lado = r * 0.14f * (0.6f + p * 0.6f)
                val origen = Offset(c.x + r * (0.3f + p * 0.25f), c.y - r * (0.25f + p * 0.35f))
                val z = Path().apply {
                    moveTo(origen.x, origen.y)
                    lineTo(origen.x + lado, origen.y)
                    lineTo(origen.x, origen.y + lado)
                    lineTo(origen.x + lado, origen.y + lado)
                }
                drawPath(
                    z,
                    rasgos.copy(alpha = sin(p * PI.toFloat()) * zetas),
                    style = Stroke(r * 0.035f, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/**
 * La mascota de la app: una gota petroleo con la misma cara. Un toque la
 * hace reir. Es decorativa: no anuncia nada al lector de pantalla.
 */
@Composable
fun Mascota(estado: EstadoCara, modifier: Modifier = Modifier, tamano: Dp = 120.dp) {
    val esquema = MaterialTheme.colorScheme
    Canvas(
        modifier
            .size(tamano)
            .pointerInput(estado) {
                detectTapGestures {
                    val volverA = estado.animacion?.takeIf { it.bucle }
                    estado.reproducir(Animaciones.risa) { volverA?.let { estado.reproducir(it) } }
                }
            },
    ) {
        val r = size.minDimension / 2 * 0.86f
        val e = estado.actual
        // La sombra se encoge cuando salta.
        val sombra = 1f + e.salto.coerceAtMost(0f) * 0.35f
        drawOval(
            esquema.onSurface.copy(alpha = 0.12f),
            Offset(center.x - r * 0.7f * sombra, center.y + r * 1.02f),
            Size(r * 1.4f * sombra, r * 0.18f),
        )
        dibujarCara(e, r, esquema.onPrimary, esquema.tertiary, esquema.primary, Offset.Zero, cuerpo = esquema.primary)
    }
}
