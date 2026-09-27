package com.example.recuerdallamar

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.PhoneLookup
import java.time.MonthDay

/**
 * Cumpleanos de la agenda, buscado por numero como la foto: la persona y su
 * fecha de nacimiento son filas distintas del proveedor, asi que hace falta
 * READ_CONTACTS. Sin permiso, o si no lo tiene apuntado, null.
 */
object CumpleanosAgenda {

    fun leer(context: Context, telefono: String): MonthDay? {
        if (!FotoContacto.permitida(context) || telefono.isBlank()) return null
        // Un proveedor raro o una fecha mal escrita no deben tumbar nada.
        return runCatching {
            val busqueda = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(telefono))
            val idContacto = context.contentResolver
                .query(busqueda, arrayOf(PhoneLookup._ID), null, null, null)
                ?.use { if (it.moveToFirst()) it.getLong(0) else null }
                ?: return@runCatching null
            context.contentResolver.query(
                Data.CONTENT_URI,
                arrayOf(Event.START_DATE),
                "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ? AND ${Event.TYPE} = ?",
                arrayOf(idContacto.toString(), Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString()),
                null,
            )?.use { if (it.moveToFirst()) it.getString(0) else null }?.let(::interpretar)
        }.getOrNull()
    }

    /**
     * La agenda lo guarda como "1990-03-12", "--03-12" (sin ano) o, segun el
     * fabricante, "19900312" o "12.03.1990". Solo interesan dia y mes.
     */
    internal fun interpretar(texto: String): MonthDay? {
        val t = texto.trim()
        val (mes, dia) = Regex("""^(?:\d{4}|-)-(\d{1,2})-(\d{1,2})""").find(t)?.destructured?.let { (m, d) -> m to d }
            ?: Regex("""^\d{4}(\d{2})(\d{2})$""").find(t)?.destructured?.let { (m, d) -> m to d }
            ?: Regex("""^(\d{1,2})[./](\d{1,2})[./]\d{4}$""").find(t)?.destructured?.let { (d, m) -> m to d }
            ?: return null
        return runCatching { MonthDay.of(mes.toInt(), dia.toInt()) }.getOrNull()
    }
}
