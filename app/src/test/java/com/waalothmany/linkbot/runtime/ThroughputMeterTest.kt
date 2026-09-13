package com.waalothmany.linkbot.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThroughputMeterTest {
    @Test
    fun reportsRatesFromSessionStart() {
        val meter = ThroughputMeter(startedAtMs = 1_000)
        meter.addGroups(30, atMs = 31_000)
        meter.addLinks(120, atMs = 31_000)
        val snapshot = meter.snapshot(nowMs = 61_000)
        assertTrue(snapshot.groupsPerMinute in 29.0..31.0)
        assertTrue(snapshot.linksPerMinute in 119.0..121.0)
        assertEquals(30, snapshot.groupsProcessed)
        assertEquals(120, snapshot.linksProcessed)
    }
}
