package com.waalothmany.linkbot.whatsapp

data class LearnedSelectorCandidate(
    val signature: SelectorSignature,
    val successes: Int,
    val failures: Int,
    val sequence: Long,
) {
    val score: Int get() = successes * 10 - failures * 12
}

/**
 * Small in-memory confidence model used by the persistent selector store.
 * Keys are instance-scoped so the same package in Owner/Dual/Work profiles can learn independently.
 */
class AdaptiveSelectorMemory(
    private val maxPerRole: Int = 6,
    private val failurePruneThreshold: Int = 3,
) {
    private data class Key(val instanceId: String, val role: SelectorRole)
    private val values = LinkedHashMap<Key, LinkedHashMap<SelectorSignature, LearnedSelectorCandidate>>()
    private var sequence = 0L

    fun recordVerified(instanceId: String, role: SelectorRole, signature: SelectorSignature) {
        if (instanceId.isBlank() || !signature.isUseful()) return
        val key = Key(instanceId, role)
        val bucket = values.getOrPut(key) { LinkedHashMap() }
        val previous = bucket[signature]
        sequence += 1
        bucket[signature] = LearnedSelectorCandidate(
            signature = signature,
            successes = (previous?.successes ?: 0) + 1,
            failures = 0,
            sequence = sequence,
        )
        trim(bucket)
    }

    fun recordFailure(instanceId: String, role: SelectorRole, signature: SelectorSignature) {
        val key = Key(instanceId, role)
        val bucket = values[key] ?: return
        val previous = bucket[signature] ?: return
        val nextFailures = previous.failures + 1
        if (nextFailures >= failurePruneThreshold) {
            bucket.remove(signature)
            if (bucket.isEmpty()) values.remove(key)
            return
        }
        sequence += 1
        bucket[signature] = previous.copy(failures = nextFailures, sequence = sequence)
    }

    fun candidates(instanceId: String, role: SelectorRole): List<LearnedSelectorCandidate> =
        values[Key(instanceId, role)]?.values
            ?.sortedWith(compareByDescending<LearnedSelectorCandidate> { it.score }.thenByDescending { it.sequence })
            .orEmpty()

    fun replace(instanceId: String, role: SelectorRole, candidates: Collection<LearnedSelectorCandidate>) {
        val key = Key(instanceId, role)
        if (candidates.isEmpty()) {
            values.remove(key)
            return
        }
        val bucket = LinkedHashMap<SelectorSignature, LearnedSelectorCandidate>()
        candidates.sortedBy { it.sequence }.forEach { candidate ->
            if (candidate.signature.isUseful()) {
                bucket[candidate.signature] = candidate
                sequence = maxOf(sequence, candidate.sequence)
            }
        }
        trim(bucket)
        if (bucket.isEmpty()) values.remove(key) else values[key] = bucket
    }

    private fun trim(bucket: LinkedHashMap<SelectorSignature, LearnedSelectorCandidate>) {
        while (bucket.size > maxPerRole) {
            val victim = bucket.values.minWithOrNull(
                compareBy<LearnedSelectorCandidate> { it.score }.thenBy { it.sequence }
            ) ?: break
            bucket.remove(victim.signature)
        }
    }
}
