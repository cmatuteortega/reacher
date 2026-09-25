package com.example.recuerdallamar.datos

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class TemaElegido(val etiqueta: String) {
    SISTEMA("Sistema"),
    CLARO("Claro"),
    OSCURO("Oscuro"),
}

/** Como se ensena la gente en Personas; los mismos datos y el mismo orden en las dos. */
enum class VistaPersonas {
    BURBUJAS,
    LISTA,
}

/**
 * Preferencias de la app. Los avisos se apagan del todo ([avisosActivos] false
 * y sin fecha) o durante unos dias ([avisosActivos] false hasta [pausaHasta]):
 * pasada esa fecha vuelven solos, sin que nadie tenga que reactivarlos.
 */
data class Ajustes(
    val avisosActivos: Boolean = true,
    /** Primer dia en que vuelven los avisos; null = apagados hasta reactivarlos. */
    val pausaHasta: LocalDate? = null,
    /** Franja del dia en que se puede avisar: desde la hora [horaDesde] hasta la [horaHasta] (sin incluir). */
    val horaDesde: Int = 9,
    val horaHasta: Int = 21,
    /** Cuanto tarda en volver un aviso al pulsar "Mas tarde". */
    val horasPosponer: Int = 2,
    val medioPorDefecto: MedioContacto = MedioContacto.MARCADOR,
    val tema: TemaElegido = TemaElegido.SISTEMA,
    val vista: VistaPersonas = VistaPersonas.BURBUJAS,
    /** La bienvenida ya se vio (o se salto): no se vuelve a ensenar. */
    val bienvenidaHecha: Boolean = false,
) {
    fun avisosEncendidos(hoy: LocalDate = LocalDate.now()): Boolean =
        avisosActivos || (pausaHasta != null && !hoy.isBefore(pausaHasta))

    /** Desde == hasta es todo el dia; desde > hasta cruza la medianoche (p. ej. 20 a 8). */
    fun dentroDeFranja(hora: Int): Boolean = when {
        horaDesde == horaHasta -> true
        horaDesde < horaHasta -> hora in horaDesde until horaHasta
        else -> hora >= horaDesde || hora < horaHasta
    }

    /** Cuanto falta para poder avisar; cero si ya se puede. */
    fun esperaHastaFranja(ahora: LocalDateTime = LocalDateTime.now()): Duration {
        if (dentroDeFranja(ahora.hour)) return Duration.ZERO
        var inicio = ahora.toLocalDate().atTime(LocalTime.of(horaDesde, 0))
        if (!inicio.isAfter(ahora)) inicio = inicio.plusDays(1)
        return Duration.between(ahora, inicio)
    }
}

/**
 * Guarda los ajustes en SharedPreferences y los ofrece como flujo. Una sola
 * instancia por proceso, compartida por la interfaz y el worker.
 */
class AlmacenAjustes private constructor(context: Context) {
    private val preferencias = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    private val _ajustes = MutableStateFlow(leer())
    val ajustes: StateFlow<Ajustes> = _ajustes.asStateFlow()

    fun cambiar(transformar: (Ajustes) -> Ajustes) {
        val nuevos = transformar(_ajustes.value)
        preferencias.edit {
            putBoolean(AVISOS, nuevos.avisosActivos)
            putString(PAUSA, nuevos.pausaHasta?.toString())
            putInt(DESDE, nuevos.horaDesde)
            putInt(HASTA, nuevos.horaHasta)
            putInt(POSPONER, nuevos.horasPosponer)
            putString(MEDIO, nuevos.medioPorDefecto.name)
            putString(TEMA, nuevos.tema.name)
            putString(VISTA, nuevos.vista.name)
            putBoolean(BIENVENIDA, nuevos.bienvenidaHecha)
        }
        _ajustes.value = nuevos
    }

    private fun leer(): Ajustes {
        val defecto = Ajustes()
        return Ajustes(
            avisosActivos = preferencias.getBoolean(AVISOS, defecto.avisosActivos),
            pausaHasta = preferencias.getString(PAUSA, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            horaDesde = preferencias.getInt(DESDE, defecto.horaDesde).coerceIn(0, 23),
            horaHasta = preferencias.getInt(HASTA, defecto.horaHasta).coerceIn(0, 23),
            horasPosponer = preferencias.getInt(POSPONER, defecto.horasPosponer).coerceIn(1, 24),
            medioPorDefecto = MedioContacto.desde(preferencias.getString(MEDIO, null)),
            tema = TemaElegido.entries.firstOrNull { it.name == preferencias.getString(TEMA, null) } ?: defecto.tema,
            vista = VistaPersonas.entries.firstOrNull { it.name == preferencias.getString(VISTA, null) } ?: defecto.vista,
            bienvenidaHecha = preferencias.getBoolean(BIENVENIDA, defecto.bienvenidaHecha),
        )
    }

    companion object {
        private const val AVISOS = "avisos_activos"
        private const val PAUSA = "pausa_hasta"
        private const val DESDE = "hora_desde"
        private const val HASTA = "hora_hasta"
        private const val POSPONER = "horas_posponer"
        private const val MEDIO = "medio_por_defecto"
        private const val TEMA = "tema"
        private const val VISTA = "vista_personas"
        private const val BIENVENIDA = "bienvenida_hecha"

        @Volatile
        private var instancia: AlmacenAjustes? = null

        fun de(context: Context): AlmacenAjustes =
            instancia ?: synchronized(this) {
                instancia ?: AlmacenAjustes(context.applicationContext).also { instancia = it }
            }
    }
}
