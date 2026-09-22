package com.example.recuerdallamar

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.recuerdallamar.datos.BaseDatos
import com.example.recuerdallamar.datos.MedioContacto
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Destino del toque en la notificacion. Sin interfaz: abre la forma de
 * contacto preferida (marcador, llamada, WhatsApp, SMS o Telegram), apunta en
 * la base de datos que se pulso y se cierra.
 *
 * Una notificacion solo puede lanzar una cosa, y aqui hacen falta dos; desde
 * Android 12 el trampolin tiene que ser una Activity (un receiver o un
 * servicio ya no pueden abrir otra app).
 */
class NotificacionPulsadaActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = intent.getLongExtra(EXTRA_ID, -1)
        // Un aviso sin medio (anterior a esta version) abre el marcador, como antes.
        val medio = MedioContacto.desde(intent.getStringExtra(EXTRA_MEDIO))
        intent.getStringExtra(EXTRA_TELEFONO)?.let { Contactar.abrir(this, it, medio) }

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
        const val EXTRA_MEDIO = "contacto_medio"
    }
}
