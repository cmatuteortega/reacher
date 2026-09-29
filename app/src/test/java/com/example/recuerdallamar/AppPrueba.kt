package com.example.recuerdallamar

import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.recuerdallamar.datos.AlmacenAjustes
import com.example.recuerdallamar.datos.BaseDatos
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * La aplicacion de las pruebas con Robolectric (robolectric.properties): la
 * misma App, pero con un WorkManager de pruebas y sin lo que quedo de la
 * prueba anterior en los singletons. Robolectric da a cada prueba una
 * aplicacion y un disco nuevos; los objetos estaticos, en cambio, siguen ahi.
 */
class AppPrueba : App() {
    override fun onCreate() {
        // La App de la prueba anterior sigue leyendo la base desde su ambito (el
        // widget, la telemetria): pararla antes de cerrar la base, o su consulta
        // falla con "connection pool has been closed" dentro de otra prueba.
        anterior?.let { runBlocking { withTimeoutOrNull(5_000) { it.ambito.coroutineContext.job.cancelAndJoin() } } }
        anterior = this
        BaseDatos.olvidar()
        AlmacenAjustes.olvidar()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            this,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        super.onCreate()
    }

    private companion object {
        var anterior: AppPrueba? = null
    }
}
