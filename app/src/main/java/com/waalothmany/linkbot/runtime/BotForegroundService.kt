package com.waalothmany.linkbot.runtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.waalothmany.linkbot.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BotForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        scope.launch {
            BotRuntime.state.collectLatest { updateNotification(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> com.waalothmany.linkbot.automation.WaAccessibilityService.instance?.pauseAutomation() ?: BotRuntime.pause()
            ACTION_RESUME -> com.waalothmany.linkbot.automation.WaAccessibilityService.instance?.resumeAutomation() ?: BotRuntime.resume()
            ACTION_STOP -> com.waalothmany.linkbot.automation.WaAccessibilityService.instance?.stopAutomation() ?: BotRuntime.stop()
            ACTION_SKIP -> com.waalothmany.linkbot.automation.WaAccessibilityService.instance?.skipCurrent() ?: BotRuntime.skip()
        }
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "WA Link Bot running"
        startForeground(NOTIFICATION_ID, notification(BotRuntime.state.value.copy(title = label)))
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(state: RuntimeSnapshot) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(state))
    }

    private fun notification(state: RuntimeSnapshot) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle(state.title.ifBlank { "WA Al-Othmany Link Bot" })
        .setContentText(state.detail.ifBlank { state.phase.name })
        .setSubText("Health ${state.healthScore}% • ${state.effectiveMode} • ${state.groupsPerMinute.toInt()} grp/min")
        .setProgress(state.total.coerceAtLeast(0), state.current.coerceAtLeast(0), state.total <= 0)
        .setOngoing(state.phase in setOf(RuntimePhase.SYNCING, RuntimePhase.EXTRACTING, RuntimePhase.PAUSED, RuntimePhase.RECOVERING))
        .setOnlyAlertOnce(true)
        .setContentIntent(PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ))
        .addAction(0, if (state.phase == RuntimePhase.PAUSED) "Resume" else "Pause", actionIntent(if (state.phase == RuntimePhase.PAUSED) ACTION_RESUME else ACTION_PAUSE, 2))
        .addAction(0, "Skip", actionIntent(ACTION_SKIP, 3))
        .addAction(0, "Stop", actionIntent(ACTION_STOP, 4))
        .build()

    private fun actionIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, BotForegroundService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Bot runtime", NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        const val EXTRA_LABEL = "label"
        const val ACTION_PAUSE = "com.waalothmany.linkbot.PAUSE"
        const val ACTION_RESUME = "com.waalothmany.linkbot.RESUME"
        const val ACTION_STOP = "com.waalothmany.linkbot.STOP"
        const val ACTION_SKIP = "com.waalothmany.linkbot.SKIP"
        private const val CHANNEL_ID = "bot_runtime"
        private const val NOTIFICATION_ID = 7021
    }
}
