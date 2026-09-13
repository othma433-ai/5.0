package com.waalothmany.linkbot.runtime

fun main() {
    val sanitized = DiagnosticSanitizer.sanitize("group opened\nhttps://example.com/private 967771234567")
    check(!sanitized.contains("https://")) { sanitized }
    check(!sanitized.contains("967771234567")) { sanitized }
    check(!sanitized.contains('\n'))
    check(sanitized.length <= 160)
    println("DiagnosticSanitizerSmoke: PASS")
}
