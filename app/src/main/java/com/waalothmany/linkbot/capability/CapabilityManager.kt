package com.waalothmany.linkbot.capability

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.waalothmany.linkbot.automation.WaAccessibilityService
import com.waalothmany.linkbot.runtime.EngineRegistry
import com.waalothmany.linkbot.runtime.engine.accessibility.AccessibilityRuntimeSupervisor
import com.waalothmany.linkbot.runtime.engine.shizuku.ShizukuRuntime
import com.waalothmany.linkbot.runtime.engine.shizuku.ShizukuRuntimeState

data class CapabilitySnapshot(
    val accessibilityEnabled: Boolean,
    val accessibilityConnected: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    val shizukuInstalled: Boolean,
    val shizukuState: String,
    val shizukuReady: Boolean,
    val shizukuPermissionRequired: Boolean,
    val rootAvailable: Boolean,
    val rootFallbackEnabled: Boolean,
) {
    /** Backward-compatible convenience for UI code while v6 migrates. */
    val accessibility: Boolean get() = accessibilityEnabled

    val coreReady: Boolean
        get() = AccessibilityConnectionPolicy.isOperational(
            enabledInSettings = accessibilityEnabled,
            serviceConnected = accessibilityConnected,
        )

    /**
     * Adaptive runtime selects verified engines per operation and falls back
     * without treating package/root-file presence as readiness.
     */
    val mode: String get() = "ADAPTIVE_MULTI_ENGINE"

    fun asFlags() = CapabilityFlags(
        accessibilityEnabled = accessibilityEnabled,
        accessibilityConnected = accessibilityConnected,
        overlay = overlay,
        notifications = notifications,
        shizukuInstalled = shizukuInstalled,
        rootDetected = rootAvailable,
    )
}

object CapabilityManager {
    fun snapshot(context: Context): CapabilitySnapshot {
        val accessibilityEnabled = isAccessibilityEnabled(context)
        AccessibilityRuntimeSupervisor.updateSettingsEnabled(accessibilityEnabled)
        return CapabilitySnapshot(
            accessibilityEnabled = accessibilityEnabled,
            accessibilityConnected = AccessibilityRuntimeSupervisor.state.value.connected,
            overlay = Settings.canDrawOverlays(context),
            notifications = notificationsAllowed(context),
            shizukuInstalled = isPackageInstalled(context, "moe.shizuku.privileged.api"),
            shizukuState = ShizukuRuntime.state.value.state.name,
            shizukuReady = ShizukuRuntime.state.value.ready,
            shizukuPermissionRequired = ShizukuRuntime.state.value.state == ShizukuRuntimeState.BINDER_ALIVE_NO_PERMISSION,
            rootAvailable = hasRoot(),
            rootFallbackEnabled = runCatching { EngineRegistry.isRootFallbackEnabled(context) }.getOrDefault(false),
        )
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val component = ComponentName(context, WaAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { it.equals(component, ignoreCase = true) }
    }

    private fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    private fun hasRoot(): Boolean = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
    ).any { java.io.File(it).exists() }
}
