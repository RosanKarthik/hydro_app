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
    }
}
