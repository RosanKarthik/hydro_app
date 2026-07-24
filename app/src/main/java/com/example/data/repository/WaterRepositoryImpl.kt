package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.alarm.WaterAlarmScheduler
import com.example.data.local.datastore.PreferencesKeys
import com.example.data.local.dao.WaterDao
import com.example.data.local.entity.ActivityLevel
import com.example.data.local.entity.Climate
import com.example.data.local.entity.DayRecord
import com.example.data.local.entity.LogSource
import com.example.data.local.entity.ReminderEvent
import com.example.data.local.entity.ReminderResponse
import com.example.data.local.entity.UserProfile
import com.example.data.local.entity.WaterLogEntry
import com.example.health.HealthConnectManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Implementation of [WaterRepository] encapsulating domain formulas and Room DAO interactions.
 */
class WaterRepositoryImpl(
    private val waterDao: WaterDao,
    private val dataStore: DataStore<Preferences>? = null,
    private val context: Context? = null,
    private val healthConnectManager: HealthConnectManager
) : WaterRepository {

    override fun getUserProfileFlow(): Flow<UserProfile?> =
        waterDao.getUserProfileFlow()

    override suspend fun getUserProfile(): UserProfile? =
        waterDao.getUserProfile()

    /**
     * Calculates baseline water intake in ml using Section 4.1 formula.
     * Clamped between 1200ml and 5000ml.
     */
    override fun calculateBaseTargetMl(
        weightKg: Float,
        activityLevel: ActivityLevel,
        climate: Climate
    ): Int {
        val rawBase = weightKg * 33f

        val activityModifier = when (activityLevel) {
            ActivityLevel.SEDENTARY -> 0
            ActivityLevel.LIGHT -> 300
            ActivityLevel.MODERATE -> 500
            ActivityLevel.HIGH -> 800
        }

        val climateModifier = when (climate) {
            Climate.TEMPERATE -> 0
            Climate.HOT -> 400
            Climate.HUMID -> 300
        }

        val computed = (rawBase + activityModifier + climateModifier).toInt()
        return computed.coerceIn(1200, 5000)
    }

    override suspend fun saveUserProfile(
        heightCm: Float,
        weightKg: Float,
        activityLevel: ActivityLevel,
        climate: Climate
    ): UserProfile {
        val baseTarget = calculateBaseTargetMl(weightKg, activityLevel, climate)
        val existingProfile = getUserProfile()

        val profile = UserProfile(
            id = 1L,
            heightCm = heightCm,
            weightKg = weightKg,
            activityLevel = activityLevel,
            climate = climate,
            baseTargetMl = baseTarget,
            adaptiveTargetMl = existingProfile?.adaptiveTargetMl ?: baseTarget,
            updatedAt = System.currentTimeMillis()
        )

        waterDao.upsertUserProfile(profile)
        return profile
    }

    override suspend fun updateAdaptiveTarget(newAdaptiveTargetMl: Int) {
        val currentProfile = getUserProfile() ?: return
        val updatedProfile = currentProfile.copy(
            adaptiveTargetMl = newAdaptiveTargetMl.coerceIn(1200, 5000),
            updatedAt = System.currentTimeMillis()
        )
        waterDao.upsertUserProfile(updatedProfile)
    }

    override fun getTodayRecordFlow(dateString: String): Flow<DayRecord?> =
        waterDao.getDayRecordByDateFlow(dateString)

    override suspend fun getTodayRecord(dateString: String): DayRecord? =
        waterDao.getDayRecordByDate(dateString)

    override suspend fun getCompletedDayRecords(limit: Int): List<DayRecord> =
        waterDao.getCompletedDayRecords(limit)

    override suspend fun checkInToday(
        dateString: String,
        checkInTimeEpoch: Long
    ): DayRecord {
        val existingRecord = waterDao.getDayRecordByDate(dateString)
        if (existingRecord != null) {
            val updated = existingRecord.copy(
                checkInTime = existingRecord.checkInTime ?: checkInTimeEpoch
            )
            waterDao.updateDayRecord(updated)
            return updated
        }

        val profile = getUserProfile()
        val dayTarget = profile?.adaptiveTargetMl ?: 2500

        val newRecord = DayRecord(
            date = dateString,
            checkInTime = checkInTimeEpoch,
            checkOutTime = null,
            targetMlForDay = dayTarget,
            totalConsumedMl = 0,
            autoCheckedOut = false
        )

        val generatedId = waterDao.insertDayRecord(newRecord)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                syncDailyExternalHydration()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return newRecord.copy(id = generatedId)
    }

    override suspend fun checkOutToday(
        dayRecordId: Long,
        checkOutTimeEpoch: Long,
        autoCheckedOut: Boolean
    ): DayRecord {
        val currentRecord = waterDao.getDayRecordById(dayRecordId)
            ?: throw IllegalArgumentException("DayRecord with ID $dayRecordId not found")

        val updated = currentRecord.copy(
            checkOutTime = checkOutTimeEpoch,
            autoCheckedOut = autoCheckedOut
        )
        waterDao.updateDayRecord(updated)
        
        if (context != null) {
            val scheduler = WaterAlarmScheduler(context)
            val pendingReminders = waterDao.getPendingReminderEvents(dayRecordId)
            scheduler.cancelAllPendingAlarms(pendingReminders.map { it.id })
        }
        
        waterDao.cancelPendingRemindersForDay(dayRecordId)
        return updated
    }

    override fun getWaterLogsForDayFlow(dayRecordId: Long): Flow<List<WaterLogEntry>> =
        waterDao.getWaterLogsForDayFlow(dayRecordId)

    override suspend fun getManualLogRatioSince(sinceEpoch: Long): Float? =
        waterDao.getManualLogRatioSince(sinceEpoch)

    override suspend fun getManualLogsSince(sinceEpoch: Long): List<WaterLogEntry> =
        waterDao.getManualLogsSince(sinceEpoch)

    override suspend fun logWater(
        dayRecordId: Long,
        amountMl: Int,
        source: LogSource,
        reminderEventId: Long?
    ): WaterLogEntry {
        val entry = WaterLogEntry(
            dayRecordId = dayRecordId,
            timestamp = System.currentTimeMillis(),
            amountMl = amountMl,
            source = source,
            reminderEventId = reminderEventId
        )

        waterDao.logWaterAndIncrementTotal(entry)

        if (reminderEventId != null) {
            waterDao.updateReminderResponse(
                id = reminderEventId,
                response = ReminderResponse.DONE,
                respondedAt = System.currentTimeMillis()
            )
        }
        
        try {
            val syncEnabled = getHealthConnectSyncEnabledFlow().first()
            if (syncEnabled) {
                healthConnectManager?.syncToHealthConnect(entry)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return entry
    }

    override suspend fun deleteWaterLog(entry: WaterLogEntry) {
        waterDao.deleteWaterLogAndDecrementTotal(entry)
    }

    override fun getReminderEventsForDayFlow(dayRecordId: Long): Flow<List<ReminderEvent>> =
        waterDao.getReminderEventsForDayFlow(dayRecordId)

    override suspend fun getPendingReminders(dayRecordId: Long): List<ReminderEvent> =
        waterDao.getPendingReminderEvents(dayRecordId)

    override suspend fun getIgnoredOrNotDoneRemindersSince(sinceEpoch: Long): List<ReminderEvent> =
        waterDao.getIgnoredOrNotDoneRemindersSince(sinceEpoch)

    override suspend fun updateReminderResponse(reminderId: Long, response: ReminderResponse) {
        waterDao.updateReminderResponse(reminderId, response)
    }

    /**
     * Even-Spacing Recalculation logic (§5.3).
     * Calculates dynamic reminder count and schedule timestamps.
     */
    override suspend fun recalculateEvenSpacedReminders(
        dayRecordId: Long,
        currentTimeEpoch: Long,
        estimatedCheckoutEpoch: Long,
        averageDesiredGapMin: Int
    ): List<ScheduledReminder> {
        val dayRecord = waterDao.getDayRecordById(dayRecordId) ?: return emptyList()

        val remainingMl = dayRecord.targetMlForDay - dayRecord.totalConsumedMl
        val remainingTimeMin = (estimatedCheckoutEpoch - currentTimeEpoch) / (1000 * 60)

        // Cancel existing pending reminders before rescheduling
        if (context != null) {
            val scheduler = WaterAlarmScheduler(context)
            val pending = waterDao.getPendingReminderEvents(dayRecordId)
            pending.forEach { scheduler.cancelAlarm(it.id) }
        }
        waterDao.cancelPendingRemindersForDay(dayRecordId)

        // If goal is reached or less than 15 minutes remaining, no new reminders needed
        if (remainingMl <= 0 || remainingTimeMin <= 15) {
            return emptyList()
        }

        val safeGapMin = averageDesiredGapMin.coerceAtLeast(30)
        val reminderCount = maxOf(1, (remainingTimeMin / safeGapMin).toInt())

        val stepMillis = (remainingTimeMin * 60 * 1000) / (reminderCount + 1)
        val rawScheduledTimestamps = ArrayList<Long>(reminderCount)

        for (i in 1..reminderCount) {
            val fireTime = currentTimeEpoch + (i * stepMillis)
            rawScheduledTimestamps.add(fireTime)
        }

        // --- NEW ADAPTIVE TIMING LOGIC ---
        // Fetch 14 days of history to analyze patterns
        val fourteenDaysAgo = currentTimeEpoch - (14 * 24 * 60 * 60 * 1000L)
        val deadZoneEvents = waterDao.getIgnoredOrNotDoneRemindersSince(fourteenDaysAgo)
        val manualLogs = waterDao.getManualLogsSince(fourteenDaysAgo)

        val adjustedTimestamps = applyAdaptiveTimingConstraints(
            rawTimestamps = rawScheduledTimestamps,
            deadZoneEvents = deadZoneEvents,
            manualLogs = manualLogs,
            currentTimeEpoch = currentTimeEpoch,
            estimatedCheckoutEpoch = estimatedCheckoutEpoch
        )

        val finalScheduledTimestamps = mutableListOf<ScheduledReminder>()
        for (fireTime in adjustedTimestamps) {
            val reminderEvent = ReminderEvent(
                dayRecordId = dayRecordId,
                scheduledTime = fireTime,
                userResponse = ReminderResponse.PENDING
            )
            val id = waterDao.insertReminderEvent(reminderEvent)
            finalScheduledTimestamps.add(ScheduledReminder(id, fireTime))
        }

        return finalScheduledTimestamps
    }

    /**
     * Applies Adaptive Timing (Section 5.2) constraints to an initial list of evenly spaced timestamps.
     * 
     * 1. Avoid Dead Zones (5.2a): If >= 5 IGNORED/NOT_DONE events occurred in the same +/- 30 min window
     *    of a proposed time, we shift the reminder by 30 mins (if possible) or skip it entirely.
     * 2. Avoid Manual Clusters (5.2b): If the user often manually logs water around a certain time
     *    (recurring manual log cluster, e.g. >= 3 occurrences within +/- 45 mins),
     *    we deprioritize (skip) the reminder slot.
     */
    private fun applyAdaptiveTimingConstraints(
        rawTimestamps: List<Long>,
        deadZoneEvents: List<ReminderEvent>,
        manualLogs: List<WaterLogEntry>,
        currentTimeEpoch: Long,
        estimatedCheckoutEpoch: Long
    ): List<Long> {
        val adjustedTimestamps = mutableListOf<Long>()
        
        // Helper to get minute-of-day for a timestamp
        fun getMinuteOfDay(timeMs: Long): Int {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = timeMs
            return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        }

        val deadZoneMinutes = deadZoneEvents.map { getMinuteOfDay(it.scheduledTime) }
        val manualLogMinutes = manualLogs.map { getMinuteOfDay(it.timestamp) }

        for (timestamp in rawTimestamps) {
            val minuteOfDay = getMinuteOfDay(timestamp)
            
            // Check for Dead Zones: ≥5 occurrences in ±30 min window
            val deadZoneCount = deadZoneMinutes.count { 
                abs(it - minuteOfDay) <= 30 || abs(it - minuteOfDay) >= 24 * 60 - 30 
            }
            
            if (deadZoneCount >= 5) {
                // Try shifting by 30 mins later
                val shiftedTimestamp = timestamp + (30 * 60 * 1000L)
                val shiftedMinute = getMinuteOfDay(shiftedTimestamp)
                
                // Check if the shifted time is still in a dead zone, or past checkout
                val stillDeadZone = deadZoneMinutes.count { 
                    abs(it - shiftedMinute) <= 30 || abs(it - shiftedMinute) >= 24 * 60 - 30 
                } >= 5
                
                // Only shift if it's not still a dead zone and leaves at least 15 mins before checkout
                if (!stillDeadZone && shiftedTimestamp < estimatedCheckoutEpoch - (15 * 60 * 1000L)) {
                    adjustedTimestamps.add(shiftedTimestamp)
                }
                // Skip original slot
                continue
            }

            // Check for Manual Log Clusters: >= 3 occurrences within ±45 min window
            val manualLogCount = manualLogMinutes.count { 
                abs(it - minuteOfDay) <= 45 || abs(it - minuteOfDay) >= 24 * 60 - 45 
            }
            
            if (manualLogCount >= 3) {
                // Deprioritize: Skip this reminder slot entirely
                continue
            }

            // If neither constraint is violated, keep the original timestamp
            adjustedTimestamps.add(timestamp)
        }
        
        // Ensure we don't have overlapping timestamps after shifting
        val finalTimestamps = mutableListOf<Long>()
        var lastAdded = 0L
        for (ts in adjustedTimestamps.sorted()) {
            // At least 15 minutes between reminders to prevent notification clustering
            if (ts - lastAdded >= 15 * 60 * 1000L) {
                finalTimestamps.add(ts)
                lastAdded = ts
            }
        }

        return finalTimestamps
    }

    override fun getManualOverrideActiveFlow(): Flow<Boolean> {
        val flow = dataStore?.data
        return if (flow != null) {
            flow.map { preferences ->
                preferences[PreferencesKeys.MANUAL_OVERRIDE_ACTIVE] ?: false
            }
        } else {
            kotlinx.coroutines.flow.flowOf(false)
        }
    }

    override suspend fun setManualOverrideActive(isActive: Boolean) {
        dataStore?.edit { preferences ->
            preferences[PreferencesKeys.MANUAL_OVERRIDE_ACTIVE] = isActive
        }
    }

    override fun getFallbackCheckoutTimeFlow(): Flow<String> {
        val flow = dataStore?.data
        return if (flow != null) {
            flow.map { preferences ->
                preferences[PreferencesKeys.FALLBACK_CHECKOUT_TIME] ?: "23:59"
            }
        } else {
            kotlinx.coroutines.flow.flowOf("23:59")
        }
    }

    override suspend fun setFallbackCheckoutTime(time: String) {
        dataStore?.edit { preferences ->
            preferences[PreferencesKeys.FALLBACK_CHECKOUT_TIME] = time
        }
    }

    override fun getHealthConnectSyncEnabledFlow(): Flow<Boolean> {
        val flow = dataStore?.data
        return if (flow != null) {
            flow.map { preferences ->
                preferences[PreferencesKeys.HEALTH_CONNECT_SYNC_ENABLED] ?: false
            }
        } else {
            kotlinx.coroutines.flow.flowOf(false)
        }
    }

    override suspend fun setHealthConnectSyncEnabled(isEnabled: Boolean) {
        dataStore?.edit { preferences ->
            preferences[PreferencesKeys.HEALTH_CONNECT_SYNC_ENABLED] = isEnabled
        }
    }

    override suspend fun syncDailyExternalHydration() {
        val syncEnabled = getHealthConnectSyncEnabledFlow().first()
        if (!syncEnabled || healthConnectManager == null) return

        val until = Instant.now()
        val since = until.minus(48, ChronoUnit.HOURS)

        val externalRecords = healthConnectManager.pullExternalHydration(since, until)
        if (externalRecords.isEmpty()) return

        val allExternalIds = externalRecords.map { it.metadata.id }
        if (allExternalIds.isEmpty()) return

        val existingIds = waterDao.getExistingExternalRecordIds(allExternalIds).toSet()
        val newRecords = externalRecords.filter { it.metadata.id !in existingIds }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        for (record in newRecords) {
            val amountMl = record.volume?.inMilliliters?.toInt() ?: continue
            val timestamp = record.startTime.toEpochMilli()
            val dateString = sdf.format(Date(timestamp))

            var dayRecord = waterDao.getDayRecordByDate(dateString)
            if (dayRecord == null) {
                dayRecord = checkInToday(dateString, timestamp)
            }

            val entry = WaterLogEntry(
                dayRecordId = dayRecord.id,
                timestamp = timestamp,
                amountMl = amountMl,
                source = LogSource.EXTERNAL_HEALTH_CONNECT,
                externalRecordId = record.metadata.id
            )
            waterDao.logWaterAndIncrementTotal(entry)
        }
    }

    override fun getCustomQuickAmountsFlow(): Flow<List<Int>> {
        val flow = dataStore?.data
        return if (flow != null) {
            flow.map { preferences ->
                preferences[PreferencesKeys.CUSTOM_QUICK_AMOUNTS]
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.sorted() ?: emptyList()
            }
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }

    override suspend fun addCustomQuickAmount(amountMl: Int) {
        dataStore?.edit { preferences ->
            val current = preferences[PreferencesKeys.CUSTOM_QUICK_AMOUNTS] ?: emptySet()
            if (current.size < 3) {
                preferences[PreferencesKeys.CUSTOM_QUICK_AMOUNTS] = current + amountMl.toString()
            }
        }
    }

    override suspend fun removeCustomQuickAmount(amountMl: Int) {
        dataStore?.edit { preferences ->
            val current = preferences[PreferencesKeys.CUSTOM_QUICK_AMOUNTS] ?: emptySet()
            preferences[PreferencesKeys.CUSTOM_QUICK_AMOUNTS] = current - amountMl.toString()
        }
    }
}
