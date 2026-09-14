package com.waalothmany.linkbot.automation

import java.util.ArrayDeque

enum class EventSignalKind { CONTENT, SCROLL, WINDOW_STATE, CLICK, OTHER }

data class AccessibilityEventSignal(
    val packageName: String,
    val eventType: Int,
    val kind: EventSignalKind,
)

class AccessibilityEventCoalescer(
    private val capacity: Int = 4,
) {
    init { require(capacity >= 1) }

    private val queue = ArrayDeque<AccessibilityEventSignal>(capacity)

    @Synchronized
    fun offer(signal: AccessibilityEventSignal) {
        if (signal.kind == EventSignalKind.CONTENT) {
            val retained = queue.filterNot {
                it.kind == EventSignalKind.CONTENT && it.packageName == signal.packageName
            }
            queue.clear()
            retained.forEach(queue::addLast)
        }
        while (queue.size >= capacity) {
            val contentIndex = queue.indexOfFirst { it.kind == EventSignalKind.CONTENT }
            if (contentIndex >= 0) {
                val rebuilt = queue.toMutableList().also { it.removeAt(contentIndex) }
                queue.clear()
                rebuilt.forEach(queue::addLast)
            } else {
                queue.removeFirst()
            }
        }
        queue.addLast(signal)
    }

    @Synchronized
    fun poll(): AccessibilityEventSignal? = if (queue.isEmpty()) null else queue.removeFirst()

    @Synchronized
    fun clear() = queue.clear()

    @Synchronized
    fun size(): Int = queue.size
}
