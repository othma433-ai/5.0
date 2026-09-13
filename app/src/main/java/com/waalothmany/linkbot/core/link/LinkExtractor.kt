package com.waalothmany.linkbot.core.link

object LinkExtractor {
    // Accepts explicit schemes and common scheme-less hosts. Punctuation is repaired by UrlNormalizer.
    private val urlRegex = Regex(
        "(?i)(?:(?:https?|ftp)://[^\\s<>]+|(?:www\\.)[A-Z0-9.-]+\\.[A-Z]{2,}[^\\s<>]*|(?:chat\\.whatsapp\\.com|wa\\.me|t\\.me|drive\\.google\\.com|docs\\.google\\.com|youtu\\.be|youtube\\.com|mega\\.nz)/[^\\s<>]+|(?<!@)\\b(?:[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?\\.)+[A-Z]{2,}(?:/[^\\s<>]*)?)"
    )

    fun extract(text: String): List<LinkCandidate> {
        val seen = LinkedHashSet<String>()
        val output = ArrayList<LinkCandidate>()
        for (match in urlRegex.findAll(text.replace('\n', ' '))) {
            val raw = match.value
            val canonical = UrlNormalizer.normalize(raw)
            if (!isPlausible(canonical) || !seen.add(canonical)) continue
            output += LinkCandidate(raw, canonical, LinkClassifier.classify(canonical))
        }
        return output
    }

    private fun isPlausible(value: String): Boolean =
        value.startsWith("http://", true) || value.startsWith("https://", true) || value.startsWith("ftp://", true)
}
