package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.WaterAlarmScheduler
import com.example.data.local.database.WaterDatabase
import com.example.data.local.datastore.dataStore
import com.example.data.local.entity.ActivityLevel
import com.example.data.local.entity.Climate
import com.example.data.local.entity.DayRecord
import com.example.data.local.entity.LogSource
import com.example.data.local.entity.ReminderEvent
import com.example.data.local.entity.UserProfile
import com.example.data.local.entity.WaterLogEntry
import com.example.data.repository.WaterRepository
import com.example.data.repository.WaterRepositoryImpl
import com.example.health.HealthConnectManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WaterViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WaterDatabase.getInstance(application)
    private val dataStore = application.dataStore
    private val repository: WaterRepository = WaterRepositoryImpl(db.waterDao(), dataStore, application, HealthConnectManager(application))
    private val alarmScheduler = WaterAlarmScheduler(application)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    fun getTodayDateString(): String = dateFormat.format(Calendar.getInstance().time)

    val userProfile: StateFlow<UserProfile?> = repository.getUserProfileFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayRecord: StateFlow<DayRecord?> = repository.getTodayRecordFlow(getTodayDateString())
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayWaterLogs: StateFlow<List<WaterLogEntry>> = todayRecord.flatMapLatest { record ->
        if (record != null) {
            repository.getWaterLogsForDayFlow(record.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayReminders: StateFlow<List<ReminderEvent>> = todayRecord.flatMapLatest { record ->
        if (record != null) {
            repository.getReminderEventsForDayFlow(record.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentHistory: StateFlow<List<DayRecord>> = db.waterDao().getRecentDayRecordsFlow(14)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val manualOverrideActive: StateFlow<Boolean> = repository.getManualOverrideActiveFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val fallbackCheckoutTime: StateFlow<String> = repository.getFallbackCheckoutTimeFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "23:59"
        )

    val customQuickAmounts: StateFlow<List<Int>> = repository.getCustomQuickAmountsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val healthConnectSyncEnabled: StateFlow<Boolean> = repository.getHealthConnectSyncEnabledFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun addCustomQuickAmount(amountMl: Int) {
        viewModelScope.launch {
            repository.addCustomQuickAmount(amountMl)
        }
    }

    fun removeCustomQuickAmount(amountMl: Int) {
        viewModelScope.launch {
            repository.removeCustomQuickAmount(amountMl)
        }
    }

    fun calculateBaseTargetMl(weightKg: Float, activityLevel: ActivityLevel, climate: Climate): Int {
        return repository.calculateBaseTargetMl(weightKg, activityLevel, climate)
    }

    fun saveUserProfile(
        heightCm: Float,
        weightKg: Float,
        activityLevel: ActivityLevel,
        climate: Climate,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.saveUserProfile(heightCm, weightKg, activityLevel, climate)
            onComplete()
        }
    }

    fun checkInToday() {
        viewModelScope.launch {
            val record = repository.checkInToday(getTodayDateString())
            recalculateAndScheduleReminders(record.id)
        }
    }

    fun checkOutToday() {
        viewModelScope.launch {
            val currentRecord = todayRecord.value ?: return@launch
            repository.checkOutToday(currentRecord.id)
        }
    }

    fun logWater(amountMl: Int, source: LogSource = LogSource.MANUAL_APP_OPEN, reminderEventId: Long? = null) {
        viewModelScope.launch {
            var currentRecord = todayRecord.value
            if (currentRecord == null) {
                currentRecord = repository.checkInToday(getTodayDateString())
            }

            repository.logWater(
                dayRecordId = currentRecord.id,
                amountMl = amountMl,
                source = source,
                reminderEventId = reminderEventId
            )

            // Dynamic rescheduling (§5.3)
            recalculateAndScheduleReminders(currentRecord.id)
        }
    }

    fun deleteWaterLog(entry: WaterLogEntry) {
        viewModelScope.launch {
            repository.deleteWaterLog(entry)
            val currentRecord = todayRecord.value
            if (currentRecord != null) {
                recalculateAndScheduleReminders(currentRecord.id)
            }
        }
    }

    fun updateAdaptiveTarget(newTargetMl: Int) {
        viewModelScope.launch {
            repository.updateAdaptiveTarget(newTargetMl)
            // Implicitly updating manual target means we override
            setManualOverrideActive(true)
            val currentRecord = todayRecord.value
            if (currentRecord != null) {
                db.waterDao().updateDayRecord(currentRecord.copy(targetMlForDay = newTargetMl))
                recalculateAndScheduleReminders(currentRecord.id)
            }
        }
    }

    fun setManualOverrideActive(isActive: Boolean) {
        viewModelScope.launch {
            repository.setManualOverrideActive(isActive)
        }
    }

    fun setHealthConnectSyncEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            repository.setHealthConnectSyncEnabled(isEnabled)
        }
    }

    fun updateFallbackCheckoutTime(timeString: String) {
        viewModelScope.launch {
            repository.setFallbackCheckoutTime(timeString)
            val currentRecord = todayRecord.value
            if (currentRecord != null) {
                recalculateAndScheduleReminders(currentRecord.id)
            }
        }
    }

    private suspend fun recalculateAndScheduleReminders(dayRecordId: Long) {
        val now = System.currentTimeMillis()
        val checkoutTimeStr = fallbackCheckoutTime.value // e.g. "23:59"
        val parts = checkoutTimeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 23
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 59

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!cal.timeInMillis.let { it > now }) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        val estCheckoutEpoch = cal.timeInMillis

        val scheduledReminders = repository.recalculateEvenSpacedReminders(
            dayRecordId = dayRecordId,
            currentTimeEpoch = now,
            estimatedCheckoutEpoch = estCheckoutEpoch,
            averageDesiredGapMin = 75
        )

        // Schedule exact alarms via AlarmManager
        scheduledReminders.forEach { reminder ->
            alarmScheduler.scheduleExactAlarm(
                reminderEventId = reminder.id,
                scheduledTimeEpoch = reminder.timestamp,
                defaultMl = 250
            )
        }
    }
}
