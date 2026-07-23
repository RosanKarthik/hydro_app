package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.DayRecord
import com.example.data.local.entity.ReminderEvent
import com.example.data.local.entity.ReminderResponse
import com.example.data.local.entity.UserProfile
import com.example.data.local.entity.WaterLogEntry
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object providing CRUD operations and reactive Flows for Water Reminder data.
 */
@Dao
interface WaterDao {

    // ==================== USER PROFILE ====================

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfileFlow(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserProfile(userProfile: UserProfile)

    // ==================== DAY RECORD ====================

    @Query("SELECT * FROM day_record WHERE checkOutTime IS NOT NULL ORDER BY date DESC LIMIT :limit")
    suspend fun getCompletedDayRecords(limit: Int = 14): List<DayRecord>

    @Query("SELECT * FROM day_record WHERE date = :date LIMIT 1")
    fun getDayRecordByDateFlow(date: String): Flow<DayRecord?>

    @Query("SELECT * FROM day_record WHERE date = :date LIMIT 1")
    suspend fun getDayRecordByDate(date: String): DayRecord?

    @Query("SELECT * FROM day_record WHERE id = :id LIMIT 1")
    fun getDayRecordByIdFlow(id: Long): Flow<DayRecord?>

    @Query("SELECT * FROM day_record WHERE id = :id LIMIT 1")
    suspend fun getDayRecordById(id: Long): DayRecord?

    @Query("SELECT * FROM day_record ORDER BY date DESC LIMIT :limit")
    fun getRecentDayRecordsFlow(limit: Int = 14): Flow<List<DayRecord>>

    @Query("SELECT * FROM day_record ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentDayRecords(limit: Int = 14): List<DayRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDayRecord(dayRecord: DayRecord): Long

    @Update
    suspend fun updateDayRecord(dayRecord: DayRecord)

    @Query("UPDATE day_record SET totalConsumedMl = totalConsumedMl + :amountMl WHERE id = :dayRecordId")
    suspend fun incrementTotalConsumedMl(dayRecordId: Long, amountMl: Int)

    @Query("UPDATE day_record SET totalConsumedMl = max(0, totalConsumedMl - :amountMl) WHERE id = :dayRecordId")
    suspend fun decrementTotalConsumedMl(dayRecordId: Long, amountMl: Int)

    // ==================== WATER LOG ENTRY ====================

    @Query("""
        SELECT 
            CAST(SUM(CASE WHEN source IN ('MANUAL_APP_OPEN', 'MANUAL_NOTIFICATION_TAP') THEN 1 ELSE 0 END) AS REAL) / 
            COUNT(id) 
        FROM water_log_entry 
        WHERE timestamp >= :sinceEpoch AND amountMl > 0
    """)
    suspend fun getManualLogRatioSince(sinceEpoch: Long): Float?

    @Query("""
        SELECT * FROM water_log_entry 
        WHERE timestamp >= :sinceEpoch 
        AND source IN ('MANUAL_APP_OPEN', 'MANUAL_NOTIFICATION_TAP')
    """)
    suspend fun getManualLogsSince(sinceEpoch: Long): List<WaterLogEntry>

    @Query("SELECT * FROM water_log_entry WHERE dayRecordId = :dayRecordId ORDER BY timestamp DESC")
    fun getWaterLogsForDayFlow(dayRecordId: Long): Flow<List<WaterLogEntry>>

    @Query("SELECT * FROM water_log_entry WHERE dayRecordId = :dayRecordId ORDER BY timestamp DESC")
    suspend fun getWaterLogsForDay(dayRecordId: Long): List<WaterLogEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaterLogEntry(entry: WaterLogEntry): Long

    @Delete
    suspend fun deleteWaterLogEntry(entry: WaterLogEntry)

    /**
     * Transaction: Log water consumption and atomically update totalConsumedMl in DayRecord.
     */
    @Transaction
    suspend fun logWaterAndIncrementTotal(entry: WaterLogEntry) {
        insertWaterLogEntry(entry)
        incrementTotalConsumedMl(entry.dayRecordId, entry.amountMl)
    }

    /**
     * Transaction: Delete water log entry and atomically decrement totalConsumedMl in DayRecord.
     */
    @Transaction
    suspend fun deleteWaterLogAndDecrementTotal(entry: WaterLogEntry) {
        deleteWaterLogEntry(entry)
        decrementTotalConsumedMl(entry.dayRecordId, entry.amountMl)
    }

    // ==================== REMINDER EVENT ====================

    @Query("""
        SELECT * FROM reminder_event 
        WHERE scheduledTime >= :sinceEpoch 
        AND userResponse IN ('IGNORED', 'NOT_DONE')
    """)
    suspend fun getIgnoredOrNotDoneRemindersSince(sinceEpoch: Long): List<ReminderEvent>

    @Query("SELECT * FROM reminder_event WHERE dayRecordId = :dayRecordId ORDER BY scheduledTime ASC")
    fun getReminderEventsForDayFlow(dayRecordId: Long): Flow<List<ReminderEvent>>

    @Query("SELECT * FROM reminder_event WHERE dayRecordId = :dayRecordId ORDER BY scheduledTime ASC")
    suspend fun getReminderEventsForDay(dayRecordId: Long): List<ReminderEvent>

    @Query("SELECT * FROM reminder_event WHERE dayRecordId = :dayRecordId AND userResponse = 'PENDING' ORDER BY scheduledTime ASC")
    suspend fun getPendingReminderEvents(dayRecordId: Long): List<ReminderEvent>

    @Query("SELECT * FROM reminder_event WHERE id = :id LIMIT 1")
    suspend fun getReminderEventById(id: Long): ReminderEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminderEvent(event: ReminderEvent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminderEvents(events: List<ReminderEvent>): List<Long>

    @Update
    suspend fun updateReminderEvent(event: ReminderEvent)

    @Query("UPDATE reminder_event SET userResponse = :response, respondedAt = :respondedAt WHERE id = :id")
    suspend fun updateReminderResponse(id: Long, response: ReminderResponse, respondedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM reminder_event WHERE dayRecordId = :dayRecordId AND userResponse = 'PENDING'")
    suspend fun cancelPendingRemindersForDay(dayRecordId: Long)
}
