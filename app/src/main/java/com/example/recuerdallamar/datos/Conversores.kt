package com.example.recuerdallamar.datos

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime

/** Room no sabe guardar java.time: la fecha va como dia epoch y el instante como texto ISO. */
class Conversores {
    @TypeConverter
    fun deFecha(fecha: LocalDate): Long = fecha.toEpochDay()

    @TypeConverter
    fun aFecha(dia: Long): LocalDate = LocalDate.ofEpochDay(dia)

    @TypeConverter
    fun deMomento(momento: LocalDateTime?): String? = momento?.toString()

    @TypeConverter
    fun aMomento(texto: String?): LocalDateTime? = texto?.let(LocalDateTime::parse)
}
