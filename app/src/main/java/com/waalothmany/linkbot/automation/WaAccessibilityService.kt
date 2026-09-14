package com.waalothmany.linkbot.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.waalothmany.linkbot.ServiceLocator
import com.waalothmany.linkbot.core.exporter.AutomaticExportCoordinator
import com.waalothmany.linkbot.capability.AccessibilityConnectionMonitor
import com.waalothmany.linkbot.core.link.LinkExtractor
import com.waalothmany.linkbot.data.BotSessionEntity
import com.waalothmany.linkbot.data.GroupEntity
import com.waalothmany.linkbot.data.QueueItemEntity
import com.waalothmany.linkbot.data.WhatsAppInstanceEntity
import com.waalothmany.linkbot.runtime.BotForegroundService
import com.waalothmany.linkbot.runtime.DiagnosticLog
import com.waalothmany.linkbot.runtime.BotRuntime
import com.waalothmany.linkbot.runtime.RuntimePhase
import com.waalothmany.linkbot.runtime.RuntimeSnapshot
import com.waalothmany.linkbot.runtime.OperationLease
import com.waalothmany.linkbot.runtime.OperationLeaseController
import com.waalothmany.linkbot.runtime.OperationKind
import com.waalothmany.linkbot.runtime.TerminalOnceGate
import com.waalothmany.linkbot.runtime.ThroughputMeter
import com.waalothmany.linkbot.whatsapp.WhatsAppInstanceDetector
import com.waalothmany.linkbot.whatsapp.WhatsAppAdapter
import com.waalothmany.linkbot.whatsapp.WhatsAppAdapterRegistry
import com.waalothmany.linkbot.whatsapp.GenericDiscoverableWhatsAppAdapter
import com.waalothmany.linkbot.whatsapp.AdaptiveSelectorStore
import com.waalothmany.linkbot.whatsapp.SelectorRole
import com.waalothmany.linkbot.whatsapp.SelectorSignature
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
    private val operationLeaseController = OperationLeaseController()
    private val groupTerminalGate = TerminalOnceGate<String>()
    private var targetPackage: String? = null
    private var targetInstanceId: String? = null
    private var targetInstance: WhatsAppInstanceEntity? = null
    private var targetAdapter: WhatsAppAdapter = GenericDiscoverableWhatsAppAdapter
    private val adaptiveSelectorStore by lazy(LazyThreadSafetyMode.NONE) { AdaptiveSelectorStore(this) }
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
    private var syncOpeningProbeJob: Job? = null
    private var syncStrategy: SyncStrategy = SyncStrategyPolicy.initial()
    private var syncOpeningMisses: Int = 0
    private var selectionAttempts: Int = 0
    private var selectionMenuOpened: Boolean = false
    private var selectionModeActive: Boolean = false
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
    private var extractionNavigationProbeJob: Job? = null
    private var extractionNavigationMisses: Int = 0
    private var groupsCompleted = 0
    private var totalQueue = 0
    private var stageWatchdogJob: Job? = null
    private var accessibilityHeartbeatJob: Job? = null
    private val eventGuard = AtomicBoolean(false)
    private val eventCoalescer = AccessibilityEventCoalescer(contentWindowMs = 120L)
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
        AccessibilityConnectionMonitor.markConnected()
        accessibilityHeartbeatJob?.cancel()
        accessibilityHeartbeatJob = scope.launch {
            while (true) {
                delay(10_000)
                AccessibilityConnectionMonitor.markHeartbeat()
            }
        }
        DiagnosticLog.record("ACCESSIBILITY_CONNECTED")
        scope.launch {
            val active = ServiceLocator.database.sessionDao().active()
            if (active != null && active.type == "EXTRACTION") {
                val instanceEntity = ServiceLocator.database.instanceDao().get(active.instanceId)
                if (instanceEntity != null) {
                    extractionSessionId = active.id
                    targetInstanceId = active.instanceId
                    targetPackage = instanceEntity.packageName
                    targetInstance = instanceEntity
                    targetAdapter = WhatsAppAdapterRegistry.byId(instanceEntity.adapterId)
                    extractionMode = runCatching { AutomationMode.valueOf(active.mode) }.getOrDefault(AutomationMode.NEW_ONLY)
                    val recoveredQueue = ServiceLocator.database.queueDao().forSession(active.id)
                    val progress = QueueProgressPolicy.summarize(recoveredQueue.map { it.state })
                    totalQueue = progress.total
                    groupsCompleted = progress.completed
                    resetAdaptiveSession()
                    BotRuntime.pause()
                    val recoveredLease = operationLeaseController.beginExclusive(OperationKind.EXTRACTION)
                    if (recoveredLease == null) {
                        DiagnosticLog.record("RECOVERY_LEASE_BUSY", mapOf("session" to active.id.take(12)))
                        return@launch
                    }
                    operation = Operation.Extract(ExtractStage.PREPARE_LIST, recoveredLease)
                    setExtractStage(ExtractStage.PREPARE_LIST)
                    val autoResume = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("auto_resume", true)
                    val shouldAutoResume = autoResume && active.state != "PAUSED"
                    BotRuntime.update(
                        RuntimeSnapshot(
                            RuntimePhase.PAUSED,
                            if (shouldAutoResume) "Resuming recovered session" else "Resume available",
                            "Recovered ${progress.completed}/${progress.total} groups • ${progress.failed} failed",
                            progress.completed,
                            progress.total,
                        )
                    )
                    if (shouldAutoResume) {
                        DiagnosticLog.record("AUTO_RESUME_TRIGGERED", mapOf("session" to active.id.take(12)))
                        resumeAutomation()
                    }
                } else {
                    BotRuntime.update(RuntimeSnapshot(phase = RuntimePhase.READY, title = "Accessibility ready"))
                }
            } else {
                BotRuntime.update(RuntimeSnapshot(phase = RuntimePhase.READY, title = "Accessibility ready"))
            }
        }
    }

    override fun onDestroy() {
        accessibilityHeartbeatJob?.cancel()
        accessibilityHeartbeatJob = null
        if (operation != Operation.None) suspendForAccessibilityLoss("ACCESSIBILITY_DISCONNECTED")
        stageWatchdogJob?.cancel()
        syncProbeJob?.cancel()
        syncOpeningProbeJob?.cancel()
        extractionProbeJob?.cancel()
        extractionNavigationProbeJob?.cancel()
        scope.cancel()
        if (instance === this) instance = null
        AccessibilityConnectionMonitor.markDisconnected()
        DiagnosticLog.record("ACCESSIBILITY_DISCONNECTED")
        super.onDestroy()
    }

    override fun onInterrupt() {
        AccessibilityConnectionMonitor.markInterrupted()
        DiagnosticLog.record("ACCESSIBILITY_INTERRUPTED")
        suspendForAccessibilityLoss("ACCESSIBILITY_INTERRUPTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        AccessibilityConnectionMonitor.markActivity()
        val pkg = event?.packageName?.toString() ?: return
        val wanted = targetPackage ?: return
        if (pkg != wanted) return
        val eventClass = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> AccessibilityEventClass.WINDOW_STATE
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> AccessibilityEventClass.WINDOW_CONTENT_CHANGED
            else -> AccessibilityEventClass.OTHER
        }
        if (!eventCoalescer.shouldProcess(
                eventClass = eventClass,
                nowMs = System.currentTimeMillis(),
                operationGeneration = currentOperationLease()?.generation,
                windowId = event.windowId,
            )
        ) return
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


    private fun suspendForAccessibilityLoss(code: String) {
        if (operation == Operation.None) return
        BotRuntime.pause()
        stageWatchdogJob?.cancel()
        syncProbeJob?.cancel()
        syncOpeningProbeJob?.cancel()
        extractionProbeJob?.cancel()
        extractionNavigationProbeJob?.cancel()
        DiagnosticLog.record("AUTOMATION_SUSPENDED_ACCESSIBILITY", mapOf("reason" to code))
        BotRuntime.update(
            BotRuntime.state.value.copy(
                phase = RuntimePhase.PAUSED,
                title = "Accessibility unavailable",
                detail = "Automation suspended safely until the Accessibility service reconnects.",
                error = code,
            )
        )
        val session = extractionSessionId
        val queue = currentQueue
        if (session != null || queue != null) {
            val lease = currentOperationLease()
            scope.launch {
                if (lease != null && !operationLeaseController.isCurrent(lease)) return@launch
                if (session != null) ServiceLocator.database.sessionDao().updateState(session, "PAUSED")
                if (queue != null) ServiceLocator.database.queueDao().updateState(queue.id, "PAUSED", queue.attempts, code)
            }
        }
    }

    fun pauseAutomation() {
        BotRuntime.pause()
        stageWatchdogJob?.cancel()
        val session = extractionSessionId
        val queue = currentQueue
        val group = currentGroup
        val checkpoint = currentCheckpointForPersistence()
        scope.launch {
            if (session != null) ServiceLocator.database.sessionDao().updateState(session, "PAUSED")
            if (queue != null) ServiceLocator.database.queueDao().updateState(queue.id, "PAUSED", queue.attempts, null)
            if (group != null) ServiceLocator.groups.updateExtraction(group.id, "PENDING", checkpoint ?: group.checkpoint)
            DiagnosticLog.record("PAUSE_CHECKPOINT_PERSISTED", mapOf("hasCheckpoint" to (checkpoint != null), "group" to (group?.id?.take(12) ?: "none")))
        }
    }

    fun resumeAutomation() {
        BotRuntime.resume()
        armCurrentStageWatchdog()
        val sync = operation as? Operation.Sync
        if (sync?.stage in setOf(SyncStage.OPENING, SyncStage.ALL_CHATS_OPENING)) {
            scheduleSyncOpeningProbe()
        }
        val session = extractionSessionId
        scope.launch {
            if (session != null) {
                ServiceLocator.database.sessionDao().updateState(session, "RUNNING")
                currentQueue?.let { ServiceLocator.database.queueDao().updateState(it.id, "RUNNING", it.attempts, null) }
            }
            targetInstance?.let { WhatsAppInstanceDetector.launch(this@WaAccessibilityService, it) }
                ?: targetPackage?.let { WhatsAppInstanceDetector.launch(this@WaAccessibilityService, it) }
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
        val queue = currentQueue
        val group = currentGroup
        val checkpoint = currentCheckpointForPersistence()
        scope.launch {
            if (queue != null) ServiceLocator.database.queueDao().updateState(queue.id, "PARTIAL", queue.attempts, "USER_STOPPED")
            if (group != null) ServiceLocator.groups.updateExtraction(group.id, "PENDING", checkpoint ?: group.checkpoint)
            if (session != null) ServiceLocator.database.sessionDao().updateState(session, "STOPPED", completedAt = null)
        }
        finishStopped()
    }

    fun skipCurrent() { BotRuntime.skip() }

    fun retryFailed() {
        if (operation != Operation.None) {
            DiagnosticLog.record("OPERATION_START_REJECTED_BUSY", mapOf("requested" to "RETRY_FAILED"))
            return
        }
        val retryLease = operationLeaseController.beginExclusive(OperationKind.RETRY_FAILED) ?: return
        operation = Operation.Extract(ExtractStage.PREPARE_LIST, retryLease)
        scope.launch {
            val session = extractionSessionId?.let { id ->
                ServiceLocator.database.sessionDao().latest()?.takeIf { it.id == id }
            } ?: ServiceLocator.database.sessionDao().latest()
            if (session == null || session.type != "EXTRACTION") {
                BotRuntime.update(RuntimeSnapshot(RuntimePhase.ERROR, "No extraction session", "Nothing to retry"))
                clearOperation(retryLease)
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
                clearOperation(retryLease)
                return@launch
            }
            val instanceEntity = ServiceLocator.database.instanceDao().get(session.instanceId) ?: run {
                clearOperation(retryLease)
                return@launch
            }
            if (!operationLeaseController.isCurrent(retryLease)) return@launch
            extractionSessionId = session.id
            targetInstanceId = session.instanceId
            targetPackage = instanceEntity.packageName
            targetInstance = instanceEntity
            targetAdapter = WhatsAppAdapterRegistry.byId(instanceEntity.adapterId)
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
            WhatsAppInstanceDetector.launch(this@WaAccessibilityService, instanceEntity)
        }
    }

    fun resumePending() {
        if (operation != Operation.None) {
            DiagnosticLog.record("OPERATION_START_REJECTED_BUSY", mapOf("requested" to "RESUME_PENDING"))
            return
        }
        val lease = operationLeaseController.beginExclusive(OperationKind.EXTRACTION) ?: return
        operation = Operation.Extract(ExtractStage.PREPARE_LIST, lease)
        scope.launch {
            val session = ServiceLocator.database.sessionDao().latestResumable()
            if (session == null) {
                BotRuntime.update(RuntimeSnapshot(RuntimePhase.READY, "No pending session", "No interrupted extraction session is available."))
                clearOperation(lease)
                return@launch
            }
            val queue = ServiceLocator.database.queueDao().forSession(session.id)
            val resumable = queue.any { QueuePersistencePolicy.isResumable(QueuePersistencePolicy.normalize(it.state)) }
            if (!resumable) {
                BotRuntime.update(RuntimeSnapshot(RuntimePhase.READY, "Nothing pending", "The latest extraction session has no resumable queue items."))
                clearOperation(lease)
                return@launch
            }
            val instance = ServiceLocator.database.instanceDao().get(session.instanceId)
            if (instance == null || !WhatsAppInstanceDetector.isLaunchable(this@WaAccessibilityService, instance)) {
                BotRuntime.update(RuntimeSnapshot(RuntimePhase.ERROR, "WhatsApp unavailable", "The saved WhatsApp instance is not visible in the current Android profile context."))
                clearOperation(lease)
                return@launch
            }
            extractionSessionId = session.id
            targetInstanceId = session.instanceId
            targetPackage = instance.packageName
            targetInstance = instance
            targetAdapter = WhatsAppAdapterRegistry.byId(instance.adapterId)
            extractionMode = runCatching { AutomationMode.valueOf(session.mode) }.getOrDefault(AutomationMode.NEW_ONLY)
            val states = queue.map { item ->
                val normalized = QueuePersistencePolicy.normalize(item.state)
                if (QueuePersistencePolicy.isResumable(normalized)) {
                    ServiceLocator.database.queueDao().updateState(item.id, "WAITING", item.attempts, null)
                    "WAITING"
                } else item.state
            }
            val progress = QueueProgressPolicy.summarize(states)
            totalQueue = progress.total
            groupsCompleted = progress.completed
            currentQueue = null
            currentGroup = null
            resetAdaptiveSession()
            setExtractStage(ExtractStage.PREPARE_LIST)
            ServiceLocator.database.sessionDao().updateState(session.id, "RUNNING", completedAt = null)
            BotRuntime.resetControlFlags()
            BotRuntime.update(RuntimeSnapshot(RuntimePhase.EXTRACTING, "Resuming pending extraction", extractionMode.name, progress.completed, progress.total))
            startRuntimeService("Resuming extraction")
            WhatsAppInstanceDetector.launch(this@WaAccessibilityService, instance)
        }
    }

    fun startGroupSync(instance: WhatsAppInstanceEntity): Boolean {
        val instanceId = instance.id
        val packageName = instance.packageName
        if (operation != Operation.None) {
            DiagnosticLog.record(
                "OPERATION_START_REJECTED_BUSY",
                mapOf("requested" to "SYNC", "package" to (targetPackage ?: packageName)),
            )
            return false
        }
        val canLaunch = WhatsAppInstanceDetector.isLaunchable(this, instance)
        if (!canLaunch) {
            BotRuntime.update(RuntimeSnapshot(RuntimePhase.ERROR, "WhatsApp unavailable", packageName))
            return false
        }
        val syncLease = operationLeaseController.beginExclusive(OperationKind.SYNC) ?: run {
            DiagnosticLog.record("OPERATION_START_REJECTED_BUSY", mapOf("requested" to "SYNC_LEASE"))
            return false
        }
        operation = Operation.Sync(SyncStage.OPENING, syncLease)
        BotRuntime.resetControlFlags()
        targetPackage = packageName
        targetInstanceId = instanceId
        targetInstance = instance
        targetAdapter = WhatsAppAdapterRegistry.byId(instance.adapterId)
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
        syncOpeningProbeJob?.cancel()
        syncOpeningProbeJob = null
        syncStrategy = SyncStrategyPolicy.initial()
        syncOpeningMisses = 0
        selectionAttempts = 0
        selectionMenuOpened = false
        selectionModeActive = false
        filterVerifyAttempts = 0
        BotRuntime.update(RuntimeSnapshot(RuntimePhase.SYNCING_GROUPS, "Preparing sync", "Loading local group identities"))
        DiagnosticLog.record(
            "SYNC_START",
            mapOf(
                "instance" to instanceId.take(12),
                "package" to packageName,
                "strategy" to syncStrategy.name,
            ),
        )
        startRuntimeService("Group sync")
        scope.launch {
            syncExistingGroups = ServiceLocator.database.groupDao().byInstance(instanceId)
            if (!operationLeaseController.isCurrent(syncLease)) return@launch
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
            BotRuntime.update(RuntimeSnapshot(RuntimePhase.SYNCING_GROUPS, "Opening WhatsApp", "Preparing group sync"))
            WhatsAppInstanceDetector.launch(this@WaAccessibilityService, instance)
        }
        return true
    }

    fun startExtraction(instance: WhatsAppInstanceEntity, mode: AutomationMode) {
        val instanceId = instance.id
        val packageName = instance.packageName
        if (operation != Operation.None) {
            DiagnosticLog.record("OPERATION_START_REJECTED_BUSY", mapOf("requested" to "EXTRACTION", "package" to packageName))
            return
        }
        val extractionLease = operationLeaseController.beginExclusive(OperationKind.EXTRACTION) ?: run {
            DiagnosticLog.record("OPERATION_START_REJECTED_BUSY", mapOf("requested" to "EXTRACTION_LEASE"))
            return
        }
        operation = Operation.Extract(ExtractStage.PREPARE_LIST, extractionLease)
        BotRuntime.resetControlFlags()
        extractionMode = mode
        targetPackage = packageName
        targetInstanceId = instanceId
        targetInstance = instance
        targetAdapter = WhatsAppAdapterRegistry.byId(instance.adapterId)
        groupsCompleted = 0
        resetAdaptiveSession()
        scope.launch {
            val selected = ServiceLocator.groups.selected(instanceId)
            if (!operationLeaseController.isCurrent(extractionLease)) return@launch
            if (selected.isEmpty()) {
                BotRuntime.update(RuntimeSnapshot(RuntimePhase.ERROR, "Nothing selected", "Select at least one group"))
                clearOperation(extractionLease)
                return@launch
            }
            val eligibleIds = ExtractionSelectionPolicy.eligible(
                selected.map { ExtractionSelectionItem(it.id, it.unread) },
                mode,
            ).mapTo(LinkedHashSet()) { it.id }
            val eligible = selected.filter { it.id in eligibleIds }
            if (eligible.isEmpty()) {
                val detail = if (mode == AutomationMode.UNREAD_ONLY) {
                    "No selected groups are currently marked unread. Synchronize first or select unread groups."
                } else {
                    "No eligible groups remain for this extraction mode."
                }
                BotRuntime.update(RuntimeSnapshot(RuntimePhase.ERROR, "Nothing eligible", detail))
                clearOperation(extractionLease)
                return@launch
            }
            val byId = eligible.associateBy { it.id }
            val orderedIds = SmartQueuePolicy.order(
                eligible.map { group ->
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
            if (!operationLeaseController.isCurrent(extractionLease)) return@launch
            setExtractStage(ExtractStage.PREPARE_LIST)
            BotRuntime.update(RuntimeSnapshot(RuntimePhase.EXTRACTING, "Starting extraction", mode.name, 0, ordered.size))
            DiagnosticLog.record("EXTRACTION_START", mapOf("mode" to mode.name, "groups" to ordered.size))
            startRuntimeService("Extracting links")
            WhatsAppInstanceDetector.launch(this@WaAccessibilityService, instance)
        }
    }

    private fun adapterSuffixes(role: SelectorRole): Set<String> = when (role) {
        SelectorRole.GROUPS_FILTER -> targetAdapter.groupFilterIdSuffixes
        SelectorRole.ALL_FILTER -> targetAdapter.allFilterIdSuffixes
        SelectorRole.CHATS_ANCHOR -> targetAdapter.chatsAnchorIdSuffixes
        SelectorRole.SELECT_ALL -> targetAdapter.selectAllIdSuffixes
        SelectorRole.CONVERSATION_ROW_TITLE -> targetAdapter.conversationRowTitleIdSuffixes
    }

    private fun learnedSelectors(role: SelectorRole): List<SelectorSignature> =
        targetInstanceId?.let { instanceId ->
            adaptiveSelectorStore.candidates(instanceId, role).map { it.signature }
        }.orEmpty()

    private fun findAdaptiveControl(
        root: AccessibilityNodeInfo,
        role: SelectorRole,
        labels: Collection<String>,
        peerLabels: Collection<String> = emptyList(),
        legacyHints: Collection<String> = emptyList(),
    ): AccessibilityNodeInfo? {
        AccessibilityTree.findBySelectorSignatures(root, learnedSelectors(role))?.let { return it }
        AccessibilityTree.findByViewIdSuffixes(root, adapterSuffixes(role))?.let { return it }
        targetPackage?.let { pkg ->
            AccessibilityTree.findByViewIdHints(root, targetAdapter.resourceIdHintsFor(pkg, role))?.let { return it }
        }
        if (legacyHints.isNotEmpty()) AccessibilityTree.findByViewIdHints(root, legacyHints)?.let { return it }
        return if (peerLabels.isNotEmpty()) {
            AccessibilityTree.findFilterControl(root, labels, peerLabels)
        } else {
            AccessibilityTree.findByControlLabel(root, labels)
                ?: AccessibilityTree.findByAnyText(root, labels)
        }
    }

    private fun recordVerifiedSelector(role: SelectorRole, node: AccessibilityNodeInfo?) {
        val instanceId = targetInstanceId ?: return
        val signature = AccessibilityTree.selectorSignature(node) ?: return
        adaptiveSelectorStore.recordVerified(instanceId, role, signature)
        DiagnosticLog.record(
            "ADAPTIVE_SELECTOR_VERIFIED",
            mapOf("role" to role.name, "idSuffix" to signature.resourceIdSuffix.orEmpty(), "hasLabel" to (signature.normalizedLabel != null)),
        )
    }

    private fun recordFailedSelector(role: SelectorRole, node: AccessibilityNodeInfo?) {
        val instanceId = targetInstanceId ?: return
        val signature = AccessibilityTree.selectorSignature(node) ?: return
        adaptiveSelectorStore.recordFailure(instanceId, role, signature)
        DiagnosticLog.record("ADAPTIVE_SELECTOR_REJECTED", mapOf("role" to role.name, "idSuffix" to signature.resourceIdSuffix.orEmpty()))
    }

    private fun findGroupsFilter(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findAdaptiveControl(
            root = root,
            role = SelectorRole.GROUPS_FILTER,
            labels = targetAdapter.groupFilterLabels,
            peerLabels = targetAdapter.filterPeerLabels,
            legacyHints = GROUP_ID_HINTS,
        )

    private fun findAllFilter(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findAdaptiveControl(
            root = root,
            role = SelectorRole.ALL_FILTER,
            labels = targetAdapter.allFilterLabels,
            peerLabels = targetAdapter.filterPeerLabels,
        )

    private fun findChatsAnchor(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findAdaptiveControl(
            root = root,
            role = SelectorRole.CHATS_ANCHOR,
            labels = CHAT_LABELS,
            legacyHints = CHAT_ID_HINTS,
        )

    private fun findSelectAll(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findAdaptiveControl(
            root = root,
            role = SelectorRole.SELECT_ALL,
            labels = targetAdapter.selectAllLabels,
            legacyHints = SELECT_ALL_ID_HINTS,
        )

    private fun handleSync(root: AccessibilityNodeInfo, eventType: Int) {
        val op = operation as? Operation.Sync ?: return
        when (op.stage) {
            SyncStage.OPENING -> openGroupsFilter(root)
            SyncStage.VERIFY_FILTER -> verifyGroupFilter(root)
            SyncStage.SCANNING -> scanGroupViewport(root, eventType)
            SyncStage.SELECTION_ENTER -> enterSelectionMode(root)
            SyncStage.SELECTION_SELECT_ALL -> selectAllInSelectionMode(root)
            SyncStage.SELECTION_SCANNING -> scanGroupViewport(root, eventType)
            SyncStage.ALL_CHATS_OPENING -> openAllChatsFallback(root)
            SyncStage.ALL_CHATS_SCANNING -> scanGroupViewport(root, eventType)
        }
    }

    private fun openGroupsFilter(root: AccessibilityNodeInfo) {
        val groups = findGroupsFilter(root)
        val chats = findChatsAnchor(root)
        val evidence = AccessibilityTree.screenEvidence(root)

        syncOpeningMisses++
        val action = NavigationFallbackPolicy.decide(
            groupsFound = groups != null,
            chatsFound = chats != null,
            missCount = syncOpeningMisses,
            inChat = evidence.kind == ScreenKind.CHAT && evidence.confidence >= 60,
        )

        if (syncOpeningMisses == 1 || syncOpeningMisses % 3 == 0 || action == NavigationFallbackAction.FALLBACK_ALL_CHATS) {
            DiagnosticLog.record(
                "SYNC_NAV_DECISION",
                mapOf(
                    "miss" to syncOpeningMisses,
                    "screen" to evidence.kind.name,
                    "confidence" to evidence.confidence,
                    "groupsFound" to (groups != null),
                    "chatsFound" to (chats != null),
                    "action" to action.name,
                    "package" to root.packageName?.toString().orEmpty(),
                    "strategy" to syncStrategy.name,
                ),
            )
        }

        when (action) {
            NavigationFallbackAction.OPEN_GROUPS -> {
                if (clickTarget(groups)) {
                    syncOpeningMisses = 0
                    recordStageSuccess("SYNC_OPENING")
                    filterVerifyAttempts = 0
                    DiagnosticLog.record(
                        "SYNC_GROUP_FILTER_CLICKED",
                        mapOf("package" to targetPackage.orEmpty(), "strategy" to syncStrategy.name),
                    )
                    setSyncStage(SyncStage.VERIFY_FILTER)
                    BotRuntime.update(BotRuntime.state.value.copy(title = "Groups filter", detail = "Verifying active filter"))
                    scheduleFilterVerificationProbe()
                }
            }

            NavigationFallbackAction.ANCHOR_CHATS -> {
                clickTarget(chats)
            }

            NavigationFallbackAction.BACK_FROM_CHAT -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }

            NavigationFallbackAction.BACK_TOWARD_CHATS -> {
                DiagnosticLog.record("SYNC_NAV_BACK_RECOVERY", mapOf("miss" to syncOpeningMisses))
                performGlobalAction(GLOBAL_ACTION_BACK)
            }

            NavigationFallbackAction.FALLBACK_ALL_CHATS -> {
                switchSyncStrategy(
                    SyncStrategySignal.GROUP_FILTER_UNAVAILABLE,
                    "Groups filter unavailable after verified Chats anchor",
                )
            }

            NavigationFallbackAction.WAIT -> Unit
        }
    }

    private fun openAllChatsFallback(root: AccessibilityNodeInfo) {
        val chats = findChatsAnchor(root)

        // First establish Chats as the navigation anchor. We deliberately wait for a
        // fresh accessibility snapshot before scanning; the root that existed before
        // the click can still represent Updates/Tools/Communities.
        if (syncOpeningMisses == 0 && chats != null) {
            clickTarget(chats)
            syncOpeningMisses = 1
            DiagnosticLog.record("SYNC_ALL_CHATS_ANCHOR_CLICKED", mapOf("package" to targetPackage.orEmpty()))
            return
        }

        val all = findAllFilter(root)
        if (syncOpeningMisses <= 1 && all != null) {
            clickTarget(all)
            syncOpeningMisses = 2
            DiagnosticLog.record("SYNC_ALL_FILTER_CLICKED")
            return
        }

        val list = AccessibilityTree.bestConversationScrollable(root)
        val screenEvidence = AccessibilityTree.screenEvidence(root)
        val safeAllChatsSurface = list != null &&
            screenEvidence.kind != ScreenKind.CHAT &&
            screenEvidence.kind != ScreenKind.SEARCH &&
            (chats != null || all != null)
        if (safeAllChatsSurface) {
            if (all != null && AccessibilityTree.controlSelectionEvidence(all) == FilterEvidence.ACTIVE) {
                recordVerifiedSelector(SelectorRole.ALL_FILTER, all)
            }
            if (chats != null) recordVerifiedSelector(SelectorRole.CHATS_ANCHOR, chats)
            recordStageSuccess("SYNC_ALL_CHATS_OPENING")
            syncEndGuard.reset()
            syncPreviousViewport = emptyList()
            syncRequestedFingerprint = null
            setSyncStage(SyncStage.ALL_CHATS_SCANNING)
            DiagnosticLog.record(
                "SYNC_STRATEGY_ACTIVE",
                mapOf("strategy" to SyncStrategy.ALL_CHATS_CLASSIFY.name, "safety" to "conservative"),
            )
            scanGroupViewport(root, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, fromProbe = true)
        }
    }

    private fun enterSelectionMode(root: AccessibilityNodeInfo) {
        val row = AccessibilityTree.firstConversationRowNode(root)
        if (row != null && longClickTarget(row)) {
            selectionAttempts = 0
            selectionMenuOpened = false
            selectionModeActive = true
            recordStageSuccess("SYNC_SELECTION_ENTER")
            setSyncStage(SyncStage.SELECTION_SELECT_ALL)
            DiagnosticLog.record("SYNC_SELECTION_MODE_ENTERED", mapOf("groupsAlreadySeen" to syncSeen.size))
            scheduleSelectionProbe()
            return
        }

        selectionAttempts++
        if (selectionAttempts >= 5) {
            DiagnosticLog.record("SYNC_SELECTION_UNAVAILABLE", mapOf("stage" to "ENTER", "groups" to syncSeen.size))
            switchSyncStrategy(
                SyncStrategySignal.SELECTION_UNAVAILABLE,
                "Selection mode could not be entered safely",
            )
        }
    }

    private fun selectAllInSelectionMode(root: AccessibilityNodeInfo) {
        val selectAll = findSelectAll(root)
        if (selectAll != null && clickTarget(selectAll)) {
            recordVerifiedSelector(SelectorRole.SELECT_ALL, selectAll)
            recordStageSuccess("SYNC_SELECTION_SELECT_ALL")
            syncEndGuard.reset()
            syncPreviousViewport = emptyList()
            syncRequestedFingerprint = null
            setSyncStage(SyncStage.SELECTION_SCANNING)
            DiagnosticLog.record("SYNC_SELECT_ALL_APPLIED", mapOf("groupsAlreadySeen" to syncSeen.size))
            scheduleSyncProbe(syncTiming.nextProbeDelay(scrollAccepted = true))
            return
        }

        if (!selectionMenuOpened) {
            val more = AccessibilityTree.findByViewIdHints(root, MORE_ID_HINTS)
                ?: AccessibilityTree.findByControlLabel(root, MORE_LABELS)
                ?: AccessibilityTree.findByAnyText(root, MORE_LABELS)
            if (more != null && clickTarget(more)) {
                selectionMenuOpened = true
                selectionAttempts++
                scheduleSelectionProbe()
                return
            }
        }

        selectionAttempts++
        if (selectionAttempts >= 8) {
            DiagnosticLog.record("SYNC_SELECTION_UNAVAILABLE", mapOf("stage" to "SELECT_ALL", "groups" to syncSeen.size))
            if (selectionModeActive) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                selectionModeActive = false
            }
            switchSyncStrategy(
                SyncStrategySignal.SELECTION_UNAVAILABLE,
                "Select-All could not be verified safely",
            )
        }
    }

    private fun switchSyncStrategy(signal: SyncStrategySignal, reason: String) {
        val previous = syncStrategy
        val next = SyncStrategyPolicy.next(previous, signal)
        if (next == previous) return
        syncStrategy = next
        syncOpeningMisses = 0
        selectionAttempts = 0
        selectionMenuOpened = false
        syncEndGuard.reset()
        syncRequestedFingerprint = null
        syncProbeJob?.cancel()
        syncProbeJob = null
        DiagnosticLog.record(
            "SYNC_STRATEGY_SWITCH",
            mapOf("from" to previous.name, "to" to next.name, "signal" to signal.name, "reason" to reason),
        )
        when (next) {
            SyncStrategy.GROUP_FILTER_SCROLL -> setSyncStage(SyncStage.OPENING)
            SyncStrategy.GROUP_FILTER_SELECT_ALL -> setSyncStage(SyncStage.SELECTION_ENTER)
            SyncStrategy.ALL_CHATS_CLASSIFY -> setSyncStage(SyncStage.ALL_CHATS_OPENING)
        }
        BotRuntime.update(
            BotRuntime.state.value.copy(
                title = "Changing sync strategy",
                detail = next.name.replace('_', ' '),
            )
        )
    }

    private fun verifyGroupFilter(root: AccessibilityNodeInfo) {
        filterVerifyAttempts++
        val groupControl = findGroupsFilter(root)
        val evidence = AccessibilityTree.controlSelectionEvidence(groupControl)
        val decision = FilterVerificationPolicy.decide(evidence, effectiveMode, filterVerifyAttempts)
        when (decision) {
            FilterDecision.PROCEED -> {
                recordVerifiedSelector(SelectorRole.GROUPS_FILTER, groupControl)
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
                    val groups = findGroupsFilter(root)
                    clickTarget(groups)
                }
                scheduleFilterVerificationProbe()
            }
            FilterDecision.FAIL -> {
                recordFailedSelector(SelectorRole.GROUPS_FILTER, groupControl)
                recordTransientFailure("GROUP_FILTER_VERIFY_FAILED")
                DiagnosticLog.record(
                    "GROUP_FILTER_VERIFY_FAILED",
                    mapOf("attempts" to filterVerifyAttempts, "mode" to effectiveMode.name, "strategy" to syncStrategy.name),
                )
                switchSyncStrategy(
                    SyncStrategySignal.GROUP_FILTER_UNAVAILABLE,
                    "Groups filter could not be verified safely",
                )
            }
        }
    }

    private fun scheduleSelectionProbe() {
        syncProbeJob?.cancel()
        syncProbeJob = scope.launch {
            delay(NavigationProbePolicy.delayMs(effectiveMode, 0))
            val current = operation as? Operation.Sync ?: return@launch
            if (current.stage != SyncStage.SELECTION_SELECT_ALL || BotRuntime.pauseRequested || BotRuntime.stopRequested) return@launch
            rootInActiveWindow?.let(::selectAllInSelectionMode)
        }
    }

    private fun scheduleSyncOpeningProbe() {
        val expectedLease = currentOperationLease() ?: return
        syncOpeningProbeJob?.cancel()
        syncOpeningProbeJob = scope.launch {
            val probeMode = effectiveMode
            repeat(NavigationProbePolicy.maxAttempts(probeMode)) { attempt ->
                delay(NavigationProbePolicy.delayMs(probeMode, attempt))
                if (!operationLeaseController.isCurrent(expectedLease)) {
                    DiagnosticLog.record("STALE_OPERATION_CALLBACK_IGNORED", mapOf("source" to "sync-opening", "generation" to expectedLease.generation))
                    return@launch
                }
                val current = operation as? Operation.Sync ?: return@launch
                if (current.stage !in setOf(SyncStage.OPENING, SyncStage.ALL_CHATS_OPENING) ||
                    BotRuntime.pauseRequested || BotRuntime.stopRequested
                ) return@launch

                val root = rootInActiveWindow
                if (root != null && root.packageName?.toString() == targetPackage && eventGuard.compareAndSet(false, true)) {
                    try {
                        handleSync(root, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                    } finally {
                        eventGuard.set(false)
                    }
                    val after = operation as? Operation.Sync
                    if (after == null || after.stage !in setOf(SyncStage.OPENING, SyncStage.ALL_CHATS_OPENING)) {
                        return@launch
                    }
                }
            }
        }
    }

    private fun scheduleFilterVerificationProbe() {
        val expectedLease = currentOperationLease() ?: return
        syncProbeJob?.cancel()
        syncProbeJob = scope.launch {
            delay(syncTiming.nextProbeDelay(scrollAccepted = false))
            if (!operationLeaseController.isCurrent(expectedLease)) return@launch
            val current = operation as? Operation.Sync ?: return@launch
            if (current.stage != SyncStage.VERIFY_FILTER || BotRuntime.pauseRequested || BotRuntime.stopRequested) return@launch
            rootInActiveWindow?.let(::verifyGroupFilter)
        }
    }

    private fun scanGroupViewport(root: AccessibilityNodeInfo, eventType: Int, fromProbe: Boolean = false) {
        val conservativeAllChats = syncStrategy == SyncStrategy.ALL_CHATS_CLASSIFY
        val selectionModeScan = syncStrategy == SyncStrategy.GROUP_FILTER_SELECT_ALL &&
            (operation as? Operation.Sync)?.stage == SyncStage.SELECTION_SCANNING
        if (conservativeAllChats || selectionModeScan) {
            val evidence = AccessibilityTree.screenEvidence(root)
            val safeConversationList = AccessibilityTree.bestConversationScrollable(root) != null &&
                evidence.kind != ScreenKind.CHAT && evidence.kind != ScreenKind.SEARCH
            if (!safeConversationList) {
                scheduleSyncProbe(syncTiming.nextProbeDelay(scrollAccepted = false))
                return
            }
        } else if (!screenMatches(root, ScreenKind.GROUP_LIST, minConfidence = 60)) {
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

        val rows = if (conservativeAllChats) {
            AccessibilityTree.conservativeGroupRowCandidates(root)
        } else {
            AccessibilityTree.rowCandidates(root)
        }
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
        BotRuntime.update(BotRuntime.state.value.copy(phase = RuntimePhase.SYNCING_GROUPS, title = "Synchronizing groups", detail = "${syncSeen.size} groups found", current = syncSeen.size, total = 0))
        publishTelemetry()

        val scrollable = AccessibilityTree.bestConversationScrollable(root)
        val moved = scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
        val completed = syncEndGuard.observe(ProgressSample(listFingerprint, newCount, moved))
        if (completed) {
            when (syncStrategy) {
                SyncStrategy.GROUP_FILTER_SCROLL -> {
                    if (syncSeen.isNotEmpty()) {
                        syncStrategy = SyncStrategyPolicy.next(
                            syncStrategy,
                            SyncStrategySignal.DIRECT_SCAN_STALLED,
                        )
                        selectionAttempts = 0
                        selectionMenuOpened = false
                        syncEndGuard.reset()
                        syncRequestedFingerprint = null
                        DiagnosticLog.record(
                            "SYNC_SECONDARY_STRATEGY_START",
                            mapOf(
                                "strategy" to syncStrategy.name,
                                "groupsAlreadySeen" to syncSeen.size,
                            ),
                        )
                        setSyncStage(SyncStage.SELECTION_ENTER)
                        enterSelectionMode(root)
                    } else {
                        switchSyncStrategy(
                            SyncStrategySignal.GROUP_FILTER_UNAVAILABLE,
                            "Group-filter scan completed without any group rows",
                        )
                    }
                }
                SyncStrategy.GROUP_FILTER_SELECT_ALL -> {
                    completeSync(markMissing = true, verification = "direct-scroll + select-all")
                }
                SyncStrategy.ALL_CHATS_CLASSIFY -> {
                    completeSync(markMissing = false, verification = "conservative all-chats fallback")
                }
            }
            return
        }

        syncRequestedFingerprint = if (moved) listFingerprint else null
        syncActionIssuedAt = if (moved) System.currentTimeMillis() else null
        scheduleSyncProbe(syncTiming.nextProbeDelay(moved))
    }

    private fun scheduleSyncProbe(delayMs: Long) {
        val expectedLease = currentOperationLease() ?: return
        syncProbeJob?.cancel()
        syncProbeJob = scope.launch {
            delay(delayMs)
            if (!operationLeaseController.isCurrent(expectedLease)) {
                DiagnosticLog.record("STALE_OPERATION_CALLBACK_IGNORED", mapOf("source" to "sync-probe", "generation" to expectedLease.generation))
                return@launch
            }
            if (operation !is Operation.Sync || BotRuntime.pauseRequested || BotRuntime.stopRequested) return@launch
            rootInActiveWindow?.let { scanGroupViewport(it, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, fromProbe = true) }
        }
    }

    private fun completeSync(
        markMissing: Boolean,
        verification: String,
    ) {
        val syncOperation = operation as? Operation.Sync ?: return
        val lease = syncOperation.lease
        if (!operationLeaseController.claimTerminal(lease)) {
            DiagnosticLog.record("SYNC_TERMINAL_DUPLICATE_IGNORED", mapOf("generation" to lease.generation))
            return
        }
        val instanceId = targetInstanceId
        val completedSyncId = syncId
        val previousPresent = syncExistingGroups.count { it.present }
        val discovered = syncSeen.size
        val coverageDecision = if (markMissing) {
            SyncCoveragePolicy.decide(previousPresent, discovered)
        } else {
            SyncCoverageDecision.ACCEPT
        }
        if (selectionModeActive) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            selectionModeActive = false
        }
        scope.launch {
            if (syncSeen.isNotEmpty()) ServiceLocator.groups.upsert(syncSeen.values.toList())
            if (!operationLeaseController.isCurrent(lease)) return@launch
            if (coverageDecision == SyncCoverageDecision.SAFETY_STOP) {
                recordHardFailure("SYNC_COVERAGE_SAFETY_STOP")
                DiagnosticLog.record(
                    "SYNC_COVERAGE_SAFETY_STOP",
                    mapOf(
                        "previous" to previousPresent,
                        "discovered" to discovered,
                        "verification" to verification,
                    ),
                )
                BotRuntime.update(
                    RuntimeSnapshot(
                        RuntimePhase.ERROR,
                        "Sync safety stop",
                        "Only $discovered of $previousPresent previously-present groups were observed; existing registry was preserved",
                        discovered,
                        previousPresent,
                    )
                )
                clearOperation(lease)
                stopRuntimeService()
                return@launch
            }
            if (markMissing && instanceId != null && completedSyncId != null) {
                ServiceLocator.groups.markMissing(instanceId, completedSyncId)
                if (!operationLeaseController.isCurrent(lease)) return@launch
            }
            val preservationNote = if (markMissing) "" else " • existing unseen groups preserved"
            BotRuntime.update(
                BotRuntime.state.value.copy(
                    phase = RuntimePhase.COMPLETED,
                    title = "Sync complete",
                    detail = "$discovered groups synchronized • $verification$preservationNote",
                    current = discovered,
                    total = discovered,
                )
            )
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
                    "strategy" to syncStrategy.name,
                    "verification" to verification,
                    "markMissing" to markMissing,
                )
            )
            clearOperation(lease)
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
        val groupsFilter = findGroupsFilter(root)
        val chats = findChatsAnchor(root)
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
                groupTerminalGate.reset(next.id)
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
                extractionNavigationProbeJob?.cancel()
                extractionNavigationProbeJob = null
                extractionNavigationMisses = 0
                ServiceLocator.database.queueDao().updateState(next.id, "RUNNING", next.attempts, null)
                setExtractStage(ExtractStage.ACTIVATE_GROUP_FILTER)
                BotRuntime.update(RuntimeSnapshot(RuntimePhase.EXTRACTING, "Locating group", group.displayTitle, next.position + 1, totalQueue, BotRuntime.state.value.linksFound))
                targetInstance?.let { WhatsAppInstanceDetector.launch(this@WaAccessibilityService, it) }
                ?: targetPackage?.let { WhatsAppInstanceDetector.launch(this@WaAccessibilityService, it) }
            } finally {
                prepareInFlight.set(false)
            }
        }
    }

    private fun activateGroupFilter(root: AccessibilityNodeInfo) {
        val groups = findGroupsFilter(root)
        val chats = findChatsAnchor(root)
        val search = AccessibilityTree.findByViewIdHints(root, listOf("search"))
            ?: AccessibilityTree.findByControlLabel(root, SEARCH_LABELS)

        val screenEvidence = AccessibilityTree.screenEvidence(root)
        extractionNavigationMisses++
        val action = ExtractionNavigationPolicy.decide(
            groupsFound = groups != null,
            chatsFound = chats != null,
            searchFound = search != null,
            inChat = screenEvidence.kind == ScreenKind.CHAT && screenEvidence.confidence >= 60,
            missCount = extractionNavigationMisses,
        )

        if (extractionNavigationMisses == 1 || extractionNavigationMisses % 3 == 0 || action == ExtractionNavigationAction.FAIL) {
            DiagnosticLog.record(
                "EXTRACT_NAV_DECISION",
                mapOf(
                    "miss" to extractionNavigationMisses,
                    "groupsFound" to (groups != null),
                    "chatsFound" to (chats != null),
                    "searchFound" to (search != null),
                    "screen" to screenEvidence.kind.name,
                    "confidence" to screenEvidence.confidence,
                    "action" to action.name,
                    "package" to root.packageName?.toString().orEmpty(),
                ),
            )
        }

        when (action) {
            ExtractionNavigationAction.GROUP_FILTER_SEARCH -> {
                if (clickTarget(groups)) {
                    extractionNavigationMisses = 0
                    recordStageSuccess("EXTRACT_ACTIVATE_GROUP_FILTER")
                    setExtractStage(ExtractStage.OPEN_SEARCH)
                }
            }

            ExtractionNavigationAction.ANCHOR_CHATS -> {
                clickTarget(chats)
            }

            ExtractionNavigationAction.OPEN_SEARCH -> {
                extractionNavigationMisses = 0
                setExtractStage(ExtractStage.OPEN_SEARCH)
                if (search != null) clickTarget(search)
            }

            ExtractionNavigationAction.BACK_TOWARD_CHATS -> {
                DiagnosticLog.record("EXTRACT_NAV_BACK_RECOVERY", mapOf("miss" to extractionNavigationMisses))
                performGlobalAction(GLOBAL_ACTION_BACK)
            }

            ExtractionNavigationAction.WAIT -> Unit

            ExtractionNavigationAction.FAIL -> {
                failCurrentGroup(
                    "NAVIGATION_UNAVAILABLE",
                    "Could not reach a verified WhatsApp Chats/Search surface",
                )
            }
        }
    }

    private fun openSearch(root: AccessibilityNodeInfo) {
        val evidence = AccessibilityTree.screenEvidence(root)
        val existingEditor = AccessibilityTree.findEditable(root)
        val search = AccessibilityTree.findByViewIdHints(root, listOf("search"))
            ?: AccessibilityTree.findByControlLabel(root, SEARCH_LABELS)
            ?: AccessibilityTree.findByAnyText(root, SEARCH_LABELS)
        when (SearchEntryPolicy.decide(evidence.kind, existingEditor != null, search != null)) {
            SearchEntryAction.BACK_TO_CHATS -> {
                DiagnosticLog.record("EXTRACT_SEARCH_CHAT_RECOVERY", mapOf("confidence" to evidence.confidence))
                performGlobalAction(GLOBAL_ACTION_BACK)
                setExtractStage(ExtractStage.ACTIVATE_GROUP_FILTER)
            }
            SearchEntryAction.USE_EXISTING_EDITOR -> {
                recordStageSuccess("EXTRACT_OPEN_SEARCH")
                setExtractStage(ExtractStage.TYPE_QUERY)
                typeQuery(root)
            }
            SearchEntryAction.OPEN_GLOBAL_SEARCH -> {
                if (search != null && clickTarget(search)) {
                    // Dispatch acceptance is not success. Stay in OPEN_SEARCH until a
                    // subsequent snapshot proves ScreenKind.SEARCH + editor.
                    DiagnosticLog.record("EXTRACT_GLOBAL_SEARCH_CLICKED", mapOf("screen" to evidence.kind.name))
                    scheduleExtractionNavigationProbe()
                }
            }
            SearchEntryAction.WAIT -> Unit
        }
    }

    private fun typeQuery(root: AccessibilityNodeInfo) {
        val evidence = AccessibilityTree.screenEvidence(root)
        if (evidence.kind != ScreenKind.SEARCH || evidence.confidence < 60) {
            DiagnosticLog.record("EXTRACT_QUERY_SCREEN_NOT_VERIFIED", mapOf("screen" to evidence.kind.name, "confidence" to evidence.confidence))
            setExtractStage(ExtractStage.OPEN_SEARCH)
            return
        }
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
                if (clickTarget(resolution.node)) {
                    recordStageSuccess("EXTRACT_OPEN_RESULT")
                    currentQueue?.let { q -> scope.launch { ServiceLocator.database.queueDao().updateState(q.id, "RUNNING", q.attempts, null) } }
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
        val lease = currentOperationLease() ?: return
        if (!operationLeaseController.isCurrent(lease)) return
        if (!groupTerminalGate.claim(queue.id)) {
            DiagnosticLog.record("GROUP_TERMINAL_DUPLICATE_IGNORED", mapOf("state" to "FAILED", "group" to group.id.take(12)))
            return
        }
        if (code !in setOf("AMBIGUOUS_GROUP", "STAGE_TIMEOUT", "PERSISTENCE_ERROR")) recordTransientFailure(code)
        DiagnosticLog.record("GROUP_FAIL", mapOf("code" to code, "group" to group.id.take(12)))
        extractionProbeJob?.cancel()
        extractionProbeJob = null
        scope.launch {
            ServiceLocator.groups.finalizeExtractionTransition(
                queueId = queue.id,
                queueState = "FAILED",
                attempts = queue.attempts + 1,
                error = "$code: $detail",
                groupId = group.id,
                groupState = "FAILED",
                checkpoint = group.checkpoint,
            )
            if (!operationLeaseController.isCurrent(lease)) {
                DiagnosticLog.record("STALE_OPERATION_CALLBACK_IGNORED", mapOf("source" to "group-fail", "generation" to lease.generation))
                return@launch
            }
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
            currentQueue?.let { q -> scope.launch { ServiceLocator.database.queueDao().updateState(q.id, "RUNNING", q.attempts, null) } }
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
                    val persistResult = ServiceLocator.links.recordBatch(
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
                    DiagnosticLog.record(
                        "LINK_PERSISTED",
                        mapOf(
                            "count" to persistResult.insertedOccurrences,
                            "newLinks" to persistResult.newLinks,
                            "duplicates" to persistResult.duplicateOccurrences,
                            "detected" to detected,
                            "group" to group.id.take(12),
                            "session" to sessionId.take(12),
                        ),
                    )
                    throughputMeter.addLinks(persistResult.insertedOccurrences)
                    BotRuntime.update(
                        BotRuntime.state.value.copy(
                            linksFound = BotRuntime.state.value.linksFound + persistResult.insertedOccurrences
                        )
                    )
                    publishTelemetry()
                    advanceChatViewport(
                        groupId = group.id,
                        fingerprint = fingerprint,
                        detected = detected,
                        reachedOldCheckpoint = reachedOldCheckpoint,
                        unreadBoundaryReached = viewport.unreadMarkerPresent,
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
            unreadBoundaryReached = viewport.unreadMarkerPresent,
            persistenceState = ViewportPersistenceState.NO_CHANGES,
        )
    }

    private fun advanceChatViewport(
        groupId: String,
        fingerprint: String,
        detected: Int,
        reachedOldCheckpoint: Boolean,
        unreadBoundaryReached: Boolean,
        persistenceState: ViewportPersistenceState,
    ) {
        if (!DurableViewportPolicy.canAdvance(persistenceState)) return
        val group = currentGroup?.takeIf { it.id == groupId } ?: return
        val stage = operation as? Operation.Extract ?: return
        if (stage.stage != ExtractStage.SCAN_VIEWPORT || BotRuntime.pauseRequested || BotRuntime.stopRequested) return

        previousViewportFingerprint = fingerprint
        if (reachedOldCheckpoint ||
            (extractionMode == AutomationMode.UNREAD_ONLY &&
                UnreadTraversalPolicy.next(unreadBoundaryReached) == UnreadTraversalAction.COMPLETE)
        ) {
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
            AutomationMode.UNREAD_ONLY -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
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

    private fun scheduleExtractionNavigationProbe() {
        val expectedLease = currentOperationLease() ?: return
        extractionNavigationProbeJob?.cancel()
        extractionNavigationProbeJob = scope.launch {
            val probeMode = effectiveMode
            repeat(NavigationProbePolicy.maxAttempts(probeMode)) { attempt ->
                delay(NavigationProbePolicy.delayMs(probeMode, attempt))
                if (!operationLeaseController.isCurrent(expectedLease)) {
                    DiagnosticLog.record("STALE_OPERATION_CALLBACK_IGNORED", mapOf("source" to "extract-navigation", "generation" to expectedLease.generation))
                    return@launch
                }
                val current = operation as? Operation.Extract ?: return@launch
                if (current.stage !in setOf(
                        ExtractStage.ACTIVATE_GROUP_FILTER,
                        ExtractStage.OPEN_SEARCH,
                        ExtractStage.TYPE_QUERY,
                        ExtractStage.OPEN_RESULT,
                        ExtractStage.VERIFY_CHAT,
                        ExtractStage.RETURNING,
                    ) || BotRuntime.pauseRequested || BotRuntime.stopRequested
                ) return@launch

                val root = rootInActiveWindow
                if (root != null && root.packageName?.toString() == targetPackage && eventGuard.compareAndSet(false, true)) {
                    try {
                        handleExtraction(root, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                    } finally {
                        eventGuard.set(false)
                    }
                    val after = operation as? Operation.Extract ?: return@launch
                    if (after.stage == ExtractStage.SCAN_VIEWPORT || after.stage == ExtractStage.PREPARE_LIST) return@launch
                }
            }
        }
    }

    private fun scheduleExtractionProbe(delayMs: Long) {
        val expectedLease = currentOperationLease() ?: return
        extractionProbeJob?.cancel()
        extractionProbeJob = scope.launch {
            delay(delayMs)
            if (!operationLeaseController.isCurrent(expectedLease)) {
                DiagnosticLog.record("STALE_OPERATION_CALLBACK_IGNORED", mapOf("source" to "extract-probe", "generation" to expectedLease.generation))
                return@launch
            }
            if (operation !is Operation.Extract || BotRuntime.pauseRequested || BotRuntime.stopRequested) return@launch
            rootInActiveWindow?.let { scanChatViewport(it, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, fromProbe = true) }
        }
    }

    private fun currentCheckpointForPersistence(): String? = when (extractionMode) {
        AutomationMode.UNREAD_ONLY -> newestViewportFingerprint ?: viewportStartFingerprint
        AutomationMode.DEEP, AutomationMode.NEW_ONLY -> viewportStartFingerprint
    }

    private fun completeCurrentGroup(state: String, error: String?) {
        val queue = currentQueue ?: return
        val group = currentGroup ?: return
        val lease = currentOperationLease() ?: return
        if (!operationLeaseController.isCurrent(lease)) return
        if (!groupTerminalGate.claim(queue.id)) {
            DiagnosticLog.record("GROUP_TERMINAL_DUPLICATE_IGNORED", mapOf("state" to state, "group" to group.id.take(12)))
            return
        }
        DiagnosticLog.record("GROUP_COMPLETE", mapOf("state" to state, "group" to group.id.take(12)))
        val latestCheckpoint = currentCheckpointForPersistence()
        extractionProbeJob?.cancel()
        extractionProbeJob = null
        extractionRequestedFingerprint = null
        scope.launch {
            ServiceLocator.groups.finalizeExtractionTransition(
                queueId = queue.id,
                queueState = state,
                attempts = queue.attempts + if (state == "FAILED") 1 else 0,
                error = error,
                groupId = group.id,
                groupState = if (state == "COMPLETED") "COMPLETED" else state,
                checkpoint = latestCheckpoint,
            )
            if (!operationLeaseController.isCurrent(lease)) {
                DiagnosticLog.record("STALE_OPERATION_CALLBACK_IGNORED", mapOf("source" to "group-complete", "generation" to lease.generation))
                return@launch
            }
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
        val extractOperation = operation as? Operation.Extract ?: return
        val lease = extractOperation.lease
        if (!operationLeaseController.claimTerminal(lease)) {
            DiagnosticLog.record("EXTRACTION_TERMINAL_DUPLICATE_IGNORED", mapOf("generation" to lease.generation))
            return
        }
        val sessionId = extractionSessionId
        scope.launch {
            val queue = sessionId?.let { ServiceLocator.database.queueDao().forSession(it) }.orEmpty()
            if (!operationLeaseController.isCurrent(lease)) return@launch
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
            if (!operationLeaseController.isCurrent(lease)) return@launch
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
            val autoRetry = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("auto_retry_failed", true)
            val safeRetryCount = if (autoRetry) {
                queue.count { item ->
                    item.state == "FAILED" && FailureRecoveryPolicy.decide(item.lastError, item.attempts) == FailureDisposition.RETRY
                }
            } else {
                0
            }
            clearOperation(lease)
            if (safeRetryCount > 0) {
                DiagnosticLog.record("AUTO_RETRY_TRIGGERED", mapOf("session" to (sessionId?.take(12) ?: "none"), "count" to safeRetryCount))
                retryFailed()
            } else {
                if (sessionId != null) AutomaticExportCoordinator.runForCompletedSession(this@WaAccessibilityService, sessionId)
                stopRuntimeService()
            }
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
        val lease = operationLeaseController.current() ?: return
        if (!operationLeaseController.isCurrent(lease)) return
        operation = Operation.Sync(stage, lease)
        if (stage in setOf(SyncStage.OPENING, SyncStage.ALL_CHATS_OPENING)) {
            scheduleSyncOpeningProbe()
        } else {
            syncOpeningProbeJob?.cancel()
            syncOpeningProbeJob = null
        }
        armCurrentStageWatchdog()
    }

    private fun setExtractStage(stage: ExtractStage) {
        val lease = operationLeaseController.current() ?: return
        if (!operationLeaseController.isCurrent(lease)) return
        operation = Operation.Extract(stage, lease)
        if (stage in setOf(
                ExtractStage.ACTIVATE_GROUP_FILTER,
                ExtractStage.OPEN_SEARCH,
                ExtractStage.TYPE_QUERY,
                ExtractStage.OPEN_RESULT,
                ExtractStage.VERIFY_CHAT,
                ExtractStage.RETURNING,
            )
        ) {
            scheduleExtractionNavigationProbe()
        } else {
            extractionNavigationProbeJob?.cancel()
            extractionNavigationProbeJob = null
        }
        armCurrentStageWatchdog()
    }

    private fun currentOperationLease(): OperationLease? = when (val current = operation) {
        is Operation.Sync -> current.lease
        is Operation.Extract -> current.lease
        Operation.None -> null
    }

    private fun clearOperation(expectedLease: OperationLease? = currentOperationLease()) {
        if (expectedLease != null && !operationLeaseController.invalidate(expectedLease)) {
            DiagnosticLog.record("STALE_OPERATION_CLEAR_IGNORED", mapOf("generation" to expectedLease.generation))
            return
        }
        if (expectedLease == null) operationLeaseController.invalidate()
        operation = Operation.None
        groupTerminalGate.clear()
        eventCoalescer.reset()
        viewportCommitInFlight.set(false)
        syncOpeningProbeJob?.cancel()
        syncOpeningProbeJob = null
        syncProbeJob?.cancel()
        syncProbeJob = null
        extractionNavigationProbeJob?.cancel()
        extractionNavigationProbeJob = null
        stageWatchdogJob?.cancel()
        stageWatchdogJob = null
    }

    private fun armCurrentStageWatchdog() {
        stageWatchdogJob?.cancel()
        val snapshot = operation
        val shouldWatch = when (snapshot) {
            is Operation.Sync -> snapshot.stage !in setOf(SyncStage.SCANNING, SyncStage.SELECTION_SCANNING, SyncStage.ALL_CHATS_SCANNING)
            is Operation.Extract -> snapshot.stage !in setOf(ExtractStage.SCAN_VIEWPORT)
            Operation.None -> false
        }
        if (!shouldWatch || BotRuntime.pauseRequested || BotRuntime.stopRequested) return
        val timeout = effectivePerformanceProfile().stageTimeoutMs
        stageWatchdogJob = scope.launch {
            delay(timeout)
            val snapshotLease = when (snapshot) {
                is Operation.Sync -> snapshot.lease
                is Operation.Extract -> snapshot.lease
                Operation.None -> null
            }
            if (snapshotLease != null && !operationLeaseController.isCurrent(snapshotLease)) return@launch
            if (BotRuntime.pauseRequested || BotRuntime.stopRequested || operation != snapshot) return@launch
            when (snapshot) {
                is Operation.Sync -> {
                    val key = "SYNC_${snapshot.stage.name}"
                    DiagnosticLog.record(
                        "STAGE_TIMEOUT",
                        mapOf("stage" to key, "strategy" to syncStrategy.name),
                    )
                    recordTransientFailure(key)

                    when (snapshot.stage) {
                        SyncStage.OPENING, SyncStage.VERIFY_FILTER -> {
                            switchSyncStrategy(
                                SyncStrategySignal.GROUP_FILTER_UNAVAILABLE,
                                "Timeout at ${snapshot.stage.name}",
                            )
                        }

                        SyncStage.SELECTION_ENTER, SyncStage.SELECTION_SELECT_ALL -> {
                            DiagnosticLog.record(
                                "SYNC_SELECTION_TIMEOUT_FALLBACK",
                                mapOf("stage" to snapshot.stage.name, "groups" to syncSeen.size),
                            )
                            if (selectionModeActive) {
                                performGlobalAction(GLOBAL_ACTION_BACK)
                                selectionModeActive = false
                            }
                            switchSyncStrategy(
                                SyncStrategySignal.SELECTION_UNAVAILABLE,
                                "Selection mode timed out",
                            )
                        }

                        SyncStage.ALL_CHATS_OPENING -> {
                            when (stageCircuitBreaker.recordFailure(key)) {
                                CircuitDecision.RETRY -> {
                                    DiagnosticLog.record(
                                        "STAGE_RETRY",
                                        mapOf("stage" to key, "attempt" to stageCircuitBreaker.failureCount(key)),
                                    )
                                    setSyncStage(SyncStage.ALL_CHATS_OPENING)
                                    targetInstance?.let { WhatsAppInstanceDetector.launch(this@WaAccessibilityService, it) }
                ?: targetPackage?.let { WhatsAppInstanceDetector.launch(this@WaAccessibilityService, it) }
                                }
                                CircuitDecision.TRIP -> {
                                    recordHardFailure(key)
                                    clearOperation()
                                    BotRuntime.update(
                                        RuntimeSnapshot(
                                            RuntimePhase.ERROR,
                                            "Sync safety stop",
                                            "All three synchronization paths could not obtain a safe chat list",
                                        )
                                    )
                                    stopRuntimeService()
                                }
                            }
                        }

                        SyncStage.SCANNING, SyncStage.SELECTION_SCANNING, SyncStage.ALL_CHATS_SCANNING -> Unit
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

    /**
     * Real action executor: try semantic ACTION_CLICK first, then a gesture at the
     * resolved node bounds. Every caller still verifies the resulting screen/state;
     * dispatch acceptance is never treated as proof of success by itself.
     */
    private fun clickTarget(node: AccessibilityNodeInfo?): Boolean {
        if (AccessibilityTree.click(node)) return true
        node ?: return false
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return false
        val x = bounds.exactCenterX()
        val y = bounds.exactCenterY()
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        val accepted = dispatchGesture(gesture, null, null)
        if (accepted) {
            DiagnosticLog.record("GESTURE_CLICK_FALLBACK", mapOf("x" to x.toInt(), "y" to y.toInt()))
        }
        return accepted
    }

    private fun longClickTarget(node: AccessibilityNodeInfo?): Boolean {
        if (AccessibilityTree.longClick(node)) return true
        node ?: return false
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return false
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 650))
            .build()
        val accepted = dispatchGesture(gesture, null, null)
        if (accepted) DiagnosticLog.record("GESTURE_LONG_CLICK_FALLBACK")
        return accepted
    }

    private fun normalizePreview(value: String?): String = value.orEmpty()
        .replace('\u200f'.toString(), "")
        .replace('\u200e'.toString(), "")
        .trim()
        .replace(Regex("\\s+"), " ")
        .lowercase()

    private fun normalizeTitle(value: String) = value.trim().replace(Regex("\\s+"), " ").lowercase()
    private fun stableHash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private sealed interface Operation {
        data object None : Operation
        data class Sync(val stage: SyncStage, val lease: OperationLease) : Operation
        data class Extract(val stage: ExtractStage, val lease: OperationLease) : Operation
    }

    private enum class SyncStage {
        OPENING,
        VERIFY_FILTER,
        SCANNING,
        SELECTION_ENTER,
        SELECTION_SELECT_ALL,
        SELECTION_SCANNING,
        ALL_CHATS_OPENING,
        ALL_CHATS_SCANNING,
    }
    private enum class ExtractStage { PREPARE_LIST, ACTIVATE_GROUP_FILTER, OPEN_SEARCH, TYPE_QUERY, OPEN_RESULT, VERIFY_CHAT, SCAN_VIEWPORT, RETURNING }

    companion object {
        @Volatile var instance: WaAccessibilityService? = null
            private set

        val GROUP_LABELS = listOf("Groups", "المجموعات", "Group chats", "دردشات المجموعات")
        val CHAT_LABELS = listOf("Chats", "الدردشات")
        val SEARCH_LABELS = listOf("Search", "بحث")
        val ALL_LABELS = listOf("All", "الكل")
        val FILTER_PEER_LABELS = listOf("All", "الكل", "Unread", "غير المقروءة", "Favorites", "المفضلة", "Groups", "المجموعات")
        val SELECT_ALL_LABELS = listOf("Select all", "Select All", "تحديد الكل")
        val MORE_LABELS = listOf("More options", "More", "المزيد من الخيارات", "مزيد من الخيارات", "المزيد")
        val GROUP_ID_HINTS = listOf("group_filter", "filter_groups", "groups_filter", "chat_filter_group")
        val CHAT_ID_HINTS = listOf("chats", "tab_chats", "navigation_chats")
        val ALL_ID_HINTS = listOf("filter_all", "all_filter", "chat_filter_all")
        val SELECT_ALL_ID_HINTS = listOf("select_all", "action_select_all")
        val MORE_ID_HINTS = listOf("more", "overflow", "menu")
    }
}
