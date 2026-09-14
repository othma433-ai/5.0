package com.waalothmany.linkbot.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RuntimeSnapshot(
    val phase: RuntimePhase = RuntimePhase.IDLE,
    val title: String = "Idle",
    val detail: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val linksFound: Int = 0,
    val error: String? = null,
    val healthScore: Int = 100,
    val effectiveMode: String = "BALANCED",
    val groupsPerMinute: Double = 0.0,
    val linksPerMinute: Double = 0.0,
)

object BotRuntime {
    private val mutable: MutableStateFlow<RuntimeSnapshot> = MutableStateFlow(RuntimeSnapshot())
    val state: StateFlow<RuntimeSnapshot> = mutable.asStateFlow()

    @Volatile private var phaseBeforePause: RuntimePhase = RuntimePhase.READY

    @Volatile var pauseRequested: Boolean = false
        private set
    @Volatile var stopRequested: Boolean = false
        private set
    @Volatile var skipRequested: Boolean = false
        private set

    fun update(value: RuntimeSnapshot) {
        mutable.value = value
        if (!pauseRequested && value.phase in setOf(RuntimePhase.SYNCING_GROUPS, RuntimePhase.EXTRACTING, RuntimePhase.RECOVERING, RuntimePhase.READY)) {
            phaseBeforePause = value.phase
        }
    }

    fun pause() {
        if (!pauseRequested && mutable.value.phase != RuntimePhase.PAUSED) {
            phaseBeforePause = mutable.value.phase
        }
        pauseRequested = true
        mutable.value = mutable.value.copy(phase = RuntimePhase.PAUSED, title = "Paused")
    }

    fun resume() {
        pauseRequested = false
        val target = when (phaseBeforePause) {
            RuntimePhase.SYNCING_GROUPS, RuntimePhase.EXTRACTING, RuntimePhase.RECOVERING -> phaseBeforePause
            else -> if (mutable.value.total > 0) RuntimePhase.EXTRACTING else RuntimePhase.READY
        }
        mutable.value = mutable.value.copy(phase = target, title = "Running")
    }

    fun stop() {
        stopRequested = true
        pauseRequested = false
        mutable.value = mutable.value.copy(phase = RuntimePhase.STOPPED, title = "Stopped")
    }

    fun skip() {
        val active = mutable.value.phase == RuntimePhase.EXTRACTING ||
            (mutable.value.phase == RuntimePhase.PAUSED && phaseBeforePause == RuntimePhase.EXTRACTING)
        if (active) skipRequested = true
    }
    fun consumeSkip(): Boolean = skipRequested.also { skipRequested = false }

    fun resetControlFlags() {
        pauseRequested = false
        stopRequested = false
        skipRequested = false
    }

    fun updateTelemetry(
        healthScore: Int,
        effectiveMode: String,
        groupsPerMinute: Double,
        linksPerMinute: Double,
    ) {
        mutable.value = mutable.value.copy(
            healthScore = healthScore.coerceIn(0, 100),
            effectiveMode = effectiveMode,
            groupsPerMinute = groupsPerMinute.coerceAtLeast(0.0),
            linksPerMinute = linksPerMinute.coerceAtLeast(0.0),
        )
    }
}
