package com.example.recuerdallamar.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Caras de solo ojos sobre una esfera. Los ojos son elipses pegadas a la
 * superficie: al girar la cabeza se deslizan por ella, se estrechan al
 * acercarse al borde y se esconden detras, y eso da el aire de 3D sin
 * sombreado. Una expresion es la postura de la cabeza y la forma de cada
 * ojo; una animacion, una lista de pasos (transicion y espera) entre
 * expresiones. [EstadoCara] la reproduce con el reloj de fotogramas, parpadea
 * y anade el movimiento ambiental (microsacadas, deriva, temblor).
 */

enum class MovimientoOjos { NINGUNO, MICROSACADAS, TEMBLOR }

enum class MovimientoCuerpo { NINGUNO, DERIVA, TEMBLOR }

/** Muelle: se pasa un poco y vuelve. Suave: entra y sale despacio. Seca: arranca de golpe. */
enum class Transicion { MUELLE, SUAVE, SECA }

enum class Modo { BUCLE, UNA_VEZ, IDA_Y_VUELTA }

/** Un ojo en el plano de la cara, en radios de la esfera. [angulo] en grados. */
@Immutable
data class Ojo(
    val ancho: Float = 0.24f,
    val alto: Float = 0.35f,
    val x: Float = 0f,
    val y: Float = 0f,
    val angulo: Float = 0f,
)

@Immutable
data class Expresion(
    /** Grados: mas de 0 mira arriba. */
    val cabezaX: Float = 0f,
    /** Grados: mas de 0 mira a la derecha de la pantalla. */
    val cabezaY: Float = 0f,
    /** Grados: ladea la cabeza. */
    val cabezaZ: Float = 0f,
    val izquierdo: Ojo = Ojo(),
    val derecho: Ojo = Ojo(),
    /** Distancia entre los centros de los ojos, en radios. */
    val separacion: Float = 0.54f,
    /** 0 sin perspectiva; hacia 0.5, muy marcada. */
    val perspectiva: Float = 0.3f,
    val movOjos: MovimientoOjos = MovimientoOjos.MICROSACADAS,
    val movCuerpo: MovimientoCuerpo = MovimientoCuerpo.DERIVA,
) {
    /** Los dos ojos iguales, en espejo: [ojo] es el derecho. */
    fun conOjos(ojo: Ojo) = copy(izquierdo = ojo.copy(x = -ojo.x, angulo = -ojo.angulo), derecho = ojo)

    fun girada(x: Float = cabezaX, y: Float = cabezaY, z: Float = cabezaZ) = copy(cabezaX = x, cabezaY = y, cabezaZ = z)
}

private fun mezcla(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun mezcla(a: Ojo, b: Ojo, t: Float) = Ojo(
    mezcla(a.ancho, b.ancho, t),
    mezcla(a.alto, b.alto, t),
    mezcla(a.x, b.x, t),
    mezcla(a.y, b.y, t),
    mezcla(a.angulo, b.angulo, t),
)

private fun mezcla(a: Expresion, b: Expresion, t: Float) = Expresion(
    cabezaX = mezcla(a.cabezaX, b.cabezaX, t),
    cabezaY = mezcla(a.cabezaY, b.cabezaY, t),
    cabezaZ = mezcla(a.cabezaZ, b.cabezaZ, t),
    izquierdo = mezcla(a.izquierdo, b.izquierdo, t),
    derecho = mezcla(a.derecho, b.derecho, t),
    separacion = mezcla(a.separacion, b.separacion, t),
    perspectiva = mezcla(a.perspectiva, b.perspectiva, t),
    movOjos = if (t < 0.5f) a.movOjos else b.movOjos,
    movCuerpo = if (t < 0.5f) a.movCuerpo else b.movCuerpo,
)

private fun curva(transicion: Transicion, t: Float): Float = when (transicion) {
    Transicion.SUAVE -> t * t * (3 - 2 * t)
    Transicion.SECA -> 1 - (1 - t) * (1 - t) * (1 - t)
    // Muelle amortiguado: se pasa un poco del destino y se asienta.
    Transicion.MUELLE -> if (t >= 1f) 1f else 1f - exp(-6f * t) * cos(9f * t)
}

object Expresiones {
    val neutral = Expresion()
    val contenta = Expresion(cabezaX = 8f).conOjos(Ojo(ancho = 0.29f, alto = 0.13f, y = -0.02f))
    val atenta = Expresion(cabezaX = 6f).conOjos(Ojo(ancho = 0.27f, alto = 0.42f))
    val sorpresa = Expresion(cabezaX = 10f, movCuerpo = MovimientoCuerpo.TEMBLOR)
        .conOjos(Ojo(ancho = 0.33f, alto = 0.4f))
    val dormida = Expresion(cabezaX = -14f, movOjos = MovimientoOjos.NINGUNO)
        .conOjos(Ojo(ancho = 0.27f, alto = 0.04f, y = 0.05f))

    /** Cejijunta: los ojos inclinados hacia dentro. */
    val impaciente = Expresion(movOjos = MovimientoOjos.NINGUNO)
        .conOjos(Ojo(ancho = 0.29f, alto = 0.22f, angulo = -18f, y = 0.02f))

    /** Al apretarla: ojos cerrados de gusto. */
    val apretada = Expresion(cabezaX = 4f, movOjos = MovimientoOjos.NINGUNO)
        .conOjos(Ojo(ancho = 0.3f, alto = 0.07f))
}

/** Cada paso: [transicionMs] para llegar a [expresion] y [esperaMs] quieta en ella. */
data class Paso(
    val expresion: Expresion,
    val transicionMs: Int = 300,
    val esperaMs: Int = 0,
    val transicion: Transicion = Transicion.MUELLE,
)

data class Parpadeo(
    val activo: Boolean = true,
    val primeroMs: Int = 900,
    val minMs: Int = 2200,
    val maxMs: Int = 5500,
    val duracionMs: Int = 170,
)

data class Animacion(
    val pasos: List<Paso>,
    val modo: Modo = Modo.BUCLE,
    val parpadeo: Parpadeo = Parpadeo(),
)

object Animaciones {
    /** Cabecea despacio con los ojos cerrados. */
    val durmiendo = Animacion(
        listOf(
            Paso(Expresiones.dormida, 1800, 300, Transicion.SUAVE),
            Paso(Expresiones.dormida.girada(x = -22f, y = 6f), 1800, 300, Transicion.SUAVE),
        ),
        modo = Modo.IDA_Y_VUELTA,
        parpadeo = Parpadeo(activo = false),
    )

    /** En reposo: mira a un lado y al otro. */
    val mirarAlrededor = Animacion(
        listOf(
            Paso(Expresiones.neutral, 400, 2400),
            Paso(Expresiones.neutral.girada(y = -32f), 450, 1200),
            Paso(Expresiones.neutral.girada(x = 10f, y = 28f), 550, 1200),
            Paso(Expresiones.neutral, 400, 800),
        ),
    )

    /** Se le acerca la fecha: ojos grandes, pendiente, mira hacia arriba. */
    val atenta = Animacion(
        listOf(
            Paso(Expresiones.atenta, 300, 1800),
            Paso(Expresiones.atenta.girada(x = 18f, y = 20f), 320, 900, Transicion.SECA),
            Paso(Expresiones.atenta.girada(x = 12f, y = -16f), 380, 700, Transicion.SECA),
        ),
    )

    /** Ya toca llamar: asiente contenta y se balancea. */
    val ilusionada = Animacion(
        listOf(
            Paso(Expresiones.contenta.girada(x = 18f), 180, 40, Transicion.SECA),
            Paso(Expresiones.contenta.girada(x = -4f), 180, 40, Transicion.SECA),
            Paso(Expresiones.contenta.girada(x = 18f), 180, 40, Transicion.SECA),
            Paso(Expresiones.contenta.girada(x = 4f), 200, 120),
            Paso(Expresiones.contenta.girada(y = -18f, z = -14f), 320, 150),
            Paso(Expresiones.contenta.girada(y = 18f, z = 14f), 380, 150),
            Paso(Expresiones.contenta, 300, 900),
        ),
    )

    /** Muy pasada de fecha: niega con la cabeza y te mira fijo. */
    val impaciente = Animacion(
        listOf(
            Paso(Expresiones.impaciente.girada(y = -22f), 220, 60, Transicion.SECA),
            Paso(Expresiones.impaciente.girada(y = 22f), 260, 60, Transicion.SECA),
            Paso(Expresiones.impaciente.girada(y = -16f), 240, 60, Transicion.SECA),
            Paso(Expresiones.impaciente, 300, 1600),
        ),
    )

    /** Una vez: se sorprende y sonrie. */
    val saludo = Animacion(
        listOf(
            Paso(Expresiones.sorpresa, 150, 180, Transicion.SECA),
            Paso(Expresiones.contenta.girada(x = 12f, z = -12f), 380, 300),
            Paso(Expresiones.contenta, 320),
        ),
        modo = Modo.UNA_VEZ,
    )

    /** Una vez: se rie meneandose. */
    val risa = Animacion(
        listOf(
            Paso(Expresiones.apretada.girada(z = -12f), 110, 0, Transicion.SECA),
            Paso(Expresiones.apretada.girada(z = 12f), 130, 0, Transicion.SECA),
            Paso(Expresiones.apretada.girada(z = -10f), 130, 0, Transicion.SECA),
            Paso(Expresiones.apretada.girada(z = 8f), 130, 0, Transicion.SECA),
            Paso(Expresiones.contenta, 380, 200),
        ),
        modo = Modo.UNA_VEZ,
    )
}

/**
 * Reproduce animaciones en una cara al ritmo de los fotogramas. Se crea con
 * [rememberEstadoCara]; [ahora] cambia en cada fotograma y solo lo lee el
 * dibujo, asi que nada se recompone.
 */
@Stable
class EstadoCara internal constructor(inicial: Expresion, private val semilla: Int) {
    /** Milisegundos del reloj de fotogramas. */
    var ahora by mutableLongStateOf(0L)
        private set

    var animacion by mutableStateOf<Animacion?>(null)
        private set

    private var desde = inicial
    private var hacia = inicial
    private var inicioFase = 0L
    private var duracion = 0L
    private var transicion = Transicion.SUAVE
    private var enTransicion = false
    private var paso = 0
    private var sentido = 1
    private var alAcabar: () -> Unit = {}
    private var pausadaEn = -1L
    private var proximoParpadeo = -1L
    private var parpadeoDesde = -1L
    private val azar = Random(semilla)

    val enPausa: Boolean get() = pausadaEn >= 0

    /**
     * Movimiento reducido: sin parpadeo ni vaiven, y las animaciones en bucle
     * se quedan en su primer gesto. Lo que dura una vez (reirse al tocarla)
     * si se hace: responde al dedo.
     */
    var quieta = false

    /**
     * Segundos para lo que se mueve solo alrededor de la cara (el sol que
     * respira y gira). Quieta, siempre 0.
     */
    val reloj: Float get() = if (quieta) 0f else ahora / 1000f

    /** La expresion hacia la que va. */
    val expresion: Expresion get() = hacia

    /**
     * Empieza [anim], o la reanuda donde se quedo si es la que estaba en
     * pausa. [empezarEn] sirve para que varias caras no vayan al unisono.
     */
    fun reproducir(anim: Animacion, empezarEn: Int = 0, alAcabar: () -> Unit = {}) {
        if (anim == animacion && enPausa) {
            inicioFase += ahora - pausadaEn
            pausadaEn = -1
            return
        }
        paso = empezarEn.coerceIn(0, anim.pasos.lastIndex)
        sentido = 1
        val p = anim.pasos[paso]
        ir(p.expresion, p.transicionMs, p.transicion)
        animacion = anim
        this.alAcabar = alAcabar
        proximoParpadeo = ahora + anim.parpadeo.primeroMs
    }

    /** Congela la animacion justo donde esta. */
    fun pausar() {
        if (animacion != null && !enPausa) pausadaEn = ahora
    }

    /** Pasa a [e] y se queda ahi, sin animacion. */
    fun poner(e: Expresion, ms: Int = 250, transicion: Transicion = Transicion.MUELLE) {
        ir(e, ms, transicion)
        animacion = null
    }

    fun parar() = poner(Expresiones.neutral)

    private fun ir(e: Expresion, ms: Int, t: Transicion) {
        desde = postura(ahora)
        hacia = e
        inicioFase = ahora
        duracion = ms.toLong()
        transicion = t
        enTransicion = true
        pausadaEn = -1
    }

    /** La expresion de este instante, sin el movimiento ambiental. */
    private fun postura(t: Long): Expresion {
        if (!enTransicion || duracion <= 0) return hacia
        val tt = if (enPausa) pausadaEn else t
        val progreso = ((tt - inicioFase).toFloat() / duracion).coerceIn(0f, 1f)
        return mezcla(desde, hacia, curva(transicion, progreso))
    }

    internal fun avanzar(t: Long) {
        if (ahora == 0L) {
            // Primer fotograma: lo pedido antes se cuenta desde ahora.
            inicioFase += t
            if (proximoParpadeo >= 0) proximoParpadeo += t
            if (pausadaEn >= 0) pausadaEn += t
        }
        ahora = t
        parpadear(t)
        if (enPausa) return
        // Tras mucho rato sin fotogramas (app en segundo plano) no se recupera el tiempo.
        if (t - inicioFase > 10_000L) inicioFase = t
        if (enTransicion && t >= inicioFase + duracion) {
            enTransicion = false
            inicioFase += duracion
        }
        val anim = animacion ?: return
        if (quieta && anim.modo != Modo.UNA_VEZ) return
        var vueltas = 0
        while (!enTransicion && vueltas++ <= anim.pasos.size * 2) {
            val fin = inicioFase + anim.pasos[paso].esperaMs
            if (t < fin) break
            val siguiente = siguientePaso(anim)
            if (siguiente == null) {
                animacion = null
                val callback = alAcabar
                alAcabar = {}
                callback()
                return
            }
            desde = hacia
            paso = siguiente
            val p = anim.pasos[paso]
            hacia = p.expresion
            inicioFase = fin
            duracion = p.transicionMs.toLong()
            transicion = p.transicion
            enTransicion = true
            if (t >= inicioFase + duracion) {
                enTransicion = false
                inicioFase += duracion
            }
        }
    }

    private fun siguientePaso(anim: Animacion): Int? {
        val n = anim.pasos.size
        return when (anim.modo) {
            Modo.BUCLE -> (paso + 1) % n
            Modo.UNA_VEZ -> if (paso + 1 < n) paso + 1 else null
            Modo.IDA_Y_VUELTA -> {
                if (n == 1) return 0
                if (paso + sentido !in 0 until n) sentido = -sentido
                paso + sentido
            }
        }
    }

    private fun parpadear(t: Long) {
        val p = animacion?.parpadeo ?: Parpadeo()
        if (!p.activo || quieta) {
            parpadeoDesde = -1
            return
        }
        if (proximoParpadeo < 0) proximoParpadeo = t + p.primeroMs
        if (parpadeoDesde >= 0 && t > parpadeoDesde + p.duracionMs) parpadeoDesde = -1
        if (parpadeoDesde < 0 && t >= proximoParpadeo) {
            parpadeoDesde = t
            proximoParpadeo = t + p.duracionMs + azar.nextInt(p.minMs, max(p.minMs + 1, p.maxMs))
        }
    }

    /** 1 abiertos, hacia 0 en mitad del parpadeo. */
    internal fun parpadeo(): Float {
        if (parpadeoDesde < 0) return 1f
        val d = (animacion?.parpadeo ?: Parpadeo()).duracionMs.coerceAtLeast(1)
        val progreso = ((ahora - parpadeoDesde).toFloat() / d).coerceIn(0f, 1f)
        return (1f - sin(progreso * PI.toFloat())).coerceAtLeast(0.05f)
    }

    /** Lo que se ve en este instante: postura mas movimiento ambiental. */
    internal fun muestra(): Expresion {
        val t = ahora
        val e = postura(t)
        if (quieta) return e
        val s = t / 1000f
        val f = semilla * 0.37f
        var x = e.cabezaX
        var y = e.cabezaY
        var z = e.cabezaZ
        when (e.movCuerpo) {
            MovimientoCuerpo.DERIVA -> {
                x += sin(s * 2.4f + f) * 2.5f
                y += sin(s * 1.9f + f * 1.7f) * 3.5f
                z += sin(s * 1.5f + f * 2.3f) * 1.5f
            }
            MovimientoCuerpo.TEMBLOR -> y += sin(s * 70f) * 4f
            MovimientoCuerpo.NINGUNO -> {}
        }
        var dx = 0f
        var dy = 0f
        when (e.movOjos) {
            MovimientoOjos.MICROSACADAS -> {
                // Saltos pequenos y rapidos de la mirada cada poco menos de un segundo.
                val reloj = t + semilla * 331L
                val tramo = reloj / 850L
                val salto = ((reloj % 850L) / 70f).coerceIn(0f, 1f)
                dx = mezcla(ruido(tramo - 1, 1), ruido(tramo, 1), salto) * 0.035f
                dy = mezcla(ruido(tramo - 1, 2), ruido(tramo, 2), salto) * 0.025f
            }
            MovimientoOjos.TEMBLOR -> dx = sin(s * 90f) * 0.015f
            MovimientoOjos.NINGUNO -> {}
        }
        return e.copy(
            cabezaX = x,
            cabezaY = y,
            cabezaZ = z,
            izquierdo = e.izquierdo.copy(x = e.izquierdo.x + dx, y = e.izquierdo.y + dy),
            derecho = e.derecho.copy(x = e.derecho.x + dx, y = e.derecho.y + dy),
        )
    }

    /** De -1 a 1, fijo para cada tramo y cara. */
    private fun ruido(tramo: Long, canal: Int): Float {
        var h = tramo * 6364136223846793005L + semilla * 1442695040888963407L + canal * 2654435761L
        h = h xor (h ushr 33)
        h *= -0xae502812aa7333L
        h = h xor (h ushr 29)
        return ((h ushr 40).toFloat() / (1L shl 24)) * 2f - 1f
    }
}

@Composable
fun rememberEstadoCara(inicial: Expresion = Expresiones.neutral, semilla: Int = 0): EstadoCara {
    val estado = remember { EstadoCara(inicial, semilla) }
    estado.quieta = LocalMovimientoReducido.current
    LaunchedEffect(estado) {
        while (true) withInfiniteAnimationFrameNanos { estado.avanzar(it / 1_000_000L) }
    }
    return estado
}

/**
 * Pinta los ojos de [estado] sobre el contenido, que hace de esfera. [giro]
 * suma grados a la cabeza (x: hacia la derecha, y: hacia abajo); se lee al
 * dibujar, asi que puede cambiar en cada fotograma.
 */
fun Modifier.cara(
    estado: EstadoCara,
    ojos: Color,
    giro: Density.() -> Offset = { Offset.Zero },
): Modifier = drawWithContent {
    drawContent()
    estado.ahora
    dibujarOjos(estado.muestra(), estado.parpadeo(), center, size.minDimension / 2, ojos, giro())
}

private const val PUNTOS = 32

internal fun DrawScope.dibujarOjos(e: Expresion, parpadeo: Float, centro: Offset, radio: Float, color: Color, giro: Offset) {
    val rad = PI.toFloat() / 180f
    val cabezaY = (e.cabezaY + giro.x) * rad
    val cabezaX = (e.cabezaX - giro.y) * rad
    val cabezaZ = e.cabezaZ * rad
    val cy = cos(cabezaY)
    val sy = sin(cabezaY)
    val cx = cos(cabezaX)
    val sx = sin(cabezaX)
    val cz = cos(cabezaZ)
    val sz = sin(cabezaZ)
    val p = e.perspectiva.coerceIn(0f, 0.6f)
    // Con perspectiva la silueta de la esfera se ve mayor: se escala para que ocupe el radio.
    val ajuste = sqrt(1f - p * p)

    for (lado in intArrayOf(-1, 1)) {
        val ojo = if (lado < 0) e.izquierdo else e.derecho
        val centroU = lado * e.separacion / 2 + ojo.x
        val centroV = ojo.y
        val a = ojo.ancho / 2
        val b = (ojo.alto / 2 * parpadeo).coerceAtLeast(0.006f)
        val ca = cos(ojo.angulo * rad)
        val sa = sin(ojo.angulo * rad)
        val trazo = Path()
        var profundidad = 0f
        for (i in 0 until PUNTOS) {
            val th = 2f * PI.toFloat() * i / PUNTOS
            val ex = a * cos(th)
            val ey = b * sin(th)
            var u = centroU + ex * ca - ey * sa
            var v = centroV + ex * sa + ey * ca
            val d2 = u * u + v * v
            if (d2 > 0.96f) {
                val k = sqrt(0.96f / d2)
                u *= k
                v *= k
            }
            // Punto de la esfera que queda debajo, mirando a camara.
            val w = sqrt(max(0f, 1f - u * u - v * v))
            // Giro: primero a los lados, luego arriba/abajo, luego ladear.
            val x1 = u * cy + w * sy
            val z1 = -u * sy + w * cy
            val y2 = v * cx - z1 * sx
            val z2 = v * sx + z1 * cx
            val x3 = x1 * cz - y2 * sz
            val y3 = x1 * sz + y2 * cz
            profundidad += z2
            val px: Float
            val py: Float
            if (z2 < p) {
                // Detras del borde: se queda pegado a la silueta.
                val l = sqrt(x3 * x3 + y3 * y3).coerceAtLeast(1e-4f)
                px = x3 / l
                py = y3 / l
            } else {
                val f = ajuste / (1f - p * z2)
                px = x3 * f
                py = y3 * f
            }
            val x = centro.x + px * radio
            val y = centro.y + py * radio
            if (i == 0) trazo.moveTo(x, y) else trazo.lineTo(x, y)
        }
        if (profundidad / PUNTOS > p * 0.6f) {
            trazo.close()
            drawPath(trazo, color)
        }
    }
}

/**
 * La mascota de la app: una esfera azul marino con ojos crema. Un toque la hace
 * reir. Es decorativa: no anuncia nada al lector de pantalla.
 */
@Composable
fun Mascota(estado: EstadoCara, modifier: Modifier = Modifier, tamano: Dp = 120.dp) {
    val esquema = MaterialTheme.colorScheme
    Canvas(
        modifier
            .size(tamano)
            .pointerInput(estado) {
                detectTapGestures {
                    val volverA = estado.animacion?.takeIf { it.modo != Modo.UNA_VEZ }
                    estado.reproducir(Animaciones.risa) { volverA?.let { estado.reproducir(it) } }
                }
            },
    ) {
        estado.ahora
        val r = size.minDimension / 2 * 0.86f
        val e = estado.muestra()
        // La sombra se estrecha cuando levanta la cabeza.
        val sombra = 1f - (e.cabezaX / 90f).coerceIn(-0.2f, 0.3f)
        drawOval(
            esquema.onSurface.copy(alpha = 0.12f),
            Offset(center.x - r * 0.7f * sombra, center.y + r * 1.02f),
            Size(r * 1.4f * sombra, r * 0.16f),
        )
        drawCircle(esquema.primary, r, center)
        dibujarOjos(e, estado.parpadeo(), center, r, esquema.onPrimary, Offset.Zero)
    }
}
