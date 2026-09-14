package com.waalothmany.linkbot.automation

fun main() {
    check(QueuePersistencePolicy.normalize("LOCATING") == QueuePersistentState.RUNNING)
    check(QueuePersistencePolicy.normalize("OPENING") == QueuePersistentState.RUNNING)
    check(QueuePersistencePolicy.normalize("SCANNING") == QueuePersistentState.RUNNING)
    check(QueuePersistencePolicy.normalize("WAITING") == QueuePersistentState.WAITING)
    check(QueuePersistencePolicy.normalize("PAUSED") == QueuePersistentState.PAUSED)
    check(QueuePersistencePolicy.normalize("COMPLETED") == QueuePersistentState.COMPLETED)
    check(QueuePersistencePolicy.normalize("FAILED") == QueuePersistentState.FAILED)
    check(QueuePersistencePolicy.normalize("SKIPPED") == QueuePersistentState.SKIPPED)
    check(QueuePersistencePolicy.isResumable(QueuePersistentState.PARTIAL))
    check(!QueuePersistencePolicy.isResumable(QueuePersistentState.COMPLETED))
    println("QueuePersistencePolicySmoke: PASS")
}
