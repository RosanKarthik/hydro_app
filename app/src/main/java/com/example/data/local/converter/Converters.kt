package com.example.data.local.converter

import androidx.room.TypeConverter
import com.example.data.local.entity.ActivityLevel
import com.example.data.local.entity.Climate
import com.example.data.local.entity.Gender
import com.example.data.local.entity.LogSource
import com.example.data.local.entity.ReminderResponse

/**
 * Room TypeConverters for custom Enums and Date/Time mapping.
 */
class Converters {

    @TypeConverter
    fun fromGender(value: Gender?): String? = value?.name

    @TypeConverter
    fun toGender(value: String?): Gender? = value?.let { enumValueOf<Gender>(it) }

    @TypeConverter
    fun fromActivityLevel(value: ActivityLevel?): String? = value?.name

    @TypeConverter
    fun toActivityLevel(value: String?): ActivityLevel? = value?.let { enumValueOf<ActivityLevel>(it) }

    @TypeConverter
    fun fromClimate(value: Climate?): String? = value?.name

    @TypeConverter
    fun toClimate(value: String?): Climate? = value?.let { enumValueOf<Climate>(it) }

    @TypeConverter
    fun fromLogSource(value: LogSource?): String? = value?.name

    @TypeConverter
    fun toLogSource(value: String?): LogSource? = value?.let { enumValueOf<LogSource>(it) }

    @TypeConverter
    fun fromReminderResponse(value: ReminderResponse?): String? = value?.name

    @TypeConverter
    fun toReminderResponse(value: String?): ReminderResponse? = value?.let { enumValueOf<ReminderResponse>(it) }
}
