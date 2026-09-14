package com.waalothmany.linkbot.runtime.trace

import com.waalothmany.linkbot.runtime.engine.EngineId
import com.waalothmany.linkbot.runtime.engine.ExecutionFailureClass

data class ExecutionTraceEvent(
    val traceId: String,
    val step: String,
    val engine: EngineId?,
    val attempt: Int,
    val startedAtMs: Long,
    val durationMs: Long,
    val result: String,
    val failure: ExecutionFailureClass?,
    val fallbackTo: EngineId?,
    val metadata: Map<String, String> = emptyMap(),
)
