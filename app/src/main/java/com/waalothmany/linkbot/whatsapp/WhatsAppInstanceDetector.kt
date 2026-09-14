package com.waalothmany.linkbot.whatsapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.waalothmany.linkbot.runtime.AndroidUserIdentity
import com.waalothmany.linkbot.data.WhatsAppInstanceEntity

object WhatsAppInstanceDetector {
    private val official = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun detect(context: Context): List<WhatsAppInstanceEntity> {
        val pm = context.packageManager
        val currentUserId = AndroidUserIdentity.currentUserId()
        val packages = LinkedHashSet<String>()

        // Explicitly queried packages are visible without QUERY_ALL_PACKAGES.
        official.filterTo(packages) { packageExists(pm, it) }

        // Discover launcher-visible compatible variants without broad package enumeration.
        launcherPackages(pm).forEach { pkg ->
            val label = applicationLabel(pm, pkg)
            if (PackageCandidatePolicy.isWhatsAppCandidate(pkg, label)) packages += pkg
        }

        return packages.mapNotNull { pkg ->
            if (pm.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null
            val label = applicationLabel(pm, pkg).ifBlank { pkg }
            WhatsAppInstanceEntity(
                id = InstanceIdentityPolicy.stableId(currentUserId, pkg),
                packageName = pkg,
                label = label,
                kind = PackageCandidatePolicy.kind(pkg, label),
                androidUserId = currentUserId,
                profileType = if (currentUserId == 0) "PERSONAL" else "UNKNOWN",
                launchStrategy = "STANDARD",
                reachable = true,
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun isLaunchable(context: Context, packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    fun launch(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
        return true
    }

    private fun launcherPackages(pm: PackageManager): Sequence<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return resolved.asSequence().mapNotNull { it.activityInfo?.packageName }.distinct()
    }

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
