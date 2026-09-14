package com.waalothmany.linkbot

import android.app.Application

class BotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        com.waalothmany.linkbot.runtime.DiagnosticLog.init(this)
        com.waalothmany.linkbot.runtime.DiagnosticLog.record("APP_START")
    }
}
