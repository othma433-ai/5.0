package com.waalothmany.linkbot.runtime.engine

fun main() {
    val target = InstanceTarget(
        packageName = "com.whatsapp",
        androidUserId = 10,
        profileType = ProfileType.WORK,
    )
    val request = ExecutionRequest(
        operation = SystemOperation.LAUNCH_INSTANCE,
        target = target,
        verification = VerificationPolicy.FOREGROUND_PACKAGE,
    )
    check(request.target?.androidUserId == 10)
    check(EngineId.SHIZUKU != EngineId.ACCESSIBILITY)
    check(EngineCapability.LAUNCH_PACKAGE_FOR_USER in SystemOperation.LAUNCH_INSTANCE.preferredCapabilities)
    println("ExecutionModelsSmoke: PASS")
}
