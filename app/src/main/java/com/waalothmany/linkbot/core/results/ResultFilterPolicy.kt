package com.waalothmany.linkbot.core.results

data class ResultFilterItem(
    val id: String,
    val url: String,
    val category: String,
    val group: String?,
    val timestamp: String?,
)

data class ResultFilter(
    val query: String = "",
    val category: String? = null,
    val group: String? = null,
    val date: String = "",
)

object ResultFilterPolicy {
    fun filter(items: List<ResultFilterItem>, filter: ResultFilter): List<ResultFilterItem> {
        val q = filter.query.trim().lowercase()
        val date = filter.date.trim().lowercase()
        return items.filter { item ->
            (q.isBlank() || item.url.lowercase().contains(q) || item.category.lowercase().contains(q) || item.group.orEmpty().lowercase().contains(q)) &&
                (filter.category.isNullOrBlank() || item.category.equals(filter.category, ignoreCase = true)) &&
                (filter.group.isNullOrBlank() || item.group.equals(filter.group, ignoreCase = true)) &&
                (date.isBlank() || item.timestamp.orEmpty().lowercase().contains(date))
        }
    }
}
