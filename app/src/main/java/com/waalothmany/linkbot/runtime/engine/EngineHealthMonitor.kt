package com.waalothmany.linkbot.runtime.engine

enum class HealthState { HEALTHY, DEGRADED, UNHEALTHY, UNAVAILABLE }

data class EngineHealth(
    val state: HealthState = HealthState.HEALTHY,
    val successCount: Long = 0,
    val failureCount: Long = 0,
    val consecutiveFailures: Int = 0,
    val latencyEwmaMs: Long? = null,
    val lastSuccessAtMs: Long? = null,
    val lastFailureAtMs: Long? = null,
    val lastFailure: ExecutionFailureClass? = null,
)

/**
 * Process-local adaptive health memory. It does not decide capability support;
 * it only biases routing between engines that are currently eligible.
 */
class EngineHealthMonitor {
    private val health = EngineId.entries.associateWith { EngineHealth() }.toMutableMap()

    @Synchronized
    fun snapshot(engine: EngineId): EngineHealth = health.getValue(engine)

    @Synchronized
    fun score(engine: EngineId, operation: SystemOperation): Int {
        val h = health.getValue(engine)
        val affinity = affinity(engine, operation)
        val failurePenalty = (h.consecutiveFailures * 18).coerceAtMost(54)
        val latencyPenalty = when (h.latencyEwmaMs) {
            null -> 0
            in 0..250 -> 0
            in 251..750 -> 4
            in 751..1_500 -> 8
            else -> 14
        }
        val statePenalty = when (h.state) {
            HealthState.HEALTHY -> 0
            HealthState.DEGRADED -> 15
            HealthState.UNHEALTHY -> 40
            HealthState.UNAVAILABLE -> 100
        }
        return (70 + affinity - failurePenalty - latencyPenalty - statePenalty).coerceIn(0, 100)
    }

    @Synchronized
    fun recordSuccess(engine: EngineId, durationMs: Long, nowMs: Long) {
        val previous = health.getValue(engine)
        val nextEwma = previous.latencyEwmaMs?.let { ((it * 3L) + durationMs.coerceAtLeast(0L)) / 4L }
            ?: durationMs.coerceAtLeast(0L)
        health[engine] = previous.copy(
            state = HealthState.HEALTHY,
            successCount = previous.successCount + 1,
            consecutiveFailures = 0,
            latencyEwmaMs = nextEwma,
            lastSuccessAtMs = nowMs,
            lastFailure = null,
        )
    }

    @Synchronized
    fun recordFailure(engine: EngineId, failure: ExecutionFailureClass, nowMs: Long) {
        val previous = health.getValue(engine)
        val consecutive = previous.consecutiveFailures + 1
        val state = when {
            failure == ExecutionFailureClass.PERMISSION_DENIED -> HealthState.UNAVAILABLE
            failure == ExecutionFailureClass.PLATFORM_RESTRICTED -> HealthState.UNAVAILABLE
            consecutive >= 3 -> HealthState.UNHEALTHY
            else -> HealthState.DEGRADED
        }
        health[engine] = previous.copy(
            state = state,
            failureCount = previous.failureCount + 1,
            consecutiveFailures = consecutive,
            lastFailureAtMs = nowMs,
            lastFailure = failure,
        )
    }

    @Synchronized
    fun markUnavailable(engine: EngineId) {
        health[engine] = health.getValue(engine).copy(state = HealthState.UNAVAILABLE)
    }

    @Synchronized
    fun reset(engine: EngineId) {
        health[engine] = EngineHealth()
    }

    private fun affinity(engine: EngineId, operation: SystemOperation): Int = when (operation) {
        SystemOperation.DISCOVER_USERS,
        SystemOperation.DISCOVER_PACKAGES,
        SystemOperation.FORCE_STOP_INSTANCE -> when (engine) {
            EngineId.SHIZUKU -> 25
            EngineId.ROOT -> 15
            else -> 0
        }
        SystemOperation.LAUNCH_INSTANCE,
        SystemOperation.RECOVER_INSTANCE -> when (engine) {
            EngineId.SHIZUKU -> 20
            EngineId.ROOT -> 10
            EngineId.STANDARD_ANDROID -> 8
            EngineId.ACCESSIBILITY -> 0
        }
        SystemOperation.READ_UI,
        SystemOperation.UI_GESTURE,
        SystemOperation.VERIFY_UI -> if (engine == EngineId.ACCESSIBILITY) 25 else 0
        SystemOperation.READ_FOREGROUND,
        SystemOperation.RESOLVE_INSTANCE -> when (engine) {
            EngineId.SHIZUKU -> 15
            EngineId.ROOT -> 8
            EngineId.STANDARD_ANDROID -> 6
            EngineId.ACCESSIBILITY -> 0
        }
    }
}
