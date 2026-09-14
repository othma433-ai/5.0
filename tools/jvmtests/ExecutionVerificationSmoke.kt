package com.waalothmany.linkbot.runtime.engine

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

private class VerificationFakeEngine(
    override val id: EngineId,
    private val caps: Set<EngineCapability>,
) : ExecutionEngine {
    override fun capabilities(): Set<EngineCapability> = caps
    override suspend fun probe() = EngineProbeResult(id, ProbeState.READY, caps)
    override suspend fun execute(request: ExecutionRequest, context: ExecutionContext) =
        EngineExecutionResult(id, success = true)
}

private fun <T> runVerificationImmediate(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) { outcome = result }
    })
    return outcome!!.getOrThrow()
}

fun main() {
    val shizuku = VerificationFakeEngine(EngineId.SHIZUKU, setOf(EngineCapability.LAUNCH_PACKAGE_FOR_USER))
    val standard = VerificationFakeEngine(EngineId.STANDARD_ANDROID, setOf(EngineCapability.LAUNCH_PACKAGE_CURRENT_USER))
    val verifier = object : ExecutionPostconditionVerifier {
        override suspend fun verify(request: ExecutionRequest, result: EngineExecutionResult): Boolean =
            result.engine == EngineId.STANDARD_ANDROID
    }
    val orchestrator = ExecutionOrchestrator(
        engines = listOf(shizuku, standard),
        verifier = verifier,
        clockMs = { 10L },
        traceIdFactory = { "verify-trace" },
    )
    val request = ExecutionRequest(
        SystemOperation.LAUNCH_INSTANCE,
        InstanceTarget("com.whatsapp", 0, ProfileType.PERSONAL),
        VerificationPolicy.FOREGROUND_PACKAGE,
    )
    val result = runVerificationImmediate { orchestrator.execute(request) }
    check(result.success)
    check(result.engine == EngineId.STANDARD_ANDROID)
    check(result.attempts.size == 2)
    check(result.attempts.first().failure == ExecutionFailureClass.VERIFICATION_FAILED)
    println("ExecutionVerificationSmoke: PASS")
}
