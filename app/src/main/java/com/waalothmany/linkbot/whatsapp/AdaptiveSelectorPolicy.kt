package com.waalothmany.linkbot.whatsapp

enum class SelectorRole {
    GROUPS_FILTER,
    ALL_FILTER,
    CHATS_ANCHOR,
    SELECT_ALL,
    CONVERSATION_ROW_TITLE,
}

data class SelectorSignature(
    val resourceIdSuffix: String? = null,
    val normalizedLabel: String? = null,
    val className: String? = null,
) {
    fun isUseful(): Boolean = !resourceIdSuffix.isNullOrBlank() || !normalizedLabel.isNullOrBlank()
}

/** Pure selector helpers. No Android classes here so behavior is hermetically testable. */
object AdaptiveSelectorPolicy {
    fun resourceIdSuffix(resourceId: String?): String? {
        val raw = resourceId?.trim().orEmpty()
        val marker = ":id/"
        val index = raw.indexOf(marker)
        if (index <= 0 || index + marker.length >= raw.length) return null
        return raw.substring(index + marker.length).trim().takeIf { it.isNotEmpty() }
    }

    fun buildHints(packageName: String, suffixes: Collection<String>): List<String> {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return emptyList()
        return suffixes.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .map { "$pkg:id/$it" }
            .toList()
    }

    fun normalizedLabel(value: String?): String? = value
        ?.trim()
        ?.lowercase()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf { it.isNotEmpty() }
}
