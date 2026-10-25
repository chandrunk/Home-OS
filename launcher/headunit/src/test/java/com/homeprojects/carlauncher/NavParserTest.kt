package com.homeprojects.carlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavParserTest {
    @Test
    fun distanceTitleRoadText() {
        val n = parseMapsNotification("250 m", "Jl. Jend. Sudirman (Semanggi)", "14 min · 5.2 km · 20:40 ETA")!!
        assertEquals("250m", n.distance)
        assertNull(n.maneuver)
        assertEquals("Jl. Jend. Sudirman (Semanggi)", n.road)
        assertEquals(14, n.etaMin)
    }

    @Test
    fun instructionWithRoadEnglish() {
        val n = parseMapsNotification("1,2 km", "Turn left onto Jl. Gatot Subroto", "1 hr 5 min · 40 km")!!
        assertEquals("1.2km", n.distance)
        assertEquals("TURN LEFT", n.maneuver)
        assertEquals("Jl. Gatot Subroto", n.road)
        assertEquals(65, n.etaMin)
    }

    @Test
    fun indonesianInstructionLongConnectorFirst() {
        val n = parseMapsNotification("Belok kanan ke arah Blok M", "300 m", "8 mnt · 3,1 km")!!
        assertEquals("300m", n.distance)
        assertEquals("TURN RIGHT", n.maneuver)
        assertEquals("Blok M", n.road)
        assertEquals(8, n.etaMin)
    }

    @Test
    fun instructionOnlyHasNoRoad() {
        val n = parseMapsNotification("Keep right", "", "")!!
        assertEquals("KEEP RIGHT", n.maneuver)
        assertNull(n.road)
        assertNull(n.etaMin)
    }

    @Test
    fun otherMapsNotificationsAreIgnored() {
        assertNull(parseMapsNotification("Google Maps", "Location sharing is on", null))
    }
}
