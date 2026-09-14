package com.waalothmany.linkbot.runtime.engine.shizuku

enum class ShizukuRuntimeState {
    NOT_INSTALLED,
    BINDER_UNAVAILABLE,
    BINDER_ALIVE_NO_PERMISSION,
    PERMISSION_REQUESTING,
    PROBING,
    READY,
    BINDER_DEAD,
    UNSUPPORTED,
    ERROR,
}

data class ShizukuRuntimeSnapshot(
    val state: ShizukuRuntimeState = ShizukuRuntimeState.NOT_INSTALLED,
    val packageDetected: Boolean = false,
    val binderAlive: Boolean = false,
    val permissionGranted: Boolean = false,
    val serverUid: Int? = null,
    val apiVersion: Int? = null,
    val lastError: String? = null,
    val generation: Long = 0L,
) {
    val ready: Boolean
        get() = state == ShizukuRuntimeState.READY && binderAlive && permissionGranted && serverUid != null
}

sealed interface ShizukuSignal {
    data object PackageMissing : ShizukuSignal
    data object PackageDetected : ShizukuSignal
    data object BinderReceived : ShizukuSignal
    data object PermissionRequired : ShizukuSignal
    data object PermissionRequestStarted : ShizukuSignal
    data object PermissionGranted : ShizukuSignal
    data object PermissionDenied : ShizukuSignal
    data class ProbeSucceeded(val uid: Int, val apiVersion: Int? = null) : ShizukuSignal
    data class ProbeFailed(val detail: String) : ShizukuSignal
    data object BinderDied : ShizukuSignal
    data object Unsupported : ShizukuSignal
}

object ShizukuStateReducer {
    fun reduce(
        current: ShizukuRuntimeSnapshot,
        signal: ShizukuSignal,
    ): ShizukuRuntimeSnapshot = when (signal) {
        ShizukuSignal.PackageMissing -> ShizukuRuntimeSnapshot(
            state = ShizukuRuntimeState.NOT_INSTALLED,
            generation = current.generation + 1,
        )

        ShizukuSignal.PackageDetected -> current.copy(
            state = if (current.binderAlive) current.state else ShizukuRuntimeState.BINDER_UNAVAILABLE,
            packageDetected = true,
            lastError = null,
        )

        ShizukuSignal.BinderReceived -> current.copy(
            state = if (current.permissionGranted) ShizukuRuntimeState.PROBING else ShizukuRuntimeState.BINDER_ALIVE_NO_PERMISSION,
            packageDetected = true,
            binderAlive = true,
            serverUid = null,
            lastError = null,
            generation = current.generation + 1,
        )

        ShizukuSignal.PermissionRequired -> current.copy(
            state = ShizukuRuntimeState.BINDER_ALIVE_NO_PERMISSION,
            permissionGranted = false,
            serverUid = null,
            lastError = null,
        )

        ShizukuSignal.PermissionRequestStarted -> current.copy(
            state = ShizukuRuntimeState.PERMISSION_REQUESTING,
            permissionGranted = false,
            serverUid = null,
            lastError = null,
        )

        ShizukuSignal.PermissionGranted -> current.copy(
            state = ShizukuRuntimeState.PROBING,
            permissionGranted = true,
            lastError = null,
        )

        ShizukuSignal.PermissionDenied -> current.copy(
            state = ShizukuRuntimeState.BINDER_ALIVE_NO_PERMISSION,
            permissionGranted = false,
            serverUid = null,
            lastError = "PERMISSION_DENIED",
        )

        is ShizukuSignal.ProbeSucceeded -> current.copy(
            state = ShizukuRuntimeState.READY,
            binderAlive = true,
            permissionGranted = true,
            serverUid = signal.uid,
            apiVersion = signal.apiVersion ?: current.apiVersion,
            lastError = null,
        )

        is ShizukuSignal.ProbeFailed -> current.copy(
            state = ShizukuRuntimeState.ERROR,
            serverUid = null,
            lastError = signal.detail,
        )

        ShizukuSignal.BinderDied -> current.copy(
            state = ShizukuRuntimeState.BINDER_DEAD,
            binderAlive = false,
            permissionGranted = false,
            serverUid = null,
            lastError = "BINDER_DEAD",
            generation = current.generation + 1,
        )

        ShizukuSignal.Unsupported -> current.copy(
            state = ShizukuRuntimeState.UNSUPPORTED,
            permissionGranted = false,
            serverUid = null,
            lastError = "UNSUPPORTED",
        )
    }
}
