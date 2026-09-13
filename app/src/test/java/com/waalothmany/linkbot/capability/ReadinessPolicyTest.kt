package com.waalothmany.linkbot.capability

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessPolicyTest {
    @Test
    fun enabledButDisconnectedIsNotReady() {
        val report = ReadinessEvaluator.evaluate(
            CapabilityFlags(
                accessibilityEnabled = true,
                accessibilityConnected = false,
                overlay = true,
                notifications = true,
                shizukuInstalled = true,
                rootDetected = false,
            ),
            whatsappInstances = 1,
        )
        assertFalse(report.coreReady)
        assertTrue("Accessibility service not connected" in report.blockers)
    }

    @Test
    fun operationGateAllowsOnlyFullyOperationalContext() {
        val allowed = OperationStartGate.evaluate(
            OperationStartContext(
                instanceSelected = true,
                packageLaunchable = true,
                accessibilityEnabled = true,
                accessibilityConnected = true,
                notificationsReady = true,
                operationBusy = false,
            )
        )
        assertTrue(allowed.allowed)
    }
}
