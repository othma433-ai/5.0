package com.waalothmany.linkbot.automation

fun main() {
    val items = listOf(
        ViewportMessage("m1", false),
        ViewportMessage("m2", false),
        ViewportMessage(null, true),
        ViewportMessage("m3", false),
        ViewportMessage("m4", false),
    )

    val unread = MessageViewportPolicy.select(items, seen = setOf("m1"), unreadOnly = true)
    check(unread == listOf("m3", "m4")) { unread.toString() }

    val all = MessageViewportPolicy.select(items, seen = setOf("m1", "m3"), unreadOnly = false)
    check(all == listOf("m2", "m4")) { all.toString() }

    val noMarker = MessageViewportPolicy.select(
        listOf(ViewportMessage("n1", false), ViewportMessage("n2", false)),
        seen = setOf("n1"),
        unreadOnly = true,
    )
    check(noMarker == listOf("n2"))

    val bounded = MessageViewportPolicy.mergeSeen((1..1000).map { "x$it" }.toSet(), listOf("z1", "z2"), maxEntries = 256)
    check(bounded.size == 256)
    check("z1" in bounded && "z2" in bounded)
    println("MessageViewportPolicySmoke: PASS")
}
