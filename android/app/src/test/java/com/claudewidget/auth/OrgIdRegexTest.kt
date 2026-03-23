package com.claudewidget.auth

import org.junit.Assert.*
import org.junit.Test

class OrgIdRegexTest {
    private val regex = LoginActivity.ORG_ID_REGEX

    @Test
    fun matchesStandardOrgIdUrl() {
        val url = "https://claude.ai/api/organizations/a1b2c3d4-e5f6-7890-abcd-ef1234567890/usage"
        val match = regex.find(url)
        assertNotNull("Should match standard org URL", match)
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", match!!.groupValues[1])
    }

    @Test
    fun matchesOrgIdInMiddleOfPath() {
        val url = "https://claude.ai/api/organizations/12345678-1234-1234-1234-123456789abc/settings/billing"
        val match = regex.find(url)
        assertNotNull(match)
        assertEquals("12345678-1234-1234-1234-123456789abc", match!!.groupValues[1])
    }

    @Test
    fun doesNotMatchNonUuidPath() {
        val url = "https://claude.ai/api/organizations/notauuid/usage"
        val match = regex.find(url)
        assertNull("Should not match non-UUID", match)
    }

    @Test
    fun doesNotMatchUnrelatedUrl() {
        val url = "https://claude.ai/login"
        val match = regex.find(url)
        assertNull("Should not match login URL", match)
    }

    @Test
    fun matchesUrlWithQueryParams() {
        val url = "https://claude.ai/api/organizations/abcdef12-3456-7890-abcd-ef1234567890/usage?period=5h"
        val match = regex.find(url)
        assertNotNull(match)
        assertEquals("abcdef12-3456-7890-abcd-ef1234567890", match!!.groupValues[1])
    }
}
