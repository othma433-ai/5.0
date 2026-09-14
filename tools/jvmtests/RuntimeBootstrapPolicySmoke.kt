package com.waalothmany.linkbot.jvmtests

import com.waalothmany.linkbot.runtime.RuntimeBootstrapPolicy
import com.waalothmany.linkbot.runtime.RuntimePhase

class RuntimeBootstrapPolicySmoke {
    fun doesNotOverrideActiveAutomation() {
        check(RuntimeBootstrapPolicy.canBootstrap(RuntimePhase.SYNCING_GROUPS).not())
        check(RuntimeBootstrapPolicy.canBootstrap(RuntimePhase.EXTRACTING).not())
        check(RuntimeBootstrapPolicy.canBootstrap(RuntimePhase.PAUSED).not())
        check(RuntimeBootstrapPolicy.canBootstrap(RuntimePhase.RECOVERING).not())
    }

    fun permitsIdleReadyStoppedAndErrorRefresh() {
        check(RuntimeBootstrapPolicy.canBootstrap(RuntimePhase.IDLE))
        check(RuntimeBootstrapPolicy.canBootstrap(RuntimePhase.READY))
        check(RuntimeBootstrapPolicy.canBootstrap(RuntimePhase.STOPPED))
        check(RuntimeBootstrapPolicy.canBootstrap(RuntimePhase.ERROR))
    }

    fun readinessMapsToReadyOrError() {
        check(RuntimeBootstrapPolicy.finalPhase(coreReady = true) == RuntimePhase.READY)
        check(RuntimeBootstrapPolicy.finalPhase(coreReady = false) == RuntimePhase.ERROR)
    }
}
