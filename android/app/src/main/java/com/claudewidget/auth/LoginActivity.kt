package com.claudewidget.auth

import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LoginActivity"
        private const val LOGIN_URL = "https://claude.ai/login"
        val ORG_ID_REGEX = Regex("/api/organizations/([0-9a-f-]{36})/")
    }

    private var capturedOrgId: String? = null
    private var loginHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        // Clear WebView cookies so the user can choose a different account
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.removeAllCookies(null)
        cookieManager.flush()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()
                val match = ORG_ID_REGEX.find(url)
                if (match != null && capturedOrgId == null) {
                    capturedOrgId = match.groupValues[1]
                    Log.i(TAG, "Captured org ID: $capturedOrgId")
                    // Org API calls prove the user is authenticated — save credentials
                    val cookie = CookieManager.getInstance().getCookie("https://claude.ai")
                    if (!cookie.isNullOrEmpty()) {
                        runOnUiThread { completeLogin(cookie) }
                    }
                }
                return null
            }
        }

        webView.loadUrl(LOGIN_URL)
    }

    private fun completeLogin(cookie: String) {
        if (loginHandled) return
        loginHandled = true

        val orgId = capturedOrgId ?: return
        Log.i(TAG, "Saving credentials (org: ${orgId.take(8)}..., cookie length: ${cookie.length})")
        CredentialStore.save(this, cookie, orgId)
        CookieManager.getInstance().flush()
        setResult(RESULT_OK)
        finish()
    }
}
