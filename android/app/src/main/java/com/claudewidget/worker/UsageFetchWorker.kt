package com.claudewidget.worker

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.claudewidget.data.UsageRepository
import com.claudewidget.widget.STALE_THRESHOLD_MS
import com.claudewidget.widget.forceWidgetUpdate
import java.util.concurrent.TimeUnit

class UsageFetchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "UsageFetchWorker"
        const val WORK_NAME = "usage_fetch"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting usage fetch (attempt $runAttemptCount)")

        val result = UsageRepository.fetchAndStore(applicationContext)

        return if (result.isSuccess) {
            Log.i(TAG, "Usage fetch succeeded, updating widget")
            forceWidgetUpdate(applicationContext)
            // Schedule a stale check to re-render widget when data becomes outdated
            val staleCheck = OneTimeWorkRequestBuilder<StaleCheckWorker>()
                .setInitialDelay(STALE_THRESHOLD_MS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "stale_check", ExistingWorkPolicy.REPLACE, staleCheck
            )
            Result.success()
        } else {
            val error = result.exceptionOrNull()
            Log.w(TAG, "Usage fetch failed, scheduling retry", error)
            val msg = error?.message ?: ""
            // Update widget whenever credentials are missing or expired
            if (msg.contains("Auth expired") || msg.contains("No session cookie") || msg.contains("No org ID")) {
                Log.i(TAG, "No credentials — updating widget to sign-in state")
                forceWidgetUpdate(applicationContext)
                return Result.failure() // Don't retry — nothing will change without re-login
            }
            Result.retry()
        }
    }
}
