package com.waalothmany.linkbot.runtime

data class ThroughputSnapshot(
    val groupsPerMinute: Double,
    val linksPerMinute: Double,
    val groupsProcessed: Int,
    val linksProcessed: Int,
)

class ThroughputMeter(startedAtMs: Long = System.currentTimeMillis()) {
    private var startedAt = startedAtMs
    private var groups = 0
    private var links = 0

    fun addGroups(count: Int = 1, atMs: Long = System.currentTimeMillis()) {
        if (count > 0 && atMs >= startedAt) groups += count
    }

    fun addLinks(count: Int = 1, atMs: Long = System.currentTimeMillis()) {
        if (count > 0 && atMs >= startedAt) links += count
    }

    fun snapshot(nowMs: Long = System.currentTimeMillis()): ThroughputSnapshot {
        val elapsedMinutes = ((nowMs - startedAt).coerceAtLeast(1L)).toDouble() / 60_000.0
        return ThroughputSnapshot(
            groupsPerMinute = groups / elapsedMinutes,
            linksPerMinute = links / elapsedMinutes,
            groupsProcessed = groups,
            linksProcessed = links,
        )
    }

    fun reset(nowMs: Long = System.currentTimeMillis()) {
        startedAt = nowMs
        groups = 0
        links = 0
    }
}
