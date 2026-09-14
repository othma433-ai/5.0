package com.waalothmany.linkbot.runtime.engine.root

fun main() {
    check(RootCommandPolicy.render(RootCommand.ProbeUid) == "id -u")
    check(RootCommandPolicy.render(RootCommand.ListUsers) == "pm list users")
    check(RootCommandPolicy.render(RootCommand.ListPackagesForUser(10)) == "pm list packages --user 10")
    check(
        RootCommandPolicy.render(RootCommand.ResolvePackageForUser(10, "com.whatsapp")) ==
            "cmd package resolve-activity --brief --user 10 com.whatsapp"
    )
    check(
        RootCommandPolicy.render(RootCommand.StartComponentForUser(10, "com.whatsapp/.Main")) ==
            "am start --user 10 -n com.whatsapp/.Main"
    )
    check(RootCommandPolicy.render(RootCommand.ForceStopPackageForUser(0, "com.whatsapp.w4b")) == "am force-stop --user 0 com.whatsapp.w4b")

    val attacks = listOf(
        "com.whatsapp;id",
        "com.whatsapp&&id",
        "com.whatsapp\nid",
        "com.whatsapp`id`",
        "com.whatsapp$(id)",
        "../com.whatsapp",
    )
    attacks.forEach { bad ->
        check(runCatching { RootCommandPolicy.render(RootCommand.ResolvePackageForUser(0, bad)) }.isFailure) { bad }
    }
    check(runCatching { RootCommandPolicy.render(RootCommand.StartComponentForUser(0, "com.whatsapp/.Main;id")) }.isFailure)
    check(runCatching { RootCommandPolicy.render(RootCommand.ListPackagesForUser(-1)) }.isFailure)
    println("RootCommandPolicySmoke: PASS")
}
