package com.claudewidget.auth

import org.junit.Assert.*
import org.junit.Test

class LoginDetectionTest {

    // Mirrors the exact condition used in LoginActivity.onPageFinished
    private fun isPostLogin(url: String): Boolean =
        url.contains("claude.ai") && !url.contains("login")

    @Test
    fun loginPageIsNotPostLogin() {
        assertFalse(isPostLogin("https://claude.ai/login"))
    }

    @Test
    fun loginSubpathIsNotPostLogin() {
        assertFalse(isPostLogin("https://claude.ai/login?redirect=chat"))
    }

    @Test
    fun mainPageIsPostLogin() {
        assertTrue(isPostLogin("https://claude.ai/"))
    }

    @Test
    fun chatPageIsPostLogin() {
        assertTrue(isPostLogin("https://claude.ai/chat"))
    }

    @Test
    fun nonClaudeUrlIsNotPostLogin() {
        assertFalse(isPostLogin("https://example.com/dashboard"))
    }

    @Test
    fun claudeApiUrlIsPostLogin() {
        assertTrue(isPostLogin("https://claude.ai/api/organizations/uuid/usage"))
    }
}
