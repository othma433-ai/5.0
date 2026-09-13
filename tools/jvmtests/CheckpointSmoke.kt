package com.waalothmany.linkbot.automation

fun main() {
    val encoded = CheckpointCodec.encode(listOf("a", "b", "b", "c"), maxAnchors = 3)
    check(encoded == "v2:a|b|c") { encoded }
    check(CheckpointCodec.decode(encoded) == listOf("a", "b", "c"))
    check(CheckpointCodec.decode("old1|old2") == listOf("old1", "old2"))
    check(CheckpointCodec.reached(encoded, listOf("x", "b")))
    check(!CheckpointCodec.reached(encoded, listOf("x", "y")))
    println("CheckpointSmoke: PASS")
}
