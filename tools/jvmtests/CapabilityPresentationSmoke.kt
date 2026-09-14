package com.waalothmany.linkbot.tools

import com.waalothmany.linkbot.capability.CapabilityPresentation
import com.waalothmany.linkbot.runtime.engine.ProbeState
import com.waalothmany.linkbot.runtime.engine.shizuku.ShizukuRuntimeSnapshot
import com.waalothmany.linkbot.runtime.engine.shizuku.ShizukuRuntimeState

object CapabilityPresentationSmoke {
    fun run() {
        check(CapabilityPresentation.shizuku(ShizukuRuntimeSnapshot(state = ShizukuRuntimeState.BINDER_UNAVAILABLE)).text == "Binder unavailable")
        check(CapabilityPresentation.shizuku(ShizukuRuntimeSnapshot(state = ShizukuRuntimeState.BINDER_ALIVE_NO_PERMISSION, binderAlive = true)).action == "GRANT_PERMISSION")
        check(CapabilityPresentation.shizuku(ShizukuRuntimeSnapshot(state = ShizukuRuntimeState.READY, binderAlive = true, permissionGranted = true)).ready)
        check(!CapabilityPresentation.root(enabled = true, state = ProbeState.UNAVAILABLE).ready)
        check(CapabilityPresentation.root(enabled = true, state = ProbeState.READY).text == "Ready")
        check(CapabilityPresentation.executionMode(shizukuReady = true, rootReady = false) == "ADAPTIVE • SHIZUKU + ACCESSIBILITY")
        println("CapabilityPresentationSmoke: PASS")
    }
}
