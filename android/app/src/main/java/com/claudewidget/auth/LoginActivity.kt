package com.claudewidget.auth

import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LoginActivity"
        private const val LOGIN_URL = "https://claude.ai/login"
        private const val ORG_ID_TIMEOUT_MS = 10_000L
        val ORG_ID_REGEX = Regex("/api/organizations/([0-9a-f-]{36})/")
    }

    private var capturedOrgId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()
                val match = ORG_ID_REGEX.find(url)
                if (match != null && capturedOrgId == null) {
                    capturedOrgId = match.groupValues[1]
                    Log.d(TAG, "Captured org ID: $capturedOrgId")
                }
                return null
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (url.contains("claude.ai") && !url.contains("login")) {
                    val cookie = CookieManager.getInstance().getCookie("https://claude.ai")
                    if (!cookie.isNullOrEmpty()) {
                        handleLoginSuccess(cookie)
                    }
                }
            }
        }

        webView.loadUrl(LOGIN_URL)
    }

    private fun handleLoginSuccess(cookie: String) {
        lifecycleScope.launch {
            val deadline = System.currentTimeMillis() + ORG_ID_TIMEOUT_MS
            while (capturedOrgId == null && System.currentTimeMillis() < deadline) {
                delay(200)
            }
            if (capturedOrgId != null) {
                CredentialStore.save(this@LoginActivity, cookie, capturedOrgId!!)
                CookieManager.getInstance().flush()
                setResult(RESULT_OK)
                finish()
            } else {
                showOrgIdErrorDialog()
            }
        }
    }

    private fun showOrgIdErrorDialog() {
        AlertDialog.Builder(this)
            .setTitle("Unable to capture Organization ID")
            .setMessage("Please try logging in again. If this persists, the Claude.ai interface may have changed.")
            .setPositiveButton("Retry") { _, _ ->
                capturedOrgId = null
                val webView = findViewById<WebView>(android.R.id.content)
                    ?.let { (it as? android.view.ViewGroup)?.getChildAt(0) as? WebView }
                webView?.loadUrl(LOGIN_URL)
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }
}
