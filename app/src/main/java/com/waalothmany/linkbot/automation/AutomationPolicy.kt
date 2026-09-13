package com.waalothmany.linkbot.automation

data class ProgressSample(
    val fingerprint: String,
    val newItems: Int,
    val scrollAccepted: Boolean,
)

/**
 * Conservative end-of-list detector. A single rejected scroll is never enough to
 * declare completion because WhatsApp/RecyclerView can transiently refuse an action
 * while it is rebinding rows. Completion requires a stable viewport and multiple
 * rejected scrolls across repeated probes.
 */
class EndOfListGuard(
    private val stableRequired: Int = 3,
    private val rejectedScrollRequired: Int = 2,
) {
    private var previousFingerprint: String? = null
    private var stableCycles: Int = 0
    private var rejectedScrolls: Int = 0

    fun observe(sample: ProgressSample): Boolean {
        val sameViewport = previousFingerprint == sample.fingerprint
        val progressed = sample.newItems > 0 || !sameViewport

        if (progressed) {
            stableCycles = 0
            rejectedScrolls = 0
        } else {
            stableCycles++
            if (!sample.scrollAccepted) rejectedScrolls++ else rejectedScrolls = 0
        }

        previousFingerprint = sample.fingerprint
        return stableCycles >= stableRequired && rejectedScrolls >= rejectedScrollRequired
    }

    fun reset() {
        previousFingerprint = null
        stableCycles = 0
        rejectedScrolls = 0
    }
}
