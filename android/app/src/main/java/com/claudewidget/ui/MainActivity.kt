package com.claudewidget.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.claudewidget.auth.CredentialStore
import com.claudewidget.auth.LoginActivity
import com.claudewidget.worker.UsageFetchWorker
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var statusText: TextView

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.i(TAG, "Login successful")
            updateUI()
        } else {
            Log.w(TAG, "Login cancelled or failed")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        statusText = TextView(this).apply {
            textSize = 18f
        }
        layout.addView(statusText)
        setContentView(layout)
        checkCredentials()
        scheduleUsageFetch()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun checkCredentials() {
        val cookie = CredentialStore.loadSessionCookie(this)
        val orgId = CredentialStore.loadOrgId(this)
        if (cookie == null || orgId == null) {
            Log.i(TAG, "No credentials found, launching login")
            launchLogin()
        } else {
            Log.i(TAG, "Credentials present (org ID: ${orgId.take(8)}...)")
        }
    }

    private fun launchLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        loginLauncher.launch(intent)
    }

    private fun scheduleUsageFetch() {
        val request = PeriodicWorkRequestBuilder<UsageFetchWorker>(
            15, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            UsageFetchWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.i(TAG, "WorkManager: scheduled periodic usage fetch")
    }

    private fun updateUI() {
        val cookie = CredentialStore.loadSessionCookie(this)
        val orgId = CredentialStore.loadOrgId(this)
        statusText.text = if (cookie != null && orgId != null) {
            "Logged in\nOrg ID: ${orgId.take(8)}...\nCookie: ${cookie.take(20)}..."
        } else {
            "Not logged in"
        }
    }
}
