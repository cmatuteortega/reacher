package com.example.recuerdallamar.avisos

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.recuerdallamar.datos.AlmacenAjustes
import com.example.recuerdallamar.datos.BaseDatos
import com.example.recuerdallamar.datos.Contacto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class RecordatorioWorkerTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val dao = BaseDatos.de(app).contactos()
    private val notificaciones = app.getSystemService(NotificationManager::class.java)

    @Before
    fun preparar() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        // Todo el dia: que la hora a la que corre la prueba no cuente, salvo donde se prueba.
        AlmacenAjustes.de(app).cambiar { it.copy(horaDesde = 0, horaHasta = 0) }
    }

    private fun guardar(contacto: Contacto): Long = runBlocking { dao.insertar(contacto) }

    private fun debido(dias: Int = 7) =
        Contacto(nombre = "Ana", telefono = "600", frecuenciaDias = dias, ultimoContacto = LocalDate.now().minusDays(dias.toLong()))

    private fun correr(id: Long, forzar: Boolean = false): ListenableWorker.Result =
        runBlocking {
            TestListenableWorkerBuilder<RecordatorioWorker>(app)
                .setInputData(workDataOf("id" to id, "forzar" to forzar))
                .build()
                .doWork()
        }

    private fun avisoDe(id: Long) = shadowOf(notificaciones).getNotification(id.toInt())

    @Test
    fun avisaCuandoToca() {
        val id = guardar(debido())
        assertEquals(ListenableWorker.Result.success(), correr(id))
        val aviso = avisoDe(id)
        assertNotNull(aviso)
        assertEquals("Ana", aviso.extras.getString("android.title"))
        // Contactar, Mas tarde y Mas opciones.
        assertEquals(3, aviso.actions.size)
    }

    @Test
    fun noAvisaAntesDeTiempo() {
        val id = guardar(debido().copy(ultimoContacto = LocalDate.now().minusDays(3)))
        correr(id)
        assertNull(avisoDe(id))
    }

    @Test
    fun noAvisaEnPausaNiPospuesto() {
        val pausado = guardar(debido().copy(pausadoHasta = LocalDateTime.now().plusDays(2)))
        val pospuesto = guardar(debido().copy(pospuestoHasta = LocalDateTime.now().plusHours(2)))
        correr(pausado)
        correr(pospuesto)
        assertNull(avisoDe(pausado))
        assertNull(avisoDe(pospuesto))
    }

    @Test
    fun noAvisaConLosAvisosApagados() {
        AlmacenAjustes.de(app).cambiar { it.copy(avisosActivos = false) }
        val id = guardar(debido())
        correr(id)
        assertNull(avisoDe(id))
    }

    @Test
    fun forzarAvisaAunqueNoToque() {
        AlmacenAjustes.de(app).cambiar { it.copy(avisosActivos = false) }
        val id = guardar(debido().copy(ultimoContacto = LocalDate.now()))
        correr(id, forzar = true)
        assertNotNull(avisoDe(id))
    }

    @Test
    fun fueraDeHorarioSeAplazaAlEmpezarLaFranja() {
        // Una franja de una hora que empieza dentro de dos: ahora seguro que no.
        val desde = (LocalDateTime.now().hour + 2) % 24
        AlmacenAjustes.de(app).cambiar { it.copy(horaDesde = desde, horaHasta = (desde + 1) % 24) }
        val id = guardar(debido())
        correr(id)
        assertNull(avisoDe(id))
        val aplazado = WorkManager.getInstance(app).getWorkInfosForUniqueWork("aplazado-$id").get().single()
        assertEquals(WorkInfo.State.ENQUEUED, aplazado.state)
    }

    @Test
    fun siLaPersonaYaNoExisteNoHayNadaQueHacer() {
        assertEquals(ListenableWorker.Result.success(), correr(999))
    }

    @Test
    fun programarDejaUnTrabajoDiarioPorPersona() {
        val id = guardar(debido())
        RecordatorioWorker.programar(app, id)
        val trabajos = WorkManager.getInstance(app).getWorkInfosForUniqueWork("recordatorio-$id").get()
        assertEquals(1, trabajos.size)
        RecordatorioWorker.cancelar(app, id)
        assertTrue(
            WorkManager.getInstance(app).getWorkInfosForUniqueWork("recordatorio-$id").get()
                .all { it.state == WorkInfo.State.CANCELLED },
        )
    }

    /** "Mas tarde" en el aviso: se pospone, se quita de la bandeja y vuelve pasadas las horas elegidas. */
    @Test
    fun masTardePosponeYAplaza() {
        AlmacenAjustes.de(app).cambiar { it.copy(horasPosponer = 3) }
        val id = guardar(debido())
        correr(id)
        assertNotNull(avisoDe(id))

        enviar(app, AccionesAviso.MAS_TARDE, id)
        esperarA { runBlocking { dao.buscar(id) }!!.pospuestoHasta != null }

        val hasta = runBlocking { dao.buscar(id) }!!.pospuestoHasta!!
        assertTrue(hasta.isAfter(LocalDateTime.now().plusHours(2).plusMinutes(59)))
        esperarA { avisoDe(id) == null }
        val aplazado = WorkManager.getInstance(app).getWorkInfosForUniqueWork("aplazado-$id").get().single()
        assertEquals(WorkInfo.State.ENQUEUED, aplazado.state)
    }

    @Test
    fun quitarElAvisoDeLaBandejaCuentaUnDescarte() {
        val id = guardar(debido())
        enviar(app, AccionesAviso.DESCARTADO, id)
        esperarA { runBlocking { dao.buscar(id) }!!.descartes == 1 }
    }

    private fun enviar(context: Context, accion: String, id: Long) {
        AccionesAviso().onReceive(
            context,
            Intent(context, AccionesAviso::class.java).setAction(accion).putExtra("contacto_id", id),
        )
    }

    /** El receiver escribe en otro hilo (App.ambito): se espera a que se vea, con un limite. */
    private fun esperarA(condicion: () -> Boolean) {
        val limite = System.currentTimeMillis() + 5_000
        while (!condicion()) {
            check(System.currentTimeMillis() < limite) { "No llego a cumplirse" }
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }
}
