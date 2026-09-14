package com.waalothmany.linkbot.runtime

import android.os.Process

/**
 * Android allocates application UIDs in per-user ranges of 100,000.
 * Keep this logic centralized so app code never depends on hidden UserHandle APIs.
 */
object AndroidUserIdentity {
    private const val PER_USER_RANGE = 100_000

    fun currentUserId(): Int = Process.myUid() / PER_USER_RANGE
}
