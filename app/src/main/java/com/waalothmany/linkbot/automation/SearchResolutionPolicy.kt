package com.waalothmany.linkbot.automation

data class SearchRowEvidence(
    val title: String,
    val preview: String?,
    val unreadCount: Int? = null,
)

sealed interface SearchResolution {
    data class Unique(val index: Int) : SearchResolution
    data class Ambiguous(val count: Int) : SearchResolution
    data object None : SearchResolution
}

/**
 * Fail-safe search resolver. It refuses to choose an arbitrary row when multiple
 * WhatsApp search results have the same title. Preview evidence is used only when
 * it selects exactly one candidate.
 */
object SearchResolutionPolicy {
    fun resolve(targetTitle: String, targetPreview: String?, rows: List<SearchRowEvidence>): SearchResolution {
        val title = normalize(targetTitle)
        val titled = rows.withIndex().filter { normalize(it.value.title) == title }
        if (titled.isEmpty()) return SearchResolution.None
        if (titled.size == 1) return SearchResolution.Unique(titled.first().index)

        val preview = normalizePreview(targetPreview)
        if (preview.isNotBlank()) {
            val previewMatches = titled.filter { candidate ->
                val candidatePreview = normalizePreview(candidate.value.preview)
                candidatePreview.isNotBlank() && previewsOverlap(preview, candidatePreview)
            }
            if (previewMatches.size == 1) return SearchResolution.Unique(previewMatches.first().index)
        }

        return SearchResolution.Ambiguous(titled.size)
    }

    private fun previewsOverlap(a: String, b: String): Boolean {
        if (a == b) return true
        val shortA = a.take(96)
        val shortB = b.take(96)
        if (shortA.length >= 10 && b.contains(shortA)) return true
        if (shortB.length >= 10 && a.contains(shortB)) return true
        val aTokens = shortA.split(' ').filter { it.length >= 3 }.toSet()
        val bTokens = shortB.split(' ').filter { it.length >= 3 }.toSet()
        if (aTokens.isEmpty() || bTokens.isEmpty()) return false
        val overlap = aTokens.intersect(bTokens).size
        return overlap >= 2 && overlap.toDouble() / minOf(aTokens.size, bTokens.size) >= 0.6
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")
    private fun normalizePreview(value: String?): String = value.orEmpty()
        .replace('\u200f'.toString(), "")
        .replace('\u200e'.toString(), "")
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
}
