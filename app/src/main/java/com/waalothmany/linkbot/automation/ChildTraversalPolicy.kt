package com.waalothmany.linkbot.automation

/**
 * Accessibility tree traversal uses a LIFO stack. Children therefore have to
 * be pushed in reverse index order so they are visited in their natural UI
 * order (0, 1, 2, ...). Keeping that order matters when resource ids are
 * unavailable and title/message extraction falls back to semantic text order.
 */
object ChildTraversalPolicy {
    fun pushOrder(childCount: Int): IntProgression = (childCount - 1) downTo 0
}
