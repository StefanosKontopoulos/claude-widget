package com.claudewidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.text.Text

class ClaudeUsageWidget : GlanceAppWidget() {

    // TODO: Phase 5 will replace this with the full widget UI
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Text("Claude Usage")
        }
    }
}
