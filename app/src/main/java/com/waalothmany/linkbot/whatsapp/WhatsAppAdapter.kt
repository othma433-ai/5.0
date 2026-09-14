package com.waalothmany.linkbot.whatsapp

enum class AdapterPackageMode { EXACT, STRUCTURAL }

/** Variant-owned WhatsApp UI knowledge. Generic automation consumes only this contract. */
interface WhatsAppAdapter {
    val id: String
    val packageMode: AdapterPackageMode
    val groupFilterLabels: List<String>
    val allFilterLabels: List<String>
    val filterPeerLabels: List<String>
    val selectAllLabels: List<String>
    val resourceIdHints: Set<String>
    val requiresStructuralRowProof: Boolean

    val groupFilterIdSuffixes: Set<String>
        get() = setOf("conversations_filter_debug_view_id_groups")
    val allFilterIdSuffixes: Set<String>
        get() = setOf("conversations_filter_debug_view_id_all")
    val chatsAnchorIdSuffixes: Set<String>
        get() = setOf("chats", "tab_chats", "navigation_chats")
    val selectAllIdSuffixes: Set<String>
        get() = setOf("select_all", "action_select_all")
    val conversationRowTitleIdSuffixes: Set<String>
        get() = setOf("conversations_row_contact_name")

    fun resourceIdHintsFor(packageName: String, role: SelectorRole): List<String> = when (role) {
        SelectorRole.GROUPS_FILTER -> AdaptiveSelectorPolicy.buildHints(packageName, groupFilterIdSuffixes)
        SelectorRole.ALL_FILTER -> AdaptiveSelectorPolicy.buildHints(packageName, allFilterIdSuffixes)
        SelectorRole.CHATS_ANCHOR -> AdaptiveSelectorPolicy.buildHints(packageName, chatsAnchorIdSuffixes)
        SelectorRole.SELECT_ALL -> AdaptiveSelectorPolicy.buildHints(packageName, selectAllIdSuffixes)
        SelectorRole.CONVERSATION_ROW_TITLE -> AdaptiveSelectorPolicy.buildHints(packageName, conversationRowTitleIdSuffixes)
    }

    fun matches(packageName: String, applicationLabel: String): Boolean
}

abstract class OfficialWhatsAppAdapter(
    private val exactPackage: String,
    override val id: String,
) : WhatsAppAdapter {
    override val packageMode: AdapterPackageMode = AdapterPackageMode.EXACT
    override val groupFilterLabels = listOf("Groups", "المجموعات", "Group chats", "دردشات المجموعات", "Filter by Groups", "Groups filter", "Show Groups", "تصفية حسب المجموعات", "فلتر المجموعات", "إظهار المجموعات")
    override val allFilterLabels = listOf("All", "الكل")
    override val filterPeerLabels = listOf(
        "All", "الكل", "Unread", "غير المقروءة", "Favorites", "المفضلة", "Groups", "المجموعات"
    )
    override val selectAllLabels = listOf("Select all", "Select All", "تحديد الكل")
    override val resourceIdHints: Set<String> = setOf(
        "$exactPackage:id/conversations_filter_debug_view_id_groups",
        "$exactPackage:id/conversations_filter_debug_view_id_all",
        "$exactPackage:id/conversations_row_contact_name",
        "$exactPackage:id/conversations_row_message_count",
        "$exactPackage:id/conversations_row_date",
    )
    override val requiresStructuralRowProof: Boolean = true

    override fun matches(packageName: String, applicationLabel: String): Boolean = packageName == exactPackage
}

object OfficialPersonalWhatsAppAdapter : OfficialWhatsAppAdapter(
    exactPackage = "com.whatsapp",
    id = "official-personal",
)

object OfficialBusinessWhatsAppAdapter : OfficialWhatsAppAdapter(
    exactPackage = "com.whatsapp.w4b",
    id = "official-business",
)

object GenericDiscoverableWhatsAppAdapter : WhatsAppAdapter {
    override val id: String = "generic-discoverable"
    override val packageMode: AdapterPackageMode = AdapterPackageMode.STRUCTURAL
    override val groupFilterLabels = listOf("Groups", "المجموعات", "Group chats", "دردشات المجموعات", "Filter by Groups", "Groups filter", "Show Groups", "تصفية حسب المجموعات", "فلتر المجموعات", "إظهار المجموعات")
    override val allFilterLabels = listOf("All", "الكل")
    override val filterPeerLabels = listOf(
        "All", "الكل", "Unread", "غير المقروءة", "Favorites", "المفضلة", "Groups", "المجموعات"
    )
    override val selectAllLabels = listOf("Select all", "Select All", "تحديد الكل")
    override val resourceIdHints: Set<String> = emptySet()
    override val requiresStructuralRowProof: Boolean = true

    override fun matches(packageName: String, applicationLabel: String): Boolean =
        PackageCandidatePolicy.isWhatsAppCandidate(packageName, applicationLabel)
}
