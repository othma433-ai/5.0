package com.waalothmany.linkbot.core.exporter

import com.waalothmany.linkbot.ServiceLocator

enum class ExportFormat(val extension: String, val mime: String) {
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CSV("csv", "text/csv"),
    TXT("txt", "text/plain"),
    JSON("json", "application/json"),
}

object ExportUseCase {
    suspend fun build(format: ExportFormat): ByteArray {
        val rows = ServiceLocator.database.exportDao().occurrences().map {
            ExportRow(it.url, it.category, it.groupTitle, it.sender, it.timestampRaw, it.occurrenceCount)
        }
        return when (format) {
            ExportFormat.XLSX -> ResultExporter.xlsx(rows)
            ExportFormat.CSV -> ResultExporter.csv(rows).toByteArray()
            ExportFormat.TXT -> ResultExporter.txt(rows).toByteArray()
            ExportFormat.JSON -> ResultExporter.json(rows).toByteArray()
        }
    }
}
