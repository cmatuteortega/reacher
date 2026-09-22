package com.example.recuerdallamar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Foto de la agenda del contacto, buscada por numero cada vez que se abre la
 * ficha: no se copia a la app, asi que sirve tambien para los contactos ya
 * guardados y sigue los cambios de foto.
 *
 * La foto es otra fila del proveedor distinta de la que concede el selector,
 * asi que aqui si hace falta READ_CONTACTS; se pide desde la ficha, y sin el
 * simplemente no hay foto.
 */
object FotoContacto {

    fun permitida(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** null si no hay permiso, no se encuentra el numero o no tiene foto. */
    suspend fun cargar(context: Context, telefono: String): ImageBitmap? {
        if (!permitida(context) || telefono.isBlank()) return null
        return withContext(Dispatchers.IO) {
            // Un contacto sin foto, un numero que ya no esta en la agenda o un
            // proveedor raro no deben tumbar la ficha.
            runCatching {
                val busqueda = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(telefono))
                // PHOTO_URI es la foto grande si la hay y si no la miniatura.
                val foto = context.contentResolver
                    .query(busqueda, arrayOf(PhoneLookup.PHOTO_URI), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?: return@runCatching null
                context.contentResolver.openInputStream(Uri.parse(foto))
                    ?.use { BitmapFactory.decodeStream(it) }
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
}
