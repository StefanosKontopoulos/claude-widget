package com.claudewidget.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
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

// Design System Colors
private val WIDGET_BG = Color(0xFF1E1E24)
private val BORDER_COLOR = Color(0x0DFFFFFF) // rgba(255,255,255,0.05)
private val GOLD = Color(0xFFE2B973)
private val TEXT_WHITE = Color(0xFFE0E0E0)
private val TEXT_GREY = Color(0xFF9CA3AF)
private val GREEN = Color(0xFF4ADE80)
private val ORANGE = Color(0xFFFB923C)
private val TRACK_COLOR = Color(0xFF2A2A2A)
private val CARD_BG = Color(0xFF252530)

// Int versions for Canvas
private const val GREEN_INT = 0xFF4ADE80.toInt()
private const val ORANGE_INT = 0xFFFB923C.toInt()
private const val RED_INT = 0xFFEF4444.toInt()
private const val TRACK_INT = 0xFF2A2A2A.toInt()

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

    // Outer border (subtle white inner border)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(BORDER_COLOR)
            .padding(1.dp)
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(23.dp)
                .background(WIDGET_BG)
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
                color = ColorProvider(GOLD),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "Tap to sign in",
            style = TextStyle(
                color = ColorProvider(TEXT_GREY),
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
                color = ColorProvider(GOLD),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "Loading...",
            style = TextStyle(
                color = ColorProvider(TEXT_GREY),
                fontSize = 13.sp
            )
        )
    }
}

// ─── SMALL WIDGET (Component 2) ─────────────────────────────────────

@Composable
private fun SmallDataState(data: UsageData, isStale: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header: "Claude" + refresh button
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Claude",
                style = TextStyle(
                    color = ColorProvider(GOLD),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            RefreshButton(size = 24)
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        // Two circle gauges side by side
        Row(
            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GaugeWithLabel(
                label = "5H",
                period = data.response.fiveHour,
                isGreen = true
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            GaugeWithLabel(
                label = "7D",
                period = data.response.sevenDay,
                isGreen = false
            )
        }

        // Footer
        Text(
            text = "Updated ${formatUpdatedTime(data.fetchedAt)}",
            style = TextStyle(
                color = ColorProvider(TEXT_GREY),
                fontSize = 8.sp
            )
        )
    }
}

@Composable
private fun GaugeWithLabel(label: String, period: UsagePeriod, isGreen: Boolean) {
    val colorInt = if (isGreen) GREEN_INT else ORANGE_INT

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val bitmap = drawGlowingCircle(
            fraction = period.fraction.toFloat(),
            percent = period.percent,
            colorInt = colorInt
        )
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "$label usage: ${period.percent}%",
            modifier = GlanceModifier.size(56.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(TEXT_WHITE),
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        )
        Text(
            text = "Resets ${period.formatResetTime()}",
            style = TextStyle(
                color = ColorProvider(TEXT_GREY),
                fontSize = 7.sp
            )
        )
    }
}

/**
 * Draws a circular progress gauge with glow effect.
 * Track: dark grey ring. Progress: colored arc with outer glow.
 * Center: percentage text in the accent color.
 */
private fun drawGlowingCircle(
    fraction: Float,
    percent: Int,
    colorInt: Int
): Bitmap {
    val sizePx = 168 // 56dp * 3
    // Extra padding for glow to not clip
    val totalSize = sizePx + 24
    val bitmap = Bitmap.createBitmap(totalSize, totalSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = totalSize / 2f
    val cy = totalSize / 2f

    val strokeWidth = 14f
    val radius = (sizePx / 2f) - strokeWidth / 2 - 2f
    val rect = RectF(
        cx - radius, cy - radius,
        cx + radius, cy + radius
    )

    // Track ring
    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        color = TRACK_INT
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawArc(rect, -90f, 360f, false, trackPaint)

    if (fraction > 0.01f) {
        // Glow layer (wide, blurred, colored)
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth + 18f
            color = (colorInt and 0x00FFFFFF) or 0x40000000 // 25% alpha
            strokeCap = Paint.Cap.ROUND
            maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawArc(rect, -90f, 360f * fraction, false, glowPaint)

        // Progress arc
        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            color = colorInt
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(rect, -90f, 360f * fraction, false, progressPaint)
    }

    // Percentage text
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        textSize = 42f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val textY = cy + 14f
    canvas.drawText("$percent%", cx, textY, textPaint)

    return bitmap
}

// ─── MEDIUM WIDGET (Component 3) ────────────────────────────────────

@Composable
private fun MediumDataState(data: UsageData, isStale: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(14.dp)
    ) {
        // Header: "Claude Usage" + refresh
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Claude Usage",
                style = TextStyle(
                    color = ColorProvider(GOLD),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            RefreshButton(size = 26)
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        // 5-Hour bar
        BarRow(
            label = "5 Hour",
            period = data.response.fiveHour,
            isGreen = true
        )

        Spacer(modifier = GlanceModifier.height(5.dp))

        // 7-Day bar
        BarRow(
            label = "7 Day",
            period = data.response.sevenDay,
            isGreen = false
        )

        Spacer(modifier = GlanceModifier.defaultWeight())

        // Footer
        Text(
            text = "Updated ${formatUpdatedTime(data.fetchedAt)}",
            style = TextStyle(
                color = ColorProvider(TEXT_GREY),
                fontSize = 9.sp
            )
        )
    }
}

@Composable
private fun BarRow(label: String, period: UsagePeriod, isGreen: Boolean) {
    val accentColor = if (isGreen) GREEN else ORANGE
    val colorInt = if (isGreen) GREEN_INT else ORANGE_INT

    // Text row: label ... reset time ... percent
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(TEXT_WHITE),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(
            text = "Resets ${period.formatResetTime()}",
            style = TextStyle(
                color = ColorProvider(TEXT_GREY),
                fontSize = 9.sp
            )
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = "${period.percent}%",
            style = TextStyle(
                color = ColorProvider(accentColor),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        )
    }

    Spacer(modifier = GlanceModifier.height(3.dp))

    // Glowing progress bar bitmap
    val barBitmap = drawGlowingBar(
        fraction = period.fraction.toFloat(),
        colorInt = colorInt
    )
    Image(
        provider = ImageProvider(barBitmap),
        contentDescription = null,
        modifier = GlanceModifier.fillMaxWidth().height(12.dp),
        contentScale = ContentScale.FillBounds
    )
}

/**
 * Draws a thick pill-shaped progress bar with glow.
 * Track: dark inset grey (#2A2A2A) with outline.
 * Fill: colored with drop-shadow/glow of same color + glossy highlight.
 */
private fun drawGlowingBar(
    fraction: Float,
    colorInt: Int,
    widthPx: Int = 900,
    heightPx: Int = 48
): Bitmap {
    // Extra vertical space for glow
    val totalHeight = heightPx + 16
    val bitmap = Bitmap.createBitmap(widthPx, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val yOffset = 8f // center the bar vertically in the padded bitmap
    val radius = heightPx / 2f

    // Track outline
    val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0xFF333333.toInt()
    }
    canvas.drawRoundRect(
        RectF(1f, yOffset + 1f, widthPx - 1f, yOffset + heightPx - 1f),
        radius, radius, outlinePaint
    )

    // Track fill (dark inset)
    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TRACK_INT
    }
    canvas.drawRoundRect(
        RectF(2f, yOffset + 2f, widthPx - 2f, yOffset + heightPx - 2f),
        radius - 1, radius - 1, trackPaint
    )

    if (fraction > 0.02f) {
        val fillWidth = (widthPx * fraction).coerceAtLeast(heightPx.toFloat())

        // Glow behind fill (blurred, colored shadow)
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (colorInt and 0x00FFFFFF) or 0x50000000 // 31% alpha
            maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(
            RectF(2f, yOffset, fillWidth, yOffset + heightPx),
            radius, radius, glowPaint
        )

        // Progress fill
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
        }
        canvas.drawRoundRect(
            RectF(2f, yOffset + 2f, fillWidth, yOffset + heightPx - 2f),
            radius - 1, radius - 1, fillPaint
        )

        // Glossy highlight (top half)
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x30FFFFFF // 19% white
        }
        canvas.drawRoundRect(
            RectF(4f, yOffset + 3f, fillWidth - 2f, yOffset + heightPx * 0.4f),
            radius * 0.5f, radius * 0.5f, highlightPaint
        )
    }

    return bitmap
}

// ─── SHARED COMPONENTS ──────────────────────────────────────────────

@Composable
private fun RefreshButton(size: Int) {
    Box(
        modifier = GlanceModifier
            .size(size.dp)
            .cornerRadius((size / 2).dp)
            .background(CARD_BG)
            .clickable(actionRunCallback<ForceRefreshAction>()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\u21BB",
            style = TextStyle(
                color = ColorProvider(GOLD),
                fontSize = (size - 10).coerceAtLeast(12).sp
            )
        )
    }
}

// ─── HELPERS ────────────────────────────────────────────────────────

private fun formatUpdatedTime(fetchedAt: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(fetchedAt))
}
