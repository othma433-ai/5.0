package com.waalothmany.linkbot.capability

fun main() {
    check(RuntimeCapabilityResolver.resolve(shizukuUsable = false, rootUsable = false) == RuntimeCapabilityMode.STANDARD)
    check(RuntimeCapabilityResolver.resolve(shizukuUsable = true, rootUsable = false) == RuntimeCapabilityMode.SHIZUKU_ENHANCED)
    check(RuntimeCapabilityResolver.resolve(shizukuUsable = false, rootUsable = true) == RuntimeCapabilityMode.ROOT_ENHANCED)
    check(RuntimeCapabilityResolver.resolve(shizukuUsable = true, rootUsable = true) == RuntimeCapabilityMode.SHIZUKU_ENHANCED) {
        "Shizuku is preferred when both enhanced backends are usable"
    }
    check(RuntimeCapabilityResolver.resolve(shizukuUsable = false, rootUsable = false, shizukuInstalled = true, rootDetected = true) == RuntimeCapabilityMode.STANDARD) {
        "installation/detection evidence alone must never promote runtime mode"
    }
    println("RuntimeCapabilityModeSmoke: PASS")
}
