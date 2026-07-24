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
    }
}
