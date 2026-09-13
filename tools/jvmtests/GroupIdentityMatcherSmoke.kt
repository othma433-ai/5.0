package com.waalothmany.linkbot.automation

fun main() {
    val existing = listOf(
        GroupIdentityRecord("g1", "radiology", "row", "Lecture 4 uploaded", 3),
        GroupIdentityRecord("g2", "radiology", "row", "Exam schedule", 0),
        GroupIdentityRecord("g3", "surgery", "row", "Ward list", 2),
    )
    val selected = GroupIdentityMatcher.match(
        GroupIdentityEvidence("radiology", "row", "Exam schedule", 0),
        existing,
        emptySet(),
    )
    check(selected == "g2") { selected.toString() }

    // A single same-title historical candidate is not enough when both strong identity
    // signals conflict. This prevents a newly-created duplicate-title group from being
    // silently attached to an old local record.
    val conflictingSingle = GroupIdentityMatcher.match(
        GroupIdentityEvidence("surgery", "different-structure", "Completely different current chat", null),
        existing,
        emptySet(),
    )
    check(conflictingSingle == null) { "conflicting single candidate must not auto-match: $conflictingSingle" }

    val structuralSingle = GroupIdentityMatcher.match(
        GroupIdentityEvidence("surgery", "row", null, null),
        existing,
        emptySet(),
    )
    check(structuralSingle == "g3")

    val ambiguous = GroupIdentityMatcher.match(
        GroupIdentityEvidence("radiology", "row", null, null),
        existing,
        emptySet(),
    )
    check(ambiguous == null)
    println("GroupIdentityMatcherSmoke: PASS")
}
