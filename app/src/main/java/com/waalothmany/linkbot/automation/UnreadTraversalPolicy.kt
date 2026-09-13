package com.waalothmany.linkbot.automation

enum class UnreadTraversalAction {
    SCROLL_BACKWARD,
    COMPLETE,
}

/**
 * A chat opened from search normally lands at the newest messages. To locate
 * WhatsApp's unread boundary safely, walk toward older messages until the
 * unread marker is observed. Once the marker is visible, the current viewport
 * policy keeps only rows after that marker and extraction can complete.
 */
object UnreadTraversalPolicy {
    fun next(markerPresent: Boolean): UnreadTraversalAction =
        if (markerPresent) UnreadTraversalAction.COMPLETE else UnreadTraversalAction.SCROLL_BACKWARD
}
