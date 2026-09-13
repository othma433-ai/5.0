package com.waalothmany.linkbot.whatsapp

fun main() {
    val existing = listOf(
        InstanceInventoryItem("a", "com.whatsapp", "WhatsApp", "PERSONAL", enabled = true, lastSeenAt = 10),
        InstanceInventoryItem("b", "com.whatsapp.w4b", "WhatsApp Business", "BUSINESS", enabled = true, lastSeenAt = 10),
    )
    val detected = listOf(
        InstanceInventoryItem("a", "com.whatsapp", "WhatsApp", "PERSONAL", enabled = true, lastSeenAt = 20),
    )

    val merged = InstanceInventoryPolicy.reconcile(existing, detected, nowMs = 30)
    check(merged.size == 2)
    check(merged.first { it.id == "a" }.enabled)
    check(merged.first { it.id == "a" }.lastSeenAt == 30L)
    check(!merged.first { it.id == "b" }.enabled)
    check(merged.first { it.id == "b" }.lastSeenAt == 10L)

    val newlyDetected = InstanceInventoryPolicy.reconcile(
        emptyList(),
        listOf(InstanceInventoryItem("c", "com.vendor.whatsapp", "WhatsApp Dual", "VARIANT", true, 1)),
        nowMs = 40,
    )
    check(newlyDetected.single().lastSeenAt == 40L)
    check(newlyDetected.single().enabled)

    println("InstanceInventoryPolicySmoke: PASS")
}
