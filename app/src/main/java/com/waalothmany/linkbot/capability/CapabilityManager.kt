package com.waalothmany.linkbot.capability

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.waalothmany.linkbot.automation.WaAccessibilityService
import com.waalothmany.linkbot.runtime.BotForegroundService

data class CapabilitySnapshot(
    val accessibilityEnabled: Boolean,
    val accessibilityState: AccessibilityRuntimeState,
    val overlay: Boolean,
    val notifications: Boolean,
    val foregroundServiceReady: Boolean,
    val storageAccessFrameworkReady: Boolean,
    val shizukuInstalled: Boolean,
    val shizukuBinderAlive: Boolean,
    val shizukuPermissionGranted: Boolean,
    val rootDetected: Boolean,
    val rootUsable: Boolean,
) {
    val accessibility: Boolean get() = accessibilityEnabled
    val accessibilityConnected: Boolean
        get() = AccessibilityConnectionPolicy.isOperational(accessibilityState)

    val shizukuUsable: Boolean get() = shizukuBinderAlive && shizukuPermissionGranted
    val coreReady: Boolean get() = accessibilityConnected && foregroundServiceReady
    val mode: RuntimeCapabilityMode
        get() = RuntimeCapabilityResolver.resolve(
            shizukuUsable = shizukuUsable,
            rootUsable = rootUsable,
            shizukuInstalled = shizukuInstalled,
            rootDetected = rootDetected,
        )

    fun asFlags() = CapabilityFlags(
        accessibilityEnabled = accessibilityEnabled,
        accessibilityConnected = accessibilityConnected,
        overlay = overlay,
        notifications = notifications,
        foregroundServiceReady = foregroundServiceReady,
        storageAccessFrameworkReady = storageAccessFrameworkReady,
        shizukuInstalled = shizukuInstalled,
        shizukuUsable = shizukuUsable,
        rootDetected = rootDetected,
        rootUsable = rootUsable,
    )
}

object CapabilityManager {
    const val SHIZUKU_PERMISSION_REQUEST = 7101

    fun snapshot(context: Context): CapabilitySnapshot {
        val enabled = isAccessibilityEnabled(context)
        val live = AccessibilityConnectionMonitor.state.value
        val accessibilityState = AccessibilityConnectionPolicy.deriveState(
            enabledInSettings = enabled,
            snapshot = live,
        )
        val shizukuInstalled = isPackageInstalled(context, "moe.shizuku.privileged.api")
        val shizukuBinderAlive = shizukuInstalled && ShizukuCapabilityBackend.binderAlive()
        val shizukuPermissionGranted = shizukuBinderAlive && ShizukuCapabilityBackend.permissionGranted()
        val rootDetected = RootCapabilityBackend.detected()
        return CapabilitySnapshot(
            accessibilityEnabled = enabled,
            accessibilityState = accessibilityState,
            overlay = Settings.canDrawOverlays(context),
            notifications = notificationsAllowed(context),
            foregroundServiceReady = foregroundServiceDeclared(context),
            storageAccessFrameworkReady = safAvailable(context),
            shizukuInstalled = shizukuInstalled,
            shizukuBinderAlive = shizukuBinderAlive,
            shizukuPermissionGranted = shizukuPermissionGranted,
            rootDetected = rootDetected,
            rootUsable = if (rootDetected) RootCapabilityBackend.usable() else false,
        )
    }

    fun requestShizukuPermission(): Boolean =
        ShizukuCapabilityBackend.requestPermission(SHIZUKU_PERMISSION_REQUEST)

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

    private fun foregroundServiceDeclared(context: Context): Boolean = runCatching {
        val component = ComponentName(context, BotForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getServiceInfo(component, PackageManager.ComponentInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getServiceInfo(component, 0)
        }
        true
    }.getOrDefault(false)

    private fun safAvailable(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE)
        return intent.resolveActivity(context.packageManager) != null
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    }.getOrDefault(false)
}
