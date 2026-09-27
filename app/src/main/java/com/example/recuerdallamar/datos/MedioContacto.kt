package com.example.recuerdallamar.datos

import androidx.annotation.StringRes
import com.example.recuerdallamar.R

/**
 * Como se prefiere contactar con alguien. Decide que abre la notificacion y
 * el boton de la ficha. Se guarda por nombre: no renombrar las constantes.
 */
enum class MedioContacto(
    @StringRes val etiqueta: Int,
    /** Texto corto del boton de la ficha. */
    @StringRes val boton: Int,
    /** Texto de la notificacion; lleva el nombre de la persona. */
    @StringRes val aviso: Int,
) {
    MARCADOR(R.string.medio_marcador, R.string.boton_llamar, R.string.aviso_llamar),
    LLAMADA(R.string.medio_llamada, R.string.boton_llamar, R.string.aviso_llamar),
    WHATSAPP(R.string.whatsapp, R.string.whatsapp, R.string.aviso_whatsapp),
    SMS(R.string.medio_sms, R.string.boton_mensaje, R.string.aviso_sms),
    TELEGRAM(R.string.telegram, R.string.telegram, R.string.aviso_telegram),
    ;

    companion object {
        /** Un nombre desconocido o ausente vuelve al marcador, lo de antes. */
        fun desde(nombre: String?): MedioContacto = entries.firstOrNull { it.name == nombre } ?: MARCADOR
    }
}
