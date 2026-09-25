package com.example.recuerdallamar.avisos

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.recuerdallamar.App
import com.example.recuerdallamar.datos.AlmacenAjustes
import com.example.recuerdallamar.datos.BaseDatos
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Lo que se hace con el aviso sin abrir nada: "Mas tarde" y quitarlo de la
 * bandeja. Un receiver basta porque ninguna de las dos lanza una Activity.
 */
class AccionesAviso : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        if (id < 0) return
        val app = context.applicationContext as App
        val dao = BaseDatos.de(app).contactos()
        val resultado = goAsync()
        app.ambito.launch {
            try {
                when (intent.action) {
                    MAS_TARDE -> {
                        val horas = AlmacenAjustes.de(app).ajustes.value.horasPosponer
                        dao.posponer(id, LocalDateTime.now().plusHours(horas.toLong()))
                        Notificaciones.quitar(app, id)
                        RecordatorioWorker.aplazar(app, id, horas * 60L)
                    }
                    // Sin hacer nada mas: el trabajo diario lo vuelve a sacar
                    // manana mientras siga tocando.
                    DESCARTADO -> dao.sumarDescarte(id)
                }
            } finally {
                resultado.finish()
            }
        }
    }

    companion object {
        const val MAS_TARDE = "com.example.recuerdallamar.MAS_TARDE"
        const val DESCARTADO = "com.example.recuerdallamar.DESCARTADO"
        private const val EXTRA_ID = "contacto_id"

        /** La accion va en el intent, asi cada una tiene su PendingIntent aunque compartan requestCode. */
        fun pendiente(context: Context, accion: String, id: Long): PendingIntent {
            val intent = Intent(context, AccionesAviso::class.java).apply {
                action = accion
                putExtra(EXTRA_ID, id)
            }
            return PendingIntent.getBroadcast(
                context,
                id.toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
