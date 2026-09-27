package com.example.recuerdallamar.datos

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay

/**
 * Room no sabe guardar java.time: la fecha va como dia epoch y el instante como
 * texto ISO, y el cumpleanos como "--MM-DD". El medio de contacto va por nombre.
 */
class Conversores {
    @TypeConverter
    fun deFecha(fecha: LocalDate): Long = fecha.toEpochDay()

    @TypeConverter
    fun aFecha(dia: Long): LocalDate = LocalDate.ofEpochDay(dia)

    @TypeConverter
    fun deMomento(momento: LocalDateTime?): String? = momento?.toString()

    @TypeConverter
    fun aMomento(texto: String?): LocalDateTime? = texto?.let(LocalDateTime::parse)

    @TypeConverter
    fun deMedio(medio: MedioContacto): String = medio.name

    @TypeConverter
    fun aMedio(nombre: String): MedioContacto = MedioContacto.desde(nombre)

    @TypeConverter
    fun deDiaDelAno(dia: MonthDay?): String? = dia?.toString()

    @TypeConverter
    fun aDiaDelAno(texto: String?): MonthDay? = texto?.let { runCatching { MonthDay.parse(it) }.getOrNull() }
}
