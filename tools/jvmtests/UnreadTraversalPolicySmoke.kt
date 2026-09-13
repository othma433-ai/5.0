package com.waalothmany.linkbot.automation

fun main() {
    check(UnreadTraversalPolicy.next(markerPresent = false) == UnreadTraversalAction.SCROLL_BACKWARD)
    check(UnreadTraversalPolicy.next(markerPresent = true) == UnreadTraversalAction.COMPLETE)
    println("UnreadTraversalPolicySmoke: PASS")
}
