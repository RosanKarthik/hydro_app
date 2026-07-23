package com.example.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.local.converter.Converters
import com.example.data.local.dao.WaterDao
import com.example.data.local.entity.DayRecord
import com.example.data.local.entity.ReminderEvent
import com.example.data.local.entity.UserProfile
import com.example.data.local.entity.WaterLogEntry

/**
 * Main Room Database class for the Water Reminder Application.
 */
@Database(
    entities = [
        UserProfile::class,
        DayRecord::class,
        WaterLogEntry::class,
        ReminderEvent::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WaterDatabase : RoomDatabase() {
    abstract fun waterDao(): WaterDao

    companion object {
        @Volatile
        private var INSTANCE: WaterDatabase? = null

        fun getInstance(context: android.content.Context): WaterDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    WaterDatabase::class.java,
                    "water_reminder.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
