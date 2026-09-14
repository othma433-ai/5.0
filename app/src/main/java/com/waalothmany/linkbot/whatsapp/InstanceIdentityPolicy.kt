package com.waalothmany.linkbot.whatsapp

import java.security.MessageDigest

/** Stable across app-label/localization changes and distinct across Android users/profiles. */
object InstanceIdentityPolicy {
    fun stableId(androidUserId: Int, packageName: String): String {
        require(androidUserId >= 0) { "androidUserId must be non-negative" }
        val normalizedPackage = packageName.trim().lowercase()
        val canonical = "user:$androidUserId|package:$normalizedPackage"
        val bytes = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return "wa-" + bytes.take(8).joinToString("") { "%02x".format(it) }
    }

    /** Legacy helper retained for pure tests and v7.2 callers; user 0 only. */
    fun stableId(packageName: String): String = stableId(0, packageName)
}
