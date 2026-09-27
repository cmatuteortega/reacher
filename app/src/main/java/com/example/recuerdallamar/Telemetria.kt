package com.example.recuerdallamar

import android.app.Application
import com.example.recuerdallamar.datos.AlmacenAjustes
import com.posthog.PersonProfiles
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Lo unico que sale del telefono, y solo con permiso:
 *
 * - Informes de fallos (Sentry): cierres, ANR y los [Registro.fallo]. Activados
 *   por defecto; se apagan en Ajustes > Privacidad.
 * - Estadisticas de uso (PostHog, servidores en la UE): los [Evento] de abajo.
 *   Apagadas hasta que el usuario acepta.
 *
 * Nada de lo que hay en la app: ni nombres, ni telefonos, ni notas. Sin claves
 * (BuildConfig vacio: compilaciones locales y PR) no se arranca ningun SDK.
 */
object Telemetria {
    private const val POSTHOG_UE = "https://eu.i.posthog.com"

    /** Lo que se cuenta. El nombre es el que se ve en PostHog: no cambiarlo a la ligera. */
    enum class Evento(val nombre: String) {
        BIENVENIDA_TERMINADA("onboarding_completed"),
        PERSONA_ANADIDA("person_added"),
        PERSONA_ELIMINADA("person_deleted"),
        PERSONA_PAUSADA("person_paused"),
        PERSONA_REANUDADA("person_resumed"),
        CONTACTO_HECHO("contact_made"),
        AVISO_MOSTRADO("reminder_shown"),
        AVISO_POSPUESTO("reminder_snoozed"),
        AVISO_DESCARTADO("reminder_dismissed"),
        VISTA_CAMBIADA("view_changed"),
        COPIA_EXPORTADA("backup_exported"),
        COPIA_IMPORTADA("backup_imported"),
    }

    private val hayPostHog get() = BuildConfig.POSTHOG_KEY.isNotBlank()
    private val haySentry get() = BuildConfig.SENTRY_DSN.isNotBlank()

    /** En App.onCreate, antes que nada: asi se recogen tambien los fallos del arranque. */
    fun iniciar(app: Application, ambito: CoroutineScope) {
        val almacen = AlmacenAjustes.de(app)
        val ajustes = almacen.ajustes.value
        if (haySentry && ajustes.informesFallos) arrancarSentry(app)
        if (hayPostHog) arrancarPostHog(app, aceptadas = ajustes.estadisticas == true)

        // Cambios en Ajustes > Privacidad, al momento y sin reiniciar la app.
        almacen.ajustes
            .map { it.informesFallos }
            .distinctUntilChanged()
            .drop(1)
            .onEach { si ->
                if (!haySentry) return@onEach
                if (si) arrancarSentry(app) else Sentry.close()
            }
            .launchIn(ambito)
        almacen.ajustes
            .map { it.estadisticas == true }
            .distinctUntilChanged()
            .drop(1)
            .onEach { si ->
                if (!hayPostHog) return@onEach
                if (si) {
                    PostHog.optIn()
                } else {
                    PostHog.optOut()
                    // Olvida el id anonimo: si vuelve a aceptar, es otro.
                    PostHog.reset()
                }
            }
            .launchIn(ambito)
    }

    private fun arrancarSentry(app: Application) {
        SentryAndroid.init(app) { opciones ->
            opciones.dsn = BuildConfig.SENTRY_DSN
            opciones.environment = if (BuildConfig.DEBUG) "debug" else "production"
            // Ni IP, ni capturas de pantalla, ni el arbol de vistas (lleva los
            // nombres de la gente), ni que se ha tocado.
            opciones.isSendDefaultPii = false
            opciones.isAttachScreenshot = false
            opciones.isAttachViewHierarchy = false
            opciones.isEnableUserInteractionBreadcrumbs = false
            opciones.isEnableUserInteractionTracing = false
        }
    }

    private fun arrancarPostHog(app: Application, aceptadas: Boolean) {
        val config = PostHogAndroidConfig(apiKey = BuildConfig.POSTHOG_KEY, host = POSTHOG_UE).apply {
            optOut = !aceptadas
            // Eventos sin persona detras: nada de perfiles.
            personProfiles = PersonProfiles.NEVER
            // Abrir y cerrar la app (y actualizarla) si; las pantallas las
            // cuenta la app con sus rutas, no con el nombre de la Activity.
            captureApplicationLifecycleEvents = true
            captureScreenViews = false
            captureDeepLinks = false
            captureElementInteractions = false
            // Sin flags ni configuracion remota: ninguna peticion que no sea mandar eventos.
            preloadFeatureFlags = false
            remoteConfig = false
            sendFeatureFlagEvent = false
            debug = BuildConfig.DEBUG
        }
        PostHogAndroid.setup(app, config)
    }

    /** [datos]: solo enumerados y numeros, nunca nada que escriba el usuario. */
    fun evento(evento: Evento, vararg datos: Pair<String, Any>) {
        if (!hayPostHog) return
        PostHog.capture(evento.nombre, properties = datos.toMap())
    }

    /** La pantalla de Navigation ("Personas", "Ficha"...); sin el id de la persona. */
    fun pantalla(nombre: String) {
        if (!hayPostHog) return
        PostHog.screen(nombre)
    }

    /** Cuanta gente hay, para cruzarlo con el resto de eventos. */
    fun personas(cuantas: Int) {
        if (!hayPostHog) return
        PostHog.register("people_count", cuantas)
    }
}
