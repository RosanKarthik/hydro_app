package com.example.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WaterDatabase : RoomDatabase() {
    abstract fun waterDao(): WaterDao

    companion object {
        @Volatile
        private var INSTANCE: WaterDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE water_log_entry ADD COLUMN externalRecordId TEXT")
            }
        }

        fun getInstance(context: android.content.Context): WaterDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    WaterDatabase::class.java,
                    "water_reminder.db"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
