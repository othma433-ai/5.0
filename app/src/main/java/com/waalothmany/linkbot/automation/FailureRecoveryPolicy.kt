package com.waalothmany.linkbot.automation

enum class FailureDisposition { RETRY, MANUAL_REVIEW, EXHAUSTED }

/**
 * Separates transient automation failures from identity/safety failures.
 * Ambiguous identity must never be auto-retried into an unsafe click loop.
 */
object FailureRecoveryPolicy {
    private const val MAX_ATTEMPTS = 3

    fun decide(lastError: String?, attempts: Int): FailureDisposition {
        if (attempts >= MAX_ATTEMPTS) return FailureDisposition.EXHAUSTED
        val code = lastError.orEmpty().substringBefore(':').trim().uppercase()
        if (code in setOf(
                "AMBIGUOUS_GROUP",
                "GROUP_MISSING",
                "GROUP_IDENTITY_MISMATCH",
                "UNSUPPORTED_UI",
            )
        ) return FailureDisposition.MANUAL_REVIEW
        return FailureDisposition.RETRY
    }
}
