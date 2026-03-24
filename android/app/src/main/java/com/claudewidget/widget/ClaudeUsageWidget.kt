package com.claudewidget.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.action.clickable
import com.claudewidget.auth.CredentialStore
import com.claudewidget.data.UsageData
import com.claudewidget.data.UsagePeriod
import com.claudewidget.data.UsageRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Colors matching Canva designs
private val BG_COLOR = Color(0xFF141414)
private val BORDER_COLOR = Color(0xFF2A2A2A)
private val TITLE_COLOR = Color(0xFFD4A843)
private val TEXT_COLOR = Color(0xFFE0E0E0)
private val TEXT_DIM = Color(0xFF777777)
private val CARD_BG = Color(0xFF1C1C1C)

class ClaudeUsageWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(120.dp, 120.dp)
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

    // Outer box for subtle border
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(BORDER_COLOR)
            .padding(1.dp)
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(19.dp)
                .background(BG_COLOR)
                .clickable(actionStartActivity(Intent().setClassName("com.claudewidget", "com.claudewidget.ui.MainActivity"))),
            contentAlignment = Alignment.Center
        ) {
            when {
                !hasCreds -> NotLoggedInState()
                cached == null -> LoadingState()
                isMedium -> MediumDataState(data = cached, isStale = isStale)
                else -> SmallDataState(data = cached, isStale = isStale)
            }
        }
    }
}

@Composable
private fun NotLoggedInState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = GlanceModifier.padding(16.dp)
    ) {
        Text(
            text = "Claude",
            style = TextStyle(
                color = ColorProvider(TITLE_COLOR),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "Tap to sign in",
            style = TextStyle(
                color = ColorProvider(TEXT_DIM),
                fontSize = 13.sp
            )
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = GlanceModifier.padding(16.dp)
    ) {
        Text(
            text = "Claude",
            style = TextStyle(
                color = ColorProvider(TITLE_COLOR),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "Loading...",
            style = TextStyle(
                color = ColorProvider(TEXT_DIM),
                fontSize = 13.sp
            )
        )
    }
}

// --- SMALL WIDGET: Circle gauges with labels + reset times (Image 2) ---

@Composable
private fun SmallDataState(data: UsageData, isStale: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title row with refresh button
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Claude",
                style = TextStyle(
                    color = ColorProvider(TITLE_COLOR),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier
                    .size(22.dp)
                    .cornerRadius(11.dp)
                    .background(CARD_BG)
                    .clickable(actionRunCallback<ForceRefreshAction>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u21BB",
                    style = TextStyle(
                        color = ColorProvider(TITLE_COLOR),
                        fontSize = 13.sp
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(2.dp))

        // Gauges row with labels and reset times
        Row(
            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 5H gauge
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircleGauge(period = data.response.fiveHour)
                Text(
                    text = "5H",
                    style = TextStyle(
                        color = ColorProvider(TEXT_COLOR),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                )
                Text(
                    text = "Resets ${data.response.fiveHour.formatResetTime()}",
                    style = TextStyle(
                        color = ColorProvider(TEXT_DIM),
                        fontSize = 7.sp
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            // 7D gauge
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircleGauge(period = data.response.sevenDay)
                Text(
                    text = "7D",
                    style = TextStyle(
                        color = ColorProvider(TEXT_COLOR),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                )
                Text(
                    text = "Resets ${data.response.sevenDay.formatResetTime()}",
                    style = TextStyle(
                        color = ColorProvider(TEXT_DIM),
                        fontSize = 7.sp
                    )
                )
            }
        }

        Text(
            text = "Updated ${formatUpdatedTime(data.fetchedAt)}",
            style = TextStyle(
                color = ColorProvider(TEXT_DIM),
                fontSize = 8.sp
            )
        )
    }
}

@Composable
private fun CircleGauge(period: UsagePeriod) {
    val bitmap = drawCircleProgress(
        fraction = period.fraction.toFloat(),
        percent = period.percent,
        colorInt = progressColorInt(period.fraction)
    )

    Image(
        provider = ImageProvider(bitmap),
        contentDescription = "${period.percent}% usage",
        modifier = GlanceModifier.size(54.dp),
        contentScale = ContentScale.Fit
    )
}

private fun drawCircleProgress(
    fraction: Float,
    percent: Int,
    colorInt: Int
): Bitmap {
    val sizePx = 162 // 54dp * 3 for density
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val strokeWidth = 14f
    val padding = strokeWidth / 2 + 6f
    val rect = RectF(padding, padding, sizePx - padding, sizePx - padding)

    // Track (background ring)
    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        color = 0xFF2A2A2A.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawArc(rect, -90f, 360f, false, trackPaint)

    // Glow behind progress arc
    if (fraction > 0.01f) {
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth + 14f
            color = (colorInt and 0x00FFFFFF) or 0x30000000 // 19% alpha
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(rect, -90f, 360f * fraction, false, glowPaint)
    }

    // Progress arc
    if (fraction > 0.01f) {
        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            color = colorInt
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(rect, -90f, 360f * fraction, false, progressPaint)
    }

    // Percentage text centered
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        textSize = 44f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText(
        "$percent%",
        sizePx / 2f,
        sizePx / 2f + 14f,
        textPaint
    )

    return bitmap
}

private fun progressColorInt(fraction: Double): Int {
    return when {
        fraction < 0.50 -> 0xFF2ECC71.toInt()   // Green
        fraction < 0.75 -> 0xFF27AE60.toInt()    // Deeper green
        fraction < 0.90 -> 0xFFF39C12.toInt()    // Orange
        else -> 0xFFE74C3C.toInt()                // Red
    }
}

// --- MEDIUM WIDGET: Glossy progress bars with reset times (Image 1) ---

@Composable
private fun MediumDataState(data: UsageData, isStale: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(14.dp)
    ) {
        // Header row
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Claude Usage",
                style = TextStyle(
                    color = ColorProvider(TITLE_COLOR),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier
                    .size(26.dp)
                    .cornerRadius(13.dp)
                    .background(CARD_BG)
                    .clickable(actionRunCallback<ForceRefreshAction>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u21BB",
                    style = TextStyle(
                        color = ColorProvider(TEXT_DIM),
                        fontSize = 15.sp
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        // 5-Hour usage bar
        UsageBarRow(label = "5 Hour", period = data.response.fiveHour)

        Spacer(modifier = GlanceModifier.height(4.dp))

        // 7-Day usage bar
        UsageBarRow(label = "7 Day", period = data.response.sevenDay)

        Spacer(modifier = GlanceModifier.defaultWeight())

        // Footer
        Text(
            text = "Updated ${formatUpdatedTime(data.fetchedAt)}",
            style = TextStyle(
                color = ColorProvider(TEXT_DIM),
                fontSize = 9.sp
            )
        )
    }
}

@Composable
private fun UsageBarRow(label: String, period: UsagePeriod) {
    val color = progressColor(period.fraction)
    val colorInt = progressColorInt(period.fraction)

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(TEXT_COLOR),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(
            text = "Resets ${period.formatResetTime()}",
            style = TextStyle(
                color = ColorProvider(TEXT_DIM),
                fontSize = 9.sp
            )
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = "${period.percent}%",
            style = TextStyle(
                color = ColorProvider(color),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        )
    }

    Spacer(modifier = GlanceModifier.height(3.dp))

    // Glossy rounded progress bar via Canvas bitmap
    val barBitmap = drawRoundedBar(
        fraction = period.fraction.toFloat(),
        colorInt = colorInt
    )
    Image(
        provider = ImageProvider(barBitmap),
        contentDescription = null,
        modifier = GlanceModifier.fillMaxWidth().height(10.dp),
        contentScale = ContentScale.FillBounds
    )
}

private fun drawRoundedBar(
    fraction: Float,
    colorInt: Int,
    widthPx: Int = 900,
    heightPx: Int = 36
): Bitmap {
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = heightPx / 2f

    // Track outline
    val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0xFF333333.toInt()
    }
    canvas.drawRoundRect(
        RectF(0.5f, 0.5f, widthPx - 0.5f, heightPx - 0.5f),
        radius, radius, outlinePaint
    )

    // Track fill
    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E1E1E.toInt()
    }
    canvas.drawRoundRect(
        RectF(2f, 2f, widthPx - 2f, heightPx - 2f),
        radius - 1, radius - 1, trackPaint
    )

    // Progress fill
    if (fraction > 0.02f) {
        val fillWidth = (widthPx * fraction).coerceAtLeast(heightPx.toFloat())
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
        }
        canvas.drawRoundRect(
            RectF(2f, 2f, fillWidth, heightPx - 2f),
            radius - 1, radius - 1, fillPaint
        )

        // Glossy highlight on top half of fill
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x20FFFFFF // 12% white
        }
        canvas.drawRoundRect(
            RectF(4f, 3f, fillWidth - 2f, heightPx * 0.42f),
            radius * 0.6f, radius * 0.6f, highlightPaint
        )
    }

    return bitmap
}

// --- Helpers ---

private fun progressColor(fraction: Double): Color {
    return when {
        fraction < 0.50 -> Color(0xFF2ECC71)
        fraction < 0.75 -> Color(0xFF27AE60)
        fraction < 0.90 -> Color(0xFFF39C12)
        else -> Color(0xFFE74C3C)
    }
}

private fun formatUpdatedTime(fetchedAt: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(fetchedAt))
}
