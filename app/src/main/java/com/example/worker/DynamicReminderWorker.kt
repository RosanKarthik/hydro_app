package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.alarm.WaterAlarmScheduler
import com.example.data.local.database.WaterDatabase
import com.example.data.local.datastore.dataStore
import com.example.data.repository.WaterRepositoryImpl
import com.example.data.local.entity.ReminderEvent
import com.example.data.local.entity.ReminderResponse
import com.example.health.HealthConnectManager
import kotlinx.coroutines.flow.first
import java.util.Calendar

class DynamicReminderWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = WaterDatabase.getInstance(appContext)
        val dataStore = appContext.dataStore
        val healthConnectManager = HealthConnectManager(appContext)
        val repository = WaterRepositoryImpl(db.waterDao(), dataStore, appContext, healthConnectManager)
        
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val dateString = dateFormat.format(Calendar.getInstance().time)
        val todayRecord = repository.getTodayRecord(dateString) ?: return Result.success()

        if (todayRecord.checkOutTime != null) {
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val pending = db.waterDao().getPendingReminderEvents(todayRecord.id)
        if (pending.isNotEmpty()) {
            val nextAlarm = pending.minByOrNull { it.scheduledTime }
            if (nextAlarm != null && nextAlarm.scheduledTime - now < 15 * 60 * 1000) {
                // An alarm is firing soon, don't override it and push it back
                return Result.success()
            }
        }

        val checkoutTimeStr = repository.getFallbackCheckoutTimeFlow().first()
        val parts = checkoutTimeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 23
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 59

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }
        
        var estCheckoutEpoch = cal.timeInMillis
        if (estCheckoutEpoch <= now) {
            estCheckoutEpoch = now + (2 * 60 * 60 * 1000)
        }

        val remainingMl = todayRecord.targetMlForDay - todayRecord.totalConsumedMl
        if (remainingMl <= 0) return Result.success()

        val remainingTimeMin = (estCheckoutEpoch - now) / (60 * 1000)
        if (remainingTimeMin <= 0) return Result.success()

        // Cancel existing pending reminders
        val alarmScheduler = WaterAlarmScheduler(appContext)
        pending.forEach { alarmScheduler.cancelAlarm(it.id) }
        db.waterDao().cancelPendingRemindersForDay(todayRecord.id)

        // Calculate interval for the NEXT reminder
        val remindersNeeded = Math.ceil(remainingMl.toDouble() / 250.0).toInt().coerceAtLeast(1)
        var intervalMins = remainingTimeMin / remindersNeeded
        
        // Ensure it does not become annoyingly frequent (not more frequent than 15 mins)
        if (intervalMins < 15) {
            intervalMins = 15
        }

        val nextReminderTime = now + (intervalMins * 60 * 1000)
        if (nextReminderTime >= estCheckoutEpoch) {
            return Result.success()
        }

        // Schedule the next reminder
        val reminderEvent = ReminderEvent(
            dayRecordId = todayRecord.id,
            scheduledTime = nextReminderTime,
            userResponse = ReminderResponse.PENDING
        )
        val id = db.waterDao().insertReminderEvent(reminderEvent)
        alarmScheduler.scheduleExactAlarm(
            reminderEventId = id,
            scheduledTimeEpoch = nextReminderTime,
            defaultMl = 250
        )

        return Result.success()
    }
}
