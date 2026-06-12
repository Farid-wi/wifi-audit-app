package com.wifiaudit.app.domain.model

/**
 * Scan pacing strategy chosen by the user at the start of a measurement session.
 *
 * The measurement engine stays unique: the same sample pipeline (timestamp, position, BSSID,
 * frequency, RSSI) runs in both modes — only the cadence between scans changes.
 *
 * STANDARD — default, safe on every device. Deliberate spacing between scans that respects the
 *            Android foreground throttle (4 scans / 2 min). This is the existing behavior and
 *            must never be regressed.
 * FAST     — only meaningful once the user has manually disabled "Wi-Fi scan throttling" in the
 *            developer options (Android 10+). Scans are then chained at ~1 scan / 5 s. If the
 *            throttle turns out to still be active mid-audit, the engine falls back to STANDARD.
 */
enum class ScanMode {
    STANDARD,
    FAST
}
