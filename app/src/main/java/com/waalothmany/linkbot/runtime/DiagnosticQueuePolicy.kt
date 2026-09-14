package com.waalothmany.linkbot.runtime

import java.util.concurrent.atomic.AtomicLong

object DiagnosticQueuePolicy {
    fun validCapacity(capacity: Int): Boolean = capacity > 0
    fun shouldRotate(fileBytes: Long, maxBytes: Long): Boolean = fileBytes > maxBytes
}

class DiagnosticQueueMetrics {
    private val accepted = AtomicLong(0)
    private val dropped = AtomicLong(0)

    fun recordOffer(accepted: Boolean) {
        if (accepted) this.accepted.incrementAndGet() else dropped.incrementAndGet()
    }

    fun acceptedCount(): Long = accepted.get()
    fun droppedCount(): Long = dropped.get()
}
