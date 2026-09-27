package com.example.recuerdallamar.ui

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

/**
 * El paso a la pantalla principal desde donde estaba el sol antes, para que
 * suba y crezca hasta la esquina sin cortes. Dos origenes:
 *
 * - La bienvenida: al terminar se saca una foto de tu sistema (donde esta el
 *   sol y donde y hacia donde va cada planeta) y la pantalla principal sigue
 *   desde ahi: el sol sube al horizonte y los planetas se salen de su orbita
 *   y caen a su sitio entre las burbujas.
 * - La pantalla de arranque: el sol del icono, en el centro, sube a la
 *   esquina cada vez que se abre la app. Mientras el arranque se ve, la
 *   pantalla principal ya esta debajo esperando; al irse dice donde estaba
 *   el icono de verdad.
 *
 * Todo en pixeles y coordenadas de la ventana. Cada parte se toma una sola
 * vez: al volver a la lista mas tarde ya no hay nada que continuar.
 */
class Relevo {
    class Sol(val centro: Offset, val radio: Float)
    class Planeta(val centro: Offset, val velocidad: Offset, val radio: Float)
    class Foto(val sol: Sol, val planetas: Map<Long, Planeta>)

    /** Un sol que espera a que se vaya la pantalla de arranque: donde se cree que esta y donde estaba al final. */
    class Arranque(val estimado: Sol, val listo: Deferred<Sol>)

    /** La pone quien dibuja el sistema de la bienvenida mientras se ve. */
    internal var fuente: (() -> Foto)? = null

    private var sol: Sol? = null
    private var planetas: Map<Long, Planeta>? = null
    private var arranque: Arranque? = null
    private var alIrseArranque: CompletableDeferred<Sol>? = null

    fun sacarFoto() {
        val foto = fuente?.invoke() ?: return
        sol = foto.sol
        planetas = foto.planetas
    }

    /** Al abrir la app: el sol saldra del icono de arranque, que se cree en [estimado]. */
    fun esperarArranque(estimado: Sol) {
        val listo = CompletableDeferred<Sol>()
        alIrseArranque = listo
        arranque = Arranque(estimado, listo)
    }

    /** Se va la pantalla de arranque: el icono estaba en [sol] (o donde se estimo, si null). */
    fun arranqueTerminado(sol: Sol?) {
        val listo = alIrseArranque ?: return
        alIrseArranque = null
        listo.complete(sol ?: arranque?.estimado ?: return)
    }

    internal fun tomarSol(): Sol? = sol.also { sol = null }

    internal fun tomarPlanetas(): Map<Long, Planeta>? = planetas.also { planetas = null }

    internal fun tomarArranque(): Arranque? = arranque.also { arranque = null }
}
