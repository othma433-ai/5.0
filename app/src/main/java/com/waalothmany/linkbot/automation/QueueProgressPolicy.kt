package com.waalothmany.linkbot.automation

data class QueueProgressSummary(
    val total: Int,
    val completed: Int,
    val failed: Int,
    val remaining: Int,
    val inFlight: Int,
)

object QueueProgressPolicy {
    private val completedStates = setOf("COMPLETED", "SKIPPED")
    private val inFlightStates = setOf("LOCATING", "OPENING", "VERIFYING", "SCANNING", "PAUSED")

    fun summarize(states: Collection<String>): QueueProgressSummary {
        val normalized = states.map { it.uppercase() }
        val completed = normalized.count { it in completedStates }
        val failed = normalized.count { it == "FAILED" }
        val inFlight = normalized.count { it in inFlightStates }
        return QueueProgressSummary(
            total = normalized.size,
            completed = completed,
            failed = failed,
            remaining = (normalized.size - completed - failed).coerceAtLeast(0),
            inFlight = inFlight,
        )
    }
}
