package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ActivityLevel {
    SEDENTARY,
    LIGHT,
    MODERATE,
    HIGH
}

enum class Climate {
    TEMPERATE,
    HOT,
    HUMID
}

/**
 * User Profile entity storing baseline body metrics, activity level, climate,
 * and target water intake metrics.
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey
    val id: Long = 1L,
    val heightCm: Float,
    val weightKg: Float,
    val activityLevel: ActivityLevel,
    val climate: Climate = Climate.TEMPERATE,
    val baseTargetMl: Int,
    val adaptiveTargetMl: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
