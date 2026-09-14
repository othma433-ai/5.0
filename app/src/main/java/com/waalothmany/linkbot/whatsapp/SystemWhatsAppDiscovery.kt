package com.waalothmany.linkbot.whatsapp

import com.waalothmany.linkbot.data.WhatsAppInstanceEntity
import com.waalothmany.linkbot.runtime.DiagnosticLog
import com.waalothmany.linkbot.runtime.engine.ExecutionOrchestrator
import com.waalothmany.linkbot.runtime.engine.ExecutionRequest
import com.waalothmany.linkbot.runtime.engine.InstanceTarget
import com.waalothmany.linkbot.runtime.engine.ProfileType
import com.waalothmany.linkbot.runtime.engine.SystemOperation
import com.waalothmany.linkbot.runtime.engine.VerificationPolicy

/** Privileged profile/package discovery through the same adaptive engine router used for launch. */
class SystemWhatsAppDiscovery(
    private val orchestrator: ExecutionOrchestrator,
) {
    suspend fun discover(): List<WhatsAppInstanceEntity> {
        val usersResult = orchestrator.execute(
            ExecutionRequest(
                operation = SystemOperation.DISCOVER_USERS,
                verification = VerificationPolicy.NONE,
                timeoutMs = 4_000,
            )
        )
        if (!usersResult.success) {
            DiagnosticLog.record(
                "PRIVILEGED_DISCOVERY_UNAVAILABLE",
                mapOf("failure" to (usersResult.failure?.name ?: "UNKNOWN"), "trace" to usersResult.traceId),
            )
            return emptyList()
        }
        val usersAttempt = usersResult.attempts.lastOrNull { it.success } ?: return emptyList()
        val rawUsers = usersAttempt.metadata["usersRaw"].orEmpty()
        val profiles = ProfileAwareInstanceResolver.parseUsers(rawUsers)
        if (profiles.isEmpty()) return emptyList()

        val discovered = LinkedHashMap<Pair<Int, String>, WhatsAppInstanceEntity>()
        profiles.forEach { profile ->
            val runtimeProfile = runCatching { ProfileType.valueOf(profile.profileType) }.getOrDefault(ProfileType.UNKNOWN)
            val packagesResult = orchestrator.execute(
                ExecutionRequest(
                    operation = SystemOperation.DISCOVER_PACKAGES,
                    target = InstanceTarget("com.whatsapp", profile.userId, runtimeProfile),
                    verification = VerificationPolicy.NONE,
                    timeoutMs = 4_000,
                )
            )
            val packageAttempt = packagesResult.attempts.lastOrNull { it.success } ?: return@forEach
            val packages = ProfileAwareInstanceResolver.parsePackages(packageAttempt.metadata["packagesRaw"].orEmpty())
            val engineName = packageAttempt.engine.name
            ProfileAwareInstanceResolver.candidates(profile, packages).forEach { candidate ->
                val key = candidate.androidUserId to candidate.packageName
                discovered[key] = WhatsAppInstanceEntity(
                    id = candidate.id,
                    packageName = candidate.packageName,
                    label = candidate.label,
                    kind = candidate.kind,
                    androidUserId = candidate.androidUserId,
                    profileType = candidate.profileType,
                    profileLabel = candidate.profileLabel,
                    launchStrategy = engineName,
                    lastResolvedEngine = engineName,
                    reachable = true,
                    enabled = true,
                )
            }
        }
        DiagnosticLog.record(
            "PRIVILEGED_DISCOVERY_COMPLETE",
            mapOf("instances" to discovered.size, "profiles" to profiles.size, "trace" to usersResult.traceId),
        )
        return discovered.values.toList()
    }
}
