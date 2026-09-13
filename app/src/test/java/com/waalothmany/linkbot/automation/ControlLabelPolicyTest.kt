package com.waalothmany.linkbot.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlLabelPolicyTest {

    private val groups =
        listOf(
            "Groups",
            "المجموعات",
            "Group chats",
            "دردشات المجموعات",
        )

    @Test
    fun exactArabicGroupLabelMatches() {
        assertTrue(
            ControlLabelPolicy.matches(
                "المجموعات",
                groups,
            )
        )
    }

    @Test
    fun arabicBadgeCountMatches() {
        assertTrue(
            ControlLabelPolicy.matches(
                "المجموعات ٣",
                groups,
            )
        )
    }

    @Test
    fun arabicBadgeWithCommaMatches() {
        assertTrue(
            ControlLabelPolicy.matches(
                "المجموعات، ٣",
                groups,
            )
        )
    }

    @Test
    fun englishBadgeCountMatches() {
        assertTrue(
            ControlLabelPolicy.matches(
                "Groups 3",
                groups,
            )
        )
    }

    @Test
    fun englishParenthesizedBadgeMatches() {
        assertTrue(
            ControlLabelPolicy.matches(
                "Groups (12)",
                groups,
            )
        )
    }

    @Test
    fun realGroupTitleIsNotMistakenForFilter() {
        assertFalse(
            ControlLabelPolicy.matches(
                "المجموعات الطبية",
                groups,
            )
        )
    }
}
