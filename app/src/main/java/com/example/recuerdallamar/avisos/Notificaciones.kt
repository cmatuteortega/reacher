package com.example.recuerdallamar.avisos

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.recuerdallamar.NotificacionPulsadaActivity
import com.example.recuerdallamar.datos.Contacto

object Notificaciones {
    private const val CANAL = "recordatorios"

    fun crearCanal(context: Context) {
        val canal = NotificationChannel(
            CANAL,
            "Recordatorios de llamada",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Aviso cuando toca llamar a un contacto" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    fun permitidas(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // comprobado en permitidas()
    fun mostrar(context: Context, contacto: Contacto) {
        if (!permitidas(context)) return

        // Numero y medio viajan en el intent para no esperar a la base de datos al tocar.
        val intent = Intent(context, NotificacionPulsadaActivity::class.java).apply {
            putExtra(NotificacionPulsadaActivity.EXTRA_ID, contacto.id)
            putExtra(NotificacionPulsadaActivity.EXTRA_TELEFONO, contacto.telefono)
            putExtra(NotificacionPulsadaActivity.EXTRA_MEDIO, contacto.medio.name)
        }
        // requestCode por contacto: con uno fijo, FLAG_UPDATE_CURRENT haria que
        // todas las notificaciones abiertas irian al ultimo contacto.
        val pendiente = PendingIntent.getActivity(
            context,
            contacto.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notificacion = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(contacto.nombre)
            .setContentText(contacto.medio.aviso(contacto.nombre))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendiente)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(contacto.id.toInt(), notificacion)
    }

    /** Quita el aviso de un contacto si sigue en la bandeja. */
    fun quitar(context: Context, id: Long) {
        NotificationManagerCompat.from(context).cancel(id.toInt())
    }
}
