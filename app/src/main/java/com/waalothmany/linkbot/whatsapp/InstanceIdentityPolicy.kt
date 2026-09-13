package com.waalothmany.linkbot.whatsapp

import java.security.MessageDigest

/** Stable across app-label/localization changes for a package visible in the current Android profile. */
object InstanceIdentityPolicy {
    fun stableId(packageName: String): String {
        val normalized = packageName.trim().lowercase()
        val bytes = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return "wa-" + bytes.take(8).joinToString("") { "%02x".format(it) }
    }
}
