package com.waalothmany.linkbot.runtime

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object DiagnosticLog {
    private const val MAX_BYTES = 512 * 1024L
    private const val ROTATE_AT_BYTES = 256 * 1024L
    private const val QUEUE_CAPACITY = 256
    private val lock = Any()
    @Volatile private var logFile: File? = null

    private val executor = ThreadPoolExecutor(
        1,
        1,
        30L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(QUEUE_CAPACITY),
        { runnable -> Thread(runnable, "wa-diagnostic-log").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    )

    fun init(context: Context) {
        synchronized(lock) {
            val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
            logFile = File(dir, "runtime.log")
        }
    }

    fun record(code: String, fields: Map<String, Any?> = emptyMap()) {
        val target = logFile ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
        val body = fields.entries.joinToString(" ") { (key, value) ->
            "$key=${DiagnosticSanitizer.sanitize(value?.toString().orEmpty())}"
        }
        val line = "$timestamp ${DiagnosticSanitizer.sanitize(code, 64)}${if (body.isBlank()) "" else " $body"}\n"
        executor.execute {
            synchronized(lock) {
                rotateIfNeeded(target)
                target.appendText(line)
            }
        }
    }

    fun flushPending(timeoutMs: Long = 2_000L) {
        val latch = CountDownLatch(1)
        runCatching {
            executor.execute { latch.countDown() }
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        }
    }

    fun exportText(): String {
        flushPending()
        return synchronized(lock) {
            val current = logFile
            if (current == null) "Diagnostics not initialized\n"
            else buildString {
                val previous = File(current.parentFile, "runtime.prev.log")
                if (previous.exists()) append(previous.readText())
                if (current.exists()) append(current.readText())
            }
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
