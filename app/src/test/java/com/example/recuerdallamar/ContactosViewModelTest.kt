package com.example.recuerdallamar

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.recuerdallamar.datos.BaseDatos
import com.example.recuerdallamar.datos.Contacto
import com.example.recuerdallamar.datos.MedioContacto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import java.time.LocalDate
import java.time.LocalDateTime

/** Lo que hace la ficha al guardar: la fila en la base de datos y el trabajo diario. */
@RunWith(AndroidJUnit4::class)
class ContactosViewModelTest {
    private val app: App = ApplicationProvider.getApplicationContext()
    private val dao = BaseDatos.de(app).contactos()
    private val vm = ContactosViewModel(app, SavedStateHandle())

    private fun esperarA(condicion: () -> Boolean) {
        val limite = System.currentTimeMillis() + 5_000
        while (!condicion()) {
            check(System.currentTimeMillis() < limite) { "No llego a cumplirse" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun lista() = runBlocking { dao.lista() }

    @Test
    fun anadirGuardaConElUltimoContactoDeHoyYProgramaElAviso() {
        vm.empezarAlta(Contacto(nombre = "Ana", telefono = "600", frecuenciaDias = 14, medio = MedioContacto.SMS))
        vm.anadir(vm.borrador.value!!.copy(ultimoContacto = LocalDate.of(2020, 1, 1)))
        esperarA { lista().isNotEmpty() }

        val ana = lista().single()
        assertEquals("Ana", ana.nombre)
        assertEquals(14, ana.frecuenciaDias)
        assertEquals(MedioContacto.SMS, ana.medio)
        assertEquals(LocalDate.now(), ana.ultimoContacto)
        // El borrador ya no esta: la ficha de alta se cierra.
        assertNull(vm.borrador.value)
        esperarA {
            WorkManager.getInstance(app).getWorkInfosForUniqueWork("recordatorio-${ana.id}").get()
                .any { it.state == WorkInfo.State.ENQUEUED }
        }
    }

    @Test
    fun elBorradorSobreviveAQueAndroidCierreLaApp() {
        val estado = SavedStateHandle()
        ContactosViewModel(app, estado).empezarAlta(Contacto(nombre = "Leo", telefono = "611", frecuenciaDias = 3))
        // Otra instancia con el mismo estado guardado, como tras recrear el proceso.
        val otra = ContactosViewModel(app, estado)
        assertEquals("Leo", otra.borrador.value?.nombre)
        assertEquals(3, otra.borrador.value?.frecuenciaDias)
    }

    @Test
    fun pausarYReanudarNoTocanElUltimoContacto() {
        val hace = LocalDate.now().minusDays(9)
        val id = runBlocking { dao.insertar(Contacto(nombre = "Ana", telefono = "600", frecuenciaDias = 7, ultimoContacto = hace)) }
        vm.pausar(id, 7)
        esperarA { runBlocking { dao.buscar(id) }!!.pausadoHasta != null }
        val pausada = runBlocking { dao.buscar(id) }!!
        assertEquals(LocalDate.now().plusDays(7).atStartOfDay(), pausada.pausadoHasta)
        assertTrue(pausada.pausado(LocalDateTime.now()))
        assertEquals(hace, pausada.ultimoContacto)

        vm.reanudar(id)
        esperarA { runBlocking { dao.buscar(id) }!!.pausadoHasta == null }
        assertEquals(hace, runBlocking { dao.buscar(id) }!!.ultimoContacto)
    }
}
