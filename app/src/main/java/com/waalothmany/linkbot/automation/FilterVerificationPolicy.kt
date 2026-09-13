package com.waalothmany.linkbot.automation

enum class FilterEvidence { ACTIVE, INACTIVE, UNKNOWN }
enum class FilterDecision { PROCEED, RETRY, PROCEED_WITH_WARNING, FAIL }

/**
 * The Groups filter is an accuracy gate, not a performance hint. If it cannot be proven active,
 * scanning is refused instead of risking private chats being registered as groups.
 */
object FilterVerificationPolicy {
    fun decide(evidence: FilterEvidence, mode: PerformanceMode, attempt: Int): FilterDecision {
        if (evidence == FilterEvidence.ACTIVE) return FilterDecision.PROCEED
        val maxAttempts = when (mode) {
            PerformanceMode.FAST -> 2
            PerformanceMode.BALANCED -> 3
            PerformanceMode.SAFE -> 4
        }
        return if (attempt >= maxAttempts) FilterDecision.FAIL else FilterDecision.RETRY
    }
}
