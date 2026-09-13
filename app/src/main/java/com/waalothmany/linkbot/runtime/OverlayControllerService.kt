package com.waalothmany.linkbot.runtime

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

class OverlayControllerService : Service() {
    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        if (Settings.canDrawOverlays(this)) showOverlay()
    }

    override fun onDestroy() {
        overlay?.let { runCatching { windowManager?.removeView(it) } }
        overlay = null
        params = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        if (overlay != null) return
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences("overlay", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
            setBackgroundColor(0xE6171A1D.toInt())
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", dp(12))
            y = prefs.getInt("y", dp(180))
        }
        params = layoutParams

        val handle = TextView(this).apply {
            text = "⋮⋮"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(0xFF9AA4AA.toInt())
            setPadding(dp(6), 0, dp(6), 0)
            contentDescription = "Move controller"
        }
        root.addView(handle, LinearLayout.LayoutParams(dp(36), dp(44)))
        installDragHandle(handle)

        fun button(icon: Int, description: String, action: () -> Unit) = ImageButton(this).apply {
            setImageResource(icon)
            setBackgroundColor(0x00000000)
            setColorFilter(0xFFFFFFFF.toInt())
            contentDescription = description
            setOnClickListener { action() }
            root.addView(this, LinearLayout.LayoutParams(dp(46), dp(46)))
        }
        button(android.R.drawable.ic_media_pause, "Pause") {
            com.waalothmany.linkbot.automation.WaAccessibilityService.instance?.pauseAutomation() ?: BotRuntime.pause()
        }
        button(android.R.drawable.ic_media_play, "Resume") {
            com.waalothmany.linkbot.automation.WaAccessibilityService.instance?.resumeAutomation() ?: BotRuntime.resume()
        }
        button(android.R.drawable.ic_media_next, "Skip group") {
            com.waalothmany.linkbot.automation.WaAccessibilityService.instance?.skipCurrent() ?: BotRuntime.skip()
        }
        button(android.R.drawable.ic_menu_close_clear_cancel, "Stop") {
            com.waalothmany.linkbot.automation.WaAccessibilityService.instance?.stopAutomation() ?: BotRuntime.stop()
        }

        windowManager?.addView(root, layoutParams)
        overlay = root
    }

    private fun installDragHandle(handle: View) {
        var initialX = 0
        var initialY = 0
        var downX = 0f
        var downY = 0f
        handle.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    downX = event.rawX
                    downY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = initialX + (event.rawX - downX).roundToInt()
                    lp.y = initialY + (event.rawY - downY).roundToInt()
                    runCatching { windowManager?.updateViewLayout(overlay, lp) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    getSharedPreferences("overlay", MODE_PRIVATE).edit()
                        .putInt("x", lp.x)
                        .putInt("y", lp.y)
                        .apply()
                    true
                }
                else -> false
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
