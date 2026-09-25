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
import com.example.recuerdallamar.MainActivity
import com.example.recuerdallamar.NotificacionPulsadaActivity
import com.example.recuerdallamar.R
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
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(ContextCompat.getColor(context, R.color.naranja))
            .setContentTitle(contacto.nombre)
            .setContentText(contacto.medio.aviso(contacto.nombre))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendiente)
            .setAutoCancel(true)
            // Solo llega si el usuario lo quita de la bandeja: ni al tocarlo ni
            // al retirarlo la app con cancel().
            .setDeleteIntent(AccionesAviso.pendiente(context, AccionesAviso.DESCARTADO, contacto.id))
            // Tocar el boton es lo mismo que tocar el aviso.
            .addAction(0, "Contactar", pendiente)
            .addAction(0, "Más tarde", AccionesAviso.pendiente(context, AccionesAviso.MAS_TARDE, contacto.id))
            // Android deja tres botones; el tercero lleva a la ficha, donde estan
            // "He llamado hoy" (si ya se hablo por otro lado), pausar y eliminar.
            .addAction(0, "Más opciones", abrirFicha(context, contacto.id))
            .build()

        NotificationManagerCompat.from(context).notify(contacto.id.toInt(), notificacion)
    }

    /** Abre la app en la ficha del contacto. */
    private fun abrirFicha(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_FICHA, id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Quita el aviso de un contacto si sigue en la bandeja. */
    fun quitar(context: Context, id: Long) {
        NotificationManagerCompat.from(context).cancel(id.toInt())
    }
}
