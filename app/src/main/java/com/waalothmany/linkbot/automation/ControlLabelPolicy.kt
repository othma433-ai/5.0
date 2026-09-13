package com.waalothmany.linkbot.automation

/**
 * Canonical matcher for WhatsApp navigation/filter controls.
 *
 * WhatsApp frequently appends badge counts to labels:
 *
 *   Groups 3
 *   Groups (3)
 *   المجموعات ٣
 *   المجموعات، ٣
 *
 * Those numbers represent UI state and are not part of the control's
 * semantic identity.
 */
object ControlLabelPolicy {

    private val bidiMarks =
        Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")

    private val whitespace =
        Regex("\\s+")

    private val trailingBadge =
        Regex("""[\s،,:·•\-–—]*(?:\(?\p{Nd}+\)?)\s*$""")

    fun canonical(value: String): String =
        value
            .replace(bidiMarks, "")
            .trim()
            .lowercase()
            .replace(whitespace, " ")
            .replace(trailingBadge, "")
            .trim()

    fun matches(
        candidate: String,
        labels: Collection<String>,
    ): Boolean {
        val normalized = canonical(candidate)
        if (normalized.isBlank()) return false

        return labels.any {
            canonical(it) == normalized
        }
    }
}
