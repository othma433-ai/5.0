package com.waalothmany.linkbot.whatsapp

fun main() {
    val personal = WhatsAppAdapterRegistry.resolve("com.whatsapp", "WhatsApp")
    check(personal.id == "official-personal")
    check(personal.packageMode == AdapterPackageMode.EXACT)
    check(personal.groupFilterLabels.any { it.equals("Groups", ignoreCase = true) })
    check(personal.resourceIdHints.isNotEmpty())
    check("conversations_filter_debug_view_id_groups" in personal.groupFilterIdSuffixes)
    check("conversations_filter_debug_view_id_all" in personal.allFilterIdSuffixes)
    check(personal.groupFilterLabels.any { it.equals("Filter by Groups", ignoreCase = true) })

    val business = WhatsAppAdapterRegistry.resolve("com.whatsapp.w4b", "WhatsApp Business")
    check(business.id == "official-business")
    check(business.packageMode == AdapterPackageMode.EXACT)

    val generic = WhatsAppAdapterRegistry.resolve("com.gbwhatsapp", "GBWhatsApp")
    check(generic.id == "generic-discoverable")
    check(generic.packageMode == AdapterPackageMode.STRUCTURAL)
    check(generic.resourceIdHints.isEmpty()) { "generic adapter must not depend on official full resource ids" }
    check("conversations_filter_debug_view_id_groups" in generic.groupFilterIdSuffixes) { "generic adapter must still use package-neutral stable suffixes" }
    check(generic.requiresStructuralRowProof)

    println("WhatsAppAdapterRegistrySmoke: PASS")
}
