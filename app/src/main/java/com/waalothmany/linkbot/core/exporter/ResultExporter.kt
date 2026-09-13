package com.waalothmany.linkbot.core.exporter

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportRow(
    val url: String,
    val category: String,
    val group: String?,
    val sender: String?,
    val timestamp: String?,
    val occurrences: Int,
)

object ResultExporter {
    private val headers = listOf("URL", "Category", "Group", "Sender", "Timestamp", "Occurrences")

    fun csv(rows: List<ExportRow>): String = buildString {
        appendLine(headers.joinToString(","))
        rows.forEach { row ->
            appendLine(
                listOf(row.url, row.category, row.group.orEmpty(), row.sender.orEmpty(), row.timestamp.orEmpty(), row.occurrences.toString())
                    .joinToString(",") { csvCell(it) }
            )
        }
    }

    fun txt(rows: List<ExportRow>): String = buildString {
        rows.forEach { appendLine(it.url) }
    }

    fun json(rows: List<ExportRow>): String = rows.joinToString(prefix = "[", postfix = "]", separator = ",") { row ->
        "{\"url\":${jsonString(row.url)},\"category\":${jsonString(row.category)},\"group\":${jsonNullable(row.group)},\"sender\":${jsonNullable(row.sender)},\"timestamp\":${jsonNullable(row.timestamp)},\"occurrences\":${row.occurrences}}"
    }

    fun xlsx(rows: List<ExportRow>): ByteArray {
        val all = listOf(headers) + rows.map {
            listOf(it.url, it.category, it.group.orEmpty(), it.sender.orEmpty(), it.timestamp.orEmpty(), it.occurrences.toString())
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""")
            entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Links" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>""")
            val sheet = buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
                all.forEachIndexed { rIndex, cells ->
                    append("<row r=\"${rIndex + 1}\">")
                    cells.forEachIndexed { cIndex, value ->
                        val ref = "${columnName(cIndex)}${rIndex + 1}"
                        append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>")
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
            entry("xl/worksheets/sheet1.xml", sheet)
        }
        return out.toByteArray()
    }

    private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    private fun jsonNullable(value: String?): String = value?.let(::jsonString) ?: "null"
    private fun jsonString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")}\""
    private fun xml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private fun columnName(index: Int): String {
        var n = index + 1
        val out = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            out.append(('A'.code + r).toChar())
            n = (n - 1) / 26
        }
        return out.reverse().toString()
    }
}
