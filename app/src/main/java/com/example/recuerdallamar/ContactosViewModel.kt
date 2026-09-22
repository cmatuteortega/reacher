package com.example.recuerdallamar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.recuerdallamar.avisos.Notificaciones
import com.example.recuerdallamar.avisos.RecordatorioWorker
import com.example.recuerdallamar.datos.BaseDatos
import com.example.recuerdallamar.datos.Contacto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ContactosViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = BaseDatos.de(app).contactos()

    /** null mientras carga, para no ensenar "lista vacia" un instante al abrir. */
    val contactos: StateFlow<List<Contacto>?> = dao.todos()
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

    fun cerrarFicha() {
        _ficha.value = null
    }

    fun observar(id: Long): Flow<Contacto?> = dao.observar(id)

    fun guardar(borrador: Contacto, dias: Int) {
        viewModelScope.launch {
            val id = if (borrador.id == 0L) {
                dao.insertar(borrador.copy(frecuenciaDias = dias, ultimoContacto = LocalDate.now()))
            } else {
                dao.actualizarFrecuencia(borrador.id, dias)
                borrador.id
            }
            RecordatorioWorker.programar(getApplication(), id)
        }
        cerrarFicha()
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

    fun forzarNotificacion(id: Long) {
        RecordatorioWorker.forzar(getApplication(), id)
    }
}
