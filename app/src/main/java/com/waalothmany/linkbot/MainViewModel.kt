package com.waalothmany.linkbot

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.waalothmany.linkbot.automation.AutomationMode
import com.waalothmany.linkbot.automation.PerformanceMode
import com.waalothmany.linkbot.automation.PerformanceProfiles
import com.waalothmany.linkbot.automation.WaAccessibilityService
import com.waalothmany.linkbot.capability.AccessibilityConnectionMonitor
import com.waalothmany.linkbot.capability.CapabilityManager
import com.waalothmany.linkbot.capability.OperationStartBlockReason
import com.waalothmany.linkbot.capability.OperationStartContext
import com.waalothmany.linkbot.capability.OperationStartGate
import com.waalothmany.linkbot.capability.ReadinessEvaluator
import com.waalothmany.linkbot.data.GroupEntity
import com.waalothmany.linkbot.data.LinkEntity
import com.waalothmany.linkbot.data.WhatsAppInstanceEntity
import com.waalothmany.linkbot.runtime.BotRuntime
import com.waalothmany.linkbot.runtime.DiagnosticLog
import com.waalothmany.linkbot.runtime.RuntimePhase
import com.waalothmany.linkbot.runtime.RuntimeSnapshot
import com.waalothmany.linkbot.whatsapp.InstanceInventoryItem
import com.waalothmany.linkbot.whatsapp.InstanceInventoryPolicy
import com.waalothmany.linkbot.whatsapp.WhatsAppInstanceDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = ServiceLocator.database
    private val appContext = app.applicationContext

    private val _selectedInstanceId = MutableStateFlow<String?>(null)
    val selectedInstanceId = _selectedInstanceId.asStateFlow()

    private val allGroups: StateFlow<List<GroupEntity>> = ServiceLocator.groups.groups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val groups: StateFlow<List<GroupEntity>> = combine(allGroups, _selectedInstanceId) { items, instanceId ->
        if (instanceId == null) emptyList() else items.filter { it.instanceId == instanceId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val links: StateFlow<List<LinkEntity>> = ServiceLocator.links.links
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val groupCount: StateFlow<Int> = groups.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val linkCount: StateFlow<Int> = ServiceLocator.links.linkCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val occurrenceCount: StateFlow<Int> = ServiceLocator.links.occurrenceCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val instances: StateFlow<List<WhatsAppInstanceEntity>> = db.instanceDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val runtime: StateFlow<RuntimeSnapshot> = BotRuntime.state

    private val _capabilities = MutableStateFlow(CapabilityManager.snapshot(appContext))
    val capabilities = _capabilities.asStateFlow()

    val readiness = combine(_capabilities, instances) { caps, inventory ->
        ReadinessEvaluator.evaluate(
            flags = caps.asFlags(),
            whatsappInstances = inventory.count { it.enabled },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ReadinessEvaluator.evaluate(_capabilities.value.asFlags(), whatsappInstances = 0),
    )

    private val prefs = app.getSharedPreferences("settings", 0)
    private val _saveMessageText = MutableStateFlow(prefs.getBoolean("save_message_text", false))
    val saveMessageText = _saveMessageText.asStateFlow()

    private val _performanceMode = MutableStateFlow(
        PerformanceProfiles.parse(prefs.getString("performance_mode", PerformanceMode.BALANCED.name))
    )
    val performanceMode = _performanceMode.asStateFlow()

    init {
        viewModelScope.launch {
            AccessibilityConnectionMonitor.state.collectLatest { connection ->
                _capabilities.value = CapabilityManager.snapshot(appContext)
                if (!connection.connected && BotRuntime.state.value.phase in ACTIVE_PHASES) {
                    BotRuntime.update(
                        BotRuntime.state.value.copy(
                            phase = RuntimePhase.ERROR,
                            title = "Accessibility disconnected",
                            detail = "Re-enable or reconnect the accessibility service before continuing.",
                            error = "ACCESSIBILITY_DISCONNECTED",
                        )
                    )
                    DiagnosticLog.record("RUNTIME_BLOCKED_ACCESSIBILITY_DISCONNECTED")
                }
            }
        }
        refreshEnvironment()
    }

    fun refreshEnvironment() {
        _capabilities.value = CapabilityManager.snapshot(appContext)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val detectedEntities = WhatsAppInstanceDetector.detect(appContext)
            val existingEntities = db.instanceDao().all()
            val merged = InstanceInventoryPolicy.reconcile(
                existing = existingEntities.map { it.toInventoryItem() },
                detected = detectedEntities.map { it.toInventoryItem() },
                nowMs = now,
            ).map { it.toEntity() }
            db.instanceDao().upsertAll(merged)

            val available = merged.filter { it.enabled }
            val selected = _selectedInstanceId.value
            if (selected == null || available.none { it.id == selected }) {
                _selectedInstanceId.value = available.firstOrNull()?.id
            }

            DiagnosticLog.record(
                "ENVIRONMENT_REFRESHED",
                mapOf(
                    "instances" to available.size,
                    "a11yEnabled" to _capabilities.value.accessibilityEnabled,
                    "a11yConnected" to _capabilities.value.accessibilityConnected,
                    "notifications" to _capabilities.value.notifications,
                    "overlay" to _capabilities.value.overlay,
                    "shizukuInstalled" to _capabilities.value.shizukuInstalled,
                ),
            )
        }
    }

    fun selectInstance(id: String) {
        if (instances.value.any { it.id == id && it.enabled }) {
            _selectedInstanceId.value = id
        }
    }

    fun syncGroups() {
        val instance = selectedAvailableInstance()
        if (!allowOperationStart(instance)) return
        val service = WaAccessibilityService.instance ?: run {
            rejectStart(OperationStartBlockReason.ACCESSIBILITY_NOT_CONNECTED)
            return
        }
        service.startGroupSync(instance!!.id, instance.packageName)
    }

    fun startExtraction(mode: AutomationMode) {
        val instance = selectedAvailableInstance()
        if (!allowOperationStart(instance)) return
        val service = WaAccessibilityService.instance ?: run {
            rejectStart(OperationStartBlockReason.ACCESSIBILITY_NOT_CONNECTED)
            return
        }
        service.startExtraction(instance!!.id, instance.packageName, mode)
    }

    private fun selectedAvailableInstance(): WhatsAppInstanceEntity? =
        instances.value.firstOrNull { it.id == _selectedInstanceId.value && it.enabled }

    private fun allowOperationStart(instance: WhatsAppInstanceEntity?): Boolean {
        val fresh = CapabilityManager.snapshot(appContext)
        _capabilities.value = fresh
        val decision = OperationStartGate.evaluate(
            OperationStartContext(
                instanceSelected = instance != null,
                packageLaunchable = instance?.let {
                    WhatsAppInstanceDetector.isLaunchable(appContext, it.packageName)
                } == true,
                accessibilityEnabled = fresh.accessibilityEnabled,
                accessibilityConnected = fresh.accessibilityConnected,
                notificationsReady = fresh.notifications,
                operationBusy = BotRuntime.state.value.phase in ACTIVE_PHASES,
            )
        )
        if (!decision.allowed) {
            rejectStart(decision.reason ?: OperationStartBlockReason.ACCESSIBILITY_NOT_CONNECTED)
        }
        return decision.allowed
    }

    private fun rejectStart(reason: OperationStartBlockReason) {
        val detail = when (reason) {
            OperationStartBlockReason.NO_INSTANCE -> "No available WhatsApp instance is selected."
            OperationStartBlockReason.PACKAGE_NOT_LAUNCHABLE -> "The selected WhatsApp instance cannot be launched from the current Android profile."
            OperationStartBlockReason.ACCESSIBILITY_DISABLED -> "Enable WA Al-Othmany accessibility service in Android settings."
            OperationStartBlockReason.ACCESSIBILITY_NOT_CONNECTED -> "Accessibility is enabled but the service is not connected. Open Accessibility settings and reconnect the service."
            OperationStartBlockReason.NOTIFICATIONS_DISABLED -> "Allow notifications so the foreground automation service can run reliably."
            OperationStartBlockReason.OPERATION_ALREADY_RUNNING -> "An automation operation is already running. Pause or stop it before starting another."
        }
        DiagnosticLog.record("OPERATION_START_BLOCKED", mapOf("reason" to reason.name))
        BotRuntime.update(
            RuntimeSnapshot(
                phase = RuntimePhase.ERROR,
                title = "Cannot start",
                detail = detail,
                error = reason.name,
            )
        )
    }

    fun selectAll() = withSelectedInstance { ServiceLocator.groups.selectAll(it) }
    fun clearSelection() = withSelectedInstance { ServiceLocator.groups.clearSelection(it) }
    fun selectUnread() = withSelectedInstance { ServiceLocator.groups.selectUnread(it) }
    fun selectRead() = withSelectedInstance { ServiceLocator.groups.selectRead(it) }
    fun selectActive() = withSelectedInstance { ServiceLocator.groups.selectActive(it) }
    fun selectNeverScanned() = withSelectedInstance { ServiceLocator.groups.selectNeverScanned(it) }
    fun selectFailed() = withSelectedInstance { ServiceLocator.groups.selectFailed(it) }
    fun setSelected(group: GroupEntity, selected: Boolean) = viewModelScope.launch {
        ServiceLocator.groups.setSelected(group.id, selected)
    }

    private fun withSelectedInstance(block: suspend (String) -> Unit) = viewModelScope.launch {
        _selectedInstanceId.value?.let { block(it) }
    }

    fun setSaveMessageText(value: Boolean) {
        prefs.edit().putBoolean("save_message_text", value).apply()
        _saveMessageText.value = value
    }

    fun setPerformanceMode(value: PerformanceMode) {
        prefs.edit().putString("performance_mode", value.name).apply()
        _performanceMode.value = value
    }

    fun pause() = WaAccessibilityService.instance?.pauseAutomation() ?: BotRuntime.pause()
    fun resume() = WaAccessibilityService.instance?.resumeAutomation() ?: BotRuntime.resume()
    fun stop() = WaAccessibilityService.instance?.stopAutomation() ?: BotRuntime.stop()
    fun skip() = WaAccessibilityService.instance?.skipCurrent() ?: BotRuntime.skip()
    fun retryFailed() = WaAccessibilityService.instance?.retryFailed() ?:
        rejectStart(OperationStartBlockReason.ACCESSIBILITY_NOT_CONNECTED)

    private fun WhatsAppInstanceEntity.toInventoryItem() = InstanceInventoryItem(
        id = id,
        packageName = packageName,
        label = label,
        kind = kind,
        enabled = enabled,
        lastSeenAt = lastSeenAt,
    )

    private fun InstanceInventoryItem.toEntity() = WhatsAppInstanceEntity(
        id = id,
        packageName = packageName,
        label = label,
        kind = kind,
        enabled = enabled,
        lastSeenAt = lastSeenAt,
    )

    companion object {
        private val ACTIVE_PHASES = setOf(
            RuntimePhase.SYNCING,
            RuntimePhase.EXTRACTING,
            RuntimePhase.PAUSED,
            RuntimePhase.RECOVERING,
        )
    }
}
