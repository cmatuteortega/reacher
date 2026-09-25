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

    // Solo lo que se edita en la ficha, y no un @Update de la fila entera: la
    // ficha puede llevar una copia vieja y pisaria la marca de notificacion pulsada.
    @Query("UPDATE contactos SET frecuenciaDias = :dias, medio = :medio WHERE id = :id")
    suspend fun actualizarFicha(id: Long, dias: Int, medio: MedioContacto)

    // Contactar deja a cero lo que se acumulo con el aviso anterior.
    @Query("UPDATE contactos SET ultimoContacto = :fecha, descartes = 0, pospuestoHasta = NULL WHERE id = :id")
    suspend fun actualizarUltimoContacto(id: Long, fecha: LocalDate)

    /** Tocar el aviso (o su boton Contactar) cuenta como contacto de hoy. */
    @Query(
        "UPDATE contactos SET notificacionPulsada = 1, fechaPulsacion = :cuando, " +
            "ultimoContacto = :hoy, descartes = 0, pospuestoHasta = NULL WHERE id = :id",
    )
    suspend fun marcarPulsada(id: Long, cuando: LocalDateTime, hoy: LocalDate)

    /** El usuario quito el aviso de la bandeja sin tocarlo. */
    @Query("UPDATE contactos SET descartes = descartes + 1 WHERE id = :id")
    suspend fun sumarDescarte(id: Long)

    @Query("UPDATE contactos SET pospuestoHasta = :hasta WHERE id = :id")
    suspend fun posponer(id: Long, hasta: LocalDateTime)

    /** null reanuda los avisos de esta persona. */
    @Query("UPDATE contactos SET pausadoHasta = :hasta WHERE id = :id")
    suspend fun pausar(id: Long, hasta: LocalDateTime?)

    @Query("DELETE FROM contactos WHERE id = :id")
    suspend fun borrar(id: Long)
}
