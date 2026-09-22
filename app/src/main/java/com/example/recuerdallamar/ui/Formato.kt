package com.example.recuerdallamar.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val fecha = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val momento = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

fun LocalDate.bonita(): String = format(fecha)
fun LocalDateTime.bonito(): String = format(momento)
