package com.example.recuerdallamar.datos

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

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

    /**
     * Parte de su cadencia ya consumida: dias desde el ultimo contacto entre la
     * frecuencia. 0 recien hablado, 1 toca hoy, mas de 1 va con retraso. Relativo
     * a cada persona: 10 dias es poco para quien va cada mes y mucho para quien va cada semana.
     */
    fun urgencia(hoy: LocalDate = LocalDate.now()): Float =
        ChronoUnit.DAYS.between(ultimoContacto, hoy).coerceAtLeast(0).toFloat() / frecuenciaDias.coerceAtLeast(1)
}

/** El orden de Personas, en lista y en burbujas: primero a quien mas le toca. */
fun List<Contacto>.porUrgencia(hoy: LocalDate = LocalDate.now()): List<Contacto> =
    sortedWith(compareByDescending<Contacto> { it.urgencia(hoy) }.thenBy { it.nombre.lowercase() })
