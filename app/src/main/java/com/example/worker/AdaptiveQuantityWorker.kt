package com.example.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.data.local.database.WaterDatabase
import com.example.data.local.datastore.dataStore
import com.example.data.repository.WaterRepositoryImpl
import com.example.health.HealthConnectManager
import com.example.receiver.WaterReminderReceiver
import kotlinx.coroutines.flow.first

class AdaptiveQuantityWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = WaterDatabase.getInstance(appContext)
        val dataStore = appContext.dataStore
        val repository = WaterRepositoryImpl(db.waterDao(), dataStore, appContext, HealthConnectManager(appContext))

        // Check if manualOverrideActive is true. If it is, abort the worker.
        val isManualOverrideActive = repository.getManualOverrideActiveFlow().first()
        if (isManualOverrideActive) {
            return Result.success()
        }

        // Fetch the inputs from the Repository (last 7-14 days of avgConsumedMl, completionRate, etc.)
        val recentCompletedDays = repository.getCompletedDayRecords(14)
        
        // Ensure we have at least 7 completed days to make an adaptive adjustment
        if (recentCompletedDays.size < 7) {
            return Result.success()
        }

        var totalCompletionRatio = 0f
        var validDaysCount = 0

        for (day in recentCompletedDays) {
            if (day.targetMlForDay > 0) {
                val ratio = day.totalConsumedMl.toFloat() / day.targetMlForDay
                totalCompletionRatio += ratio
                validDaysCount++
            }
        }

        if (validDaysCount == 0) return Result.success()

        val avgCompletionRate = totalCompletionRatio / validDaysCount

        // 1. If completionRate > 1.15, raise adaptiveTargetMl by 5-10% (cap at 15%).
        if (avgCompletionRate > 1.15f) {
            val userProfile = repository.getUserProfile()
            if (userProfile != null) {
                // Raise by 5% as described
                val increasedTarget = (userProfile.adaptiveTargetMl * 1.05f).toInt()
                
                // Maximum 15% increase limit check (optional bound if we tracked previous state)
                // We'll just apply a standard 5% increase per week.
                repository.updateAdaptiveTarget(increasedTarget)
            }
        } 
        // 2. If completionRate < 0.6, do not auto-lower it. Instead, trigger a local notification.
        else if (avgCompletionRate < 0.6f) {
            showPromptToLowerTargetNotification()
        }
        // 3. If between 0.85 and 1.15, make no changes. (And implicit: 0.6 to 0.85 also make no changes).

        return Result.success()
    }

    private fun showPromptToLowerTargetNotification() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Water Reminders"
            val descriptionText = "Notifications for dynamic water intake reminders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel(WaterReminderReceiver.CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, WaterReminderReceiver.CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
            .setContentTitle("Hydration Goal Check")
            .setContentText("You've been under your goal most days — lower your target, or get more reminders?")
            .setStyle(NotificationCompat.BigTextStyle().bigText("You've been under your goal most days — lower your target, or get more reminders?"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            val notificationManager = NotificationManagerCompat.from(appContext)
            notificationManager.notify(1001, notification)
        } catch (e: SecurityException) {
            // Missing POST_NOTIFICATIONS permission
        }
    }
}
