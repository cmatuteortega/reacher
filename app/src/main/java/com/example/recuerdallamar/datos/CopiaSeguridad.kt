package com.example.recuerdallamar.datos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay

/**
 * Copia en un archivo JSON para cambiar de telefono a mano (la copia de
 * Android se hace sola, ver res/xml/reglas_copia.xml). Lleva la gente y los
 * ajustes que tienen sentido en otro telefono; no lo que es de un aviso en
 * curso (descartes, "Mas tarde", notificacion pulsada) ni el idioma de depuracion.
 *
 * Las fechas van como texto ISO, para que el archivo se entienda a simple
 * vista. Los campos que falten toman su valor por defecto y los que sobren se
 * ignoran: una copia de una version mas nueva de la app se sigue pudiendo abrir.
 */
object CopiaSeguridad {
    /** Sube solo si cambia el significado de algun campo, no al anadir campos. */
    const val FORMATO = 1

    private const val APP = "contacto"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun escribir(gente: List<Contacto>, ajustes: Ajustes, ahora: LocalDateTime = LocalDateTime.now()): String =
        json.encodeToString(
            Archivo.serializer(),
            Archivo(
                app = APP,
                formato = FORMATO,
                exportado = ahora.withNano(0).toString(),
                ajustes = AjustesCopia.de(ajustes),
                personas = gente.map(PersonaCopia::de),
            ),
        )

    /** Lo leido de un archivo. Lanza [ArchivoNoValido] si no es una copia de esta app. */
    fun leer(texto: String): Leido {
        val archivo = try {
            json.decodeFromString(Archivo.serializer(), texto)
        } catch (e: IllegalArgumentException) {
            // SerializationException es un IllegalArgumentException.
            throw ArchivoNoValido(e)
        }
        if (archivo.app != APP || archivo.formato > FORMATO) throw ArchivoNoValido(null)
        return Leido(
            personas = archivo.personas.mapNotNull { it.aContacto() },
            ajustes = archivo.ajustes,
        )
    }

    class Leido internal constructor(val personas: List<Contacto>, private val ajustes: AjustesCopia?) {
        /** Los ajustes de ahora con los del archivo encima; sin ajustes en el archivo, los de ahora. */
        fun aplicarA(actuales: Ajustes): Ajustes = ajustes?.aplicarA(actuales) ?: actuales
    }

    class ArchivoNoValido(causa: Throwable?) : Exception(causa)

    /** Solo las cifras (y el + inicial): el mismo numero escrito con o sin espacios es la misma persona. */
    fun mismoTelefono(a: String, b: String): Boolean {
        val na = normalizar(a)
        return na.isNotEmpty() && na == normalizar(b)
    }

    private fun normalizar(telefono: String): String {
        val cifras = telefono.filter { it.isDigit() }
        return if (telefono.trimStart().startsWith("+")) "+$cifras" else cifras
    }
}

@Serializable
internal class Archivo(
    val app: String = "",
    val formato: Int = 0,
    val exportado: String? = null,
    val ajustes: AjustesCopia? = null,
    val personas: List<PersonaCopia> = emptyList(),
)

@Serializable
internal class PersonaCopia(
    val nombre: String,
    val telefono: String,
    @SerialName("frecuencia_dias") val frecuenciaDias: Int = 7,
    @SerialName("ultimo_contacto") val ultimoContacto: String? = null,
    val medio: String? = null,
    val notas: String = "",
    val cumpleanos: String? = null,
    @SerialName("cumpleanos_manual") val cumpleanosManual: Boolean = false,
    val circulo: String? = null,
    @SerialName("pausado_hasta") val pausadoHasta: String? = null,
) {
    /** null si no hay nombre ni telefono: no es nadie a quien avisar. */
    fun aContacto(): Contacto? {
        if (nombre.isBlank() && telefono.isBlank()) return null
        return Contacto(
            nombre = nombre,
            telefono = telefono,
            frecuenciaDias = frecuenciaDias.coerceAtLeast(1),
            ultimoContacto = ultimoContacto.fecha() ?: LocalDate.now(),
            medio = MedioContacto.desde(medio),
            notas = notas,
            cumpleanos = cumpleanos?.let { runCatching { MonthDay.parse(it) }.getOrNull() },
            cumpleanosManual = cumpleanosManual,
            circulo = circulo?.trim()?.takeIf { it.isNotEmpty() },
            pausadoHasta = pausadoHasta?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
        )
    }

    companion object {
        fun de(c: Contacto) = PersonaCopia(
            nombre = c.nombre,
            telefono = c.telefono,
            frecuenciaDias = c.frecuenciaDias,
            ultimoContacto = c.ultimoContacto.toString(),
            medio = c.medio.name,
            notas = c.notas,
            cumpleanos = c.cumpleanos?.toString(),
            cumpleanosManual = c.cumpleanosManual,
            circulo = c.circulo,
            pausadoHasta = c.pausadoHasta?.toString(),
        )
    }
}

/** Todos opcionales: lo que no venga se queda como esta en este telefono. */
@Serializable
internal class AjustesCopia(
    @SerialName("avisos_activos") val avisosActivos: Boolean? = null,
    @SerialName("pausa_hasta") val pausaHasta: String? = null,
    @SerialName("hora_desde") val horaDesde: Int? = null,
    @SerialName("hora_hasta") val horaHasta: Int? = null,
    @SerialName("horas_posponer") val horasPosponer: Int? = null,
    @SerialName("medio_por_defecto") val medioPorDefecto: String? = null,
    val tema: String? = null,
    val vista: String? = null,
) {
    fun aplicarA(a: Ajustes): Ajustes {
        val activos = avisosActivos ?: a.avisosActivos
        return a.copy(
            avisosActivos = activos,
            // Una pausa solo tiene sentido con los avisos apagados.
            pausaHasta = if (avisosActivos == null) a.pausaHasta else pausaHasta.fecha().takeIf { !activos },
            horaDesde = horaDesde?.coerceIn(0, 23) ?: a.horaDesde,
            horaHasta = horaHasta?.coerceIn(0, 23) ?: a.horaHasta,
            horasPosponer = horasPosponer?.coerceIn(1, 24) ?: a.horasPosponer,
            medioPorDefecto = medioPorDefecto?.let(MedioContacto::desde) ?: a.medioPorDefecto,
            tema = TemaElegido.entries.firstOrNull { it.name == tema } ?: a.tema,
            vista = VistaPersonas.entries.firstOrNull { it.name == vista } ?: a.vista,
        )
    }

    companion object {
        fun de(a: Ajustes) = AjustesCopia(
            avisosActivos = a.avisosActivos,
            pausaHasta = a.pausaHasta?.toString(),
            horaDesde = a.horaDesde,
            horaHasta = a.horaHasta,
            horasPosponer = a.horasPosponer,
            medioPorDefecto = a.medioPorDefecto.name,
            tema = a.tema.name,
            vista = a.vista.name,
        )
    }
}

private fun String?.fecha(): LocalDate? = this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
