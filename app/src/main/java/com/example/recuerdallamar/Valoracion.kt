package com.example.recuerdallamar

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import com.google.android.play.core.review.ReviewManagerFactory
import java.time.LocalDate

/**
 * Pedir una valoracion en Google Play, pero solo cuando la app ya ha servido
 * de algo: tras unos cuantos contactos hechos, y como mucho una vez cada
 * varios meses. Google decide ademas si llega a ensenar el dialogo; si no, no
 * pasa nada. Tambien los enlaces de "Valorar" y "Enviar opiniones" de Ajustes.
 */
object Valoracion {
    private const val PREFERENCIAS = "valoracion"
    private const val HECHOS = "contactos_hechos"
    private const val PEDIDA = "pedida_el"

    /** Contactos hechos (aviso tocado o "He llamado hoy") antes de pedirla. */
    private const val HECHOS_PARA_PEDIR = 5
    private const val DIAS_ENTRE_PETICIONES = 120L

    private fun preferencias(context: Context) = context.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)

    fun contactoHecho(context: Context) {
        val p = preferencias(context)
        p.edit { putInt(HECHOS, p.getInt(HECHOS, 0) + 1) }
    }

    fun tocaPedir(context: Context, hoy: LocalDate = LocalDate.now()): Boolean {
        val p = preferencias(context)
        if (p.getInt(HECHOS, 0) < HECHOS_PARA_PEDIR) return false
        val pedida = p.getString(PEDIDA, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return pedida == null || !hoy.isBefore(pedida.plusDays(DIAS_ENTRE_PETICIONES))
    }

    /** Lanza el dialogo de Play si toca. Sin Play Store (o sin red) no hace nada. */
    fun pedirSiToca(activity: Activity) {
        if (!tocaPedir(activity)) return
        preferencias(activity).edit { putString(PEDIDA, LocalDate.now().toString()) }
        val gestor = ReviewManagerFactory.create(activity)
        gestor.requestReviewFlow().addOnCompleteListener { peticion ->
            if (peticion.isSuccessful && !activity.isFinishing) {
                gestor.launchReviewFlow(activity, peticion.result)
            }
        }
    }

    /** La ficha de la app en Play Store, o en el navegador si no esta instalada. */
    fun abrirFichaTienda(context: Context) {
        val paquete = context.packageName
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$paquete")))
        } catch (e: ActivityNotFoundException) {
            abrirSiSePuede(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$paquete")))
        }
    }

    /**
     * Correo de opiniones con la version y el telefono ya puestos, que es lo
     * primero que hace falta para entender un fallo. El destinatario sale de
     * BuildConfig.CORREO_OPINIONES; vacio, se elige en la app de correo.
     */
    fun enviarOpiniones(context: Context, asunto: String): Boolean {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        val cuerpo = "\n\n—\n${context.getString(R.string.app_name)} $version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            if (BuildConfig.CORREO_OPINIONES.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.CORREO_OPINIONES))
            putExtra(Intent.EXTRA_SUBJECT, asunto)
            putExtra(Intent.EXTRA_TEXT, cuerpo)
        }
        return abrirSiSePuede(context, intent)
    }

    private fun abrirSiSePuede(context: Context, intent: Intent): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
}
