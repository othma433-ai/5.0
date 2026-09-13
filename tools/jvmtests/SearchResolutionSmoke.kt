package com.waalothmany.linkbot.automation

fun main() {
    val rows = listOf(
        SearchRowEvidence("Radiology", "Lecture 4 uploaded", 2),
        SearchRowEvidence("Radiology", "Exam schedule", 0),
        SearchRowEvidence("Surgery", "Ward list", 1),
    )
    val byPreview = SearchResolutionPolicy.resolve("Radiology", "Exam schedule", rows)
    check(byPreview is SearchResolution.Unique && byPreview.index == 1)

    val ambiguous = SearchResolutionPolicy.resolve("Radiology", null, rows)
    check(ambiguous is SearchResolution.Ambiguous && ambiguous.count == 2)

    val unique = SearchResolutionPolicy.resolve("Surgery", null, rows)
    check(unique is SearchResolution.Unique && unique.index == 2)
    println("SearchResolutionSmoke: PASS")
}
