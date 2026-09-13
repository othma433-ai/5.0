package com.waalothmany.linkbot.runtime

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BotRuntimeTest {

    @Before
    fun setUp() {
        BotRuntime.resetControlFlags()
        BotRuntime.update(RuntimeSnapshot())
    }

    @After
    fun tearDown() {
        BotRuntime.resetControlFlags()
        BotRuntime.update(RuntimeSnapshot())
    }

    @Test
    fun pauseAndResumeRestoreSyncingPhase() {
        BotRuntime.update(
            RuntimeSnapshot(
                phase = RuntimePhase.SYNCING,
                title = "sync",
            )
        )

        BotRuntime.pause()

        assertEquals(
            RuntimePhase.PAUSED,
            BotRuntime.state.value.phase,
        )

        BotRuntime.resume()

        assertEquals(
            RuntimePhase.SYNCING,
            BotRuntime.state.value.phase,
        )
    }

    @Test
    fun pauseAndResumeRestoreExtractingPhase() {
        BotRuntime.update(
            RuntimeSnapshot(
                phase = RuntimePhase.EXTRACTING,
                title = "extract",
                total = 4,
            )
        )

        BotRuntime.pause()
        BotRuntime.resume()

        assertEquals(
            RuntimePhase.EXTRACTING,
            BotRuntime.state.value.phase,
        )
    }

    @Test
    fun skipIsIgnoredOutsideExtraction() {
        BotRuntime.update(
            RuntimeSnapshot(
                phase = RuntimePhase.SYNCING,
                title = "sync",
            )
        )

        BotRuntime.skip()

        assertFalse(BotRuntime.consumeSkip())
    }

    @Test
    fun skipIsConsumedOnceDuringExtraction() {
        BotRuntime.update(
            RuntimeSnapshot(
                phase = RuntimePhase.EXTRACTING,
                title = "extract",
            )
        )

        BotRuntime.skip()

        assertTrue(BotRuntime.consumeSkip())
        assertFalse(BotRuntime.consumeSkip())
    }

    @Test
    fun telemetryIsClampedAndStored() {
        BotRuntime.updateTelemetry(
            healthScore = 150,
            effectiveMode = "FAST",
            groupsPerMinute = -10.0,
            linksPerMinute = 45.5,
        )

        val snapshot = BotRuntime.state.value

        assertEquals(100, snapshot.healthScore)
        assertEquals("FAST", snapshot.effectiveMode)
        assertEquals(0.0, snapshot.groupsPerMinute, 0.0)
        assertEquals(45.5, snapshot.linksPerMinute, 0.0)
    }
}
