package com.vexmusic.player

import com.vexmusic.player.audio.EqualizerController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerPresetTest {

    @Test
    fun `flat preset maps to every reported device band`() {
        val levels = EqualizerController.levelsFor("Flat", 10, -1200, 1200)

        assertEquals(10, levels.size)
        assertTrue(levels.all { it == 0 })
    }

    @Test
    fun `preset output respects device band limits`() {
        val levels = EqualizerController.levelsFor("Bass Boost", 7, -800, 900)

        assertEquals(7, levels.size)
        assertTrue(levels.all { it in -800..900 })
    }

    @Test
    fun `unsupported band count produces no settings`() {
        assertTrue(EqualizerController.levelsFor("Rock", 0, -1000, 1000).isEmpty())
    }
}