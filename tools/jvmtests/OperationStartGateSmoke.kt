package com.waalothmany.linkbot.capability

fun main() {
    val allowed = OperationStartGate.evaluate(
        OperationStartContext(
            instanceSelected = true,
            packageLaunchable = true,
            accessibilityEnabled = true,
            accessibilityConnected = true,
            notificationsReady = true,
            operationBusy = false,
        )
    )
    check(allowed.allowed) { allowed.toString() }
    check(allowed.reason == null)

    val disconnected = OperationStartGate.evaluate(
        OperationStartContext(
            instanceSelected = true,
            packageLaunchable = true,
            accessibilityEnabled = true,
            accessibilityConnected = false,
            notificationsReady = true,
            operationBusy = false,
        )
    )
    check(!disconnected.allowed)
    check(disconnected.reason == OperationStartBlockReason.ACCESSIBILITY_NOT_CONNECTED)

    val noInstance = OperationStartGate.evaluate(
        OperationStartContext(
            instanceSelected = false,
            packageLaunchable = false,
            accessibilityEnabled = true,
            accessibilityConnected = true,
            notificationsReady = true,
            operationBusy = false,
        )
    )
    check(noInstance.reason == OperationStartBlockReason.NO_INSTANCE)


    val notificationsDenied = OperationStartGate.evaluate(
        OperationStartContext(
            instanceSelected = true,
            packageLaunchable = true,
            accessibilityEnabled = true,
            accessibilityConnected = true,
            notificationsReady = false,
            operationBusy = false,
        )
    )
    check(notificationsDenied.allowed) { notificationsDenied.toString() }
    check(notificationsDenied.reason == null)

    val busy = OperationStartGate.evaluate(
        OperationStartContext(
            instanceSelected = true,
            packageLaunchable = true,
            accessibilityEnabled = true,
            accessibilityConnected = true,
            notificationsReady = true,
            operationBusy = true,
        )
    )
    check(busy.reason == OperationStartBlockReason.OPERATION_ALREADY_RUNNING)

    println("OperationStartGateSmoke: PASS")
}
