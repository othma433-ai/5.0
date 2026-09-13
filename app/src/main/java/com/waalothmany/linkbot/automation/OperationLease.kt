package com.waalothmany.linkbot.automation

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class AutomationRunKind { SYNC, EXTRACT, RETRY_FAILED }

data class OperationLeaseToken internal constructor(
    val kind: AutomationRunKind,
    val generation: Long,
)

/**
 * Atomic service-local lease that prevents overlapping automation workflows and
 * rejects stale asynchronous startup work after Stop/restart (ABA protection).
 */
class OperationLease {
    private val generation = AtomicLong(0)
    private val owner = AtomicReference<OperationLeaseToken?>(null)

    fun acquire(kind: AutomationRunKind): OperationLeaseToken? {
        val candidate = OperationLeaseToken(kind, generation.incrementAndGet())
        return if (owner.compareAndSet(null, candidate)) candidate else null
    }

    fun isActive(token: OperationLeaseToken): Boolean = owner.get() == token

    fun release(token: OperationLeaseToken): Boolean = owner.compareAndSet(token, null)

    fun releaseAny(): OperationLeaseToken? = owner.getAndSet(null)

    fun current(): AutomationRunKind? = owner.get()?.kind
}
