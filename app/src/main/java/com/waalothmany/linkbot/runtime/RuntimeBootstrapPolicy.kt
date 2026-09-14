package com.waalothmany.linkbot.runtime

object RuntimeBootstrapPolicy {
    private val protectedPhases = setOf(
        RuntimePhase.INITIALIZING,
        RuntimePhase.CHECKING_CAPABILITIES,
        RuntimePhase.SYNCING_GROUPS,
        RuntimePhase.EXTRACTING,
        RuntimePhase.PAUSED,
        RuntimePhase.RECOVERING,
    )

    fun canBootstrap(current: RuntimePhase): Boolean = current !in protectedPhases

    fun finalPhase(coreReady: Boolean): RuntimePhase = if (coreReady) RuntimePhase.READY else RuntimePhase.ERROR
}
