package com.waalothmany.linkbot.automation

enum class PerformanceMode { FAST, BALANCED, SAFE }

data class PerformanceProfile(
    val scrollProbeMs: Long,
    val rejectedScrollProbeMs: Long,
    val stableEndCycles: Int,
    val rejectedScrollsRequired: Int,
    val stageTimeoutMs: Long,
)

object PerformanceProfiles {
    fun forMode(mode: PerformanceMode): PerformanceProfile = when (mode) {
        PerformanceMode.FAST -> PerformanceProfile(
            scrollProbeMs = 260,
            rejectedScrollProbeMs = 150,
            stableEndCycles = 2,
            rejectedScrollsRequired = 2,
            stageTimeoutMs = 8_000,
        )
        PerformanceMode.BALANCED -> PerformanceProfile(
            scrollProbeMs = 420,
            rejectedScrollProbeMs = 220,
            stableEndCycles = 3,
            rejectedScrollsRequired = 2,
            stageTimeoutMs = 12_000,
        )
        PerformanceMode.SAFE -> PerformanceProfile(
            scrollProbeMs = 650,
            rejectedScrollProbeMs = 320,
            stableEndCycles = 4,
            rejectedScrollsRequired = 3,
            stageTimeoutMs = 18_000,
        )
    }

    fun parse(raw: String?): PerformanceMode = runCatching {
        PerformanceMode.valueOf(raw.orEmpty().uppercase())
    }.getOrDefault(PerformanceMode.BALANCED)
}
