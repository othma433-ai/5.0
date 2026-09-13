package com.waalothmany.linkbot.core.importer

object ExportChatParser {
    private val bracketPattern = Regex("^\\[([^]]+)]\\s*(.*)$")
    private val dashPattern = Regex("^(.+?)\\s+-\\s+(.*)$")

    fun parse(content: String): List<ParsedMessage> {
        val normalized = normalizeDigits(content.replace("\r\n", "\n"))
        val out = ArrayList<ParsedMessage>()
        var current: MutableMessage? = null

        fun flush() {
            val item = current ?: return
            if (item.text.isNotBlank()) {
                out += ParsedMessage(item.timestamp, item.sender, item.text.trimEnd())
            }
            current = null
        }

        for (line in normalized.lineSequence()) {
            val parsed = parseStart(line)
            if (parsed != null) {
                flush()
                current = MutableMessage(parsed.first, parsed.second, parsed.third)
            } else if (current != null) {
                current!!.text += "\n$line"
            } else if (line.isNotBlank()) {
                current = MutableMessage(null, null, line)
            }
        }
        flush()
        return out
    }

    /**
     * WhatsApp exports can contain normal user rows (`timestamp - Sender: text`)
     * and system rows (`timestamp - system event`) without a sender. Treat both
     * as message boundaries so system events never get appended to the previous
     * sender's message.
     */
    private fun parseStart(line: String): Triple<String?, String?, String>? {
        val bracket = bracketPattern.matchEntire(line)
        if (bracket != null && looksLikeTimestamp(bracket.groupValues[1])) {
            val timestamp = bracket.groupValues[1].trim()
            val payload = bracket.groupValues[2]
            return parsePayload(timestamp, payload)
        }

        val dash = dashPattern.matchEntire(line)
        if (dash != null && looksLikeTimestamp(dash.groupValues[1])) {
            val timestamp = dash.groupValues[1].trim()
            val payload = dash.groupValues[2]
            return parsePayload(timestamp, payload)
        }
        return null
    }

    private fun parsePayload(timestamp: String, payload: String): Triple<String?, String?, String> {
        val separator = payload.indexOf(": ")
        if (separator > 0) {
            val sender = payload.substring(0, separator).trim().takeIf(String::isNotBlank)
            val text = payload.substring(separator + 2)
            return Triple(timestamp, sender, text)
        }
        return Triple(timestamp, null, payload)
    }

    private fun looksLikeTimestamp(value: String): Boolean {
        val v = value.trim()
        return Regex(".*\\d{1,4}[./-]\\d{1,2}[./-]\\d{1,4}.*\\d{1,2}:\\d{2}.*").matches(v)
    }

    private fun normalizeDigits(value: String): String {
        val ar = "٠١٢٣٤٥٦٧٨٩"
        val fa = "۰۱۲۳۴۵۶۷۸۹"
        return buildString(value.length) {
            for (c in value) {
                val ai = ar.indexOf(c)
                val fi = fa.indexOf(c)
                append(
                    when {
                        ai >= 0 -> ('0'.code + ai).toChar()
                        fi >= 0 -> ('0'.code + fi).toChar()
                        else -> c
                    }
                )
            }
        }
    }

    private data class MutableMessage(
        val timestamp: String?,
        val sender: String?,
        var text: String,
    )
}
