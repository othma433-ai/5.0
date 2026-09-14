package com.waalothmany.linkbot.runtime.engine.shizuku

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

class ShizukuEngine(
    private val gateway: ShizukuSystemGateway,
) : ExecutionEngine {
    override val id: EngineId = EngineId.SHIZUKU

    private val supported = setOf(
        EngineCapability.ENUMERATE_USERS,
        EngineCapability.ENUMERATE_PACKAGES_FOR_USER,
        EngineCapability.RESOLVE_INSTANCE,
        EngineCapability.LAUNCH_PACKAGE_FOR_USER,
        EngineCapability.FORCE_STOP_PACKAGE,
        EngineCapability.BRING_TASK_TO_FRONT,
    )

    override fun capabilities(): Set<EngineCapability> = supported

    override suspend fun probe(): EngineProbeResult {
        ShizukuRuntime.refresh()
        val snapshot = ShizukuRuntime.state.value
        if (!snapshot.binderAlive) {
            return EngineProbeResult(id, ProbeState.UNAVAILABLE, emptySet(), snapshot.state.name)
        }
        if (!snapshot.permissionGranted) {
            return EngineProbeResult(id, ProbeState.PERMISSION_REQUIRED, emptySet(), snapshot.state.name)
        }
        return runCatching {
            val uid = gateway.probeUid()
            EngineProbeResult(id, ProbeState.READY, supported, "uid=$uid")
        }.getOrElse { error ->
            ShizukuRuntime.markProbeFailure(error.javaClass.simpleName)
            EngineProbeResult(id, ProbeState.ERROR, emptySet(), error.javaClass.simpleName)
        }
    }

    override suspend fun execute(
        request: ExecutionRequest,
        context: ExecutionContext,
    ): EngineExecutionResult = runCatching {
        when (request.operation) {
            SystemOperation.DISCOVER_USERS -> EngineExecutionResult(
                id,
                true,
                metadata = mapOf("usersRaw" to gateway.listUsers(request.timeoutMs)),
            )
            SystemOperation.DISCOVER_PACKAGES -> {
                val user = request.target?.androidUserId
                    ?: return EngineExecutionResult(id, false, failure = ExecutionFailureClass.PROFILE_UNREACHABLE)
                EngineExecutionResult(
                    id,
                    true,
                    metadata = mapOf("packagesRaw" to gateway.listPackagesForUser(user, request.timeoutMs)),
                )
            }
            SystemOperation.RESOLVE_INSTANCE -> {
                val target = request.target
                    ?: return EngineExecutionResult(id, false, failure = ExecutionFailureClass.PROFILE_UNREACHABLE)
                val packages = gateway.listPackagesForUser(target.androidUserId, request.timeoutMs)
                val found = packages.lineSequence().any { it.trim() == "package:${target.packageName}" }
                EngineExecutionResult(
                    id,
                    found,
                    failure = if (found) null else ExecutionFailureClass.PACKAGE_UNAVAILABLE,
                )
            }
            SystemOperation.LAUNCH_INSTANCE,
            SystemOperation.RECOVER_INSTANCE -> {
                val target = request.target
                    ?: return EngineExecutionResult(id, false, failure = ExecutionFailureClass.PROFILE_UNREACHABLE)
                val exit = gateway.launchPackageForUser(target.androidUserId, target.packageName, request.timeoutMs)
                EngineExecutionResult(
                    id,
                    exit == 0,
                    detail = "exit=$exit trace=${context.traceId}",
                    failure = if (exit == 0) null else classifyExit(exit),
                )
            }
            SystemOperation.FORCE_STOP_INSTANCE -> {
                val target = request.target
                    ?: return EngineExecutionResult(id, false, failure = ExecutionFailureClass.PROFILE_UNREACHABLE)
                val exit = gateway.forceStopPackageForUser(target.androidUserId, target.packageName, request.timeoutMs)
                EngineExecutionResult(id, exit == 0, detail = "exit=$exit", failure = if (exit == 0) null else classifyExit(exit))
            }
            else -> EngineExecutionResult(id, false, failure = ExecutionFailureClass.UNSUPPORTED_OPERATION)
        }
    }.getOrElse { error ->
        EngineExecutionResult(
            engine = id,
            success = false,
            detail = error.javaClass.simpleName,
            failure = when (error) {
                is kotlinx.coroutines.TimeoutCancellationException -> ExecutionFailureClass.TIMEOUT
                is SecurityException -> ExecutionFailureClass.PERMISSION_DENIED
                else -> ExecutionFailureClass.BINDER_DEAD
            },
        )
    }

    private fun classifyExit(exit: Int): ExecutionFailureClass = when (exit) {
        124 -> ExecutionFailureClass.TIMEOUT
        64, 65 -> ExecutionFailureClass.PACKAGE_UNAVAILABLE
        else -> ExecutionFailureClass.PLATFORM_RESTRICTED
    }
}
