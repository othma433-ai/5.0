package com.waalothmany.linkbot.core.exporter

enum class ExportFormat(val extension: String, val mime: String) {
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CSV("csv", "text/csv"),
    TXT("txt", "text/plain"),
    JSON("json", "application/json"),
}

enum class ExportScope { UNIQUE_LINKS, ALL_OCCURRENCES }
enum class ExportGrouping { COMBINED, PER_GROUP }

data class ExportOptions(
    val format: ExportFormat = ExportFormat.XLSX,
    val scope: ExportScope = ExportScope.UNIQUE_LINKS,
    val grouping: ExportGrouping = ExportGrouping.COMBINED,
    val autoAfterSession: Boolean = false,
)

object ExportPlanning {
    fun applyScope(rows: List<ExportRow>, scope: ExportScope): List<ExportRow> = when (scope) {
        ExportScope.ALL_OCCURRENCES -> rows
        ExportScope.UNIQUE_LINKS -> rows
            .groupBy { it.url }
            .values
            .map { occurrences ->
                val first = occurrences.first()
                first.copy(
                    group = null,
                    sender = null,
                    timestamp = null,
                    occurrences = occurrences.maxOfOrNull { it.occurrences } ?: first.occurrences,
                )
            }
            .sortedBy { it.url.lowercase() }
    }

    fun partition(rows: List<ExportRow>, grouping: ExportGrouping): Map<String, List<ExportRow>> = when (grouping) {
        ExportGrouping.COMBINED -> linkedMapOf("all-groups" to rows)
        ExportGrouping.PER_GROUP -> rows
            .groupBy { safeFileKey(it.group ?: "ungrouped") }
            .toSortedMap()
    }

    fun safeFileKey(value: String): String = value
        .trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .take(80)
        .ifBlank { "ungrouped" }
}
