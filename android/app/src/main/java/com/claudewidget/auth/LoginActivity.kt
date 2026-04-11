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

        // JS injected after page load to hide the Google sign-in button on the
        // claude.ai login page. Google OAuth does not work inside an embedded
        // WebView, so surfacing the button only confuses users. The script is
        // re-run on a short schedule to catch React re-renders / hydration.
        private const val HIDE_GOOGLE_JS = """
            (function() {
              try {
                function hideGoogle() {
                  var els = document.querySelectorAll('button, a, [role="button"]');
                  for (var i = 0; i < els.length; i++) {
                    var el = els[i];
                    var text = (el.innerText || el.textContent || '').trim().toLowerCase();
                    if (text.indexOf('google') !== -1) {
                      el.style.setProperty('display', 'none', 'important');
                    }
                  }
                  var dividers = document.querySelectorAll('p, span, div');
                  for (var j = 0; j < dividers.length; j++) {
                    var txt = (dividers[j].innerText || dividers[j].textContent || '').trim();
                    if (txt === 'OR' || txt === 'or' || txt === 'Or') {
                      dividers[j].style.setProperty('display', 'none', 'important');
                    }
                  }
                }
                hideGoogle();
                setTimeout(hideGoogle, 500);
                setTimeout(hideGoogle, 1500);
              } catch(e) {}
            })();
        """
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

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && url.contains("claude.ai")) {
                    view?.evaluateJavascript(HIDE_GOOGLE_JS, null)
                    Log.d(TAG, "Injected Google-hide JS on $url")
                }
            }
        }

        // Clear cache + history synchronously before we kick off cookie clearing
        webView.clearCache(true)
        webView.clearHistory()

        // removeAllCookies is async — loadUrl MUST run inside the callback so the
        // login page isn't loaded with stale session cookies (which cause claude.ai
        // to redirect to its marketing landing page instead of /login).
        cookieManager.removeAllCookies { _ ->
            cookieManager.flush()
            webView.loadUrl(LOGIN_URL)
        }
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
