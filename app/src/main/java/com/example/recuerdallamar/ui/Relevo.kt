package com.example.recuerdallamar.ui

import androidx.compose.ui.geometry.Offset

/**
 * El paso de la bienvenida a la pantalla principal. Al terminar se saca una
 * foto de tu sistema (donde esta el sol y donde y hacia donde va cada planeta)
 * y la pantalla principal sigue desde ahi: el sol baja y crece hasta el
 * horizonte y los planetas se salen de su orbita y caen a su sitio entre las
 * burbujas. Todo en pixeles y coordenadas de la ventana.
 *
 * Cada parte se toma una sola vez: al volver a la lista mas tarde ya no hay
 * nada que continuar.
 */
class Relevo {
    class Sol(val centro: Offset, val radio: Float)
    class Planeta(val centro: Offset, val velocidad: Offset, val radio: Float)
    class Foto(val sol: Sol, val planetas: Map<Long, Planeta>)

    /** La pone quien dibuja el sistema de la bienvenida mientras se ve. */
    internal var fuente: (() -> Foto)? = null

    private var sol: Sol? = null
    private var planetas: Map<Long, Planeta>? = null

    fun sacarFoto() {
        val foto = fuente?.invoke() ?: return
        sol = foto.sol
        planetas = foto.planetas
    }

    internal fun tomarSol(): Sol? = sol.also { sol = null }

    internal fun tomarPlanetas(): Map<Long, Planeta>? = planetas.also { planetas = null }
}
