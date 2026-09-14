package com.waalothmany.linkbot.whatsapp

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.waalothmany.linkbot.data.WhatsAppInstanceEntity

object WhatsAppInstanceDetector {
    private val official = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun detect(context: Context): List<WhatsAppInstanceEntity> {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val userManager = context.getSystemService(UserManager::class.java)
        val current = Process.myUserHandle()
        val profiles = runCatching { launcherApps.profiles }.getOrDefault(listOf(current)).ifEmpty { listOf(current) }
        val detected = LinkedHashMap<String, WhatsAppInstanceEntity>()

        profiles.forEach { user ->
            val profileIdentity = profileIdentity(userManager, user, current)
            val profileSerial = runCatching { userManager.getSerialNumberForUser(user) }.getOrNull()?.takeIf { it >= 0 }
            val activities = discoverLauncherActivities(launcherApps, user)
            activities.forEach { info ->
                val pkg = info.applicationInfo.packageName
                val label = info.label?.toString().orEmpty().ifBlank { pkg }
                if (pkg !in official && !PackageCandidatePolicy.isWhatsAppCandidate(pkg, label)) return@forEach
                val installationIdentity = if (user == current) {
                    InstanceIdentityPolicy.DEFAULT_INSTALLATION_IDENTITY
                } else {
                    info.componentName.flattenToShortString()
                }
                val adapter = WhatsAppAdapterRegistry.resolve(pkg, label)
                val id = InstanceIdentityPolicy.stableId(pkg, profileIdentity, installationIdentity)
                detected[id] = WhatsAppInstanceEntity(
                    id = id,
                    packageName = pkg,
                    label = if (user == current) label else "$label (${profileDisplay(profileIdentity)})",
                    kind = PackageCandidatePolicy.kind(pkg, label),
                    profileIdentity = profileIdentity,
                    installationIdentity = installationIdentity,
                    profileSerial = profileSerial,
                    adapterId = adapter.id,
                    discoveryEvidence = if (user == current) "LAUNCHER_APPS_CURRENT_PROFILE" else "LAUNCHER_APPS_PROFILE",
                )
            }
        }

        // Explicit package checks preserve detection on OEMs that hide launcher rows.
        official.forEach { pkg ->
            if (detected.values.any { it.packageName == pkg && it.profileIdentity == InstanceIdentityPolicy.DEFAULT_PROFILE_IDENTITY }) return@forEach
            val pm = context.packageManager
            if (!packageExists(pm, pkg) || pm.getLaunchIntentForPackage(pkg) == null) return@forEach
            val label = applicationLabel(pm, pkg).ifBlank { pkg }
            val adapter = WhatsAppAdapterRegistry.resolve(pkg, label)
            val id = InstanceIdentityPolicy.stableId(pkg)
            detected[id] = WhatsAppInstanceEntity(
                id = id,
                packageName = pkg,
                label = label,
                kind = PackageCandidatePolicy.kind(pkg, label),
                adapterId = adapter.id,
                discoveryEvidence = "EXPLICIT_CURRENT_PROFILE_PACKAGE",
            )
        }

        return detected.values.sortedWith(compareBy<WhatsAppInstanceEntity> { it.profileIdentity != "current" }.thenBy { it.label.lowercase() })
    }

    fun isLaunchable(context: Context, instance: WhatsAppInstanceEntity): Boolean =
        resolveLauncherActivity(context, instance) != null ||
            (instance.profileIdentity == InstanceIdentityPolicy.DEFAULT_PROFILE_IDENTITY &&
                context.packageManager.getLaunchIntentForPackage(instance.packageName) != null)

    fun isLaunchable(context: Context, packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    fun launch(context: Context, instance: WhatsAppInstanceEntity): Boolean {
        val resolved = resolveLauncherActivity(context, instance)
        if (resolved != null) {
            val (launcherApps, user, activity) = resolved
            return runCatching {
                launcherApps.startMainActivity(activity.componentName, user, null, null)
                true
            }.getOrDefault(false)
        }
        return launch(context, instance.packageName)
    }

    fun launch(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
        return true
    }

    private data class ResolvedLauncher(
        val launcherApps: LauncherApps,
        val user: UserHandle,
        val activity: LauncherActivityInfo,
    )

    private fun resolveLauncherActivity(context: Context, instance: WhatsAppInstanceEntity): ResolvedLauncher? {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val userManager = context.getSystemService(UserManager::class.java)
        val current = Process.myUserHandle()
        val profiles = runCatching { launcherApps.profiles }.getOrDefault(listOf(current))
        val targetUser = profiles.firstOrNull { profileIdentity(userManager, it, current) == instance.profileIdentity } ?: return null
        val activities = runCatching { launcherApps.getActivityList(instance.packageName, targetUser) }.getOrDefault(emptyList())
        val activity = if (instance.installationIdentity == InstanceIdentityPolicy.DEFAULT_INSTALLATION_IDENTITY) {
            activities.firstOrNull()
        } else {
            activities.firstOrNull { it.componentName.flattenToShortString() == instance.installationIdentity }
                ?: activities.firstOrNull()
        } ?: return null
        return ResolvedLauncher(launcherApps, targetUser, activity)
    }

    private fun discoverLauncherActivities(launcherApps: LauncherApps, user: UserHandle): List<LauncherActivityInfo> {
        val combined = LinkedHashMap<String, LauncherActivityInfo>()
        official.forEach { pkg ->
            runCatching { launcherApps.getActivityList(pkg, user) }.getOrDefault(emptyList()).forEach {
                combined["${it.applicationInfo.packageName}|${it.componentName.flattenToShortString()}"] = it
            }
        }
        runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList()).forEach {
            val label = it.label?.toString().orEmpty()
            if (PackageCandidatePolicy.isWhatsAppCandidate(it.applicationInfo.packageName, label)) {
                combined["${it.applicationInfo.packageName}|${it.componentName.flattenToShortString()}"] = it
            }
        }
        return combined.values.toList()
    }

    private fun profileIdentity(userManager: UserManager, user: UserHandle, current: UserHandle): String {
        if (user == current) return InstanceIdentityPolicy.DEFAULT_PROFILE_IDENTITY
        val serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrDefault(-1L)
        return if (serial >= 0L) "user:$serial" else "profile:${user.hashCode()}"
    }

    private fun profileDisplay(profileIdentity: String): String =
        if (profileIdentity == "current") "Current" else profileIdentity

    private fun packageExists(pm: PackageManager, packageName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
        true
    }.getOrDefault(false)

    private fun applicationLabel(pm: PackageManager, packageName: String): String = runCatching {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
        pm.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)
}
