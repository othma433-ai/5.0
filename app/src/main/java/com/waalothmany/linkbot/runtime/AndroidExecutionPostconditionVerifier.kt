package com.waalothmany.linkbot.runtime

import com.waalothmany.linkbot.runtime.engine.EngineExecutionResult
import com.waalothmany.linkbot.runtime.engine.ExecutionPostconditionVerifier
import com.waalothmany.linkbot.runtime.engine.ExecutionRequest
import com.waalothmany.linkbot.runtime.engine.VerificationPolicy
import com.waalothmany.linkbot.runtime.engine.accessibility.AccessibilityRuntimeSupervisor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Verifies state-changing system actions using the live Accessibility channel. */
class AndroidExecutionPostconditionVerifier : ExecutionPostconditionVerifier {
    override suspend fun verify(
        request: ExecutionRequest,
        result: EngineExecutionResult,
    ): Boolean = when (request.verification) {
        VerificationPolicy.NONE -> true
        VerificationPolicy.FOREGROUND_PACKAGE -> {
            val targetPackage = request.target?.packageName ?: return false
            await(request.timeoutMs) { snapshot ->
                snapshot.connected && snapshot.lastEventPackageName == targetPackage
            }
        }
        VerificationPolicy.ACCESSIBILITY_WINDOW -> {
            val targetPackage = request.target?.packageName
            await(request.timeoutMs) { snapshot ->
                snapshot.windowReady && (targetPackage == null || snapshot.lastEventPackageName == targetPackage)
            }
        }
    }

    private suspend fun await(
        requestedTimeoutMs: Long,
        predicate: (com.waalothmany.linkbot.runtime.engine.accessibility.AccessibilityRuntimeSnapshot) -> Boolean,
    ): Boolean {
        val timeoutMs = requestedTimeoutMs.coerceIn(500L, 8_000L)
        return withTimeoutOrNull(timeoutMs) {
            AccessibilityRuntimeSupervisor.state.first(predicate)
            true
        } ?: false
    }
}
