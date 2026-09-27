package com.example.recuerdallamar

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Enlaces profundos de la app (contacto://...). Los usan el aviso, el widget
 * y el atajo del icono; Navigation los resuelve a su pantalla (ver Navegacion.kt).
 *
 * - contacto://personas — la pantalla principal
 * - contacto://ficha/{id} — la ficha de alguien
 * - contacto://nuevo — abre la agenda para anadir a alguien
 */
object Enlaces {
    const val ESQUEMA = "contacto"
    const val PERSONAS = "$ESQUEMA://personas"
    const val FICHA = "$ESQUEMA://ficha/{id}"
    const val NUEVO = "$ESQUEMA://nuevo"

    fun personas(context: Context): Intent = abrir(context, Uri.parse(PERSONAS))

    fun ficha(context: Context, id: Long): Intent = abrir(context, Uri.parse("$ESQUEMA://ficha/$id"))

    fun nuevo(context: Context): Intent = abrir(context, Uri.parse(NUEVO))

    /** Dirigido a MainActivity: ningun otro paquete puede recibirlo. */
    private fun abrir(context: Context, uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java).apply {
            // Desde el aviso o el widget no hay actividad de la que colgar; si
            // la app ya esta abierta, singleTop la reusa (onNewIntent).
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    /** El id de un contacto://ficha/{id}, o null si el enlace es otro. */
    fun idDeFicha(uri: Uri?): Long? =
        uri?.takeIf { it.scheme == ESQUEMA && it.host == "ficha" }?.lastPathSegment?.toLongOrNull()

    fun esNuevo(uri: Uri?): Boolean = uri?.scheme == ESQUEMA && uri.host == "nuevo"
}
