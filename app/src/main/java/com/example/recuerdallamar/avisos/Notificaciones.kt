package com.example.recuerdallamar.avisos

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.recuerdallamar.Enlaces
import com.example.recuerdallamar.Idioma
import com.example.recuerdallamar.NotificacionPulsadaActivity
import com.example.recuerdallamar.R
import com.example.recuerdallamar.datos.Contacto

/**
 * Tres canales, cada uno con su importancia, para que el usuario decida en
 * los ajustes del sistema cuales suenan: los que tocan hoy (normal), los que
 * ya van muy atrasados (alta, salen arriba) y los cumpleanos (alta). Con dos
 * o mas avisos de gente en la bandeja se agrupan bajo un resumen con los nombres.
 */
object Notificaciones {
    /** El de siempre: se mantiene el id para no perder lo que el usuario ya ajusto. */
    private const val CANAL = "recordatorios"
    private const val CANAL_ATRASADOS = "atrasados"
    private const val CANAL_CUMPLEANOS = "cumpleanos"
    private const val GRUPO_CANALES = "personas"

    /** Todos los avisos de gente van en el mismo grupo de la bandeja. */
    private const val GRUPO = "com.example.recuerdallamar.PERSONAS"
    private const val ETIQUETA_RESUMEN = "resumen"
    private const val ETIQUETA_CUMPLEANOS = "cumpleanos"

    /** A partir de aqui (la mitad de su cadencia de retraso) el aviso va en el canal de atrasados. */
    private const val URGENCIA_ATRASADO = 1.5f

    /** Crearlos otra vez con el mismo id solo les cambia el nombre: asi siguen al idioma. */
    fun crearCanal(context: Context) {
        val textos = Idioma.envolver(context)
        val gestor = context.getSystemService(NotificationManager::class.java)
        gestor.createNotificationChannelGroup(NotificationChannelGroup(GRUPO_CANALES, textos.getString(R.string.canal_grupo)))
        fun canal(id: String, nombre: Int, descripcion: Int, importancia: Int) =
            NotificationChannel(id, textos.getString(nombre), importancia).apply {
                description = textos.getString(descripcion)
                group = GRUPO_CANALES
            }
        gestor.createNotificationChannels(
            listOf(
                canal(CANAL, R.string.canal_nombre, R.string.canal_descripcion, NotificationManager.IMPORTANCE_DEFAULT),
                canal(CANAL_ATRASADOS, R.string.canal_atrasados, R.string.canal_atrasados_descripcion, NotificationManager.IMPORTANCE_HIGH),
                canal(CANAL_CUMPLEANOS, R.string.canal_cumpleanos, R.string.canal_cumpleanos_descripcion, NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    fun permitidas(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // comprobado en permitidas()
    fun mostrar(context: Context, contacto: Contacto) {
        if (!permitidas(context)) return
        // Sale del worker, sin actividad: el idioma elegido hay que ponerlo a mano.
        val textos = Idioma.envolver(context)
        val pendiente = contactar(context, contacto, cumpleanos = false)
        val canal = if (contacto.urgencia() >= URGENCIA_ATRASADO) CANAL_ATRASADOS else CANAL

        val notificacion = NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(ContextCompat.getColor(context, R.color.teja))
            .setContentTitle(contacto.nombre)
            .setContentText(textos.getString(contacto.medio.aviso, contacto.nombre))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendiente)
            .setAutoCancel(true)
            .setGroup(GRUPO)
            // Suena cada aviso, no el resumen: si no, al agruparse sonaria dos veces.
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            // Solo llega si el usuario lo quita de la bandeja: ni al tocarlo ni
            // al retirarlo la app con cancel().
            .setDeleteIntent(AccionesAviso.pendiente(context, AccionesAviso.DESCARTADO, contacto.id))
            // Tocar el boton es lo mismo que tocar el aviso.
            .addAction(0, textos.getString(R.string.aviso_contactar), pendiente)
            .addAction(0, textos.getString(R.string.aviso_mas_tarde), AccionesAviso.pendiente(context, AccionesAviso.MAS_TARDE, contacto.id))
            // Android deja tres botones; el tercero lleva a la ficha, donde estan
            // "He llamado hoy" (si ya se hablo por otro lado), pausar y eliminar.
            .addAction(0, textos.getString(R.string.aviso_mas_opciones), abrirFicha(context, contacto.id))
            .build()

        NotificationManagerCompat.from(context).notify(contacto.id.toInt(), notificacion)
        actualizarResumen(context)
    }

    /** Felicitar por la forma de contacto de siempre; cuenta como contacto, como el aviso normal. */
    @SuppressLint("MissingPermission") // comprobado en permitidas()
    fun mostrarCumpleanos(context: Context, contacto: Contacto) {
        if (!permitidas(context)) return
        val textos = Idioma.envolver(context)
        val pendiente = contactar(context, contacto, cumpleanos = true)
        val notificacion = NotificationCompat.Builder(context, CANAL_CUMPLEANOS)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(ContextCompat.getColor(context, R.color.teja))
            .setContentTitle(textos.getString(R.string.cumpleanos_hoy, contacto.nombre))
            .setContentText(textos.getString(R.string.cumpleanos_felicita))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(pendiente)
            .setAutoCancel(true)
            .setGroup(GRUPO)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .addAction(0, textos.getString(R.string.aviso_contactar), pendiente)
            .addAction(0, textos.getString(R.string.aviso_mas_opciones), abrirFicha(context, contacto.id))
            .build()
        NotificationManagerCompat.from(context).notify(ETIQUETA_CUMPLEANOS, contacto.id.toInt(), notificacion)
        actualizarResumen(context)
    }

    /**
     * Resumen del grupo: con dos o mas avisos de gente lo pide Android para
     * plegarlos juntos (en el reloj y en versiones antiguas no se agrupan
     * solos). Lleva los nombres; tocarlo abre Personas. Con uno o ninguno se quita.
     */
    @SuppressLint("MissingPermission")
    private fun actualizarResumen(context: Context) {
        if (!permitidas(context)) return
        val gestor = context.getSystemService(NotificationManager::class.java)
        val enBandeja = gestor.activeNotifications
            .filter { it.notification.group == GRUPO && it.tag != ETIQUETA_RESUMEN }
        if (enBandeja.size < 2) {
            NotificationManagerCompat.from(context).cancel(ETIQUETA_RESUMEN, 0)
            return
        }
        val textos = Idioma.envolver(context)
        val titulo = textos.resources.getQuantityString(R.plurals.resumen_avisos, enBandeja.size, enBandeja.size)
        val estilo = NotificationCompat.InboxStyle().setBigContentTitle(titulo)
        val nombres = enBandeja.mapNotNull { it.notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE) }
        nombres.forEach { estilo.addLine(it) }
        val abrir = PendingIntent.getActivity(
            context,
            0,
            Enlaces.personas(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val resumen = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(ContextCompat.getColor(context, R.color.teja))
            .setContentTitle(titulo)
            .setContentText(nombres.joinToString(", "))
            .setStyle(estilo)
            .setGroup(GRUPO)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ETIQUETA_RESUMEN, 0, resumen)
    }

    /** Numero y medio viajan en el intent para no esperar a la base de datos al tocar. */
    private fun contactar(context: Context, contacto: Contacto, cumpleanos: Boolean): PendingIntent {
        val intent = Intent(context, NotificacionPulsadaActivity::class.java).apply {
            // La accion distingue el del cumpleanos del normal: si no, compartirian PendingIntent.
            action = if (cumpleanos) ACCION_CUMPLEANOS else ACCION_AVISO
            putExtra(NotificacionPulsadaActivity.EXTRA_ID, contacto.id)
            putExtra(NotificacionPulsadaActivity.EXTRA_TELEFONO, contacto.telefono)
            putExtra(NotificacionPulsadaActivity.EXTRA_MEDIO, contacto.medio.name)
        }
        // requestCode por contacto: con uno fijo, FLAG_UPDATE_CURRENT haria que
        // todas las notificaciones abiertas irian al ultimo contacto.
        return PendingIntent.getActivity(
            context,
            contacto.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Abre la app en la ficha del contacto, por su enlace profundo. */
    private fun abrirFicha(context: Context, id: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            id.toInt(),
            Enlaces.ficha(context, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Quita los avisos de un contacto (el normal y el del cumpleanos) si siguen en la bandeja. */
    fun quitar(context: Context, id: Long) {
        val gestor = NotificationManagerCompat.from(context)
        gestor.cancel(id.toInt())
        gestor.cancel(ETIQUETA_CUMPLEANOS, id.toInt())
        actualizarResumen(context)
    }

    private const val ACCION_AVISO = "com.example.recuerdallamar.AVISO"
    private const val ACCION_CUMPLEANOS = "com.example.recuerdallamar.CUMPLEANOS"
}
