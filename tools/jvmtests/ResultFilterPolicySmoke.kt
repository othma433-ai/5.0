package com.waalothmany.linkbot.jvmtests

import com.waalothmany.linkbot.core.results.ResultFilter
import com.waalothmany.linkbot.core.results.ResultFilterItem
import com.waalothmany.linkbot.core.results.ResultFilterPolicy

class ResultFilterPolicySmoke {
    private val items = listOf(
        ResultFilterItem("1", "https://drive.google.com/a", "GOOGLE_DRIVE", "Study", "2026-09-14 10:00"),
        ResultFilterItem("2", "https://t.me/a", "TELEGRAM", "Friends", "2026-09-13 09:00"),
    )

    fun filtersByQueryTypeGroupAndDateTogether() {
        val filtered = ResultFilterPolicy.filter(
            items,
            ResultFilter(query = "drive", category = "GOOGLE_DRIVE", group = "Study", date = "2026-09-14"),
        )
        check(filtered.map { it.id } == listOf("1"))
    }

    fun blankFiltersReturnAll() {
        check(ResultFilterPolicy.filter(items, ResultFilter()).size == 2)
    }
}
