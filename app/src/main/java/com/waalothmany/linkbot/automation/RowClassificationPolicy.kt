package com.waalothmany.linkbot.automation

/**
 * Pure conversation-row classification logic kept outside Android UI APIs so it can be
 * regression-tested without an emulator. AccessibilityTree supplies the structural evidence.
 */
object RowClassificationPolicy {
    private val systemLabels = setOf(
        "search", "بحث",
        "new chat", "دردشة جديدة",
        "archived", "المؤرشفة",
        "communities", "المجتمعات",
        "settings", "الإعدادات",
        "calls", "المكالمات",
        "updates", "التحديثات",
        "chats", "الدردشات",
        "groups", "المجموعات",
        "all", "الكل",
        "unread", "غير المقروءة",
        "favorites", "المفضلة",
        "locked chats", "الدردشات المقفلة",
        "new group", "مجموعة جديدة",
        "manage groups", "إدارة المجموعات",
        "new community", "مجتمع جديد",
        "channels", "القنوات",
        "broadcast lists", "قوائم البث",
    )

    private val rowIdHints = listOf(
        "contact_name",
        "conversation",
        "chat_title",
        "conversation_name",
        "conversations_row",
        "chat_row",
    )

    private val clockRegex = Regex("^\\s*\\d{1,2}:\\d{2}(?:\\s*[AaPp][Mm])?\\s*$")
    private val plainCountRegex = Regex("^\\s*[0-9٠-٩]{1,4}\\s*$")
    private val englishUnreadRegex = Regex("([0-9٠-٩]{1,4})\\s*(?:unread(?:\\s+messages?)?|new messages?)", RegexOption.IGNORE_CASE)
    private val arabicUnreadRegex = Regex("([0-9٠-٩]{1,4})\\s*(?:(?:رسالة|رسائل)\\s*)?(?:غير\\s*مقرو(?:ءة|ءه|ؤة)?|جديدة)")

    fun isSystemLabel(value: String): Boolean = normalize(value) in systemLabels

    fun isLikelyConversationRow(
        title: String,
        rowClickable: Boolean,
        subtreeClickable: Boolean,
        viewIds: Collection<String>,
    ): Boolean {
        if (title.isBlank() || title.length > 180 || isSystemLabel(title)) return false
        val structuralId = viewIds.any { id ->
            val lowered = id.lowercase()
            rowIdHints.any(lowered::contains)
        }
        return rowClickable || subtreeClickable || structuralId
    }

    fun extractUnreadCount(values: Collection<String>): Int? {
        for (value in values) {
            val match = englishUnreadRegex.find(value) ?: arabicUnreadRegex.find(value) ?: continue
            return parseLocalizedInt(match.groupValues[1])
        }
        return null
    }

    fun isUnread(values: Collection<String>): Boolean = values.any { value ->
        val n = normalize(value)
        n.contains("unread") || n.contains("غير مقرو") || n.contains("رسائل غير مقروءة")
    } || extractUnreadCount(values) != null

    fun isActive(values: Collection<String>): Boolean = values.any { raw ->
        val value = raw.trim()
        val n = normalize(value)
        clockRegex.matches(value) || n == "today" || n == "yesterday" ||
            n == "اليوم" || n == "أمس" || n == "امس"
    }

    fun pickPreview(title: String, values: Collection<String>): String? {
        val normalizedTitle = normalize(title)
        return values.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .firstOrNull { value ->
                val normalized = normalize(value)
                normalized != normalizedTitle &&
                    normalized !in systemLabels &&
                    !clockRegex.matches(value) &&
                    !plainCountRegex.matches(value) &&
                    englishUnreadRegex.find(value) == null &&
                    arabicUnreadRegex.find(value) == null &&
                    normalized !in setOf("today", "yesterday", "اليوم", "أمس", "امس")
            }
    }

    private fun normalize(value: String): String = value.trim().lowercase()

    private fun parseLocalizedInt(value: String): Int? {
        val ascii = buildString(value.length) {
            value.forEach { c ->
                append(
                    when (c) {
                        '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                        '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                        else -> c
                    }
                )
            }
        }
        return ascii.toIntOrNull()
    }
}
