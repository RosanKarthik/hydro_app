package com.example

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.worker.AdaptiveQuantityWorker
import java.util.concurrent.TimeUnit

class WaterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Enqueue the Adaptive Quantity Worker to run weekly
        val adaptiveWorkRequest = PeriodicWorkRequestBuilder<AdaptiveQuantityWorker>(
            7, TimeUnit.DAYS
        ).build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "AdaptiveQuantityWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            adaptiveWorkRequest
        )
        
        // Enqueue DynamicReminderWorker to adjust reminders periodically (not more frequent than 15 mins)
        val dynamicReminderRequest = PeriodicWorkRequestBuilder<com.example.worker.DynamicReminderWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DynamicReminderWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            dynamicReminderRequest
        )
    }
}
