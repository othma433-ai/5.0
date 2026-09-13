package com.waalothmany.linkbot.capability

fun main() {
    check(!AccessibilityConnectionPolicy.isOperational(enabledInSettings = true, serviceConnected = false))
    check(!AccessibilityConnectionPolicy.isOperational(enabledInSettings = false, serviceConnected = true))
    check(AccessibilityConnectionPolicy.isOperational(enabledInSettings = true, serviceConnected = true))
    check(!AccessibilityConnectionPolicy.isOperational(enabledInSettings = false, serviceConnected = false))
    println("AccessibilityConnectionPolicySmoke: PASS")
}
