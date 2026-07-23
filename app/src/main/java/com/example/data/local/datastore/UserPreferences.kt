package com.example.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

object PreferencesKeys {
    val MANUAL_OVERRIDE_ACTIVE = booleanPreferencesKey("manual_override_active")
    val FALLBACK_CHECKOUT_TIME = androidx.datastore.preferences.core.stringPreferencesKey("fallback_checkout_time")
}
