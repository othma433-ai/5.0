package com.waalothmany.linkbot.runtime.engine

enum class EngineId { SHIZUKU, ROOT, STANDARD_ANDROID, ACCESSIBILITY }

enum class EngineCapability {
    ENUMERATE_USERS,
    ENUMERATE_PACKAGES_FOR_USER,
    RESOLVE_INSTANCE,
    LAUNCH_PACKAGE_CURRENT_USER,
    LAUNCH_PACKAGE_FOR_USER,
    FORCE_STOP_PACKAGE,
    BRING_TASK_TO_FRONT,
    READ_FOREGROUND_PACKAGE,
    ACCESSIBILITY_TREE,
    ACCESSIBILITY_GESTURE,
    ACCESSIBILITY_GLOBAL_ACTION,
    VERIFY_SCREEN,
    RECOVER_TO_HOME,
}

enum class ProfileType { PERSONAL, WORK, DUAL, SECURE, UNKNOWN }

data class InstanceTarget(
    val packageName: String,
    val androidUserId: Int,
    val profileType: ProfileType,
    val componentName: String? = null,
)

enum class VerificationPolicy {
    NONE,
    FOREGROUND_PACKAGE,
    ACCESSIBILITY_WINDOW,
}

enum class SystemOperation(val preferredCapabilities: Set<EngineCapability>) {
    DISCOVER_USERS(setOf(EngineCapability.ENUMERATE_USERS)),
    DISCOVER_PACKAGES(setOf(EngineCapability.ENUMERATE_PACKAGES_FOR_USER)),
    RESOLVE_INSTANCE(setOf(EngineCapability.RESOLVE_INSTANCE)),
    LAUNCH_INSTANCE(setOf(EngineCapability.LAUNCH_PACKAGE_FOR_USER, EngineCapability.LAUNCH_PACKAGE_CURRENT_USER)),
    RECOVER_INSTANCE(setOf(EngineCapability.BRING_TASK_TO_FRONT, EngineCapability.LAUNCH_PACKAGE_FOR_USER, EngineCapability.LAUNCH_PACKAGE_CURRENT_USER)),
    FORCE_STOP_INSTANCE(setOf(EngineCapability.FORCE_STOP_PACKAGE)),
    READ_FOREGROUND(setOf(EngineCapability.READ_FOREGROUND_PACKAGE)),
    READ_UI(setOf(EngineCapability.ACCESSIBILITY_TREE)),
    UI_GESTURE(setOf(EngineCapability.ACCESSIBILITY_GESTURE)),
    VERIFY_UI(setOf(EngineCapability.VERIFY_SCREEN)),
}

data class ExecutionRequest(
    val operation: SystemOperation,
    val target: InstanceTarget? = null,
    val verification: VerificationPolicy = VerificationPolicy.NONE,
    val timeoutMs: Long = 5_000,
)

data class ExecutionContext(
    val traceId: String,
    val attempt: Int,
)

enum class ProbeState { READY, DEGRADED, UNAVAILABLE, PERMISSION_REQUIRED, ERROR }

data class EngineProbeResult(
    val engine: EngineId,
    val state: ProbeState,
    val capabilities: Set<EngineCapability>,
    val detail: String? = null,
) {
    val usable: Boolean get() = state == ProbeState.READY || state == ProbeState.DEGRADED
}

enum class ExecutionFailureClass {
    TIMEOUT,
    PERMISSION_DENIED,
    BINDER_DEAD,
    PACKAGE_UNAVAILABLE,
    PROFILE_UNREACHABLE,
    VERIFICATION_FAILED,
    PLATFORM_RESTRICTED,
    CIRCUIT_OPEN,
    UNSUPPORTED_OPERATION,
    UNKNOWN,
}

data class EngineExecutionResult(
    val engine: EngineId,
    val success: Boolean,
    val detail: String? = null,
    val failure: ExecutionFailureClass? = null,
    val metadata: Map<String, String> = emptyMap(),
)

data class OrchestratedExecutionResult(
    val success: Boolean,
    val engine: EngineId? = null,
    val failure: ExecutionFailureClass? = null,
    val attempts: List<EngineExecutionResult> = emptyList(),
    val traceId: String,
)
