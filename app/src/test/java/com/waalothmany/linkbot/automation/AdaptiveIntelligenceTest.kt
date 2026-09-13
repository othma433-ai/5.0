package com.waalothmany.linkbot.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveIntelligenceTest {
    @Test
    fun screenClassifierPrefersSemanticEvidence() {
        val list = ScreenEvidencePolicy.classify(
            ScreenSignals(true, true, 8, false, false, false, 1)
        )
        assertEquals(ScreenKind.GROUP_LIST, list.kind)
        assertTrue(list.confidence >= 85)

        val chat = ScreenEvidencePolicy.classify(
            ScreenSignals(false, false, 0, false, true, true, 1)
        )
        assertEquals(ScreenKind.CHAT, chat.kind)
        assertTrue(chat.confidence >= 90)
    }

    @Test
    fun healthGovernorSlowsAfterAmbiguityAndRecoversAfterSuccess() {
        val health = AutomationHealthPolicy()
        repeat(10) { health.recordSuccess() }
        assertEquals(PerformanceMode.FAST, health.snapshot().recommendedMode)

        health.recordAmbiguity()
        health.recordTransientFailure()
        health.recordTransientFailure()
        assertEquals(PerformanceMode.SAFE, health.snapshot().recommendedMode)

        repeat(20) { health.recordSuccess() }
        assertTrue(health.snapshot().recommendedMode != PerformanceMode.SAFE)
    }

    @Test
    fun unreadViewportStartsAfterMarkerAndDedupesOverlap() {
        val items = listOf(
            ViewportMessage("before", false),
            ViewportMessage(null, true),
            ViewportMessage("new1", false),
            ViewportMessage("new2", false),
        )
        val selected = MessageViewportPolicy.select(items, seen = setOf("new1"), unreadOnly = true)
        assertEquals(listOf("new2"), selected)
    }

    @Test
    fun containerPolicySeparatesConversationAndMessageLists() {
        val conversations = ContainerRolePolicy.score(ContainerSignals(10, 9, 10, 0))
        val messages = ContainerRolePolicy.score(ContainerSignals(14, 0, 13, 3))
        assertEquals(ContainerRole.CONVERSATIONS, ContainerRolePolicy.preferredRole(conversations))
        assertEquals(ContainerRole.MESSAGES, ContainerRolePolicy.preferredRole(messages))
    }
}
