package com.waalothmany.linkbot.runtime.engine

/**
 * Converts an execution request into an ordered list of eligible engines.
 * Eligibility is capability-driven; ordering is health/operation affinity-driven.
 */
class OperationPlanner(
    private val health: EngineHealthMonitor,
    private val circuits: Map<EngineId, EngineCircuitBreaker>,
) {
    fun candidates(
        request: ExecutionRequest,
        engines: List<ExecutionEngine>,
        probes: Map<EngineId, EngineProbeResult>,
        nowMs: Long,
    ): List<ExecutionEngine> = engines
        .asSequence()
        .filter { engine ->
            val probe = probes[engine.id] ?: return@filter false
            probe.usable && supports(request, probe.capabilities)
        }
        .filter { engine -> circuits[engine.id]?.allow(nowMs) != false }
        .sortedWith(
            compareByDescending<ExecutionEngine> { health.score(it.id, request.operation) }
                .thenBy { it.id.ordinal }
        )
        .toList()

    private fun supports(request: ExecutionRequest, capabilities: Set<EngineCapability>): Boolean {
        if (request.operation.preferredCapabilities.none { it in capabilities }) return false

        // A current-profile engine must never be treated as cross-user capable.
        if (request.operation == SystemOperation.LAUNCH_INSTANCE || request.operation == SystemOperation.RECOVER_INSTANCE) {
            val target = request.target
            if (target != null && target.androidUserId != 0 &&
                EngineCapability.LAUNCH_PACKAGE_FOR_USER !in capabilities
            ) return false
        }
        return true
    }
}
