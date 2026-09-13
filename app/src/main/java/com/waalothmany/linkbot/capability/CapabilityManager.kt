package com.waalothmany.linkbot.capability

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.waalothmany.linkbot.automation.WaAccessibilityService

data class CapabilitySnapshot(
    val accessibilityEnabled: Boolean,
    val accessibilityConnected: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    val shizukuInstalled: Boolean,
    val rootAvailable: Boolean,
) {
    /** Backward-compatible convenience for UI code while v6 migrates. */
    val accessibility: Boolean get() = accessibilityEnabled

    val coreReady: Boolean
        get() = AccessibilityConnectionPolicy.isOperational(
            enabledInSettings = accessibilityEnabled,
            serviceConnected = accessibilityConnected,
        )

    /**
     * v6 rescue intentionally advertises only the execution adapter that is
     * actually implemented and exercised. Presence of Shizuku/root is shown
     * separately but never promoted to an active mode without a runtime probe.
     */
    val mode: String get() = "STANDARD"

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
    fun snapshot(context: Context): CapabilitySnapshot = CapabilitySnapshot(
        accessibilityEnabled = isAccessibilityEnabled(context),
        accessibilityConnected = AccessibilityConnectionMonitor.state.value.connected,
        overlay = Settings.canDrawOverlays(context),
        notifications = notificationsAllowed(context),
        shizukuInstalled = isPackageInstalled(context, "moe.shizuku.privileged.api"),
        rootAvailable = hasRoot(),
    )

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
