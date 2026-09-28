package com.example.recuerdallamar

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Actualizaciones desde dentro de la app (Google Play), para que una version
 * rota no se quede semanas en los telefonos:
 *
 * - Normal: si hay version nueva desde hace unos dias, Play ofrece bajarla
 *   (flexible) una sola vez por version. Se baja mientras se usa la app y se
 *   instala al salir de ella, sin cortar a nadie: no hace falta ningun aviso
 *   propio.
 * - Urgente: con prioridad [PRIORIDAD_INMEDIATA] o mas, pantalla completa de
 *   Play hasta que se instala (inmediata). La prioridad solo se pone al subir
 *   la version con la API de Play (fastlane `supply`), no desde Play Console.
 *
 * Sin Play Store (emulador, version de depuracion, instalada a mano) no hace
 * nada.
 */
class Actualizacion(
    private val activity: ComponentActivity,
    private val crearGestor: (Context) -> AppUpdateManager = AppUpdateManagerFactory::create,
    private val activa: Boolean = !BuildConfig.DEBUG,
) : DefaultLifecycleObserver {

    /** Que hacer con lo que dice Play al volver a la app. */
    enum class Paso { NADA, FLEXIBLE, INMEDIATA }

    private val gestor by lazy { crearGestor(activity) }

    private val lanzador = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        Registro.info("actualizacion", "respuesta", "codigo" to it.resultCode)
    }

    /** Bajada flexible terminada: se instala en cuanto la app pasa a segundo plano. */
    private var descargada = false

    private val oyente = InstallStateUpdatedListener { estado ->
        if (estado.installStatus() == InstallStatus.DOWNLOADED) {
            descargada = true
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) instalar()
        }
    }

    init {
        if (activa) activity.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        runCatching { gestor.registerListener(oyente) }
            .onFailure { Registro.fallo("actualizacion", it) }
    }

    override fun onResume(owner: LifecycleOwner) {
        runCatching { comprobar() }
            .onFailure { Registro.fallo("actualizacion", it) }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (descargada) instalar()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        runCatching { gestor.unregisterListener(oyente) }
    }

    private fun comprobar() {
        gestor.appUpdateInfo
            .addOnSuccessListener { info ->
                if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@addOnSuccessListener
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    descargada = true
                    return@addOnSuccessListener
                }
                val paso = paso(info, ofrecida(activity))
                if (paso == Paso.NADA) return@addOnSuccessListener
                val tipo = if (paso == Paso.INMEDIATA) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE
                if (paso == Paso.FLEXIBLE) marcarOfrecida(activity, info.availableVersionCode())
                Registro.info("actualizacion", "ofrecida", "tipo" to paso.name, "version" to info.availableVersionCode())
                gestor.startUpdateFlowForResult(info, lanzador, AppUpdateOptions.newBuilder(tipo).build())
            }
            .addOnFailureListener { Registro.info("actualizacion", "sin play", "error" to it.javaClass.simpleName) }
    }

    private fun instalar() {
        descargada = false
        Registro.info("actualizacion", "instalar")
        runCatching { gestor.completeUpdate() }
            .onFailure { Registro.fallo("actualizacion", it) }
    }

    companion object {
        /** Prioridad (0-5, la pone quien sube la version) desde la que no se puede esperar. */
        const val PRIORIDAD_INMEDIATA = 4

        /** Prioridad desde la que se ofrece en cuanto sale, sin esperar [DIAS_PARA_OFRECER]. */
        const val PRIORIDAD_SIN_ESPERA = 2

        /** Dias que Play lleva sabiendo de la version nueva antes de ofrecerla. */
        const val DIAS_PARA_OFRECER = 3

        private const val PREFERENCIAS = "actualizacion"
        private const val OFRECIDA = "version_ofrecida"

        private fun paso(info: AppUpdateInfo, ofrecida: Int): Paso = paso(
            disponible = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE,
            enCurso = info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
            version = info.availableVersionCode(),
            prioridad = info.updatePriority(),
            dias = info.clientVersionStalenessDays(),
            flexiblePermitida = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
            inmediataPermitida = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
            ofrecida = ofrecida,
        )

        /**
         * La decision, sin Play de por medio. [enCurso] es una inmediata que se
         * quedo a medias (la app se cerro): se retoma. [ofrecida] es la ultima
         * version que se ofrecio en flexible; si dijo que no, no se insiste.
         */
        fun paso(
            disponible: Boolean,
            enCurso: Boolean,
            version: Int,
            prioridad: Int,
            dias: Int?,
            flexiblePermitida: Boolean,
            inmediataPermitida: Boolean,
            ofrecida: Int,
        ): Paso = when {
            (disponible || enCurso) && inmediataPermitida && prioridad >= PRIORIDAD_INMEDIATA -> Paso.INMEDIATA
            !disponible || !flexiblePermitida || version <= ofrecida -> Paso.NADA
            prioridad >= PRIORIDAD_SIN_ESPERA || (dias ?: 0) >= DIAS_PARA_OFRECER -> Paso.FLEXIBLE
            else -> Paso.NADA
        }

        private fun ofrecida(context: Context) =
            context.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE).getInt(OFRECIDA, 0)

        private fun marcarOfrecida(context: Context, version: Int) {
            context.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE).edit { putInt(OFRECIDA, version) }
        }
    }
}
