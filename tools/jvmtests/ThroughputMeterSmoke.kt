package com.waalothmany.linkbot.runtime

fun main() {
    val meter = ThroughputMeter(startedAtMs = 1_000)
    meter.addGroups(30, atMs = 31_000)
    meter.addLinks(120, atMs = 31_000)
    val snap = meter.snapshot(nowMs = 61_000)
    check(snap.groupsPerMinute in 29.0..31.0) { snap.toString() }
    check(snap.linksPerMinute in 119.0..121.0) { snap.toString() }
    meter.reset(100_000)
    val reset = meter.snapshot(100_500)
    check(reset.groupsPerMinute == 0.0)
    check(reset.linksPerMinute == 0.0)
    println("ThroughputMeterSmoke: PASS")
}
