package com.example.recuerdallamar

import android.content.Context
import android.content.res.Configuration
import com.example.recuerdallamar.datos.AlmacenAjustes
import java.util.Locale

/**
 * Pone el idioma elegido en Ajustes (o el del telefono) a un contexto. Lo usan
 * las actividades al crearse y lo que habla fuera de ellas: la notificacion,
 * que sale del worker, y el aviso de "no hay app" del trampolin.
 *
 * Tambien cambia el Locale por defecto, que es el que usan las fechas.
 */
object Idioma {

    fun envolver(base: Context): Context {
        val elegido = AlmacenAjustes.de(base).ajustes.value.idioma.codigo
        val configuracion = Configuration(base.resources.configuration)
        val delTelefono = configuracion.locales[0]
        if (elegido == null) {
            Locale.setDefault(delTelefono)
            return base
        }
        // Se conserva el pais del telefono: da el formato de las fechas y el
        // prefijo de los numeros sin SIM (ver Contactar.internacional).
        val locale = Locale.Builder().setLanguage(elegido).setRegion(delTelefono.country).build()
        Locale.setDefault(locale)
        configuracion.setLocale(locale)
        return base.createConfigurationContext(configuracion)
    }
}
