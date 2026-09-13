package com.waalothmany.linkbot.automation

/**
 * Acceptance policy for WhatsApp navigation/filter controls when the semantic
 * label is visible but WhatsApp exposes only generic clickable containers.
 *
 * Strict structural evidence always wins. Otherwise the label must sit on a
 * clickable path inside a cluster that also exposes at least two sibling
 * controls (for example All / Unread / Favorites next to Groups).
 */
object ControlClusterPolicy {
    fun accept(
        labelMatched: Boolean,
        strictStructuralControl: Boolean,
        clickablePath: Boolean,
        peerLabelsFound: Int,
    ): Boolean {
        if (!labelMatched) return false
        if (strictStructuralControl) return true
        return clickablePath && peerLabelsFound >= 2
    }
}
