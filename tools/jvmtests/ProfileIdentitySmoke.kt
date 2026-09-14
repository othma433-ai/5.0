package com.waalothmany.linkbot.whatsapp

fun main() {
    val personal = InstanceIdentityPolicy.stableId(0, "com.whatsapp")
    val work = InstanceIdentityPolicy.stableId(10, "com.whatsapp")
    check(personal != work)
    check(personal == InstanceIdentityPolicy.stableId(0, "COM.WHATSAPP"))

    val existing = listOf(
        InstanceInventoryItem(personal, "com.whatsapp", "WhatsApp", "PERSONAL", enabled = true, lastSeenAt = 10L, androidUserId = 0, profileType = "PERSONAL"),
        InstanceInventoryItem(work, "com.whatsapp", "WhatsApp Work", "WORK", enabled = true, lastSeenAt = 10L, androidUserId = 10, profileType = "WORK"),
    )
    val detected = listOf(
        InstanceInventoryItem(work, "com.whatsapp", "WhatsApp Work", "WORK", enabled = true, lastSeenAt = 20L, androidUserId = 10, profileType = "WORK"),
    )
    val merged = InstanceInventoryPolicy.reconcile(existing, detected, 30L)
    check(merged.size == 2)
    check(merged.single { it.androidUserId == 0 }.enabled.not())
    check(merged.single { it.androidUserId == 10 }.enabled)
    println("ProfileIdentitySmoke: PASS")
}
