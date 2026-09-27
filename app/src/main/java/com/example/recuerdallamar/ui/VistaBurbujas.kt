package com.example.recuerdallamar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.recuerdallamar.FotoContacto
import com.example.recuerdallamar.R
import com.example.recuerdallamar.datos.Contacto
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/** Clave de la burbuja que agrupa a los que van sobrados (los id de Room empiezan en 1). */
private const val GRUPO = -1L

/** A partir de cuantas personas se agrupan las que van sobradas. */
private const val MUCHOS = 18

/** Por debajo de esta urgencia alguien va sobrado: se atenua y, si hay muchos, se agrupa. */
private const val CALMA = 0.5f

/** No merece la pena un grupo para dos o tres. */
private const val MIN_AGRUPAR = 4

/** Por encima, la burbuja ya se acerca a su fecha y el anillo se colorea. */
internal const val CERCA = 0.75f

internal val RADIO_MIN = 26.dp // 52dp de diametro: sigue siendo facil de pulsar
internal val RADIO_MAX = 84.dp
private val MARGEN = 12.dp // aire alrededor de cada una, para el nombre y el dedo
private val BORDE = 8.dp
private val ANCHO_EXTRA_ETIQUETA = 24.dp
private val ALTO_ETIQUETA = 22.dp

/** Cuanto se estira una burbuja lanzada, y a que velocidad (dp/s) llega a ello. */
private const val ESTIRON = 0.14f
private const val VELOCIDAD_ESTIRON = 1400f

/** Velocidad (dp/s) a la que la cara ya mira del todo hacia donde va, y cuantos grados gira. */
private const val MIRA_A_VELOCIDAD = 600f
private const val GIRO_AL_CORRER = 40f

/**
 * Al llegar de la bienvenida, cuanto se aviva el impulso de la orbita al
 * soltarse de ella, y cuanto caen de mas (dp/s).
 */
private const val ESCAPE = 2.2f
private const val CAIDA = 160f

/** Parte del lienzo visible que se deja ocupar antes de encoger las grandes. */
private const val OCUPACION = 0.5f

/** Choques mas flojos que esto (dp/s) no vibran: el vaiven de reposo y los recolocados. */
private const val GOLPE_MINIMO = 280f

/** A partir de aqui (dp/s por encima del minimo) el choque vibra a toda fuerza. */
private const val GOLPE_PLENO = 1400f

/** Separacion minima entre dos vibraciones de choque, para que una cascada no zumbe. */
private const val ENTRE_GOLPES_NS = 70_000_000L

/** Soltarla mas deprisa que esto (dp/s) es lanzarla: se nota un golpe seco. */
private const val VELOCIDAD_LANZAR = 700f

/**
 * Personas como burbujas flotantes: mas grandes cuanto mas cerca estan de su
 * fecha, las mas urgentes en el centro. Se pueden arrastrar y lanzar; al
 * soltarlas vuelven solas a su sitio. Un toque abre la ficha.
 *
 * [contactos] llega ya ordenado por urgencia: es el orden en que se colocan.
 * Abajo se dejan libres [huecoInferior], para el +, y arriba a la izquierda
 * el sol: un circulo de [solRadio] con centro [solArriba] por encima del
 * lienzo, en su borde izquierdo. Si [relevo]
 * trae los planetas de la bienvenida, cada uno sale de su orbita, desde
 * donde estaba, y cae a su sitio.
 */
@Composable
fun VistaBurbujas(
    contactos: List<Contacto>,
    fotosPermitidas: Boolean,
    onAbrir: (Contacto) -> Unit,
    huecoInferior: Dp,
    solArriba: Dp,
    solRadio: Dp,
    modifier: Modifier = Modifier,
    relevo: Relevo? = null,
) {
    val densidad = LocalDensity.current
    var grupoAbierto by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val sim = remember { Simulacion() }
    val llegadas = remember { relevo?.tomarPlanetas()?.toMutableMap() }
    val llegan = remember { llegadas?.keys?.toSet().orEmpty() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val ancho = constraints.maxWidth.toFloat()
        val alto = constraints.maxHeight.toFloat()

        val hoy = remember(contactos) { LocalDate.now() }
        val urgencias = remember(contactos, hoy) { contactos.associate { it.id to it.urgencia(hoy) } }
        val muchos = contactos.size > MUCHOS
        val tranquilos = remember(contactos, urgencias) {
            if (muchos) contactos.filter { urgencias.getValue(it.id) < CALMA }.map { it.id }.toSet() else emptySet()
        }
        val agrupar = tranquilos.size >= MIN_AGRUPAR

        val sol = with(densidad) { Sitio(GRUPO, 0f, -solArriba.toPx(), solRadio.toPx()) }
        val plano = remember(contactos, urgencias, agrupar, grupoAbierto, ancho, alto, huecoInferior, sol.y, sol.radio, densidad) {
            val visibles = contactos.filter { !agrupar || grupoAbierto || it.id !in tranquilos }
            disponer(visibles.map { it.id to urgencias.getValue(it.id) }, agrupar, ancho, alto, huecoInferior, sol, densidad).also {
                sim.densidad = densidad.density
                sim.ancho = ancho
                sim.alto = it.altoMundo
                with(densidad) {
                    sim.margen = MARGEN.toPx()
                    sim.borde = BORDE.toPx()
                    sim.bordeInferior = huecoInferior.toPx()
                    sim.obstaculos = listOf(sol)
                }
                // Al abrir el grupo, los tranquilos salen de el.
                sim.colocar(it.sitios, if (agrupar) tranquilos.associateWith { GRUPO } else emptyMap())
            }
        }

        // Un contador por fotograma: las burbujas lo leen al colocarse y
        // pintarse, asi que se mueven sin recomponer nada.
        val fotograma = remember { mutableLongStateOf(0L) }
        val vista = LocalView.current
        LaunchedEffect(sim) {
            var antes = 0L
            var ultimoGolpe = 0L
            while (true) {
                withInfiniteAnimationFrameNanos { ahora ->
                    if (antes != 0L) sim.paso(((ahora - antes) / 1e9f).coerceAtMost(1f / 30f))
                    antes = ahora
                    fotograma.longValue = ahora
                    // Cada choque se siente, con mas fuerza cuanto mas deprisa iban.
                    val golpe = sim.golpe / densidad.density - GOLPE_MINIMO
                    if (golpe > 0f && ahora - ultimoGolpe > ENTRE_GOLPES_NS) {
                        ultimoGolpe = ahora
                        vista.choque(golpe / GOLPE_PLENO)
                    }
                }
            }
        }

        val porId = remember(contactos) { contactos.associateBy { it.id } }
        // Si hay mas de las que caben, el lienzo crece hacia abajo y se desplaza
        // arrastrando el fondo; arrastrar una burbuja la mueve a ella.
        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(densidad) { plano.altoMundo.toDp() })
                    .onGloballyPositioned { mundo ->
                        // Los planetas de la bienvenida, con su impulso, en coordenadas del lienzo.
                        if (llegadas.isNullOrEmpty()) return@onGloballyPositioned
                        val origen = mundo.positionInWindow()
                        val caida = CAIDA * densidad.density
                        llegadas.forEach { (id, p) ->
                            val cuerpo = sim.cuerpos[id] ?: return@forEach
                            val en = p.centro - origen
                            sim.recibir(cuerpo, en.x, en.y, p.velocidad.x * ESCAPE, p.velocidad.y * ESCAPE + caida, p.radio)
                        }
                        llegadas.clear()
                    },
            ) {
                plano.sitios.forEachIndexed { orden, sitio ->
                    val cuerpo = sim.cuerpos[sitio.clave]
                    val contacto = porId[sitio.clave]
                    key(sitio.clave) {
                        val radio = with(densidad) { sitio.radio.toDp() }
                        // colocar() ya ha creado un cuerpo por sitio.
                        if (cuerpo != null && sitio.clave == GRUPO) {
                            BurbujaGrupo(
                                cuantos = tranquilos.size,
                                abierto = grupoAbierto,
                                radio = radio,
                                cuerpo = cuerpo,
                                sim = sim,
                                fotograma = fotograma,
                                orden = orden,
                                onToque = { grupoAbierto = !grupoAbierto },
                            )
                        } else if (cuerpo != null && contacto != null) {
                            val urgencia = urgencias.getValue(contacto.id)
                            BurbujaPersona(
                                contacto = contacto,
                                urgencia = urgencia,
                                atenuada = muchos && urgencia < CALMA,
                                fotosPermitidas = fotosPermitidas,
                                radio = radio,
                                cuerpo = cuerpo,
                                sim = sim,
                                fotograma = fotograma,
                                orden = orden,
                                llegando = contacto.id in llegan,
                                onToque = { onAbrir(contacto) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private class Disposicion(val sitios: List<Sitio>, val altoMundo: Float)

/**
 * Tamanos y casas de todas. Con mucha gente las grandes encogen para que
 * quepan mas en pantalla, sin bajar nunca del minimo que se pulsa bien; lo
 * que aun asi no quepa queda mas abajo, desplazando.
 */
private fun disponer(
    personas: List<Pair<Long, Float>>,
    conGrupo: Boolean,
    ancho: Float,
    alto: Float,
    huecoInferior: Dp,
    sol: Sitio,
    densidad: Density,
): Disposicion = with(densidad) {
    val margen = MARGEN.toPx()
    val minimo = RADIO_MIN.toPx()
    var maximo = min(ancho * 0.2f, RADIO_MAX.toPx()).coerceAtLeast(minimo * 1.4f)

    fun huecos(maximo: Float) = buildList {
        personas.forEach { (id, urgencia) -> add(Hueco(id, radioPorUrgencia(urgencia, minimo, maximo))) }
        if (conGrupo) add(Hueco(GRUPO, minimo * 1.3f))
    }

    val libre = alto - huecoInferior.toPx()
    val disponible = ancho * libre * OCUPACION
    val ocupado = huecos(maximo).sumOf { val r = it.radio + margen; PI * r * r }.toFloat()
    if (ocupado > disponible) maximo = max(minimo * 1.4f, maximo * sqrt(disponible / ocupado))

    val sitios = empaquetar(huecos(maximo), ancho, margen, BORDE.toPx(), ancho / 2, libre * 0.46f, fijos = listOf(sol))
    val fondo = sitios.maxOfOrNull { it.y + it.radio + margen } ?: 0f
    Disposicion(sitios, max(alto, fondo + huecoInferior.toPx()))
}

@Composable
private fun BurbujaPersona(
    contacto: Contacto,
    urgencia: Float,
    atenuada: Boolean,
    fotosPermitidas: Boolean,
    radio: Dp,
    cuerpo: Cuerpo,
    sim: Simulacion,
    fotograma: MutableLongState,
    orden: Int,
    llegando: Boolean,
    onToque: () -> Unit,
) {
    val toca = urgencia >= 1f
    val esquema = MaterialTheme.colorScheme
    val anillo = when {
        toca -> esquema.tertiary
        urgencia >= CERCA -> esquema.primary
        else -> esquema.outlineVariant
    }
    // La foto grande solo para las burbujas grandes; la miniatura se veria borrosa.
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
    val animo = animoPara(urgencia, atenuada)
    Burbuja(
        cuerpo = cuerpo,
        sim = sim,
        fotograma = fotograma,
        radio = radio,
        orden = orden,
        llegando = llegando,
        etiqueta = contacto.nombre,
        descripcion = descripcion,
        atenuada = atenuada,
        anillo = anillo,
        latido = toca,
        onToque = onToque,
    ) { pulsada, agarrada ->
        CaraBurbuja(animo, pulsada, agarrada, cuerpo.clave.toInt(), fotograma) { Offset(cuerpo.vx, cuerpo.vy) }
        // La foto va en una chapita abajo a la derecha: la cara es la burbuja.
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
}

/** Como esta cada burbuja segun su plazo. */
internal enum class Animo(val animacion: Animacion) {
    DORMIDA(Animaciones.durmiendo),
    TRANQUILA(Animaciones.mirarAlrededor),
    ATENTA(Animaciones.atenta),
    ILUSIONADA(Animaciones.ilusionada),
    IMPACIENTE(Animaciones.impaciente),
}

/** El animo que toca con esa [urgencia]; [atenuada] si va sobrada entre mucha gente. */
internal fun animoPara(urgencia: Float, atenuada: Boolean): Animo = when {
    urgencia >= URGENCIA_TOPE -> Animo.IMPACIENTE
    urgencia >= 1f -> Animo.ILUSIONADA
    atenuada -> Animo.DORMIDA
    urgencia >= CERCA -> Animo.ATENTA
    else -> Animo.TRANQUILA
}

/**
 * Una esfera con ojos: su animo segun el plazo, cierra los ojos de gusto al
 * apretarla, se asusta al cogerla y gira la cabeza hacia donde la lanzas.
 */
@Composable
internal fun CaraBurbuja(
    animo: Animo,
    pulsada: Boolean,
    agarrada: Boolean,
    semilla: Int,
    fotograma: MutableLongState,
    velocidad: () -> Offset,
) {
    val cara = rememberEstadoCara(animo.animacion.pasos.first().expresion, semilla = semilla)
    LaunchedEffect(animo, pulsada, agarrada) {
        when {
            agarrada -> cara.poner(Expresiones.sorpresa, 140, Transicion.SECA)
            pulsada -> cara.poner(Expresiones.apretada, 100, Transicion.SECA)
            // Cada una empieza por un paso distinto para que no vayan al unisono.
            else -> cara.reproducir(animo.animacion, empezarEn = semilla.mod(animo.animacion.pasos.size))
        }
    }
    val esquema = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(esquema.primaryContainer)
            .cara(cara, esquema.onPrimaryContainer) {
                fotograma.longValue
                val (vx, vy) = velocidad()
                val v = hypot(vx, vy)
                if (v < 1f) {
                    Offset.Zero
                } else {
                    val f = min(v / (MIRA_A_VELOCIDAD * density), 1f) * GIRO_AL_CORRER
                    Offset(vx / v * f, vy / v * f)
                }
            },
    )
}

@Composable
private fun BurbujaGrupo(
    cuantos: Int,
    abierto: Boolean,
    radio: Dp,
    cuerpo: Cuerpo,
    sim: Simulacion,
    fotograma: MutableLongState,
    orden: Int,
    onToque: () -> Unit,
) {
    Burbuja(
        cuerpo = cuerpo,
        sim = sim,
        fotograma = fotograma,
        radio = radio,
        orden = orden,
        etiqueta = stringResource(if (abierto) R.string.recoger else R.string.con_calma),
        descripcion = pluralStringResource(
            if (abierto) R.plurals.con_calma_recoger else R.plurals.con_calma_mostrar,
            cuantos,
            cuantos,
        ),
        atenuada = false,
        anillo = MaterialTheme.colorScheme.outlineVariant,
        latido = false,
        onToque = onToque,
    ) { _, _ ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Text(
                if (abierto) "–" else "+$cuantos",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Lo comun a todas: posicion tomada de la simulacion, aparicion con muelle,
 * se hunde un poco al pulsar y se levanta al arrastrar. El nombre va debajo.
 */
@Composable
private fun Burbuja(
    cuerpo: Cuerpo,
    sim: Simulacion,
    fotograma: MutableLongState,
    radio: Dp,
    orden: Int,
    etiqueta: String,
    llegando: Boolean = false,
    descripcion: String,
    atenuada: Boolean,
    anillo: Color,
    latido: Boolean,
    onToque: () -> Unit,
    contenido: @Composable BoxScope.(pulsada: Boolean, agarrada: Boolean) -> Unit,
) {
    val alToque by rememberUpdatedState(onToque)
    val vista = LocalView.current
    val anchoCaja = radio * 2 + ANCHO_EXTRA_ETIQUETA
    val altoCaja = radio * 2 + ALTO_ETIQUETA

    // Entran una tras otra, creciendo desde nada con un rebote. Las que vienen
    // de la orbita de la bienvenida ya estan a la vista.
    val aparicion = remember { Animatable(if (llegando) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay((orden * 30L).coerceAtMost(600L))
        aparicion.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }

    var pulsada by remember { mutableStateOf(false) }
    var agarrada by remember { mutableStateOf(false) }
    val escala by animateFloatAsState(
        targetValue = when {
            agarrada -> 1.1f
            pulsada -> 0.93f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "escala",
    )
    val sombra by animateDpAsState(if (agarrada) 14.dp else 3.dp, label = "sombra")
    // Escala total con que se ve: hace falta para que la burbuja siga al dedo exacta.
    fun escalaVista() = aparicion.value * escala * (cuerpo.radio / cuerpo.radioObjetivo)

    Box(
        Modifier
            .offset {
                fotograma.longValue
                IntOffset(
                    (cuerpo.x - anchoCaja.toPx() / 2).roundToInt(),
                    (cuerpo.y - radio.toPx()).roundToInt(),
                )
            }
            .zIndex(if (agarrada) 1f else 0f)
            .size(anchoCaja, altoCaja)
            .graphicsLayer {
                fotograma.longValue
                val s = escalaVista()
                scaleX = s
                scaleY = s
                alpha = aparicion.value.coerceIn(0f, 1f) * if (atenuada) 0.55f else 1f
                transformOrigin = TransformOrigin(0.5f, radio.toPx() / altoCaja.toPx())
            },
    ) {
        if (latido) {
            // A quien ya le toca respira: un halo que crece y se encoge despacio.
            val respiracion = rememberInfiniteTransition(label = "latido")
            val pulso by respiracion.animateFloat(
                initialValue = 1f,
                targetValue = 1.16f,
                animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "pulso",
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
                .graphicsLayer {
                    // Se estira en la direccion en que corre y se aplasta en la otra.
                    // Cogida no: el arrastre tiene que seguir al dedo exacto.
                    fotograma.longValue
                    if (!agarrada) {
                        val referencia = VELOCIDAD_ESTIRON * density
                        val d = ESTIRON * (min(abs(cuerpo.vx) / referencia, 1f) - min(abs(cuerpo.vy) / referencia, 1f))
                        scaleX = 1f + d
                        scaleY = 1f - d
                    }
                }
                .shadow(sombra, CircleShape)
                .border(if (latido) 3.dp else 2.dp, anillo, CircleShape)
                .clip(CircleShape)
                .semantics {
                    contentDescription = descripcion
                    role = Role.Button
                    onClick {
                        alToque()
                        true
                    }
                }
                .pointerInput(cuerpo) {
                    detectTapGestures(
                        onPress = {
                            pulsada = true
                            tryAwaitRelease()
                            pulsada = false
                        },
                        onTap = { alToque() },
                    )
                }
                .pointerInput(cuerpo) {
                    val rastreo = VelocityTracker()
                    detectDragGestures(
                        onDragStart = {
                            rastreo.resetTracking()
                            agarrada = true
                            vista.agarrar()
                            sim.agarrar(cuerpo)
                        },
                        onDragEnd = {
                            agarrada = false
                            val v = rastreo.calculateVelocity()
                            if (hypot(v.x, v.y) > VELOCIDAD_LANZAR * density) vista.lanzar()
                            sim.soltar(cuerpo, v.x, v.y)
                        },
                        onDragCancel = {
                            agarrada = false
                            sim.soltar(cuerpo, 0f, 0f)
                        },
                        onDrag = { cambio, arrastre ->
                            cambio.consume()
                            // El arrastre llega en coordenadas de la burbuja, que esta escalada.
                            val s = escalaVista().coerceAtLeast(0.1f)
                            sim.mover(cuerpo, arrastre.x * s, arrastre.y * s)
                            rastreo.addPosition(cambio.uptimeMillis, Offset(cuerpo.x, cuerpo.y))
                            val v = rastreo.calculateVelocity()
                            sim.arrastrarA(cuerpo, v.x, v.y)
                        },
                    )
                },
        ) {
            contenido(pulsada, agarrada)
        }
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        )
    }
}
