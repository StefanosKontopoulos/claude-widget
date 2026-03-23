package com.claudewidget.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class UsageDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val sampleJson = """
        {
            "five_hour": {
                "utilization": 42.5,
                "resets_at": "2026-03-23T13:00:00.886839+00:00"
            },
            "seven_day": {
                "utilization": 78.0,
                "resets_at": "2026-03-30T00:00:00.000000+00:00"
            },
            "extra_usage": null,
            "opus": null
        }
    """.trimIndent()

    @Test
    fun `deserialize full response with unknown keys`() {
        val response = json.decodeFromString<UsageResponse>(sampleJson)
        assertNotNull(response)
        assertEquals(42.5, response.fiveHour.utilization, 0.001)
        assertEquals(78.0, response.sevenDay.utilization, 0.001)
    }

    @Test
    fun `fraction is utilization divided by 100`() {
        val period = UsagePeriod(utilization = 5.0, resetsAt = "2026-03-23T13:00:00.000000+00:00")
        assertEquals(0.05, period.fraction, 0.001)
    }

    @Test
    fun `fraction is clamped to 1 when utilization exceeds 100`() {
        val period = UsagePeriod(utilization = 150.0, resetsAt = "2026-03-23T13:00:00.000000+00:00")
        assertEquals(1.0, period.fraction, 0.001)
    }

    @Test
    fun `fraction is 0 when utilization is 0`() {
        val period = UsagePeriod(utilization = 0.0, resetsAt = "2026-03-23T13:00:00.000000+00:00")
        assertEquals(0.0, period.fraction, 0.001)
    }

    @Test
    fun `parse ISO-8601 date with fractional seconds`() {
        val raw = "2026-03-23T13:00:00.886839+00:00"
        val parsed = OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        assertEquals(13, parsed.hour)
        assertEquals(0, parsed.minute)
    }

    @Test
    fun `format reset date as day and time in UTC`() {
        val raw = "2026-03-23T13:00:00.886839+00:00"
        val parsed = OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val formatter = DateTimeFormatter.ofPattern("EEE h:mm a").withZone(ZoneOffset.UTC)
        val formatted = formatter.format(parsed)
        assertEquals("Mon 1:00 PM", formatted)
    }

    @Test
    fun `serialize UsageData round-trip`() {
        val period = UsagePeriod(utilization = 50.0, resetsAt = "2026-03-23T13:00:00.000000+00:00")
        val response = UsageResponse(fiveHour = period, sevenDay = period)
        val encoded = json.encodeToString(UsageResponse.serializer(), response)
        val decoded = json.decodeFromString<UsageResponse>(encoded)
        assertEquals(50.0, decoded.fiveHour.utilization, 0.001)
    }
}
