package com.example.recuerdallamar.datos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Contacto::class], version = 3, exportSchema = false)
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

        // v3: descartes, "mas tarde" y pausa por persona. Todos empiezan sin nada.
        private val MIGRACION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contactos ADD COLUMN descartes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contactos ADD COLUMN pospuestoHasta TEXT")
                db.execSQL("ALTER TABLE contactos ADD COLUMN pausadoHasta TEXT")
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
                ).addMigrations(MIGRACION_1_2, MIGRACION_2_3).build().also { instancia = it }
            }
    }
}
