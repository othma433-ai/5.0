package com.waalothmany.linkbot.runtime.engine.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class RootCommandResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean = false,
) {
    val success: Boolean get() = !timedOut && exitCode == 0
}

class RootCommandGateway {
    suspend fun probeUid(timeoutMs: Long = 2_500): Int? {
        val result = execute(RootCommand.ProbeUid, timeoutMs)
        if (!result.success) return null
        return result.output.lineSequence().map { it.trim() }.firstOrNull { it.matches(Regex("\\d+")) }?.toIntOrNull()
    }

    suspend fun listUsers(timeoutMs: Long = 4_000): RootCommandResult = execute(RootCommand.ListUsers, timeoutMs)

    suspend fun listPackagesForUser(userId: Int, timeoutMs: Long = 4_000): RootCommandResult =
        execute(RootCommand.ListPackagesForUser(userId), timeoutMs)

    suspend fun currentUser(timeoutMs: Long = 2_500): Int? {
        val result = execute(RootCommand.CurrentUser, timeoutMs)
        return if (result.success) result.output.trim().lineSequence().firstOrNull()?.toIntOrNull() else null
    }

    suspend fun launchPackageForUser(userId: Int, packageName: String, timeoutMs: Long = 5_000): RootCommandResult {
        val resolve = execute(RootCommand.ResolvePackageForUser(userId, packageName), timeoutMs.coerceAtMost(3_000))
        if (!resolve.success) return resolve
        val component = resolve.output.lineSequence()
            .map { it.trim() }
            .lastOrNull { RootCommandPolicy.isValidComponent(it) }
            ?: return RootCommandResult(65, "No launch component")
        return execute(RootCommand.StartComponentForUser(userId, component), timeoutMs)
    }

    suspend fun forceStopPackageForUser(userId: Int, packageName: String, timeoutMs: Long = 4_000): RootCommandResult =
        execute(RootCommand.ForceStopPackageForUser(userId, packageName), timeoutMs)

    suspend fun execute(command: RootCommand, timeoutMs: Long): RootCommandResult = withContext(Dispatchers.IO) {
        require(timeoutMs in 250..30_000) { "Invalid root command timeout" }
        val rendered = RootCommandPolicy.render(command)
        val process = ProcessBuilder("su", "-c", rendered)
            .redirectErrorStream(true)
            .start()
        val completed = runCatching { process.waitFor(timeoutMs, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        if (!completed) {
            process.destroyForcibly()
            runCatching { process.waitFor(250, TimeUnit.MILLISECONDS) }
            return@withContext RootCommandResult(124, "timeout", timedOut = true)
        }
        val output = runCatching { process.inputStream.bufferedReader().use { it.readText().take(MAX_OUTPUT_CHARS) } }
            .getOrDefault("")
        RootCommandResult(process.exitValue(), output)
    }

    companion object {
        private const val MAX_OUTPUT_CHARS = 64 * 1024
    }
}
