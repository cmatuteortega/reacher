package com.example.recuerdallamar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Foto de la agenda del contacto, buscada por numero: no se copia a la app,
 * asi que sirve tambien para los contactos ya guardados.
 *
 * La foto es otra fila del proveedor distinta de la que concede el selector,
 * asi que aqui si hace falta READ_CONTACTS. Se pide una vez al arrancar y,
 * si se deniega, desde la ficha; sin el simplemente no hay foto.
 */
object FotoContacto {

    private const val PREFERENCIAS = "fotos"
    private const val PREGUNTADO = "preguntado_al_arrancar"

    /** Por bytes: una foto grande pesa lo que muchas miniaturas. */
    private val cache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun permitida(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Al arrancar se pregunta solo la primera vez: repetirlo en cada apertura
     * tras un "no" seria pesado, y la ficha ya ofrece pedirlo otra vez.
     */
    fun preguntarAlArrancar(context: Context): Boolean =
        !permitida(context) &&
            !context.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE).getBoolean(PREGUNTADO, false)

    fun marcarPreguntado(context: Context) {
        context.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE).edit { putBoolean(PREGUNTADO, true) }
    }

    /**
     * null si no hay permiso, no se encuentra el numero o no tiene foto.
     * [miniatura] para la lista: la foto pequena de la agenda, sin decodificar
     * la grande para pintarla a 40dp.
     */
    suspend fun cargar(context: Context, telefono: String, miniatura: Boolean = false): ImageBitmap? {
        if (!permitida(context) || telefono.isBlank()) return null
        val clave = "$miniatura|$telefono"
        cache.get(clave)?.let { return it }
        return withContext(Dispatchers.IO) {
            // Un contacto sin foto, un numero que ya no esta en la agenda o un
            // proveedor raro no deben tumbar la pantalla.
            runCatching {
                val busqueda = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(telefono))
                // PHOTO_URI es la foto grande si la hay y si no la miniatura.
                val columna = if (miniatura) PhoneLookup.PHOTO_THUMBNAIL_URI else PhoneLookup.PHOTO_URI
                val foto = context.contentResolver
                    .query(busqueda, arrayOf(columna), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?: return@runCatching null
                context.contentResolver.openInputStream(Uri.parse(foto))
                    ?.use { BitmapFactory.decodeStream(it) }
                    ?.asImageBitmap()
            }.getOrNull()
        }?.also { cache.put(clave, it) }
    }
}
