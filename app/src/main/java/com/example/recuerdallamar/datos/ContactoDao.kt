package com.example.recuerdallamar.datos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

@Dao
interface ContactoDao {
    // Los nuevos arriba, para que la animacion de alta se vea sin desplazarse.
    @Query("SELECT * FROM contactos ORDER BY id DESC")
    fun todos(): Flow<List<Contacto>>

    @Query("SELECT * FROM contactos WHERE id = :id")
    fun observar(id: Long): Flow<Contacto?>

    @Query("SELECT * FROM contactos WHERE id = :id")
    suspend fun buscar(id: Long): Contacto?

    @Insert
    suspend fun insertar(contacto: Contacto): Long

    // Solo la frecuencia, y no un @Update de la fila entera: la ficha puede
    // llevar una copia vieja y pisaria la marca de notificacion pulsada.
    @Query("UPDATE contactos SET frecuenciaDias = :dias WHERE id = :id")
    suspend fun actualizarFrecuencia(id: Long, dias: Int)

    @Query("UPDATE contactos SET ultimoContacto = :fecha WHERE id = :id")
    suspend fun actualizarUltimoContacto(id: Long, fecha: LocalDate)

    @Query("UPDATE contactos SET notificacionPulsada = 1, fechaPulsacion = :cuando WHERE id = :id")
    suspend fun marcarPulsada(id: Long, cuando: LocalDateTime)
}
