package com.waalothmany.linkbot.whatsapp

import java.security.MessageDigest

/**
 * Stable identity for a WhatsApp installation visible in the current Android
 * context. The default path deliberately preserves the legacy package-only id.
 * Non-default profile/installation identities are reserved for adapters that can
 * actually discover those environments.
 */
object InstanceIdentityPolicy {
    const val DEFAULT_PROFILE_IDENTITY = "current"
    const val DEFAULT_INSTALLATION_IDENTITY = "default"

    fun stableId(
        packageName: String,
        profileIdentity: String = DEFAULT_PROFILE_IDENTITY,
        installationIdentity: String = DEFAULT_INSTALLATION_IDENTITY,
    ): String {
        val pkg = normalize(packageName)
        val profile = normalize(profileIdentity)
        val installation = normalize(installationIdentity)
        val material = if (
            profile == DEFAULT_PROFILE_IDENTITY &&
            installation == DEFAULT_INSTALLATION_IDENTITY
        ) {
            // Compatibility with the v7.1 package-only primary key.
            pkg
        } else {
            "$pkg|profile=$profile|installation=$installation"
        }
        val bytes = MessageDigest.getInstance("SHA-256").digest(material.toByteArray())
        return "wa-" + bytes.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun normalize(value: String): String = value.trim().lowercase()
}
