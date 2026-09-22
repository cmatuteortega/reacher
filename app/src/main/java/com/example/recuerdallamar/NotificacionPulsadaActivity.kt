package com.example.recuerdallamar

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.recuerdallamar.datos.BaseDatos
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Destino del toque en la notificacion. Sin interfaz: abre el marcador,
 * apunta en la base de datos que se pulso y se cierra.
 *
 * Una notificacion solo puede lanzar una cosa, y aqui hacen falta dos; desde
 * Android 12 el trampolin tiene que ser una Activity (un receiver o un
 * servicio ya no pueden abrir el marcador).
 */
class NotificacionPulsadaActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = intent.getLongExtra(EXTRA_ID, -1)
        intent.getStringExtra(EXTRA_TELEFONO)?.let { Marcador.abrir(this, it) }

        if (id >= 0) {
            val app = application as App
            app.ambito.launch {
                BaseDatos.de(app).contactos().marcarPulsada(id, LocalDateTime.now())
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_ID = "contacto_id"
        const val EXTRA_TELEFONO = "contacto_telefono"
    }
}
