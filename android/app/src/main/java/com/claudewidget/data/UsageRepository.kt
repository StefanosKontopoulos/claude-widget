package com.claudewidget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

val Context.usageDataStore: DataStore<Preferences> by preferencesDataStore(name = "usage_data")

object UsageRepository {

    private val KEY_RESPONSE_JSON = stringPreferencesKey("response_json")
    private val KEY_FETCHED_AT = longPreferencesKey("fetched_at")
    private val KEY_CANARY = stringPreferencesKey("canary")

    private val json = Json { ignoreUnknownKeys = true }

    /** Write a test value -- used in Phase 1 to verify DataStore is accessible */
    suspend fun writeCanary(context: Context, value: String) {
        context.usageDataStore.edit { prefs ->
            prefs[KEY_CANARY] = value
        }
    }

    /** Read back the canary value -- returns null if never written */
    suspend fun readCanary(context: Context): String? {
        val prefs = context.usageDataStore.data.first()
        return prefs[KEY_CANARY]
    }

    /** Persist a UsageData object to DataStore (called after successful API fetch) */
    suspend fun save(context: Context, data: UsageData) {
        val responseJson = json.encodeToString(UsageResponse.serializer(), data.response)
        context.usageDataStore.edit { prefs ->
            prefs[KEY_RESPONSE_JSON] = responseJson
            prefs[KEY_FETCHED_AT] = data.fetchedAt
        }
    }

    /** Read cached UsageData -- returns null if nothing is stored yet */
    suspend fun getCached(context: Context): UsageData? {
        val prefs = context.usageDataStore.data.first()
        val responseJson = prefs[KEY_RESPONSE_JSON] ?: return null
        val fetchedAt = prefs[KEY_FETCHED_AT] ?: System.currentTimeMillis()
        return try {
            val response = json.decodeFromString<UsageResponse>(responseJson)
            UsageData(response = response, fetchedAt = fetchedAt)
        } catch (e: Exception) {
            null
        }
    }
}
