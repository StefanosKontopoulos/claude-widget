package com.claudewidget.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.claudewidget.auth.CredentialStore
import com.claudewidget.auth.LoginActivity

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
