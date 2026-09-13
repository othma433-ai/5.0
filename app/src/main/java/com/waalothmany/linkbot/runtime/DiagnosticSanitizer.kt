package com.waalothmany.linkbot.runtime

object DiagnosticSanitizer {
    private val url = Regex("(?i)\\b(?:https?|ftp)://\\S+")
    private val longNumber = Regex("(?<!\\d)\\d{9,}(?!\\d)")

    fun sanitize(value: String, maxLength: Int = 160): String = value
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace(url, "<url>")
        .replace(longNumber, "<number>")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(maxLength)
}
