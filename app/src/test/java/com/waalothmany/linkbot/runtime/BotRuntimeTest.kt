package com.waalothmany.linkbot.runtime

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

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
        BotRuntime.update(RuntimeSnapshot(RuntimePhase.SYNCING, "sync"))
        BotRuntime.pause()
        assertEquals(RuntimePhase.PAUSED, BotRuntime.state.value.phase)

        BotRuntime.resume()
        assertEquals(RuntimePhase.SYNCING, BotRuntime.state.value.phase)
    }

    @Test
    fun pauseAndResumeRestoreExtractingPhase() {
        BotRuntime.update(RuntimeSnapshot(RuntimePhase.EXTRACTING, "extract", total = 4))
        BotRuntime.pause()
        BotRuntime.resume()
        assertEquals(RuntimePhase.EXTRACTING, BotRuntime.state.value.phase)
    }

    @Test
    fun skipIsAcceptedOnlyDuringExtraction() {
        BotRuntime.update(RuntimeSnapshot(RuntimePhase.SYNCING, "sync"))
        BotRuntime.skip()
        assertFalse(BotRuntime.consumeSkip())

        BotRuntime.update(RuntimeSnapshot(RuntimePhase.EXTRACTING, "extract"))
        BotRuntime.skip()
        assertTrue(BotRuntime.consumeSkip())
        assertFalse(BotRuntime.consumeSkip())
    }
}
