package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.alarm.WaterAlarmScheduler
import com.example.data.local.database.WaterDatabase
import com.example.data.local.entity.LogSource
import com.example.data.local.entity.ReminderResponse
import com.example.data.repository.WaterRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver responsible for handling fired reminder alarms and notification quick actions (Done / Not Done).
 */
class WaterReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)

        when (action) {
            ACTION_REMINDER_ALARM -> {
                showReminderNotification(context, reminderId)
            }
            ACTION_DONE -> {
                val amountMl = intent.getIntExtra(EXTRA_DEFAULT_ML, 250)
                handleDoneAction(context, reminderId, amountMl)
            }
            ACTION_NOT_DONE -> {
                handleNotDoneAction(context, reminderId)
            }
        }
    }

    private fun showReminderNotification(context: Context, reminderId: Long) {
        val notificationManager = NotificationManagerCompat.from(context)
        createNotificationChannel(context)

        // Notification content tap intent -> open MainActivity
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_QUICK_LOG, true)
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }

        val contentPendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Quick Action 1: Done
        val doneIntent = Intent(context, WaterReminderReceiver::class.java).apply {
            action = ACTION_DONE
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_DEFAULT_ML, 250)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId * 10 + 1).toInt(),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Quick Action 2: Not Done
        val notDoneIntent = Intent(context, WaterReminderReceiver::class.java).apply {
            action = ACTION_NOT_DONE
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        val notDonePendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId * 10 + 2).toInt(),
            notDoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // fallback system icon
            .setContentTitle("Hydration Check")
            .setContentText("Time for a sip! Drink 250ml of water to stay on track.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.checkbox_on_background,
                "Done (250ml)",
                donePendingIntent
            )
            .addAction(
                android.R.drawable.ic_delete,
                "Not Done",
                notDonePendingIntent
            )
            .build()

        try {
            notificationManager.notify(reminderId.toInt(), notification)
        } catch (e: SecurityException) {
            // Notification permission missing
        }

        // Record firedTime in database
        if (reminderId != -1L) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = WaterDatabase.getInstance(context)
                    val dao = db.waterDao()
                    val event = dao.getReminderEventById(reminderId)
                    if (event != null) {
                        dao.updateReminderEvent(event.copy(firedTime = System.currentTimeMillis()))
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun handleDoneAction(context: Context, reminderId: Long, amountMl: Int) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (reminderId != -1L) {
            notificationManager.cancel(reminderId.toInt())
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = WaterDatabase.getInstance(context)
                val repository = WaterRepositoryImpl(db.waterDao(), null, context)
                val event = db.waterDao().getReminderEventById(reminderId)

                if (event != null) {
                    repository.logWater(
                        dayRecordId = event.dayRecordId,
                        amountMl = amountMl,
                        source = LogSource.REMINDER_TAP_DONE,
                        reminderEventId = reminderId
                    )

                    // Perform even-spacing recalculation
                    val now = System.currentTimeMillis()
                    val estimatedCheckout = now + (4 * 60 * 60 * 1000) // Default 4 hours or end of day
                    val scheduledReminders = repository.recalculateEvenSpacedReminders(
                        dayRecordId = event.dayRecordId,
                        currentTimeEpoch = now,
                        estimatedCheckoutEpoch = estimatedCheckout
                    )

                    // Reschedule alarms
                    val scheduler = WaterAlarmScheduler(context)
                    scheduledReminders.forEach { reminder ->
                        scheduler.scheduleExactAlarm(
                            reminderEventId = reminder.id,
                            scheduledTimeEpoch = reminder.timestamp
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleNotDoneAction(context: Context, reminderId: Long) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (reminderId != -1L) {
            notificationManager.cancel(reminderId.toInt())
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = WaterDatabase.getInstance(context)
                val repository = WaterRepositoryImpl(db.waterDao(), null, context)
                val event = db.waterDao().getReminderEventById(reminderId)

                if (event != null) {
                    repository.updateReminderResponse(reminderId, ReminderResponse.NOT_DONE)

                    // Recalculate remaining schedule
                    val now = System.currentTimeMillis()
                    val estimatedCheckout = now + (4 * 60 * 60 * 1000)
                    val scheduledReminders = repository.recalculateEvenSpacedReminders(
                        dayRecordId = event.dayRecordId,
                        currentTimeEpoch = now,
                        estimatedCheckoutEpoch = estimatedCheckout
                    )

                    val scheduler = WaterAlarmScheduler(context)
                    scheduledReminders.forEach { reminder ->
                        scheduler.scheduleExactAlarm(
                            reminderEventId = reminder.id,
                            scheduledTimeEpoch = reminder.timestamp
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Water Reminders"
            val descriptionText = "Notifications for dynamic water intake reminders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "water_reminders_channel"
        const val ACTION_REMINDER_ALARM = "com.example.ACTION_REMINDER_ALARM"
        const val ACTION_DONE = "com.example.ACTION_DONE"
        const val ACTION_NOT_DONE = "com.example.ACTION_NOT_DONE"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_DEFAULT_ML = "extra_default_ml"
        const val EXTRA_OPEN_QUICK_LOG = "extra_open_quick_log"
    }
}
