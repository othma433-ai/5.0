package com.waalothmany.linkbot

import android.content.Context
import com.waalothmany.linkbot.data.AppDatabase
import com.waalothmany.linkbot.data.GroupRepository
import com.waalothmany.linkbot.data.LinkRepository

object ServiceLocator {
    lateinit var database: AppDatabase
        private set
    lateinit var groups: GroupRepository
        private set
    lateinit var links: LinkRepository
        private set

    fun init(context: Context) {
        if (::database.isInitialized) return
        database = AppDatabase.create(context)
        groups = GroupRepository(database)
        links = LinkRepository(database)
    }
}
