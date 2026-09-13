package com.waalothmany.linkbot.core.link

import java.net.URI

object UrlNormalizer {
    private val trailingPunctuation = Regex("[\\s>,.!?;:'\"،؛؟]+$")

    fun normalize(input: String): String {
        var value = input.trim()
            .removePrefix("<")
            .removeSuffix(">")
            .replace("\u200B", "")
            .replace("\u2060", "")
        value = value.replace(trailingPunctuation, "")
        value = trimUnbalancedClosers(value)
        if (value.startsWith("www.", ignoreCase = true)) value = "https://$value"
        if (!value.contains("://") && looksLikeHost(value)) value = "https://$value"

        return runCatching {
            val uri = URI(value)
            val scheme = (uri.scheme ?: "https").lowercase()
            val host = uri.host?.lowercase() ?: return@runCatching value
            val port = if (uri.port == -1) "" else ":${uri.port}"
            val rawPath = uri.rawPath.orEmpty()
            val path = if (rawPath == "/") "" else rawPath.trimEnd('/')
            val query = filteredQuery(host, uri.rawQuery)?.let { "?$it" }.orEmpty()
            val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
            "$scheme://$host$port$path$query$fragment"
        }.getOrDefault(value)
    }

    private fun filteredQuery(host: String, rawQuery: String?): String? {
        if (rawQuery.isNullOrBlank()) return null
        val genericTracking = setOf("fbclid", "gclid", "dclid", "mc_cid", "mc_eid")
        val youtubeTracking = setOf("si", "feature")
        val parts = rawQuery.split('&').filter { part ->
            val key = part.substringBefore('=').lowercase()
            when {
                key.startsWith("utm_") -> false
                key in genericTracking -> false
                (host == "youtu.be" || host.endsWith("youtube.com")) && key in youtubeTracking -> false
                else -> true
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("&")
    }

    private fun trimUnbalancedClosers(input: String): String {
        var value = input
        val pairs = listOf('(' to ')', '[' to ']', '{' to '}')
        var changed: Boolean
        do {
            changed = false
            for ((open, close) in pairs) {
                if (value.endsWith(close) && value.count { it == close } > value.count { it == open }) {
                    value = value.dropLast(1)
                    changed = true
                }
            }
        } while (changed)
        return value
    }

    private fun looksLikeHost(value: String): Boolean =
        Regex("^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}([/:?#].*)?$").matches(value)
}
