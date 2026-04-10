package com.claudewidget.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.claudewidget.auth.CredentialStore
import com.claudewidget.auth.LoginActivity
import com.claudewidget.data.UsageData
import com.claudewidget.data.UsagePeriod
import com.claudewidget.data.UsageRepository
import com.claudewidget.widget.STALE_THRESHOLD_MS
import com.claudewidget.worker.StaleCheckWorker
import com.claudewidget.worker.UsageFetchWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// ─── Design System ──────────────────────────────────────────────────
private val AppBg = Color(0xFF121212)
private val CardBg = Color(0xFF1E1E24)
private val CardBorder = Color(0x0DFFFFFF)
private val BezelHighlight = Color(0xFF5A5A68)
private val BezelShadow = Color(0xFF050506)
private val Gold = Color(0xFFE2B973)
private val TextWhite = Color(0xFFE0E0E0)
private val TextGrey = Color(0xFF9CA3AF)
private val Green = Color(0xFF4ADE80)
private val Orange = Color(0xFFFB923C)
private val Red = Color(0xFFEF4444)
private val TrackColor = Color(0xFF2A2A2A)
private val DividerColor = Color(0xFF2A2A2E)

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val loginResult = mutableStateOf(0)

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
                    // Refresh widgets after login so they pick up new data
                    val update = OneTimeWorkRequestBuilder<StaleCheckWorker>().build()
                    WorkManager.getInstance(context).enqueue(update)
                }
            }

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
                    scope.launch {
                        CredentialStore.clear(context)
                        UsageRepository.clearCache(context)
                        val update = OneTimeWorkRequestBuilder<StaleCheckWorker>().build()
                        WorkManager.getInstance(context).enqueue(update)
                        isLoggedIn = false
                        usageData = null
                        orgId = null
                    }
                },
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        val result = UsageRepository.fetchAndStore(context)
                        result.onSuccess {
                            usageData = it
                            // Update widget immediately via WorkManager (same path that already works)
                            val immediate = OneTimeWorkRequestBuilder<StaleCheckWorker>().build()
                            WorkManager.getInstance(context).enqueue(immediate)
                            // Reschedule stale check from fresh fetch time
                            val staleCheck = OneTimeWorkRequestBuilder<StaleCheckWorker>()
                                .setInitialDelay(STALE_THRESHOLD_MS, TimeUnit.MILLISECONDS)
                                .build()
                            WorkManager.getInstance(context).enqueueUniqueWork(
                                "stale_check", ExistingWorkPolicy.REPLACE, staleCheck
                            )
                        }
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

// ─── Dashboard Screen ───────────────────────────────────────────────

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
    Surface(modifier = Modifier.fillMaxSize(), color = AppBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Header — serif italic matching widget style
            Text(
                text = "Claude",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.Serif,
                color = Gold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Usage Monitor",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.Serif,
                color = TextGrey,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!isLoggedIn) {
                SignInCard(onLogin)
            } else {
                if (usageData != null) {
                    UsageCard(usageData, isRefreshing, onRefresh)
                } else if (isRefreshing) {
                    AppCard {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Gold,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Two-column grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Left column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SetupCard()
                        SocialLinksCard()
                    }
                    // Right column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WidgetSizesCard()
                        AccountCard(orgId, onLogin, onLogout)
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ─── Cards ──────────────────────────────────────────────────────────

@Composable
private fun AppCard(content: @Composable () -> Unit) {
    // 3D beveled border matching widget
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BezelShadow,
        shadowElevation = 4.dp
    ) {
        Box(modifier = Modifier.padding(start = 2.dp, top = 1.dp, end = 2.dp, bottom = 3.dp)) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = BezelHighlight
            ) {
                Box(modifier = Modifier.padding(start = 1.dp, top = 3.dp, end = 1.dp, bottom = 0.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardBg
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = TextWhite,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
    )
}

// ─── Sign In Card ───────────────────────────────────────────────────

@Composable
private fun SignInCard(onLogin: () -> Unit) {
    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(TrackColor),
                contentAlignment = Alignment.Center
            ) {
                Text("C", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Gold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Sign in to view your usage", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("Connect your Claude account", color = TextGrey, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onLogin,
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Sign in to Claude", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

// ─── Usage Card (3D beveled border matching widget) ─────────────────

@Composable
private fun UsageCard(data: UsageData, isRefreshing: Boolean, onRefresh: () -> Unit) {
    // 3D beveled border: shadow layer → highlight layer → content
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = BezelShadow,
        shadowElevation = 8.dp
    ) {
        Box(modifier = Modifier.padding(start = 3.dp, top = 1.dp, end = 3.dp, bottom = 4.dp)) {
            Surface(
                shape = RoundedCornerShape(21.dp),
                color = BezelHighlight
            ) {
                Box(modifier = Modifier.padding(start = 1.dp, top = 4.dp, end = 1.dp, bottom = 0.dp)) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CardBg
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header: "Usage" + skeuomorphic refresh button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Usage",
                                    color = TextWhite,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                SkeuomorphicRefreshButton(isRefreshing, onRefresh)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Two gauge panels side by side
                            Row(modifier = Modifier.fillMaxWidth()) {
                                GaugePanel(
                                    title = "5H",
                                    period = data.response.fiveHour,
                                    accentColor = Green,
                                    modifier = Modifier.weight(1f)
                                )
                                GaugePanel(
                                    title = "7D",
                                    period = data.response.sevenDay,
                                    accentColor = Orange,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Footer
                            Text(
                                text = "Updated ${formatTime(data.fetchedAt)}",
                                color = TextGrey,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeuomorphicRefreshButton(isRefreshing: Boolean, onRefresh: () -> Unit) {
    // Outer ring: raised gradient matching widget (#555560 → #28282E)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF555560), Color(0xFF28282E))
                )
            )
            .clickable(enabled = !isRefreshing) { onRefresh() },
        contentAlignment = Alignment.Center
    ) {
        // Gloss highlight on outer ring
        Box(
            modifier = Modifier
                .size(40.dp)
                .drawBehind {
                    drawArc(
                        color = Color(0x22FFFFFF),
                        startAngle = -160f,
                        sweepAngle = 140f,
                        useCenter = true,
                        size = size
                    )
                }
        )
        // Inner recessed circle (#222230 → #3A3A48)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF222230), Color(0xFF3A3A48))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = Gold,
                    strokeWidth = 1.5.dp
                )
            } else {
                Text("\u21BB", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GaugePanel(
    title: String,
    period: UsagePeriod,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title above circle (matching widget layout)
        Text(title, color = TextWhite, fontSize = 13.sp)

        Spacer(modifier = Modifier.height(6.dp))

        // Circular progress ring — clean, no glow
        Box(
            modifier = Modifier
                .size(120.dp)
                .drawBehind {
                    val strokeW = 10.dp.toPx()
                    val arcSize = size.minDimension - strokeW
                    val topLeft = Offset(strokeW / 2, strokeW / 2)

                    // Track ring
                    drawArc(
                        color = accentColor.copy(alpha = 0.19f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(arcSize, arcSize),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )

                    // Progress arc
                    if (period.fraction > 0.01) {
                        drawArc(
                            color = accentColor,
                            startAngle = -90f,
                            sweepAngle = 360f * period.fraction.toFloat(),
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${period.percent}%",
                color = accentColor,
                fontSize = 28.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Reset time below circle
        Text(
            text = "Resets ${period.formatResetTime()}",
            color = TextGrey,
            fontSize = 11.sp
        )
    }
}

// ─── Setup Card ─────────────────────────────────────────────────────

@Composable
private fun SetupCard() {
    Column {
        Text("Setup", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text("Add Widget", color = TextGrey, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))

        AppCard {
            Column(modifier = Modifier.padding(14.dp)) {
                StepItem(1, "Long-press on your home screen")
                StepItem(2, "Tap \"Widgets\"")
                StepItem(3, "Search for \"Claude Widget\"")
                StepItem(4, "Drag to your home screen")

                HorizontalDivider(
                    color = DividerColor,
                    thickness = 1.dp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                )

                Text(
                    "Auto-refreshes every 15 minutes",
                    color = TextGrey,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StepItem(number: Int, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Gold),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, color = TextWhite, fontSize = 12.sp)
    }
}

// ─── Widget Sizes Card ──────────────────────────────────────────────

@Composable
private fun WidgetSizesCard() {
    Column {
        Text("Widget Sizes", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp))

        AppCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Small (2\u00D72)", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Compact circle gauges showing 5H and 7D usage", color = TextGrey, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp))

                HorizontalDivider(
                    color = DividerColor,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                Text("Medium (4\u00D72)", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Progress bars with reset times and percentages", color = TextGrey, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

// ─── Social Links Card ──────────────────────────────────────────────

@Composable
private fun SocialLinksCard() {
    val uriHandler = LocalUriHandler.current
    Column {
        Text("Connect", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp))

        AppCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "GitHub Repo",
                    color = Gold,
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://github.com/StefanosKontopoulos/claude-widget")
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Feedback / Issues",
                    color = Gold,
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://github.com/StefanosKontopoulos/claude-widget/issues")
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "LinkedIn",
                    color = Gold,
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://www.linkedin.com/in/stefanos-kontopoulos/")
                    }
                )
            }
        }
    }
}

// ─── Account Card ───────────────────────────────────────────────────

@Composable
private fun AccountCard(orgId: String?, onLogin: () -> Unit, onLogout: () -> Unit) {
    Column {
        Text("Account", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp))

        AppCard {
            Column(modifier = Modifier.padding(14.dp)) {
                AccountRow("Status", "Connected", Green)

                Spacer(modifier = Modifier.height(8.dp))

                if (orgId != null) {
                    AccountRow("Organization", "${orgId.take(8)}...", TextWhite)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                AccountRow("Auto-refresh", "Every 15 min", TextWhite)

                Spacer(modifier = Modifier.height(14.dp))

                // Buttons stacked vertically to fit in column layout
                OutlinedButton(
                    onClick = onLogin,
                    border = BorderStroke(1.dp, Gold),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text("Re-login", color = Gold, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onLogout,
                    border = BorderStroke(1.dp, Red),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text("Sign Out", color = Red, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AccountRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextGrey, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ─── Helpers ────────────────────────────────────────────────────────

private fun formatTime(millis: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
}
