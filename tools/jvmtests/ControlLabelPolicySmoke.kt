package com.waalothmany.linkbot.automation

fun main() {
    val labels = listOf("Groups", "المجموعات")
    check(ControlLabelPolicy.matches("المجموعات +99", labels))
    check(ControlLabelPolicy.matches("+99 المجموعات", labels))
    check(ControlLabelPolicy.matches("Groups +99", labels))
    check(ControlLabelPolicy.matches("99+ Groups", labels))
    check(ControlLabelPolicy.matches("المجموعات ٣", labels))
    check(ControlLabelPolicy.matches("\u200fالمجموعات +٩٩", labels))
    check(ControlLabelPolicy.matchesDecorated("المجموعات +99 دردشة", labels))
    check(ControlLabelPolicy.matchesDecorated("Groups, 12 unread", labels))
    check(ControlLabelPolicy.matchesDecorated("+99 المجموعات دردشة", labels))
    check(!ControlLabelPolicy.matchesDecorated("المجموعات الطبية", labels))
    println("ControlLabelPolicySmoke: PASS")
}
