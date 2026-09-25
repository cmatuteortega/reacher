package com.example.recuerdallamar.datos

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * La unica tabla de la app: a quien hay que llamar, cada cuanto y cuando fue
 * la ultima vez, si se pulso la notificacion y por donde se prefiere contactar.
 *
 * Ademas, lo que el usuario hace con el aviso: cuantas veces lo ha quitado de
 * la bandeja sin contactar ([descartes]), hasta cuando lo ha pospuesto con
 * "Mas tarde" ([pospuestoHasta]) y hasta cuando no quiere avisos de esta
 * persona ([pausadoHasta]). Pausar no toca [ultimoContacto]: la urgencia sigue
 * creciendo y la burbuja se sigue haciendo grande.
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
    val descartes: Int = 0,
    val pospuestoHasta: LocalDateTime? = null,
    val pausadoHasta: LocalDateTime? = null,
) {
    /** Dia a partir del cual toca avisar. */
    fun proximoAviso(): LocalDate = ultimoContacto.plusDays(frecuenciaDias.toLong())

    /** "Hoy es igual o posterior a ultimo contacto + frecuencia". */
    fun tocaLlamar(hoy: LocalDate = LocalDate.now()): Boolean = !hoy.isBefore(proximoAviso())

    /** Avisos de esta persona en pausa desde la ficha. */
    fun pausado(ahora: LocalDateTime = LocalDateTime.now()): Boolean =
        pausadoHasta?.isAfter(ahora) == true

    /**
     * Pospuesto con "Mas tarde" y aun sin cumplir. Un minuto de margen: el
     * trabajo del aplazamiento puede arrancar un pelin antes de la hora exacta.
     */
    fun pospuesto(ahora: LocalDateTime = LocalDateTime.now()): Boolean =
        pospuestoHasta?.minusMinutes(1)?.isAfter(ahora) == true

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
