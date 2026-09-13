package com.waalothmany.linkbot.core.importer

fun main() {
    val sample = """
        12/09/2026, 4:32 PM - Ahmed: Lecture
        https://drive.google.com/file/d/ABC
        12/09/2026, 4:35 PM - Ali: Group https://chat.whatsapp.com/XYZ
        continued line
    """.trimIndent()
    val messages = ExportChatParser.parse(sample)
    check(messages.size == 2) { "expected 2 messages, got ${messages.size}" }
    check(messages[0].sender == "Ahmed")
    check(messages[0].text.contains("drive.google.com"))
    check(messages[1].text.contains("continued line"))

    val withSystem = ExportChatParser.parse("""
        12/09/2026, 4:32 PM - Ahmed: before
        12/09/2026, 4:33 PM - Messages and calls are end-to-end encrypted.
        12/09/2026, 4:34 PM - Ali: after
    """.trimIndent())
    check(withSystem.size == 3) { "system line must form its own message boundary: $withSystem" }
    check(withSystem[1].sender == null)
    check(withSystem[1].text.contains("end-to-end encrypted"))
    println("ExportChatParserSmoke: PASS")
}
