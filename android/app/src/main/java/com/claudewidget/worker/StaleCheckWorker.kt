package com.claudewidget.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.claudewidget.widget.forceWidgetUpdate

/**
 * Fires once after the stale threshold to re-render the widget,
 * so the stale warning appears without waiting for the next fetch.
 */
class StaleCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        forceWidgetUpdate(applicationContext)
        return Result.success()
    }
}
