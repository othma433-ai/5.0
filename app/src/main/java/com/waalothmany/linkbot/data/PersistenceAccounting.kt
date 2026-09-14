package com.waalothmany.linkbot.data

data class LinkPersistResult(
    val newLinks: Int = 0,
    val insertedOccurrences: Int = 0,
    val duplicateOccurrences: Int = 0,
) {
    val persistedCount: Int get() = insertedOccurrences
    val detectedCount: Int get() = insertedOccurrences + duplicateOccurrences

    operator fun plus(other: LinkPersistResult): LinkPersistResult = LinkPersistResult(
        newLinks = newLinks + other.newLinks,
        insertedOccurrences = insertedOccurrences + other.insertedOccurrences,
        duplicateOccurrences = duplicateOccurrences + other.duplicateOccurrences,
    )
}
