package com.waalothmany.linkbot.whatsapp

fun main() {
    check(PackageCandidatePolicy.isWhatsAppCandidate("com.whatsapp", "WhatsApp"))
    check(PackageCandidatePolicy.isWhatsAppCandidate("com.whatsapp.w4b", "WhatsApp Business"))
    check(PackageCandidatePolicy.isWhatsAppCandidate("com.vendor.clone.whatsapp", "WhatsApp Dual"))
    check(PackageCandidatePolicy.isWhatsAppCandidate("com.vendor.anything", "واتساب العمل"))
    check(!PackageCandidatePolicy.isWhatsAppCandidate("com.google.android.youtube", "YouTube"))
    check(PackageCandidatePolicy.kind("com.whatsapp", "WhatsApp") == "PERSONAL")
    check(PackageCandidatePolicy.kind("com.whatsapp.w4b", "WhatsApp Business") == "BUSINESS")
    println("PackageCandidatePolicySmoke: PASS")
}
