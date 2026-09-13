package com.waalothmany.linkbot.automation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.waalothmany.linkbot.ServiceLocator
import com.waalothmany.linkbot.core.link.LinkExtractor
import com.waalothmany.linkbot.data.BotSessionEntity
import com.waalothmany.linkbot.data.GroupEntity
import com.waalothmany.linkbot.data.QueueItemEntity
import com.waalothmany.linkbot.runtime.BotForegroundService
import com.waalothmany.linkbot.runtime.DiagnosticLog
import com.waalothmany.linkbot.runtime.BotRuntime
import com.waalothmany.linkbot.runtime.RuntimePhase
import com.waalothmany.linkbot.runtime.RuntimeSnapshot
import com.waalothmany.linkbot.runtime.ThroughputMeter
import com.waalothmany.linkbot.whatsapp.WhatsAppInstanceDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class WaAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var operation: Operation = Operation.None
    private var targetPackage: String? = null
    private var targetInstanceId: String? = null
    private var syncId: String? = null
    private val syncSeen = LinkedHashMap<String, GroupEntity>()
    private var syncExistingGroups: List<GroupEntity> = emptyList()
    private var syncExistingIdentityByTitle: Map<String, List<GroupIdentityRecord>> = emptyMap()
    private val syncNewOrdinalByTitle = HashMap<String, Int>()
    private val syncDiscoveredIdentityByTitle = HashMap<String, MutableList<GroupIdentityRecord>>()
    private var syncPreviousViewport: List<ViewportIdentity> = emptyList()
    private var syncEndGuard = EndOfListGuard()
    private var syncRequestedFingerprint: String? = null
    private var syncProbeJob: Job? = null
    private var filterVerifyAttempts: Int = 0
    private var extractionSessionId: String? = null
    private var extractionMode: AutomationMode = AutomationMode.DEEP
    private var currentGroup: GroupEntity? = null
    private var currentQueue: QueueItemEntity? = null
    private var viewportStartFingerprint: String? = null
    private var newestViewportFingerprint: String? = null
    private var previousViewportFingerprint: String? = null
    private var extractionEndGuard = EndOfListGuard()
    private var extractionRequestedFingerprint: String? = null
    private var extractionProbeJob: Job? = null
    private var groupsCompleted = 0
    private var totalQueue = 0
    private var stageWatchdogJob: Job? = null
    private val eventGuard = AtomicBoolean(false)
    private val prepareInFlight = AtomicBoolean(false)
    private var syncTiming = AdaptiveTimingPolicy(PerformanceProfiles.forMode(PerformanceMode.BALANCED))
    private var extractionTiming = AdaptiveTimingPolicy(PerformanceProfiles.forMode(PerformanceMode.BALANCED))
    private var syncActionIssuedAt: Long? = null
    private var extractionActionIssuedAt: Long? = null
    private val stageCircuitBreaker = StageCircuitBreaker(maxConsecutiveFailures = 3)
    private val automationHealth = AutomationHealthPolicy()
    private val throughputMeter = ThroughputMeter()
    private var effectiveMode: PerformanceMode = PerformanceMode.BALANCED
    private var extractionSeenMessageFingerprints: Set<String> = emptySet()
    private val viewportCommitInFlight = AtomicBoolean(false)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        DiagnosticLog.record("ACCESSIBILITY_CONNECTED")
        scope.launch {
            val active = ServiceLocator.database.sessionDao().active()
            if (active != null && active.type == "EXTRACTION") {
                val instanceEntity = ServiceLocator.database.instanceDao().get(active.instanceId)
                if (instanceEntity != null) {
                    extractionSessionId = active.id
                    targetInstanceId = active.instanceId
                    targetPackage = instanceEntity.packageName
                    extractionMode = runCatching { AutomationMode.valueOf(active.mode) }.getOrDefault(AutomationMode.NEW_ONLY)
                    val recoveredQueue = ServiceLocator.database.queueDao().forSession(active.id)
                    val progress = QueueProgressPolicy.summarize(recoveredQueue.map { it.state })
                    totalQueue = progress.total
                    groupsCompleted = progress.completed
                    resetAdaptiveSession()
                    BotRuntime.pause()
                    setExtractStage(ExtractStage.PREPARE_LIST)
                    BotRuntime.update(
                        RuntimeSnapshot(
                            RuntimePhase.PAUSED,
                            "Resume available",
                            "Recovered ${progress.completed}/${progress.total} groups • ${progress.failed} failed",
                            progress.completed,
                            progress.total,
                        )
                    )
                } else {
                    BotRuntime.update(RuntimeSnapshot(phase = RuntimePhase.READY, title = "Accessibility ready"))
                }
            } else {
                BotRuntime.update(RuntimeSnapshot(phase = RuntimePhase.READY, title = "Accessibility ready"))
            }
        }
    }

    override fun onDestroy() {
        stageWatchdogJob?.cancel()
        syncProbeJob?.cancel()
        extractionProbeJob?.cancel()
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {
        BotRuntime.update(BotRuntime.state.value.copy(phase = RuntimePhase.ERROR, title = "Accessibility interrupted"))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val wanted = targetPackage ?: return
        if (pkg != wanted) return
        if (BotRuntime.stopRequested) {
            finishStopped()
            return
        }
        if (BotRuntime.pauseRequested) return
        if (!eventGuard.compareAndSet(false, true)) return
        try {
            val root = rootInActiveWindow ?: return
            when (val op = operation) {
                is Operation.Sync -> handleSync(root, event.eventType)
                is Operation.Extract -> handleExtraction(root, event.eventType)
                Operation.None -> Unit
            }
        } finally {
            eventGuard.set(false)
        }
    }


    fun pauseAutomation() {
        BotRuntime.pause()
        stageWatchdogJob?.cancel()
        val session = extractionSessionId ?: return
        scope.launch {
            ServiceLocator.database.sessionDao().updateState(session, "PAUSED")
            currentQueue?.let { ServiceLocator.database.queueDao().updateState(it.id, "PAUSED", it.attempts, null) }
        }
    }

    fun resumeAutomation() {
        BotRuntime.resume()
        armCurrentStageWatchdog()
        val session = extractionSessionId
        scope.launch {
            if (session != null) {
                ServiceLocator.database.sessionDao().updateState(session, "RUNNING")
                currentQueue?.let { ServiceLocator.database.queueDao().updateState(it.id, "SCANNING", it.attempts, null) }
            }
            targetPackage?.let { WhatsAppInstanceDetector.launch(this@WaAccessibilityService, it) }
            rootInActiveWindow?.let { root ->
                when (operation) {
                    is Operation.Sync -> handleSync(root, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                    is Operation.Extract -> handleExtraction(root, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                    Operation.None -> Unit
                }
            }
        }
    }

    fun stopAutomation() {
        BotRuntime.stop()
        val session = extractionSessionId
        scope.launch { if (session != null) ServiceLocator.database.sessionDao().updateState(session, "STOPPED", completedAt = System.currentTimeMillis()) }
        finishStopped()
    }

    fun skipCurrent() { BotRuntime.skip() }

    fun retryFailed() {
        scope.launch {
            val session = extractionSessionId?.let { id ->
                ServiceLocator.database.sessionDao().latest()?.takeIf { it.id == id }
            } ?: ServiceLocator.database.sessionDao().latest()
            if (session == null || session.type != "EXTRACTION") {
                BotRuntime.update(RuntimeSnapshot(RuntimePhase.ERROR, "No extraction session", "Nothing to retry"))
                return@launch
            }
            val allQueue = ServiceLocator.database.queueDao().forSession(session.id)
            val failed = allQueue.filter { it.state == "FAILED" }
            val retryable = failed.filter { item ->
                FailureRecoveryPolicy.decide(item.lastError, item.attempts) == FailureDisposition.RETRY
            }
            val protected = failed.size - retryable.size
            val retryableIds = retryable.mapTo(hashSetOf()) { it.id }
            retryable.forEach { item ->
                ServiceLocator.database.queueDao().updateState(item.id, "WAITING", item.attempts, null)
                ServiceLocator.groups.updateExtraction(item.groupId, "PENDING", ServiceLocator.database.groupDao().get(item.groupId)?.checkpoint)
            }
            if (retryable.isEmpty()) {
                BotRuntime.update(
                    BotRuntime.state.value.copy(
                        title = "No safe retries",
                        detail = if (protected > 0) "$protected failed group(s) require review" else "Nothing to retry",
                    )
                )
                return@launch
            }
            val instanceEntity = ServiceLocator.database.instanceDao().get(session.instanceId) ?: return@launch
            extractionSessionId = session.id
            targetInstanceId = session.instanceId
            targetPackage = instanceEntity.packageName
            extractionMode = runCatching { AutomationMode.valueOf(session.mode) }.getOrDefault(AutomationMode.NEW_ONLY)
            val progress = QueueProgressPolicy.summarize(allQueue.map { item -> if (item.id in retryableIds) "WAITING" else item.state })
            totalQueue = progress.total
            groupsCompleted = progress.completed
            currentQueue = null
            currentGroup = null
            resetAdaptiveSession()
            setExtractStage(ExtractStage.PREPARE_LIST)
            ServiceLocator.database.sessionDao().updateState(session.id, "RUNNING")
            BotRuntime.resetControlFlags()
            BotRuntime.update(
                RuntimeSnapshot(
                    RuntimePhase.EXTRACTING,
                    "Retrying failed groups",
                    "${retryable.size} safe retry • $protected protected",
                    progress.completed,
                    progress.total,
                )
            )
            startRuntimeService("Retrying failed groups")
            WhatsAppInstanceDetector.launch(this@WaAccessibilityService, instanceEntity.packageName)
        }
    }

    fun startGroupSync(instanceId: String, packageName: String): Boolean {
        val canLaunch = packageManager.getLaunchIntentForPackage(packageName) != null
        if (!canLaunch) {
            BotRuntime.update(RuntimeSnapshot(RuntimePhase.ERROR, "WhatsApp unavailable", packageName))
            return false
        }
        BotRuntime.resetControlFlags()
        targetPackage = packageName
        targetInstanceId = instanceId
        syncId = UUID.randomUUID().toString()
        syncSeen.clear()
        syncNewOrdinalByTitle.clear()
        syncDiscoveredIdentityByTitle.clear()
        syncPreviousViewport = emptyList()
        resetAdaptiveSession()
        val syncProfile = effectivePerformanceProfile()
        syncEndGuard = EndOfListGuard(syncProfile.stableEndCycles, syncProfile.rejectedScrollsRequired)
        syncTiming = AdaptiveTimingPolicy(syncProfile)
        syncActionIssuedAt = null
        stageCircuitBreaker.reset()
        syncRequestedFingerprint = null
        syncProbeJob?.cancel()
        syncProbeJob = null
        filterVerifyAttempts = 0
        BotRuntime.update(RuntimeSnapshot(RuntimePhase.SYNCING, "Preparing sync", "Loading local group identities"))
        DiagnosticLog.record("SYNC_START", mapOf("instance" to instanceId.take(12)))
        startRuntimeService("Group sync")
        scope.launch {
            syncExistingGroups = ServiceLocator.database.groupDao().byInstance(instanceId)
            syncExistingIdentityByTitle = syncExistingGroups.groupBy { it.normalizedTitle }.mapValues { (_, groups) ->
                groups.map { existing ->
                    GroupIdentityRecord(
                        id = existing.id,
                        normalizedTitle = existing.normalizedTitle,
                        rowFingerprint = existing.rowFingerprint,
                        preview = existing.lastPreview,
                        unreadCount = existing.unreadCount,
                    )
                }
            }
            setSyncStage(SyncStage.OPENING)
            BotRuntime.update(RuntimeSnapshot(RuntimePhase.SYNCING, "Opening WhatsApp", "Preparing group sync"))
            WhatsAppInstanceDetector.launch(this@WaAccessibilityService, packageName)
        }
        return true
    }

    fun startExtraction(instanceId: String, packageName: String, mode: AutomationMode) {
        BotRuntime.resetControlFlags()
        extractionMode = mode
        targetPackage = packageName
        targetInstanceId = instanceId
        groupsCompleted = 0
        resetAdaptiveSession()
        scope.launch {
            val selected = ServiceLocator.groups.selected(instanceId)
            if (selected.isEmpty()) {
                BotRuntime.update(RuntimeSnapshot(RuntimePhase.ERROR, "Nothing selected", "Select at least one group"))
                return@launch
            }
            val byId = selected.associateBy { it.id }
            val orderedIds = SmartQueuePolicy.order(
                selected.map { group ->
                    QueuePriorityInput(
                        id = group.id,
                        unread = group.unread,
                        unreadCount = group.unreadCount,
                        active = group.active,
                        extractionState = group.extractionState,
                        lastSeenAt = group.lastSeenAt,
                    )
                },
                mode,
            ).map { it.id }
            val ordered = orderedIds.mapNotNull(byId::get)
            val sessionId = UUID.randomUUID().toString()
            extractionSessionId = sessionId
            totalQueue = ordered.size
            ServiceLocator.database.queueDao().upsertAll(ordered.mapIndexed { index, group ->
                QueueItemEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    groupId = group.id,
                    position = index,
                )
            })
            ServiceLocator.database.sessionDao().upsert(
                BotSessionEntity(sessionId, instanceId, "EXTRACTION", mode.name, "RUNNING")
            )
            setExtractStage(ExtractStage.PREPARE_LIST)
            BotRuntime.update(RuntimeSnapshot(RuntimePhase.EXTRACTING, "Starting extraction", mode.name, 0, ordered.size))
            DiagnosticLog.record("EXTRACTION_START", mapOf("mode" to mode.name, "groups" to ordered.size))
            startRuntimeService("Extracting links")
            WhatsAppInstanceDetector.launch(this@WaAccessibilityService, packageName)
        }
    }

    private fun handleSync(root: AccessibilityNodeInfo, eventType: Int) {
        val op = operation as? Operation.Sync ?: return
        when (op.stage) {
            SyncStage.OPENING -> {
                val groups = AccessibilityTree.findByViewIdHints(root, GROUP_ID_HINTS)
                    ?: AccessibilityTree.findByAnyText(root, GROUP_LABELS)
                if (groups != null && AccessibilityTree.click(groups)) {
                    recordStageSuccess("SYNC_OPENING")
                    filterVerifyAttempts = 0
                    setSyncStage(SyncStage.VERIFY_FILTER)
                    BotRuntime.update(BotRuntime.state.value.copy(title = "Groups filter", detail = "Verifying active filter"))
                    scheduleFilterVerificationProbe()
                } else {
                    // If WhatsApp was last left inside a chat or another tab, return to Chats first.
                    val chats = AccessibilityTree.findByViewIdHints(root, CHAT_ID_HINTS)
                        ?: AccessibilityTree.findByAnyText(root, CHAT_LABELS)
                    if (chats != null) AccessibilityTree.click(chats)
                    else performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
            SyncStage.VERIFY_FILTER -> verifyGroupFilter(root)
            SyncStage.SCANNING -> scanGroupViewport(root, eventType)
        }
    }

    private fun verifyGroupFilter(root: AccessibilityNodeInfo) {
        filterVerifyAttempts++
        val evidence = AccessibilityTree.filterEvidence(root, GROUP_LABELS, GROUP_ID_HINTS)
        val decision = FilterVerificationPolicy.decide(evidence, effectiveMode, filterVerifyAttempts)
        when (decision) {
            FilterDecision.PROCEED -> {
                recordStageSuccess("SYNC_VERIFY_FILTER")
                DiagnosticLog.record("GROUP_FILTER_VERIFIED", mapOf("attempts" to filterVerifyAttempts))
                setSyncStage(SyncStage.SCANNING)
                scanGroupViewport(root, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, fromProbe = true)
            }
            FilterDecision.PROCEED_WITH_WARNING -> {
                recordTransientFailure("GROUP_FILTER_UNVERIFIED_FALLBACK")
                DiagnosticLog.record("GROUP_FILTER_UNVERIFIED_FALLBACK", mapOf("attempts" to filterVerifyAttempts, "mode" to effectiveMode.name))
                setSyncStage(SyncStage.SCANNING)
                scanGroupViewport(root, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, fromProbe = true)
            }
            FilterDecision.RETRY -> {
                if (evidence == FilterEvidence.INACTIVE) {
                    val groups = AccessibilityTree.findByViewIdHints(root, GROUP_ID_HINTS)
                        ?: AccessibilityTree.findByAnyText(root, GROUP_LABELS)
                    AccessibilityTree.click(groups)
                }
                scheduleFilterVerificationProbe()
            }
            FilterDecision.FAIL -> {
                recordHardFailure("GROUP_FILTER_VERIFY_FAILED")
                DiagnosticLog.record("GROUP_FILTER_VERIFY_FAILED", mapOf("attempts" to filterVerifyAttempts, "mode" to effectiveMode.name))
                clearOperation()
                BotRuntime.update(RuntimeSnapshot(RuntimePhase.ERROR, "Group filter not verified", "WhatsApp did not expose reliable selected-state evidence; refusing unsafe synchronization"))
                stopRuntimeService()
            }
        }
    }

    private fun scheduleFilterVerificationProbe() {
        syncProbeJob?.cancel()
        syncProbeJob = scope.launch {
            delay(syncTiming.nextProbeDelay(scrollAccepted = false))
            val current = operation as? Operation.Sync ?: return@launch
            if (current.stage != SyncStage.VERIFY_FILTER || BotRuntime.pauseRequested || BotRuntime.stopRequested) return@launch
            rootInActiveWindow?.let(::verifyGroupFilter)
        }
    }

    private fun scanGroupViewport(root: AccessibilityNodeInfo, eventType: Int, fromProbe: Boolean = false) {
        if (!screenMatches(root, ScreenKind.GROUP_LIST, minConfidence = 60)) {
            scheduleSyncProbe(syncTiming.nextProbeDelay(scrollAccepted = false))
            return
        }
        val listFingerprint = AccessibilityTree.viewportFingerprint(root)
        if (!fromProbe && syncRequestedFingerprint == listFingerprint) {
            // Ignore duplicate events emitted before RecyclerView has actually rebound.
            return
        }
        if (syncRequestedFingerprint != null && syncRequestedFingerprint != listFingerprint) {
            syncActionIssuedAt?.let { issued -> syncTiming.observeUiLatency((System.currentTimeMillis() - issued).coerceAtLeast(1)) }
            syncActionIssuedAt = null
            syncRequestedFingerprint = null
            syncProbeJob?.cancel()
            syncProbeJob = null
        }

        val rows = AccessibilityTree.rowCandidates(root)
        val instanceId = targetInstanceId ?: return
        val thisSync = syncId ?: return
        val now = System.currentTimeMillis()
        var newCount = 0
        val viewportEntities = ArrayList<GroupEntity>(rows.size)
        val signatures = rows.map { row ->
            val normalized = normalizeTitle(row.title)
            "$normalized|${row.rowFingerprint}|${normalizePreview(row.preview)}|${row.unreadCount ?: -1}"
        }
        val overlapIds = ViewportIdentityPolicy.reuseIds(signatures, syncPreviousViewport)
        val viewportUsedIds = LinkedHashSet<String>()
        val viewportIdentity = ArrayList<ViewportIdentity>(rows.size)

        for ((index, row) in rows.withIndex()) {
            val normalized = normalizeTitle(row.title)
            val currentSignature = signatures[index]
            val overlapId = overlapIds[index]?.takeIf { it !in viewportUsedIds }
            val candidates = buildList {
                addAll(syncExistingIdentityByTitle[normalized].orEmpty())
                addAll(syncDiscoveredIdentityByTitle[normalized].orEmpty())
            }.distinctBy { it.id }
            val matchedId = overlapId ?: GroupIdentityMatcher.match(
                GroupIdentityEvidence(
                    normalizedTitle = normalized,
                    rowFingerprint = row.rowFingerprint,
                    preview = row.preview,
                    unreadCount = row.unreadCount,
                ),
                candidates,
                viewportUsedIds,
            )
            val id = matchedId ?: run {
                val ordinal = syncNewOrdinalByTitle.getOrDefault(normalized, 0)
                syncNewOrdinalByTitle[normalized] = ordinal + 1
                stableHash("$instanceId|$normalized|slot:$ordinal")
            }
            viewportUsedIds += id
            viewportIdentity += ViewportIdentity(currentSignature, id)
            if (!syncSeen.containsKey(id)) newCount++

            val previous = syncSeen[id] ?: syncExistingGroups.firstOrNull { it.id == id }
            val entity = GroupEntity(
                id = id,
                instanceId = instanceId,
                displayTitle = row.title,
                normalizedTitle = normalized,
                rowFingerprint = row.rowFingerprint,
                unread = row.unread,
                unreadCount = row.unreadCount,
                active = row.active,
                lastPreview = row.preview,
                firstSeenAt = previous?.firstSeenAt ?: now,
                lastSeenAt = now,
                lastSeenSyncId = thisSync,
                extractionState = previous?.extractionState ?: "NEVER_SCANNED",
                selected = previous?.selected ?: false,
                checkpoint = previous?.checkpoint,
            )
            syncSeen[id] = entity
            viewportEntities += entity

            val identityRecord = GroupIdentityRecord(
                id = id,
                normalizedTitle = normalized,
                rowFingerprint = row.rowFingerprint,
                preview = row.preview,
                unreadCount = row.unreadCount,
            )
            val discovered = syncDiscoveredIdentityByTitle.getOrPut(normalized) { mutableListOf() }
            val at = discovered.indexOfFirst { it.id == id }
            if (at >= 0) discovered[at] = identityRecord else discovered += identityRecord
        }
        syncPreviousViewport = viewportIdentity

        scope.launch { if (viewportEntities.isNotEmpty()) ServiceLocator.groups.upsert(viewportEntities) }
        if (newCount > 0) {
            throughputMeter.addGroups(newCount)
            recordStageSuccess("SYNC_SCAN_PROGRESS")
        }
        BotRuntime.update(BotRuntime.state.value.copy(phase = RuntimePhase.SYNCING, title = "Synchronizing groups", detail = "${syncSeen.size} groups found", current = syncSeen.size, total = 0))
        publishTelemetry()

        val scrollable = AccessibilityTree.bestConversationScrollable(root)
        val moved = scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
        val completed = syncEndGuard.observe(ProgressSample(listFingerprint, newCount, moved))
        if (completed) {
            completeSync()
            return
        }

        syncRequestedFingerprint = if (moved) listFingerprint else null
        syncActionIssuedAt = if (moved) System.currentTimeMillis() else null
        scheduleSyncProbe(syncTiming.nextProbeDelay(moved))
    }

    private fun scheduleSyncProbe(delayMs: Long) {
        syncProbeJob?.cancel()
        syncProbeJob = scope.launch {
            delay(delayMs)
            if (operation !is Operation.Sync || BotRuntime.pauseRequested || BotRuntime.stopRequested) return@launch
            rootInActiveWindow?.let { scanGroupViewport(it, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, fromProbe = true) }
        }
    }

    private fun completeSync() {
        val instanceId = targetInstanceId
        val completedSyncId = syncId
        val previousPresent = syncExistingGroups.count { it.present }
        val discovered = syncSeen.size
        val coverageDecision = SyncCoveragePolicy.decide(previousPresent, discovered)
        clearOperation()
        scope.launch {
            if (syncSeen.isNotEmpty()) ServiceLocator.groups.upsert(syncSeen.values.toList())
            if (coverageDecision == SyncCoverageDecision.SAFETY_STOP) {
                recordHardFailure("SYNC_COVERAGE_SAFETY_STOP")
                DiagnosticLog.record("SYNC_COVERAGE_SAFETY_STOP", mapOf("previous" to previousPresent, "discovered" to discovered))
                BotRuntime.update(
                    RuntimeSnapshot(
                        RuntimePhase.ERROR,
                        "Sync safety stop",
                        "Only $discovered of $previousPresent previously-present groups were observed; existing registry was preserved",
                        discovered,
                        previousPresent,
                    )
                )
                stopRuntimeService()
                return@launch
            }
            if (instanceId != null && completedSyncId != null) {
                ServiceLocator.groups.markMissing(instanceId, completedSyncId)
            }
            BotRuntime.update(BotRuntime.state.value.copy(phase = RuntimePhase.COMPLETED, title = "Sync complete", detail = "$discovered groups synchronized", current = discovered, total = discovered))
            publishTelemetry()
            val throughput = throughputMeter.snapshot()
            DiagnosticLog.record(
                "SYNC_COMPLETE",
                mapOf(
                    "groups" to discovered,
                    "previous" to previousPresent,
                    "health" to automationHealth.snapshot().score,
                    "mode" to effectiveMode.name,
                    "groupsPerMin" to throughput.groupsPerMinute.toInt(),
                )
            )
            stopRuntimeService()
        }
    }

    private fun handleExtraction(root: AccessibilityNodeInfo, eventType: Int) {
        if (BotRuntime.consumeSkip()) {
            completeCurrentGroup("SKIPPED", "Skipped by user")
            return
        }
        val op = operation as? Operation.Extract ?: return
        when (op.stage) {
            ExtractStage.PREPARE_LIST -> prepareNextGroup(root)
            ExtractStage.ACTIVATE_GROUP_FILTER -> activateGroupFilter(root)
            ExtractStage.OPEN_SEARCH -> openSearch(root)
            ExtractStage.TYPE_QUERY -> typeQuery(root)
            ExtractStage.OPEN_RESULT -> openSearchResult(root)
            ExtractStage.VERIFY_CHAT -> verifyChat(root)
            ExtractStage.SCAN_VIEWPORT -> scanChatViewport(root, eventType)
            ExtractStage.RETURNING -> returnToGroupList(root)
        }
    }

    private fun returnToGroupList(root: AccessibilityNodeInfo) {
        val evidence = AccessibilityTree.screenEvidence(root)
        val listReady = evidence.kind == ScreenKind.GROUP_LIST && evidence.confidence >= 60
        val groupsFilter = AccessibilityTree.findByViewIdHints(root, GROUP_ID_HINTS)
            ?: AccessibilityTree.findByAnyText(root, GROUP_LABELS)
        val chats = AccessibilityTree.findByViewIdHints(root, CHAT_ID_HINTS)
            ?: AccessibilityTree.findByAnyText(root, CHAT_LABELS)
        if (listReady || groupsFilter != null || chats != null) {
            recordStageSuccess("EXTRACT_RETURNING")
            setExtractStage(ExtractStage.PREPARE_LIST)
            prepareNextGroup(root)
            return
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
        armCurrentStageWatchdog()
    }

    private fun prepareNextGroup(root: AccessibilityNodeInfo) {
        if (!prepareInFlight.compareAndSet(false, true)) return
        val sessionId = extractionSessionId ?: run { prepareInFlight.set(false); return }
        scope.launch {
            try {
                val next = ServiceLocator.database.queueDao().nextPending(sessionId)
                if (next == null) {
                    finishExtraction()
                    return@launch
                }
                val group = ServiceLocator.database.groupDao().get(next.groupId)
                if (group == null) {
                    ServiceLocator.database.queueDao().updateState(next.id, "FAILED", next.attempts + 1, "GROUP_MISSING: Group missing from registry")
                    setExtractStage(ExtractStage.PREPARE_LIST)
                    return@launch
                }
                recordStageSuccess("EXTRACT_PREPARE_LIST")
                currentQueue = next
                currentGroup = group
                viewportStartFingerprint = null
                newestViewportFingerprint = null
                previousViewportFingerprint = null
                extractionSeenMessageFingerprints = emptySet()
                viewportCommitInFlight.set(false)
                val extractionProfile = effectivePerformanceProfile()
                extractionEndGuard = EndOfListGuard(extractionProfile.stableEndCycles, extractionProfile.rejectedScrollsRequired)
                extractionTiming = AdaptiveTimingPolicy(extractionProfile)
                extractionActionIssuedAt = null
                extractionRequestedFingerprint = null
                extractionProbeJob?.cancel()
                extractionProbeJob = null
                ServiceLocator.database.queueDao().updateState(next.id, "LOCATING", next.attempts, null)
                setExtractStage(ExtractStage.ACTIVATE_GROUP_FILTER)
                BotRuntime.update(RuntimeSnapshot(RuntimePhase.EXTRACTING, "Locating group", group.displayTitle, next.position + 1, totalQueue, BotRuntime.state.value.linksFound))
                targetPackage?.let { WhatsAppInstanceDetector.launch(this@WaAccessibilityService, it) }
            } finally {
                prepareInFlight.set(false)
            }
        }
    }

    private fun activateGroupFilter(root: AccessibilityNodeInfo) {
        val node = AccessibilityTree.findByViewIdHints(root, GROUP_ID_HINTS)
            ?: AccessibilityTree.findByAnyText(root, GROUP_LABELS)
        if (node != null && AccessibilityTree.click(node)) {
            recordStageSuccess("EXTRACT_ACTIVATE_GROUP_FILTER")
            setExtractStage(ExtractStage.OPEN_SEARCH)
        }
    }

    private fun openSearch(root: AccessibilityNodeInfo) {
        val existingEditor = AccessibilityTree.findEditable(root)
        if (existingEditor != null) {
            recordStageSuccess("EXTRACT_OPEN_SEARCH")
            setExtractStage(ExtractStage.TYPE_QUERY)
            typeQuery(root)
            return
        }
        val search = AccessibilityTree.findByAnyText(root, SEARCH_LABELS)
            ?: AccessibilityTree.findByViewIdHints(root, listOf("search"))
        if (search != null && AccessibilityTree.click(search)) {
            recordStageSuccess("EXTRACT_OPEN_SEARCH")
            setExtractStage(ExtractStage.TYPE_QUERY)
        }
    }

    private fun typeQuery(root: AccessibilityNodeInfo) {
        val editor = AccessibilityTree.findEditable(root) ?: return
        val title = currentGroup?.displayTitle ?: return
        if (AccessibilityTree.setText(editor, title)) {
            recordStageSuccess("EXTRACT_TYPE_QUERY")
            setExtractStage(ExtractStage.OPEN_RESULT)
        }
    }

    private fun openSearchResult(root: AccessibilityNodeInfo) {
        val title = currentGroup?.displayTitle ?: return
        val searchEvidence = AccessibilityTree.screenEvidence(root)
        if (searchEvidence.kind != ScreenKind.SEARCH || searchEvidence.confidence < 60) return
        when (val resolution = AccessibilityTree.searchResultResolution(root, title, currentGroup?.lastPreview)) {
            is SearchNodeResolution.Unique -> {
                if (AccessibilityTree.click(resolution.node)) {
                    recordStageSuccess("EXTRACT_OPEN_RESULT")
                    currentQueue?.let { q -> scope.launch { ServiceLocator.database.queueDao().updateState(q.id, "OPENING", q.attempts, null) } }
                    setExtractStage(ExtractStage.VERIFY_CHAT)
                }
            }
            is SearchNodeResolution.Ambiguous -> {
                recordAmbiguity("AMBIGUOUS_GROUP")
                failCurrentGroup("AMBIGUOUS_GROUP", "${resolution.count} search results share the same title; refusing unsafe selection")
            }
            SearchNodeResolution.None -> Unit
        }
    }

    private fun failCurrentGroup(code: String, detail: String) {
        val queue = currentQueue ?: return
        val group = currentGroup ?: return
        if (code !in setOf("AMBIGUOUS_GROUP", "STAGE_TIMEOUT", "PERSISTENCE_ERROR")) recordTransientFailure(code)
        DiagnosticLog.record("GROUP_FAIL", mapOf("code" to code, "group" to group.id.take(12)))
        extractionProbeJob?.cancel()
        extractionProbeJob = null
        scope.launch {
            ServiceLocator.database.queueDao().updateState(queue.id, "FAILED", queue.attempts + 1, "$code: $detail")
            ServiceLocator.groups.updateExtraction(group.id, "FAILED", group.checkpoint)
            performGlobalAction(GLOBAL_ACTION_BACK)
            currentGroup = null
            currentQueue = null
            setExtractStage(ExtractStage.RETURNING)
            BotRuntime.update(BotRuntime.state.value.copy(detail = "$code • ${group.displayTitle}"))
        }
    }

    private fun verifyChat(root: AccessibilityNodeInfo) {
        val group = currentGroup ?: return
        val semanticMatch = screenMatches(root, ScreenKind.CHAT, minConfidence = 80, expectedTitle = group.displayTitle)
        if (semanticMatch || AccessibilityTree.isLikelyChatScreen(root, group.displayTitle)) {
            recordStageSuccess("EXTRACT_VERIFY_CHAT")
            currentQueue?.let { q -> scope.launch { ServiceLocator.database.queueDao().updateState(q.id, "SCANNING", q.attempts, null) } }
            setExtractStage(ExtractStage.SCAN_VIEWPORT)
            scanChatViewport(root, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
    }

    private fun scanChatViewport(root: AccessibilityNodeInfo, eventType: Int, fromProbe: Boolean = false) {
        if (viewportCommitInFlight.get()) return
        val group = currentGroup ?: return
        val sessionId = extractionSessionId ?: return
        val container = AccessibilityTree.bestMessageScrollable(root) ?: return
        val viewport = AccessibilityTree.messageViewport(container)
        val batches = viewport.batches
        val joined = batches.joinToString("\n") { it.text }
        val fingerprint = stableHash(joined)

        if (!fromProbe && extractionRequestedFingerprint == fingerprint) return
        if (extractionRequestedFingerprint != null && extractionRequestedFingerprint != fingerprint) {
            extractionActionIssuedAt?.let { issued -> extractionTiming.observeUiLatency((System.currentTimeMillis() - issued).coerceAtLeast(1)) }
            extractionActionIssuedAt = null
            extractionRequestedFingerprint = null
            extractionProbeJob?.cancel()
            extractionProbeJob = null
            recordStageSuccess("EXTRACT_VIEWPORT_PROGRESS")
        }

        val visibleAnchors = batches.map { it.fingerprint }
        if (viewportStartFingerprint == null) {
            viewportStartFingerprint = CheckpointCodec.encode(batches.takeLast(8).map { it.fingerprint })
        }
        if (extractionMode == AutomationMode.UNREAD_ONLY || newestViewportFingerprint == null) {
            newestViewportFingerprint = CheckpointCodec.encode(batches.takeLast(8).map { it.fingerprint })
        }
        val reachedOldCheckpoint = extractionMode == AutomationMode.NEW_ONLY &&
            CheckpointCodec.reached(group.checkpoint, visibleAnchors)

        val selectedFingerprints = MessageViewportPolicy.select(
            items = viewport.orderedItems,
            seen = extractionSeenMessageFingerprints,
            unreadOnly = extractionMode == AutomationMode.UNREAD_ONLY,
        )
        val selectedSet = selectedFingerprints.toHashSet()
        val batchesToProcess = batches.filter { it.fingerprint in selectedSet }
        extractionSeenMessageFingerprints = MessageViewportPolicy.mergeSeen(
            current = extractionSeenMessageFingerprints,
            newlySeen = visibleAnchors,
            maxEntries = 512,
        )

        val findings = if (batchesToProcess.isEmpty()) emptyList() else batchesToProcess.flatMap { batch ->
            LinkExtractor.extract(batch.text).map { candidate -> Triple(batch, candidate, batch.fingerprint) }
        }
        val detected = findings.size
        if (DurableViewportPolicy.mustPersist(detected)) {
            if (!viewportCommitInFlight.compareAndSet(false, true)) return
            scope.launch {
                try {
                    val saveText = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("save_message_text", false)
                    ServiceLocator.links.recordBatch(
                        findings.map { (batch, candidate, fingerprintSource) ->
                            com.waalothmany.linkbot.data.LinkRecordRequest(
                                candidate = candidate,
                                groupId = group.id,
                                sessionId = sessionId,
                                source = "ACCESSIBILITY",
                                messageText = if (saveText) batch.text else null,
                                messageFingerprintSource = fingerprintSource,
                            )
                        }
                    )
                    throughputMeter.addLinks(detected)
                    BotRuntime.update(BotRuntime.state.value.copy(linksFound = BotRuntime.state.value.linksFound + detected))
                    publishTelemetry()
                    advanceChatViewport(
                        groupId = group.id,
                        fingerprint = fingerprint,
                        detected = detected,
                        reachedOldCheckpoint = reachedOldCheckpoint,
                        persistenceState = ViewportPersistenceState.COMMITTED,
                    )
                } catch (t: Throwable) {
                    recordHardFailure("PERSISTENCE_ERROR")
                    DiagnosticLog.record("VIEWPORT_PERSISTENCE_FAILED", mapOf("type" to t.javaClass.simpleName))
                    failCurrentGroup("PERSISTENCE_ERROR", "Could not commit extracted links; viewport was not advanced")
                } finally {
                    viewportCommitInFlight.set(false)
                }
            }
            return
        }

        advanceChatViewport(
            groupId = group.id,
            fingerprint = fingerprint,
            detected = 0,
            reachedOldCheckpoint = reachedOldCheckpoint,
            persistenceState = ViewportPersistenceState.NO_CHANGES,
        )
    }

    private fun advanceChatViewport(
        groupId: String,
        fingerprint: String,
        detected: Int,
        reachedOldCheckpoint: Boolean,
        persistenceState: ViewportPersistenceState,
    ) {
        if (!DurableViewportPolicy.canAdvance(persistenceState)) return
        val group = currentGroup?.takeIf { it.id == groupId } ?: return
        val stage = operation as? Operation.Extract ?: return
        if (stage.stage != ExtractStage.SCAN_VIEWPORT || BotRuntime.pauseRequested || BotRuntime.stopRequested) return

        previousViewportFingerprint = fingerprint
        if (reachedOldCheckpoint) {
            completeCurrentGroup("COMPLETED", null)
            return
        }

        val freshRoot = rootInActiveWindow ?: run {
            scheduleExtractionProbe(extractionTiming.nextProbeDelay(scrollAccepted = false))
            return
        }
        if (!screenMatches(freshRoot, ScreenKind.CHAT, minConfidence = 70, expectedTitle = group.displayTitle)) {
            scheduleExtractionProbe(extractionTiming.nextProbeDelay(scrollAccepted = false))
            return
        }
        val freshContainer = AccessibilityTree.bestMessageScrollable(freshRoot) ?: run {
            scheduleExtractionProbe(extractionTiming.nextProbeDelay(scrollAccepted = false))
            return
        }
        val action = when (extractionMode) {
            AutomationMode.DEEP, AutomationMode.NEW_ONLY -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            AutomationMode.UNREAD_ONLY -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        val moved = freshContainer.performAction(action)
        val completed = extractionEndGuard.observe(ProgressSample(fingerprint, detected, moved))
        if (completed) {
            completeCurrentGroup("COMPLETED", null)
            return
        }

        extractionRequestedFingerprint = if (moved) fingerprint else null
        extractionActionIssuedAt = if (moved) System.currentTimeMillis() else null
        scheduleExtractionProbe(extractionTiming.nextProbeDelay(moved))
    }

    private fun scheduleExtractionProbe(delayMs: Long) {
        extractionProbeJob?.cancel()
        extractionProbeJob = scope.launch {
            delay(delayMs)
            if (operation !is Operation.Extract || BotRuntime.pauseRequested || BotRuntime.stopRequested) return@launch
            rootInActiveWindow?.let { scanChatViewport(it, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, fromProbe = true) }
        }
    }

    private fun completeCurrentGroup(state: String, error: String?) {
        val queue = currentQueue ?: return
        val group = currentGroup ?: return
        DiagnosticLog.record("GROUP_COMPLETE", mapOf("state" to state, "group" to group.id.take(12)))
        val latestCheckpoint = when (extractionMode) {
            AutomationMode.UNREAD_ONLY -> newestViewportFingerprint ?: viewportStartFingerprint
            AutomationMode.DEEP, AutomationMode.NEW_ONLY -> viewportStartFingerprint
        }
        extractionProbeJob?.cancel()
        extractionProbeJob = null
        extractionRequestedFingerprint = null
        scope.launch {
            ServiceLocator.database.queueDao().updateState(queue.id, state, queue.attempts + if (state == "FAILED") 1 else 0, error)
            ServiceLocator.groups.updateExtraction(group.id, if (state == "COMPLETED") "COMPLETED" else state, latestCheckpoint)
            if (state in setOf("COMPLETED", "SKIPPED")) {
                groupsCompleted++
                throughputMeter.addGroups(1)
                if (state == "COMPLETED") recordStageSuccess("EXTRACT_GROUP_COMPLETE")
                publishTelemetry()
            }
            performGlobalAction(GLOBAL_ACTION_BACK)
            currentGroup = null
            currentQueue = null
            setExtractStage(ExtractStage.RETURNING)
            BotRuntime.update(BotRuntime.state.value.copy(current = queue.position + 1, detail = "Returning to group list"))
        }
    }

    private fun finishExtraction() {
        val sessionId = extractionSessionId
        clearOperation()
        scope.launch {
            val queue = sessionId?.let { ServiceLocator.database.queueDao().forSession(it) }.orEmpty()
            val progress = QueueProgressPolicy.summarize(queue.map { it.state })
            val sessionState = if (progress.failed > 0) "COMPLETED_WITH_ERRORS" else "COMPLETED"
            if (sessionId != null) {
                val old = ServiceLocator.database.sessionDao().latest()
                if (old != null && old.id == sessionId) {
                    ServiceLocator.database.sessionDao().upsert(
                        old.copy(
                            state = sessionState,
                            updatedAt = System.currentTimeMillis(),
                            completedAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
            val title = if (progress.failed > 0) "Extraction complete with errors" else "Extraction complete"
            val detail = if (progress.failed > 0) "${progress.failed} group(s) failed • Retry safe failures or review protected items" else "All queued groups processed"
            BotRuntime.update(
                BotRuntime.state.value.copy(
                    phase = RuntimePhase.COMPLETED,
                    title = title,
                    detail = detail,
                    current = progress.total - progress.remaining,
                    total = progress.total,
                )
            )
            publishTelemetry()
            val throughput = throughputMeter.snapshot()
            DiagnosticLog.record(
                "EXTRACTION_COMPLETE",
                mapOf(
                    "groups" to progress.completed,
                    "failed" to progress.failed,
                    "links" to BotRuntime.state.value.linksFound,
                    "health" to automationHealth.snapshot().score,
                    "mode" to effectiveMode.name,
                    "groupsPerMin" to throughput.groupsPerMinute.toInt(),
                    "linksPerMin" to throughput.linksPerMinute.toInt(),
                )
            )
            stopRuntimeService()
        }
    }

    private fun finishStopped() {
        clearOperation()
        BotRuntime.update(BotRuntime.state.value.copy(phase = RuntimePhase.STOPPED, title = "Stopped"))
        stopRuntimeService()
    }

    private fun startRuntimeService(label: String) {
        val intent = Intent(this, BotForegroundService::class.java).putExtra(BotForegroundService.EXTRA_LABEL, label)
        startForegroundService(intent)
        if (android.provider.Settings.canDrawOverlays(this)) {
            startService(Intent(this, com.waalothmany.linkbot.runtime.OverlayControllerService::class.java))
        }
    }

    private fun stopRuntimeService() {
        stopService(Intent(this, BotForegroundService::class.java))
        stopService(Intent(this, com.waalothmany.linkbot.runtime.OverlayControllerService::class.java))
    }

    private fun setSyncStage(stage: SyncStage) {
        operation = Operation.Sync(stage)
        armCurrentStageWatchdog()
    }

    private fun setExtractStage(stage: ExtractStage) {
        operation = Operation.Extract(stage)
        armCurrentStageWatchdog()
    }

    private fun clearOperation() {
        operation = Operation.None
        viewportCommitInFlight.set(false)
        stageWatchdogJob?.cancel()
        stageWatchdogJob = null
    }

    private fun armCurrentStageWatchdog() {
        stageWatchdogJob?.cancel()
        val snapshot = operation
        val shouldWatch = when (snapshot) {
            is Operation.Sync -> snapshot.stage != SyncStage.SCANNING
            is Operation.Extract -> snapshot.stage !in setOf(ExtractStage.SCAN_VIEWPORT)
            Operation.None -> false
        }
        if (!shouldWatch || BotRuntime.pauseRequested || BotRuntime.stopRequested) return
        val timeout = effectivePerformanceProfile().stageTimeoutMs
        stageWatchdogJob = scope.launch {
            delay(timeout)
            if (BotRuntime.pauseRequested || BotRuntime.stopRequested || operation != snapshot) return@launch
            when (snapshot) {
                is Operation.Sync -> {
                    val key = "SYNC_${snapshot.stage.name}"
                    DiagnosticLog.record("STAGE_TIMEOUT", mapOf("stage" to key))
                    recordTransientFailure(key)
                    when (stageCircuitBreaker.recordFailure(key)) {
                        CircuitDecision.RETRY -> {
                            DiagnosticLog.record("STAGE_RETRY", mapOf("stage" to key, "attempt" to stageCircuitBreaker.failureCount(key)))
                            setSyncStage(SyncStage.OPENING)
                            targetPackage?.let { WhatsAppInstanceDetector.launch(this@WaAccessibilityService, it) }
                        }
                        CircuitDecision.TRIP -> {
                            recordHardFailure(key)
                            clearOperation()
                            BotRuntime.update(RuntimeSnapshot(RuntimePhase.ERROR, "Sync safety stop", "Repeated timeout at ${snapshot.stage.name}"))
                            stopRuntimeService()
                        }
                    }
                }
                is Operation.Extract -> {
                    val key = "EXTRACT_${snapshot.stage.name}"
                    DiagnosticLog.record("STAGE_TIMEOUT", mapOf("stage" to key))
                    recordTransientFailure(key)
                    if (currentGroup != null && currentQueue != null) {
                        when (stageCircuitBreaker.recordFailure(key)) {
                            CircuitDecision.RETRY -> {
                                DiagnosticLog.record("STAGE_RETRY", mapOf("stage" to key, "attempt" to stageCircuitBreaker.failureCount(key)))
                                performGlobalAction(GLOBAL_ACTION_BACK)
                                setExtractStage(ExtractStage.RETURNING)
                            }
                            CircuitDecision.TRIP -> {
                                recordHardFailure(key)
                                failCurrentGroup("STAGE_TIMEOUT", "Repeated timeout at ${snapshot.stage.name}")
                            }
                        }
                    } else {
                        clearOperation()
                        BotRuntime.update(RuntimeSnapshot(RuntimePhase.ERROR, "Extraction timeout", snapshot.stage.name))
                        stopRuntimeService()
                    }
                }
                Operation.None -> Unit
            }
        }
    }

    private fun performanceMode(): PerformanceMode {
        val raw = getSharedPreferences("settings", MODE_PRIVATE).getString("performance_mode", PerformanceMode.BALANCED.name)
        return PerformanceProfiles.parse(raw)
    }

    private fun effectivePerformanceMode(): PerformanceMode {
        val requested = performanceMode()
        val healthRecommended = automationHealth.snapshot().recommendedMode
        // The user setting is a maximum-speed preference. Health may slow the engine down,
        // but it never silently runs faster than the selected safety level.
        return if (requested.ordinal >= healthRecommended.ordinal) requested else healthRecommended
    }

    private fun effectivePerformanceProfile(): PerformanceProfile = PerformanceProfiles.forMode(effectivePerformanceMode())

    private fun resetAdaptiveSession() {
        automationHealth.reset()
        throughputMeter.reset()
        effectiveMode = effectivePerformanceMode()
        val profile = PerformanceProfiles.forMode(effectiveMode)
        syncTiming = AdaptiveTimingPolicy(profile)
        extractionTiming = AdaptiveTimingPolicy(profile)
        publishTelemetry()
        DiagnosticLog.record("ADAPTIVE_SESSION_RESET", mapOf("mode" to effectiveMode.name))
    }

    private fun recordStageSuccess(stage: String) {
        stageCircuitBreaker.recordSuccess(stage)
        automationHealth.recordSuccess()
        refreshEffectiveMode()
        publishTelemetry()
    }

    private fun recordTransientFailure(code: String) {
        automationHealth.recordTransientFailure()
        DiagnosticLog.record("HEALTH_TRANSIENT_FAILURE", mapOf("code" to code, "score" to automationHealth.snapshot().score))
        refreshEffectiveMode()
        publishTelemetry()
    }

    private fun recordAmbiguity(code: String) {
        automationHealth.recordAmbiguity()
        DiagnosticLog.record("HEALTH_AMBIGUITY", mapOf("code" to code, "score" to automationHealth.snapshot().score))
        refreshEffectiveMode()
        publishTelemetry()
    }

    private fun recordHardFailure(code: String) {
        automationHealth.recordHardFailure()
        DiagnosticLog.record("HEALTH_HARD_FAILURE", mapOf("code" to code, "score" to automationHealth.snapshot().score))
        refreshEffectiveMode()
        publishTelemetry()
    }

    private fun refreshEffectiveMode() {
        val next = effectivePerformanceMode()
        if (next == effectiveMode) return
        val previous = effectiveMode
        effectiveMode = next
        val profile = PerformanceProfiles.forMode(next)
        syncTiming = AdaptiveTimingPolicy(profile)
        extractionTiming = AdaptiveTimingPolicy(profile)
        DiagnosticLog.record("ADAPTIVE_MODE_CHANGED", mapOf("from" to previous.name, "to" to next.name, "score" to automationHealth.snapshot().score))
    }

    private fun publishTelemetry() {
        val health = automationHealth.snapshot()
        val throughput = throughputMeter.snapshot()
        BotRuntime.updateTelemetry(
            healthScore = health.score,
            effectiveMode = effectiveMode.name,
            groupsPerMinute = throughput.groupsPerMinute,
            linksPerMinute = throughput.linksPerMinute,
        )
    }

    private fun screenMatches(root: AccessibilityNodeInfo, expected: ScreenKind, minConfidence: Int, expectedTitle: String? = null): Boolean {
        val evidence = AccessibilityTree.screenEvidence(root, expectedTitle)
        val matches = evidence.kind == expected && evidence.confidence >= minConfidence
        if (!matches) {
            DiagnosticLog.record(
                "SCREEN_GUARD_BLOCK",
                mapOf(
                    "expected" to expected.name,
                    "actual" to evidence.kind.name,
                    "confidence" to evidence.confidence,
                    "reasons" to evidence.reasons.joinToString(","),
                )
            )
        }
        return matches
    }

    private fun normalizeTitle(value: String) = value.trim().replace(Regex("\\s+"), " ").lowercase()
    private fun stableHash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private sealed interface Operation {
        data object None : Operation
        data class Sync(val stage: SyncStage) : Operation
        data class Extract(val stage: ExtractStage) : Operation
    }

    private enum class SyncStage { OPENING, VERIFY_FILTER, SCANNING }
    private enum class ExtractStage { PREPARE_LIST, ACTIVATE_GROUP_FILTER, OPEN_SEARCH, TYPE_QUERY, OPEN_RESULT, VERIFY_CHAT, SCAN_VIEWPORT, RETURNING }

    companion object {
        @Volatile var instance: WaAccessibilityService? = null
            private set

        val GROUP_LABELS = listOf("Groups", "المجموعات", "Group chats", "دردشات المجموعات")
        val CHAT_LABELS = listOf("Chats", "الدردشات")
        val SEARCH_LABELS = listOf("Search", "بحث")
        val GROUP_ID_HINTS = listOf("group_filter", "filter_groups", "groups_filter", "chat_filter_group")
        val CHAT_ID_HINTS = listOf("chats", "tab_chats", "navigation_chats")
    }
}
