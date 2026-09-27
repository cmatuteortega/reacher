package com.example.recuerdallamar.datos

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Contacto::class], version = 5, exportSchema = true)
@TypeConverters(Conversores::class)
abstract class BaseDatos : RoomDatabase() {
    abstract fun contactos(): ContactoDao

    companion object {
        // v2: forma de contacto preferida. Los contactos existentes siguen
        // abriendo el marcador, que es lo que hacian antes.
        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contactos ADD COLUMN medio TEXT NOT NULL DEFAULT 'MARCADOR'")
            }
        }

        // v3: descartes, "mas tarde" y pausa por persona. Todos empiezan sin nada.
        val MIGRACION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contactos ADD COLUMN descartes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contactos ADD COLUMN pospuestoHasta TEXT")
                db.execSQL("ALTER TABLE contactos ADD COLUMN pausadoHasta TEXT")
            }
        }

        // v4: notas, cumpleanos y circulo. Todos vacios: el cumpleanos se lee
        // de la agenda la proxima vez que haya permiso.
        val MIGRACION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contactos ADD COLUMN notas TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE contactos ADD COLUMN cumpleanos TEXT")
                db.execSQL("ALTER TABLE contactos ADD COLUMN circulo TEXT")
            }
        }

        // v5: si el cumpleanos se puso a mano. Los que ya habia vienen de la agenda.
        val MIGRACION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contactos ADD COLUMN cumpleanosManual INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Todas, en orden. Las prueba MigracionesTest contra los esquemas de app/schemas. */
        val MIGRACIONES = arrayOf(MIGRACION_1_2, MIGRACION_2_3, MIGRACION_3_4, MIGRACION_4_5)

        @Volatile
        private var instancia: BaseDatos? = null

        // Una sola instancia para la app, el worker y el trampolin de la notificacion.
        fun de(context: Context): BaseDatos =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    BaseDatos::class.java,
                    "contactos.db",
                ).addMigrations(*MIGRACIONES).build().also { instancia = it }
            }

        /** Solo pruebas: cierra la base y la proxima llamada a [de] abre otra. */
        @VisibleForTesting
        fun olvidar() {
            synchronized(this) {
                instancia?.close()
                instancia = null
            }
        }
    }
}
