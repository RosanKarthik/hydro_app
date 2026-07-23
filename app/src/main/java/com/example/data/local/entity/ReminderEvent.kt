package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ReminderResponse {
    PENDING,
    DONE,
    NOT_DONE,
    IGNORED
}

/**
 * ReminderEvent represents each scheduled dynamic notification prompt.
 */
@Entity(
    tableName = "reminder_event",
    foreignKeys = [
        ForeignKey(
            entity = DayRecord::class,
            parentColumns = ["id"],
            childColumns = ["dayRecordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["dayRecordId"])
    ]
)
data class ReminderEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dayRecordId: Long,
    val scheduledTime: Long, // Epoch millis
    val firedTime: Long? = null, // Actual trigger time
    val userResponse: ReminderResponse = ReminderResponse.PENDING,
    val respondedAt: Long? = null // Epoch millis when user responded
)
