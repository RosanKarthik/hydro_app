package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * DayRecord represents a single calendar day bounded by check-in and check-out times.
 */
@Entity(
    tableName = "day_record",
    indices = [Index(value = ["date"], unique = true)]
)
data class DayRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // Format: yyyy-MM-dd in local timezone
    val checkInTime: Long? = null, // Epoch millis
    val checkOutTime: Long? = null, // Epoch millis
    val targetMlForDay: Int, // Snapshot of adaptiveTargetMl at check-in time
    val totalConsumedMl: Int = 0, // Running sum updated on every water log
    val autoCheckedOut: Boolean = false // True if auto-checkout job closed the day
)
