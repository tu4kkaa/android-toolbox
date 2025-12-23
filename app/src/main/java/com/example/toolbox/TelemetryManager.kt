// TelemetryManager.kt
package com.example.toolbox

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

data class TelemetrySnapshot(
    val launchCount: Int,
    val totalUsageMs: Long,
    val sessionsCount: Int,
    val totalLevelMs: Long,
    val totalSoundMs: Long,
    val totalFlashMs: Long,
    val totalOtherMs: Long,
    val lastRating: Int?
) {
    val averageSessionMs: Long
        get() = if (sessionsCount > 0) totalUsageMs / sessionsCount else 0L

    // average time per tab per session (approximation)
    val averagePerTabMs: Map<String, Long>
        get() {
            val sc = if (sessionsCount > 0) sessionsCount else 1
            return mapOf(
                "Level" to (totalLevelMs / sc),
                "Sound" to (totalSoundMs / sc),
                "Flash" to (totalFlashMs / sc),
                "Other" to (totalOtherMs / sc)
            )
        }
}

class TelemetryManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("toolbox_telemetry", Context.MODE_PRIVATE)

    // keys
    private val KEY_LAUNCH_COUNT = "launch_count"
    private val KEY_TOTAL_USAGE_MS = "total_usage_ms"
    private val KEY_SESSIONS_COUNT = "sessions_count"
    private val KEY_LEVEL_MS = "level_ms"
    private val KEY_SOUND_MS = "sound_ms"
    private val KEY_FLASH_MS = "flash_ms"
    private val KEY_OTHER_MS = "other_ms"
    private val KEY_LAST_RATING = "last_rating"

    private val _flow = MutableStateFlow(loadSnapshot())
    val telemetryFlow = _flow.asStateFlow()

    private fun loadSnapshot(): TelemetrySnapshot {
        val launchCount = prefs.getInt(KEY_LAUNCH_COUNT, 0)
        val totalUsageMs = prefs.getLong(KEY_TOTAL_USAGE_MS, 0L)
        val sessionsCount = prefs.getInt(KEY_SESSIONS_COUNT, 0)
        val totalLevelMs = prefs.getLong(KEY_LEVEL_MS, 0L)
        val totalSoundMs = prefs.getLong(KEY_SOUND_MS, 0L)
        val totalFlashMs = prefs.getLong(KEY_FLASH_MS, 0L)
        val totalOtherMs = prefs.getLong(KEY_OTHER_MS, 0L)
        val lastRating = if (prefs.contains(KEY_LAST_RATING)) prefs.getInt(KEY_LAST_RATING, 0) else null
        return TelemetrySnapshot(
            launchCount, totalUsageMs, sessionsCount,
            totalLevelMs, totalSoundMs, totalFlashMs, totalOtherMs, lastRating
        )
    }

    private fun publish() {
        _flow.value = loadSnapshot()
    }

    /**
     * Increment launch count and return new value
     */
    suspend fun incrementLaunchCount(): Int = withContext(Dispatchers.IO) {
        val new = prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1
        prefs.edit().putInt(KEY_LAUNCH_COUNT, new).apply()
        publish()
        new
    }

    fun getSnapshot(): TelemetrySnapshot = _flow.value

    fun addSession(durationMs: Long) {
        if (durationMs <= 0) return
        val total = prefs.getLong(KEY_TOTAL_USAGE_MS, 0L) + durationMs
        val sessions = prefs.getInt(KEY_SESSIONS_COUNT, 0) + 1
        prefs.edit().putLong(KEY_TOTAL_USAGE_MS, total).putInt(KEY_SESSIONS_COUNT, sessions).apply()
        publish()
    }

    fun addTabTime(tab: AppDestinations, durationMs: Long) {
        if (durationMs <= 0) return
        when (tab) {
            AppDestinations.LEVEL -> prefs.edit().putLong(KEY_LEVEL_MS, prefs.getLong(KEY_LEVEL_MS, 0L) + durationMs).apply()
            AppDestinations.SOUNDMETER -> prefs.edit().putLong(KEY_SOUND_MS, prefs.getLong(KEY_SOUND_MS, 0L) + durationMs).apply()
            AppDestinations.FLASHLIGHT -> prefs.edit().putLong(KEY_FLASH_MS, prefs.getLong(KEY_FLASH_MS, 0L) + durationMs).apply()
            AppDestinations.OTHER -> prefs.edit().putLong(KEY_OTHER_MS, prefs.getLong(KEY_OTHER_MS, 0L) + durationMs).apply()
        }
        publish()
    }

    fun saveRating(rating: Int) {
        prefs.edit().putInt(KEY_LAST_RATING, rating).apply()
        publish()
    }
}
