package com.waalothmany.linkbot.runtime.engine

interface ExecutionEngine {
    val id: EngineId
    fun capabilities(): Set<EngineCapability>
    suspend fun probe(): EngineProbeResult
    suspend fun execute(
        request: ExecutionRequest,
        context: ExecutionContext,
    ): EngineExecutionResult
}
