package com.waalothmany.linkbot.whatsapp

private val USER_INFO_REGEX = Regex("UserInfo\\{(\\d+):([^:}]+):")

data class AndroidProfileRecord(
    val userId: Int,
    val label: String,
    val profileType: String,
)

data class PrivilegedInstanceCandidate(
    val id: String,
    val packageName: String,
    val label: String,
    val kind: String,
    val androidUserId: Int,
    val profileType: String,
    val profileLabel: String?,
    val launchStrategy: String,
)

object ProfileAwareInstanceResolver {
    fun parseUsers(raw: String): List<AndroidProfileRecord> = raw.lineSequence()
        .mapNotNull { line ->
            val match = USER_INFO_REGEX.find(line) ?: return@mapNotNull null
            val userId = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val label = match.groupValues[2].trim().ifBlank { "User $userId" }
            AndroidProfileRecord(userId, label, classifyProfile(userId, label))
        }
        .distinctBy { it.userId }
        .sortedBy { it.userId }
        .toList()

    fun parsePackages(raw: String): Set<String> = raw.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("package:") }
        .map { it.removePrefix("package:").trim().lowercase() }
        .filter { it.isNotBlank() }
        .toCollection(linkedSetOf())

    fun candidates(
        profile: AndroidProfileRecord,
        packages: Iterable<String>,
    ): List<PrivilegedInstanceCandidate> = packages
        .asSequence()
        .map { it.trim().lowercase() }
        .filter { pkg -> PackageCandidatePolicy.isWhatsAppCandidate(pkg, labelForPackage(pkg)) }
        .distinct()
        .map { pkg ->
            val label = labelForPackage(pkg)
            PrivilegedInstanceCandidate(
                id = InstanceIdentityPolicy.stableId(profile.userId, pkg),
                packageName = pkg,
                label = when (profile.profileType) {
                    "PERSONAL" -> label
                    else -> "$label • ${profile.label}"
                },
                kind = PackageCandidatePolicy.kind(pkg, label),
                androidUserId = profile.userId,
                profileType = profile.profileType,
                profileLabel = profile.label,
                launchStrategy = "SHIZUKU",
            )
        }
        .sortedBy { it.label.lowercase() }
        .toList()

    private fun classifyProfile(userId: Int, label: String): String {
        val l = label.lowercase()
        return when {
            userId == 0 -> "PERSONAL"
            "work" in l || "عمل" in l -> "WORK"
            "dual" in l || "clone" in l || "parallel" in l || "مزدوج" in l -> "DUAL"
            "secure" in l || "knox" in l || "آمن" in l -> "SECURE"
            else -> "UNKNOWN"
        }
    }

    private fun labelForPackage(packageName: String): String = when (packageName) {
        "com.whatsapp" -> "WhatsApp"
        "com.whatsapp.w4b" -> "WhatsApp Business"
        else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
