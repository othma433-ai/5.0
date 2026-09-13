package com.waalothmany.linkbot.core.exporter

import org.junit.Assert.assertTrue
import org.junit.Test

class ResultExporterTest {
    @Test fun creates_valid_zip_based_xlsx_container() {
        val bytes = ResultExporter.xlsx(listOf(ExportRow("https://example.com", "WEBSITE", "A", null, null, 1)))
        assertTrue(bytes.size > 200)
        assertTrue(bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte())
    }
}
