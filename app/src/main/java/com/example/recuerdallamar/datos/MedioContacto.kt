package com.example.recuerdallamar.datos

/**
 * Como se prefiere contactar con alguien. Decide que abre la notificacion y
 * el boton de la ficha. Se guarda por nombre: no renombrar las constantes.
 */
enum class MedioContacto(
    val etiqueta: String,
    /** Texto corto del boton de la ficha. */
    val boton: String,
) {
    MARCADOR("Abrir el marcador", "Llamar"),
    LLAMADA("Llamar directamente", "Llamar"),
    WHATSAPP("WhatsApp", "WhatsApp"),
    SMS("Mensaje (SMS)", "Mensaje"),
    TELEGRAM("Telegram", "Telegram"),
    ;

    /** Texto de la notificacion. */
    fun aviso(nombre: String): String = when (this) {
        MARCADOR, LLAMADA -> "Toca para llamar a $nombre"
        WHATSAPP -> "Toca para escribir a $nombre por WhatsApp"
        SMS -> "Toca para mandar un mensaje a $nombre"
        TELEGRAM -> "Toca para escribir a $nombre por Telegram"
    }

    companion object {
        /** Un nombre desconocido o ausente vuelve al marcador, lo de antes. */
        fun desde(nombre: String?): MedioContacto = entries.firstOrNull { it.name == nombre } ?: MARCADOR
    }
}
