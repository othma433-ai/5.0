package com.waalothmany.linkbot.automation

fun main() {
    check(ChildTraversalPolicy.pushOrder(0).toList().isEmpty())
    check(ChildTraversalPolicy.pushOrder(1).toList() == listOf(0))
    check(ChildTraversalPolicy.pushOrder(4).toList() == listOf(3, 2, 1, 0))
    println("ChildTraversalPolicySmoke: PASS")
}
