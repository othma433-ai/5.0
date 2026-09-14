package com.waalothmany.linkbot.runtime.engine.standard

import android.content.Context
import android.content.Intent
import com.waalothmany.linkbot.runtime.AndroidUserIdentity
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

class StandardAndroidEngine(
    context: Context,
) : ExecutionEngine {
    private val appContext = context.applicationContext

    override val id: EngineId = EngineId.STANDARD_ANDROID

    private val supported = setOf(
        EngineCapability.RESOLVE_INSTANCE,
        EngineCapability.LAUNCH_PACKAGE_CURRENT_USER,
        EngineCapability.BRING_TASK_TO_FRONT,
    )

    override fun capabilities(): Set<EngineCapability> = supported

    override suspend fun probe(): EngineProbeResult = EngineProbeResult(
        engine = id,
        state = ProbeState.READY,
        capabilities = supported,
        detail = "user=${AndroidUserIdentity.currentUserId()}",
    )

    override suspend fun execute(
        request: ExecutionRequest,
        context: ExecutionContext,
    ): EngineExecutionResult {
        val target = request.target
            ?: return EngineExecutionResult(id, false, failure = ExecutionFailureClass.PROFILE_UNREACHABLE)
        val currentUserId = AndroidUserIdentity.currentUserId()
        if (target.androidUserId != currentUserId) {
            return EngineExecutionResult(
                id,
                false,
                detail = "targetUser=${target.androidUserId} currentUser=$currentUserId",
                failure = ExecutionFailureClass.PROFILE_UNREACHABLE,
            )
        }

        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(target.packageName)
        return when (request.operation) {
            SystemOperation.RESOLVE_INSTANCE -> EngineExecutionResult(
                id,
                success = launchIntent != null,
                failure = if (launchIntent != null) null else ExecutionFailureClass.PACKAGE_UNAVAILABLE,
            )
            SystemOperation.LAUNCH_INSTANCE,
            SystemOperation.RECOVER_INSTANCE -> {
                if (launchIntent == null) {
                    EngineExecutionResult(id, false, failure = ExecutionFailureClass.PACKAGE_UNAVAILABLE)
                } else {
                    runCatching {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        appContext.startActivity(launchIntent)
                    }.fold(
                        onSuccess = {
                            EngineExecutionResult(
                                id,
                                true,
                                detail = "trace=${context.traceId}",
                            )
                        },
                        onFailure = { error ->
                            EngineExecutionResult(
                                id,
                                false,
                                detail = error.javaClass.simpleName,
                                failure = if (error is SecurityException) {
                                    ExecutionFailureClass.PERMISSION_DENIED
                                } else {
                                    ExecutionFailureClass.PLATFORM_RESTRICTED
                                },
                            )
                        },
                    )
                }
            }
            else -> EngineExecutionResult(id, false, failure = ExecutionFailureClass.UNSUPPORTED_OPERATION)
        }
    }
}
