package com.waalothmany.linkbot.automation

fun main() {
    val selected = listOf(
        ExtractionSelectionItem("u1", unread = true),
        ExtractionSelectionItem("r1", unread = false),
        ExtractionSelectionItem("u2", unread = true),
    )
    check(ExtractionSelectionPolicy.eligible(selected, AutomationMode.UNREAD_ONLY).map { it.id } == listOf("u1", "u2"))
    check(ExtractionSelectionPolicy.eligible(selected, AutomationMode.DEEP).size == 3)
    check(ExtractionSelectionPolicy.eligible(selected, AutomationMode.NEW_ONLY).size == 3)
    println("ExtractionSelectionPolicySmoke: PASS")
}
