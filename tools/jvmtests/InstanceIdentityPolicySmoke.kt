package com.waalothmany.linkbot.whatsapp

fun main() {
    val legacy = InstanceIdentityPolicy.stableId("com.whatsapp")
    val sameLegacy = InstanceIdentityPolicy.stableId(
        packageName = "com.whatsapp",
        profileIdentity = InstanceIdentityPolicy.DEFAULT_PROFILE_IDENTITY,
        installationIdentity = InstanceIdentityPolicy.DEFAULT_INSTALLATION_IDENTITY,
    )
    val business = InstanceIdentityPolicy.stableId("com.whatsapp.w4b")
    val work = InstanceIdentityPolicy.stableId("com.whatsapp", profileIdentity = "work", installationIdentity = "default")
    val clone = InstanceIdentityPolicy.stableId("com.whatsapp", profileIdentity = "current", installationIdentity = "clone-2")
    check(legacy == sameLegacy) { "legacy current-profile identity must remain stable" }
    check(legacy != business)
    check(legacy != work)
    check(legacy != clone)
    check(work != clone)
    check(legacy.startsWith("wa-"))
    println("InstanceIdentityPolicySmoke: PASS")
}
