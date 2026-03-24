package com.claudewidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.action.clickable
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import com.claudewidget.auth.CredentialStore
import com.claudewidget.data.UsageData
import com.claudewidget.data.UsagePeriod
import com.claudewidget.data.UsageRepository
import com.claudewidget.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClaudeUsageWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(180.dp, 120.dp)
        private val MEDIUM = DpSize(250.dp, 120.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val hasCreds = CredentialStore.loadSessionCookie(context) != null
        val cached = UsageRepository.getCached(context)
        provideContent {
            WidgetContent(hasCreds = hasCreds, cached = cached)
        }
    }
}

@Composable
private fun WidgetContent(hasCreds: Boolean, cached: UsageData?) {
    val isStale = cached != null &&
        (System.currentTimeMillis() - cached.fetchedAt) > 2 * 60 * 60 * 1000L
    val size = LocalSize.current
    val isMedium = size.width >= 250.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center
    ) {
        when {
            !hasCreds -> NotLoggedInState()
            cached == null -> LoadingState()
            else -> DataState(data = cached, isStale = isStale, isMedium = isMedium)
        }
    }
}

@Composable
private fun NotLoggedInState() {
    Text(
        text = "Sign in to Claude app",
        style = TextStyle(
            color = ColorProvider(Color.White),
            fontSize = 14.sp
        )
    )
}

@Composable
private fun LoadingState() {
    Text(
        text = "Loading...",
        style = TextStyle(
            color = ColorProvider(Color.White),
            fontSize = 14.sp
        )
    )
}

@Composable
private fun DataState(data: UsageData, isStale: Boolean, isMedium: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp)
    ) {
        // Title
        val title = if (isStale) "Claude Usage (stale)" else "Claude Usage"
        Text(
            text = title,
            style = TextStyle(
                color = ColorProvider(Color(0xFFD4A843)),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        )

        Spacer(modifier = GlanceModifier.height(6.dp))

        // 5-Hour row
        UsageRow(label = "5 Hour", period = data.response.fiveHour)

        Spacer(modifier = GlanceModifier.height(4.dp))

        // 7-Day row
        UsageRow(label = "7 Day", period = data.response.sevenDay)

        Spacer(modifier = GlanceModifier.height(6.dp))

        // Reset time
        Text(
            text = "Resets ${data.response.fiveHour.formatResetTime()}",
            style = TextStyle(
                color = ColorProvider(Color(0xB3FFFFFF)),
                fontSize = 10.sp
            )
        )

        if (isMedium) {
            Spacer(modifier = GlanceModifier.height(4.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Updated ${formatUpdatedTime(data.fetchedAt)}",
                    style = TextStyle(
                        color = ColorProvider(Color(0x80FFFFFF)),
                        fontSize = 9.sp
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Button(
                    text = "Refresh",
                    onClick = actionRunCallback<ForceRefreshAction>(),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = ColorProvider(Color(0x33FFFFFF)),
                        contentColor = ColorProvider(Color.White)
                    )
                )
            }
        } else {
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = "Updated ${formatUpdatedTime(data.fetchedAt)}",
                style = TextStyle(
                    color = ColorProvider(Color(0x80FFFFFF)),
                    fontSize = 9.sp
                )
            )
        }
    }
}

@Composable
private fun UsageRow(label: String, period: UsagePeriod) {
    val color = progressColor(period.fraction)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 11.sp
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "${period.percent}%",
                style = TextStyle(
                    color = ColorProvider(color),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
        }
        LinearProgressIndicator(
            progress = period.fraction.toFloat(),
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = ColorProvider(color),
            backgroundColor = ColorProvider(Color(0x33FFFFFF))
        )
    }
}

private fun progressColor(fraction: Double): Color {
    return when {
        fraction < 0.70 -> Color(0xFF2ECC71)
        fraction < 0.90 -> Color(0xFFF39C12)
        else -> Color(0xFFE74C3C)
    }
}

private fun formatUpdatedTime(fetchedAt: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(fetchedAt))
}
