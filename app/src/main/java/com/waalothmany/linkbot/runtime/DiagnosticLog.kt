package com.waalothmany.linkbot.runtime

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Bounded asynchronous diagnostic logger. Accessibility callbacks only enqueue a
 * small immutable record; all formatting, rotation, and file I/O happen on the
 * dedicated daemon writer thread.
 */
object DiagnosticLog {
    private const val MAX_BYTES = 512 * 1024L
    private const val ROTATE_KEEP_BYTES = 256 * 1024L
    private const val QUEUE_CAPACITY = 1024
    private const val BATCH_SIZE = 64

    private sealed interface Command {
        data class Record(
            val timestampMs: Long,
            val code: String,
            val fields: List<Pair<String, String>>,
        ) : Command

        data class Flush(val latch: CountDownLatch) : Command
    }

    private val fileLock = Any()
    private val queue = ArrayBlockingQueue<Command>(QUEUE_CAPACITY)
    private val metrics = DiagnosticQueueMetrics()
    @Volatile private var logFile: File? = null
    @Volatile private var writerThread: Thread? = null

    fun init(context: Context) {
        synchronized(fileLock) {
            val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
            logFile = File(dir, "runtime.log")
            ensureWriterStarted()
        }
    }

    fun record(code: String, fields: Map<String, Any?> = emptyMap()) {
        val command = Command.Record(
            timestampMs = System.currentTimeMillis(),
            code = code,
            fields = fields.entries.map { (key, value) -> key to value?.toString().orEmpty() },
        )
        val accepted = queue.offer(command)
        metrics.recordOffer(accepted)
    }

    fun exportText(): String {
        flushQueuedRecords()
        return synchronized(fileLock) {
            val current = logFile
            buildString {
                append("# diagnostics dropped=${metrics.droppedCount()} queued=${queue.size}\n")
                if (current == null) {
                    append("Diagnostics not initialized\n")
                    return@buildString
                }
                val previous = File(current.parentFile, "runtime.prev.log")
                if (previous.exists()) append(previous.readText())
                if (current.exists()) append(current.readText())
            }
        }
    }

    fun droppedCount(): Long = metrics.droppedCount()

    private fun ensureWriterStarted() {
        val existing = writerThread
        if (existing?.isAlive == true) return
        writerThread = Thread(::writerLoop, "wa-diagnostics-writer").apply {
            isDaemon = true
            start()
        }
    }

    private fun writerLoop() {
        while (!Thread.currentThread().isInterrupted) {
            try {
                val first = queue.take()
                val batch = ArrayList<Command>(BATCH_SIZE).apply { add(first) }
                queue.drainTo(batch, BATCH_SIZE - 1)
                processBatch(batch)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Throwable) {
                // Diagnostics must never crash the app. Future records remain usable.
            }
        }
    }

    private fun processBatch(batch: List<Command>) {
        val lines = StringBuilder()
        fun flushLines() {
            if (lines.isEmpty()) return
            writeLines(lines.toString())
            lines.setLength(0)
        }

        for (command in batch) {
            when (command) {
                is Command.Record -> lines.append(format(command))
                is Command.Flush -> {
                    flushLines()
                    command.latch.countDown()
                }
            }
        }
        flushLines()
    }

    private fun format(record: Command.Record): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
            .format(Date(record.timestampMs))
        val body = record.fields.joinToString(" ") { (key, value) ->
            "${DiagnosticSanitizer.sanitize(key, 64)}=${DiagnosticSanitizer.sanitize(value)}"
        }
        val safeCode = DiagnosticSanitizer.sanitize(record.code, 64)
        return "$timestamp $safeCode${if (body.isBlank()) "" else " $body"}\n"
    }

    private fun writeLines(text: String) {
        synchronized(fileLock) {
            val target = logFile ?: return
            rotateIfNeeded(target)
            target.appendText(text)
        }
    }

    private fun flushQueuedRecords() {
        ensureWriterStarted()
        val latch = CountDownLatch(1)
        if (queue.offer(Command.Flush(latch))) {
            latch.await(2, TimeUnit.SECONDS)
        }
    }

    private fun rotateIfNeeded(target: File) {
        if (!target.exists() || !DiagnosticQueuePolicy.shouldRotate(target.length(), MAX_BYTES)) return
        val previous = File(target.parentFile, "runtime.prev.log")
        if (previous.exists()) previous.delete()
        val bytes = target.readBytes()
        val keep = bytes.takeLast(ROTATE_KEEP_BYTES.toInt()).toByteArray()
        previous.writeBytes(keep)
        target.writeText("")
    }
}
