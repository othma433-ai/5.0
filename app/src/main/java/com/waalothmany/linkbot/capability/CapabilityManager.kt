package com.waalothmany.linkbot.capability

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.waalothmany.linkbot.automation.WaAccessibilityService

data class CapabilitySnapshot(
    val accessibility: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    val shizukuInstalled: Boolean,
    val rootAvailable: Boolean,
) {
    // Overlay is deliberately optional: it controls only the floating controller,
    // not the core Accessibility + notification automation path.
    val coreReady: Boolean get() = accessibility && notifications
    val mode: String get() = when {
        rootAvailable -> "STANDARD • Root optional"
        shizukuInstalled -> "STANDARD • Shizuku optional"
        else -> "STANDARD"
    }

    fun asFlags() = CapabilityFlags(
        accessibility = accessibility,
        overlay = overlay,
        notifications = notifications,
        shizukuAvailable = shizukuInstalled,
        rootDetected = rootAvailable,
    )
}

object CapabilityManager {
    fun snapshot(context: Context): CapabilitySnapshot = CapabilitySnapshot(
        accessibility = isAccessibilityEnabled(context),
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
        if (Build.VERSION.SDK_INT < 33) return NotificationManagerCompat.from(context).areNotificationsEnabled()
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
