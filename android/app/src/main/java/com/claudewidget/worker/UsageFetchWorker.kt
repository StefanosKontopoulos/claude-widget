package com.claudewidget.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.claudewidget.data.UsageRepository
import com.claudewidget.widget.ClaudeUsageWidget

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
            ClaudeUsageWidget().updateAll(applicationContext)
            Result.success()
        } else {
            val error = result.exceptionOrNull()
            Log.w(TAG, "Usage fetch failed, scheduling retry", error)
            // On auth expiry, credentials are already cleared by UsageRepository.
            // Update widget immediately so it shows "Sign in" state.
            if (error?.message?.contains("Auth expired") == true) {
                Log.i(TAG, "Auth expired, updating widget to sign-in state")
                ClaudeUsageWidget().updateAll(applicationContext)
            }
            Result.retry()
        }
    }
}
