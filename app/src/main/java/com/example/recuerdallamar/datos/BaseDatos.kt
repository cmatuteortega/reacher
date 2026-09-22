package com.example.recuerdallamar.datos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Contacto::class], version = 1, exportSchema = false)
@TypeConverters(Conversores::class)
abstract class BaseDatos : RoomDatabase() {
    abstract fun contactos(): ContactoDao

    companion object {
        @Volatile
        private var instancia: BaseDatos? = null

        // Una sola instancia para la app, el worker y el trampolin de la notificacion.
        fun de(context: Context): BaseDatos =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    BaseDatos::class.java,
                    "contactos.db",
                ).build().also { instancia = it }
            }
    }
}
