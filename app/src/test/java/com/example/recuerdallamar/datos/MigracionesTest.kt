package com.example.recuerdallamar.datos

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Cada migracion contra los esquemas de app/schemas: los de 1 a 3 salen de
 * compilar esas versiones, asi que son las tablas que tiene de verdad quien
 * actualiza desde ellas. Room comprueba que el resultado es igual al esquema
 * nuevo; aqui se mira ademas que no se pierde nada de lo que ya habia.
 */
@RunWith(AndroidJUnit4::class)
class MigracionesTest {
    private val nombre = "migraciones.db"

    @get:Rule
    val ayudante = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BaseDatos::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory(),
    )

    /** Ana, tal y como la guardaba la version 1: fecha como dia epoch, sin nada mas. */
    private fun crearV1() {
        ayudante.createDatabase(nombre, 1).use { db ->
            db.execSQL(
                "INSERT INTO contactos (id, nombre, telefono, frecuenciaDias, ultimoContacto, notificacionPulsada, fechaPulsacion) " +
                    "VALUES (7, 'Ana', '+34 600 11 22 33', 10, ${LocalDate.of(2026, 9, 1).toEpochDay()}, 1, '2026-09-01T10:15')",
            )
        }
    }

    @Test
    fun de1a2PoneElMarcador() {
        crearV1()
        ayudante.runMigrationsAndValidate(nombre, 2, true, BaseDatos.MIGRACION_1_2).use { db ->
            db.query("SELECT medio FROM contactos WHERE id = 7").use {
                it.moveToFirst()
                assertEquals("MARCADOR", it.getString(0))
            }
        }
    }

    @Test
    fun de2a3SinDescartesNiPausas() {
        crearV1()
        ayudante.runMigrationsAndValidate(nombre, 2, true, BaseDatos.MIGRACION_1_2).close()
        ayudante.runMigrationsAndValidate(nombre, 3, true, BaseDatos.MIGRACION_2_3).use { db ->
            db.query("SELECT descartes, pospuestoHasta, pausadoHasta FROM contactos WHERE id = 7").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
                assertEquals(true, it.isNull(1))
                assertEquals(true, it.isNull(2))
            }
        }
    }

    @Test
    fun de3a4NotasVaciasSinCumpleanosNiCirculo() {
        ayudante.createDatabase(nombre, 3).use { db ->
            db.execSQL(
                "INSERT INTO contactos (id, nombre, telefono, frecuenciaDias, ultimoContacto, notificacionPulsada, medio, descartes, pausadoHasta) " +
                    "VALUES (3, 'Leo', '600', 7, 0, 0, 'WHATSAPP', 2, '2026-10-01T00:00')",
            )
        }
        ayudante.runMigrationsAndValidate(nombre, 4, true, BaseDatos.MIGRACION_3_4).use { db ->
            db.query("SELECT notas, cumpleanos, circulo, medio, descartes FROM contactos WHERE id = 3").use {
                it.moveToFirst()
                assertEquals("", it.getString(0))
                assertEquals(true, it.isNull(1))
                assertEquals(true, it.isNull(2))
                assertEquals("WHATSAPP", it.getString(3))
                assertEquals(2, it.getInt(4))
            }
        }
    }

    @Test
    fun de4a5ElCumpleanosQueHabiaVieneDeLaAgenda() {
        ayudante.createDatabase(nombre, 4).use { db ->
            db.execSQL(
                "INSERT INTO contactos (id, nombre, telefono, frecuenciaDias, ultimoContacto, notificacionPulsada, medio, descartes, notas, cumpleanos) " +
                    "VALUES (1, 'Mia', '600', 7, 0, 0, 'SMS', 0, 'x', '--02-29')",
            )
        }
        ayudante.runMigrationsAndValidate(nombre, 5, true, BaseDatos.MIGRACION_4_5).use { db ->
            db.query("SELECT cumpleanosManual, cumpleanos FROM contactos WHERE id = 1").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
                assertEquals("--02-29", it.getString(1))
            }
        }
    }

    /** De la primera version a la ultima, y la app lee la fila con Room como siempre. */
    @Test
    fun de1aLaUltimaSeLeeConRoom() {
        crearV1()
        ayudante.runMigrationsAndValidate(nombre, 5, true, *BaseDatos.MIGRACIONES).close()

        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val base = Room.databaseBuilder(contexto, BaseDatos::class.java, nombre)
            .addMigrations(*BaseDatos.MIGRACIONES)
            .allowMainThreadQueries()
            .build()
        try {
            val ana = runBlocking { base.contactos().buscar(7) }!!
            assertEquals("Ana", ana.nombre)
            assertEquals("+34 600 11 22 33", ana.telefono)
            assertEquals(10, ana.frecuenciaDias)
            assertEquals(LocalDate.of(2026, 9, 1), ana.ultimoContacto)
            assertEquals(LocalDateTime.of(2026, 9, 1, 10, 15), ana.fechaPulsacion)
            assertEquals(MedioContacto.MARCADOR, ana.medio)
            assertEquals(0, ana.descartes)
            assertEquals("", ana.notas)
            assertNull(ana.cumpleanos)
            assertNull(ana.circulo)
            assertFalse(ana.cumpleanosManual)
        } finally {
            base.close()
        }
    }
}
