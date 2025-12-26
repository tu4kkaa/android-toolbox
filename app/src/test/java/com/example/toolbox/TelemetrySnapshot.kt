package com.example.toolbox

import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetrySnapshotTest {

    @Test
    fun `average session time calculated correctly`() {
        val snapshot = TelemetrySnapshot(
            launchCount = 0,
            totalUsageMs = 10_000,
            sessionsCount = 4,

            totalLevelMs = 0,
            totalSoundMs = 0,
            totalFlashMs = 0,
            totalOtherMs = 0,

            levelVisits = 0,
            soundVisits = 0,
            flashVisits = 0,
            otherVisits = 0,

            lastRating = null
        )

        assertEquals(2_500L, snapshot.averageSessionMs)
    }

    @Test
    fun `average per tab calculated by visits`() {
        val snapshot = TelemetrySnapshot(
            launchCount = 0,
            totalUsageMs = 0,
            sessionsCount = 0,

            totalLevelMs = 3_000,
            totalSoundMs = 2_000,
            totalFlashMs = 0,
            totalOtherMs = 0,

            levelVisits = 3,
            soundVisits = 1,
            flashVisits = 0,
            otherVisits = 0,

            lastRating = null
        )

        val avg = snapshot.averagePerTabMs

        assertEquals(1_000L, avg["Level"])
        assertEquals(2_000L, avg["Sound"])
        assertEquals(0L, avg["Flash"])
        assertEquals(0L, avg["Other"])
    }

    @Test
    fun `total per tab returns accumulated time`() {
        val snapshot = TelemetrySnapshot(
            launchCount = 0,
            totalUsageMs = 0,
            sessionsCount = 0,

            totalLevelMs = 5_000,
            totalSoundMs = 3_000,
            totalFlashMs = 1_000,
            totalOtherMs = 500,

            levelVisits = 5,
            soundVisits = 3,
            flashVisits = 1,
            otherVisits = 1,

            lastRating = null
        )

        val total = snapshot.totalPerTabMs

        assertEquals(5_000L, total["Level"])
        assertEquals(3_000L, total["Sound"])
        assertEquals(1_000L, total["Flash"])
        assertEquals(500L, total["Other"])
    }
}
