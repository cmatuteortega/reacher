package com.example.recuerdallamar

import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Registro de lo que pasa, con un area ("aviso", "copia"...) y datos clave=valor
 * en vez de frases sueltas. Va a Logcat y, como miga de pan, a Sentry: si luego
 * falla algo, el informe lleva lo que paso justo antes.
 *
 * Nunca nombres, telefonos ni notas: solo ids, contadores y enumerados. Sin
 * Sentry arrancado (sin DSN o con los informes apagados) las llamadas a Sentry
 * no hacen nada.
 */
object Registro {
    private const val ETIQUETA = "Contacto"

    fun info(area: String, mensaje: String, vararg datos: Pair<String, Any?>) {
        Log.i(ETIQUETA, linea(area, mensaje, datos))
        Sentry.addBreadcrumb(miga(area, mensaje, SentryLevel.INFO, datos))
    }

    /** Algo que no deberia pasar pero del que la app se recupera: se informa y se sigue. */
    fun fallo(area: String, error: Throwable, vararg datos: Pair<String, Any?>) {
        Log.w(ETIQUETA, linea(area, error.javaClass.simpleName, datos), error)
        Sentry.captureException(error) { alcance ->
            alcance.setTag("area", area)
            datos.forEach { (clave, valor) -> alcance.setExtra(clave, valor.toString()) }
        }
    }

    private fun linea(area: String, mensaje: String, datos: Array<out Pair<String, Any?>>): String =
        buildString {
            append('[').append(area).append("] ").append(mensaje)
            datos.forEach { (clave, valor) -> append(' ').append(clave).append('=').append(valor) }
        }

    private fun miga(area: String, mensaje: String, nivel: SentryLevel, datos: Array<out Pair<String, Any?>>) =
        Breadcrumb().apply {
            category = area
            message = mensaje
            level = nivel
            datos.forEach { (clave, valor) -> setData(clave, valor.toString()) }
        }
}
