package com.waalothmany.linkbot.core.importer

data class ParsedMessage(
    val timestampRaw: String?,
    val sender: String?,
    val text: String,
)
