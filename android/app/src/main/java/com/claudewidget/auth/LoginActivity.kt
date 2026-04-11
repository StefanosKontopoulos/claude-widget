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
                function hide() {
                  try {
                    var clickables = document.querySelectorAll('button, a, [role="button"]');
                    for (var i = 0; i < clickables.length; i++) {
                      var el = clickables[i];
                      var text = (el.innerText || '').trim().toLowerCase();
                      var matchesText = text.indexOf('google') !== -1;
                      var matchesMedia = false;
                      if (!matchesText) {
                        var media = el.querySelectorAll('img, svg');
                        for (var j = 0; j < media.length; j++) {
                          var alt = (media[j].getAttribute('alt') || '').toLowerCase();
                          var aria = (media[j].getAttribute('aria-label') || '').toLowerCase();
                          if (alt.indexOf('google') !== -1 || aria.indexOf('google') !== -1) {
                            matchesMedia = true;
                            break;
                          }
                        }
                      }
                      if (matchesText || matchesMedia) {
                        el.style.display = 'none';
                        var parent = el.parentElement;
                        if (parent) {
                          var siblingButtons = parent.querySelectorAll(':scope > button, :scope > a, :scope > [role="button"]');
                          if (siblingButtons.length <= 1) {
                            parent.style.display = 'none';
                          }
                        }
                      }
                    }
                    var dividers = document.querySelectorAll('div, span, hr');
                    for (var k = 0; k < dividers.length; k++) {
                      var d = dividers[k];
                      var dt = (d.innerText || '').trim();
                      if (dt === 'or' || dt === 'OR') {
                        d.style.display = 'none';
                      }
                    }
                  } catch (inner) {
                    console.warn('hide() inner error', inner);
                  }
                }
                hide();
                setTimeout(hide, 300);
                setTimeout(hide, 800);
                setTimeout(hide, 1500);
              } catch (e) {
                console.warn('HIDE_GOOGLE_JS failed', e);
              }
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

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && url.contains("claude.ai")) {
                    view?.evaluateJavascript(HIDE_GOOGLE_JS, null)
                    Log.d(TAG, "Injected Google-hide JS on $url")
                }
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
