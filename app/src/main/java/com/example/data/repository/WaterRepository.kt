package com.example.data.repository

import com.example.data.local.entity.ActivityLevel
import com.example.data.local.entity.Climate
import com.example.data.local.entity.DayRecord
import com.example.data.local.entity.LogSource
import com.example.data.local.entity.ReminderEvent
import com.example.data.local.entity.ReminderResponse
import com.example.data.local.entity.UserProfile
import com.example.data.local.entity.WaterLogEntry
import kotlinx.coroutines.flow.Flow

data class ScheduledReminder(val id: Long, val timestamp: Long)

/**
 * Repository interface for managing Water Reminder application data and domain rules.
 */
interface WaterRepository {

    // User Profile
    fun getUserProfileFlow(): Flow<UserProfile?>
    suspend fun getUserProfile(): UserProfile?
    suspend fun saveUserProfile(
        heightCm: Float,
        weightKg: Float,
        activityLevel: ActivityLevel,
        climate: Climate
    ): UserProfile

    suspend fun updateAdaptiveTarget(newAdaptiveTargetMl: Int)

    // Formula calculation
    fun calculateBaseTargetMl(
        weightKg: Float,
        activityLevel: ActivityLevel,
        climate: Climate
    ): Int

    // Day Record Lifecycle
    fun getTodayRecordFlow(dateString: String): Flow<DayRecord?>
    suspend fun getTodayRecord(dateString: String): DayRecord?
    suspend fun getCompletedDayRecords(limit: Int = 14): List<DayRecord>
    suspend fun checkInToday(
        dateString: String,
        checkInTimeEpoch: Long = System.currentTimeMillis()
    ): DayRecord

    suspend fun checkOutToday(
        dayRecordId: Long,
        checkOutTimeEpoch: Long = System.currentTimeMillis(),
        autoCheckedOut: Boolean = false
    ): DayRecord

    // Water Logging
    fun getWaterLogsForDayFlow(dayRecordId: Long): Flow<List<WaterLogEntry>>
    suspend fun getManualLogRatioSince(sinceEpoch: Long): Float?
    suspend fun getManualLogsSince(sinceEpoch: Long): List<WaterLogEntry>
    suspend fun logWater(
        dayRecordId: Long,
        amountMl: Int,
        source: LogSource,
        reminderEventId: Long? = null
    ): WaterLogEntry

    suspend fun deleteWaterLog(entry: WaterLogEntry)

    // Reminders & Dynamic Spacing (§5.3)
    fun getReminderEventsForDayFlow(dayRecordId: Long): Flow<List<ReminderEvent>>
    suspend fun getPendingReminders(dayRecordId: Long): List<ReminderEvent>
    suspend fun getIgnoredOrNotDoneRemindersSince(sinceEpoch: Long): List<ReminderEvent>
    suspend fun updateReminderResponse(reminderId: Long, response: ReminderResponse)

    /**
     * Recalculates and schedules dynamically spaced reminder timestamps for remaining target water intake.
     * Implements Section 5.3 of technical architecture spec.
     *
     * @return List of ScheduledReminder objects containing the new database IDs and timestamps.
     */
    suspend fun recalculateEvenSpacedReminders(
        dayRecordId: Long,
        currentTimeEpoch: Long = System.currentTimeMillis(),
        estimatedCheckoutEpoch: Long,
        averageDesiredGapMin: Int = 75
    ): List<ScheduledReminder>

    // Preferences
    fun getManualOverrideActiveFlow(): Flow<Boolean>
    suspend fun setManualOverrideActive(isActive: Boolean)
    
    fun getFallbackCheckoutTimeFlow(): Flow<String>
    suspend fun setFallbackCheckoutTime(time: String)
    
    fun getHealthConnectSyncEnabledFlow(): Flow<Boolean>
    suspend fun setHealthConnectSyncEnabled(isEnabled: Boolean)
    suspend fun syncDailyExternalHydration()

    fun getCustomQuickAmountsFlow(): Flow<List<Int>>
    suspend fun addCustomQuickAmount(amountMl: Int)
    suspend fun removeCustomQuickAmount(amountMl: Int)
}
