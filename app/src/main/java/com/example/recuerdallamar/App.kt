package com.example.recuerdallamar

import android.app.Application
import com.example.recuerdallamar.avisos.CumpleanosWorker
import com.example.recuerdallamar.avisos.Notificaciones
import com.example.recuerdallamar.avisos.RecordatorioWorker
import com.example.recuerdallamar.datos.BaseDatos
import com.example.recuerdallamar.widget.WidgetHoy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class App : Application() {
    /**
     * Ambito que vive lo que el proceso. Lo usa el trampolin de la notificacion,
     * que se cierra al instante y no puede esperar a que acabe su escritura.
     */
    val ambito = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Notificaciones.crearCanal(this)
        CumpleanosWorker.programar(this)
        ambito.launch { RecordatorioWorker.programarQueFalten(this@App) }
        // El widget sigue a la base de datos mientras el proceso vive: cualquier
        // cambio (alta, "He llamado hoy", aviso tocado...) lo redibuja. La
        // primera emision es el estado de ahora, que el widget ya tiene.
        BaseDatos.de(this).contactos().todos()
            .drop(1)
            .debounce(300)
            .onEach { WidgetHoy.actualizar(this) }
            .launchIn(ambito)
    }
}
