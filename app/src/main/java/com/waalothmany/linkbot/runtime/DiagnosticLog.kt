package com.waalothmany.linkbot.runtime

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticLog {
    private const val MAX_BYTES = 512 * 1024L
    private const val ROTATE_AT_BYTES = 256 * 1024L
    private val lock = Any()
    @Volatile private var logFile: File? = null

    fun init(context: Context) {
        synchronized(lock) {
            val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
            logFile = File(dir, "runtime.log")
        }
    }

    fun record(code: String, fields: Map<String, Any?> = emptyMap()) {
        val target = logFile ?: return
        synchronized(lock) {
            rotateIfNeeded(target)
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
            val body = fields.entries.joinToString(" ") { (key, value) ->
                "$key=${DiagnosticSanitizer.sanitize(value?.toString().orEmpty())}"
            }
            target.appendText("$timestamp ${DiagnosticSanitizer.sanitize(code, 64)}${if (body.isBlank()) "" else " $body"}\n")
        }
    }

    fun exportText(): String = synchronized(lock) {
        val current = logFile
        if (current == null) "Diagnostics not initialized\n"
        else buildString {
            val previous = File(current.parentFile, "runtime.prev.log")
            if (previous.exists()) append(previous.readText())
            if (current.exists()) append(current.readText())
        }
    }

    private fun rotateIfNeeded(target: File) {
        if (!target.exists() || target.length() <= MAX_BYTES) return
        val previous = File(target.parentFile, "runtime.prev.log")
        if (previous.exists()) previous.delete()
        val bytes = target.readBytes()
        val keep = bytes.takeLast(ROTATE_AT_BYTES.toInt()).toByteArray()
        previous.writeBytes(keep)
        target.writeText("")
    }
}
