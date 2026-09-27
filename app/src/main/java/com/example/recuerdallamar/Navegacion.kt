package com.example.recuerdallamar

import kotlinx.serialization.Serializable

/**
 * Pantallas de la app como rutas de Navigation Compose. Personas es la base y
 * las demas se abren encima; la pila la guarda Navigation, asi que sobrevive a
 * girar la pantalla y a que Android cierre el proceso en segundo plano.
 */
object Ruta {
    @Serializable
    data object Bienvenida

    /** Enlace: contacto://personas */
    @Serializable
    data object Personas

    @Serializable
    data object Ajustes

    /** Alguien ya guardado. Enlace: contacto://ficha/{id} */
    @Serializable
    data class Ficha(val id: Long)

    /** Alta de alguien recien elegido de la agenda (el borrador vive en el ViewModel). */
    @Serializable
    data object Nueva
}
