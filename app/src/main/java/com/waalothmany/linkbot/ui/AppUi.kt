package com.waalothmany.linkbot.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waalothmany.linkbot.BuildConfig
import com.waalothmany.linkbot.MainViewModel
import com.waalothmany.linkbot.automation.AutomationMode
import com.waalothmany.linkbot.automation.PerformanceMode
import com.waalothmany.linkbot.core.exporter.ExportFormat
import com.waalothmany.linkbot.data.GroupEntity
import com.waalothmany.linkbot.data.LinkEntity
import com.waalothmany.linkbot.runtime.RuntimePhase

private enum class Screen { HOME, GROUPS, LINKS, SETTINGS }
private enum class GroupFilter { ALL, UNREAD, READ, ACTIVE, UNSCANNED, FAILED, COMPLETED }

@Composable
fun LinkBotApp(
    viewModel: MainViewModel,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenShizuku: () -> Unit,
    onImportChat: () -> Unit,
    onExport: (ExportFormat) -> Unit,
    onExportDiagnostics: () -> Unit,
) {
    val colors = darkColorScheme(
        primary = Color(0xFF48D597),
        secondary = Color(0xFF8BE4BC),
        surface = Color(0xFF15191C),
        surfaceVariant = Color(0xFF20262A),
        background = Color(0xFF0E1113),
    )
    MaterialTheme(colorScheme = colors) {
        var screen by remember { mutableStateOf(Screen.HOME) }
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(selected = screen == Screen.HOME, onClick = { screen = Screen.HOME }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("الرئيسية") })
                    NavigationBarItem(selected = screen == Screen.GROUPS, onClick = { screen = Screen.GROUPS }, icon = { Icon(Icons.Default.Group, null) }, label = { Text("القروبات") })
                    NavigationBarItem(selected = screen == Screen.LINKS, onClick = { screen = Screen.LINKS }, icon = { Icon(Icons.Default.Link, null) }, label = { Text("الروابط") })
                    NavigationBarItem(selected = screen == Screen.SETTINGS, onClick = { screen = Screen.SETTINGS }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("الإعدادات") })
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(viewModel, onOpenAccessibility, onOpenOverlay, onOpenShizuku, onImportChat, onExport)
                    Screen.GROUPS -> GroupsScreen(viewModel)
                    Screen.LINKS -> LinksScreen(viewModel, onExport)
                    Screen.SETTINGS -> SettingsScreen(viewModel, onOpenAccessibility, onOpenOverlay, onOpenShizuku, onExportDiagnostics)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    vm: MainViewModel,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenShizuku: () -> Unit,
    onImportChat: () -> Unit,
    onExport: (ExportFormat) -> Unit,
) {
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val rootProbeState by vm.rootProbeState.collectAsStateWithLifecycle()
    val rootFallbackEnabled by vm.rootFallbackEnabled.collectAsStateWithLifecycle()
    val readiness by vm.readiness.collectAsStateWithLifecycle()
    val groups by vm.groupCount.collectAsStateWithLifecycle()
    val links by vm.linkCount.collectAsStateWithLifecycle()
    val occurrences by vm.occurrenceCount.collectAsStateWithLifecycle()
    val runtime by vm.runtime.collectAsStateWithLifecycle()
    val instances by vm.instances.collectAsStateWithLifecycle()
    val selectedInstance by vm.selectedInstanceId.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(AutomationMode.NEW_ONLY) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("WA Al-Othmany Link Bot", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("v${BuildConfig.VERSION_NAME} • Verified Runtime • مزامنة واستخراج", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(runtime.phase.name)
            }
        }
        item {
            CompactCard {
                Text("حالة التشغيل", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                StatusLine("جاهزية المحرك", readiness.coreReady)
                StatusLine("الوصول مفعّل في النظام", caps.accessibilityEnabled)
                StatusLine("خدمة الوصول متصلة فعليًا", caps.accessibilityConnected)
                StatusLine("الإشعارات", caps.notifications)
                StatusLine("الزر العائم (اختياري)", caps.overlay)
                Text("وضع التنفيذ: ${readiness.executionMode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Shizuku: ${caps.shizukuState}", style = MaterialTheme.typography.bodySmall, color = if (caps.shizukuReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Root: ${if (rootFallbackEnabled) rootProbeState.name else "DISABLED"}", style = MaterialTheme.typography.bodySmall, color = if (rootProbeState.name == "READY") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                if (caps.shizukuPermissionRequired) {
                    OutlinedButton(onClick = vm::requestShizukuPermission, modifier = Modifier.fillMaxWidth()) { Text("منح صلاحية Shizuku") }
                } else if (!caps.shizukuReady) {
                    OutlinedButton(onClick = onOpenShizuku, modifier = Modifier.fillMaxWidth()) { Text("فتح Shizuku") }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("استخدام Root كمسار احتياطي")
                        Text("يُستخدم فقط بعد نجاح فحص su الفعلي، وليس لمجرد وجود الملف.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = rootFallbackEnabled, onCheckedChange = vm::setRootFallbackEnabled)
                }
                OutlinedButton(onClick = vm::retryEngineProbes, modifier = Modifier.fillMaxWidth()) { Text("إعادة فحص المحركات") }
                readiness.blockers.take(3).forEach { blocker ->
                    Text("• $blocker", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (!caps.accessibilityEnabled || !caps.accessibilityConnected || !caps.overlay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!caps.accessibilityEnabled) {
                            OutlinedButton(onClick = onOpenAccessibility) { Text("تفعيل الوصول") }
                        } else if (!caps.accessibilityConnected) {
                            OutlinedButton(onClick = onOpenAccessibility) { Text("إعادة ربط خدمة الوصول") }
                        }
                        if (!caps.overlay) OutlinedButton(onClick = onOpenOverlay) { Text("السماح بالزر") }
                    }
                }
                OutlinedButton(onClick = vm::refreshEnvironment, modifier = Modifier.fillMaxWidth()) { Text("إعادة فحص حالة النظام ونسخ واتساب") }
            }
        }
        item {
            CompactCard {
                Text("نسخة واتساب", fontWeight = FontWeight.SemiBold)
                InstanceSelector(instances, selectedInstance, vm::selectInstance)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("القروبات", groups.toString(), Modifier.weight(1f))
                MetricCard("الروابط", links.toString(), Modifier.weight(1f))
                MetricCard("الظهور", occurrences.toString(), Modifier.weight(1f))
            }
        }
        item {
            CompactCard {
                Text("الأتمتة", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mode == AutomationMode.NEW_ONLY, onClick = { mode = AutomationMode.NEW_ONLY }, label = { Text("جديد") })
                    FilterChip(selected = mode == AutomationMode.UNREAD_ONLY, onClick = { mode = AutomationMode.UNREAD_ONLY }, label = { Text("غير مقروء") })
                    FilterChip(selected = mode == AutomationMode.DEEP, onClick = { mode = AutomationMode.DEEP }, label = { Text("عميق") })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::syncGroups, enabled = readiness.coreReady && selectedInstance != null, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CloudDownload, null); Text(" مزامنة") }
                    Button(onClick = { vm.startExtraction(mode) }, enabled = readiness.coreReady && selectedInstance != null, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PlayArrow, null); Text(" استخراج") }
                }
                OutlinedButton(onClick = vm::retryFailed, modifier = Modifier.fillMaxWidth()) { Text("إعادة محاولة الفاشلة") }
            }
        }
        item {
            if (runtime.phase in setOf(RuntimePhase.SYNCING, RuntimePhase.EXTRACTING, RuntimePhase.PAUSED, RuntimePhase.RECOVERING)) {
                CompactCard {
                    Text(runtime.title, fontWeight = FontWeight.SemiBold)
                    if (runtime.detail.isNotBlank()) Text(runtime.detail, style = MaterialTheme.typography.bodySmall)
                    if (runtime.total > 0) Text("${runtime.current} / ${runtime.total} • ${runtime.linksFound} links", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "ثبات ${runtime.healthScore}% • ${runtime.effectiveMode} • ${runtime.groupsPerMinute.toInt()} قروب/د • ${runtime.linksPerMinute.toInt()} رابط/د",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (runtime.healthScore >= 75) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (runtime.phase == RuntimePhase.PAUSED) Button(onClick = vm::resume) { Icon(Icons.Default.PlayArrow, null); Text(" استكمال") }
                        else OutlinedButton(onClick = vm::pause) { Icon(Icons.Default.Pause, null); Text(" إيقاف مؤقت") }
                        OutlinedButton(onClick = vm::skip) { Text("تخطي") }
                        OutlinedButton(onClick = vm::stop) { Icon(Icons.Default.Stop, null); Text(" إيقاف") }
                    }
                }
            }
        }
        item {
            CompactCard {
                Text("الملفات", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImportChat, modifier = Modifier.weight(1f)) { Text("استيراد TXT/ZIP") }
                    OutlinedButton(onClick = { onExport(ExportFormat.XLSX) }, modifier = Modifier.weight(1f)) { Text("تصدير XLSX") }
                }
            }
        }
    }
}

@Composable
private fun GroupsScreen(vm: MainViewModel) {
    val all by vm.groups.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(GroupFilter.ALL) }
    val groups = when (filter) {
        GroupFilter.ALL -> all
        GroupFilter.UNREAD -> all.filter { it.unread }
        GroupFilter.READ -> all.filter { !it.unread }
        GroupFilter.ACTIVE -> all.filter { it.active }
        GroupFilter.UNSCANNED -> all.filter { it.extractionState == "NEVER_SCANNED" }
        GroupFilter.FAILED -> all.filter { it.extractionState == "FAILED" }
        GroupFilter.COMPLETED -> all.filter { it.extractionState == "COMPLETED" }
    }
    Column(Modifier.fillMaxSize()) {
        Text("القروبات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = filter == GroupFilter.ALL, onClick = { filter = GroupFilter.ALL }, label = { Text("الكل ${all.size}") })
            FilterChip(selected = filter == GroupFilter.UNREAD, onClick = { filter = GroupFilter.UNREAD }, label = { Text("غير مقروء ${all.count { it.unread }}") })
            FilterChip(selected = filter == GroupFilter.READ, onClick = { filter = GroupFilter.READ }, label = { Text("مقروء ${all.count { !it.unread }}") })
            FilterChip(selected = filter == GroupFilter.ACTIVE, onClick = { filter = GroupFilter.ACTIVE }, label = { Text("نشط ${all.count { it.active }}") })
            FilterChip(selected = filter == GroupFilter.UNSCANNED, onClick = { filter = GroupFilter.UNSCANNED }, label = { Text("لم يُفحص ${all.count { it.extractionState == "NEVER_SCANNED" }}") })
            FilterChip(selected = filter == GroupFilter.FAILED, onClick = { filter = GroupFilter.FAILED }, label = { Text("فشل ${all.count { it.extractionState == "FAILED" }}") })
            FilterChip(selected = filter == GroupFilter.COMPLETED, onClick = { filter = GroupFilter.COMPLETED }, label = { Text("مكتمل ${all.count { it.extractionState == "COMPLETED" }}") })
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::selectAll) { Text("تحديد الكل") }
            OutlinedButton(onClick = vm::clearSelection) { Text("إلغاء التحديد") }
            OutlinedButton(onClick = vm::selectUnread) { Text("غير مقروء") }
            OutlinedButton(onClick = vm::selectRead) { Text("المقروء") }
            OutlinedButton(onClick = vm::selectActive) { Text("النشط") }
            OutlinedButton(onClick = vm::selectNeverScanned) { Text("لم يُفحص") }
            OutlinedButton(onClick = vm::selectFailed) { Text("الفاشلة") }
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(groups, key = { it.id }) { group -> GroupRow(group, vm::setSelected) }
        }
    }
}

@Composable
private fun GroupRow(group: GroupEntity, onSelected: (GroupEntity, Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = group.selected, onCheckedChange = { onSelected(group, it) })
            Column(Modifier.weight(1f)) {
                Text(group.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(
                    listOfNotNull(if (group.unread) "غير مقروء${group.unreadCount?.let { " $it" }.orEmpty()}" else "مقروء", if (group.active) "نشط" else null, group.extractionState).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LinksScreen(vm: MainViewModel, onExport: (ExportFormat) -> Unit) {
    val links by vm.links.collectAsStateWithLifecycle()
    var formatExpanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("الروابط", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${links.size} رابط فريد", style = MaterialTheme.typography.bodySmall)
            }
            Box {
                OutlinedButton(onClick = { formatExpanded = true }) { Text("تصدير") }
                DropdownMenu(expanded = formatExpanded, onDismissRequest = { formatExpanded = false }) {
                    ExportFormat.entries.forEach { format ->
                        DropdownMenuItem(text = { Text(format.name) }, onClick = { formatExpanded = false; onExport(format) })
                    }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(links, key = { it.id }) { link -> LinkRow(link) }
        }
    }
}

@Composable
private fun LinkRow(link: LinkEntity) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(link.canonicalUrl, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text("${link.category} • ${link.occurrenceCount} ظهور", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsScreen(vm: MainViewModel, onOpenAccessibility: () -> Unit, onOpenOverlay: () -> Unit, onOpenShizuku: () -> Unit, onExportDiagnostics: () -> Unit) {
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val rootProbeState by vm.rootProbeState.collectAsStateWithLifecycle()
    val rootFallbackEnabled by vm.rootFallbackEnabled.collectAsStateWithLifecycle()
    val saveText by vm.saveMessageText.collectAsStateWithLifecycle()
    val performanceMode by vm.performanceMode.collectAsStateWithLifecycle()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("الإعدادات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            CompactCard {
                Text("الخصوصية", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("حفظ نص الرسالة كاملًا")
                        Text("متوقف افتراضيًا؛ تُحفظ الروابط والبيانات الأساسية فقط.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = saveText, onCheckedChange = vm::setSaveMessageText)
                }
            }
        }
        item {
            CompactCard {
                Text("الأداء", fontWeight = FontWeight.SemiBold)
                Text("المحرك يخفّض السرعة تلقائيًا عند ضعف الثبات ويرفعها بعد نجاحات متتالية.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = performanceMode == PerformanceMode.FAST, onClick = { vm.setPerformanceMode(PerformanceMode.FAST) }, label = { Text("سريع") })
                    FilterChip(selected = performanceMode == PerformanceMode.BALANCED, onClick = { vm.setPerformanceMode(PerformanceMode.BALANCED) }, label = { Text("ذكي") })
                    FilterChip(selected = performanceMode == PerformanceMode.SAFE, onClick = { vm.setPerformanceMode(PerformanceMode.SAFE) }, label = { Text("آمن") })
                }
            }
        }
        item {
            CompactCard {
                Text("التشخيص", fontWeight = FontWeight.SemiBold)
                Text("يُصدّر سجلًا تقنيًا منقحًا بدون نصوص الرسائل أو الروابط الكاملة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onExportDiagnostics) { Text("تصدير سجل التشخيص") }
            }
        }
        item {
            CompactCard {
                Text("القدرات", fontWeight = FontWeight.SemiBold)
                StatusLine("الوصول مفعّل في النظام", caps.accessibilityEnabled)
                StatusLine("خدمة الوصول متصلة", caps.accessibilityConnected)
                StatusLine("الزر العائم", caps.overlay)
                StatusLine("الإشعارات", caps.notifications)
                StatusLine("تطبيق Shizuku موجود", caps.shizukuInstalled)
                StatusLine("Shizuku جاهز فعليًا", caps.shizukuReady)
                Text("Shizuku: ${caps.shizukuState}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusLine("Root مكتشف", caps.rootAvailable)
                Text("Root probe: ${if (rootFallbackEnabled) rootProbeState.name else "DISABLED"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (caps.shizukuPermissionRequired) {
                    OutlinedButton(onClick = vm::requestShizukuPermission, modifier = Modifier.fillMaxWidth()) { Text("منح صلاحية Shizuku") }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("استخدام Root كمسار احتياطي", modifier = Modifier.weight(1f))
                    Switch(checked = rootFallbackEnabled, onCheckedChange = vm::setRootFallbackEnabled)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenAccessibility) { Text("إمكانية الوصول") }
                    OutlinedButton(onClick = onOpenOverlay) { Text("الزر العائم") }
                    OutlinedButton(onClick = onOpenShizuku) { Text("Shizuku") }
                }
                OutlinedButton(onClick = vm::retryEngineProbes, modifier = Modifier.fillMaxWidth()) { Text("إعادة فحص المحركات") }
            }
        }
    }
}

@Composable
private fun InstanceSelector(instances: List<com.waalothmany.linkbot.data.WhatsAppInstanceEntity>, selectedId: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = instances.firstOrNull { it.id == selectedId }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.let { "${it.label} • ${it.profileType} • User ${it.androidUserId}${if (it.enabled) "" else " • غير متاح"}" } ?: "لم يتم اكتشاف واتساب متاح", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            instances.forEach { item ->
                DropdownMenuItem(
                    text = { Text("${item.label} • ${item.profileType} • User ${item.androidUserId} • ${item.packageName}${if (item.enabled) "" else " • غير متاح"}") },
                    enabled = item.enabled,
                    onClick = { expanded = false; onSelect(item.id) },
                )
            }
        }
    }
}

@Composable
private fun CompactCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Text(if (ok) " جاهز" else " مطلوب", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}
