package com.example.toolbox

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
@Config(manifest = Config.NONE)
class TelemetryManagerTest {

    private lateinit var context: Context
    private lateinit var telemetry: TelemetryManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // clear prefs before each test
        context.getSharedPreferences("toolbox_telemetry", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        telemetry = TelemetryManager(context)
    }

    @Test
    fun `launch count increments correctly`() = runTest {
        val first = telemetry.incrementLaunchCount()
        val second = telemetry.incrementLaunchCount()

        assertEquals(1, first)
        assertEquals(2, second)

        assertEquals(2, telemetry.getSnapshot().launchCount)
    }

    @Test
    fun `addSession increases total usage and session count`() {
        telemetry.addSession(1_000)
        telemetry.addSession(2_000)

        val snapshot = telemetry.getSnapshot()

        assertEquals(2, snapshot.sessionsCount)
        assertEquals(3_000, snapshot.totalUsageMs)
    }

    @Test
    fun `addTabTime increases time and visit counter`() {
        telemetry.addTabTime(AppDestinations.LEVEL, 1_000)
        telemetry.addTabTime(AppDestinations.LEVEL, 500)
        telemetry.addTabTime(AppDestinations.SOUNDMETER, 2_000)

        val snapshot = telemetry.getSnapshot()

        assertEquals(1_500, snapshot.totalLevelMs)
        assertEquals(2, snapshot.levelVisits)

        assertEquals(2_000, snapshot.totalSoundMs)
        assertEquals(1, snapshot.soundVisits)

        assertEquals(0, snapshot.flashVisits)
        assertEquals(0, snapshot.otherVisits)
    }

    @Test
    fun `zero or negative tab duration is ignored`() {
        telemetry.addTabTime(AppDestinations.LEVEL, 0)
        telemetry.addTabTime(AppDestinations.LEVEL, -100)

        val snapshot = telemetry.getSnapshot()

        assertEquals(0, snapshot.totalLevelMs)
        assertEquals(0, snapshot.levelVisits)
    }

    @Test
    fun `rating is stored correctly`() {
        telemetry.saveRating(5)

        assertEquals(5, telemetry.getSnapshot().lastRating)
    }
}
