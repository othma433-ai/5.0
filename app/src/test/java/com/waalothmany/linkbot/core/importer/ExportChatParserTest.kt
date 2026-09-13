package com.waalothmany.linkbot.core.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportChatParserTest {
    @Test fun parses_multiline_whatsapp_messages() {
        val messages = ExportChatParser.parse(
            """
            12/09/2026, 4:32 PM - Ahmed: Lecture
            https://drive.google.com/file/d/ABC
            12/09/2026, 4:35 PM - Ali: Group https://chat.whatsapp.com/XYZ
            continued line
            """.trimIndent()
        )
        assertEquals(2, messages.size)
        assertEquals("Ahmed", messages[0].sender)
        assertTrue(messages[0].text.contains("drive.google.com"))
        assertTrue(messages[1].text.contains("continued line"))
    }
}
