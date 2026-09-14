package com.waalothmany.linkbot.runtime.engine.root

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

class RootEngine(
    private val gateway: RootCommandGateway,
    private val enabledProvider: () -> Boolean = { true },
) : ExecutionEngine {
    override val id: EngineId = EngineId.ROOT

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
        if (!enabledProvider()) return EngineProbeResult(id, ProbeState.UNAVAILABLE, emptySet(), "disabled")
        return try {
            val uid = gateway.probeUid()
            when (uid) {
                0 -> EngineProbeResult(id, ProbeState.READY, supported, "uid=0")
                null -> EngineProbeResult(id, ProbeState.UNAVAILABLE, emptySet(), "su unavailable or denied")
                else -> EngineProbeResult(id, ProbeState.UNAVAILABLE, emptySet(), "uid=$uid")
            }
        } catch (error: Throwable) {
            EngineProbeResult(id, ProbeState.UNAVAILABLE, emptySet(), error.javaClass.simpleName)
        }
    }

    override suspend fun execute(
        request: ExecutionRequest,
        context: ExecutionContext,
    ): EngineExecutionResult {
        if (!enabledProvider()) {
            return EngineExecutionResult(
                id,
                false,
                failure = ExecutionFailureClass.UNSUPPORTED_OPERATION,
            )
        }

        return try {
            when (request.operation) {
                SystemOperation.DISCOVER_USERS -> {
                    val result = gateway.listUsers(request.timeoutMs)
                    fromCommand(
                        result,
                        metadata = if (result.success) {
                            mapOf("usersRaw" to result.output)
                        } else {
                            emptyMap()
                        },
                    )
                }

                SystemOperation.DISCOVER_PACKAGES -> {
                    val target = request.target ?: return missingTarget()
                    val result = gateway.listPackagesForUser(
                        target.androidUserId,
                        request.timeoutMs,
                    )
                    fromCommand(
                        result,
                        metadata = if (result.success) {
                            mapOf("packagesRaw" to result.output)
                        } else {
                            emptyMap()
                        },
                    )
                }

                SystemOperation.RESOLVE_INSTANCE -> {
                    val target = request.target ?: return missingTarget()
                    val result = gateway.listPackagesForUser(
                        target.androidUserId,
                        request.timeoutMs,
                    )
                    if (!result.success) {
                        fromCommand(result)
                    } else {
                        val found = result.output.lineSequence()
                            .any { it.trim() == "package:${target.packageName}" }

                        EngineExecutionResult(
                            id,
                            found,
                            failure = if (found) {
                                null
                            } else {
                                ExecutionFailureClass.PACKAGE_UNAVAILABLE
                            },
                        )
                    }
                }

                SystemOperation.LAUNCH_INSTANCE,
                SystemOperation.RECOVER_INSTANCE -> {
                    val target = request.target ?: return missingTarget()
                    fromCommand(
                        gateway.launchPackageForUser(
                            target.androidUserId,
                            target.packageName,
                            request.timeoutMs,
                        )
                    )
                }

                SystemOperation.FORCE_STOP_INSTANCE -> {
                    val target = request.target ?: return missingTarget()
                    fromCommand(
                        gateway.forceStopPackageForUser(
                            target.androidUserId,
                            target.packageName,
                            request.timeoutMs,
                        )
                    )
                }

                else -> EngineExecutionResult(
                    id,
                    false,
                    failure = ExecutionFailureClass.UNSUPPORTED_OPERATION,
                )
            }
        } catch (error: SecurityException) {
            EngineExecutionResult(
                id,
                false,
                error.javaClass.simpleName,
                ExecutionFailureClass.PERMISSION_DENIED,
            )
        } catch (error: Throwable) {
            EngineExecutionResult(
                id,
                false,
                error.javaClass.simpleName,
                ExecutionFailureClass.UNKNOWN,
            )
        }
    }

    private fun fromCommand(
        result: RootCommandResult,
        metadata: Map<String, String> = emptyMap(),
    ): EngineExecutionResult = EngineExecutionResult(
        engine = id,
        success = result.success,
        detail = "exit=${result.exitCode}",
        failure = when {
            result.success -> null
            result.timedOut -> ExecutionFailureClass.TIMEOUT
            result.exitCode == 126 || result.exitCode == 127 -> ExecutionFailureClass.PERMISSION_DENIED
            else -> ExecutionFailureClass.PLATFORM_RESTRICTED
        },
        metadata = metadata,
    )

    private fun missingTarget() = EngineExecutionResult(
        id,
        false,
        failure = ExecutionFailureClass.PROFILE_UNREACHABLE,
    )
}
