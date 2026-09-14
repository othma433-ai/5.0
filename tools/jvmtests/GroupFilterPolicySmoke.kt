package com.waalothmany.linkbot.automation

fun main() {
    val base = GroupFilterFacts(unread = false, active = false, isNew = false, extractionState = "NEVER_SCANNED")
    check(GroupFilterPolicy.matches(GroupFilterMode.ALL, base))
    check(GroupFilterPolicy.matches(GroupFilterMode.READ, base))
    check(GroupFilterPolicy.matches(GroupFilterMode.NOT_SCANNED, base))
    check(!GroupFilterPolicy.matches(GroupFilterMode.UNREAD, base))

    val pending = base.copy(unread = true, active = true, isNew = true, extractionState = "PENDING")
    check(GroupFilterPolicy.matches(GroupFilterMode.UNREAD, pending))
    check(GroupFilterPolicy.matches(GroupFilterMode.ACTIVE, pending))
    check(GroupFilterPolicy.matches(GroupFilterMode.NEW, pending))
    check(GroupFilterPolicy.matches(GroupFilterMode.PENDING, pending))
    check(!GroupFilterPolicy.matches(GroupFilterMode.COMPLETED, pending))

    val completed = base.copy(extractionState = "COMPLETED")
    check(GroupFilterPolicy.matches(GroupFilterMode.COMPLETED, completed))
    val failed = base.copy(extractionState = "FAILED")
    check(GroupFilterPolicy.matches(GroupFilterMode.FAILED, failed))
    println("GroupFilterPolicySmoke: PASS")
}
