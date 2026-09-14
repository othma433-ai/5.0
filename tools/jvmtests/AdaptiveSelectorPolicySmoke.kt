package com.waalothmany.linkbot.whatsapp

fun main() {
    check(AdaptiveSelectorPolicy.resourceIdSuffix("com.whatsapp:id/conversations_filter_debug_view_id_groups") == "conversations_filter_debug_view_id_groups")
    check(AdaptiveSelectorPolicy.resourceIdSuffix("com.whatsapp.w4b:id/conversations_filter_debug_view_id_all") == "conversations_filter_debug_view_id_all")
    check(AdaptiveSelectorPolicy.resourceIdSuffix("bad-value") == null)

    val hints = AdaptiveSelectorPolicy.buildHints(
        packageName = "com.example.clone",
        suffixes = listOf("conversations_filter_debug_view_id_groups", "conversations_filter_debug_view_id_all"),
    )
    check("com.example.clone:id/conversations_filter_debug_view_id_groups" in hints)
    check("com.example.clone:id/conversations_filter_debug_view_id_all" in hints)

    check(SelectorRole.GROUPS_FILTER != SelectorRole.ALL_FILTER)
    println("AdaptiveSelectorPolicySmoke: PASS")
}
