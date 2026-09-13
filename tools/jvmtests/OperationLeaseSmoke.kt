package com.waalothmany.linkbot.automation

fun main() {
    val lease = OperationLease()
    check(lease.current() == null)

    val first = lease.acquire(AutomationRunKind.SYNC) ?: error("SYNC lease not acquired")
    check(lease.current() == AutomationRunKind.SYNC)
    check(lease.isActive(first))
    check(lease.acquire(AutomationRunKind.EXTRACT) == null)
    check(lease.acquire(AutomationRunKind.RETRY_FAILED) == null)
    check(lease.release(first))
    check(!lease.isActive(first))
    check(lease.current() == null)

    val second = lease.acquire(AutomationRunKind.SYNC) ?: error("second SYNC lease not acquired")
    check(second.generation != first.generation)
    check(!lease.release(first)) // stale/ABA token must never release a newer workflow.
    check(lease.isActive(second))
    check(lease.release(second))

    repeat(100) {
        val raceLease = OperationLease()
        val ready = java.util.concurrent.CountDownLatch(3)
        val start = java.util.concurrent.CountDownLatch(1)
        val winners = java.util.concurrent.atomic.AtomicInteger(0)
        val threads = AutomationRunKind.entries.map { kind ->
            Thread {
                ready.countDown()
                start.await()
                if (raceLease.acquire(kind) != null) winners.incrementAndGet()
            }.also(Thread::start)
        }
        ready.await()
        start.countDown()
        threads.forEach(Thread::join)
        check(winners.get() == 1)
        check(raceLease.current() != null)
    }
    println("OperationLeaseSmoke: PASS")
}
