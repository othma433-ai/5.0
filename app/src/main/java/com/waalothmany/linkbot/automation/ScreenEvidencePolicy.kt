package com.waalothmany.linkbot.automation

enum class ScreenKind { GROUP_LIST, CHAT, SEARCH, UNKNOWN }

data class ScreenSignals(
    val groupsFilterVisible: Boolean,
    val groupsFilterActive: Boolean,
    val conversationRowCount: Int,
    val searchEditorVisible: Boolean,
    val composerVisible: Boolean,
    val expectedTitleVisible: Boolean,
    val scrollableCount: Int,
)

data class ScreenEvidence(
    val kind: ScreenKind,
    val confidence: Int,
    val reasons: List<String>,
) {
    fun isConfident(minConfidence: Int = 80): Boolean = confidence >= minConfidence
}

/**
 * Pure semantic screen classifier. It deliberately scores independent evidence instead of
 * trusting one WhatsApp resource id, so UI-id drift degrades confidence rather than causing
 * an unsafe action on the wrong screen.
 */
object ScreenEvidencePolicy {
    fun classify(signals: ScreenSignals): ScreenEvidence {
        val groupReasons = mutableListOf<String>()
        var groupScore = 0
        if (signals.groupsFilterVisible) { groupScore += 20; groupReasons += "groups-filter-visible" }
        if (signals.groupsFilterActive) { groupScore += 35; groupReasons += "groups-filter-active" }
        if (signals.conversationRowCount >= 2) { groupScore += 30; groupReasons += "conversation-rows" }
        if (signals.scrollableCount > 0) { groupScore += 10; groupReasons += "scrollable-list" }
        if (signals.searchEditorVisible) groupScore -= 50
        if (signals.composerVisible) groupScore -= 45

        val chatReasons = mutableListOf<String>()
        var chatScore = 0
        if (signals.composerVisible) { chatScore += 50; chatReasons += "composer-visible" }
        if (signals.expectedTitleVisible) { chatScore += 30; chatReasons += "expected-title" }
        if (signals.scrollableCount > 0) { chatScore += 15; chatReasons += "message-list" }
        if (signals.searchEditorVisible) chatScore -= 50
        if (signals.groupsFilterActive) chatScore -= 45

        val searchReasons = mutableListOf<String>()
        var searchScore = 0
        if (signals.searchEditorVisible) { searchScore += 70; searchReasons += "search-editor" }
        if (signals.conversationRowCount > 0) { searchScore += 15; searchReasons += "search-results" }
        if (signals.scrollableCount > 0) { searchScore += 10; searchReasons += "results-list" }
        if (signals.composerVisible) searchScore -= 50
        if (signals.groupsFilterActive) searchScore -= 20

        val ranked = listOf(
            Triple(ScreenKind.GROUP_LIST, groupScore.coerceIn(0, 100), groupReasons.toList()),
            Triple(ScreenKind.CHAT, chatScore.coerceIn(0, 100), chatReasons.toList()),
            Triple(ScreenKind.SEARCH, searchScore.coerceIn(0, 100), searchReasons.toList()),
        ).sortedByDescending { it.second }

        val best = ranked.first()
        val second = ranked.getOrNull(1)?.second ?: 0
        // Refuse to classify weak or tightly competing evidence as a real screen.
        if (best.second < 60 || best.second - second < 15) {
            return ScreenEvidence(ScreenKind.UNKNOWN, best.second, best.third + "ambiguous-evidence")
        }
        return ScreenEvidence(best.first, best.second, best.third)
    }
}
