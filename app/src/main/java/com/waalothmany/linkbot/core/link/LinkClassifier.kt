package com.waalothmany.linkbot.core.link

import java.net.URI

object LinkClassifier {
    fun classify(url: String): LinkCategory {
        val normalized = UrlNormalizer.normalize(url)
        val uri = runCatching { URI(normalized) }.getOrNull()
        val host = uri?.host?.lowercase().orEmpty()
        val path = uri?.path?.lowercase().orEmpty()

        return when {
            host == "chat.whatsapp.com" -> LinkCategory.WHATSAPP_GROUP
            host.endsWith("whatsapp.com") && (path.contains("channel") || path.contains("channels")) -> LinkCategory.WHATSAPP_CHANNEL
            host.endsWith("whatsapp.com") || host == "wa.me" -> LinkCategory.WHATSAPP_OTHER
            host == "t.me" || host.endsWith("telegram.me") || host.endsWith("telegram.org") -> LinkCategory.TELEGRAM
            host == "drive.google.com" || host == "docs.google.com" -> LinkCategory.GOOGLE_DRIVE
            host.endsWith("1drv.ms") || host.endsWith("onedrive.live.com") -> LinkCategory.ONEDRIVE
            host.endsWith("dropbox.com") || host.endsWith("dropboxusercontent.com") -> LinkCategory.DROPBOX
            host == "mega.nz" || host.endsWith("mega.co.nz") -> LinkCategory.MEGA
            host == "youtu.be" || host.endsWith("youtube.com") -> LinkCategory.YOUTUBE
            host.endsWith("tiktok.com") -> LinkCategory.TIKTOK
            host.endsWith("instagram.com") -> LinkCategory.INSTAGRAM
            host.endsWith("facebook.com") || host == "fb.watch" -> LinkCategory.FACEBOOK
            host == "x.com" || host.endsWith("twitter.com") -> LinkCategory.X_TWITTER
            host.endsWith("zoom.us") || host == "meet.google.com" || host.endsWith("teams.microsoft.com") -> LinkCategory.MEETING
            path.endsWith(".pdf") || path.endsWith(".doc") || path.endsWith(".docx") || path.endsWith(".xls") || path.endsWith(".xlsx") || path.endsWith(".ppt") || path.endsWith(".pptx") || path.endsWith(".zip") -> LinkCategory.DOCUMENT
            host.isNotBlank() -> LinkCategory.WEBSITE
            else -> LinkCategory.OTHER
        }
    }
}
