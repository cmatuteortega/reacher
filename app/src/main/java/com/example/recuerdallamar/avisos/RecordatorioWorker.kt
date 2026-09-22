package com.example.recuerdallamar.avisos

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.recuerdallamar.datos.BaseDatos
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Comprueba un contacto y avisa si toca llamarle. Hay un trabajo periodico
 * diario por contacto; la prueba de depuracion usa el mismo worker con
 * FORZAR, asi que ejercita exactamente el mismo camino que el aviso real.
 */
class RecordatorioWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(CLAVE_ID, -1)
        // Si el contacto ya no existe no hay nada que reintentar.
        val contacto = BaseDatos.de(applicationContext).contactos().buscar(id)
            ?: return Result.success()

        val forzar = inputData.getBoolean(CLAVE_FORZAR, false)
        if (forzar || contacto.tocaLlamar(LocalDate.now())) {
            Notificaciones.mostrar(applicationContext, contacto)
        }
        return Result.success()
    }

    companion object {
        private const val CLAVE_ID = "id"
        private const val CLAVE_FORZAR = "forzar"

        /**
         * Se llama al guardar o actualizar. CANCEL_AND_REENQUEUE reinicia el
         * ciclo: la primera comprobacion corre ya, con la frecuencia nueva, y
         * las siguientes cada 24 h desde ahi.
         */
        fun programar(context: Context, id: Long) {
            val peticion = PeriodicWorkRequestBuilder<RecordatorioWorker>(1, TimeUnit.DAYS)
                .setInputData(workDataOf(CLAVE_ID to id))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "recordatorio-$id",
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                peticion,
            )
        }

        /** Boton de depuracion: dispara la notificacion ya, toque o no toque. */
        fun forzar(context: Context, id: Long) {
            val peticion = OneTimeWorkRequestBuilder<RecordatorioWorker>()
                .setInputData(workDataOf(CLAVE_ID to id, CLAVE_FORZAR to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "prueba-$id",
                ExistingWorkPolicy.REPLACE,
                peticion,
            )
        }
    }
}
