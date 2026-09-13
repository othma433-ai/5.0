package com.waalothmany.linkbot.automation

object CheckpointCodec {
    private const val PREFIX = "v2:"

    fun encode(anchors: List<String>, maxAnchors: Int = 8): String {
        val clean = LinkedHashSet<String>()
        anchors.asSequence().map(String::trim).filter(String::isNotBlank).forEach(clean::add)
        return PREFIX + clean.take(maxAnchors).joinToString("|")
    }

    fun decode(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        val raw = if (value.startsWith(PREFIX)) value.removePrefix(PREFIX) else value
        return raw.split('|').map(String::trim).filter(String::isNotBlank).distinct()
    }

    fun reached(checkpoint: String?, visibleFingerprints: Collection<String>): Boolean {
        val anchors = decode(checkpoint).toHashSet()
        return anchors.isNotEmpty() && visibleFingerprints.any(anchors::contains)
    }
}
