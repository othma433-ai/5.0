package com.waalothmany.linkbot.core.link

fun main() {
    val text = "Lecture https://drive.google.com/file/d/ABC and group https://chat.whatsapp.com/Invite123. Duplicate: https://drive.google.com/file/d/ABC and example.com/path"
    val found = LinkExtractor.extract(text)
    check(found.size == 3) { "expected three unique links, got ${found.size}" }
    check(found.any { it.category == LinkCategory.GOOGLE_DRIVE })
    check(found.any { it.category == LinkCategory.WHATSAPP_GROUP })
    check(found.any { it.canonicalUrl == "https://example.com/path" })
    check(UrlNormalizer.normalize("https://Example.com/") == "https://example.com")
    check(LinkClassifier.classify("https://t.me/example") == LinkCategory.TELEGRAM)
    check(UrlNormalizer.normalize("https://Example.com/page?id=5&utm_source=chat&utm_medium=share") == "https://example.com/page?id=5")
    check(UrlNormalizer.normalize("https://youtu.be/abc123?si=TRACK&feature=shared") == "https://youtu.be/abc123")
    check(UrlNormalizer.normalize("https://mega.nz/file/abc#SECRETKEY") == "https://mega.nz/file/abc#SECRETKEY")
    check(UrlNormalizer.normalize("https://en.wikipedia.org/wiki/Function_(mathematics)") == "https://en.wikipedia.org/wiki/Function_(mathematics)")
    check(UrlNormalizer.normalize("https://example.com/path)") == "https://example.com/path")
    val arabic = LinkExtractor.extract("رابط: https://example.com/test، ثم https://x.com/openai؟")
    check(arabic.map { it.canonicalUrl } == listOf("https://example.com/test", "https://x.com/openai")) { arabic.toString() }
    println("LinkEngineSmoke: PASS")
}
