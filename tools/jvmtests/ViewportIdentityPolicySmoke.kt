package com.waalothmany.linkbot.automation

fun main() {
    val previous = listOf(
        ViewportIdentity("same", "g1"),
        ViewportIdentity("same", "g2"),
        ViewportIdentity("other", "g3"),
    )
    check(ViewportIdentityPolicy.reuseIds(listOf("same", "same", "other"), previous) == listOf("g1", "g2", "g3"))
    check(ViewportIdentityPolicy.reuseIds(listOf("same", "new"), previous) == listOf("g1", null))
    check(ViewportIdentityPolicy.reuseIds(emptyList(), previous).isEmpty())
    println("ViewportIdentityPolicySmoke: PASS")
}
