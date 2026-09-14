package com.waalothmany.linkbot.runtime.engine.shizuku

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.MainThread
import com.waalothmany.linkbot.runtime.DiagnosticLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

/** Application-lifetime Shizuku Binder/permission state. */
object ShizukuRuntime {
    const val PERMISSION_REQUEST_CODE = 0x5730
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    private val initialized = AtomicBoolean(false)
    private lateinit var appContext: Context
    private val mutable = MutableStateFlow(ShizukuRuntimeSnapshot())
    val state: StateFlow<ShizukuRuntimeSnapshot> = mutable.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        DiagnosticLog.record("SHIZUKU_BINDER_RECEIVED")
        dispatch(ShizukuSignal.BinderReceived)
        refreshPermissionState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        DiagnosticLog.record("SHIZUKU_BINDER_DIED")
        dispatch(ShizukuSignal.BinderDied)
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            DiagnosticLog.record("SHIZUKU_PERMISSION_RESULT", mapOf("granted" to granted))
            dispatch(if (granted) ShizukuSignal.PermissionGranted else ShizukuSignal.PermissionDenied)
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (initialized.compareAndSet(false, true)) {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        }
        refresh()
    }

    fun refresh() {
        val packageDetected = runCatching {
            appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        }.getOrDefault(false)

        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            dispatch(if (packageDetected) ShizukuSignal.PackageDetected else ShizukuSignal.PackageMissing)
            return
        }

        dispatch(ShizukuSignal.BinderReceived)
        if (runCatching { Shizuku.isPreV11() }.getOrDefault(true)) {
            dispatch(ShizukuSignal.Unsupported)
            return
        }
        refreshPermissionState()
    }

    @MainThread
    fun requestPermission(): Boolean {
        refresh()
        if (!mutable.value.binderAlive) return false
        if (mutable.value.permissionGranted) return true
        return runCatching {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                dispatch(ShizukuSignal.PermissionDenied)
                false
            } else {
                dispatch(ShizukuSignal.PermissionRequestStarted)
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
                true
            }
        }.getOrElse {
            dispatch(ShizukuSignal.ProbeFailed(it.javaClass.simpleName))
            false
        }
    }

    fun markProbeSuccess(uid: Int) {
        val version = runCatching { Shizuku.getVersion() }.getOrNull()
        dispatch(ShizukuSignal.ProbeSucceeded(uid = uid, apiVersion = version))
        DiagnosticLog.record(
            "SHIZUKU_READY",
            mapOf("uid" to uid, "version" to (version ?: -1)),
        )
    }

    fun markProbeFailure(detail: String) {
        dispatch(ShizukuSignal.ProbeFailed(detail))
        DiagnosticLog.record("SHIZUKU_PROBE_FAILED", mapOf("reason" to detail))
    }

    private fun refreshPermissionState() {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        dispatch(if (granted) ShizukuSignal.PermissionGranted else ShizukuSignal.PermissionRequired)
    }

    private fun dispatch(signal: ShizukuSignal) {
        mutable.value = ShizukuStateReducer.reduce(mutable.value, signal)
    }
}
