package com.waalothmany.linkbot.whatsapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstanceInventoryPolicyTest {
    @Test
    fun missingPreviouslyKnownInstanceBecomesUnavailableInsteadOfDisappearing() {
        val merged = InstanceInventoryPolicy.reconcile(
            existing = listOf(
                InstanceInventoryItem("personal", "com.whatsapp", "WhatsApp", "PERSONAL", true, 10),
                InstanceInventoryItem("business", "com.whatsapp.w4b", "WhatsApp Business", "BUSINESS", true, 10),
            ),
            detected = listOf(
                InstanceInventoryItem("business", "com.whatsapp.w4b", "WhatsApp Business", "BUSINESS", true, 20),
            ),
            nowMs = 30,
        )
        assertTrue(merged.first { it.id == "business" }.enabled)
        assertFalse(merged.first { it.id == "personal" }.enabled)
    }
}
