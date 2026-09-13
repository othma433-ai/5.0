package com.waalothmany.linkbot.automation

import android.os.Build
import android.os.Bundle
import android.text.Spanned
import android.text.style.URLSpan
import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest

object AccessibilityTree {
    private val DEFAULT_GROUP_LABELS = listOf("Groups", "المجموعات", "Group chats", "دردشات المجموعات")
    private val DEFAULT_GROUP_ID_HINTS = listOf("group_filter", "filter_groups", "groups_filter", "chat_filter_group")

    private val systemLabels = setOf(
        "search", "بحث", "new chat", "دردشة جديدة", "archived", "المؤرشفة", "communities", "المجتمعات",
        "settings", "الإعدادات", "calls", "المكالمات", "updates", "التحديثات", "chats", "الدردشات",
        "groups", "المجموعات", "all", "الكل", "unread", "غير المقروءة", "favorites", "المفضلة",
        "locked chats", "الدردشات المقفلة", "new group", "مجموعة جديدة", "manage groups", "إدارة المجموعات",
        "new community", "مجتمع جديد", "channels", "القنوات", "broadcast lists", "قوائم البث",
    )

    fun flatten(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val result = ArrayList<AccessibilityNodeInfo>(256)
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            result += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(stack::add)
        }
        return result
    }

    fun texts(root: AccessibilityNodeInfo?): List<String> = flatten(root)
        .flatMap { node -> listOfNotNull(node.text?.toString(), node.contentDescription?.toString()) }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

    fun findByAnyText(root: AccessibilityNodeInfo?, labels: Collection<String>): AccessibilityNodeInfo? {
        val wanted = labels.map { it.lowercase() }.toSet()
        return flatten(root).firstOrNull { node ->
            val candidates = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            candidates.any { it.trim().lowercase() in wanted }
        }
    }

    fun findByViewIdHints(root: AccessibilityNodeInfo?, hints: Collection<String>): AccessibilityNodeInfo? {
        val lowered = hints.map { it.lowercase() }
        return flatten(root).firstOrNull { node ->
            val id = node.viewIdResourceName.orEmpty().lowercase()
            lowered.any { hint -> id.contains(hint) }
        }
    }

    fun filterEvidence(root: AccessibilityNodeInfo?, labels: Collection<String>, idHints: Collection<String>): FilterEvidence {
        val node = findByViewIdHints(root, idHints) ?: findByAnyText(root, labels) ?: return FilterEvidence.UNKNOWN
        val chain = generateSequence(node as AccessibilityNodeInfo?) { it.parent }.take(4).toList()
        if (chain.any { it.isSelected || it.isChecked }) return FilterEvidence.ACTIVE

        val stateText = chain.flatMap { candidate ->
            buildList {
                candidate.contentDescription?.toString()?.let(::add)
                if (Build.VERSION.SDK_INT >= 30) candidate.stateDescription?.toString()?.let(::add)
            }
        }.joinToString(" ").lowercase()
        if (stateText.contains("selected") || stateText.contains("active") || stateText.contains("محدد") || stateText.contains("مفعّل")) {
            return FilterEvidence.ACTIVE
        }
        if (chain.any { it.isCheckable }) return FilterEvidence.INACTIVE
        return FilterEvidence.UNKNOWN
    }

    fun findEditable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? = flatten(root).firstOrNull {
        it.isEditable || it.className?.toString()?.contains("EditText") == true
    }

    fun click(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        repeat(6) {
            if (current == null) return false
            if (current!!.isClickable && current!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            current = current!!.parent
        }
        return false
    }

    fun setText(node: AccessibilityNodeInfo?, value: String): Boolean {
        node ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun bestScrollable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        val candidates = flatten(root).filter { it.isScrollable }
        return candidates.maxByOrNull { candidate ->
            var score = candidate.childCount * 10
            for (i in 0 until candidate.childCount) {
                val child = candidate.getChild(i) ?: continue
                score += collectText(child).size
            }
            score
        }
    }

    fun bestConversationScrollable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        val candidates = flatten(root).filter { it.isScrollable }
        return candidates.maxByOrNull { containerRoleScore(it).conversationScore }
            ?.takeIf { containerRoleScore(it).conversationScore >= 35 }
            ?: bestScrollable(root)
    }

    fun bestMessageScrollable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        val candidates = flatten(root).filter { it.isScrollable }
        return candidates.maxByOrNull { containerRoleScore(it).messageScore }
            ?.takeIf { containerRoleScore(it).messageScore >= 35 }
            ?: bestScrollable(root)
    }

    private fun containerRoleScore(container: AccessibilityNodeInfo): ContainerRoleScore {
        var conversationRows = 0
        var textChildren = 0
        var urlChildren = 0
        for (i in 0 until container.childCount) {
            val child = container.getChild(i) ?: continue
            val values = collectText(child)
            if (values.isNotEmpty()) textChildren++
            if (values.any { it.contains("https://", ignoreCase = true) || it.contains("http://", ignoreCase = true) }) urlChildren++
            val title = titleFor(child, values)
            if (title != null) {
                val subtree = flatten(child)
                val viewIds = subtree.mapNotNull { it.viewIdResourceName }
                val subtreeClickable = subtree.any { it !== child && it.isClickable }
                if (RowClassificationPolicy.isLikelyConversationRow(title, child.isClickable, subtreeClickable, viewIds)) {
                    conversationRows++
                }
            }
        }
        return ContainerRolePolicy.score(
            ContainerSignals(
                childCount = container.childCount,
                conversationRows = conversationRows,
                textChildren = textChildren,
                urlChildren = urlChildren,
            )
        )
    }

    fun collectText(node: AccessibilityNodeInfo?): List<String> {
        if (node == null) return emptyList()
        val out = ArrayList<String>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(node)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            val nodeText = n.text
            nodeText?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(out::add)
            if (nodeText is Spanned) {
                nodeText.getSpans(0, nodeText.length, URLSpan::class.java)
                    .mapNotNull { it.url?.trim()?.takeIf(String::isNotBlank) }
                    .forEach(out::add)
            }
            n.contentDescription?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(out::add)
            for (i in 0 until n.childCount) n.getChild(i)?.let(stack::add)
        }
        return out.distinct()
    }

    fun rowCandidates(root: AccessibilityNodeInfo?): List<RowCandidate> {
        val container = bestConversationScrollable(root) ?: return emptyList()
        val rows = ArrayList<RowCandidate>()
        for (i in 0 until container.childCount) {
            val child = container.getChild(i) ?: continue
            val allText = collectText(child)
            if (allText.isEmpty()) continue
            val title = titleFor(child, allText) ?: continue
            val subtree = flatten(child)
            val viewIds = subtree.mapNotNull { it.viewIdResourceName }
            val subtreeClickable = subtree.any { it !== child && it.isClickable }
            if (!RowClassificationPolicy.isLikelyConversationRow(title, child.isClickable, subtreeClickable, viewIds)) continue

            val unread = RowClassificationPolicy.isUnread(allText)
            val unreadCount = if (unread) RowClassificationPolicy.extractUnreadCount(allText) else null
            rows += RowCandidate(
                title = title,
                unread = unread,
                unreadCount = unreadCount,
                active = RowClassificationPolicy.isActive(allText),
                preview = RowClassificationPolicy.pickPreview(title, allText),
                rowFingerprint = structureFingerprint(child),
            )
        }
        // Do not collapse rows by title/structure: two real groups may legitimately share both.
        // Cross-viewport reconciliation in the sync engine handles overlap and identity.
        return rows
    }

    fun viewportFingerprint(root: AccessibilityNodeInfo?): String = sha256(texts(bestConversationScrollable(root)).joinToString("\u001f"))

    fun messageViewport(container: AccessibilityNodeInfo?): MessageViewportSnapshot {
        if (container == null) return MessageViewportSnapshot(emptyList(), emptyList(), false)
        val direct = ArrayList<MessageBatch>()
        val ordered = ArrayList<ViewportMessage>()
        var unreadMarkerPresent = false
        for (i in 0 until container.childCount) {
            val child = container.getChild(i) ?: continue
            val values = collectText(child)
            if (isUnreadMarker(values)) {
                unreadMarkerPresent = true
                ordered += ViewportMessage(fingerprint = null, unreadMarker = true)
                continue
            }
            val text = values.joinToString("\n").trim()
            if (text.isBlank()) continue
            val batch = MessageBatch(text = text, fingerprint = sha256(normalizeContent(text)))
            direct += batch
            ordered += ViewportMessage(fingerprint = batch.fingerprint, unreadMarker = false)
        }
        if (direct.isNotEmpty()) {
            val distinct = LinkedHashMap<String, MessageBatch>()
            direct.forEach { distinct.putIfAbsent(it.fingerprint, it) }
            val allowed = distinct.keys
            return MessageViewportSnapshot(
                batches = distinct.values.toList(),
                orderedItems = ordered.filter { it.unreadMarker || it.fingerprint in allowed },
                unreadMarkerPresent = unreadMarkerPresent,
            )
        }
        val fallback = collectText(container).joinToString("\n").trim()
        if (fallback.isBlank()) return MessageViewportSnapshot(emptyList(), ordered, unreadMarkerPresent)
        val batch = MessageBatch(text = fallback, fingerprint = sha256(normalizeContent(fallback)))
        return MessageViewportSnapshot(
            batches = listOf(batch),
            orderedItems = listOf(ViewportMessage(batch.fingerprint, false)),
            unreadMarkerPresent = unreadMarkerPresent,
        )
    }

    fun messageBatches(container: AccessibilityNodeInfo?): List<MessageBatch> = messageViewport(container).batches

    private fun isUnreadMarker(values: List<String>): Boolean {
        if (values.isEmpty() || values.size > 4) return false
        val normalized = values.joinToString(" ") { it.trim().lowercase() }.replace(Regex("\\s+"), " ")
        return normalized == "unread messages" || normalized == "new messages" ||
            normalized == "رسائل غير مقروءة" || normalized == "رسائل غير مقروءه" ||
            normalized == "رسائل جديدة" || normalized == "رسائل جديده" ||
            normalized.startsWith("unread messages ") || normalized.startsWith("رسائل غير مقرو")
    }

    private fun normalizeContent(value: String): String = value
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString("\n")


    fun screenEvidence(root: AccessibilityNodeInfo?, expectedTitle: String? = null): ScreenEvidence {
        if (root == null) return ScreenEvidence(ScreenKind.UNKNOWN, 0, listOf("no-root"))
        val nodes = flatten(root)
        val composerVisible = nodes.any { node ->
            val id = node.viewIdResourceName.orEmpty().lowercase()
            id.contains("conversation_entry") || id.contains("message_input") || id.endsWith("/entry") || id.endsWith("_entry") ||
                id.endsWith("/send") || id.contains("send_button")
        }
        val editableNodes = nodes.filter { it.isEditable || it.className?.toString()?.contains("EditText") == true }
        val searchEditorVisible = editableNodes.any { node ->
            val id = node.viewIdResourceName.orEmpty().lowercase()
            val label = listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).joinToString(" ").lowercase()
            id.contains("search") || label == "search" || label == "بحث"
        } || (editableNodes.isNotEmpty() && !composerVisible)
        val groupsFilterVisible = findByViewIdHints(root, DEFAULT_GROUP_ID_HINTS) != null || findByAnyText(root, DEFAULT_GROUP_LABELS) != null
        val groupEvidence = filterEvidence(root, DEFAULT_GROUP_LABELS, DEFAULT_GROUP_ID_HINTS)
        val expectedTitleVisible = expectedTitle?.let { exactText(root, it) != null } ?: false
        val rowCount = if (!composerVisible) rowCandidates(root).size else 0
        return ScreenEvidencePolicy.classify(
            ScreenSignals(
                groupsFilterVisible = groupsFilterVisible,
                groupsFilterActive = groupEvidence == FilterEvidence.ACTIVE,
                conversationRowCount = rowCount,
                searchEditorVisible = searchEditorVisible,
                composerVisible = composerVisible,
                expectedTitleVisible = expectedTitleVisible,
                scrollableCount = nodes.count { it.isScrollable },
            )
        )
    }


    fun isLikelyChatScreen(root: AccessibilityNodeInfo?, expectedTitle: String): Boolean {
        if (root == null || exactText(root, expectedTitle) == null) return false
        val nodes = flatten(root)
        val searchEditorPresent = nodes.any { node ->
            val id = node.viewIdResourceName.orEmpty().lowercase()
            (node.isEditable || node.className?.toString()?.contains("EditText") == true) && id.contains("search")
        }
        if (searchEditorPresent) return false
        val composerEvidence = nodes.any { node ->
            val id = node.viewIdResourceName.orEmpty().lowercase()
            id.contains("conversation_entry") || id.contains("message_input") || id.endsWith("/entry") || id.endsWith("_entry") || id.endsWith("/send") || id.contains("send_button")
        }
        return composerEvidence || bestScrollable(root) != null
    }

    fun exactText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? = flatten(root).firstOrNull { node ->
        node.text?.toString()?.trim()?.equals(text.trim(), ignoreCase = true) == true
    }

    fun searchResultResolution(root: AccessibilityNodeInfo?, title: String, preview: String?): SearchNodeResolution {
        val list = bestConversationScrollable(root) ?: run {
            val fallback = exactText(root, title)
            return if (fallback != null) SearchNodeResolution.Unique(fallback) else SearchNodeResolution.None
        }
        val rows = (0 until list.childCount).mapNotNull(list::getChild)
        if (rows.isEmpty()) return SearchNodeResolution.None
        val evidence = rows.map { row ->
            val text = collectText(row)
            SearchRowEvidence(
                title = titleFor(row, text).orEmpty(),
                preview = RowClassificationPolicy.pickPreview(titleFor(row, text).orEmpty(), text),
                unreadCount = RowClassificationPolicy.extractUnreadCount(text),
            )
        }
        return when (val resolution = SearchResolutionPolicy.resolve(title, preview, evidence)) {
            is SearchResolution.Unique -> SearchNodeResolution.Unique(rows[resolution.index])
            is SearchResolution.Ambiguous -> SearchNodeResolution.Ambiguous(resolution.count)
            SearchResolution.None -> SearchNodeResolution.None
        }
    }

    @Deprecated("Use searchResultResolution so duplicate titles fail safely")
    fun searchResult(root: AccessibilityNodeInfo?, title: String, preview: String?): AccessibilityNodeInfo? =
        (searchResultResolution(root, title, preview) as? SearchNodeResolution.Unique)?.node

    private fun titleFor(row: AccessibilityNodeInfo, fallback: List<String>): String? {
        val preferred = flatten(row).firstOrNull { node ->
            val id = node.viewIdResourceName.orEmpty().lowercase()
            val value = node.text?.toString()?.trim().orEmpty()
            value.isNotBlank() && (id.contains("contact_name") || id.endsWith("/name") || id.contains("chat_title") || id.contains("conversation_name"))
        }?.text?.toString()?.trim()
        return preferred ?: fallback.firstOrNull { value ->
            val n = value.lowercase()
            n !in systemLabels && !RowClassificationPolicy.isSystemLabel(value) && !looksLikeTime(value) && !looksLikeCount(value)
        }
    }

    private fun looksLikeTime(value: String): Boolean = Regex("^\\d{1,2}:\\d{2}.*$").matches(value.trim())
    private fun looksLikeCount(value: String): Boolean = Regex("^\\d{1,4}$").matches(value.trim())

    private fun structureFingerprint(node: AccessibilityNodeInfo): String {
        val parts = flatten(node).map { n ->
            val id = n.viewIdResourceName.orEmpty().substringAfterLast('/')
            "${n.className}|$id|${n.isClickable}|${n.isCheckable}"
        }
        return sha256(parts.joinToString(";"))
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

data class RowCandidate(
    val title: String,
    val unread: Boolean,
    val unreadCount: Int?,
    val active: Boolean,
    val preview: String?,
    val rowFingerprint: String,
)


data class MessageBatch(
    val text: String,
    val fingerprint: String,
)

data class MessageViewportSnapshot(
    val batches: List<MessageBatch>,
    val orderedItems: List<ViewportMessage>,
    val unreadMarkerPresent: Boolean,
)

sealed interface SearchNodeResolution {
    data class Unique(val node: AccessibilityNodeInfo) : SearchNodeResolution
    data class Ambiguous(val count: Int) : SearchNodeResolution
    data object None : SearchNodeResolution
}
