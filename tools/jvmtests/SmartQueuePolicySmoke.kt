package com.waalothmany.linkbot.automation

fun main() {
    val items = listOf(
        QueuePriorityInput("done", unread = false, unreadCount = null, active = false, extractionState = "COMPLETED", lastSeenAt = 100),
        QueuePriorityInput("unread", unread = true, unreadCount = 12, active = true, extractionState = "COMPLETED", lastSeenAt = 400),
        QueuePriorityInput("new", unread = false, unreadCount = null, active = true, extractionState = "NEVER_SCANNED", lastSeenAt = 300),
        QueuePriorityInput("failed", unread = false, unreadCount = null, active = true, extractionState = "FAILED", lastSeenAt = 500),
    )
    check(SmartQueuePolicy.order(items, AutomationMode.UNREAD_ONLY).first().id == "unread")
    check(SmartQueuePolicy.order(items, AutomationMode.DEEP).first().id == "new")
    val newOrder = SmartQueuePolicy.order(items, AutomationMode.NEW_ONLY).map { it.id }
    check(newOrder.indexOf("unread") < newOrder.indexOf("done"))
    check(newOrder.indexOf("new") < newOrder.indexOf("done"))
    println("SmartQueuePolicySmoke: PASS")
}
