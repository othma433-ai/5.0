package com.waalothmany.linkbot.whatsapp

fun main() {
    val rawUsers = """
        Users:
            UserInfo{0:Owner:13} running
            UserInfo{10:Work profile:30} running
            UserInfo{95:Dual Messenger:30} running
    """.trimIndent()
    val users = ProfileAwareInstanceResolver.parseUsers(rawUsers)
    check(users.map { it.userId } == listOf(0, 10, 95))
    check(users.first { it.userId == 10 }.profileType == "WORK")
    check(users.first { it.userId == 95 }.profileType == "DUAL")

    val packages = ProfileAwareInstanceResolver.parsePackages(
        "package:com.whatsapp\npackage:com.example.other\npackage:com.gbwhatsapp\n"
    )
    check("com.whatsapp" in packages)
    check("com.gbwhatsapp" in packages)

    val candidates = ProfileAwareInstanceResolver.candidates(users[1], packages)
    check(candidates.any { it.packageName == "com.whatsapp" && it.androidUserId == 10 })
    check(candidates.any { it.packageName == "com.gbwhatsapp" })
    check(candidates.none { it.packageName == "com.example.other" })
    println("ProfileResolverSmoke: PASS")
}
