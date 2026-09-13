package com.waalothmany.linkbot.core.exporter

fun main() {
    val rows = listOf(
        ExportRow("https://example.com/a", "WEBSITE", "Group A", "Ahmed", "12/09/2026 10:00", 2),
        ExportRow("https://chat.whatsapp.com/XYZ", "WHATSAPP_GROUP", "Group B", null, null, 1),
    )
    val csv = ResultExporter.csv(rows)
    check(csv.contains("https://example.com/a"))
    val json = ResultExporter.json(rows)
    check(json.startsWith("["))
    val xlsx = ResultExporter.xlsx(rows)
    check(xlsx.size > 200)
    check(xlsx[0] == 'P'.code.toByte() && xlsx[1] == 'K'.code.toByte())
    println("ExporterSmoke: PASS")
}
