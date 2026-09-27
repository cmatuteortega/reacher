package com.example.recuerdallamar.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.recuerdallamar.FotoContacto
import com.example.recuerdallamar.datos.Contacto
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * El sistema de la bienvenida: tu eres el sol, en el centro, y cada persona
 * que anades entra en tu orbita como un planeta con cara. El sol es una
 * esfera con ojos como las burbujas, pero su silueta tiene puntas onduladas
 * que giran despacio. Los planetas salen del sol al anadirlos y van a su
 * anillo; los anillos de dentro giran mas deprisa, como en un sistema de verdad.
 */

/** Ondas del borde del sol, y cuanto sobresalen (en radios del cuerpo). */
private const val PUNTAS = 9
private const val HONDURA = 0.2f

/** Giro de las puntas y del halo, en radianes por segundo. */
private const val GIRO_PUNTAS = 0.35f
private const val GIRO_HALO = -0.22f

/** El sol, en parte del lado menor del lienzo: solo o con gente alrededor. */
private const val SOL_SOLO = 0.2f
private const val SOL_CON_GENTE = 0.12f

/** Radianes por segundo del anillo de dentro; los de fuera van mas despacio. */
private const val VUELTA = 0.32f

private const val ANILLOS_MAX = 3

/** Grados que gira la cara de un planeta hacia donde va, y del sol hacia el recien llegado. */
private const val MIRA_PLANETA = 14f
private const val MIRA_SOL = 26f

/** Milisegundos que el sol se queda mirando a quien acaba de llegar. */
private const val MIRA_NUEVO_MS = 2600L

private val HUECO_ANILLO = 10.dp
private val ALTO_NOMBRE = 16.dp
private val ANCHO_NOMBRE = 72.dp

/** Tamano de los planetas segun cuantos hay: con mucha gente, mas pequenos. */
private fun radioPlaneta(cuantos: Int): Dp = when {
    cuantos <= 5 -> 24.dp
    cuantos <= 10 -> 20.dp
    else -> 16.dp
}

/** Un planeta en movimiento. Posicion relativa al centro del sistema, en pixeles. */
internal class Planeta(val id: Long) {
    var anillo = 0
    var puesto = 0
    var deCuantos = 1
    var angulo = 0f
    var radio = 0f
    var escala = 0f
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
}

/**
 * Las cuentas del sistema, sin Compose: a que anillo va cada uno y como se
 * mueve hacia su puesto. Los cambios (alguien nuevo, un anillo mas) no dan
 * saltos: angulo, radio y tamano se acercan poco a poco a lo que les toca.
 */
internal class Orbitas {
    val planetas = LinkedHashMap<Long, Planeta>()
    val radios = FloatArray(ANILLOS_MAX)
    val radiosVistos = FloatArray(ANILLOS_MAX)
    val opacidad = FloatArray(ANILLOS_MAX)
    private val giro = FloatArray(ANILLOS_MAX) { it * 0.9f }
    var anillos = 0
        private set

    /** Quien llego el ultimo y cuando (ms del reloj de fotogramas), para que el sol lo mire. */
    var ultimo: Long? = null
        private set
    var ultimoEn = 0L
        private set
    var ahoraMs = 0L
        private set

    fun colocar(ids: List<Long>, radiosAnillo: List<Float>, reparto: List<Int>, nacerEn: Float) {
        anillos = radiosAnillo.size
        for (k in 0 until ANILLOS_MAX) {
            radios[k] = radiosAnillo.getOrElse(k) { radiosAnillo.lastOrNull() ?: 0f }
            if (radiosVistos[k] == 0f) radiosVistos[k] = radios[k]
        }
        val nuevos = LinkedHashMap<Long, Planeta>()
        var i = 0
        reparto.forEachIndexed { anillo, cuantos ->
            repeat(cuantos) { puesto ->
                val id = ids[i++]
                val p = planetas[id] ?: Planeta(id).also {
                    // Nace en el sol y sale hacia su anillo.
                    it.radio = nacerEn
                    it.angulo = objetivo(anillo, puesto, cuantos)
                    if (planetas.isNotEmpty() || ids.size == 1) {
                        ultimo = id
                        ultimoEn = ahoraMs
                    }
                }
                p.anillo = anillo
                p.puesto = puesto
                p.deCuantos = cuantos
                nuevos[id] = p
            }
        }
        planetas.clear()
        planetas.putAll(nuevos)
    }

    private fun velocidad(anillo: Int) = VUELTA / (1f + anillo * 0.55f)

    private fun objetivo(anillo: Int, puesto: Int, cuantos: Int) =
        giro[anillo] + 2f * PI.toFloat() * puesto / cuantos.coerceAtLeast(1)

    fun paso(dt: Float, ms: Long) {
        ahoraMs = ms
        val suave = 1f - exp(-2.2f * dt)
        val crecer = 1f - exp(-4f * dt)
        for (k in 0 until ANILLOS_MAX) {
            giro[k] = (giro[k] + velocidad(k) * dt) % (2f * PI.toFloat())
            radiosVistos[k] += (radios[k] - radiosVistos[k]) * suave
            val visible = if (k < anillos) 1f else 0f
            opacidad[k] += (visible - opacidad[k]) * crecer
        }
        for (p in planetas.values) {
            p.angulo += velocidad(p.anillo) * dt
            var diferencia = (objetivo(p.anillo, p.puesto, p.deCuantos) - p.angulo) % (2f * PI.toFloat())
            if (diferencia > PI) diferencia -= 2f * PI.toFloat()
            if (diferencia < -PI) diferencia += 2f * PI.toFloat()
            p.angulo += diferencia * suave
            p.radio += (radios[p.anillo] - p.radio) * suave
            p.escala += (1f - p.escala) * crecer
            val x = cos(p.angulo) * p.radio
            val y = sin(p.angulo) * p.radio
            if (dt > 0f) {
                p.vx = (x - p.x) / dt
                p.vy = (y - p.y) / dt
            }
            p.x = x
            p.y = y
        }
    }
}

/**
 * Tu sistema: el sol en el centro y [contactos] girando alrededor. Un toque
 * en un planeta llama a [onAbrir]; en el sol, se rie. [solGrande] lo pone en
 * primer plano, para cuando aun no hay nadie a quien mirar.
 */
@Composable
fun SistemaSolar(
    contactos: List<Contacto>,
    fotosPermitidas: Boolean,
    solGrande: Boolean,
    onAbrir: (Contacto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val densidad = LocalDensity.current
    val orbitas = remember { Orbitas() }
    val fotograma = remember { mutableLongStateOf(0L) }
    LaunchedEffect(orbitas) {
        var antes = 0L
        while (true) {
            withFrameNanos { ahora ->
                val dt = if (antes == 0L) 0f else ((ahora - antes) / 1e9f).coerceAtMost(1f / 30f)
                orbitas.paso(dt, ahora / 1_000_000L)
                antes = ahora
                fotograma.longValue = ahora
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val lado = min(constraints.maxWidth, constraints.maxHeight).toFloat()
        val cuantos = contactos.size
        val planetaDp = radioPlaneta(cuantos)
        val conNombre = cuantos <= 10

        val fraccionSol = if (solGrande || cuantos == 0) SOL_SOLO else SOL_CON_GENTE
        val fraccionVista by animateFloatAsState(
            fraccionSol,
            spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessVeryLow),
            label = "sol",
        )
        val radioSol = with(densidad) { (lado * fraccionVista).toDp() }

        val ids = contactos.map { it.id }
        // Como en la vista de burbujas: al cambiar la gente o el tamano se recolocan.
        remember(ids, lado, fraccionSol, densidad) {
            with(densidad) {
                val planeta = planetaDp.toPx()
                val nombre = if (conNombre) ALTO_NOMBRE.toPx() else 0f
                val cuerpoSol = lado * fraccionSol
                val exterior = max(lado / 2 - planeta - nombre, cuerpoSol)
                val interior = min(cuerpoSol * (1f + HONDURA) + planeta + HUECO_ANILLO.toPx(), exterior)
                val (radiosAnillo, reparto) = repartir(cuantos, interior, exterior, planeta * 2 + if (conNombre) 26.dp.toPx() else 10.dp.toPx())
                radiosAnillo.also { orbitas.colocar(ids, it, reparto, cuerpoSol * 0.6f) }
            }
        }

        val esquema = MaterialTheme.colorScheme
        Canvas(Modifier.fillMaxSize()) {
            fotograma.longValue
            val trazo = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())))
            for (k in 0 until ANILLOS_MAX) {
                val alfa = orbitas.opacidad[k]
                if (alfa < 0.01f) continue
                drawCircle(esquema.outlineVariant.copy(alpha = alfa), orbitas.radiosVistos[k], center, style = trazo)
            }
        }

        Sol(radio = radioSol, orbitas = orbitas, cuantos = cuantos)

        val porId = contactos.associateBy { it.id }
        orbitas.planetas.keys.forEach { id ->
            val contacto = porId[id] ?: return@forEach
            val planeta = orbitas.planetas[id] ?: return@forEach
            key(id) {
                PlanetaVista(
                    contacto = contacto,
                    planeta = planeta,
                    radio = planetaDp,
                    conNombre = conNombre,
                    fotosPermitidas = fotosPermitidas,
                    fotograma = fotograma,
                    onToque = { onAbrir(contacto) },
                )
            }
        }
    }
}

/**
 * Cuantos anillos, a que radio y cuantos van en cada uno. Se llenan de
 * dentro afuera; con los que no quepan, el de fuera se aprieta.
 */
private fun repartir(cuantos: Int, interior: Float, exterior: Float, hueco: Float): Pair<List<Float>, List<Int>> {
    if (cuantos == 0) return emptyList<Float>() to emptyList()
    for (n in 1..ANILLOS_MAX) {
        val radios = List(n) { k ->
            if (n == 1) interior + (exterior - interior) * 0.4f else interior + (exterior - interior) * k / (n - 1)
        }
        val capacidades = radios.map { max(3, floor(2f * PI.toFloat() * it / hueco).toInt()) }
        if (capacidades.sum() >= cuantos || n == ANILLOS_MAX) {
            val reparto = MutableList(n) { 0 }
            var quedan = cuantos
            for (k in 0 until n) {
                val aqui = if (k == n - 1) quedan else min(quedan, capacidades[k])
                reparto[k] = aqui
                quedan -= aqui
            }
            // Sin anillos vacios: el de fuera se queda con los que haya.
            val usados = reparto.indexOfLast { it > 0 } + 1
            return radios.take(usados) to reparto.take(usados)
        }
    }
    return emptyList<Float>() to emptyList()
}

/**
 * Tu. Una esfera con ojos como las burbujas, con puntas onduladas que giran
 * y un halo que va al reves. Saluda al aparecer, mira a quien llega a su
 * orbita, cierra los ojos al apretarla y se rie al tocarla.
 */
@Composable
private fun Sol(radio: Dp, orbitas: Orbitas, cuantos: Int) {
    val cara = rememberEstadoCara(Expresiones.contenta)
    var pulsada by remember { mutableStateOf(false) }
    val volverA = Animaciones.mirarAlrededor
    LaunchedEffect(cara) { cara.reproducir(Animaciones.saludo) { cara.reproducir(volverA) } }
    // Cuando llega alguien, se alegra.
    var vistos by remember { mutableIntStateOf(cuantos) }
    LaunchedEffect(cuantos) {
        if (cuantos > vistos) cara.reproducir(Animaciones.saludo) { cara.reproducir(volverA) }
        vistos = cuantos
    }
    LaunchedEffect(pulsada) {
        when {
            pulsada -> cara.poner(Expresiones.apretada, 100, Transicion.SECA)
            // Soltada sin toque (se fue el dedo): vuelve a lo suyo.
            cara.animacion == null -> cara.reproducir(volverA)
        }
    }
    val escala by animateFloatAsState(
        if (pulsada) 0.92f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "escalaSol",
    )
    val esquema = MaterialTheme.colorScheme
    val caja = radio * 2 * (1f + HONDURA * 1.6f)
    Canvas(
        Modifier
            .size(caja)
            .graphicsLayer {
                scaleX = escala
                scaleY = escala
            }
            .semantics { contentDescription = "Tú, el sol de tu sistema" }
            .pointerInput(cara) {
                detectTapGestures(
                    onPress = {
                        pulsada = true
                        tryAwaitRelease()
                        pulsada = false
                    },
                    onTap = { cara.reproducir(Animaciones.risa) { cara.reproducir(volverA) } },
                )
            },
    ) {
        val t = cara.ahora / 1000f
        val r = radio.toPx()
        // Respira: las puntas crecen y menguan un poco.
        val hondura = HONDURA * (1f + 0.12f * sin(t * 1.7f))
        // El halo brilla hacia fuera: mas vivo junto al cuerpo y se apaga en las puntas.
        val halo = Brush.radialGradient(
            0.7f to esquema.tertiary.copy(alpha = 0.55f),
            1f to esquema.tertiary.copy(alpha = 0.08f),
            center = center,
            radius = r * 1.06f * (1f + hondura * 1.35f),
        )
        drawPath(ondas(center, r * 1.06f, hondura * 1.35f, PUNTAS, t * GIRO_HALO + 0.35f), halo)
        drawPath(ondas(center, r, hondura, PUNTAS, t * GIRO_PUNTAS), esquema.tertiary)

        // Mira un rato hacia quien acaba de llegar.
        val nuevo = orbitas.ultimo?.let { orbitas.planetas[it] }
        val mirando = orbitas.ahoraMs - orbitas.ultimoEn < MIRA_NUEVO_MS
        val giro = if (nuevo != null && mirando) {
            val d = hypot(nuevo.x, nuevo.y).coerceAtLeast(1f)
            Offset(nuevo.x / d * MIRA_SOL, nuevo.y / d * MIRA_SOL)
        } else {
            Offset.Zero
        }
        dibujarOjos(cara.muestra(), cara.parpadeo(), center, r, esquema.onTertiary, giro)
    }
}

/**
 * Silueta del sol: un circulo de [radio] con [puntas] ondas que sobresalen
 * hasta [hondura] radios. Las ondas son redondeadas, con valles anchos, y
 * [fase] las hace girar.
 */
private fun ondas(centro: Offset, radio: Float, hondura: Float, puntas: Int, fase: Float): Path {
    val trazo = Path()
    val muestras = 180
    for (i in 0..muestras) {
        val th = 2f * PI.toFloat() * i / muestras
        val onda = (0.5f + 0.5f * cos(puntas * th - fase)).pow(1.8f)
        val r = radio * (1f + hondura * onda)
        val x = centro.x + r * cos(th)
        val y = centro.y + r * sin(th)
        if (i == 0) trazo.moveTo(x, y) else trazo.lineTo(x, y)
    }
    trazo.close()
    return trazo
}

/** Una persona en orbita: burbuja con cara que mira hacia donde va, y su nombre debajo. */
@Composable
private fun PlanetaVista(
    contacto: Contacto,
    planeta: Planeta,
    radio: Dp,
    conNombre: Boolean,
    fotosPermitidas: Boolean,
    fotograma: MutableLongState,
    onToque: () -> Unit,
) {
    val alToque by rememberUpdatedState(onToque)
    val context = LocalContext.current
    var foto by remember(contacto.telefono) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(contacto.telefono, fotosPermitidas) {
        foto = FotoContacto.cargar(context, contacto.telefono, miniatura = true)
    }
    val semilla = contacto.id.toInt()
    val cara = rememberEstadoCara(Expresiones.contenta, semilla = semilla)
    var pulsada by remember { mutableStateOf(false) }
    LaunchedEffect(pulsada) {
        if (pulsada) {
            cara.poner(Expresiones.apretada, 100, Transicion.SECA)
        } else {
            val reposo = Animaciones.mirarAlrededor
            cara.reproducir(Animaciones.saludo) {
                cara.reproducir(reposo, empezarEn = semilla.mod(reposo.pasos.size))
            }
        }
    }
    val escala by animateFloatAsState(
        if (pulsada) 0.9f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "escalaPlaneta",
    )
    val esquema = MaterialTheme.colorScheme
    val ancho = if (conNombre) max(ANCHO_NOMBRE.value, radio.value * 2).dp else radio * 2
    val alto = radio * 2 + if (conNombre) ALTO_NOMBRE else 0.dp
    Box(
        Modifier
            .offset {
                fotograma.longValue
                // La caja ya esta centrada: se mueve para que el circulo quede sobre su orbita.
                IntOffset(planeta.x.roundToInt(), (planeta.y + alto.toPx() / 2 - radio.toPx()).roundToInt())
            }
            .size(ancho, alto)
            .graphicsLayer {
                fotograma.longValue
                val s = planeta.escala * escala
                scaleX = s
                scaleY = s
                alpha = planeta.escala.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0.5f, radio.toPx() / alto.toPx())
            },
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(radio * 2)
                .shadow(3.dp, CircleShape)
                .border(2.dp, esquema.outlineVariant, CircleShape)
                .clip(CircleShape)
                .background(esquema.primaryContainer)
                .cara(cara, esquema.onPrimaryContainer) {
                    fotograma.longValue
                    val v = hypot(planeta.vx, planeta.vy)
                    if (v < 1f) Offset.Zero else Offset(planeta.vx / v * MIRA_PLANETA, planeta.vy / v * MIRA_PLANETA)
                }
                .semantics {
                    contentDescription = contacto.nombre
                    role = Role.Button
                    onClick {
                        alToque()
                        true
                    }
                }
                .pointerInput(Unit) {
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
            foto?.let {
                Avatar(
                    contacto.nombre,
                    it,
                    radio * 0.8f,
                    MaterialTheme.typography.labelSmall,
                    Modifier.align(Alignment.BottomEnd),
                )
            }
        }
        if (conNombre) {
            Text(
                contacto.nombre.substringBefore(' '),
                style = MaterialTheme.typography.labelSmall,
                color = esquema.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
    }
}
