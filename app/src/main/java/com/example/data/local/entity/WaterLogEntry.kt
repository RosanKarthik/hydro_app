package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class LogSource {
    REMINDER_TAP_DONE,
    MANUAL_APP_OPEN,
    MANUAL_NOTIFICATION_TAP
}

/**
 * WaterLogEntry represents an individual log of water consumption.
 */
@Entity(
    tableName = "water_log_entry",
    foreignKeys = [
        ForeignKey(
            entity = DayRecord::class,
            parentColumns = ["id"],
            childColumns = ["dayRecordId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ReminderEvent::class,
            parentColumns = ["id"],
            childColumns = ["reminderEventId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["dayRecordId"]),
        Index(value = ["reminderEventId"])
    ]
)
data class WaterLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dayRecordId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val amountMl: Int,
    val source: LogSource,
    val reminderEventId: Long? = null
)
