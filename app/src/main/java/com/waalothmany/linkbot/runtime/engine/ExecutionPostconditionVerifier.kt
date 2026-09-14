package com.waalothmany.linkbot.runtime.engine

interface ExecutionPostconditionVerifier {
    suspend fun verify(
        request: ExecutionRequest,
        result: EngineExecutionResult,
    ): Boolean
}

object SafeDefaultPostconditionVerifier : ExecutionPostconditionVerifier {
    override suspend fun verify(
        request: ExecutionRequest,
        result: EngineExecutionResult,
    ): Boolean = request.verification == VerificationPolicy.NONE
}
