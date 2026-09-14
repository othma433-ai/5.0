package com.waalothmany.linkbot.tools

import com.waalothmany.linkbot.automation.AccessibilityEventCoalescer
import com.waalothmany.linkbot.automation.AccessibilityEventSignal
import com.waalothmany.linkbot.automation.EventSignalKind

object AccessibilityEventCoalescerSmoke {
    fun run() {
        val q = AccessibilityEventCoalescer(capacity = 3)
        q.offer(AccessibilityEventSignal("com.whatsapp", 1, EventSignalKind.CONTENT))
        q.offer(AccessibilityEventSignal("com.whatsapp", 2, EventSignalKind.CONTENT))
        check(q.size() == 1) { "content churn should coalesce" }

        q.offer(AccessibilityEventSignal("com.whatsapp", 3, EventSignalKind.SCROLL))
        q.offer(AccessibilityEventSignal("com.whatsapp", 4, EventSignalKind.WINDOW_STATE))
        check(q.size() == 3)
        check(q.poll()?.eventType == 2)
        check(q.poll()?.kind == EventSignalKind.SCROLL)
        check(q.poll()?.kind == EventSignalKind.WINDOW_STATE)

        repeat(10) { q.offer(AccessibilityEventSignal("com.whatsapp", 100 + it, EventSignalKind.CLICK)) }
        check(q.size() == 3) { "queue must stay bounded" }
        println("AccessibilityEventCoalescerSmoke: PASS")
    }
}
