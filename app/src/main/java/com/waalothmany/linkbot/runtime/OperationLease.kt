package com.waalothmany.linkbot.runtime

/** Logical owner of the single automation lane. */
enum class OperationKind { INTERNAL, SYNC, EXTRACTION, RETRY_FAILED, RECOVERY }

/** Immutable ownership token for one logical automation operation. */
data class OperationLease internal constructor(
    val generation: Long,
    val kind: OperationKind = OperationKind.INTERNAL,
)

/**
 * Monotonic operation ownership. Only one exclusive operation can own the lane.
 * Starting a new non-exclusive internal generation is retained for compatibility,
 * while public automation entry points should use [beginExclusive].
 */
class OperationLeaseController {
    private var nextGeneration = 0L
    private var currentLease: OperationLease? = null
    private var terminalClaimed = false

    @Synchronized
    fun begin(kind: OperationKind = OperationKind.INTERNAL): OperationLease {
        nextGeneration += 1L
        return OperationLease(nextGeneration, kind).also {
            currentLease = it
            terminalClaimed = false
        }
    }

    @Synchronized
    fun beginExclusive(kind: OperationKind): OperationLease? {
        if (currentLease != null) return null
        return begin(kind)
    }

    @Synchronized
    fun isCurrent(lease: OperationLease?): Boolean =
        lease != null && currentLease?.generation == lease.generation && currentLease?.kind == lease.kind

    @Synchronized
    fun currentKind(): OperationKind? = currentLease?.kind

    @Synchronized
    fun claimTerminal(lease: OperationLease): Boolean {
        if (!isCurrent(lease) || terminalClaimed) return false
        terminalClaimed = true
        return true
    }

    @Synchronized
    fun invalidate(lease: OperationLease? = null): Boolean {
        val current = currentLease ?: return false
        if (lease != null && (current.generation != lease.generation || current.kind != lease.kind)) return false
        currentLease = null
        terminalClaimed = false
        return true
    }

    @Synchronized
    fun current(): OperationLease? = currentLease
}

/** One-shot terminal gate scoped to a caller-provided key such as queue item id. */
class TerminalOnceGate<K> {
    private var key: K? = null
    private var claimed = false

    @Synchronized
    fun reset(key: K) {
        this.key = key
        claimed = false
    }

    @Synchronized
    fun clear() {
        key = null
        claimed = false
    }

    @Synchronized
    fun claim(key: K): Boolean {
        if (this.key != key || claimed) return false
        claimed = true
        return true
    }
}
