package com.waalothmany.linkbot.tools

import com.waalothmany.linkbot.runtime.engine.EngineId
import com.waalothmany.linkbot.runtime.engine.ExecutionFailureClass
import com.waalothmany.linkbot.runtime.trace.TraceRecorder

object TraceRecorderSmoke {
    fun run() {
        val events = mutableListOf<String>()
        val recorder = TraceRecorder(clockMs = { 1_000L }) { event ->
            events += listOf(
                event.traceId,
                event.step,
                event.engine?.name ?: "NONE",
                event.attempt.toString(),
                event.durationMs.toString(),
                event.result,
                event.failure?.name ?: "NONE",
                event.fallbackTo?.name ?: "NONE",
            ).joinToString("|")
        }

        recorder.record(
            traceId = "trace-1",
            step = "ENGINE_EXECUTED",
            engine = EngineId.SHIZUKU,
            attempt = 1,
            startedAtMs = 900L,
            result = "FAILURE",
            failure = ExecutionFailureClass.BINDER_DEAD,
            fallbackTo = EngineId.STANDARD_ANDROID,
        )

        check(events.single() == "trace-1|ENGINE_EXECUTED|SHIZUKU|1|100|FAILURE|BINDER_DEAD|STANDARD_ANDROID")
        println("TraceRecorderSmoke: PASS")
    }
}
