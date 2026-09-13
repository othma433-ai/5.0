package com.waalothmany.linkbot.automation

/**
 * Canonical matcher for WhatsApp filter/navigation labels.
 *
 * WhatsApp frequently decorates labels with counters before/after the semantic
 * label and may add state words in content descriptions, for example:
 *   "Groups +99", "+99 المجموعات", "المجموعات +99 دردشة",
 *   "Groups, 12 unread".
 */
object ControlLabelPolicy {
    private val bidiMarks = Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")
    private val whitespace = Regex("\\s+")

    private val leadingBadge = Regex(
        """^\s*(?:\(?[+]?\p{Nd}+[+]?\)?)[\s،,:·•\-–—]*"""
    )
    private val trailingBadge = Regex(
        """[\s،,:·•\-–—]*(?:\(?[+]?\p{Nd}+[+]?\)?)\s*$"""
    )

    private val decorationTokens = setOf(
        "chat", "chats", "conversation", "conversations",
        "unread", "selected", "active", "new", "messages", "message",
        "دردشة", "دردشات", "محادثة", "محادثات", "غير", "مقروءة", "مقروءه",
        "مقروء", "رسالة", "رسائل", "جديدة", "جديد", "محدد", "مفعّل", "مفعل",
    )

    fun canonical(value: String): String {
        var normalized = value
            .replace(bidiMarks, "")
            .trim()
            .lowercase()
            .replace(whitespace, " ")

        repeat(2) {
            normalized = normalized
                .replace(leadingBadge, "")
                .replace(trailingBadge, "")
                .trim()
        }
        return normalized
    }

    fun matches(candidate: String, labels: Collection<String>): Boolean {
        val normalized = canonical(candidate)
        if (normalized.isBlank()) return false
        return labels.any { canonical(it) == normalized }
    }

    /**
     * Semantic matcher for accessibility content descriptions that append a
     * badge/state suffix. Remaining tokens must be UI-state decorations only;
     * arbitrary conversation titles such as "المجموعات الطبية" are rejected.
     */
    fun matchesDecorated(candidate: String, labels: Collection<String>): Boolean {
        val normalized = canonical(candidate)
            .replace(Regex("""[،,:;]+"""), " ")
            .replace(whitespace, " ")
            .trim()
        if (normalized.isBlank()) return false
        if (labels.any { canonical(it) == normalized }) return true

        return labels.any { rawLabel ->
            val label = canonical(rawLabel)
            if (label.isBlank()) return@any false
            val remainder = when {
                normalized.startsWith("$label ") -> normalized.removePrefix(label).trim()
                normalized.endsWith(" $label") -> normalized.removeSuffix(label).trim()
                else -> return@any false
            }
            decorationsOnly(remainder)
        }
    }

    private fun decorationsOnly(value: String): Boolean {
        if (value.isBlank()) return true
        val cleaned = value
            .replace(Regex("""[()\[\]{}،,:;·•\-–—]+"""), " ")
            .replace(whitespace, " ")
            .trim()
        if (cleaned.isBlank()) return true
        return cleaned.split(' ').all { token ->
            token.isBlank() || token in decorationTokens || token.matches(Regex("[+]?\\p{Nd}+[+]?"))
        }
    }
}
