package com.example.recuerdallamar

import android.app.Application
import com.example.recuerdallamar.avisos.Notificaciones
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    /**
     * Ambito que vive lo que el proceso. Lo usa el trampolin de la notificacion,
     * que se cierra al instante y no puede esperar a que acabe su escritura.
     */
    val ambito = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notificaciones.crearCanal(this)
    }
}
