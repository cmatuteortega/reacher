package com.example.recuerdallamar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.recuerdallamar.avisos.Notificaciones
import com.example.recuerdallamar.avisos.RecordatorioWorker
import com.example.recuerdallamar.datos.BaseDatos
import com.example.recuerdallamar.datos.Contacto
import com.example.recuerdallamar.datos.MedioContacto
import com.example.recuerdallamar.datos.porUrgencia
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Pausa sin cambios en la ficha tras la que se reprograma el aviso. */
private const val REPROGRAMAR_TRAS_MS = 800L

class ContactosViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = BaseDatos.de(app).contactos()

    /**
     * Ordenados por urgencia, no por fecha de alta. null mientras carga, para no
     * ensenar "lista vacia" un instante al abrir.
     */
    val contactos: StateFlow<List<Contacto>?> = dao.todos()
        .map { it.porUrgencia() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * La ficha abierta (null = lista). Vive aqui y no en la composicion para
     * que girar la pantalla no pierda un contacto recien elegido sin guardar.
     */
    private val _ficha = MutableStateFlow<Contacto?>(null)
    val ficha: StateFlow<Contacto?> = _ficha.asStateFlow()

    fun abrirFicha(contacto: Contacto) {
        _ficha.value = contacto
    }

    /** Desde el aviso: solo llega el id. Si ya no existe, se queda en la lista. */
    fun abrirFichaDe(id: Long) {
        viewModelScope.launch {
            dao.buscar(id)?.let { _ficha.value = it }
        }
    }

    fun cerrarFicha() {
        _ficha.value = null
    }

    fun observar(id: Long): Flow<Contacto?> = dao.observar(id)

    /** Da de alta a alguien nuevo y vuelve a la lista. */
    fun anadir(borrador: Contacto, dias: Int, medio: MedioContacto) {
        viewModelScope.launch {
            val id = dao.insertar(borrador.copy(frecuenciaDias = dias, medio = medio, ultimoContacto = LocalDate.now()))
            RecordatorioWorker.programar(getApplication(), id)
        }
        cerrarFicha()
    }

    private val reprogramaciones = mutableMapOf<Long, Job>()

    /**
     * Guardado automatico de la ficha de alguien que ya existe. La fila se
     * escribe al momento; el trabajo se reprograma cuando se deja de tocar,
     * para no reencolarlo en cada dia que pasa al arrastrar la frecuencia.
     */
    fun actualizar(id: Long, dias: Int, medio: MedioContacto) {
        viewModelScope.launch { dao.actualizarFicha(id, dias, medio) }
        reprogramaciones.remove(id)?.cancel()
        reprogramaciones[id] = viewModelScope.launch {
            delay(REPROGRAMAR_TRAS_MS)
            RecordatorioWorker.programar(getApplication(), id)
            reprogramaciones.remove(id)
        }
    }

    /**
     * Pone el ultimo contacto a hoy. Reprograma el trabajo como al guardar, para
     * que el ciclo diario arranque desde ahora, y retira el aviso pendiente:
     * ya no dice nada cierto.
     */
    fun llamadoHoy(id: Long) {
        viewModelScope.launch {
            dao.actualizarUltimoContacto(id, LocalDate.now())
            Notificaciones.quitar(getApplication(), id)
            RecordatorioWorker.programar(getApplication(), id)
        }
    }

    /**
     * Sin avisos de esta persona durante [dias]. El ultimo contacto no se toca:
     * la burbuja sigue creciendo mientras tanto.
     */
    fun pausar(id: Long, dias: Int) {
        viewModelScope.launch {
            dao.pausar(id, LocalDate.now().plusDays(dias.toLong()).atStartOfDay())
            Notificaciones.quitar(getApplication(), id)
        }
    }

    /** Quita la pausa; si ya tocaba, el trabajo diario lo comprueba al momento. */
    fun reanudar(id: Long) {
        viewModelScope.launch {
            dao.pausar(id, null)
            RecordatorioWorker.programar(getApplication(), id)
        }
    }

    fun eliminar(id: Long) {
        cerrarFicha()
        viewModelScope.launch {
            RecordatorioWorker.cancelar(getApplication(), id)
            Notificaciones.quitar(getApplication(), id)
            dao.borrar(id)
        }
    }

    fun forzarNotificacion(id: Long) {
        RecordatorioWorker.forzar(getApplication(), id)
    }
}
