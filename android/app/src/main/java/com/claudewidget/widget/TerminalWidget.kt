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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
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
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.claudewidget.auth.CredentialStore
import com.claudewidget.data.UsageData
import com.claudewidget.data.UsagePeriod
import com.claudewidget.data.UsageRepository
import kotlin.math.roundToInt

// ─── Terminal Design Tokens ──────────────────────────────────────────────────
// Palette from design handoff: Terminal / CLI dark theme
private val TERM_BG     = Color(0xFF1E1E24)
private val TERM_FG     = Color(0xFFC7E6C7)
private val TERM_DIM    = Color(0xFF6A7A6A)
private val TERM_ACCENT = Color(0xFFE2B973)
private val TERM_GREEN  = Color(0xFF5BE075)
private val TERM_ORANGE = Color(0xFFF0A94E)

private const val TERM_DIM_INT    = 0xFF6A7A6A.toInt()
private const val TERM_ACCENT_INT = 0xFFE2B973.toInt()
private const val TERM_GREEN_INT  = 0xFF5BE075.toInt()
private const val TERM_ORANGE_INT = 0xFFF0A94E.toInt()

private val MONO = FontFamily("monospace")

class TerminalWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val hasCreds = CredentialStore.loadSessionCookie(context) != null
        val cached   = UsageRepository.getCached(context)
        provideContent { TerminalContent(hasCreds, cached) }
    }
}

class TerminalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TerminalWidget()
}

// ─── Root ────────────────────────────────────────────────────────────────────

@Composable
private fun TerminalContent(hasCreds: Boolean, cached: UsageData?) {
    val size    = LocalSize.current
    val isWide  = size.width  >= 250.dp
    val isLarge = isWide && size.height >= 200.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(TERM_BG)
            .clickable(actionStartActivity(
                Intent().setClassName("com.claudewidget", "com.claudewidget.ui.MainActivity")
            )),
        contentAlignment = Alignment.TopStart
    ) {
        when {
            !hasCreds      -> TermStateMessage("error: not logged in", "tap to sign in")
            cached == null -> TermStateMessage("loading...", "")
            isLarge        -> TerminalLarge(cached)
            isWide         -> TerminalMedium(cached)
            else           -> TerminalSmall(cached)
        }
    }
}

@Composable
private fun TermStateMessage(primary: String, secondary: String) {
    Column(modifier = GlanceModifier.padding(14.dp)) {
        Text(
            "$ claude usage",
            style = TextStyle(color = ColorProvider(TERM_ACCENT), fontSize = 11.sp, fontFamily = MONO)
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(primary, style = TextStyle(color = ColorProvider(TERM_DIM), fontSize = 11.sp, fontFamily = MONO))
        if (secondary.isNotEmpty()) {
            Text(secondary, style = TextStyle(color = ColorProvider(TERM_FG), fontSize = 11.sp, fontFamily = MONO))
        }
    }
}

// ─── Small (2×2) — traffic lights + [↻], $ claude usage, compact period rows ─

@Composable
private fun TerminalSmall(data: UsageData) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
        // 1. Title row
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(drawTermTrafficLights()),
                contentDescription = null,
                modifier = GlanceModifier.height(9.dp).width(44.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(GlanceModifier.defaultWeight())
            Image(
                provider = ImageProvider(drawTermRefreshBtn(66)),
                contentDescription = "Refresh",
                modifier = GlanceModifier.size(20.dp).clickable(actionRunCallback<ForceRefreshAction>()),
                contentScale = ContentScale.Fit
            )
        }
        // 2. Spacer
        Spacer(GlanceModifier.height(4.dp))
        // 3. Command
        Text(
            "$ claude usage",
            style = TextStyle(color = ColorProvider(TERM_ACCENT), fontSize = 11.sp, fontFamily = MONO)
        )
        // 4. Spacer
        Spacer(GlanceModifier.height(6.dp))
        // 5. Period rows nested so they count as one outer child
        Column {
            TermSmallPeriodRow(data.response.fiveHour, "5H", TERM_GREEN_INT)
            Spacer(GlanceModifier.height(8.dp))
            TermSmallPeriodRow(data.response.sevenDay, "7D", TERM_ORANGE_INT)
        }
        // 6. Cursor
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.padding(top = 12.dp)
        ) {
            Text("$ ", style = TextStyle(color = ColorProvider(TERM_ACCENT), fontSize = 11.sp, fontFamily = MONO))
            Text("█", style = TextStyle(color = ColorProvider(TERM_ACCENT.copy(alpha = 0.55f)), fontSize = 13.sp, fontFamily = MONO))
        }
        // 7. Push timestamp to bottom
        Spacer(GlanceModifier.defaultWeight())
        // 8. Synced timestamp — matches "Updated HH:MM" style of other widget variants
        Text(
            "Updated ${fmtFetchTime(data.fetchedAt)}",
            style = TextStyle(
                color = ColorProvider(TERM_DIM),
                fontSize = 10.sp,
                textAlign = androidx.glance.text.TextAlign.Center
            ),
            modifier = GlanceModifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TermSmallPeriodRow(period: UsagePeriod, label: String, colorInt: Int) {
    // Label row: "5H" left, "↻ 1h 48m" right
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            label,
            style = TextStyle(color = ColorProvider(TERM_DIM), fontSize = 10.sp,
                fontFamily = MONO, fontWeight = FontWeight.Bold)
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            "↻ ${fmtCountdown(period.resetsAt)}",
            style = TextStyle(color = ColorProvider(TERM_DIM), fontSize = 9.sp, fontFamily = MONO)
        )
    }
    // Bar row: block bar + percentage
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(drawTermBlockBar(period.fraction.toFloat(), 8, colorInt, textSizePx = 42f)),
            contentDescription = null,
            modifier = GlanceModifier.defaultWeight().height(18.dp),
            contentScale = ContentScale.FillBounds
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            "${period.percent}%",
            style = TextStyle(color = ColorProvider(TERM_FG), fontSize = 14.sp,
                fontFamily = MONO, fontWeight = FontWeight.Bold)
        )
    }
}

// ─── Medium (4×2) — macOS title bar, 14-char bars, reset chips ───────────────

@Composable
private fun TerminalMedium(data: UsageData) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(16.dp)) {
        TermTitleBar()
        Spacer(GlanceModifier.height(8.dp))
        Text(
            "$ claude usage",
            style = TextStyle(color = ColorProvider(TERM_ACCENT), fontSize = 13.sp, fontFamily = MONO)
        )
        Spacer(GlanceModifier.defaultWeight())
        TermMediumPeriodRow(data.response.fiveHour, "5h", TERM_GREEN_INT)
        Spacer(GlanceModifier.height(10.dp))
        TermMediumPeriodRow(data.response.sevenDay, "7d", TERM_ORANGE_INT)
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$ ", style = TextStyle(color = ColorProvider(TERM_ACCENT), fontSize = 13.sp, fontFamily = MONO))
            Image(
                provider = ImageProvider(drawTermCursor(8, 14)),
                contentDescription = null,
                modifier = GlanceModifier.width(8.dp).height(14.dp),
                contentScale = ContentScale.FillBounds
            )
        }
    }
}

@Composable
private fun TermTitleBar() {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // macOS-style traffic lights
        Image(
            provider = ImageProvider(drawTermTrafficLights()),
            contentDescription = null,
            modifier = GlanceModifier.height(10.dp).width(48.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            "~/claude/usage",
            style = TextStyle(color = ColorProvider(TERM_DIM), fontSize = 10.sp, fontFamily = MONO)
        )
        Spacer(GlanceModifier.defaultWeight())
        Image(
            provider = ImageProvider(drawTermRefreshBtn(66)),
            contentDescription = "Refresh",
            modifier = GlanceModifier.size(22.dp).clickable(actionRunCallback<ForceRefreshAction>()),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun TermMediumPeriodRow(period: UsagePeriod, label: String, colorInt: Int) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = TextStyle(color = ColorProvider(TERM_DIM), fontSize = 13.sp, fontFamily = MONO))
        Spacer(GlanceModifier.width(8.dp))
        // Block bar expands to fill available space between fixed-width elements
        Image(
            provider = ImageProvider(drawTermBlockBar(period.fraction.toFloat(), 14, colorInt, textSizePx = 54f)),
            contentDescription = null,
            modifier = GlanceModifier.defaultWeight().height(18.dp),
            contentScale = ContentScale.FillBounds
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            " ${period.percent}%",
            style = TextStyle(color = ColorProvider(TERM_FG), fontSize = 17.sp,
                fontFamily = MONO, fontWeight = FontWeight.Bold)
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            "↻ ${fmtCountdown(period.resetsAt)}",
            style = TextStyle(color = ColorProvider(TERM_DIM), fontSize = 11.sp, fontFamily = MONO)
        )
    }
}

// ─── Large (4×4) — --verbose output, 42sp numbers, full-width block bars ─────

@Composable
private fun TerminalLarge(data: UsageData) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(16.dp)) {
        TermTitleBar()
        Spacer(GlanceModifier.height(6.dp))
        Text(
            "$ claude usage --verbose",
            style = TextStyle(color = ColorProvider(TERM_ACCENT), fontSize = 13.sp, fontFamily = MONO)
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            "reading ~/.claude/session.json ... ok",
            style = TextStyle(color = ColorProvider(TERM_DIM), fontSize = 10.sp, fontFamily = MONO)
        )
        Spacer(GlanceModifier.height(10.dp))
        TermLargeSection(data.response.fiveHour, "[ 5-HOUR WINDOW ]", TERM_GREEN, TERM_GREEN_INT)
        Spacer(GlanceModifier.height(10.dp))
        TermLargeSection(data.response.sevenDay, "[ 7-DAY WINDOW ]", TERM_ORANGE, TERM_ORANGE_INT)
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$ ", style = TextStyle(color = ColorProvider(TERM_ACCENT), fontSize = 12.sp, fontFamily = MONO))
            Image(
                provider = ImageProvider(drawTermCursor(8, 14)),
                contentDescription = null,
                modifier = GlanceModifier.width(8.dp).height(14.dp),
                contentScale = ContentScale.FillBounds
            )
        }
    }
}

@Composable
private fun TermLargeSection(period: UsagePeriod, windowLabel: String, color: Color, colorInt: Int) {
    Text(
        windowLabel,
        style = TextStyle(color = ColorProvider(TERM_DIM), fontSize = 11.sp,
            fontFamily = MONO, fontWeight = FontWeight.Bold)
    )
    // Large percentage: 42sp number + smaller % sign aligned to bottom
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            "${period.percent}",
            style = TextStyle(color = ColorProvider(color), fontSize = 42.sp,
                fontFamily = MONO, fontWeight = FontWeight.Bold)
        )
        Text(
            "%",
            style = TextStyle(color = ColorProvider(color.copy(alpha = 0.7f)), fontSize = 22.sp,
                fontFamily = MONO, fontWeight = FontWeight.Bold)
        )
    }
    Spacer(GlanceModifier.height(2.dp))
    // Full-width block bar
    Image(
        provider = ImageProvider(drawTermBlockBar(period.fraction.toFloat(), 22, colorInt, textSizePx = 54f)),
        contentDescription = null,
        modifier = GlanceModifier.fillMaxWidth().height(20.dp),
        contentScale = ContentScale.FillBounds
    )
    Spacer(GlanceModifier.height(3.dp))
    Text(
        "resets ${period.formatResetTime()} · ${fmtCountdown(period.resetsAt)}",
        style = TextStyle(color = ColorProvider(TERM_DIM), fontSize = 10.sp, fontFamily = MONO)
    )
}

// ─── Canvas Helpers ───────────────────────────────────────────────────────────

private fun tintedDim(colorInt: Int, tint: Float = 0.35f): Int {
    val dim = TERM_DIM_INT
    val r = ((dim shr 16 and 0xFF) * (1 - tint) + (colorInt shr 16 and 0xFF) * tint).toInt()
    val g = ((dim shr 8  and 0xFF) * (1 - tint) + (colorInt shr 8  and 0xFF) * tint).toInt()
    val b = ((dim        and 0xFF) * (1 - tint) + (colorInt        and 0xFF) * tint).toInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

// Renders █░ block bar with filled portion at full color and empty portion tinted with the bar color
private fun drawTermBlockBar(fraction: Float, chars: Int, colorInt: Int, textSizePx: Float = 42f): Bitmap {
    val filled = (fraction * chars).roundToInt().coerceIn(0, chars)
    val filledText = "█".repeat(filled)
    val emptyText  = "░".repeat(chars - filled)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = textSizePx
    }
    val filledW = paint.measureText(filledText)
    val totalW  = paint.measureText(filledText + emptyText).toInt().coerceAtLeast(1)
    val fm = paint.fontMetrics
    val h  = (fm.descent - fm.ascent).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(totalW + 4, h + 4, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val baseline = -fm.ascent + 2f

    // Filled blocks — full accent color
    paint.color = colorInt
    if (filledText.isNotEmpty()) c.drawText(filledText, 2f, baseline, paint)

    // Empty blocks — dim base tinted toward the bar color for CLI feel
    paint.color = tintedDim(colorInt, 0.35f)
    if (emptyText.isNotEmpty()) c.drawText(emptyText, 2f + filledW, baseline, paint)

    return bmp
}

// Boxed [↻] key — matches terminal refresh button from design spec
private fun drawTermRefreshBtn(sizePx: Int): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    // Keyboard-key shape: thin gold border, mild corner radius (~6/28 of size)
    val radius = sizePx * 0.21f
    c.drawRoundRect(
        RectF(1.5f, 1.5f, sizePx - 1.5f, sizePx - 1.5f), radius, radius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.5f; color = TERM_ACCENT_INT
        }
    )
    // Bottom inset shadow line (inset 0 -1px 0 accent44 from design spec)
    c.drawLine(
        radius, sizePx - 4f, sizePx - radius, sizePx - 4f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f
            color = (TERM_ACCENT_INT and 0x00FFFFFF) or 0x55000000
        }
    )
    // ↻ icon in gold
    val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TERM_ACCENT_INT
        typeface = Typeface.MONOSPACE
        textSize = sizePx * 0.68f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val fm = iconPaint.fontMetrics
    c.drawText("↻", sizePx / 2f, sizePx / 2f - (fm.ascent + fm.descent) / 2, iconPaint)
    return bmp
}

// macOS-style red/yellow/green circles for the title bar
private fun drawTermTrafficLights(): Bitmap {
    val diam = 30   // 10dp at 3× density
    val gap  = 18   // 6dp at 3× density
    val w = 3 * diam + 2 * gap + 4
    val h = diam + 4
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r  = diam / 2f
    val cy = h / 2f
    intArrayOf(0xFFFF5F56.toInt(), 0xFFFFBD2E.toInt(), 0xFF27C93F.toInt()).forEachIndexed { i, col ->
        c.drawCircle(2f + r + i * (diam + gap), cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
    }
    return bmp
}

private fun fmtFetchTime(millis: Long): String {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault())
    return java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(formatter)
}

// Static gold block cursor (Glance can't animate; omit blinking on widget path)
private fun drawTermCursor(widthDp: Int, heightDp: Int): Bitmap {
    val w = (widthDp  * 3).coerceAtLeast(1)
    val h = (heightDp * 3).coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    Canvas(bmp).drawRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint().apply { color = TERM_ACCENT_INT })
    return bmp
}
