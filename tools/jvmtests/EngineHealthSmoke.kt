package com.waalothmany.linkbot.runtime.engine

fun main() {
    val health = EngineHealthMonitor()
    val base = health.score(EngineId.SHIZUKU, SystemOperation.LAUNCH_INSTANCE)
    health.recordFailure(EngineId.SHIZUKU, ExecutionFailureClass.TIMEOUT, nowMs = 100L)
    val degraded = health.score(EngineId.SHIZUKU, SystemOperation.LAUNCH_INSTANCE)
    check(degraded < base)
    health.recordSuccess(EngineId.SHIZUKU, durationMs = 40L, nowMs = 200L)
    check(health.snapshot(EngineId.SHIZUKU).consecutiveFailures == 0)

    val cb = EngineCircuitBreaker(maxFailures = 2, openMs = 1_000)
    check(cb.allow(100L))
    cb.recordFailure(100L)
    cb.recordFailure(101L)
    check(!cb.allow(500L))
    check(cb.allow(1_102L))
    cb.recordSuccess()
    check(cb.allow(1_103L))
    println("EngineHealthSmoke: PASS")
}
