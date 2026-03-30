package com.claudewidget.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.claudewidget.auth.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

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

    /** Clear cached usage data (called on sign-out) */
    suspend fun clearCache(context: Context) {
        context.usageDataStore.edit { it.clear() }
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

    // MARK: - Network

    suspend fun fetchAndStore(context: Context): Result<UsageData> = withContext(Dispatchers.IO) {
        val cookie = CredentialStore.loadSessionCookie(context)
            ?: return@withContext Result.failure(IllegalStateException("No session cookie"))
        val orgId = CredentialStore.loadOrgId(context)
            ?: return@withContext Result.failure(IllegalStateException("No org ID"))

        val url = "https://claude.ai/api/organizations/$orgId/usage"
        val request = Request.Builder()
            .url(url)
            .addHeader("Cookie", cookie)
            .build()

        return@withContext try {
            val response = OkHttpClient().newCall(request).execute()
            response.use { r ->
                when (r.code) {
                    401, 403 -> {
                        CredentialStore.clear(context)
                        postAuthExpiredNotification(context)
                        Result.failure(IOException("Auth expired: ${r.code}"))
                    }
                    in 200..299 -> {
                        val body = r.body?.string()
                            ?: return@use Result.failure(IOException("Empty response body"))
                        val usageResponse = json.decodeFromString<UsageResponse>(body)
                        val data = UsageData(response = usageResponse)
                        save(context, data)
                        Result.success(data)
                    }
                    else -> Result.failure(IOException("HTTP ${r.code}"))
                }
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private fun postAuthExpiredNotification(context: Context) {
        val channelId = "auth_expired"
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId, "Authentication Alerts", NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Claude session expired")
            .setContentText("Tap to sign in again")
            .setAutoCancel(true)
            .build()
        manager.notify(1001, notification)
    }
}
