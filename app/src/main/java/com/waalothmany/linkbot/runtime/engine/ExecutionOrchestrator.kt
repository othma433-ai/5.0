package com.waalothmany.linkbot.runtime.engine

import com.waalothmany.linkbot.runtime.trace.TraceRecorder
import java.util.UUID

/**
 * Adaptive runtime coordinator. Each engine is responsible for bounding its own
 * platform call; this coordinator handles eligibility, health, circuit breaking,
 * fallback, and a stable trace id for the complete attempt sequence.
 */
class ExecutionOrchestrator(
    private val engines: List<ExecutionEngine>,
    private val health: EngineHealthMonitor = EngineHealthMonitor(),
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val traceIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val verifier: ExecutionPostconditionVerifier = SafeDefaultPostconditionVerifier,
    private val traceRecorder: TraceRecorder = TraceRecorder(),
    circuits: Map<EngineId, EngineCircuitBreaker>? = null,
) {
    private val circuitBreakers: Map<EngineId, EngineCircuitBreaker> = circuits
        ?: EngineId.entries.associateWith { EngineCircuitBreaker(maxFailures = 3, openMs = 30_000) }
    private val planner = OperationPlanner(health, circuitBreakers)

    suspend fun execute(request: ExecutionRequest): OrchestratedExecutionResult {
        val traceId = traceIdFactory()
        val traceStarted = clockMs()
        traceRecorder.record(
            traceId = traceId,
            step = "TRACE_START",
            startedAtMs = traceStarted,
            result = request.operation.name,
            metadata = mapOf("verification" to request.verification.name),
        )
        val probeMap = LinkedHashMap<EngineId, EngineProbeResult>()

        for (engine in engines.distinctBy { it.id }) {
            val probe = runCatching { engine.probe() }.getOrElse { error ->
                EngineProbeResult(
                    engine = engine.id,
                    state = ProbeState.ERROR,
                    capabilities = emptySet(),
                    detail = error.javaClass.simpleName,
                )
            }
            probeMap[engine.id] = probe
            if (!probe.usable && probe.state == ProbeState.UNAVAILABLE) {
                health.markUnavailable(engine.id)
            }
        }

        val candidates = planner.candidates(
            request = request,
            engines = engines.distinctBy { it.id },
            probes = probeMap,
            nowMs = clockMs(),
        )
        val attempts = mutableListOf<EngineExecutionResult>()

        for ((index, engine) in candidates.withIndex()) {
            val started = clockMs()
            traceRecorder.record(
                traceId = traceId,
                step = "ENGINE_SELECTED",
                engine = engine.id,
                attempt = index + 1,
                startedAtMs = started,
                result = engine.id.name,
            )
            val result = runCatching {
                engine.execute(
                    request = request,
                    context = ExecutionContext(traceId = traceId, attempt = index + 1),
                )
            }.getOrElse { error ->
                EngineExecutionResult(
                    engine = engine.id,
                    success = false,
                    detail = error.javaClass.simpleName,
                    failure = ExecutionFailureClass.UNKNOWN,
                )
            }
            val finished = clockMs()
            val duration = (finished - started).coerceAtLeast(0L)

            val verifiedResult = if (result.success && request.verification != VerificationPolicy.NONE) {
                val verified = runCatching { verifier.verify(request, result) }.getOrDefault(false)
                if (verified) result else result.copy(
                    success = false,
                    detail = listOfNotNull(result.detail, "postcondition=${request.verification.name}:failed").joinToString(" "),
                    failure = ExecutionFailureClass.VERIFICATION_FAILED,
                )
            } else {
                result
            }
            attempts += verifiedResult
            val nextEngine = candidates.getOrNull(index + 1)?.id
            traceRecorder.record(
                traceId = traceId,
                step = "ENGINE_EXECUTED",
                engine = engine.id,
                attempt = index + 1,
                startedAtMs = started,
                result = if (verifiedResult.success) "SUCCESS" else "FAILURE",
                failure = verifiedResult.failure,
                fallbackTo = if (verifiedResult.success) null else nextEngine,
                metadata = verifiedResult.metadata,
            )

            if (verifiedResult.success) {
                health.recordSuccess(engine.id, durationMs = duration, nowMs = finished)
                circuitBreakers[engine.id]?.recordSuccess()
                traceRecorder.record(
                    traceId = traceId,
                    step = "TRACE_FINISH",
                    engine = engine.id,
                    attempt = index + 1,
                    startedAtMs = traceStarted,
                    result = "SUCCESS",
                )
                return OrchestratedExecutionResult(
                    success = true,
                    engine = engine.id,
                    attempts = attempts.toList(),
                    traceId = traceId,
                )
            }

            if (nextEngine != null) {
                traceRecorder.record(
                    traceId = traceId,
                    step = "FALLBACK_SELECTED",
                    engine = engine.id,
                    attempt = index + 1,
                    startedAtMs = finished,
                    result = nextEngine.name,
                    failure = verifiedResult.failure,
                    fallbackTo = nextEngine,
                )
            }

            val failure = verifiedResult.failure ?: ExecutionFailureClass.UNKNOWN
            health.recordFailure(engine.id, failure, nowMs = finished)
            circuitBreakers[engine.id]?.recordFailure(finished)
        }

        val finalFailure = attempts.lastOrNull()?.failure ?: ExecutionFailureClass.UNSUPPORTED_OPERATION
        traceRecorder.record(
            traceId = traceId,
            step = "TRACE_FINISH",
            startedAtMs = traceStarted,
            result = "FAILURE",
            failure = finalFailure,
        )
        return OrchestratedExecutionResult(
            success = false,
            failure = finalFailure,
            attempts = attempts.toList(),
            traceId = traceId,
        )
    }

    fun healthSnapshot(engine: EngineId): EngineHealth = health.snapshot(engine)
}
