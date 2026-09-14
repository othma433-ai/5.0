# Adaptive Multi-Engine Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade WA Al‑Othmany Link Bot from v7.2.0-rc1 to v7.3.0-rc1 with a real Shizuku runtime, optional validated Root engine, Standard Android fallback, supervised Accessibility lifecycle, profile-aware WhatsApp identities, adaptive engine routing, durable recovery, and measurable runtime verification.

**Architecture:** Introduce an `ExecutionOrchestrator` that plans system operations by capabilities and engine health instead of hard-coding a fixed fallback chain. Shizuku, Root, Standard Android, and Accessibility remain isolated adapters; every state-changing operation has a bounded timeout and a postcondition. Existing sync/extraction logic remains behaviorally compatible and is migrated incrementally behind the new execution boundary.

**Tech Stack:** Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Java 17, minSdk 26, target/compileSdk 35, Room 2.6.1, Kotlin Coroutines 1.9.0, Jetpack Compose, Shizuku API/provider 13.1.5, JUnit 4, AndroidX instrumentation tests.

**Spec:** `docs/superpowers/specs/2026-09-14-adaptive-multi-engine-runtime-design.md`

## Global Constraints

- Target version is `versionCode = 73`, `versionName = "7.3.0-rc1"`.
- Shizuku `READY` requires live Binder + permission granted + successful probe.
- Root availability requires an executed bounded `id -u` probe; file presence alone is insufficient.
- Accessibility remains mandatory for WhatsApp UI-tree reading and gestures.
- No destructive Room migration is allowed.
- Existing v7.2 sync/extraction behavior must remain available when Shizuku/Root are unavailable.
- Every privileged operation requires timeout, verification, structured failure, and fallback eligibility.
- Secure Folder/Knox restrictions must be reported, never bypassed.
- CI success means source/build verification only; real-device certification is tracked separately.
- No arbitrary shell interface and no user-controlled raw shell execution.

---

# Program Decomposition

This implementation is split into four reviewable subprojects so each one produces working software:

1. **Core Runtime & Shizuku** — engine model, planner, health, Shizuku lifecycle.
2. **Profile Identity & System Engines** — Room v3, profile-aware discovery, Root/Standard engines.
3. **Accessibility Recovery & Workflow Integration** — supervised binding, event readiness, orchestrator integration with sync/extraction.
4. **Telemetry, UI & Release Certification** — structured traces, status UI, tests, CI gates, runtime certification docs.

Each subproject is independently buildable and testable. Do not begin the next subproject until the prior subproject passes its stated gate.

---

# Subproject 1 — Core Runtime & Shizuku

### Task 1: Pin v7.3 version and Shizuku dependencies

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `tools/v73-runtime-integration-smoke.py`

**Interfaces:**
- Consumes: existing Android application module.
- Produces: Shizuku API/provider available to runtime code and manifest provider registration.

- [ ] **Step 1: Write the failing source smoke test**

Create `tools/v73-runtime-integration-smoke.py` with:

```python
from pathlib import Path

build = Path("app/build.gradle.kts").read_text()
manifest = Path("app/src/main/AndroidManifest.xml").read_text()

assert 'versionCode = 73' in build
assert 'versionName = "7.3.0-rc1"' in build
assert 'dev.rikka.shizuku:api:13.1.5' in build
assert 'dev.rikka.shizuku:provider:13.1.5' in build
assert 'rikka.shizuku.ShizukuProvider' in manifest
assert '${applicationId}.shizuku' in manifest
print("v7.3 Shizuku configuration: PASS")
```

- [ ] **Step 2: Run it and verify failure**

Run:

```bash
python3 tools/v73-runtime-integration-smoke.py
```

Expected: FAIL because v7.2 has no Shizuku dependency/provider and version is still 72.

- [ ] **Step 3: Modify build configuration**

In `app/build.gradle.kts`:

```kotlin
defaultConfig {
    applicationId = "com.waalothmany.linkbot"
    minSdk = 26
    targetSdk = 35
    versionCode = 73
    versionName = "7.3.0-rc1"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables.useSupportLibrary = true
}
```

Add:

```kotlin
implementation("dev.rikka.shizuku:api:13.1.5")
implementation("dev.rikka.shizuku:provider:13.1.5")
```

- [ ] **Step 4: Add Shizuku provider**

Inside `<application>` in `AndroidManifest.xml`:

```xml
<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:enabled="true"
    android:exported="true"
    android:multiprocess="false"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
```

- [ ] **Step 5: Run smoke + Gradle dependency resolution**

```bash
python3 tools/v73-runtime-integration-smoke.py
./gradlew --no-daemon :app:dependencies >/tmp/v73-deps.txt
grep -F "shizuku" /tmp/v73-deps.txt
```

Expected: smoke PASS and Shizuku dependencies resolve.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml tools/v73-runtime-integration-smoke.py
git commit -m "feat: add Shizuku runtime dependency for v7.3"
```

---

### Task 2: Define engine contracts and execution models

**Files:**
- Create: `app/src/main/java/com/waalothmany/linkbot/runtime/engine/ExecutionModels.kt`
- Create: `app/src/main/java/com/waalothmany/linkbot/runtime/engine/ExecutionEngine.kt`
- Create: `tools/jvmtests/ExecutionModelsSmoke.kt`
- Modify: `tools/verify-core.sh`

**Interfaces:**
- Produces:
  - `EngineId`
  - `EngineCapability`
  - `SystemOperation`
  - `InstanceTarget`
  - `ExecutionRequest`
  - `ExecutionContext`
  - `EngineProbeResult`
  - `EngineExecutionResult`
  - `ExecutionEngine`

- [ ] **Step 1: Write failing pure-Kotlin smoke**

```kotlin
package com.waalothmany.linkbot.tools

import com.waalothmany.linkbot.runtime.engine.*

object ExecutionModelsSmoke {
    @JvmStatic
    fun main(args: Array<String>) {
        val target = InstanceTarget(
            packageName = "com.whatsapp",
            androidUserId = 10,
            profileType = ProfileType.WORK,
        )
        val request = ExecutionRequest(
            operation = SystemOperation.LAUNCH_INSTANCE,
            target = target,
            verification = VerificationPolicy.FOREGROUND_PACKAGE,
        )
        check(request.target.androidUserId == 10)
        check(EngineId.SHIZUKU != EngineId.ACCESSIBILITY)
        check(EngineCapability.LAUNCH_PACKAGE_FOR_USER in SystemOperation.LAUNCH_INSTANCE.preferredCapabilities)
        println("ExecutionModelsSmoke: PASS")
    }
}
```

- [ ] **Step 2: Compile and confirm failure**

Add the smoke file to `tools/verify-core.sh` before implementation and run:

```bash
bash tools/verify-core.sh
```

Expected: compile failure for missing runtime engine types.

- [ ] **Step 3: Implement models**

`ExecutionModels.kt`:

```kotlin
package com.waalothmany.linkbot.runtime.engine

enum class EngineId { SHIZUKU, ROOT, STANDARD_ANDROID, ACCESSIBILITY }

enum class EngineCapability {
    ENUMERATE_USERS,
    ENUMERATE_PACKAGES_FOR_USER,
    RESOLVE_INSTANCE,
    LAUNCH_PACKAGE_CURRENT_USER,
    LAUNCH_PACKAGE_FOR_USER,
    FORCE_STOP_PACKAGE,
    BRING_TASK_TO_FRONT,
    READ_FOREGROUND_PACKAGE,
    ACCESSIBILITY_TREE,
    ACCESSIBILITY_GESTURE,
    ACCESSIBILITY_GLOBAL_ACTION,
    VERIFY_SCREEN,
    RECOVER_TO_HOME,
}

enum class ProfileType { PERSONAL, WORK, DUAL, SECURE, UNKNOWN }

data class InstanceTarget(
    val packageName: String,
    val androidUserId: Int,
    val profileType: ProfileType,
)

enum class VerificationPolicy {
    NONE,
    FOREGROUND_PACKAGE,
    ACCESSIBILITY_WINDOW,
}

enum class SystemOperation(val preferredCapabilities: Set<EngineCapability>) {
    DISCOVER_USERS(setOf(EngineCapability.ENUMERATE_USERS)),
    RESOLVE_INSTANCE(setOf(EngineCapability.RESOLVE_INSTANCE)),
    LAUNCH_INSTANCE(setOf(EngineCapability.LAUNCH_PACKAGE_FOR_USER)),
    RECOVER_INSTANCE(setOf(EngineCapability.BRING_TASK_TO_FRONT)),
    READ_UI(setOf(EngineCapability.ACCESSIBILITY_TREE)),
    UI_GESTURE(setOf(EngineCapability.ACCESSIBILITY_GESTURE)),
}

data class ExecutionRequest(
    val operation: SystemOperation,
    val target: InstanceTarget?,
    val verification: VerificationPolicy,
    val timeoutMs: Long = 5_000,
)

data class ExecutionContext(
    val traceId: String,
    val attempt: Int,
)

enum class ProbeState { READY, DEGRADED, UNAVAILABLE, PERMISSION_REQUIRED, ERROR }

data class EngineProbeResult(
    val engine: EngineId,
    val state: ProbeState,
    val capabilities: Set<EngineCapability>,
    val detail: String? = null,
)

enum class ExecutionFailureClass {
    TIMEOUT,
    PERMISSION_DENIED,
    BINDER_DEAD,
    PACKAGE_UNAVAILABLE,
    PROFILE_UNREACHABLE,
    VERIFICATION_FAILED,
    PLATFORM_RESTRICTED,
    UNKNOWN,
}

data class EngineExecutionResult(
    val engine: EngineId,
    val success: Boolean,
    val detail: String? = null,
    val failure: ExecutionFailureClass? = null,
)
```

`ExecutionEngine.kt`:

```kotlin
package com.waalothmany.linkbot.runtime.engine

interface ExecutionEngine {
    val id: EngineId
    fun capabilities(): Set<EngineCapability>
    suspend fun probe(): EngineProbeResult
    suspend fun execute(
        request: ExecutionRequest,
        context: ExecutionContext,
    ): EngineExecutionResult
}
```

- [ ] **Step 4: Run verification**

```bash
bash tools/verify-core.sh
```

Expected: `ExecutionModelsSmoke: PASS`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/waalothmany/linkbot/runtime/engine tools
git commit -m "feat: define adaptive execution engine contracts"
```

---

### Task 3: Add health scoring and circuit breaker

**Files:**
- Create: `runtime/engine/EngineHealthMonitor.kt`
- Create: `runtime/engine/EngineCircuitBreaker.kt`
- Create: `tools/jvmtests/EngineHealthSmoke.kt`
- Modify: `tools/verify-core.sh`

**Interfaces:**
- Produces:
  - `EngineHealthMonitor.score(engine, operation, target): Int`
  - `recordSuccess(engine, durationMs)`
  - `recordFailure(engine, failure)`
  - `EngineCircuitBreaker.allow(nowMs): Boolean`

- [ ] **Step 1: Write failing smoke**

```kotlin
package com.waalothmany.linkbot.tools

import com.waalothmany.linkbot.runtime.engine.*

object EngineHealthSmoke {
    @JvmStatic
    fun main(args: Array<String>) {
        val health = EngineHealthMonitor()
        val base = health.score(EngineId.SHIZUKU, SystemOperation.LAUNCH_INSTANCE)
        health.recordFailure(EngineId.SHIZUKU, ExecutionFailureClass.TIMEOUT)
        val degraded = health.score(EngineId.SHIZUKU, SystemOperation.LAUNCH_INSTANCE)
        check(degraded < base)

        val cb = EngineCircuitBreaker(maxFailures = 2, openMs = 1_000)
        check(cb.allow(100L))
        cb.recordFailure(100L)
        cb.recordFailure(101L)
        check(!cb.allow(500L))
        check(cb.allow(1_102L))
        println("EngineHealthSmoke: PASS")
    }
}
```

- [ ] **Step 2: Run and confirm missing types**

```bash
bash tools/verify-core.sh
```

- [ ] **Step 3: Implement circuit breaker**

```kotlin
package com.waalothmany.linkbot.runtime.engine

class EngineCircuitBreaker(
    private val maxFailures: Int,
    private val openMs: Long,
) {
    private var failures = 0
    private var openUntil = 0L

    fun allow(nowMs: Long): Boolean = nowMs >= openUntil

    fun recordSuccess() {
        failures = 0
        openUntil = 0L
    }

    fun recordFailure(nowMs: Long) {
        failures += 1
        if (failures >= maxFailures) {
            openUntil = nowMs + openMs
            failures = 0
        }
    }
}
```

- [ ] **Step 4: Implement health monitor**

Use deterministic scoring: base 100, subtract 20 per recent consecutive failure, clamp 0..100; successful execution resets consecutive failures and stores EWMA latency.

- [ ] **Step 5: Run verification**

```bash
bash tools/verify-core.sh
```

Expected PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/waalothmany/linkbot/runtime/engine tools
git commit -m "feat: add engine health and circuit breaker"
```

---

### Task 4: Implement operation planner and orchestrator

**Files:**
- Create: `runtime/engine/OperationPlanner.kt`
- Create: `runtime/engine/ExecutionOrchestrator.kt`
- Create: `tools/jvmtests/ExecutionOrchestratorSmoke.kt`
- Modify: `tools/verify-core.sh`

**Interfaces:**
- Consumes: `ExecutionEngine`, `EngineHealthMonitor`.
- Produces:
  - `OperationPlanner.candidates(request, probes)`
  - `ExecutionOrchestrator.execute(request): OrchestratedExecutionResult`

- [ ] **Step 1: Write fake-engine smoke**

The test must prove:
1. unsupported engines are skipped;
2. a failed high-score Shizuku attempt falls back to Standard;
3. health is penalized after failure;
4. success returns the selected engine.

Use a `FakeEngine` implementing `ExecutionEngine`.

- [ ] **Step 2: Run and confirm failure**

```bash
bash tools/verify-core.sh
```

- [ ] **Step 3: Implement planner**

Planner filters by:

```text
probe state READY/DEGRADED
required capability
circuit breaker availability
```

Then sorts by health score descending.

- [ ] **Step 4: Implement orchestrator**

Pseudo-contract:

```kotlin
suspend fun execute(request: ExecutionRequest): OrchestratedExecutionResult {
    val probes = engines.map { it.probe() }
    val candidates = planner.candidates(request, probes)
    for ((attempt, engine) in candidates.withIndex()) {
        val result = withTimeoutOrNull(request.timeoutMs) {
            engine.execute(request, ExecutionContext(traceId(), attempt + 1))
        } ?: EngineExecutionResult(engine.id, false, failure = ExecutionFailureClass.TIMEOUT)

        if (result.success) {
            health.recordSuccess(engine.id, durationMs)
            return OrchestratedExecutionResult.success(engine.id)
        }
        health.recordFailure(engine.id, result.failure ?: ExecutionFailureClass.UNKNOWN)
    }
    return OrchestratedExecutionResult.failure(ExecutionFailureClass.UNKNOWN)
}
```

The production implementation must preserve the same behavior but compute timing using `SystemClock.elapsedRealtime()` and inject a trace ID factory for tests.

- [ ] **Step 5: Run smoke**

```bash
bash tools/verify-core.sh
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/waalothmany/linkbot/runtime/engine tools
git commit -m "feat: add adaptive execution orchestrator"
```

---

### Task 5: Implement real Shizuku lifecycle

**Files:**
- Create: `runtime/engine/shizuku/ShizukuState.kt`
- Create: `runtime/engine/shizuku/ShizukuRuntime.kt`
- Create: `runtime/engine/shizuku/ShizukuEngine.kt`
- Create: `runtime/engine/shizuku/ShizukuSystemGateway.kt`
- Test: `app/src/test/java/com/waalothmany/linkbot/runtime/engine/shizuku/ShizukuStateReducerTest.kt`

**Interfaces:**
- Produces:
  - `StateFlow<ShizukuRuntimeSnapshot>`
  - `requestPermission(activityRequestCode: Int)`
  - `ShizukuEngine : ExecutionEngine`

- [ ] **Step 1: Write reducer tests**

Test exact transitions:

```text
BINDER_RECEIVED + permission=false -> BINDER_ALIVE_NO_PERMISSION
PERMISSION_GRANTED + binder alive -> READY after probe
BINDER_DEAD -> BINDER_DEAD
PERMISSION_DENIED -> PERMISSION_REQUIRED
```

- [ ] **Step 2: Run test to verify failure**

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests '*ShizukuStateReducerTest'
```

- [ ] **Step 3: Implement pure reducer**

Keep Android/Shizuku static API calls out of the reducer so it remains unit-testable.

- [ ] **Step 4: Implement runtime listeners**

Register:

```kotlin
Shizuku.addBinderReceivedListenerSticky(...)
Shizuku.addBinderDeadListener(...)
Shizuku.addRequestPermissionResultListener(...)
```

Read:

```kotlin
Shizuku.pingBinder()
Shizuku.checkSelfPermission()
Shizuku.shouldShowRequestPermissionRationale()
```

`READY` is emitted only after `pingBinder()` and permission success.

- [ ] **Step 5: Implement gateway**

Expose typed operations only. No arbitrary command string API.

- [ ] **Step 6: Implement ShizukuEngine**

Capabilities depend on gateway probe results. For any unsupported Android version/OEM operation, return `PLATFORM_RESTRICTED` rather than pretending success.

- [ ] **Step 7: Run tests + assemble**

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

- [ ] **Step 8: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: implement real Shizuku binder runtime"
```

**Subproject 1 gate:**

```bash
bash tools/verify-core.sh
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

All must pass before Subproject 2.

---

# Subproject 2 — Profile Identity & System Engines

### Task 6: Migrate WhatsApp instance schema to Room v3

**Files:**
- Modify: `data/Entities.kt`
- Modify: `data/AppDatabase.kt`
- Modify: `data/Daos.kt`
- Create: `app/src/androidTest/java/com/waalothmany/linkbot/data/Migration2To3Test.kt`
- Create/Update: `app/schemas/com.waalothmany.linkbot.data.AppDatabase/3.json`

**Interfaces:**
- `WhatsAppInstanceEntity` adds:
  - `androidUserId: Int`
  - `profileType: String`
  - `profileLabel: String?`
  - `launchStrategy: String`
  - `lastResolvedEngine: String?`
  - `reachable: Boolean`
  - `lastSuccessfulLaunchAt: Long?`
- Unique index changes from `[packageName]` to `[androidUserId, packageName]`.

- [ ] **Step 1: Write migration instrumentation test**

Create a v2 DB with two existing WhatsApp rows, run migration, assert:
- rows preserved;
- current-user mapping defaults to user `0` only when no runtime user ID is available to migration SQL;
- group/session foreign references remain intact;
- composite index exists.

- [ ] **Step 2: Run and verify failure**

```bash
./gradlew --no-daemon :app:connectedDebugAndroidTest
```

If no emulator/device exists in CI, run migration test in a managed-device job added later; locally the test remains source-controlled.

- [ ] **Step 3: Modify entity**

Use:

```kotlin
@Entity(
    tableName = "whatsapp_instances",
    indices = [Index(value = ["androidUserId", "packageName"], unique = true)],
)
data class WhatsAppInstanceEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val label: String,
    val kind: String,
    val androidUserId: Int = 0,
    val profileType: String = "PERSONAL",
    val profileLabel: String? = null,
    val launchStrategy: String = "STANDARD",
    val lastResolvedEngine: String? = null,
    val reachable: Boolean = true,
    val lastSuccessfulLaunchAt: Long? = null,
    val enabled: Boolean = true,
    val lastSeenAt: Long = System.currentTimeMillis(),
)
```

- [ ] **Step 4: Implement `MIGRATION_2_3`**

Create replacement table, copy all old columns plus defaults, drop old table, rename, recreate indexes.

- [ ] **Step 5: Update Room version**

```kotlin
version = 3
```

Add migration chain:

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3)
```

- [ ] **Step 6: Run Room schema/export and tests**

```bash
./gradlew --no-daemon :app:kspDebugKotlin :app:testDebugUnitTest
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/waalothmany/linkbot/data app/schemas app/src/androidTest
git commit -m "feat: make WhatsApp instances profile-aware"
```

---

### Task 7: Add profile-aware identity and discovery

**Files:**
- Create: `whatsapp/InstanceTarget.kt`
- Create: `whatsapp/ProfileAwareInstanceResolver.kt`
- Modify: `whatsapp/InstanceIdentityPolicy.kt`
- Modify: `whatsapp/WhatsAppInstanceDetector.kt`
- Modify: `whatsapp/InstanceInventoryPolicy.kt`
- Create: `tools/jvmtests/ProfileIdentitySmoke.kt`

**Interfaces:**
- `InstanceIdentityPolicy.stableId(androidUserId, packageName)`
- `ProfileAwareInstanceResolver.merge(standard, privileged)`

- [ ] **Step 1: Write smoke**

Assert:
- user 0 + `com.whatsapp` and user 10 + `com.whatsapp` produce different IDs;
- same user/package is stable;
- merge never collapses different users.

- [ ] **Step 2: Run failure**

```bash
bash tools/verify-core.sh
```

- [ ] **Step 3: Implement stable ID**

Hash canonical string:

```text
user:<id>|package:<packageName>
```

- [ ] **Step 4: Update detector**

Standard detector remains current-profile only. It must explicitly label its discovery source as `STANDARD`.

Privileged discovery results from Shizuku/Root are merged by `(androidUserId, packageName)`.

- [ ] **Step 5: Run verification**

```bash
bash tools/verify-core.sh
./gradlew --no-daemon :app:testDebugUnitTest
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/waalothmany/linkbot/whatsapp tools
git commit -m "feat: resolve WhatsApp instances across Android users"
```

---

### Task 8: Implement bounded Root engine

**Files:**
- Create: `runtime/engine/root/RootProbe.kt`
- Create: `runtime/engine/root/RootCommandGateway.kt`
- Create: `runtime/engine/root/RootEngine.kt`
- Test: `app/src/test/java/com/waalothmany/linkbot/runtime/engine/root/RootCommandGatewayTest.kt`

**Interfaces:**
- `RootCommandGateway.execute(command: RootCommand, timeoutMs: Long): RootCommandResult`
- `RootCommand` is a sealed class; no arbitrary raw string public API.

- [ ] **Step 1: Write tests for command serialization**

Allowed commands:

```kotlin
sealed interface RootCommand {
    data object ProbeUid : RootCommand
    data class ListPackagesForUser(val userId: Int) : RootCommand
    data class StartPackageForUser(val userId: Int, val component: String) : RootCommand
}
```

Test that malicious component strings containing `;`, `&&`, newline, backticks, `$(` are rejected before process execution.

- [ ] **Step 2: Verify failure**

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests '*RootCommandGatewayTest'
```

- [ ] **Step 3: Implement gateway**

Use `ProcessBuilder("su", "-c", sanitizedCommand)` with:
- timeout;
- bounded output;
- process destruction on timeout;
- explicit result object.

Probe command:

```text
id -u
```

Only output `0` means `READY`.

- [ ] **Step 4: Implement RootEngine**

Advertise only capabilities that have concrete gateway commands.

- [ ] **Step 5: Run tests**

```bash
./gradlew --no-daemon :app:testDebugUnitTest
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/waalothmany/linkbot/runtime/engine/root app/src/test
git commit -m "feat: add bounded optional root execution engine"
```

---

### Task 9: Implement Standard Android engine

**Files:**
- Create: `runtime/engine/standard/StandardAndroidEngine.kt`
- Test: `app/src/test/java/com/waalothmany/linkbot/runtime/engine/standard/StandardAndroidEngineTest.kt`

**Interfaces:**
- Supports current-user package launch only.
- Never advertises `LAUNCH_PACKAGE_FOR_USER`.

- [ ] **Step 1: Write capability test**

Assert Standard engine capabilities include:

```text
LAUNCH_PACKAGE_CURRENT_USER
RESOLVE_INSTANCE
```

and exclude:

```text
LAUNCH_PACKAGE_FOR_USER
ENUMERATE_USERS
```

- [ ] **Step 2: Implement engine**

Use `PackageManager.getLaunchIntentForPackage()` and `Context.startActivity()`.

- [ ] **Step 3: Run tests**

```bash
./gradlew --no-daemon :app:testDebugUnitTest
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/waalothmany/linkbot/runtime/engine/standard app/src/test
git commit -m "feat: add standard Android execution fallback"
```

**Subproject 2 gate:**

```bash
bash tools/verify-core.sh
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

---

# Subproject 3 — Accessibility Recovery & Workflow Integration

### Task 10: Add Accessibility runtime supervisor

**Files:**
- Create: `runtime/engine/accessibility/AccessibilityRuntimeState.kt`
- Create: `runtime/engine/accessibility/AccessibilityRuntimeSupervisor.kt`
- Modify: `capability/AccessibilityConnectionMonitor.kt`
- Modify: `automation/WaAccessibilityService.kt`
- Test: `app/src/test/java/com/waalothmany/linkbot/runtime/engine/accessibility/AccessibilityRuntimeSupervisorTest.kt`

**Interfaces:**
- Produces `StateFlow<AccessibilityRuntimeSnapshot>`.
- States:
  - `DISABLED`
  - `ENABLED_WAITING_BIND`
  - `BINDER_CONNECTED`
  - `EVENT_CHANNEL_ALIVE`
  - `WINDOW_ACCESS_READY`
  - `INTERRUPTED`
  - `DISCONNECTED`
  - `STALE`

- [ ] **Step 1: Write state-machine tests**

Test:
- enabled but no binder => WAITING_BIND;
- `onServiceConnected` => BINDER_CONNECTED;
- first event => EVENT_CHANNEL_ALIVE;
- non-null active root => WINDOW_ACCESS_READY;
- elapsed stale threshold => STALE;
- onDestroy => DISCONNECTED.

- [ ] **Step 2: Run tests and verify failure**

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests '*AccessibilityRuntimeSupervisorTest'
```

- [ ] **Step 3: Implement pure supervisor state logic**

Inject clock function to make stale detection deterministic.

- [ ] **Step 4: Wire lifecycle callbacks**

From `WaAccessibilityService`:
- `onServiceConnected` -> `markBinderConnected`
- `onAccessibilityEvent` -> `markEvent`
- successful `rootInActiveWindow` access -> `markWindowReady`
- `onInterrupt` -> `markInterrupted`
- `onDestroy` -> `markDisconnected`

- [ ] **Step 5: Preserve existing monitor compatibility**

`CapabilityManager` may continue reading `AccessibilityConnectionMonitor` during migration, but it must derive `connected` from the supervisor rather than maintaining a second conflicting source of truth.

- [ ] **Step 6: Run tests**

```bash
./gradlew --no-daemon :app:testDebugUnitTest
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/waalothmany/linkbot/runtime/engine/accessibility app/src/main/java/com/waalothmany/linkbot/capability app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt app/src/test
git commit -m "fix: supervise accessibility binding and event readiness"
```

---

### Task 11: Replace immediate Accessibility hard block with readiness policy

**Files:**
- Modify: `capability/OperationStartGate.kt`
- Modify: `MainViewModel.kt`
- Create: `capability/AccessibilityStartPolicy.kt`
- Test: `tools/jvmtests/AccessibilityStartPolicySmoke.kt`

**Interfaces:**
- `AccessibilityStartPolicy.evaluate(snapshot, nowMs)` returns:
  - `ALLOW`
  - `WAIT_FOR_BIND`
  - `REQUIRE_USER_ACTION`
  - `BLOCK`

- [ ] **Step 1: Write smoke**

Prove:
- disabled => REQUIRE_USER_ACTION;
- enabled + within bind grace => WAIT_FOR_BIND;
- window ready => ALLOW;
- enabled + bind timeout => REQUIRE_USER_ACTION.

- [ ] **Step 2: Run failure**

```bash
bash tools/verify-core.sh
```

- [ ] **Step 3: Implement policy**

Use an explicit bounded grace window; do not fake readiness.

- [ ] **Step 4: Update MainViewModel**

`syncGroups()`/`startExtraction()`:
- do not call service while only WAITING_FOR_BIND;
- show `"Connecting Accessibility…"` rather than generic failure;
- after timeout, show reconnect action.

- [ ] **Step 5: Run tests**

```bash
bash tools/verify-core.sh
./gradlew --no-daemon :app:testDebugUnitTest
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/waalothmany/linkbot/capability app/src/main/java/com/waalothmany/linkbot/MainViewModel.kt tools
git commit -m "fix: add accessibility bind grace and actionable recovery"
```

---

### Task 12: Introduce AccessibilityEngine adapter

**Files:**
- Create: `runtime/engine/accessibility/AccessibilityEngine.kt`
- Modify: `automation/WaAccessibilityService.kt`
- Test: `app/src/test/java/com/waalothmany/linkbot/runtime/engine/accessibility/AccessibilityEngineTest.kt`

**Interfaces:**
- Implements `ExecutionEngine`.
- Capabilities depend on supervisor state.
- UI operations remain delegated to `WaAccessibilityService`.

- [ ] **Step 1: Write capability tests**

Window-ready state advertises tree/gesture/screen verification.

Disconnected state advertises no UI capabilities.

- [ ] **Step 2: Implement adapter**

Do not move all navigation code yet. Adapter is an isolation boundary around the existing service.

- [ ] **Step 3: Run tests**

```bash
./gradlew --no-daemon :app:testDebugUnitTest
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/waalothmany/linkbot/runtime/engine/accessibility app/src/test
git commit -m "refactor: expose accessibility through execution engine"
```

---

### Task 13: Integrate orchestrator into WhatsApp launch/recovery

**Files:**
- Modify: `MainViewModel.kt`
- Modify: `automation/WaAccessibilityService.kt`
- Modify: `whatsapp/WhatsAppInstanceDetector.kt`
- Create: `runtime/EngineRegistry.kt`
- Test: `app/src/test/java/com/waalothmany/linkbot/runtime/EngineRegistryTest.kt`

**Interfaces:**
- `EngineRegistry.orchestrator`
- Launch requests go through orchestrator.
- UI parsing stays in Accessibility service.

- [ ] **Step 1: Write registry tests**

Verify engine registration order does not determine final routing; planner score/capabilities do.

- [ ] **Step 2: Implement registry**

Construct singleton engines with application context and shared health monitor.

- [ ] **Step 3: Replace direct launch calls**

Replace:

```kotlin
WhatsAppInstanceDetector.launch(context, packageName)
```

for cross-profile/runtime operations with:

```kotlin
orchestrator.execute(
    ExecutionRequest(
        operation = SystemOperation.LAUNCH_INSTANCE,
        target = target,
        verification = VerificationPolicy.FOREGROUND_PACKAGE,
    )
)
```

Keep Standard detector helper for current-profile discovery only.

- [ ] **Step 4: Add post-launch Accessibility verification**

Do not proceed to sync/extraction stage until target foreground + Accessibility window readiness pass.

- [ ] **Step 5: Run test/lint/build**

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main
git commit -m "feat: route WhatsApp launch through adaptive orchestrator"
```

---

### Task 14: Replace event dropping with bounded event coalescing

**Files:**
- Create: `automation/AccessibilityEventCoalescer.kt`
- Modify: `automation/WaAccessibilityService.kt`
- Test: `tools/jvmtests/AccessibilityEventCoalescerSmoke.kt`

**Interfaces:**
- `offer(EventSignal)`
- `drainLatestMeaningful()`

- [ ] **Step 1: Write smoke**

Prove:
- repeated content-change events for same screen are coalesced;
- scroll and window-state events are retained as meaningful transitions;
- queue is bounded.

- [ ] **Step 2: Implement coalescer**

Use a bounded structure; never let Accessibility callback accumulate unbounded work.

- [ ] **Step 3: Replace `eventGuard` drop behavior**

Keep single mutation execution, but store latest meaningful signal while busy and process once current handler finishes.

- [ ] **Step 4: Run core verification**

```bash
bash tools/verify-core.sh
./gradlew --no-daemon :app:testDebugUnitTest
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/waalothmany/linkbot/automation tools
git commit -m "perf: coalesce accessibility events without blind drops"
```

**Subproject 3 gate:**

```bash
bash tools/release-audit.sh
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

---

# Subproject 4 — Telemetry, UI & Release Certification

### Task 15: Add structured execution traces

**Files:**
- Create: `runtime/trace/ExecutionTrace.kt`
- Create: `runtime/trace/TraceRecorder.kt`
- Modify: `runtime/DiagnosticLog.kt`
- Modify: `runtime/engine/ExecutionOrchestrator.kt`
- Test: `tools/jvmtests/TraceRecorderSmoke.kt`

**Interfaces:**
- `TraceRecorder.start(operation, instanceId): traceId`
- `recordStep(...)`
- `finish(...)`

- [ ] **Step 1: Write smoke**

Prove all records contain:
- traceId;
- step;
- engine;
- attempt;
- duration;
- result;
- failure;
- fallback.

- [ ] **Step 2: Implement bounded asynchronous trace queue**

No synchronous file writes on the Accessibility callback path.

- [ ] **Step 3: Wire orchestrator**

Record:
- candidate selection;
- execution;
- postcondition;
- fallback;
- final result.

- [ ] **Step 4: Run verification**

```bash
bash tools/verify-core.sh
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/waalothmany/linkbot/runtime tools
git commit -m "feat: add structured multi-engine execution traces"
```

---

### Task 16: Replace misleading capability status UI

**Files:**
- Modify: `capability/CapabilityManager.kt`
- Modify: `ui/AppUi.kt`
- Modify: `MainViewModel.kt`
- Test: `app/src/test/java/com/waalothmany/linkbot/capability/CapabilityPresentationTest.kt`

**Interfaces:**
- UI states for:
  - Accessibility lifecycle
  - Shizuku Binder/permission/probe
  - Root readiness
  - execution mode
  - selected profile/user

- [ ] **Step 1: Write presentation tests**

Examples:

```text
Shizuku installed + binder absent => "Binder unavailable", not "Ready"
Binder alive + permission missing => "Permission required"
Binder alive + permission + probe pass => "Ready"
Root su file only => not "Ready"
```

- [ ] **Step 2: Update `CapabilitySnapshot`**

Replace simple `shizukuInstalled` presentation with a runtime snapshot while retaining a compatibility boolean only where needed.

- [ ] **Step 3: Add UI action**

When permission is required, display a single `Grant Shizuku permission` action.

- [ ] **Step 4: Show exact instance identity**

Example:

```text
WhatsApp • Personal • User 0
WhatsApp • Work • User 10
WhatsApp Business • User 0
```

- [ ] **Step 5: Run tests/build**

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/waalothmany/linkbot
git commit -m "feat: show verified engine and profile readiness"
```

---

### Task 17: Strengthen release audit and CI

**Files:**
- Modify: `tools/release-audit.sh`
- Modify: `tools/v73-runtime-integration-smoke.py`
- Modify: `.github/workflows/build-android-apk.yml`
- Modify: `.github/workflows/release-android-apk.yml`

**Interfaces:**
- Build CI certifies `SOURCE_VERIFIED` and `APK_BUILT`.
- Does not claim device runtime verification.

- [ ] **Step 1: Extend static safety audit**

Assert:
- version 73;
- Shizuku dependencies/provider;
- no `packageName`-unique instance index;
- Room version 3;
- orchestrator/health/circuit breaker present;
- root gateway has no public raw string execution function;
- Accessibility supervisor present.

- [ ] **Step 2: Add unit-test gate**

Ensure both workflows run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest
```

before lint/build.

- [ ] **Step 3: Keep APK artifact**

Artifact name:

```text
WA-Al-Othmany-Link-Bot-v7.3.0-rc1-APK
```

with SHA256 file.

- [ ] **Step 4: Run local audit**

```bash
bash tools/release-audit.sh
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

- [ ] **Step 5: Commit**

```bash
git add tools .github/workflows
git commit -m "ci: certify v7.3 multi-engine source and APK"
```

---

### Task 18: Add physical-device certification checklist

**Files:**
- Create: `DEVICE_TEST_v7.3.0-rc1.md`
- Modify: `PROJECT_STATUS.md`
- Create: `VERIFICATION_v7.3.0-rc1.txt`

**Interfaces:**
- Defines evidence needed before `RELEASE_CANDIDATE`.

- [ ] **Step 1: Add exact device checks**

Required checks:

```text
1. Fresh install and upgrade from v7.2.
2. Accessibility disabled -> enabled -> binder connected.
3. First Accessibility event and active-window root proof.
4. Shizuku Binder unavailable.
5. Shizuku Binder alive without permission.
6. Permission grant.
7. Shizuku READY probe.
8. Simulated/real Binder death while idle.
9. Binder death during a recoverable workflow.
10. WhatsApp personal launch.
11. WhatsApp Business launch.
12. Group synchronization.
13. Deep extraction.
14. New-only extraction.
15. Pause/resume.
16. App process recreation and checkpoint resume.
17. Shizuku -> Standard fallback for current user.
18. Shizuku -> Root fallback on rooted device only.
19. Duplicate-link protection after retry.
20. Work Profile routing where available.
21. Dual Messenger routing where available.
22. Secure Folder reports reachable or PLATFORM_RESTRICTED honestly.
```

- [ ] **Step 2: Define evidence for each check**

Each row records:
- device/model;
- Android version;
- app version;
- trace ID;
- result;
- relevant diagnostic excerpt;
- tester date.

- [ ] **Step 3: Update status definitions**

```text
SOURCE_VERIFIED
APK_BUILT
INSTALL_VERIFIED
RUNTIME_CORE_VERIFIED
PROFILE_VERIFIED
RELEASE_CANDIDATE
```

- [ ] **Step 4: Commit**

```bash
git add DEVICE_TEST_v7.3.0-rc1.md PROJECT_STATUS.md VERIFICATION_v7.3.0-rc1.txt
git commit -m "docs: add v7.3 physical-device certification gates"
```

---

# Final Verification

Run the complete release gate:

```bash
bash tools/release-audit.sh
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:lintDebug
./gradlew --no-daemon :app:assembleDebug
```

Expected:

```text
RELEASE AUDIT: PASS
BUILD SUCCESSFUL
APK: app/build/outputs/apk/debug/app-debug.apk
```

Then calculate:

```bash
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

Do **not** label the APK runtime-verified until the device checklist has passed.

# Review Checkpoints

After Task 5:
- core engine architecture and real Shizuku lifecycle review.

After Task 9:
- profile schema/system engine review.

After Task 14:
- Accessibility/workflow integration review.

After Task 18:
- final release-readiness review.

# Completion Definition

Implementation is complete only when:

1. All source/JVM/Android unit tests pass.
2. Room v2->v3 migration is validated.
3. GitHub Actions builds an installable v7.3.0-rc1 APK.
4. Runtime logs show actual Shizuku Binder/permission/probe state.
5. Existing no-Shizuku Accessibility path still works.
6. Real-device certification is performed separately and its status is recorded truthfully.
