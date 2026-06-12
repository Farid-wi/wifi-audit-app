package com.wifiaudit.app.data.log

import android.util.Log
import com.wifiaudit.app.BuildConfig

/**
 * Structured, DEBUG-only logging for the Wi-Fi scan diagnostics.
 *
 * Everything goes under a single dedicated tag so it can be isolated from the business logs
 * (tag "WIFI_AUDIT") with:  adb logcat -s WifiDiagScan
 *
 * All calls are gated by [BuildConfig.DEBUG] → no diagnostic noise (and no string building cost
 * for the lazy variants) in release builds.
 */
object WifiDiagLog {

    const val TAG = "WifiDiagScan"

    /** Age (ms) above which a ScanResult is considered STALE — i.e. the throttle returned cache. */
    const val STALE_AGE_MS = 10_000L

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    /** Lazy variant — the message is only built when DEBUG logging is enabled. */
    inline fun d(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }

    fun w(message: String) {
        if (BuildConfig.DEBUG) Log.w(TAG, message)
    }
}
