package com.waalothmany.linkbot.automation

data class ExtractionSelectionItem(
    val id: String,
    val unread: Boolean,
)

object ExtractionSelectionPolicy {
    fun eligible(
        selected: List<ExtractionSelectionItem>,
        mode: AutomationMode,
    ): List<ExtractionSelectionItem> = when (mode) {
        AutomationMode.UNREAD_ONLY -> selected.filter { it.unread }
        AutomationMode.DEEP,
        AutomationMode.NEW_ONLY -> selected
    }
}
