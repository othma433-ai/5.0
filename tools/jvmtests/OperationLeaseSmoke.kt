package com.waalothmany.linkbot.runtime

fun main() {
    val controller = OperationLeaseController()

    val exclusiveSync = controller.beginExclusive(OperationKind.SYNC)
    check(exclusiveSync != null)
    check(controller.beginExclusive(OperationKind.EXTRACTION) == null) { "second operation must be rejected while lease is active" }
    check(controller.currentKind() == OperationKind.SYNC)
    controller.invalidate(exclusiveSync)

    val first = controller.begin()
    check(controller.isCurrent(first))
    check(controller.claimTerminal(first))
    check(!controller.claimTerminal(first)) { "same operation must not claim terminal completion twice" }

    val second = controller.begin()
    check(second.generation > first.generation)
    check(!controller.isCurrent(first)) { "old generation must be rejected after a new operation begins" }
    check(controller.isCurrent(second))
    check(!controller.claimTerminal(first)) { "stale generation must never claim terminal ownership" }
    check(controller.claimTerminal(second))

    controller.invalidate(second)
    check(!controller.isCurrent(second))

    val groupGate = TerminalOnceGate<String>()
    groupGate.reset("queue-1")
    check(groupGate.claim("queue-1"))
    check(!groupGate.claim("queue-1")) { "group completion must be idempotent" }
    groupGate.reset("queue-2")
    check(!groupGate.claim("queue-1")) { "old group callback must not claim a new group gate" }
    check(groupGate.claim("queue-2"))

    println("OperationLeaseSmoke: PASS")
}
