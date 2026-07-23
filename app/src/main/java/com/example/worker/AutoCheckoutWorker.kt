package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.alarm.WaterAlarmScheduler
import com.example.data.local.database.WaterDatabase
import com.example.data.repository.WaterRepositoryImpl
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker responsible for auto-checking out any active day record
 * that wasn't manually closed past the fallback bedtime (e.g., 11:59 PM).
 * Implements Section 6.2 of the technical spec.
 */
class AutoCheckoutWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = WaterDatabase.getInstance(applicationContext)
        val repository = WaterRepositoryImpl(db.waterDao(), null, applicationContext)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Calendar.getInstance().time)

        val activeDay = repository.getTodayRecord(todayStr)

        if (activeDay != null && activeDay.checkOutTime == null) {
            // Cancel any pending alarms for the day FIRST
            val scheduler = WaterAlarmScheduler(applicationContext)
            val pendingReminders = db.waterDao().getPendingReminderEvents(activeDay.id)
            scheduler.cancelAllPendingAlarms(pendingReminders.map { it.id })
            
            // Then checkout (which clears database)
            val now = System.currentTimeMillis()
            repository.checkOutToday(
                dayRecordId = activeDay.id,
                checkOutTimeEpoch = now,
                autoCheckedOut = true
            )
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "auto_checkout_daily_work"

        /**
         * Schedules a daily background job timed for fallback checkout (11:59 PM).
         */
        fun scheduleDailyAutoCheckout(context: Context) {
            val now = Calendar.getInstance()
            val targetTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (now.after(targetTime)) {
                targetTime.add(Calendar.DAY_OF_YEAR, 1)
            }

            val initialDelayMillis = targetTime.timeInMillis - now.timeInMillis

            val workRequest = OneTimeWorkRequestBuilder<AutoCheckoutWorker>()
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
