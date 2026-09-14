package com.waalothmany.linkbot.runtime.engine.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.waalothmany.linkbot.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

/** Typed client for the privileged Shizuku UserService. */
class ShizukuSystemGateway(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val bindMutex = Mutex()
    @Volatile private var service: IPrivilegedOps? = null
    @Volatile private var pending: CompletableDeferred<IPrivilegedOps>? = null

    private val args: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(appContext, PrivilegedOpsService::class.java))
            .processNameSuffix("wa_privileged")
            .debuggable(BuildConfig.DEBUG)
            .version(73)
            .daemon(false)
            .tag("wa-v73-privileged")
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val resolved = IPrivilegedOps.Stub.asInterface(binder)
            service = resolved
            pending?.complete(resolved)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            pending?.cancel()
            pending = null
            ShizukuRuntime.markProbeFailure("USER_SERVICE_DISCONNECTED")
        }
    }

    suspend fun probeUid(timeoutMs: Long = 5_000): Int {
        val remote = ensureBound(timeoutMs)
        val uid = withTimeout(timeoutMs) {
            withContext(Dispatchers.IO) { remote.probeUid() }
        }
        ShizukuRuntime.markProbeSuccess(uid)
        return uid
    }

    suspend fun listUsers(timeoutMs: Long = 5_000): String = withRemote(timeoutMs) { it.listUsers() }

    suspend fun listPackagesForUser(userId: Int, timeoutMs: Long = 5_000): String =
        withRemote(timeoutMs) { it.listPackagesForUser(userId) }

    suspend fun launchPackageForUser(userId: Int, packageName: String, timeoutMs: Long = 5_000): Int =
        withRemote(timeoutMs) { it.launchPackageForUser(userId, packageName) }

    suspend fun forceStopPackageForUser(userId: Int, packageName: String, timeoutMs: Long = 5_000): Int =
        withRemote(timeoutMs) { it.forceStopPackageForUser(userId, packageName) }

    suspend fun currentUser(timeoutMs: Long = 5_000): Int {
        val raw = withRemote(timeoutMs) { it.currentUser() }
        return raw.trim().toIntOrNull() ?: error("Invalid current user: $raw")
    }

    fun disconnect(remove: Boolean = false) {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return
        runCatching { Shizuku.unbindUserService(args, connection, remove) }
        service = null
        pending = null
    }

    private suspend fun <T> withRemote(timeoutMs: Long, block: (IPrivilegedOps) -> T): T {
        val remote = ensureBound(timeoutMs)
        return withTimeout(timeoutMs) { withContext(Dispatchers.IO) { block(remote) } }
    }

    private suspend fun ensureBound(timeoutMs: Long): IPrivilegedOps {
        service?.let { return it }
        val deferred = bindMutex.withLock {
            service?.let { return@withLock CompletableDeferred(it) }
            pending?.let { return@withLock it }
            check(ShizukuRuntime.state.value.binderAlive) { "Shizuku binder unavailable" }
            check(ShizukuRuntime.state.value.permissionGranted) { "Shizuku permission required" }
            CompletableDeferred<IPrivilegedOps>().also { created ->
                pending = created
                try {
                    Shizuku.bindUserService(args, connection)
                } catch (t: Throwable) {
                    pending = null
                    created.completeExceptionally(t)
                }
            }
        }
        return withTimeout(timeoutMs) { deferred.await() }
    }
}
