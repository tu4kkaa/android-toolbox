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

    // counts of visits / hits per tab
    val levelVisits: Int,
    val soundVisits: Int,
    val flashVisits: Int,
    val otherVisits: Int,

    val lastRating: Int?
) {
    val averageSessionMs: Long
        get() = if (sessionsCount > 0) totalUsageMs / sessionsCount else 0L

    /**
     * Среднее время на одно посещение каждой вкладки (в миллисекундах).
     * Если посещений для вкладки нет — 0.
     */
    val averagePerTabMs: Map<String, Long>
        get() {
            val lvl = if (levelVisits > 0) totalLevelMs / levelVisits.toLong() else 0L
            val snd = if (soundVisits > 0) totalSoundMs / soundVisits.toLong() else 0L
            val fls = if (flashVisits > 0) totalFlashMs / flashVisits.toLong() else 0L
            val oth = if (otherVisits > 0) totalOtherMs / otherVisits.toLong() else 0L
            return mapOf(
                "Level" to lvl,
                "Sound" to snd,
                "Flash" to fls,
                "Other" to oth
            )
        }

    /**
     * Новое: суммарное (общее) время по каждой вкладке.
     * Это именно то, что вы просили — общее накопленное время (в миллисекундах).
     */
    val totalPerTabMs: Map<String, Long>
        get() = mapOf(
            "Level" to totalLevelMs,
            "Sound" to totalSoundMs,
            "Flash" to totalFlashMs,
            "Other" to totalOtherMs
        )
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

    // new: per-tab visit counters
    private val KEY_LEVEL_COUNT = "level_count"
    private val KEY_SOUND_COUNT = "sound_count"
    private val KEY_FLASH_COUNT = "flash_count"
    private val KEY_OTHER_COUNT = "other_count"

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

        val levelVisits = prefs.getInt(KEY_LEVEL_COUNT, 0)
        val soundVisits = prefs.getInt(KEY_SOUND_COUNT, 0)
        val flashVisits = prefs.getInt(KEY_FLASH_COUNT, 0)
        val otherVisits = prefs.getInt(KEY_OTHER_COUNT, 0)

        val lastRating = if (prefs.contains(KEY_LAST_RATING)) prefs.getInt(KEY_LAST_RATING, 0) else null
        return TelemetrySnapshot(
            launchCount, totalUsageMs, sessionsCount,
            totalLevelMs, totalSoundMs, totalFlashMs, totalOtherMs,
            levelVisits, soundVisits, flashVisits, otherVisits,
            lastRating
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

    /**
     * Добавление сессии (увеличивает общий суммарный таймер и счётчик сессий).
     * Эта функция остаётся как прежде.
     */
    fun addSession(durationMs: Long) {
        if (durationMs <= 0) return
        val total = prefs.getLong(KEY_TOTAL_USAGE_MS, 0L) + durationMs
        val sessions = prefs.getInt(KEY_SESSIONS_COUNT, 0) + 1
        prefs.edit().putLong(KEY_TOTAL_USAGE_MS, total).putInt(KEY_SESSIONS_COUNT, sessions).apply()
        publish()
    }

    /**
     * Добавляет время для конкретной вкладки и увеличивает соответствующий счётчик посещений.
     *
     * Важно: вызов этой функции ожидается при уходе с вкладки (то есть за каждое посещение вкладки
     * вызывать один раз addTabTime(tab, durationMs)), тогда счётчики правильно отражают число посещений.
     */
    fun addTabTime(tab: AppDestinations, durationMs: Long) {
        if (durationMs <= 0) return
        when (tab) {
            AppDestinations.LEVEL -> {
                val newMs = prefs.getLong(KEY_LEVEL_MS, 0L) + durationMs
                val newCount = prefs.getInt(KEY_LEVEL_COUNT, 0) + 1
                prefs.edit().putLong(KEY_LEVEL_MS, newMs).putInt(KEY_LEVEL_COUNT, newCount).apply()
            }
            AppDestinations.SOUNDMETER -> {
                val newMs = prefs.getLong(KEY_SOUND_MS, 0L) + durationMs
                val newCount = prefs.getInt(KEY_SOUND_COUNT, 0) + 1
                prefs.edit().putLong(KEY_SOUND_MS, newMs).putInt(KEY_SOUND_COUNT, newCount).apply()
            }
            AppDestinations.FLASHLIGHT -> {
                val newMs = prefs.getLong(KEY_FLASH_MS, 0L) + durationMs
                val newCount = prefs.getInt(KEY_FLASH_COUNT, 0) + 1
                prefs.edit().putLong(KEY_FLASH_MS, newMs).putInt(KEY_FLASH_COUNT, newCount).apply()
            }
            AppDestinations.OTHER -> {
                val newMs = prefs.getLong(KEY_OTHER_MS, 0L) + durationMs
                val newCount = prefs.getInt(KEY_OTHER_COUNT, 0) + 1
                prefs.edit().putLong(KEY_OTHER_MS, newMs).putInt(KEY_OTHER_COUNT, newCount).apply()
            }
        }
        publish()
    }

    fun saveRating(rating: Int) {
        prefs.edit().putInt(KEY_LAST_RATING, rating).apply()
        publish()
    }
}
