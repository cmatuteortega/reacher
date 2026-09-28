package com.example.recuerdallamar.ui

import kotlinx.coroutines.channels.Channel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Las cuentas de la vista de burbujas, sin nada de Compose: que tamano tiene
 * cada una, donde descansa y como se mueve. Todo en pixeles.
 */

/** Urgencia a la que se llega al tamano maximo: un poco pasada la fecha. */
internal const val URGENCIA_TOPE = 1.25f

/**
 * Radio segun la parte de la cadencia consumida. La curva es mas plana al
 * principio: quien va sobrado se queda pequeno y crece deprisa al acercarse.
 */
internal fun radioPorUrgencia(urgencia: Float, minimo: Float, maximo: Float): Float {
    val t = (urgencia / URGENCIA_TOPE).coerceIn(0f, 1f)
    return minimo + (maximo - minimo) * t.pow(1.6f)
}

internal class Hueco(val clave: Long, val radio: Float)

internal class Sitio(val clave: Long, val x: Float, val y: Float, val radio: Float)

/**
 * Empaquetado voraz: cada circulo, en el orden dado, va al punto libre mas
 * cercano al foco. Asi los primeros (los mas urgentes) quedan en el centro y
 * el resto se reparte alrededor y hacia abajo. Los candidatos son los puntos
 * tangentes a lo ya colocado y a las paredes laterales.
 *
 * [margen] se suma al radio de cada uno: aire para el nombre y para el dedo.
 * [fijos] son circulos que ya ocupan sitio (el sol de la esquina): nadie se
 * coloca encima, pero se puede arrimar a ellos.
 * No hay pared de abajo: si no caben, el lienzo crece y se desplaza.
 */
internal fun empaquetar(
    huecos: List<Hueco>,
    ancho: Float,
    margen: Float,
    borde: Float,
    focoX: Float,
    focoY: Float,
    fijos: List<Sitio> = emptyList(),
): List<Sitio> {
    val puestos = ArrayList<Sitio>(huecos.size)
    for (hueco in huecos) {
        val alcance = hueco.radio + margen
        val minX = alcance + borde
        val maxX = max(minX, ancho - alcance - borde)
        val minY = alcance + borde

        // Pesa mas alejarse en vertical: llena el ancho antes de bajar.
        fun coste(x: Float, y: Float): Float {
            val dx = x - focoX
            val dy = (y - focoY) * 1.35f
            return dx * dx + dy * dy
        }

        fun libre(x: Float, y: Float): Boolean {
            if (x < minX - 0.5f || x > maxX + 0.5f || y < minY - 0.5f) return false
            val fuera = fijos.all { f ->
                val minima = f.radio + alcance - 0.5f
                val dx = f.x - x
                val dy = f.y - y
                dx * dx + dy * dy >= minima * minima
            }
            return fuera && puestos.all { p ->
                val minima = p.radio + margen + alcance - 0.5f
                val dx = p.x - x
                val dy = p.y - y
                dx * dx + dy * dy >= minima * minima
            }
        }

        var mejorX = focoX.coerceIn(minX, maxX)
        var mejorY = max(focoY, minY)
        var mejor = if (libre(mejorX, mejorY)) coste(mejorX, mejorY) else Float.MAX_VALUE

        fun probar(x: Float, y: Float) {
            val c = coste(x, y)
            if (c < mejor && libre(x, y)) {
                mejor = c
                mejorX = x
                mejorY = y
            }
        }

        for (f in fijos) {
            val distancia = f.radio + alcance
            for (k in 0 until ANGULOS) {
                val angulo = (2 * PI * k / ANGULOS).toFloat()
                probar(f.x + distancia * cos(angulo), f.y + distancia * sin(angulo))
            }
        }
        for (p in puestos) {
            val distancia = p.radio + margen + alcance
            for (k in 0 until ANGULOS) {
                val angulo = (2 * PI * k / ANGULOS).toFloat()
                probar(p.x + distancia * cos(angulo), p.y + distancia * sin(angulo))
            }
            // Pegado a una pared y tocando a este.
            for (pared in floatArrayOf(minX, maxX)) {
                val dx = pared - p.x
                if (abs(dx) <= distancia) {
                    val dy = sqrt(distancia * distancia - dx * dx)
                    probar(pared, p.y + dy)
                    probar(pared, p.y - dy)
                }
            }
        }

        if (mejor == Float.MAX_VALUE) {
            // Ningun hueco junto a nadie (no deberia pasar): debajo de todo.
            mejorX = focoX.coerceIn(minX, maxX)
            mejorY = (puestos.maxOfOrNull { it.y + it.radio + margen } ?: 0f) + alcance
        }
        puestos += Sitio(hueco.clave, mejorX, mejorY, hueco.radio)
    }
    return puestos
}

private const val ANGULOS = 24

/** Una burbuja en movimiento. [casaX]/[casaY] es donde descansa. */
internal class Cuerpo(val clave: Long, var x: Float, var y: Float, var radio: Float) {
    var vx = 0f
    var vy = 0f
    var casaX = x
    var casaY = y
    var radioObjetivo = radio
    var agarrado = false
    var soltadoEn = -100f

    // Cada una flota a su aire: fase y ritmo propios, sacados de la clave.
    val fase = ((clave * 2654435761L) and 0xFFFF).toFloat() / 0xFFFF * 2 * PI.toFloat()
    val ritmo = 0.35f + ((clave * 40503L) and 0xFF).toFloat() / 0xFF * 0.25f
}

/**
 * Muelles hacia casa, choques entre burbujas y paredes. Quien esta en el
 * dedo no cede: empuja a las demas. Al soltarla, el muelle arranca flojo y
 * se endurece poco a poco, para que primero se vea la inercia del lanzamiento
 * y luego vuelva sola, con un pequeno rebote, a su sitio.
 */
internal class Simulacion {
    val cuerpos = LinkedHashMap<Long, Cuerpo>()
    private var orden: Array<Cuerpo> = emptyArray()

    var densidad = 1f
    var ancho = 0f
    var alto = 0f
    var margen = 0f
    var borde = 0f
    var bordeInferior = 0f

    /** Circulos fijos que las burbujas no pueden pisar (el sol de la esquina). */
    var obstaculos: List<Sitio> = emptyList()

    /** Movimiento reducido: el reposo es quieto y las nuevas nacen ya en su sitio. */
    var calma = false
        set(valor) {
            if (field != valor) despertar()
            field = valor
        }
    var tiempo = 0f
        private set

    /**
     * El choque mas fuerte del ultimo paso: la velocidad (px/s) con que se
     * acercaban dos burbujas, o una y el borde. Para la vibracion.
     */
    var golpe = 0f
        private set

    /**
     * Avisa de que algo se mueve otra vez (casas nuevas, un dedo, un
     * lanzamiento) a quien dejo de pedir fotogramas porque estaba todo en
     * [reposo]. Solo guarda un aviso: varios seguidos valen por uno.
     */
    val despertador = Channel<Unit>(Channel.CONFLATED)

    private fun despertar() {
        despertador.trySend(Unit)
    }

    /**
     * Todas quietas en su casa, con su tamano y sin nadie en el dedo: otro
     * paso no cambiaria nada, asi que no hace falta pedir mas fotogramas hasta
     * que llegue un aviso al [despertador]. Solo con [calma]: si no, el
     * reposo flota y nunca esta quieto.
     */
    fun reposo(): Boolean {
        if (!calma) return false
        val quieto = QUIETO * densidad
        return orden.all { c ->
            !c.agarrado &&
                abs(c.vx) < quieto && abs(c.vy) < quieto &&
                abs(c.x - c.casaX) < CERCA_DE_CASA && abs(c.y - c.casaY) < CERCA_DE_CASA &&
                abs(c.radio - c.radioObjetivo) < CERCA_DE_CASA
        }
    }

    /**
     * Pone las casas nuevas. Las burbujas que ya estaban van nadando a su
     * sitio; las nuevas nacen un poco mas abajo y suben, o salen de la
     * burbuja indicada en [salenDe] si esta existe (el grupo al abrirse).
     */
    fun colocar(sitios: List<Sitio>, salenDe: Map<Long, Long> = emptyMap()) {
        val nuevos = LinkedHashMap<Long, Cuerpo>()
        for (sitio in sitios) {
            val cuerpo = cuerpos[sitio.clave] ?: run {
                val origen = salenDe[sitio.clave]?.let { cuerpos[it] }
                if (origen != null) {
                    Cuerpo(sitio.clave, origen.x, origen.y, sitio.radio)
                } else {
                    Cuerpo(sitio.clave, sitio.x, sitio.y + if (calma) 0f else NACER_DESDE * densidad, sitio.radio)
                }
            }
            cuerpo.casaX = sitio.x
            cuerpo.casaY = sitio.y
            cuerpo.radioObjetivo = sitio.radio
            nuevos[sitio.clave] = cuerpo
        }
        cuerpos.clear()
        cuerpos.putAll(nuevos)
        orden = nuevos.values.toTypedArray()
        despertar()
    }

    /**
     * Llega de fuera (un planeta de la bienvenida): aparece en [x],[y] con su
     * tamano de antes y su impulso, como recien lanzada, asi que primero sigue
     * de largo y luego el muelle la lleva a casa.
     */
    fun recibir(cuerpo: Cuerpo, x: Float, y: Float, vx: Float, vy: Float, radio: Float) {
        cuerpo.x = x
        cuerpo.y = y
        if (radio > 0f) cuerpo.radio = radio
        soltar(cuerpo, vx, vy)
    }

    fun agarrar(cuerpo: Cuerpo) {
        cuerpo.agarrado = true
        cuerpo.vx = 0f
        cuerpo.vy = 0f
        despertar()
    }

    /** Sigue al dedo. */
    fun mover(cuerpo: Cuerpo, dx: Float, dy: Float) {
        cuerpo.x += dx
        cuerpo.y += dy
        contenerEnParedes(cuerpo)
    }

    /** La velocidad del dedo: con ella empuja a las que choca mientras la lleva. */
    fun arrastrarA(cuerpo: Cuerpo, vx: Float, vy: Float) {
        cuerpo.vx = vx
        cuerpo.vy = vy
    }

    fun soltar(cuerpo: Cuerpo, vx: Float, vy: Float) {
        val maxima = VELOCIDAD_MAXIMA * densidad
        val v = sqrt(vx * vx + vy * vy)
        val factor = if (v > maxima) maxima / v else 1f
        cuerpo.vx = vx * factor
        cuerpo.vy = vy * factor
        cuerpo.agarrado = false
        cuerpo.soltadoEn = tiempo
        despertar()
    }

    fun paso(dt: Float) {
        tiempo += dt
        golpe = 0f
        val deriva = if (calma) 0f else DERIVA * densidad
        val crecer = 1f - exp(-8f * dt)
        for (c in orden) {
            c.radio += (c.radioObjetivo - c.radio) * crecer
            if (c.agarrado) continue
            val rigidez = RIGIDEZ * ((tiempo - c.soltadoEn) / ENDURECER).coerceIn(0.06f, 1f)
            val amortiguacion = max(2f * sqrt(rigidez) * AMORTIGUACION, FRICCION)
            // El reposo no es quieto: la casa se mece un poco, como algo que flota.
            val objetivoX = c.casaX + deriva * sin(tiempo * c.ritmo + c.fase)
            val objetivoY = c.casaY + deriva * cos(tiempo * c.ritmo * 0.8f + c.fase * 1.3f)
            c.vx += (rigidez * (objetivoX - c.x) - amortiguacion * c.vx) * dt
            c.vy += (rigidez * (objetivoY - c.y) - amortiguacion * c.vy) * dt
            c.x += c.vx * dt
            c.y += c.vy * dt
        }
        repeat(2) { separar() }
        for (c in orden) {
            apartarDeObstaculos(c)
            contenerEnParedes(c)
        }
    }

    private fun separar() {
        for (i in orden.indices) {
            val a = orden[i]
            for (j in i + 1 until orden.size) {
                val b = orden[j]
                if (a.agarrado && b.agarrado) continue
                val minima = a.radio + b.radio + 2 * margen
                val dx = b.x - a.x
                val dy = b.y - a.y
                val d2 = dx * dx + dy * dy
                if (d2 >= minima * minima) continue
                val d = sqrt(d2)
                val nx: Float
                val ny: Float
                if (d > 0.01f) {
                    nx = dx / d
                    ny = dy / d
                } else {
                    nx = cos(a.fase)
                    ny = sin(a.fase)
                }
                val solape = minima - d
                val pa = when {
                    a.agarrado -> 0f
                    b.agarrado -> 1f
                    else -> 0.5f
                }
                val pb = 1f - pa
                a.x -= nx * solape * pa
                a.y -= ny * solape * pa
                b.x += nx * solape * pb
                b.y += ny * solape * pb
                // Choque blando: se quita la velocidad con que se acercan y un poco mas.
                val acercamiento = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
                if (acercamiento < 0f) {
                    golpe = max(golpe, -acercamiento)
                    val impulso = -(1f + REBOTE) * acercamiento
                    a.vx -= impulso * nx * pa
                    a.vy -= impulso * ny * pa
                    b.vx += impulso * nx * pb
                    b.vy += impulso * ny * pb
                }
            }
        }
    }

    /** Si pisa un obstaculo, sale hacia fuera y pierde la velocidad con que entraba. */
    private fun apartarDeObstaculos(c: Cuerpo) {
        if (c.agarrado) return
        for (o in obstaculos) {
            val minima = o.radio + c.radio
            val dx = c.x - o.x
            val dy = c.y - o.y
            val d2 = dx * dx + dy * dy
            if (d2 >= minima * minima) continue
            val d = sqrt(d2).coerceAtLeast(0.01f)
            val nx = dx / d
            val ny = dy / d
            c.x = o.x + nx * minima
            c.y = o.y + ny * minima
            val entrando = c.vx * nx + c.vy * ny
            if (entrando < 0f) {
                c.vx -= (1f + REBOTE_PARED) * entrando * nx
                c.vy -= (1f + REBOTE_PARED) * entrando * ny
            }
        }
    }

    private fun contenerEnParedes(c: Cuerpo) {
        val minX = c.radio + borde
        val maxX = max(minX, ancho - c.radio - borde)
        val minY = c.radio + borde
        val maxY = max(minY, alto - bordeInferior - c.radio)
        // Solo cuenta como golpe si no la lleva el dedo: arrastrarla contra el borde no vibra.
        val suelto = !c.agarrado
        if (c.x < minX) {
            c.x = minX
            if (c.vx < 0f) {
                if (suelto) golpe = max(golpe, -c.vx)
                c.vx = -c.vx * REBOTE_PARED
            }
        } else if (c.x > maxX) {
            c.x = maxX
            if (c.vx > 0f) {
                if (suelto) golpe = max(golpe, c.vx)
                c.vx = -c.vx * REBOTE_PARED
            }
        }
        if (c.y < minY) {
            c.y = minY
            if (c.vy < 0f) {
                if (suelto) golpe = max(golpe, -c.vy)
                c.vy = -c.vy * REBOTE_PARED
            }
        } else if (c.y > maxY) {
            c.y = maxY
            if (c.vy > 0f) {
                if (suelto) golpe = max(golpe, c.vy)
                c.vy = -c.vy * REBOTE_PARED
            }
        }
    }

    private companion object {
        /** Muelle hacia casa (1/s²): vuelve en algo mas de un segundo. */
        const val RIGIDEZ = 14f

        /** Menos de 1: un pequeno rebote al llegar, no un frenazo. */
        const val AMORTIGUACION = 0.6f

        /** Freno minimo mientras el muelle aun esta flojo tras soltarla. */
        const val FRICCION = 2.6f

        /** Segundos que tarda el muelle en recuperar toda su fuerza tras soltarla. */
        const val ENDURECER = 1.4f
        const val REBOTE = 0.3f
        const val REBOTE_PARED = 0.5f

        // En dp y dp/s; se pasan a pixeles con [densidad].
        const val DERIVA = 3f
        const val VELOCIDAD_MAXIMA = 1800f
        const val NACER_DESDE = 36f

        /** Por debajo de esto (dp/s) una burbuja ya no se mueve a la vista. */
        const val QUIETO = 1f

        /** Pixeles de su casa y de su tamano a los que ya cuenta como llegada. */
        const val CERCA_DE_CASA = 0.5f
    }
}
