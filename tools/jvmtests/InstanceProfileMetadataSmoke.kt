package com.waalothmany.linkbot.whatsapp

fun main() {
    val currentId = InstanceIdentityPolicy.stableId("com.whatsapp")
    val currentExplicit = InstanceIdentityPolicy.stableId("com.whatsapp", "current", "default")
    check(currentId == currentExplicit)

    val work = InstanceInventoryItem(
        id = InstanceIdentityPolicy.stableId("com.whatsapp", "user:10", "com.whatsapp/.Main"),
        packageName = "com.whatsapp",
        label = "WhatsApp (Work)",
        kind = "PERSONAL",
        enabled = true,
        lastSeenAt = 1,
        profileIdentity = "user:10",
        installationIdentity = "com.whatsapp/.Main",
        adapterId = "official-personal",
        discoveryEvidence = "LAUNCHER_APPS_PROFILE",
    )
    check(work.profileIdentity == "user:10")
    check(work.id != currentId)
    check(work.adapterId == "official-personal")
    println("InstanceProfileMetadataSmoke: PASS")
}
