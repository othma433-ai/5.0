package com.waalothmany.linkbot.whatsapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Persists only verified UI-control metadata. It never stores chat/message text.
 */
class AdaptiveSelectorStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("adaptive_selectors_v1", Context.MODE_PRIVATE)
    private val memory = AdaptiveSelectorMemory()
    private val loaded = HashSet<String>()

    @Synchronized
    fun candidates(instanceId: String, role: SelectorRole): List<LearnedSelectorCandidate> {
        ensureLoaded(instanceId, role)
        return memory.candidates(instanceId, role)
    }

    @Synchronized
    fun recordVerified(instanceId: String, role: SelectorRole, signature: SelectorSignature) {
        ensureLoaded(instanceId, role)
        memory.recordVerified(instanceId, role, signature)
        persist(instanceId, role)
    }

    @Synchronized
    fun recordFailure(instanceId: String, role: SelectorRole, signature: SelectorSignature) {
        ensureLoaded(instanceId, role)
        memory.recordFailure(instanceId, role, signature)
        persist(instanceId, role)
    }

    private fun ensureLoaded(instanceId: String, role: SelectorRole) {
        val key = key(instanceId, role)
        if (!loaded.add(key)) return
        val raw = prefs.getString(key, null) ?: return
        val decoded = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val signature = SelectorSignature(
                        resourceIdSuffix = item.optString("id").takeIf(String::isNotBlank),
                        normalizedLabel = item.optString("label").takeIf(String::isNotBlank),
                        className = item.optString("class").takeIf(String::isNotBlank),
                    )
                    if (!signature.isUseful()) continue
                    add(
                        LearnedSelectorCandidate(
                            signature = signature,
                            successes = item.optInt("successes", 1).coerceAtLeast(1),
                            failures = item.optInt("failures", 0).coerceAtLeast(0),
                            sequence = item.optLong("sequence", i.toLong() + 1L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
        memory.replace(instanceId, role, decoded)
    }

    private fun persist(instanceId: String, role: SelectorRole) {
        val array = JSONArray()
        memory.candidates(instanceId, role).forEach { candidate ->
            array.put(
                JSONObject().apply {
                    candidate.signature.resourceIdSuffix?.let { put("id", it) }
                    candidate.signature.normalizedLabel?.let { put("label", it) }
                    candidate.signature.className?.let { put("class", it) }
                    put("successes", candidate.successes)
                    put("failures", candidate.failures)
                    put("sequence", candidate.sequence)
                }
            )
        }
        prefs.edit().putString(key(instanceId, role), array.toString()).apply()
    }

    private fun key(instanceId: String, role: SelectorRole): String =
        "${stableKey(instanceId)}_${role.name.lowercase()}"

    private fun stableKey(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
