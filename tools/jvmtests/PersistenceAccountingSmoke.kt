package com.waalothmany.linkbot.data

fun main() {
    val empty = LinkPersistResult()
    check(empty.persistedCount == 0)
    val first = LinkPersistResult(newLinks = 2, insertedOccurrences = 3, duplicateOccurrences = 1)
    check(first.persistedCount == 3)
    check(first.detectedCount == 4)
    val sum = first + LinkPersistResult(newLinks = 1, insertedOccurrences = 2, duplicateOccurrences = 2)
    check(sum.newLinks == 3)
    check(sum.insertedOccurrences == 5)
    check(sum.duplicateOccurrences == 3)
    check(sum.detectedCount == 8)
    println("PersistenceAccountingSmoke: PASS")
}
