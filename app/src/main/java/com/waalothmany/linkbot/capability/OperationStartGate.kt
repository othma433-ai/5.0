package com.waalothmany.linkbot.capability

enum class OperationStartBlockReason {
    NO_INSTANCE,
    PACKAGE_NOT_LAUNCHABLE,
    ACCESSIBILITY_DISABLED,
    ACCESSIBILITY_NOT_CONNECTED,
    NOTIFICATIONS_DISABLED,
    OPERATION_ALREADY_RUNNING,
}

data class OperationStartContext(
    val instanceSelected: Boolean,
    val packageLaunchable: Boolean,
    val accessibilityEnabled: Boolean,
    val accessibilityConnected: Boolean,
    val notificationsReady: Boolean,
    val operationBusy: Boolean,
)

data class OperationStartDecision(
    val allowed: Boolean,
    val reason: OperationStartBlockReason? = null,
)

object OperationStartGate {
    fun evaluate(context: OperationStartContext): OperationStartDecision {
        val reason = when {
            !context.instanceSelected -> OperationStartBlockReason.NO_INSTANCE
            !context.packageLaunchable -> OperationStartBlockReason.PACKAGE_NOT_LAUNCHABLE
            !context.accessibilityEnabled -> OperationStartBlockReason.ACCESSIBILITY_DISABLED
            !context.accessibilityConnected -> OperationStartBlockReason.ACCESSIBILITY_NOT_CONNECTED
            context.operationBusy -> OperationStartBlockReason.OPERATION_ALREADY_RUNNING
            else -> null
        }
        return OperationStartDecision(
            allowed = reason == null,
            reason = reason,
        )
    }
}
