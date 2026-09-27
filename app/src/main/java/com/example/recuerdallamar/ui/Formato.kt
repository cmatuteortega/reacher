package com.example.recuerdallamar.ui

import android.content.res.Resources
import com.example.recuerdallamar.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val fecha = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val momento = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

// Con el Locale de ahora, no el de cuando se creo el formateador: el idioma
// se puede cambiar desde Ajustes con la app abierta.
fun LocalDate.bonita(): String = format(fecha.withLocale(Locale.getDefault()))
fun LocalDateTime.bonito(): String = format(momento.withLocale(Locale.getDefault()))

/** "12 de marzo", "March 12"...: dia y mes en el orden y la forma de cada idioma. */
fun MonthDay.bonito(): String {
    val locale = Locale.getDefault()
    val patron = android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMMM")
    // Un ano bisiesto cualquiera, para que el 29 de febrero exista.
    return atYear(2024).format(DateTimeFormatter.ofPattern(patron, locale))
}

fun Resources.dias(d: Int): String = getQuantityString(R.plurals.dias, d, d)

fun Resources.horas(h: Int): String = getQuantityString(R.plurals.horas, h, h)

/** "Cada 7 dias"; el 1 aparte, que en casi todos los idiomas se dice de otra forma. */
fun Resources.cadaDias(d: Int): String =
    if (d == 1) getString(R.string.cada_dia) else getQuantityString(R.plurals.cada_dias, d, d)
