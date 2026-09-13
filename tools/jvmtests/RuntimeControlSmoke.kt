package com.waalothmany.linkbot.runtime

fun main() {
    BotRuntime.resetControlFlags()
    BotRuntime.update(RuntimeSnapshot(RuntimePhase.SYNCING, "sync"))
    BotRuntime.pause()
    check(BotRuntime.state.value.phase == RuntimePhase.PAUSED)
    BotRuntime.resume()
    check(BotRuntime.state.value.phase == RuntimePhase.SYNCING) { "sync resume lost phase: ${BotRuntime.state.value.phase}" }

    BotRuntime.update(RuntimeSnapshot(RuntimePhase.EXTRACTING, "extract", total = 4))
    BotRuntime.pause()
    BotRuntime.resume()
    check(BotRuntime.state.value.phase == RuntimePhase.EXTRACTING)

    BotRuntime.update(RuntimeSnapshot(RuntimePhase.SYNCING, "sync"))
    BotRuntime.skip()
    check(!BotRuntime.consumeSkip()) { "skip must be ignored outside extraction" }
    println("RuntimeControlSmoke: PASS")
}
