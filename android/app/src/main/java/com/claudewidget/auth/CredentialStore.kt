package com.claudewidget.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object CredentialStore {

    private const val PREFS_FILE = "auth_credentials"
    private const val KEY_SESSION_COOKIE = "session_cookie"
    private const val KEY_ORG_ID = "org_id"

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(context: Context, sessionCookie: String, orgId: String) {
        getPrefs(context).edit()
            .putString(KEY_SESSION_COOKIE, sessionCookie)
            .putString(KEY_ORG_ID, orgId)
            .apply()
    }

    fun loadSessionCookie(context: Context): String? =
        getPrefs(context).getString(KEY_SESSION_COOKIE, null)

    fun loadOrgId(context: Context): String? =
        getPrefs(context).getString(KEY_ORG_ID, null)

    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
