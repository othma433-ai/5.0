package com.waalothmany.linkbot.runtime.engine.shizuku

fun main() {
    var s = ShizukuRuntimeSnapshot()
    s = ShizukuStateReducer.reduce(s, ShizukuSignal.PackageDetected)
    check(s.state == ShizukuRuntimeState.BINDER_UNAVAILABLE)
    s = ShizukuStateReducer.reduce(s, ShizukuSignal.BinderReceived)
    check(s.state == ShizukuRuntimeState.BINDER_ALIVE_NO_PERMISSION)
    s = ShizukuStateReducer.reduce(s, ShizukuSignal.PermissionGranted)
    check(s.state == ShizukuRuntimeState.PROBING)
    s = ShizukuStateReducer.reduce(s, ShizukuSignal.ProbeSucceeded(uid = 2000))
    check(s.state == ShizukuRuntimeState.READY)
    check(s.serverUid == 2000)
    s = ShizukuStateReducer.reduce(s, ShizukuSignal.BinderDied)
    check(s.state == ShizukuRuntimeState.BINDER_DEAD)
    check(!s.ready)
    println("ShizukuStateSmoke: PASS")
}
