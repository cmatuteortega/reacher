package com.example.recuerdallamar.datos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Contacto::class], version = 2, exportSchema = false)
@TypeConverters(Conversores::class)
abstract class BaseDatos : RoomDatabase() {
    abstract fun contactos(): ContactoDao

    companion object {
        // v2: forma de contacto preferida. Los contactos existentes siguen
        // abriendo el marcador, que es lo que hacian antes.
        private val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contactos ADD COLUMN medio TEXT NOT NULL DEFAULT 'MARCADOR'")
            }
        }

        @Volatile
        private var instancia: BaseDatos? = null

        // Una sola instancia para la app, el worker y el trampolin de la notificacion.
        fun de(context: Context): BaseDatos =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    BaseDatos::class.java,
                    "contactos.db",
                ).addMigrations(MIGRACION_1_2).build().also { instancia = it }
            }
    }
}
