package com.claudewidget.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
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
private val GOLD = Color(0xFFE2B973)
private val TEXT_WHITE = Color(0xFFE0E0E0)
private val TEXT_GREY = Color(0xFF9CA3AF)
const val STALE_THRESHOLD_MS = 2 * 60 * 60 * 1000L // 2 hours

private val GREEN = Color(0xFF4ADE80)
private val ORANGE = Color(0xFFFB923C)
private val CARD_BG = Color(0xFF252530)

// 3D border colors — more visible
private val BEZEL_HIGHLIGHT = Color(0xFF5A5A68) // top edge highlight (brighter)
private val BEZEL_SHADOW = Color(0xFF050506)     // bottom edge shadow (darker)

// Int versions for Canvas
private const val GREEN_INT = 0xFF4ADE80.toInt()
private const val ORANGE_INT = 0xFFFB923C.toInt()
private const val GOLD_INT = 0xFFE2B973.toInt()
private const val WIDGET_BG_INT = 0xFF1E1E24.toInt()

class ClaudeUsageWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val MEDIUM = DpSize(250.dp, 110.dp)
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
        (System.currentTimeMillis() - cached.fetchedAt) > STALE_THRESHOLD_MS
    val size = LocalSize.current
    val isMedium = size.width >= 250.dp

    // 3D beveled border: shadow layer → highlight layer → content
    // Bottom shadow (darker, peeks at bottom/sides)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(BEZEL_SHADOW)
            .padding(3.dp, 1.dp, 3.dp, 4.dp)
    ) {
        // Top highlight (lighter, peeks at top/sides)
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(21.dp)
                .background(BEZEL_HIGHLIGHT)
                .padding(1.dp, 4.dp, 1.dp, 0.dp)
        ) {
            // Widget content
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(20.dp)
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
}

@Composable
private fun NotLoggedInState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = GlanceModifier.padding(16.dp)
    ) {
        ClaudeTitle(sizeSp = 54f, heightDp = 18)
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
        ClaudeTitle(sizeSp = 48f, heightDp = 16)
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

// ─── SMALL WIDGET ───────────────────────────────────────────────────

@Composable
private fun SmallDataState(data: UsageData, isStale: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header: serif "Claude" + skeuomorphic refresh
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClaudeUsageTitle(sizeSp = 54f, heightDp = 28)
            Spacer(modifier = GlanceModifier.defaultWeight())
            SkeuomorphicRefreshButton(sizeDp = 24)
        }

        Spacer(modifier = GlanceModifier.height(1.dp))

        // Two circle gauges
        Row(
            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GaugeWithLabel(label = "5H", period = data.response.fiveHour, isGreen = true)
            Spacer(modifier = GlanceModifier.width(6.dp))
            GaugeWithLabel(label = "7D", period = data.response.sevenDay, isGreen = false)
        }

        // Footer
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isStale) "⚠ Data may be outdated" else "Updated ${formatUpdatedTime(data.fetchedAt)}",
                style = TextStyle(color = ColorProvider(if (isStale) ORANGE else TEXT_GREY), fontSize = 8.sp)
            )
        }
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
            modifier = GlanceModifier.size(68.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = label,
            style = TextStyle(color = ColorProvider(TEXT_WHITE), fontSize = 10.sp)
        )
        Text(
            text = "Resets ${period.formatResetTime()}",
            style = TextStyle(color = ColorProvider(TEXT_GREY), fontSize = 7.sp)
        )
    }
}

private fun drawGlowingCircle(
    fraction: Float,
    percent: Int,
    colorInt: Int
): Bitmap {
    val sizePx = 180 // 60dp * 3
    val totalSize = sizePx + 28
    val bitmap = Bitmap.createBitmap(totalSize, totalSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = totalSize / 2f
    val cy = totalSize / 2f

    val strokeWidth = 15f
    val radius = (sizePx / 2f) - strokeWidth / 2 - 2f
    val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

    // Track — low-opacity accent color
    val trackColorInt = (colorInt and 0x00FFFFFF) or 0x30000000
    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        color = trackColorInt
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawArc(rect, -90f, 360f, false, trackPaint)

    if (fraction > 0.01f) {
        // Glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth + 20f
            color = (colorInt and 0x00FFFFFF) or 0x40000000
            strokeCap = Paint.Cap.ROUND
            maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
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

    // Percentage text — bigger, non-bold
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        textSize = 50f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    canvas.drawText("$percent%", cx, cy + 16f, textPaint)

    return bitmap
}

// ─── MEDIUM WIDGET ──────────────────────────────────────────────────

@Composable
private fun MediumDataState(data: UsageData, isStale: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)
    ) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClaudeTitle(sizeSp = 56f, heightDp = 20, suffix = " Usage")
            Spacer(modifier = GlanceModifier.defaultWeight())
            SkeuomorphicRefreshButton(sizeDp = 26)
        }

        // Bars centered in remaining space
        Box(
            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                BarRow(label = "5 Hour", period = data.response.fiveHour, isGreen = true)
                Spacer(modifier = GlanceModifier.height(8.dp))
                BarRow(label = "7 Day", period = data.response.sevenDay, isGreen = false)
            }
        }

        // Footer always at bottom
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isStale) "⚠ Data may be outdated" else "Updated ${formatUpdatedTime(data.fetchedAt)}",
                style = TextStyle(color = ColorProvider(if (isStale) ORANGE else TEXT_GREY), fontSize = 8.sp)
            )
        }
    }
}

@Composable
private fun BarRow(label: String, period: UsagePeriod, isGreen: Boolean) {
    val accentColor = if (isGreen) GREEN else ORANGE
    val colorInt = if (isGreen) GREEN_INT else ORANGE_INT

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(color = ColorProvider(TEXT_WHITE), fontWeight = FontWeight.Medium, fontSize = 14.sp)
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(
            text = "Resets ${period.formatResetTime()}",
            style = TextStyle(color = ColorProvider(TEXT_GREY), fontSize = 10.sp)
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = "${period.percent}%",
            style = TextStyle(color = ColorProvider(accentColor), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        )
    }

    Spacer(modifier = GlanceModifier.height(4.dp))

    val barBitmap = drawGlowingBar(fraction = period.fraction.toFloat(), colorInt = colorInt)
    Image(
        provider = ImageProvider(barBitmap),
        contentDescription = null,
        modifier = GlanceModifier.fillMaxWidth().height(14.dp),
        contentScale = ContentScale.FillBounds
    )
}

private fun drawGlowingBar(
    fraction: Float,
    colorInt: Int,
    widthPx: Int = 900,
    heightPx: Int = 48
): Bitmap {
    val totalHeight = heightPx + 16
    val bitmap = Bitmap.createBitmap(widthPx, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val yOffset = 8f
    val radius = heightPx / 2f

    val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0xFF333333.toInt()
    }
    canvas.drawRoundRect(
        RectF(1f, yOffset + 1f, widthPx - 1f, yOffset + heightPx - 1f),
        radius, radius, outlinePaint
    )

    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2A2A2A.toInt()
    }
    canvas.drawRoundRect(
        RectF(2f, yOffset + 2f, widthPx - 2f, yOffset + heightPx - 2f),
        radius - 1, radius - 1, trackPaint
    )

    if (fraction > 0.02f) {
        val fillWidth = (widthPx * fraction).coerceAtLeast(heightPx.toFloat())

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (colorInt and 0x00FFFFFF) or 0x50000000
            maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(
            RectF(2f, yOffset, fillWidth, yOffset + heightPx),
            radius, radius, glowPaint
        )

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorInt }
        canvas.drawRoundRect(
            RectF(2f, yOffset + 2f, fillWidth, yOffset + heightPx - 2f),
            radius - 1, radius - 1, fillPaint
        )

        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x30FFFFFF }
        canvas.drawRoundRect(
            RectF(4f, yOffset + 3f, fillWidth - 2f, yOffset + heightPx * 0.4f),
            radius * 0.5f, radius * 0.5f, highlightPaint
        )
    }

    return bitmap
}

// ─── SHARED COMPONENTS ──────────────────────────────────────────────

/** Renders "Claude" in serif bold-italic as a Canvas bitmap */
@Composable
private fun ClaudeTitle(sizeSp: Float, heightDp: Int, suffix: String = "") {
    val bitmap = drawSerifTitle("Claude$suffix", sizeSp)
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = "Claude",
        modifier = GlanceModifier.height(heightDp.dp),
        contentScale = ContentScale.Fit
    )
}

private fun drawSerifTitle(text: String, sizeSp: Float): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GOLD_INT
        textSize = sizeSp
        typeface = Typeface.create("serif", Typeface.ITALIC)
        isFakeBoldText = true
    }
    val textWidth = paint.measureText(text)
    val fm = paint.fontMetrics
    val textHeight = fm.descent - fm.ascent
    val bitmap = Bitmap.createBitmap(
        (textWidth + 6).toInt(),
        (textHeight + 6).toInt(),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    canvas.drawText(text, 3f, -fm.ascent + 3f, paint)
    return bitmap
}

/** Two-line "Claude" + indented "Usage" for small widget only */
@Composable
private fun ClaudeUsageTitle(sizeSp: Float, heightDp: Int) {
    Column {
        ClaudeTitle(sizeSp = sizeSp, heightDp = heightDp)
        Image(
            provider = ImageProvider(drawSerifTitle("Usage", sizeSp * 0.55f)),
            contentDescription = "Usage",
            modifier = GlanceModifier.height((heightDp * 0.6f).toInt().dp).padding(start = 8.dp),
            contentScale = ContentScale.Fit
        )
    }
}

/** Skeuomorphic refresh button: gradient outer ring + recessed inner circle */
@Composable
private fun SkeuomorphicRefreshButton(sizeDp: Int) {
    val bitmap = drawSkeuomorphicButton(sizeDp * 3)
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = "Refresh",
        modifier = GlanceModifier
            .size(sizeDp.dp)
            .clickable(actionRunCallback<ForceRefreshAction>()),
        contentScale = ContentScale.Fit
    )
}

private fun drawSkeuomorphicButton(sizePx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = sizePx / 2f
    val cy = sizePx / 2f

    // Outer ring: brighter gradient (raised look)
    val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            cx, 0f, cx, sizePx.toFloat(),
            0xFF555560.toInt(), 0xFF28282E.toInt(),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(cx, cy, sizePx / 2f, outerPaint)

    // Inner circle: opposite gradient (recessed look), brighter
    val innerR = sizePx / 2f - (sizePx * 0.14f)
    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            cx, cy - innerR, cx, cy + innerR,
            0xFF222230.toInt(), 0xFF3A3A48.toInt(),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(cx, cy, innerR, innerPaint)

    // Top highlight on the outer ring for glossiness
    val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22FFFFFF // 13% white
    }
    canvas.drawArc(
        RectF(2f, 2f, sizePx - 2f, sizePx - 2f),
        -160f, 140f, true, glossPaint
    )
    canvas.drawCircle(cx, cy, innerR, innerPaint)

    // Refresh icon — bigger, centered, thicker
    val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GOLD_INT
        textSize = sizePx * 0.50f
        textAlign = Paint.Align.CENTER
        strokeWidth = 2f
        style = Paint.Style.FILL_AND_STROKE
    }
    val iconFm = iconPaint.fontMetrics
    val iconY = cy - (iconFm.ascent + iconFm.descent) / 2
    canvas.drawText("\u21BB", cx, iconY, iconPaint)

    return bitmap
}

// ─── HELPERS ────────────────────────────────────────────────────────

private fun formatUpdatedTime(fetchedAt: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(fetchedAt))
}
