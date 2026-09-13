package com.waalothmany.linkbot.automation

fun main() {
    val groupList = ScreenEvidencePolicy.classify(
        ScreenSignals(
            groupsFilterVisible = true,
            groupsFilterActive = true,
            conversationRowCount = 8,
            searchEditorVisible = false,
            composerVisible = false,
            expectedTitleVisible = false,
            scrollableCount = 1,
        )
    )
    check(groupList.kind == ScreenKind.GROUP_LIST)
    check(groupList.confidence >= 85)

    val chat = ScreenEvidencePolicy.classify(
        ScreenSignals(
            groupsFilterVisible = false,
            groupsFilterActive = false,
            conversationRowCount = 0,
            searchEditorVisible = false,
            composerVisible = true,
            expectedTitleVisible = true,
            scrollableCount = 1,
        )
    )
    check(chat.kind == ScreenKind.CHAT)
    check(chat.confidence >= 90)

    val search = ScreenEvidencePolicy.classify(
        ScreenSignals(
            groupsFilterVisible = false,
            groupsFilterActive = false,
            conversationRowCount = 5,
            searchEditorVisible = true,
            composerVisible = false,
            expectedTitleVisible = false,
            scrollableCount = 1,
        )
    )
    check(search.kind == ScreenKind.SEARCH)
    check(search.confidence >= 80)

    val ambiguous = ScreenEvidencePolicy.classify(
        ScreenSignals(
            groupsFilterVisible = false,
            groupsFilterActive = false,
            conversationRowCount = 0,
            searchEditorVisible = false,
            composerVisible = false,
            expectedTitleVisible = false,
            scrollableCount = 1,
        )
    )
    check(ambiguous.kind == ScreenKind.UNKNOWN)
    check(ambiguous.confidence < 60)
    println("ScreenEvidencePolicySmoke: PASS")
}
