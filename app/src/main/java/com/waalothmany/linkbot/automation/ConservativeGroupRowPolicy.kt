package com.waalothmany.linkbot.automation

/**
 * Last-resort classifier used only when WhatsApp does not expose the Groups filter.
 * It intentionally prefers false negatives over false positives so personal chats are
 * not registered as groups.
 */
object ConservativeGroupRowPolicy {
    private val groupIdHints = listOf(
        "group_avatar",
        "group_icon",
        "group_photo",
        "group_name",
        "group_chat",
        "participant",
    )

    private val strongPhrases = listOf(
        "group invite link",
        "this group's invite link",
        "joined using this group's invite link",
        "created group",
        "added you",
        "added to the group",
        "رابط المجموعة",
        "رابط هذه المجموعة",
        "انضم باستخدام رابط المجموعة",
        "انضم عبر رابط المجموعة",
        "أنشأ المجموعة",
        "انشأ المجموعة",
        "تمت إضافتك",
        "تمت اضافتك",
    )

    private val senderPrefix = Regex(
        """^\s*[~～]?\s*[^:\n]{1,80}:\s+.+$"""
    )

    fun isLikelyGroup(
        title: String,
        values: Collection<String>,
        viewIds: Collection<String>,
    ): Boolean {
        if (title.isBlank() || RowClassificationPolicy.isSystemLabel(title)) return false

        val ids = viewIds.map(String::lowercase)
        if (ids.any { id -> groupIdHints.any(id::contains) }) return true

        val normalizedValues = values
            .map { it.trim().lowercase() }
            .filter(String::isNotBlank)

        if (normalizedValues.any { value -> strongPhrases.any(value::contains) }) return true

        val previewCandidates = normalizedValues.filterNot {
            it == title.trim().lowercase()
        }
        if (previewCandidates.any(senderPrefix::matches)) return true

        return false
    }
}
