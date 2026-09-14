package com.waalothmany.linkbot.runtime.trace

import com.waalothmany.linkbot.runtime.engine.EngineId
import com.waalothmany.linkbot.runtime.engine.ExecutionFailureClass

class TraceRecorder(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val sink: (ExecutionTraceEvent) -> Unit = {},
) {
    fun record(
        traceId: String,
        step: String,
        engine: EngineId? = null,
        attempt: Int = 0,
        startedAtMs: Long = clockMs(),
        result: String,
        failure: ExecutionFailureClass? = null,
        fallbackTo: EngineId? = null,
        metadata: Map<String, String> = emptyMap(),
    ) {
        val finished = clockMs()
        sink(
            ExecutionTraceEvent(
                traceId = traceId,
                step = step,
                engine = engine,
                attempt = attempt,
                startedAtMs = startedAtMs,
                durationMs = (finished - startedAtMs).coerceAtLeast(0L),
                result = result,
                failure = failure,
                fallbackTo = fallbackTo,
                metadata = metadata,
            )
        )
    }
}
