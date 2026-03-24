package com.claudewidget.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.claudewidget.auth.CredentialStore
import com.claudewidget.auth.LoginActivity
import com.claudewidget.data.UsageData
import com.claudewidget.data.UsagePeriod
import com.claudewidget.data.UsageRepository
import com.claudewidget.worker.UsageFetchWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// Theme colors
private val DarkBg = Color(0xFF0D0D0D)
private val CardBg = Color(0xFF1A1A1A)
private val CardBorder = Color(0xFF2A2A2A)
private val GoldAccent = Color(0xFFD4A843)
private val TextPrimary = Color(0xFFE8E8E8)
private val TextSecondary = Color(0xFF888888)
private val TextTertiary = Color(0xFF555555)
private val GreenColor = Color(0xFF2ECC71)
private val OrangeColor = Color(0xFFF39C12)
private val RedColor = Color(0xFFE74C3C)

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val loginResult = mutableStateOf(0) // increment to trigger recomposition

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.i(TAG, "Login successful")
            loginResult.value++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = this

        setContent {
            val loginTrigger by loginResult
            var isLoggedIn by remember { mutableStateOf(CredentialStore.loadSessionCookie(context) != null) }
            var usageData by remember { mutableStateOf<UsageData?>(null) }
            var isRefreshing by remember { mutableStateOf(false) }
            var orgId by remember { mutableStateOf(CredentialStore.loadOrgId(context)) }
            val scope = rememberCoroutineScope()

            // React to login completion
            LaunchedEffect(loginTrigger) {
                isLoggedIn = CredentialStore.loadSessionCookie(context) != null
                orgId = CredentialStore.loadOrgId(context)
                if (isLoggedIn) {
                    usageData = UsageRepository.getCached(context)
                    if (usageData == null) {
                        isRefreshing = true
                        UsageRepository.fetchAndStore(context).onSuccess { usageData = it }
                        isRefreshing = false
                    }
                }
            }

            // Load cached data on first launch
            LaunchedEffect(Unit) {
                usageData = UsageRepository.getCached(context)
                if (isLoggedIn && usageData == null) {
                    isRefreshing = true
                    UsageRepository.fetchAndStore(context).onSuccess { usageData = it }
                    isRefreshing = false
                }
            }

            DashboardScreen(
                isLoggedIn = isLoggedIn,
                usageData = usageData,
                isRefreshing = isRefreshing,
                orgId = orgId,
                onLogin = {
                    loginLauncher.launch(Intent(context, LoginActivity::class.java))
                },
                onLogout = {
                    CredentialStore.clear(context)
                    isLoggedIn = false
                    usageData = null
                    orgId = null
                },
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        val result = UsageRepository.fetchAndStore(context)
                        result.onSuccess { usageData = it }
                        result.onFailure {
                            if (CredentialStore.loadSessionCookie(context) == null) {
                                isLoggedIn = false
                            }
                        }
                        isRefreshing = false
                    }
                }
            )
        }

        scheduleUsageFetch()
    }

    private fun scheduleUsageFetch() {
        val request = PeriodicWorkRequestBuilder<UsageFetchWorker>(
            15, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            UsageFetchWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.i(TAG, "WorkManager: scheduled periodic usage fetch")
    }
}

@Composable
private fun DashboardScreen(
    isLoggedIn: Boolean,
    usageData: UsageData?,
    isRefreshing: Boolean,
    orgId: String?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Header
            Text(
                text = "Claude",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = GoldAccent
            )
            Text(
                text = "Usage Monitor",
                fontSize = 15.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (!isLoggedIn) {
                // Sign-in prompt
                DashboardCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Claude icon circle
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(CardBorder),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "C",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldAccent
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Sign in to view your usage",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Connect your Claude account to get started",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onLogin,
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(
                                "Sign in to Claude",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            } else {
                // Usage gauges
                if (usageData != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        UsageGaugeCard(
                            title = "5 Hour",
                            period = usageData.response.fiveHour,
                            modifier = Modifier.weight(1f)
                        )
                        UsageGaugeCard(
                            title = "7 Day",
                            period = usageData.response.sevenDay,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Updated time + refresh
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Updated ${formatTime(usageData.fetchedAt)}",
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CardBg)
                                .clickable(enabled = !isRefreshing) { onRefresh() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = GoldAccent,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "\u21BB",
                                    color = GoldAccent,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }
                } else if (isRefreshing) {
                    DashboardCard {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = GoldAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // How to add widget
                SectionTitle("Add Widget")
                DashboardCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SetupStep(1, "Long-press on your home screen")
                        SetupStep(2, "Tap \"Widgets\"")
                        SetupStep(3, "Search for \"Claude Widget\"")
                        SetupStep(4, "Drag to your home screen")
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "The widget auto-refreshes every 15 minutes. " +
                                "Tap the refresh button on the widget for an instant update. " +
                                "Tap the widget to open this app.",
                            color = TextTertiary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Widget sizes
                SectionTitle("Widget Sizes")
                DashboardCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        WidgetSizeInfo(
                            name = "Small (2\u00D72)",
                            desc = "Compact circle gauges showing 5H and 7D usage"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        WidgetSizeInfo(
                            name = "Medium (4\u00D72)",
                            desc = "Progress bars with reset times and percentages"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Account
                SectionTitle("Account")
                DashboardCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        AccountRow("Status", "Connected", GreenColor)
                        if (orgId != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            AccountRow("Organization", "${orgId.take(8)}...", TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        AccountRow("Auto-refresh", "Every 15 min", TextPrimary)
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onLogin,
                                border = BorderStroke(1.dp, CardBorder),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Text("Re-login", color = TextPrimary, fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = onLogout,
                                border = BorderStroke(1.dp, Color(0xFF3D1F1F)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Text("Sign Out", color = RedColor, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// --- Composable building blocks ---

@Composable
private fun DashboardCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        content()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
private fun UsageGaugeCard(title: String, period: UsagePeriod, modifier: Modifier = Modifier) {
    val gaugeColor = progressColor(period.fraction)

    DashboardCard {
        Column(
            modifier = modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Circular gauge drawn with Compose Canvas
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .drawBehind {
                        val strokeWidth = 10.dp.toPx()
                        val arcSize = size.minDimension - strokeWidth
                        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                        // Track
                        drawArc(
                            color = CardBorder,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Progress
                        if (period.fraction > 0.01) {
                            drawArc(
                                color = gaugeColor,
                                startAngle = -90f,
                                sweepAngle = 360f * period.fraction.toFloat(),
                                useCenter = false,
                                topLeft = topLeft,
                                size = Size(arcSize, arcSize),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${period.percent}%",
                        color = gaugeColor,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Resets ${period.formatResetTime()}",
                color = TextTertiary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SetupStep(number: Int, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(CardBorder),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                color = GoldAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun WidgetSizeInfo(name: String, desc: String) {
    Column {
        Text(
            text = name,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = desc,
            color = TextTertiary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun AccountRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
        Text(text = value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// --- Helpers ---

private fun progressColor(fraction: Double): Color {
    return when {
        fraction < 0.50 -> GreenColor
        fraction < 0.75 -> Color(0xFF27AE60)
        fraction < 0.90 -> OrangeColor
        else -> RedColor
    }
}

private fun formatTime(millis: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
}
