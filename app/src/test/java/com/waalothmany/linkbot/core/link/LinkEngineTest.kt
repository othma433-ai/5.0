package com.waalothmany.linkbot.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkEngineTest {
    @Test fun extracts_normalizes_classifies_and_deduplicates() {
        val result = LinkExtractor.extract(
            "Drive https://drive.google.com/file/d/ABC group https://chat.whatsapp.com/XYZ duplicate https://drive.google.com/file/d/ABC and example.com/path"
        )
        assertEquals(3, result.size)
        assertTrue(result.any { it.category == LinkCategory.GOOGLE_DRIVE })
        assertTrue(result.any { it.category == LinkCategory.WHATSAPP_GROUP })
        assertTrue(result.any { it.canonicalUrl == "https://example.com/path" })
    }

    @Test fun normalizer_removes_trailing_root_slash_and_normalizes_host_case() {
        assertEquals("https://example.com", UrlNormalizer.normalize("https://Example.com/"))
    }
}
