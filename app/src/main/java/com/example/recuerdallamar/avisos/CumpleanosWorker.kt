package com.example.recuerdallamar.avisos

import android.content.Context
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.recuerdallamar.CumpleanosAgenda
import com.example.recuerdallamar.datos.AlmacenAjustes
import com.example.recuerdallamar.datos.BaseDatos
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Una vez al dia, para toda la gente: relee los cumpleanos de la agenda (si
 * hay permiso; alguien pudo apuntarlo despues de darlo de alta) y felicita a
 * quien cumple hoy. Sigue los mismos ajustes que el aviso normal: apagados o
 * en pausa no avisa, y fuera del horario espera a la hora de inicio. Solo
 * avisa una vez por dia aunque corra varias.
 */
class CumpleanosWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val contexto = applicationContext
        val dao = BaseDatos.de(contexto).contactos()
        val hoy = LocalDate.now()

        val gente = dao.lista().map { contacto ->
            val leido = CumpleanosAgenda.leer(contexto, contacto.telefono)
            if (leido != null && leido != contacto.cumpleanos) {
                dao.actualizarCumpleanos(contacto.id, leido)
                contacto.copy(cumpleanos = leido)
            } else {
                contacto
            }
        }

        val preferencias = contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)
        if (preferencias.getString(AVISADO_EL, null) == hoy.toString()) return Result.success()
        val ajustes = AlmacenAjustes.de(contexto).ajustes.value
        if (!ajustes.avisosEncendidos(hoy)) return Result.success()
        val espera = ajustes.esperaHastaFranja()
        if (!espera.isZero) {
            aplazar(contexto, espera.toMinutes() + 1)
            return Result.success()
        }

        gente.filter { it.esCumpleanos(hoy) && !it.pausado() }
            .forEach { Notificaciones.mostrarCumpleanos(contexto, it) }
        preferencias.edit { putString(AVISADO_EL, hoy.toString()) }
        return Result.success()
    }

    companion object {
        private const val PREFERENCIAS = "cumpleanos"
        private const val AVISADO_EL = "avisado_el"
        private const val DIARIO = "cumpleanos"
        private const val APLAZADO = "cumpleanos-aplazado"

        /** Al arrancar la app. KEEP: si ya estaba programado, no se reinicia el ciclo. */
        fun programar(context: Context) {
            val peticion = PeriodicWorkRequestBuilder<CumpleanosWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(DIARIO, ExistingPeriodicWorkPolicy.KEEP, peticion)
        }

        private fun aplazar(context: Context, minutos: Long) {
            val peticion = OneTimeWorkRequestBuilder<CumpleanosWorker>()
                .setInitialDelay(minutos, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(APLAZADO, ExistingWorkPolicy.REPLACE, peticion)
        }
    }
}
