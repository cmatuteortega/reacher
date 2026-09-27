package com.example.recuerdallamar

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.edit
import java.util.Locale

/**
 * El ahorro de bateria es lo que mas avisos se come: Android retrasa los
 * trabajos de las apps "optimizadas", y varios fabricantes (Xiaomi, Huawei,
 * Samsung, Oppo...) ademas matan la app y no dejan que vuelva a arrancar
 * sola. Aqui se mira como esta la app y se abre el ajuste que lo arregla.
 *
 * No se usa REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (el dialogo directo): Google
 * Play solo lo permite a unos pocos tipos de app. Se abre la pantalla de
 * ajustes y el usuario lo cambia ahi.
 */
object Bateria {
    enum class Estado {
        /** Exenta del ahorro de bateria: los avisos llegan a su hora. */
        LIBRE,

        /** Lo normal: Android puede retrasar los avisos (Doze). */
        OPTIMIZADA,

        /** "Restringida" en los ajustes de la app (Android 9+): puede que no lleguen. */
        RESTRINGIDA,
    }

    fun estado(context: Context): Estado {
        val actividad = context.getSystemService(ActivityManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && actividad?.isBackgroundRestricted == true) {
            return Estado.RESTRINGIDA
        }
        val energia = context.getSystemService(PowerManager::class.java)
        return if (energia?.isIgnoringBatteryOptimizations(context.packageName) == true) Estado.LIBRE else Estado.OPTIMIZADA
    }

    private val fabricante: String = Build.MANUFACTURER.orEmpty().lowercase(Locale.ROOT)

    /** "Xiaomi", "Samsung"... para los textos. */
    val nombreFabricante: String = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.titlecase(Locale.ROOT) }

    /** Fabricantes que cierran apps en segundo plano mas alla de lo que hace Android. */
    val fabricanteEstricto: Boolean get() = fabricante in ESTRICTOS

    /**
     * Merece la pena decir algo: siempre si esta restringida; optimizada, solo
     * con los fabricantes estrictos (en el resto Android la despierta a diario igual).
     */
    fun conviene(context: Context): Boolean = when (estado(context)) {
        Estado.RESTRINGIDA -> true
        Estado.OPTIMIZADA -> fabricanteEstricto
        Estado.LIBRE -> false
    }

    /**
     * La pantalla donde se quita la restriccion. Desde Android 12 (y siempre
     * que este restringida) es la de la propia app, que tiene "Bateria >
     * Sin restricciones"; antes, la lista de optimizacion de bateria.
     */
    fun abrirAjustes(context: Context): Boolean {
        val deLaApp = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        val lista = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val orden = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || estado(context) == Estado.RESTRINGIDA) {
            listOf(deLaApp, lista)
        } else {
            listOf(lista, deLaApp)
        }
        return orden.any { abrir(context, it) }
    }

    /** Hay pantalla de "inicio automatico" propia del fabricante (Xiaomi, Huawei, Oppo, Vivo, Asus). */
    val hayAutoarranque: Boolean get() = autoarranque().isNotEmpty()

    /** Abre la primera pantalla de inicio automatico que exista en este telefono. */
    fun abrirAutoarranque(context: Context): Boolean =
        autoarranque().any { abrir(context, Intent().setComponent(it)) }

    /** Instrucciones paso a paso para este fabricante en dontkillmyapp.com. */
    fun abrirGuia(context: Context): Boolean {
        val pagina = GUIAS.entries.firstOrNull { (marcas, _) -> fabricante in marcas }?.value.orEmpty()
        return abrir(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com/$pagina")))
    }

    // ------- Aviso de una sola vez -------

    // Aparte de los ajustes: no va en la copia de seguridad, porque en otro
    // telefono la bateria puede estar distinta y hay que volver a avisar.
    private const val PREFERENCIAS = "bateria"
    private const val AVISADO = "avisado"

    fun avisado(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE).getBoolean(AVISADO, false)

    fun marcarAvisado(context: Context) {
        context.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE).edit { putBoolean(AVISADO, true) }
    }

    private fun autoarranque(): List<ComponentName> = when (fabricante) {
        "xiaomi", "redmi", "poco" -> listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        "huawei", "honor" -> listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )
        "oppo", "realme", "oneplus" -> listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        )
        "vivo", "iqoo" -> listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        )
        "asus" -> listOf(
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
        )
        else -> emptyList()
    }

    // Las pantallas de otros fabricantes cambian de nombre entre versiones, o
    // no se dejan abrir desde fuera: si falla, se prueba la siguiente.
    private fun abrir(context: Context, intent: Intent): Boolean =
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }

    private val ESTRICTOS = setOf(
        "xiaomi", "redmi", "poco", "huawei", "honor", "samsung", "oneplus", "oppo", "realme",
        "vivo", "iqoo", "asus", "meizu", "nokia", "hmd global", "sony", "lenovo", "wiko",
        "tecno", "infinix", "blackview", "unihertz",
    )

    /** Pagina de dontkillmyapp.com de cada familia de marcas; sin la suya, la portada. */
    private val GUIAS: Map<Set<String>, String> = mapOf(
        setOf("xiaomi", "redmi", "poco") to "xiaomi",
        setOf("huawei", "honor") to "huawei",
        setOf("samsung") to "samsung",
        setOf("oneplus") to "oneplus",
        setOf("oppo") to "oppo",
        setOf("realme") to "realme",
        setOf("vivo", "iqoo") to "vivo",
        setOf("asus") to "asus",
        setOf("meizu") to "meizu",
        setOf("nokia", "hmd global") to "nokia",
        setOf("sony") to "sony",
        setOf("lenovo") to "lenovo",
        setOf("wiko") to "wiko",
        setOf("blackview") to "blackview",
        setOf("unihertz") to "unihertz",
    )
}
