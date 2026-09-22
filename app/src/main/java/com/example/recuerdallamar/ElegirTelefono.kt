package com.example.recuerdallamar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.result.contract.ActivityResultContract

/**
 * Selector de contactos del sistema, filtrado a numeros de telefono.
 *
 * Se pide sobre CommonDataKinds.Phone y no con PickContact porque asi el
 * resultado ya es UN numero concreto y el sistema da permiso de lectura
 * sobre esa fila: la app no necesita READ_CONTACTS.
 */
class ElegirTelefono : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Nombre y numero de la fila elegida, o null si el proveedor no la devuelve. */
fun leerTelefono(context: Context, uri: Uri): Pair<String, String>? =
    context.contentResolver.query(
        uri,
        arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val numero = cursor.getString(1) ?: return@use null
        val nombre = cursor.getString(0) ?: numero
        nombre to numero
    }
