package com.waalothmany.linkbot.jvmtests

import com.waalothmany.linkbot.core.exporter.SessionReport
import com.waalothmany.linkbot.core.exporter.SessionReportFormatter

class SessionReportSmoke {
    fun reportContainsRequiredOperationalFields() {
        val report = SessionReport(
            instance = "WhatsApp Business",
            startedAt = 10L,
            endedAt = 20L,
            groupsSelected = 5,
            groupsCompleted = 4,
            groupsFailed = 1,
            uniqueLinks = 9,
            totalOccurrences = 12,
            exportPaths = listOf("content://exports/a.xlsx"),
        )
        val text = SessionReportFormatter.text(report)
        listOf("WhatsApp Business", "groupsSelected=5", "groupsCompleted=4", "groupsFailed=1", "uniqueLinks=9", "totalOccurrences=12", "content://exports/a.xlsx")
            .forEach { check(text.contains(it)) }
    }
}
