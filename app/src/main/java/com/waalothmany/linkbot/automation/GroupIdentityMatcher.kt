package com.waalothmany.linkbot.automation

data class GroupIdentityRecord(
    val id: String,
    val normalizedTitle: String,
    val rowFingerprint: String,
    val preview: String?,
    val unreadCount: Int?,
)

data class GroupIdentityEvidence(
    val normalizedTitle: String,
    val rowFingerprint: String,
    val preview: String?,
    val unreadCount: Int?,
)

/**
 * Reconciles the current WhatsApp row with a previously known local group record.
 * It intentionally returns null on ambiguous duplicate titles instead of silently
 * attaching a row to the wrong group.
 */
object GroupIdentityMatcher {
    fun match(
        evidence: GroupIdentityEvidence,
        existing: List<GroupIdentityRecord>,
        usedIds: Set<String>,
    ): String? {
        val title = normalize(evidence.normalizedTitle)
        val candidates = existing.filter { it.id !in usedIds && normalize(it.normalizedTitle) == title }
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) {
            val candidate = candidates.first()
            val previewA = normalizePreview(evidence.preview)
            val previewB = normalizePreview(candidate.preview)
            val structureCompatible = evidence.rowFingerprint == candidate.rowFingerprint
            val previewCompatible = previewA.isNotBlank() && previewB.isNotBlank() &&
                (previewA == previewB || previewOverlap(previewA, previewB))
            // A single historical same-title row is normally the same group, but when
            // both available strong signals conflict we refuse to silently rebind it.
            return if (structureCompatible || previewCompatible || previewA.isBlank() || previewB.isBlank()) candidate.id else null
        }

        val scored = candidates.map { candidate -> candidate to score(evidence, candidate) }
        val bestScore = scored.maxOf { it.second }
        if (bestScore < 3) return null
        val best = scored.filter { it.second == bestScore }
        return if (best.size == 1) best.first().first.id else null
    }

    private fun score(evidence: GroupIdentityEvidence, record: GroupIdentityRecord): Int {
        var value = 0
        if (evidence.rowFingerprint == record.rowFingerprint) value += 1
        if (evidence.unreadCount != null && evidence.unreadCount == record.unreadCount) value += 1

        val a = normalizePreview(evidence.preview)
        val b = normalizePreview(record.preview)
        if (a.isNotBlank() && b.isNotBlank()) {
            if (a == b) value += 6
            else if (previewOverlap(a, b)) value += 4
        }
        return value
    }

    private fun previewOverlap(a: String, b: String): Boolean {
        if (a.length >= 10 && b.contains(a.take(96))) return true
        if (b.length >= 10 && a.contains(b.take(96))) return true
        val aTokens = a.take(120).split(' ').filter { it.length >= 3 }.toSet()
        val bTokens = b.take(120).split(' ').filter { it.length >= 3 }.toSet()
        if (aTokens.isEmpty() || bTokens.isEmpty()) return false
        val overlap = aTokens.intersect(bTokens).size
        return overlap >= 2 && overlap.toDouble() / minOf(aTokens.size, bTokens.size) >= 0.6
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")
    private fun normalizePreview(value: String?): String = value.orEmpty()
        .replace("\\u200f", "")
        .replace("\\u200e", "")
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
}
