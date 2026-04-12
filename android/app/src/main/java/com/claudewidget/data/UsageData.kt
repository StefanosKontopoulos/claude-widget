package com.claudewidget.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class UsagePeriod(
    val utilization: Double,
    @SerialName("resets_at") val resetsAt: String?
) {
    /** Progress bar fraction: 0.0 to 1.0 */
    val fraction: Double
        get() = (utilization / 100.0).coerceIn(0.0, 1.0)

    /** Integer percentage for display: 0 to 100 */
    val percent: Int
        get() = utilization.toInt().coerceIn(0, 100)

    /** "Mon 9:00 AM" in user's local timezone; "soon" if parsing fails or resets_at is null */
    fun formatResetTime(): String {
        val raw = resetsAt ?: return "soon"
        return try {
            val dt = OffsetDateTime.parse(raw)
            val formatter = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.getDefault())
            dt.atZoneSameInstant(ZoneId.systemDefault()).format(formatter)
        } catch (_: Exception) {
            "soon"
        }
    }
}

@Serializable
data class UsageResponse(
    @SerialName("five_hour") val fiveHour: UsagePeriod,
    @SerialName("seven_day") val sevenDay: UsagePeriod
)

/**
 * Wraps the API response with a local fetch timestamp.
 * Not serialized to JSON -- stored as two fields in DataStore.
 */
data class UsageData(
    val response: UsageResponse,
    val fetchedAt: Long = System.currentTimeMillis()
)
