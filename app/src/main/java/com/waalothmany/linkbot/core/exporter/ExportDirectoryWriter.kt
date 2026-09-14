package com.waalothmany.linkbot.core.exporter

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.waalothmany.linkbot.ServiceLocator
import com.waalothmany.linkbot.automation.QueueProgressPolicy
import com.waalothmany.linkbot.runtime.DiagnosticLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExportDirectoryWriter {
    suspend fun writeArtifacts(context: Context, treeUri: Uri, options: ExportOptions): List<Uri> {
        val artifacts = ExportUseCase.buildArtifacts(options)
        return withContext(Dispatchers.IO) {
            artifacts.map { artifact ->
                createAndWrite(
                    context = context,
                    treeUri = treeUri,
                    displayName = "${artifact.fileStem}-${System.currentTimeMillis()}.${artifact.format.extension}",
                    mime = artifact.format.mime,
                    bytes = artifact.bytes,
                )
            }
        }
    }

    suspend fun writeSessionReport(context: Context, treeUri: Uri, sessionId: String, exportPaths: List<Uri>): Uri? {
        val db = ServiceLocator.database
        val session = db.sessionDao().get(sessionId) ?: return null
        val instance = db.instanceDao().get(session.instanceId)
        val queue = db.queueDao().forSession(sessionId)
        val progress = QueueProgressPolicy.summarize(queue.map { it.state })
        val report = SessionReport(
            instance = instance?.label ?: session.instanceId,
            startedAt = session.startedAt,
            endedAt = session.completedAt ?: System.currentTimeMillis(),
            groupsSelected = queue.size,
            groupsCompleted = progress.completed,
            groupsFailed = progress.failed,
            uniqueLinks = db.linkDao().countNow(),
            totalOccurrences = db.occurrenceDao().countNow(),
            exportPaths = exportPaths.map(Uri::toString),
        )
        return withContext(Dispatchers.IO) {
            createAndWrite(
                context,
                treeUri,
                "wa-session-$sessionId-report.txt",
                "text/plain",
                SessionReportFormatter.text(report).toByteArray(),
            )
        }
    }

    private fun createAndWrite(context: Context, treeUri: Uri, displayName: String, mime: String, bytes: ByteArray): Uri {
        val resolver = context.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId)
        val output = DocumentsContract.createDocument(resolver, parent, mime, displayName)
            ?: error("Unable to create export document: $displayName")
        resolver.openOutputStream(output, "w")?.use { it.write(bytes) }
            ?: error("Unable to open export document: $displayName")
        return output
    }
}

object AutomaticExportCoordinator {
    const val PREF_AUTO_EXPORT = "auto_export"
    const val PREF_EXPORT_TREE_URI = "export_tree_uri"
    const val PREF_EXPORT_SCOPE = "export_scope"
    const val PREF_EXPORT_GROUPING = "export_grouping"
    const val PREF_EXPORT_FORMAT = "export_format"

    suspend fun runForCompletedSession(context: Context, sessionId: String): Boolean {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_AUTO_EXPORT, false)) return false
        val uriText = prefs.getString(PREF_EXPORT_TREE_URI, null).orEmpty()
        if (uriText.isBlank()) {
            DiagnosticLog.record("AUTO_EXPORT_SKIPPED", mapOf("reason" to "NO_DIRECTORY", "session" to sessionId.take(12)))
            return false
        }
        val options = ExportOptions(
            format = parseEnum(prefs.getString(PREF_EXPORT_FORMAT, null), ExportFormat.XLSX),
            scope = parseEnum(prefs.getString(PREF_EXPORT_SCOPE, null), ExportScope.UNIQUE_LINKS),
            grouping = parseEnum(prefs.getString(PREF_EXPORT_GROUPING, null), ExportGrouping.COMBINED),
            autoAfterSession = true,
        )
        return runCatching {
            val treeUri = Uri.parse(uriText)
            val paths = ExportDirectoryWriter.writeArtifacts(context, treeUri, options)
            val report = ExportDirectoryWriter.writeSessionReport(context, treeUri, sessionId, paths)
            DiagnosticLog.record(
                "AUTO_EXPORT_COMPLETE",
                mapOf("session" to sessionId.take(12), "files" to paths.size, "report" to (report != null)),
            )
            true
        }.onFailure { error ->
            DiagnosticLog.record("AUTO_EXPORT_FAILED", mapOf("session" to sessionId.take(12), "error" to (error.message ?: error::class.java.simpleName)))
        }.getOrDefault(false)
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
