package com.example.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.receiver.WaterReminderReceiver

/**
 * Helper class for scheduling exact reminders using AlarmManager.
 * Uses setExactAndAllowWhileIdle to fire reliably through Android Doze mode.
 */
class WaterAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules an exact alarm for a specific reminder event ID at the given epoch time.
     */
    @SuppressLint("ScheduleExactAlarm")
    fun scheduleExactAlarm(
        reminderEventId: Long,
        scheduledTimeEpoch: Long,
        defaultMl: Int = 250
    ) {
        // Skip if scheduled time is in the past
        if (scheduledTimeEpoch <= System.currentTimeMillis()) return

        // On Android 12+ (API 31+), check exact alarm permission if required
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // Fallback or exact alarm permission prompt can be triggered in UI
            }
        }

        val intent = Intent(context, WaterReminderReceiver::class.java).apply {
            action = WaterReminderReceiver.ACTION_REMINDER_ALARM
            putExtra(WaterReminderReceiver.EXTRA_REMINDER_ID, reminderEventId)
            putExtra(WaterReminderReceiver.EXTRA_DEFAULT_ML, defaultMl)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderEventId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                scheduledTimeEpoch,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                scheduledTimeEpoch,
                pendingIntent
            )
        }
    }

    /**
     * Cancels an alarm for a specific reminder event ID.
     */
    fun cancelAlarm(reminderEventId: Long) {
        val intent = Intent(context, WaterReminderReceiver::class.java).apply {
            action = WaterReminderReceiver.ACTION_REMINDER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderEventId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Cancels a list of pending alarms.
     */
    fun cancelAllPendingAlarms(reminderEventIds: List<Long>) {
        reminderEventIds.forEach { id ->
            cancelAlarm(id)
        }
    }
}
