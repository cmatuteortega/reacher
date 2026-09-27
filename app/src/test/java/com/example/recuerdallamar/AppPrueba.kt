package com.example.recuerdallamar

import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.recuerdallamar.datos.AlmacenAjustes
import com.example.recuerdallamar.datos.BaseDatos

/**
 * La aplicacion de las pruebas con Robolectric (robolectric.properties): la
 * misma App, pero con un WorkManager de pruebas y sin lo que quedo de la
 * prueba anterior en los singletons. Robolectric da a cada prueba una
 * aplicacion y un disco nuevos; los objetos estaticos, en cambio, siguen ahi.
 */
class AppPrueba : App() {
    override fun onCreate() {
        BaseDatos.olvidar()
        AlmacenAjustes.olvidar()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            this,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        super.onCreate()
    }
}
