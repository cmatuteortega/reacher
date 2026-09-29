package com.example.recuerdallamar.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.recuerdallamar.FotoContacto
import com.example.recuerdallamar.R
import com.example.recuerdallamar.datos.Contacto
import com.example.recuerdallamar.datos.porFrecuencia
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Personas en orbita alrededor del sol de la esquina: las burbujas de siempre
 * (mas grandes cuanto mas les toca), cada una en un anillo. La urgencia solo
 * se ve en el tamano; el anillo es la frecuencia: se ordenan de la mas a
 * menudo a la que menos y cada anillo se queda con una parte fija del total,
 * de dentro afuera, sin partir a quienes van cada tantos dias iguales. Cada
 * anillo lleva escrito "< X dias", con X la frecuencia mas larga de los suyos.
 * Con los dias nadie cambia de anillo: solo crece; al hablar con alguien,
 * encoge. Los anillos son cuartos de circulo con centro en la esquina,
 * recortados para que todo se vea sin desplazar.
 */

/** Parte de la gente en cada anillo, de dentro afuera, segun cuantos anillos hay. */
private val PARTES = listOf(
    floatArrayOf(1f),
    floatArrayOf(0.35f, 0.65f),
    floatArrayOf(0.2f, 0.35f, 0.45f),
    floatArrayOf(0.1f, 0.25f, 0.3f, 0.35f),
    floatArrayOf(0.08f, 0.14f, 0.2f, 0.26f, 0.32f),
    floatArrayOf(0.06f, 0.1f, 0.15f, 0.2f, 0.22f, 0.27f),
)
private val ANILLOS_MAX = PARTES.size

/** Cuantos anillos para tanta gente: con poca, no merece la pena repartir. */
private fun anillosPara(cuantos: Int): Int = when {
    cuantos <= 2 -> 1
    cuantos <= 5 -> 2
    cuantos <= 10 -> 3
    cuantos <= 18 -> 4
    cuantos <= 28 -> 5
    else -> 6
}

/** Hasta donde llega el anillo de fuera, en partes de la distancia a la esquina opuesta. */
private const val ALCANCE = 0.8f

private val HUECO_SOL = 16.dp
private val ENTRE_ANILLOS_MAX = 170.dp
private val MARGEN_ORBITA = 12.dp
private val BORDE_ORBITA = 8.dp
private val ANCHO_EXTRA_NOMBRE = 24.dp
private val ALTO_NOMBRE = 22.dp

/** Aire a cada lado del letrero de un anillo. */
private val SEPARACION_LETRERO = 6.dp

/** Con mucha gente las burbujas encogen hasta aqui antes de apretarse. */
private val RADIO_SUELO = 16.dp

/** Por debajo, el nombre no cabe debajo de la burbuja. */
private val RADIO_CON_NOMBRE = 20.dp

/** Vaiven de cada anillo: como mucho estos radianes, y a esta velocidad (rad/s) el de dentro. */
private const val VAIVEN_MAX = 0.06f
private const val VAIVEN_VELOCIDAD = 0.5f

/** Segundos entre la salida de una burbuja del sol y la siguiente al abrir la vista. */
private const val SALIDA_ESCALONADA = 0.04f

/** Lo que el plano necesita de cada persona: cada cuantos dias toca y cuanto le toca ya. */
internal class EnOrbita(val id: Long, val frecuencia: Int, val urgencia: Float)

/** Donde descansa cada persona: anillo, angulo (0 a la derecha, PI/2 abajo) y radio de su burbuja. */
internal class Puesto(val anillo: Int, val angulo: Float, val radio: Float)

/** El letrero de un anillo: "<[dias] D", empezando en [angulo]. */
internal class Letrero(val dias: Int, val angulo: Float)

internal class PlanoOrbitas(
    val radios: List<Float>,
    val puestos: Map<Long, Puesto>,
    /** Cuanto puede ir y venir cada anillo sin salirse ni pisar a nadie. */
    val vaivenes: List<Float>,
    val letreros: List<Letrero>,
)

/**
 * Anillos y puestos de todos. [personas] van ordenadas por frecuencia, de la
 * mas corta a la mas larga. El sol tiene centro en (0, [solY]) y radio
 * [solRadio]; la zona libre va de 0 a [ancho] y de 0 a [alto]. Al principio
 * de cada anillo quedan libres [largoLetrero] para su letrero.
 *
 * Quien va en cada anillo no depende de la urgencia, solo de cuantos son,
 * sus frecuencias y el lienzo: se cuenta con burbujas de un tamano fijo. Si
 * no caben, se anaden anillos (en horizontal cabe poca gente en cada uno) o
 * se cuenta con burbujas mas pequenas; luego, si con su tamano de verdad no
 * caben, encogen todas, y si ni asi, se pisan.
 */
internal fun planearOrbitas(
    personas: List<EnOrbita>,
    ancho: Float,
    alto: Float,
    solY: Float,
    solRadio: Float,
    largoLetrero: Float,
    densidad: Density,
): PlanoOrbitas {
    if (personas.isEmpty()) return PlanoOrbitas(emptyList(), emptyMap(), emptyList(), emptyList())
    val anillos = anillosPara(personas.size)..min(ANILLOS_MAX, max(anillosPara(personas.size), personas.size))
    return with(densidad) {
        fun intentar(n: Int, contando: Dp, aunqueNoQuepan: Boolean = false) =
            repartirEnAnillos(personas, n, contando.toPx(), aunqueNoQuepan, ancho, alto, solY, solRadio, largoLetrero)
        listOf(RADIO_MIN, RADIO_SUELO).firstNotNullOfOrNull { contando ->
            anillos.firstNotNullOfOrNull { intentar(it, contando) }
        } ?: intentar(anillos.last, RADIO_SUELO, aunqueNoQuepan = true)!!
    }
}

/**
 * El plano con [anillosPedidos] anillos (o menos, si no hay tantas
 * frecuencias distintas), contando cuantos caben en cada uno con burbujas
 * de [contando] de radio. Null si al de fuera no le caben los suyos, salvo
 * [aunqueNoQuepan].
 */
private fun Density.repartirEnAnillos(
    personas: List<EnOrbita>,
    anillosPedidos: Int,
    contando: Float,
    aunqueNoQuepan: Boolean,
    ancho: Float,
    alto: Float,
    solY: Float,
    solRadio: Float,
    largoLetrero: Float,
): PlanoOrbitas? {
    val cuantos = personas.size
    val grupos = cortesPorFrecuencia(personas.map { it.frecuencia }, PARTES[anillosPedidos - 1])
    val n = grupos.size
    val margen = MARGEN_ORBITA.toPx()
    val borde = BORDE_ORBITA.toPx()
    val extraAncho = ANCHO_EXTRA_NOMBRE.toPx() / 2
    val extraAlto = ALTO_NOMBRE.toPx()
    val suelo = RADIO_SUELO.toPx()
    val minimo = RADIO_MIN.toPx()

    // Los anillos, de junto al sol hasta cerca de la esquina opuesta. Las
    // burbujas mas grandes caben justas entre dos anillos.
    val lejos = hypot(ancho, alto - solY) * ALCANCE
    fun separacion(interior: Float) =
        if (n == 1) 0f else min((lejos - interior) / (n - 1), ENTRE_ANILLOS_MAX.toPx()).coerceAtLeast(0f)
    var maximo = min(ancho * 0.16f, RADIO_MAX.toPx())
    val paso = separacion(solRadio + HUECO_SOL.toPx() + maximo)
    if (n > 1) maximo = min(maximo, (paso - margen - extraAlto) / 2)
    maximo = maximo.coerceAtLeast(minimo)
    val interior = solRadio + HUECO_SOL.toPx() + maximo
    val entre = separacion(interior)
    val radios = List(n) { interior + entre * it }

    fun radiosBurbuja(escala: Float) = personas.map {
        (radioPorUrgencia(it.urgencia, minimo, maximo) * escala).coerceAtLeast(suelo)
    }

    /** El trozo visible de cada anillo, en radianes, para burbujas de hasta [b] de radio. */
    fun visible(r: Float, b: Float): Pair<Float, Float> {
        val xMin = borde + b + extraAncho
        val xMax = ancho - xMin
        val yMin = borde + b - solY
        val yMax = alto - b - extraAlto - solY
        val desde = max(acos((xMax / r).coerceIn(-1f, 1f)), asin((yMin / r).coerceIn(-1f, 1f)))
        val hasta = min(acos((xMin / r).coerceIn(-1f, 1f)), asin((yMax / r).coerceIn(-1f, 1f)))
        return desde to max(desde, hasta)
    }

    /** Lo que queda para las burbujas, pasado el letrero. */
    fun arco(r: Float, b: Float): Pair<Float, Float> {
        val (desde, hasta) = visible(r, b)
        return min(desde + largoLetrero / r, hasta) to hasta
    }

    fun largo(tamanos: List<Float>, desde: Int, hasta: Int): Float =
        if (hasta <= desde) 0f else (desde until hasta).sumOf { 2.0 * tamanos[it] + margen }.toFloat() - margen

    // Cada anillo con los suyos; lo que no cabe pasa, en orden, al siguiente.
    val reparto = IntArray(n)
    var quedan = 0
    for (k in 0 until n) {
        quedan += grupos[k]
        val (desde, hasta) = arco(radios[k], contando)
        val caben = ((radios[k] * (hasta - desde) + margen) / (2 * contando + margen)).toInt()
        if (k == n - 1 && caben < quedan && !aunqueNoQuepan) return null
        reparto[k] = if (k == n - 1) quedan else min(caben, quedan)
        quedan -= reparto[k]
    }

    // Del tamano que les toca hacia abajo, hasta el suelo; si ni asi, se pisan.
    val escalas = generateSequence(1f) { it * 0.9f }.takeWhile { minimo * it >= suelo * 0.9f }.toList() + (suelo / minimo)
    fun caben(escala: Float): Pair<List<Float>, List<Pair<Float, Float>>>? {
        val tamanos = radiosBurbuja(escala)
        val arcos = radios.map { arco(it, tamanos.max()) }
        var inicio = 0
        for (k in 0 until n) {
            if (largo(tamanos, inicio, inicio + reparto[k]) > radios[k] * (arcos[k].second - arcos[k].first) + 0.5f) return null
            inicio += reparto[k]
        }
        return tamanos to arcos
    }
    val (tamanos, arcos) = escalas.firstNotNullOfOrNull { caben(it) }
        ?: radiosBurbuja(escalas.last()).let { t -> t to radios.map { arco(it, t.max()) } }

    // En cada anillo, en orden, con el sitio que sobra repartido por igual
    // entre ellas y en los extremos.
    val puestos = HashMap<Long, Puesto>(cuantos)
    val vaivenes = MutableList(n) { 0f }
    val letreros = ArrayList<Letrero>(n)
    var i = 0
    for (k in 0 until n) {
        val r = radios[k]
        val (desde, hasta) = arcos[k]
        val aqui = reparto[k]
        // Todos los suyos van cada tantos dias o menos.
        val dias = personas.subList(i, i + aqui).maxOfOrNull { it.frecuencia } ?: 0
        letreros += Letrero(dias, visible(r, tamanos.max()).first)
        val sobra = r * (hasta - desde) - largo(tamanos, i, i + aqui)
        if (sobra >= 0f) {
            val hueco = sobra / (aqui + 1)
            var recorrido = desde * r + hueco
            repeat(aqui) {
                val b = tamanos[i]
                puestos[personas[i].id] = Puesto(k, (recorrido + b) / r, b)
                recorrido += 2 * b + margen + hueco
                i++
            }
            vaivenes[k] = min(hueco / 2 / r, VAIVEN_MAX)
        } else {
            // Apretadas: a distancias iguales de punta a punta, sin salirse
            // del arco ni tapar el letrero.
            val b = tamanos.subList(i, i + aqui).max() / r
            val primero = min(desde + b, (desde + hasta) / 2)
            val ultimo = max(hasta - b, primero)
            repeat(aqui) { j ->
                val t = if (aqui == 1) 0.5f else j / (aqui - 1f)
                puestos[personas[i].id] = Puesto(k, primero + (ultimo - primero) * t, tamanos[i])
                i++
            }
        }
    }
    return PlanoOrbitas(radios, puestos, vaivenes, letreros)
}

/**
 * Cuantos de [frecuencias] (ordenadas) van a cada anillo, en proporcion a
 * [pesos], pero cortando solo donde cambia la frecuencia: cada corte va al
 * cambio mas cercano al que le tocaba. Si no hay cambios para todos, salen
 * menos anillos.
 */
private fun cortesPorFrecuencia(frecuencias: List<Int>, pesos: FloatArray): IntArray {
    val cuantos = frecuencias.size
    val cambios = (1 until cuantos).filter { frecuencias[it] != frecuencias[it - 1] }
    val total = pesos.sum().takeIf { it > 0f } ?: 1f
    val fines = ArrayList<Int>(pesos.size)
    var acumulado = 0f
    for (k in 0 until pesos.size - 1) {
        acumulado += pesos[k] / total
        val ideal = acumulado * cuantos
        val antes = fines.lastOrNull() ?: 0
        val corte = cambios.filter { it > antes }.minByOrNull { abs(it - ideal) } ?: break
        fines += corte
    }
    fines += cuantos
    return IntArray(fines.size) { fines[it] - (fines.getOrNull(it - 1) ?: 0) }
}

/** Una persona en su orbita: donde deberia estar y donde se ve ahora. */
internal class Satelite(val id: Long) {
    var anillo = 0
    var angulo = 0f
    var radioOrbita = 0f
    var radioBurbuja = 0f

    /** Lo que se ve: angulo y distancia al sol, tamano y crecimiento al nacer. */
    var a = 0f
    var r = 0f
    var tam = 0f
    var escala = 0f
    var espera = 0f

    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
}

/**
 * Las cuentas del movimiento, sin Compose. Todo se acerca poco a poco a lo
 * que le toca: al cambiar de anillo una burbuja viaja en espiral hasta el
 * nuevo. Cada anillo va y viene un poco, uno en cada sentido.
 */
internal class Orbitales {
    val satelites = LinkedHashMap<Long, Satelite>()
    val radios = FloatArray(ANILLOS_MAX)
    val radiosVistos = FloatArray(ANILLOS_MAX)
    val opacidad = FloatArray(ANILLOS_MAX)
    private val vaivenes = FloatArray(ANILLOS_MAX)
    private val vaivenesVistos = FloatArray(ANILLOS_MAX)

    /** Dias y angulo de inicio del letrero de cada anillo; 0 dias, sin letrero. */
    val diasLetrero = IntArray(ANILLOS_MAX)
    val anguloLetrero = FloatArray(ANILLOS_MAX)
    var solY = 0f
    private var anillos = 0
    private var t = 0f

    /** Movimiento reducido: los anillos no se mecen. */
    var calma = false

    fun colocar(plano: PlanoOrbitas, orden: List<Long>, solY: Float, nacerEn: Float) {
        this.solY = solY
        anillos = plano.radios.size
        for (k in 0 until ANILLOS_MAX) {
            radios[k] = plano.radios.getOrElse(k) { plano.radios.lastOrNull() ?: 0f }
            if (radiosVistos[k] == 0f) radiosVistos[k] = radios[k]
            vaivenes[k] = plano.vaivenes.getOrElse(k) { 0f }
            // Un anillo que se apaga conserva su letrero mientras se desvanece.
            plano.letreros.getOrNull(k)?.let {
                diasLetrero[k] = it.dias
                anguloLetrero[k] = it.angulo
            }
        }
        val primeraVez = satelites.isEmpty()
        val nuevos = LinkedHashMap<Long, Satelite>()
        orden.forEachIndexed { i, id ->
            val puesto = plano.puestos[id] ?: return@forEachIndexed
            val s = satelites[id] ?: Satelite(id).also {
                // Sale del sol, que esta por encima de todo, y va a su anillo.
                it.r = nacerEn
                it.a = puesto.angulo
                it.tam = puesto.radio
                it.espera = if (primeraVez) i * SALIDA_ESCALONADA else 0f
                it.x = cos(it.a) * it.r
                it.y = solY + sin(it.a) * it.r
            }
            s.anillo = puesto.anillo
            s.angulo = puesto.angulo
            s.radioOrbita = radios[puesto.anillo]
            s.radioBurbuja = puesto.radio
            nuevos[id] = s
        }
        satelites.clear()
        satelites.putAll(nuevos)
    }

    /**
     * Llega de fuera (un planeta de la bienvenida) en ([x], [y]) del lienzo y
     * con [radio]: sale de ahi, ya entero, y va a su puesto como cualquiera.
     */
    fun recibir(id: Long, x: Float, y: Float, radio: Float) {
        val s = satelites[id] ?: return
        s.r = hypot(x, y - solY)
        s.a = atan2(y - solY, x)
        s.tam = radio
        s.escala = 1f
        s.espera = 0f
        s.x = x
        s.y = y
    }

    fun paso(dt: Float) {
        t += dt
        val suave = 1f - exp(-2.2f * dt)
        val crecer = 1f - exp(-4f * dt)
        val vaivenAhora = FloatArray(ANILLOS_MAX)
        for (k in 0 until ANILLOS_MAX) {
            radiosVistos[k] += (radios[k] - radiosVistos[k]) * suave
            val visible = if (k < anillos) 1f else 0f
            opacidad[k] += (visible - opacidad[k]) * crecer
            vaivenesVistos[k] += ((if (calma) 0f else vaivenes[k]) - vaivenesVistos[k]) * suave
            val sentido = if (k % 2 == 0) 1f else -1f
            vaivenAhora[k] = vaivenesVistos[k] * sentido * sin(t * VAIVEN_VELOCIDAD / (1f + k * 0.3f) + k * 1.3f)
        }
        for (s in satelites.values) {
            if (s.espera > 0f) {
                s.espera -= dt
                continue
            }
            s.a += (s.angulo - s.a) * suave
            s.r += (s.radioOrbita - s.r) * suave
            s.tam += (s.radioBurbuja - s.tam) * suave
            s.escala += (1f - s.escala) * crecer
            val angulo = s.a + vaivenAhora[s.anillo]
            val x = cos(angulo) * s.r
            val y = solY + sin(angulo) * s.r
            if (dt > 0f) {
                s.vx = (x - s.x) / dt
                s.vy = (y - s.y) / dt
            }
            s.x = x
            s.y = y
        }
    }
}

/**
 * Personas en anillos alrededor del sol de la esquina, un circulo de
 * [solRadio] con centro [solArriba] por encima del lienzo, en su borde
 * izquierdo. Abajo se dejan libres [huecoInferior], para el +. [contactos]
 * llega ya ordenado por urgencia; los anillos van por frecuencia, pero con
 * TalkBack se recorren en ese orden, con [onHablado] como accion ademas de abrir.
 * Si [relevo] trae los planetas de la bienvenida, cada uno sale de donde
 * estaba y va a su anillo.
 */
@Composable
fun VistaOrbitas(
    contactos: List<Contacto>,
    fotosPermitidas: Boolean,
    onAbrir: (Contacto) -> Unit,
    onHablado: (Contacto) -> Unit,
    huecoInferior: Dp,
    solArriba: Dp,
    solRadio: Dp,
    modifier: Modifier = Modifier,
    relevo: Relevo? = null,
) {
    val densidad = LocalDensity.current
    val orbitales = remember { Orbitales() }
    orbitales.calma = LocalMovimientoReducido.current
    val llegadas = remember { relevo?.tomarPlanetas()?.toMutableMap() }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { lienzo ->
                // Los planetas de la bienvenida, desde donde estaban, en coordenadas del lienzo.
                if (llegadas.isNullOrEmpty()) return@onGloballyPositioned
                val origen = lienzo.positionInWindow()
                llegadas.forEach { (id, p) ->
                    val en = p.centro - origen
                    orbitales.recibir(id, en.x, en.y, p.radio)
                }
                llegadas.clear()
            },
    ) {
        val ancho = constraints.maxWidth.toFloat()
        val alto = constraints.maxHeight.toFloat()

        val hoy = remember(contactos) { LocalDate.now() }
        val urgencias = remember(contactos, hoy) { contactos.associate { it.id to it.urgencia(hoy) } }

        // Los letreros de los anillos: "<7 D", estrechos, espaciados y del
        // color del sol, como en un mapa del cielo.
        val recursos = LocalContext.current.resources
        val esquema = MaterialTheme.colorScheme
        val estilo = MaterialTheme.typography.labelSmall
        val pincel = remember(esquema, estilo, densidad) {
            Paint().apply {
                isAntiAlias = true
                color = esquema.tertiary.toArgb()
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                textSize = with(densidad) { estilo.fontSize.toPx() * 1.1f }
                letterSpacing = 0.18f
            }
        }
        val letrero = { dias: Int -> recursos.getString(R.string.letrero_orbita, dias) }
        val separacionLetrero = with(densidad) { SEPARACION_LETRERO.toPx() }
        // Sitio para el mas largo, que ninguno pise a la primera burbuja.
        val largoLetrero = remember(contactos, pincel) {
            pincel.measureText(letrero(contactos.maxOfOrNull { it.frecuenciaDias } ?: 1)) + separacionLetrero * 2
        }

        val plano = remember(contactos, urgencias, ancho, alto, huecoInferior, solArriba, solRadio, largoLetrero, densidad) {
            with(densidad) {
                val solY = -solArriba.toPx()
                val sol = solRadio.toPx()
                val porFrecuencia = contactos.porFrecuencia()
                planearOrbitas(
                    porFrecuencia.map { EnOrbita(it.id, it.frecuenciaDias, urgencias.getValue(it.id)) },
                    ancho,
                    alto - huecoInferior.toPx(),
                    solY,
                    sol,
                    largoLetrero,
                    densidad,
                ).also { orbitales.colocar(it, porFrecuencia.map { c -> c.id }, solY, sol * 0.5f) }
            }
        }

        val fotograma = remember { mutableLongStateOf(0L) }
        LaunchedEffect(orbitales) {
            var antes = 0L
            while (true) {
                withInfiniteAnimationFrameNanos { ahora ->
                    orbitales.paso(if (antes == 0L) 0f else ((ahora - antes) / 1e9f).coerceAtMost(1f / 30f))
                    antes = ahora
                    fotograma.longValue = ahora
                }
            }
        }

        // Los anillos, a trazos como en la bienvenida, con su letrero al
        // principio, por fuera y siguiendo la curva.
        val arco = remember { android.graphics.Path() }
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            fotograma.longValue
            val trazo = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())))
            val centro = Offset(0f, orbitales.solY)
            val opacidadTexto = pincel.alpha
            for (k in 0 until ANILLOS_MAX) {
                val alfa = orbitales.opacidad[k]
                if (alfa < 0.01f) continue
                val r = orbitales.radiosVistos[k]
                drawCircle(esquema.outlineVariant.copy(alpha = alfa), r, centro, style = trazo)
                val dias = orbitales.diasLetrero[k]
                if (dias <= 0 || r <= 0f) continue
                // Al reves de las agujas del reloj: asi se lee de pie, por fuera.
                val barrido = Math.toDegrees((largoLetrero / r).toDouble()).toFloat()
                arco.reset()
                arco.addArc(
                    centro.x - r,
                    centro.y - r,
                    centro.x + r,
                    centro.y + r,
                    Math.toDegrees(orbitales.anguloLetrero[k].toDouble()).toFloat() + barrido,
                    -barrido,
                )
                pincel.alpha = (opacidadTexto * alfa).roundToInt()
                drawIntoCanvas {
                    it.nativeCanvas.drawTextOnPath(letrero(dias), arco, separacionLetrero, -3.dp.toPx(), pincel)
                }
                pincel.alpha = opacidadTexto
            }
        }

        contactos.forEachIndexed { orden, contacto ->
            val satelite = orbitales.satelites[contacto.id] ?: return@forEachIndexed
            val puesto = plano.puestos[contacto.id] ?: return@forEachIndexed
            key(contacto.id) {
                BurbujaOrbita(
                    contacto = contacto,
                    urgencia = urgencias.getValue(contacto.id),
                    fotosPermitidas = fotosPermitidas,
                    radio = with(densidad) { puesto.radio.toDp() },
                    satelite = satelite,
                    fotograma = fotograma,
                    orden = orden,
                    onToque = { onAbrir(contacto) },
                    onHablado = { onHablado(contacto) },
                )
            }
        }
    }
}

/** La burbuja de una persona, como en la vista de burbujas, puesta donde diga su satelite. */
@Composable
private fun BurbujaOrbita(
    contacto: Contacto,
    urgencia: Float,
    fotosPermitidas: Boolean,
    radio: Dp,
    satelite: Satelite,
    fotograma: MutableLongState,
    orden: Int,
    onToque: () -> Unit,
    onHablado: () -> Unit,
) {
    val alToque by rememberUpdatedState(onToque)
    val hablado by rememberUpdatedState(onHablado)
    val abrir = stringResource(R.string.abrir)
    val heLlamado = stringResource(R.string.he_llamado_hoy)
    val acciones = remember(heLlamado) {
        listOf(
            CustomAccessibilityAction(heLlamado) {
                hablado()
                true
            },
        )
    }
    val toca = urgencia >= 1f
    val esquema = MaterialTheme.colorScheme
    val anillo = when {
        toca -> esquema.acentoLegible
        urgencia >= CERCA -> esquema.primary
        else -> esquema.outlineVariant
    }
    val grande = radio > 56.dp
    val context = LocalContext.current
    var foto by remember(contacto.telefono) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(contacto.telefono, fotosPermitidas, grande) {
        foto = FotoContacto.cargar(context, contacto.telefono, miniatura = !grande)
    }
    val descripcion = if (toca) {
        stringResource(R.string.burbuja_toca, contacto.nombre)
    } else {
        stringResource(R.string.burbuja_plazo, contacto.nombre, (urgencia * 100).roundToInt())
    }
    val inicial = MaterialTheme.typography.headlineMedium.copy(
        fontSize = with(LocalDensity.current) { (radio * 0.8f).toSp() },
    )
    var pulsada by remember { mutableStateOf(false) }
    val escala by animateFloatAsState(
        if (pulsada) 0.93f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "escalaOrbita",
    )
    val conNombre = radio >= RADIO_CON_NOMBRE
    val anchoCaja = radio * 2 + ANCHO_EXTRA_NOMBRE
    val altoCaja = radio * 2 + ALTO_NOMBRE

    Box(
        Modifier
            .offset {
                fotograma.longValue
                IntOffset(
                    (satelite.x - anchoCaja.toPx() / 2).roundToInt(),
                    (satelite.y - radio.toPx()).roundToInt(),
                )
            }
            .size(anchoCaja, altoCaja)
            // Un solo nodo para TalkBack, con el nombre, en orden de urgencia.
            .clearAndSetSemantics {
                contentDescription = descripcion
                role = Role.Button
                traversalIndex = orden.toFloat()
                onClick(label = abrir) {
                    alToque()
                    true
                }
                customActions = acciones
            }
            .graphicsLayer {
                fotograma.longValue
                val s = satelite.escala * escala * (satelite.tam / satelite.radioBurbuja.coerceAtLeast(1f))
                scaleX = s
                scaleY = s
                // Sin alpha: la sombra saldria como un recuadro. Crece desde nada.
                transformOrigin = TransformOrigin(0.5f, radio.toPx() / altoCaja.toPx())
            },
    ) {
        if (toca) {
            val respiracion = rememberInfiniteTransition(label = "latidoOrbita")
            val pulso by respiracion.animateFloat(
                initialValue = 1f,
                targetValue = 1.16f,
                animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "pulsoOrbita",
            )
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .size(radio * 2)
                    .graphicsLayer {
                        scaleX = pulso
                        scaleY = pulso
                    }
                    .background(anillo.copy(alpha = 0.22f), CircleShape),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(radio * 2)
                .shadow(3.dp, CircleShape)
                .border(if (toca) 3.dp else 2.dp, anillo, CircleShape)
                .clip(CircleShape)
                .pointerInput(satelite) {
                    detectTapGestures(
                        onPress = {
                            pulsada = true
                            tryAwaitRelease()
                            pulsada = false
                        },
                        onTap = { alToque() },
                    )
                },
        ) {
            CaraBurbuja(animoPara(urgencia, atenuada = false), pulsada, false, contacto.id.toInt(), fotograma) {
                Offset(satelite.vx, satelite.vy)
            }
            foto?.let {
                Avatar(
                    contacto.nombre,
                    it,
                    radio * 0.7f,
                    inicial,
                    Modifier.align(Alignment.BottomEnd).offset(-radio * 0.12f, -radio * 0.12f),
                )
            }
        }
        if (conNombre) {
            Text(
                contacto.nombre,
                style = MaterialTheme.typography.labelMedium,
                color = esquema.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
    }
}
