package com.jeal.smartwidget.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jeal.smartwidget.analytics.AppCatalog
import com.jeal.smartwidget.analytics.UsageIngestor
import com.jeal.smartwidget.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

/**
 * Única tarea de fondo: apps instaladas → lectura incremental de uso → recomendaciones → widgets.
 * Periódica cada 30 min (sin servicios permanentes ni sondeo constante). WorkManager la reprograma
 * sola después de reiniciar el teléfono.
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AppCatalog.sync(applicationContext)
        UsageIngestor.ingest(applicationContext)
        WidgetUpdater.updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "refresh-periodic"
        private const val NOW = "refresh-now"
        private const val SOON = "refresh-soon"

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES).build(),
            )
        }

        fun cancelPeriodic(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RefreshWorker>().build(),
            )
        }

        /** Tras abrir una app desde el widget: agrupa los toques y actualiza en ~1 min. */
        fun refreshSoon(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                SOON, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RefreshWorker>().setInitialDelay(60, TimeUnit.SECONDS).build(),
            )
        }
    }
}
