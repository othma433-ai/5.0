package com.waalothmany.linkbot.core.exporter

data class SessionReport(
    val instance: String,
    val startedAt: Long,
    val endedAt: Long,
    val groupsSelected: Int,
    val groupsCompleted: Int,
    val groupsFailed: Int,
    val uniqueLinks: Int,
    val totalOccurrences: Int,
    val exportPaths: List<String>,
)

object SessionReportFormatter {
    fun text(report: SessionReport): String = buildString {
        appendLine("WA Al-Othmany Link Bot - Session Report")
        appendLine("instance=${report.instance}")
        appendLine("startedAt=${report.startedAt}")
        appendLine("endedAt=${report.endedAt}")
        appendLine("groupsSelected=${report.groupsSelected}")
        appendLine("groupsCompleted=${report.groupsCompleted}")
        appendLine("groupsFailed=${report.groupsFailed}")
        appendLine("uniqueLinks=${report.uniqueLinks}")
        appendLine("totalOccurrences=${report.totalOccurrences}")
        appendLine("exportPaths=${report.exportPaths.joinToString(";")}")
    }
}
