package com.waalothmany.linkbot.runtime.engine

import com.waalothmany.linkbot.runtime.trace.TraceRecorder
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

private class FakeEngine(
    override val id: EngineId,
    private val caps: Set<EngineCapability>,
    private val probeState: ProbeState = ProbeState.READY,
    private val result: EngineExecutionResult,
) : ExecutionEngine {
    var executions: Int = 0
    override fun capabilities(): Set<EngineCapability> = caps
    override suspend fun probe(): EngineProbeResult = EngineProbeResult(id, probeState, caps)
    override suspend fun execute(request: ExecutionRequest, context: ExecutionContext): EngineExecutionResult {
        executions += 1
        return result
    }
}

private fun <T> runImmediate(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) { outcome = result }
    })
    return outcome!!.getOrThrow()
}

fun main() {
    val shizuku = FakeEngine(
        EngineId.SHIZUKU,
        setOf(EngineCapability.LAUNCH_PACKAGE_FOR_USER),
        result = EngineExecutionResult(EngineId.SHIZUKU, false, failure = ExecutionFailureClass.BINDER_DEAD),
    )
    val standard = FakeEngine(
        EngineId.STANDARD_ANDROID,
        setOf(EngineCapability.LAUNCH_PACKAGE_CURRENT_USER),
        result = EngineExecutionResult(EngineId.STANDARD_ANDROID, true),
    )
    val accessibility = FakeEngine(
        EngineId.ACCESSIBILITY,
        setOf(EngineCapability.ACCESSIBILITY_TREE),
        result = EngineExecutionResult(EngineId.ACCESSIBILITY, true),
    )
    val health = EngineHealthMonitor()
    val traceSteps = mutableListOf<String>()
    val orchestrator = ExecutionOrchestrator(
        engines = listOf(accessibility, standard, shizuku),
        health = health,
        clockMs = { 100L },
        traceIdFactory = { "trace-test" },
        traceRecorder = TraceRecorder(clockMs = { 100L }) { event -> traceSteps += event.step + ":" + event.result },
    )
    val request = ExecutionRequest(
        operation = SystemOperation.LAUNCH_INSTANCE,
        target = InstanceTarget("com.whatsapp", 0, ProfileType.PERSONAL),
        verification = VerificationPolicy.NONE,
    )
    val result = runImmediate { orchestrator.execute(request) }
    check(result.success)
    check(result.engine == EngineId.STANDARD_ANDROID)
    check(result.attempts.size == 2)
    check(shizuku.executions == 1)
    check(standard.executions == 1)
    check(accessibility.executions == 0)
    check(health.snapshot(EngineId.SHIZUKU).consecutiveFailures == 1)
    check(traceSteps.any { it == "ENGINE_EXECUTED:FAILURE" })
    check(traceSteps.any { it == "FALLBACK_SELECTED:STANDARD_ANDROID" })
    check(traceSteps.last() == "TRACE_FINISH:SUCCESS")
    println("ExecutionOrchestratorSmoke: PASS")
}
