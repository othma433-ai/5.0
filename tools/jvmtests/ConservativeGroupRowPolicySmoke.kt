package com.waalothmany.linkbot.automation

fun main() {
    check(ConservativeGroupRowPolicy.isLikelyGroup(
        title = "AAU جامعة عمان العربية",
        values = listOf("AAU جامعة عمان العربية", "انضم ~ باستخدام رابط المجموعة."),
        viewIds = emptyList(),
    ))
    check(ConservativeGroupRowPolicy.isLikelyGroup(
        title = "Students",
        values = listOf("Students", "~ hgg: hello"),
        viewIds = emptyList(),
    ))
    check(ConservativeGroupRowPolicy.isLikelyGroup(
        title = "Course group",
        values = listOf("Course group", "message"),
        viewIds = listOf("com.whatsapp:id/group_avatar"),
    ))
    check(!ConservativeGroupRowPolicy.isLikelyGroup(
        title = "+967 716 112 512",
        values = listOf("+967 716 112 512", "الو"),
        viewIds = emptyList(),
    ))
    check(!ConservativeGroupRowPolicy.isLikelyGroup(
        title = "Ahmed",
        values = listOf("Ahmed", "مرحبا"),
        viewIds = emptyList(),
    ))
    println("ConservativeGroupRowPolicySmoke: PASS")
}
