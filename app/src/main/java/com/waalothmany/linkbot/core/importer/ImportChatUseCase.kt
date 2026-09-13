package com.waalothmany.linkbot.core.importer

import com.waalothmany.linkbot.ServiceLocator
import com.waalothmany.linkbot.core.link.LinkExtractor
import com.waalothmany.linkbot.data.GroupEntity
import com.waalothmany.linkbot.data.LinkRecordRequest
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object ImportChatUseCase {
    suspend fun importBytes(fileName: String, bytes: ByteArray): ImportResult {
        val text = if (fileName.endsWith(".zip", true)) readZipText(bytes) else bytes.toString(Charsets.UTF_8)
        require(text.isNotBlank()) { "No chat text found" }
        val messages = ExportChatParser.parse(text)
        val title = fileName.substringBeforeLast('.').ifBlank { "Imported chat" }
        val groupId = hash("import|$title")
        ServiceLocator.groups.upsert(listOf(
            GroupEntity(
                id = groupId,
                instanceId = "import",
                displayTitle = title,
                normalizedTitle = title.lowercase(),
                rowFingerprint = "import",
                extractionState = "IMPORTED",
            )
        ))
        var occurrences = 0
        val unique = linkedSetOf<String>()
        val batch = ArrayList<LinkRecordRequest>(512)
        suspend fun flush() {
            if (batch.isEmpty()) return
            ServiceLocator.links.recordBatch(batch.toList())
            batch.clear()
        }
        for (message in messages) {
            val fingerprint = "${message.timestampRaw}|${message.sender.orEmpty()}|${message.text}"
            for (candidate in LinkExtractor.extract(message.text)) {
                batch += LinkRecordRequest(
                    candidate = candidate,
                    groupId = groupId,
                    sessionId = null,
                    source = "EXPORT_CHAT",
                    sender = message.sender,
                    timestampRaw = message.timestampRaw,
                    messageText = null,
                    messageFingerprintSource = fingerprint,
                )
                occurrences++
                unique += candidate.canonicalUrl
                if (batch.size >= 500) flush()
            }
        }
        flush()
        return ImportResult(messages.size, unique.size, occurrences)
    }

    private fun readZipText(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".txt", true)) {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        return ""
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

data class ImportResult(val messages: Int, val uniqueLinks: Int, val occurrences: Int)
