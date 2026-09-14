package com.waalothmany.linkbot.capability

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.TimeUnit

object ShizukuCapabilityBackend {
    fun binderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun permissionGranted(): Boolean = binderAlive() && runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun usable(): Boolean = binderAlive() && permissionGranted()

    fun requestPermission(requestCode: Int): Boolean {
        if (!binderAlive()) return false
        if (permissionGranted()) return true
        return runCatching {
            if (!Shizuku.shouldShowRequestPermissionRationale()) {
                Shizuku.requestPermission(requestCode)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }
}

object RootCapabilityBackend {
    private val knownSuPaths = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/data/adb/ksu/bin/su",
    )

    fun detected(): Boolean = knownSuPaths.any { File(it).exists() }

    /** A bounded proof that `su` can actually execute as uid 0. */
    fun usable(timeoutMs: Long = 350L): Boolean {
        if (!detected()) return false
        return runCatching {
            val process = ProcessBuilder("su", "-c", "id -u").redirectErrorStream(true).start()
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                false
            } else {
                process.inputStream.bufferedReader().use { it.readText().trim() == "0" } && process.exitValue() == 0
            }
        }.getOrDefault(false)
    }
}
