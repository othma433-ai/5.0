package com.waalothmany.linkbot

import android.Manifest
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.waalothmany.linkbot.core.exporter.ExportFormat
import com.waalothmany.linkbot.core.exporter.ExportUseCase
import com.waalothmany.linkbot.core.importer.ImportChatUseCase
import com.waalothmany.linkbot.ui.LinkBotApp
import com.waalothmany.linkbot.runtime.DiagnosticLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var pendingExport: ExportFormat = ExportFormat.XLSX

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshEnvironment()
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importChat(uri)
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) writeExport(uri, pendingExport)
    }

    private val diagnosticLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) writeDiagnostics(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            LinkBotApp(
                viewModel = viewModel,
                onOpenAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                onOpenOverlay = {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                },
                onOpenShizuku = {
                    val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    if (launch != null) {
                        startActivity(launch)
                    } else {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:moe.shizuku.privileged.api")))
                    }
                },
                onImportChat = { importLauncher.launch(arrayOf("text/plain", "application/zip", "application/octet-stream")) },
                onExport = { format ->
                    pendingExport = format
                    exportLauncher.launch("wa-links-${System.currentTimeMillis()}.${format.extension}")
                },
                onExportDiagnostics = {
                    diagnosticLauncher.launch("wa-link-bot-diagnostics-${System.currentTimeMillis()}.txt")
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshEnvironment()
    }

    private fun importChat(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val name = displayName(uri) ?: "whatsapp-export.txt"
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Unable to read file")
                    ImportChatUseCase.importBytes(name, bytes)
                }
            }.onSuccess { result ->
                Toast.makeText(
                    this@MainActivity,
                    "تم استيراد ${result.uniqueLinks} رابط فريد من ${result.messages} رسالة",
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, "فشل الاستيراد: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun writeExport(uri: Uri, format: ExportFormat) {
        lifecycleScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { ExportUseCase.build(format) }
                contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) } ?: error("Unable to open export destination")
            }.onSuccess {
                Toast.makeText(this@MainActivity, "تم التصدير", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@MainActivity, "فشل التصدير: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun writeDiagnostics(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) { DiagnosticLog.exportText() }
                contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { it.write(text) }
                    ?: error("Unable to open diagnostic destination")
            }.onSuccess {
                Toast.makeText(this@MainActivity, "تم تصدير سجل التشخيص", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@MainActivity, "فشل تصدير التشخيص: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun displayName(uri: Uri): String? {
        val cursor: Cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            return if (index >= 0) it.getString(index) else null
        }
    }
}
