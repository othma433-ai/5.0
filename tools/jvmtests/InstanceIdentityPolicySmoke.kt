package com.waalothmany.linkbot.whatsapp

fun main() {
    val a = InstanceIdentityPolicy.stableId("com.whatsapp")
    val b = InstanceIdentityPolicy.stableId("com.whatsapp")
    val c = InstanceIdentityPolicy.stableId("com.whatsapp.w4b")
    check(a == b)
    check(a != c)
    check(a.startsWith("wa-"))
    println("InstanceIdentityPolicySmoke: PASS")
}
