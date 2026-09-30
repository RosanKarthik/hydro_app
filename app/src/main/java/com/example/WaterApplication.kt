package com.example

import android.app.Application
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.worker.AdaptiveQuantityWorker
import java.util.concurrent.TimeUnit

class WaterApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        try {
            scheduleAdaptiveQuantityWorker()
        } catch (e: Exception) {
            // Prevent crashes in testing environments
        }
    }

    private fun scheduleAdaptiveQuantityWorker() {
        val workRequest = PeriodicWorkRequestBuilder<AdaptiveQuantityWorker>(
            24, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ADAPTIVE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    companion object {
        const val ADAPTIVE_WORK_NAME = "adaptive_quantity_periodic_work"
    }
}
