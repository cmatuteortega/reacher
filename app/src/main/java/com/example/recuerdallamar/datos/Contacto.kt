package com.example.recuerdallamar.datos

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * La unica tabla de la app: a quien hay que llamar, cada cuanto y cuando fue
 * la ultima vez, si se pulso la notificacion y por donde se prefiere contactar.
 */
@Entity(tableName = "contactos")
data class Contacto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val telefono: String,
    val frecuenciaDias: Int,
    val ultimoContacto: LocalDate = LocalDate.now(),
    val notificacionPulsada: Boolean = false,
    val fechaPulsacion: LocalDateTime? = null,
    val medio: MedioContacto = MedioContacto.MARCADOR,
) {
    /** Dia a partir del cual toca avisar. */
    fun proximoAviso(): LocalDate = ultimoContacto.plusDays(frecuenciaDias.toLong())

    /** "Hoy es igual o posterior a ultimo contacto + frecuencia". */
    fun tocaLlamar(hoy: LocalDate = LocalDate.now()): Boolean = !hoy.isBefore(proximoAviso())
}
