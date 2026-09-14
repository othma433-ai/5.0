package com.waalothmany.linkbot

import android.app.Application
import com.waalothmany.linkbot.runtime.EngineRegistry
import com.waalothmany.linkbot.runtime.engine.shizuku.ShizukuRuntime

class BotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        com.waalothmany.linkbot.runtime.DiagnosticLog.init(this)
        ShizukuRuntime.init(this)
        EngineRegistry.init(this)
        com.waalothmany.linkbot.runtime.DiagnosticLog.record("APP_START")
    }
}
