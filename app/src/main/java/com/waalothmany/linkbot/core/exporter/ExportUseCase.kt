package com.waalothmany.linkbot.core.exporter

import com.waalothmany.linkbot.ServiceLocator

data class ExportArtifact(
    val fileStem: String,
    val format: ExportFormat,
    val bytes: ByteArray,
)

object ExportUseCase {
    suspend fun build(format: ExportFormat): ByteArray = buildArtifacts(
        ExportOptions(format = format, scope = ExportScope.ALL_OCCURRENCES, grouping = ExportGrouping.COMBINED)
    ).single().bytes

    suspend fun buildArtifacts(options: ExportOptions): List<ExportArtifact> {
        val sourceRows = ServiceLocator.database.exportDao().occurrences().map {
            ExportRow(it.url, it.category, it.groupTitle, it.sender, it.timestampRaw, it.occurrenceCount)
        }
        val partitions = when (options.grouping) {
            ExportGrouping.COMBINED -> mapOf("all-groups" to ExportPlanning.applyScope(sourceRows, options.scope))
            ExportGrouping.PER_GROUP -> ExportPlanning.partition(sourceRows, ExportGrouping.PER_GROUP)
                .mapValues { (_, rows) -> ExportPlanning.applyScope(rows, options.scope) }
        }
        return partitions.map { (key, rows) ->
            ExportArtifact(
                fileStem = "wa-links-$key",
                format = options.format,
                bytes = serialize(options.format, rows),
            )
        }
    }

    private fun serialize(format: ExportFormat, rows: List<ExportRow>): ByteArray = when (format) {
        ExportFormat.XLSX -> ResultExporter.xlsx(rows)
        ExportFormat.CSV -> ResultExporter.csv(rows).toByteArray()
        ExportFormat.TXT -> ResultExporter.txt(rows).toByteArray()
        ExportFormat.JSON -> ResultExporter.json(rows).toByteArray()
    }
}
