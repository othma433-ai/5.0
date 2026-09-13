package com.waalothmany.linkbot.automation

fun main() {
    check(RowClassificationPolicy.isSystemLabel("المجموعات"))
    check(RowClassificationPolicy.isSystemLabel("Locked chats"))
    check(!RowClassificationPolicy.isSystemLabel("طلاب الطب"))

    check(RowClassificationPolicy.isLikelyConversationRow(
        title = "طلاب الطب",
        rowClickable = true,
        subtreeClickable = false,
        viewIds = emptyList(),
    ))
    check(RowClassificationPolicy.isLikelyConversationRow(
        title = "Radiology",
        rowClickable = false,
        subtreeClickable = false,
        viewIds = listOf("com.whatsapp:id/conversations_row_contact_name"),
    ))
    check(!RowClassificationPolicy.isLikelyConversationRow(
        title = "Search",
        rowClickable = true,
        subtreeClickable = true,
        viewIds = listOf("com.whatsapp:id/search"),
    ))

    check(RowClassificationPolicy.extractUnreadCount(listOf("12 unread messages")) == 12)
    check(RowClassificationPolicy.extractUnreadCount(listOf("١٢ رسالة غير مقروءة")) == 12)
    check(RowClassificationPolicy.extractUnreadCount(listOf("10:42", "hello")) == null)

    val preview = RowClassificationPolicy.pickPreview(
        title = "Medical Group",
        values = listOf("Medical Group", "10:42", "17", "Ahmed: https://example.com", "17 unread messages")
    )
    check(preview == "Ahmed: https://example.com") { "preview=$preview" }

    check(RowClassificationPolicy.isActive(listOf("Today", "hello")))
    check(RowClassificationPolicy.isActive(listOf("اليوم", "hello")))
    check(RowClassificationPolicy.isActive(listOf("10:42", "hello")))
    check(!RowClassificationPolicy.isActive(listOf("02/01/2025", "old")))

    println("RowClassificationPolicySmoke: PASS")
}
