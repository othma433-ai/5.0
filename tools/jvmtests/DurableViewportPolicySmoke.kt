package com.waalothmany.linkbot.automation

fun main() {
    check(DurableViewportPolicy.canAdvance(ViewportPersistenceState.NO_CHANGES))
    check(!DurableViewportPolicy.canAdvance(ViewportPersistenceState.PERSISTING))
    check(DurableViewportPolicy.canAdvance(ViewportPersistenceState.COMMITTED))
    check(!DurableViewportPolicy.canAdvance(ViewportPersistenceState.FAILED))
    check(DurableViewportPolicy.mustPersist(discoveredLinks = 3))
    check(!DurableViewportPolicy.mustPersist(discoveredLinks = 0))
    println("DurableViewportPolicySmoke: PASS")
}
