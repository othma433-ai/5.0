package com.waalothmany.linkbot.whatsapp

object WhatsAppAdapterRegistry {
    private val ordered: List<WhatsAppAdapter> = listOf(
        OfficialPersonalWhatsAppAdapter,
        OfficialBusinessWhatsAppAdapter,
        GenericDiscoverableWhatsAppAdapter,
    )

    fun resolve(packageName: String, applicationLabel: String): WhatsAppAdapter =
        ordered.firstOrNull { it.matches(packageName, applicationLabel) }
            ?: GenericDiscoverableWhatsAppAdapter

    fun byId(id: String): WhatsAppAdapter = ordered.firstOrNull { it.id == id }
        ?: GenericDiscoverableWhatsAppAdapter
}
