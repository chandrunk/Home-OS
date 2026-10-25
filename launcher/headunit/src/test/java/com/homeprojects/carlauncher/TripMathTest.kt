package com.homeprojects.carlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripMathTest {
    @Test
    fun headingPointsAndWrap() {
        assertEquals("N / 015°", TripMath.heading(15f))
        assertEquals("NE / 045°", TripMath.heading(45f))
        assertEquals("N / 350°", TripMath.heading(350f))
        assertEquals("W / 270°", TripMath.heading(-90f))
    }

    @Test
    fun jitterWhileStoppedDoesNotCount() {
        assertFalse(TripMath.countSegment(4f, 8f, 0.5f))      // parked, GPS wanders a few metres
        assertFalse(TripMath.countSegment(40f, 60f, 50f))     // inaccurate fix
        assertTrue(TripMath.countSegment(15f, 8f, 54f))       // driving
        assertTrue(TripMath.countSegment(35f, 10f, 1f))       // moved clearly, speed not reported
    }

    @Test
    fun formatting() {
        assertEquals("00:42:18", TripMath.tripTime((42 * 60 + 18) * 1000L))
        assertEquals("24.8 KM", TripMath.distance(24_812.0))
        assertEquals("850 M", TripMath.distance(850.4))
        assertEquals(36f, TripMath.speedFrom(10f, 1000), 0.01f)
    }
}
