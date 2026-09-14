package com.waalothmany.linkbot.runtime

import android.content.Context
import com.waalothmany.linkbot.data.WhatsAppInstanceEntity
import com.waalothmany.linkbot.runtime.engine.EngineHealthMonitor
import com.waalothmany.linkbot.runtime.engine.ExecutionOrchestrator
import com.waalothmany.linkbot.runtime.engine.ExecutionRequest
import com.waalothmany.linkbot.runtime.engine.InstanceTarget
import com.waalothmany.linkbot.runtime.engine.OrchestratedExecutionResult
import com.waalothmany.linkbot.runtime.engine.ProfileType
import com.waalothmany.linkbot.runtime.engine.SystemOperation
import com.waalothmany.linkbot.runtime.engine.VerificationPolicy
import com.waalothmany.linkbot.runtime.engine.accessibility.AccessibilityEngine
import com.waalothmany.linkbot.runtime.engine.root.RootCommandGateway
import com.waalothmany.linkbot.runtime.engine.root.RootEngine
import com.waalothmany.linkbot.runtime.engine.shizuku.ShizukuEngine
import com.waalothmany.linkbot.runtime.engine.shizuku.ShizukuSystemGateway
import com.waalothmany.linkbot.runtime.engine.standard.StandardAndroidEngine
import com.waalothmany.linkbot.runtime.trace.TraceRecorder
import com.waalothmany.linkbot.whatsapp.SystemWhatsAppDiscovery

object EngineRegistry {
    @Volatile private var initialized = false

    lateinit var orchestrator: ExecutionOrchestrator
        private set
    lateinit var discovery: SystemWhatsAppDiscovery
        private set
    lateinit var shizukuGateway: ShizukuSystemGateway
        private set
    lateinit var rootGateway: RootCommandGateway
        private set
    lateinit var rootEngine: RootEngine
        private set

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        shizukuGateway = ShizukuSystemGateway(appContext)
        rootGateway = RootCommandGateway()
        val health = EngineHealthMonitor()
        val settings = appContext.getSharedPreferences("settings", 0)
        val traceRecorder = TraceRecorder { event ->
            DiagnosticLog.record(
                "ENGINE_TRACE",
                mapOf(
                    "traceId" to event.traceId,
                    "step" to event.step,
                    "engine" to (event.engine?.name ?: "NONE"),
                    "attempt" to event.attempt,
                    "durationMs" to event.durationMs,
                    "result" to event.result,
                    "failure" to (event.failure?.name ?: "NONE"),
                    "fallbackTo" to (event.fallbackTo?.name ?: "NONE"),
                ) + event.metadata,
            )
        }
        rootEngine = RootEngine(rootGateway) { settings.getBoolean("root_engine_enabled", false) }
        orchestrator = ExecutionOrchestrator(
            engines = listOf(
                ShizukuEngine(shizukuGateway),
                rootEngine,
                StandardAndroidEngine(appContext),
                AccessibilityEngine(),
            ),
            health = health,
            verifier = AndroidExecutionPostconditionVerifier(),
            traceRecorder = traceRecorder,
        )
        discovery = SystemWhatsAppDiscovery(orchestrator)
        initialized = true
    }

    fun setRootFallbackEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences("settings", 0)
            .edit().putBoolean("root_engine_enabled", enabled).apply()
    }

    fun isRootFallbackEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences("settings", 0)
            .getBoolean("root_engine_enabled", false)

    suspend fun launchInstance(instance: WhatsAppInstanceEntity): OrchestratedExecutionResult =
        orchestrator.execute(
            ExecutionRequest(
                operation = SystemOperation.LAUNCH_INSTANCE,
                target = instance.toTarget(),
                verification = VerificationPolicy.FOREGROUND_PACKAGE,
                timeoutMs = 6_000,
            )
        )

    suspend fun recoverInstance(instance: WhatsAppInstanceEntity): OrchestratedExecutionResult =
        orchestrator.execute(
            ExecutionRequest(
                operation = SystemOperation.RECOVER_INSTANCE,
                target = instance.toTarget(),
                verification = VerificationPolicy.FOREGROUND_PACKAGE,
                timeoutMs = 6_000,
            )
        )


    suspend fun probeRoot() = rootEngine.probe()

    fun targetOf(instance: WhatsAppInstanceEntity): InstanceTarget = instance.toTarget()

    private fun WhatsAppInstanceEntity.toTarget(): InstanceTarget = InstanceTarget(
        packageName = packageName,
        androidUserId = androidUserId,
        profileType = runCatching { ProfileType.valueOf(profileType) }.getOrDefault(ProfileType.UNKNOWN),
    )
}
