package com.waalothmany.linkbot.automation

fun main() {
    val conversation = ContainerRolePolicy.score(
        ContainerSignals(childCount = 10, conversationRows = 9, textChildren = 10, urlChildren = 0)
    )
    val messages = ContainerRolePolicy.score(
        ContainerSignals(childCount = 14, conversationRows = 0, textChildren = 13, urlChildren = 3)
    )
    check(conversation.conversationScore > conversation.messageScore)
    check(messages.messageScore > messages.conversationScore)
    check(ContainerRolePolicy.preferredRole(conversation) == ContainerRole.CONVERSATIONS)
    check(ContainerRolePolicy.preferredRole(messages) == ContainerRole.MESSAGES)
    println("ContainerRolePolicySmoke: PASS")
}
