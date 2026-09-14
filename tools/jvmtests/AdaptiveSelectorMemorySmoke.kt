package com.waalothmany.linkbot.whatsapp

fun main() {
    val memory = AdaptiveSelectorMemory(maxPerRole = 3, failurePruneThreshold = 2)
    val a = "instance-a"
    val b = "instance-b"
    val signature = SelectorSignature(resourceIdSuffix = "conversations_filter_debug_view_id_groups", normalizedLabel = "groups")

    memory.recordVerified(a, SelectorRole.GROUPS_FILTER, signature)
    check(memory.candidates(a, SelectorRole.GROUPS_FILTER).firstOrNull()?.signature == signature)
    check(memory.candidates(b, SelectorRole.GROUPS_FILTER).isEmpty()) { "selectors must be isolated per instance" }

    memory.recordFailure(a, SelectorRole.GROUPS_FILTER, signature)
    check(memory.candidates(a, SelectorRole.GROUPS_FILTER).isNotEmpty())
    memory.recordFailure(a, SelectorRole.GROUPS_FILTER, signature)
    check(memory.candidates(a, SelectorRole.GROUPS_FILTER).isEmpty()) { "repeatedly failing learned selectors must be pruned" }

    memory.recordVerified(a, SelectorRole.GROUPS_FILTER, SelectorSignature("g1", "one"))
    memory.recordVerified(a, SelectorRole.GROUPS_FILTER, SelectorSignature("g2", "two"))
    memory.recordVerified(a, SelectorRole.GROUPS_FILTER, SelectorSignature("g3", "three"))
    memory.recordVerified(a, SelectorRole.GROUPS_FILTER, SelectorSignature("g4", "four"))
    check(memory.candidates(a, SelectorRole.GROUPS_FILTER).size == 3)
    check(memory.candidates(a, SelectorRole.GROUPS_FILTER).first().signature.resourceIdSuffix == "g4")

    println("AdaptiveSelectorMemorySmoke: PASS")
}
