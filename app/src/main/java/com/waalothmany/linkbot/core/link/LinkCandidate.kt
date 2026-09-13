package com.waalothmany.linkbot.core.link

data class LinkCandidate(
    val rawUrl: String,
    val canonicalUrl: String,
    val category: LinkCategory,
)
