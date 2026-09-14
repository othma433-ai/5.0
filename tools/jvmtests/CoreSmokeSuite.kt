package com.waalothmany.linkbot.tools

/** Runs the pure-Kotlin smoke mains in one JVM to keep release verification fast and deterministic. */
object CoreSmokeSuite {
    @JvmStatic
    fun main(args: Array<String>) {
        val classes = listOf(
            "com.waalothmany.linkbot.core.link.LinkEngineSmokeKt",
            "com.waalothmany.linkbot.core.importer.ExportChatParserSmokeKt",
            "com.waalothmany.linkbot.core.exporter.ExporterSmokeKt",
            "com.waalothmany.linkbot.automation.AutomationPolicySmokeKt",
            "com.waalothmany.linkbot.automation.ControlLabelPolicySmokeKt",
            "com.waalothmany.linkbot.automation.ControlClusterPolicySmokeKt",
            "com.waalothmany.linkbot.automation.ChildTraversalPolicySmokeKt",
            "com.waalothmany.linkbot.automation.ExtractionSelectionPolicySmokeKt",
            "com.waalothmany.linkbot.automation.UnreadTraversalPolicySmokeKt",
            "com.waalothmany.linkbot.automation.NavigationFallbackPolicySmokeKt",
            "com.waalothmany.linkbot.automation.ExtractionNavigationPolicySmokeKt",
            "com.waalothmany.linkbot.automation.SyncStrategyPolicySmokeKt",
            "com.waalothmany.linkbot.automation.ConservativeGroupRowPolicySmokeKt",
            "com.waalothmany.linkbot.automation.SearchResolutionSmokeKt",
            "com.waalothmany.linkbot.automation.SearchEntryPolicySmokeKt",
            "com.waalothmany.linkbot.automation.CheckpointSmokeKt",
            "com.waalothmany.linkbot.automation.PerformanceProfileSmokeKt",
            "com.waalothmany.linkbot.automation.GroupIdentityMatcherSmokeKt",
            "com.waalothmany.linkbot.automation.FilterVerificationPolicySmokeKt",
            "com.waalothmany.linkbot.automation.RowClassificationPolicySmokeKt",
            "com.waalothmany.linkbot.automation.ViewportIdentityPolicySmokeKt",
            "com.waalothmany.linkbot.automation.AdaptiveTimingSmokeKt",
            "com.waalothmany.linkbot.automation.StageCircuitBreakerSmokeKt",
            "com.waalothmany.linkbot.automation.SyncCoveragePolicySmokeKt",
            "com.waalothmany.linkbot.automation.ScreenEvidencePolicySmokeKt",
            "com.waalothmany.linkbot.automation.AutomationHealthPolicySmokeKt",
            "com.waalothmany.linkbot.automation.AccessibilityEventCoalescerSmokeKt",
            "com.waalothmany.linkbot.automation.MessageViewportPolicySmokeKt",
            "com.waalothmany.linkbot.automation.ContainerRolePolicySmokeKt",
            "com.waalothmany.linkbot.automation.SmartQueuePolicySmokeKt",
            "com.waalothmany.linkbot.automation.DurableViewportPolicySmokeKt",
            "com.waalothmany.linkbot.automation.FailureRecoveryPolicySmokeKt",
            "com.waalothmany.linkbot.automation.QueueProgressPolicySmokeKt",
            "com.waalothmany.linkbot.whatsapp.PackageCandidatePolicySmokeKt",
            "com.waalothmany.linkbot.whatsapp.InstanceInventoryPolicySmokeKt",
            "com.waalothmany.linkbot.whatsapp.InstanceIdentityPolicySmokeKt",
            "com.waalothmany.linkbot.capability.AccessibilityConnectionPolicySmokeKt",
            "com.waalothmany.linkbot.capability.OperationStartGateSmokeKt",
            "com.waalothmany.linkbot.capability.ReadinessEvaluatorSmokeKt",
            "com.waalothmany.linkbot.runtime.DiagnosticSanitizerSmokeKt",
            "com.waalothmany.linkbot.runtime.DiagnosticQueuePolicySmokeKt",
            "com.waalothmany.linkbot.runtime.OperationLeaseSmokeKt",
            "com.waalothmany.linkbot.data.PersistenceAccountingSmokeKt",
            "com.waalothmany.linkbot.runtime.RuntimePhaseSmokeKt",
            "com.waalothmany.linkbot.capability.RuntimeCapabilityModeSmokeKt",
            "com.waalothmany.linkbot.whatsapp.AdaptiveSelectorPolicySmokeKt",
            "com.waalothmany.linkbot.whatsapp.AdaptiveSelectorMemorySmokeKt",
            "com.waalothmany.linkbot.whatsapp.WhatsAppAdapterRegistrySmokeKt",
            "com.waalothmany.linkbot.whatsapp.InstanceProfileMetadataSmokeKt",
            "com.waalothmany.linkbot.automation.GroupFilterPolicySmokeKt",
            "com.waalothmany.linkbot.automation.QueuePersistencePolicySmokeKt",
            "com.waalothmany.linkbot.automation.NavigationProbePolicySmokeKt",
        )
        classes.forEach { name ->
            val cls = Class.forName(name)
            val method = cls.methods.firstOrNull { it.name == "main" && it.parameterCount == 0 }
                ?: error("No zero-argument main in $name")
            method.invoke(null)
        }
        println("CoreSmokeSuite: PASS (${classes.size} tests)")
    }
}
