package com.waalothmany.linkbot.automation

enum class AccessibilityEventClass {
    WINDOW_STATE,
    WINDOW_CONTENT_CHANGED,
    OTHER,
}

/** Bounded event-pressure guard; never suppresses window state transitions. */
class AccessibilityEventCoalescer(private val contentWindowMs: Long = 120L) {
    init { require(contentWindowMs >= 0L) }

    private var lastContentAcceptedAtMs: Long? = null
    private var lastContentGeneration: Long? = null
    private var lastContentWindowId: Int? = null

    @Synchronized
    fun shouldProcess(
        eventClass: AccessibilityEventClass,
        nowMs: Long,
        operationGeneration: Long?,
        windowId: Int?,
    ): Boolean = when (eventClass) {
        AccessibilityEventClass.WINDOW_STATE -> {
            resetContentState()
            true
        }
        AccessibilityEventClass.OTHER -> true
        AccessibilityEventClass.WINDOW_CONTENT_CHANGED -> {
            val previousAt = lastContentAcceptedAtMs
            val sameContext = lastContentGeneration == operationGeneration &&
                lastContentWindowId == windowId
            val withinWindow = previousAt != null && nowMs - previousAt < contentWindowMs
            if (sameContext && withinWindow) {
                false
            } else {
                lastContentAcceptedAtMs = nowMs
                lastContentGeneration = operationGeneration
                lastContentWindowId = windowId
                true
            }
        }
    }

    @Synchronized
    fun reset() = resetContentState()

    private fun resetContentState() {
        lastContentAcceptedAtMs = null
        lastContentGeneration = null
        lastContentWindowId = null
    }
}
