package com.waalothmany.linkbot.runtime

fun main() {
    val required = setOf(
        RuntimePhase.IDLE,
        RuntimePhase.INITIALIZING,
        RuntimePhase.CHECKING_CAPABILITIES,
        RuntimePhase.READY,
        RuntimePhase.SYNCING_GROUPS,
        RuntimePhase.EXTRACTING,
        RuntimePhase.PAUSED,
        RuntimePhase.RECOVERING,
        RuntimePhase.COMPLETED,
        RuntimePhase.STOPPED,
        RuntimePhase.ERROR,
    )
    check(required.size == 11)
    check(RuntimePhase.SYNCING_GROUPS.isActiveAutomation)
    check(RuntimePhase.EXTRACTING.isActiveAutomation)
    check(!RuntimePhase.READY.isActiveAutomation)
    println("RuntimePhaseSmoke: PASS")
}
