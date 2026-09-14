package com.waalothmany.linkbot.runtime.engine.accessibility

import com.waalothmany.linkbot.runtime.engine.EngineCapability
import com.waalothmany.linkbot.runtime.engine.EngineExecutionResult
import com.waalothmany.linkbot.runtime.engine.EngineId
import com.waalothmany.linkbot.runtime.engine.EngineProbeResult
import com.waalothmany.linkbot.runtime.engine.ExecutionContext
import com.waalothmany.linkbot.runtime.engine.ExecutionEngine
import com.waalothmany.linkbot.runtime.engine.ExecutionFailureClass
import com.waalothmany.linkbot.runtime.engine.ExecutionRequest
import com.waalothmany.linkbot.runtime.engine.ProbeState
import com.waalothmany.linkbot.runtime.engine.SystemOperation

/**
 * Capability adapter around Android's Accessibility runtime. Existing WhatsApp
 * navigation remains in WaAccessibilityService; this engine exposes only the
 * readiness/verification operations that can be represented safely today.
 */
class AccessibilityEngine : ExecutionEngine {
    override val id: EngineId = EngineId.ACCESSIBILITY

    override fun capabilities(): Set<EngineCapability> {
        val snapshot = AccessibilityRuntimeSupervisor.state.value
        if (!snapshot.connected) return emptySet()
        return buildSet {
            add(EngineCapability.ACCESSIBILITY_TREE)
            add(EngineCapability.VERIFY_SCREEN)
            if (snapshot.windowReady) {
                add(EngineCapability.ACCESSIBILITY_GESTURE)
                add(EngineCapability.ACCESSIBILITY_GLOBAL_ACTION)
            }
        }
    }

    override suspend fun probe(): EngineProbeResult {
        val snapshot = AccessibilityRuntimeSupervisor.state.value
        val state = when {
            !snapshot.settingsEnabled -> ProbeState.UNAVAILABLE
            !snapshot.binderConnected -> ProbeState.DEGRADED
            snapshot.windowReady -> ProbeState.READY
            else -> ProbeState.DEGRADED
        }
        return EngineProbeResult(
            engine = id,
            state = state,
            capabilities = capabilities(),
            detail = snapshot.state.name,
        )
    }

    override suspend fun execute(
        request: ExecutionRequest,
        context: ExecutionContext,
    ): EngineExecutionResult {
        val snapshot = AccessibilityRuntimeSupervisor.state.value
        return when (request.operation) {
            SystemOperation.READ_UI,
            SystemOperation.VERIFY_UI -> EngineExecutionResult(
                engine = id,
                success = snapshot.windowReady,
                detail = "state=${snapshot.state.name} trace=${context.traceId}",
                failure = if (snapshot.windowReady) null else ExecutionFailureClass.VERIFICATION_FAILED,
            )
            else -> EngineExecutionResult(
                engine = id,
                success = false,
                failure = ExecutionFailureClass.UNSUPPORTED_OPERATION,
            )
        }
    }
}
