// AppDestinationsTest.kt
package com.example.toolbox

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import org.junit.Assert.*
import org.junit.Test

class AppDestinationsTest {

    @Test
    fun `AppDestinations should have correct labels`() {
        assertEquals("Level", AppDestinations.LEVEL.label)
        assertEquals("Sound", AppDestinations.SOUNDMETER.label)
        assertEquals("Flashlight", AppDestinations.FLASHLIGHT.label)
        assertEquals("Other", AppDestinations.OTHER.label)
    }

    @Test
    fun `AppDestinations should have correct icons`() {
        assertEquals(Icons.Default.Straighten, AppDestinations.LEVEL.icon)
        assertEquals(Icons.Default.Mic, AppDestinations.SOUNDMETER.icon)
        assertEquals(Icons.Default.FlashlightOn, AppDestinations.FLASHLIGHT.icon)
        assertEquals(Icons.Default.MoreHoriz, AppDestinations.OTHER.icon)
    }

    @Test
    fun `AppDestinations entries should contain all values`() {
        val entries = AppDestinations.entries
        assertEquals(4, entries.size)
        assertTrue(entries.contains(AppDestinations.LEVEL))
        assertTrue(entries.contains(AppDestinations.SOUNDMETER))
        assertTrue(entries.contains(AppDestinations.FLASHLIGHT))
        assertTrue(entries.contains(AppDestinations.OTHER))
    }
}