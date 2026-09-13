package com.waalothmany.linkbot.whatsapp

/** Pure policy so package discovery can be tested without Android framework classes. */
object PackageCandidatePolicy {
    private val official = setOf("com.whatsapp", "com.whatsapp.w4b")

    fun isWhatsAppCandidate(packageName: String, label: String): Boolean {
        val pkg = packageName.trim().lowercase()
        val normalizedLabel = label.trim().lowercase()
        if (pkg in official) return true
        return "whatsapp" in pkg || "whatsapp" in normalizedLabel || "واتساب" in normalizedLabel
    }

    fun kind(packageName: String, label: String): String = when {
        packageName == "com.whatsapp" -> "PERSONAL"
        packageName == "com.whatsapp.w4b" -> "BUSINESS"
        label.contains("business", ignoreCase = true) || label.contains("أعمال") -> "BUSINESS_VARIANT"
        else -> "VARIANT"
    }
}
