package com.waalothmany.linkbot.runtime

enum class RuntimePhase {
    IDLE,
    INITIALIZING,
    CHECKING_CAPABILITIES,
    READY,
    SYNCING_GROUPS,
    EXTRACTING,
    PAUSED,
    RECOVERING,
    COMPLETED,
    STOPPED,
    ERROR;

    val isActiveAutomation: Boolean
        get() = this == SYNCING_GROUPS || this == EXTRACTING || this == PAUSED || this == RECOVERING
}
