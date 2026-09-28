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
 * asi que aqui si hace falta READ_CONTACTS. Se pide una vez al arrancar, cada
 * vez que se anade a alguien mientras falte y desde la ficha; sin el no hay foto.
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
     * La foto si ya esta en la cache, sin ir a la agenda: para pintarla en el
     * primer fotograma de una pantalla que entra en vez de la inicial.
     */
    fun enCache(telefono: String, miniatura: Boolean = false): ImageBitmap? =
        if (telefono.isBlank()) null else cache.get("$miniatura|$telefono")

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
                val uri = Uri.parse(foto)
                // La foto grande de la agenda puede pasar de 1000 px y se pinta a
                // 124dp como mucho: se lee a menos resolucion, que pesa menos en
                // memoria y tarda menos en subir a la GPU al abrir la ficha.
                val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
                opciones.inSampleSize = muestreo(opciones.outWidth, opciones.outHeight)
                opciones.inJustDecodeBounds = false
                context.contentResolver.openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it, null, opciones) }
                    ?.asImageBitmap()
            }.getOrNull()
        }?.also { cache.put(clave, it) }
    }

    /** Lado maximo en pixeles: 124dp en la densidad mas alta. */
    private const val LADO_MAXIMO = 512

    /** Potencia de dos que deja el lado mas corto en LADO_MAXIMO o poco mas. */
    private fun muestreo(ancho: Int, alto: Int): Int {
        var n = 1
        while (minOf(ancho, alto) / (n * 2) >= LADO_MAXIMO) n *= 2
        return n
    }
}
