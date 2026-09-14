package com.waalothmany.linkbot.jvmtests

import com.waalothmany.linkbot.core.exporter.ExportGrouping
import com.waalothmany.linkbot.core.exporter.ExportPlanning
import com.waalothmany.linkbot.core.exporter.ExportRow
import com.waalothmany.linkbot.core.exporter.ExportScope

class ExportPlanningSmoke {
    private val rows = listOf(
        ExportRow("https://a.test", "Website", "Group A", "Ali", "2026-09-14", 2),
        ExportRow("https://a.test", "Website", "Group B", "Sara", "2026-09-14", 2),
        ExportRow("https://b.test", "Website", "Group A", null, null, 1),
    )

    fun uniqueScopeCollapsesMasterUrls() {
        val result = ExportPlanning.applyScope(rows, ExportScope.UNIQUE_LINKS)
        check(result.map { it.url }.toSet() == setOf("https://a.test", "https://b.test"))
        check(result.size == 2)
    }

    fun occurrenceScopePreservesEveryOccurrenceRow() {
        check(ExportPlanning.applyScope(rows, ExportScope.ALL_OCCURRENCES).size == 3)
    }

    fun perGroupPlanningProducesStableSafeFileKeys() {
        val planned = ExportPlanning.partition(rows, ExportGrouping.PER_GROUP)
        check(planned.keys == setOf("Group_A", "Group_B"))
        check(planned.getValue("Group_A").size == 2)
    }

    fun combinedPlanningUsesSingleKey() {
        val planned = ExportPlanning.partition(rows, ExportGrouping.COMBINED)
        check(planned.keys == setOf("all-groups"))
        check(planned.getValue("all-groups").size == 3)
    }
}
