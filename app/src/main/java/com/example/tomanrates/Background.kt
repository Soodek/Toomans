package com.example.tomanrates

import android.app.Application
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Scheduler.ensure(this)
    }
}

object Scheduler {
    /**
     * Every 65 minutes: a few minutes above the 60 minute rate limit so the periodic run is never rejected by
     * the local guard. No network constraint on purpose: when offline the worker must still run so the widget
     * can notice that its data is getting old.
     */
    fun ensure(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(65, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("rates_periodic", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** One extra run (e.g. when a widget is first added). Still subject to the hourly guard. */
    fun refreshSoon(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "rates_once", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<RefreshWorker>().build()
        )
    }
}

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        withContext(Dispatchers.IO) { RateRepository.refresh(applicationContext) }
        WidgetRenderer.updateAll(applicationContext)
        return Result.success() // never retry/backoff: retries would burn the rate limit
    }
}
