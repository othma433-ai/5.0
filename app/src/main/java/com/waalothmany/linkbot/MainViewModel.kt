package com.waalothmany.linkbot

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.waalothmany.linkbot.automation.AutomationMode
import com.waalothmany.linkbot.automation.PerformanceMode
import com.waalothmany.linkbot.automation.PerformanceProfiles
import com.waalothmany.linkbot.automation.WaAccessibilityService
import com.waalothmany.linkbot.capability.CapabilityManager
import com.waalothmany.linkbot.capability.ReadinessEvaluator
import com.waalothmany.linkbot.data.GroupEntity
import com.waalothmany.linkbot.data.LinkEntity
import com.waalothmany.linkbot.data.WhatsAppInstanceEntity
import com.waalothmany.linkbot.runtime.BotRuntime
import com.waalothmany.linkbot.runtime.RuntimePhase
import com.waalothmany.linkbot.runtime.RuntimeSnapshot
import com.waalothmany.linkbot.whatsapp.WhatsAppInstanceDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = ServiceLocator.database

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val runtime: StateFlow<RuntimeSnapshot> = BotRuntime.state

    private val _capabilities = MutableStateFlow(CapabilityManager.snapshot(app))
    val capabilities = _capabilities.asStateFlow()

    private val _readiness = MutableStateFlow(
        ReadinessEvaluator.evaluate(
            _capabilities.value.asFlags(),
            whatsappInstances = 0,
            accessibilityServiceConnected = WaAccessibilityService.instance != null,
        )
    )
    val readiness = _readiness.asStateFlow()

    private val prefs = app.getSharedPreferences("settings", 0)
    private val _saveMessageText = MutableStateFlow(prefs.getBoolean("save_message_text", false))
    val saveMessageText = _saveMessageText.asStateFlow()

    private val _performanceMode = MutableStateFlow(
        PerformanceProfiles.parse(prefs.getString("performance_mode", PerformanceMode.BALANCED.name))
    )
    val performanceMode = _performanceMode.asStateFlow()

    init {
        refreshEnvironment()
    }

    fun refreshEnvironment() {
        val snapshot = CapabilityManager.snapshot(getApplication())
        _capabilities.value = snapshot
        viewModelScope.launch {
            val detected = WhatsAppInstanceDetector.detect(getApplication()).map { candidate ->
                val existing = db.instanceDao().getByPackage(candidate.packageName)
                if (existing != null) candidate.copy(id = existing.id) else candidate
            }
            db.instanceDao().upsertAll(detected)
            if (_selectedInstanceId.value == null || detected.none { it.id == _selectedInstanceId.value }) {
                _selectedInstanceId.value = detected.firstOrNull()?.id
            }
            _readiness.value = ReadinessEvaluator.evaluate(
                snapshot.asFlags(),
                whatsappInstances = detected.size,
                accessibilityServiceConnected = WaAccessibilityService.instance != null,
            )
        }
    }

    fun selectInstance(id: String) { _selectedInstanceId.value = id }

    fun syncGroups() {
        val instance = instances.value.firstOrNull { it.id == _selectedInstanceId.value } ?: return
        val service = WaAccessibilityService.instance
        if (service == null) {
            BotRuntime.update(RuntimeSnapshot(RuntimePhase.ERROR, "Accessibility required", "Enable the service, then try again"))
            return
        }
        service.startGroupSync(instance.id, instance.packageName)
    }

    fun startExtraction(mode: AutomationMode) {
        val instance = instances.value.firstOrNull { it.id == _selectedInstanceId.value } ?: return
        val service = WaAccessibilityService.instance
        if (service == null) {
            BotRuntime.update(RuntimeSnapshot(RuntimePhase.ERROR, "Accessibility required", "Enable the service, then try again"))
            return
        }
        service.startExtraction(instance.id, instance.packageName, mode)
    }

    fun selectAll() = withSelectedInstance { ServiceLocator.groups.selectAll(it) }
    fun clearSelection() = withSelectedInstance { ServiceLocator.groups.clearSelection(it) }
    fun selectUnread() = withSelectedInstance { ServiceLocator.groups.selectUnread(it) }
    fun selectRead() = withSelectedInstance { ServiceLocator.groups.selectRead(it) }
    fun selectActive() = withSelectedInstance { ServiceLocator.groups.selectActive(it) }
    fun selectNeverScanned() = withSelectedInstance { ServiceLocator.groups.selectNeverScanned(it) }
    fun selectFailed() = withSelectedInstance { ServiceLocator.groups.selectFailed(it) }
    fun setSelected(group: GroupEntity, selected: Boolean) = viewModelScope.launch { ServiceLocator.groups.setSelected(group.id, selected) }

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
        BotRuntime.update(RuntimeSnapshot(RuntimePhase.ERROR, "Accessibility required", "Enable the service to retry failed groups"))
}
