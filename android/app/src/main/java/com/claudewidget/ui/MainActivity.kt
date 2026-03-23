package com.claudewidget.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.claudewidget.data.UsageRepository
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CANARY_VALUE = "canary-phase1-ok"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCanaryTest()
    }

    private fun runCanaryTest() {
        lifecycleScope.launch {
            UsageRepository.writeCanary(applicationContext, CANARY_VALUE)
            val readBack = UsageRepository.readCanary(applicationContext)
            if (readBack == CANARY_VALUE) {
                Log.i(TAG, "CANARY PASS: DataStore read-back succeeded. Value: $readBack")
            } else {
                Log.e(TAG, "CANARY FAIL: Expected '$CANARY_VALUE' but got '$readBack'")
            }
        }
    }
}
