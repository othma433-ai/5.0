# WA Al‑Othmany Link Bot v7.3.0‑rc1
## Advanced Adaptive Multi‑Engine Runtime Design

**Status:** DESIGN CANDIDATE  
**Target:** Android 8+ (minSdk 26), targetSdk 35  
**Planned versionCode:** 73  
**Planned versionName:** 7.3.0-rc1  
**Primary objective:** make WhatsApp automation resilient across normal installs, WhatsApp Business, compatible variants, Work Profile, Dual Messenger, and other Android users/profiles where the platform actually permits access, while preserving the existing extraction/sync workflows.

---

## 1. Current problems this design addresses

The v7.2 runtime currently has four important limitations:

1. `Shizuku` is only detected as an installed package. The application does not bind to Shizuku, request its permission, track Binder health, or execute privileged operations through it.
2. Runtime start is blocked when Android reports the accessibility service as enabled but `WaAccessibilityService.onServiceConnected()` has not established the process-local connection monitor. Real device logs show this exact state:
   - `a11yEnabled=true`
   - `a11yConnected=false`
   - `OPERATION_START_BLOCKED reason=ACCESSIBILITY_NOT_CONNECTED`
3. WhatsApp instance identity is package-name-centric. The database currently places a unique index on `packageName`, so identical packages installed in different Android users/profiles cannot be represented safely as separate runtime targets.
4. `WaAccessibilityService` currently owns too many responsibilities: workflow state, navigation, synchronization, extraction, package launch, recovery, timing, queue management, and runtime verification. Adding Shizuku/Root directly into this class would make reliability worse.

The solution is not a fixed fallback chain. The application needs an adaptive execution layer that plans each operation using one or more engines.

---

## 2. Architecture

### 2.1 ExecutionOrchestrator

`ExecutionOrchestrator` becomes the single runtime entry point for system-level actions.

It receives an `ExecutionRequest`, for example:

```kotlin
ExecutionRequest(
    operation = SystemOperation.LAUNCH_INSTANCE,
    target = InstanceTarget(
        packageName = "com.whatsapp",
        androidUserId = 10,
        profileType = ProfileType.WORK
    ),
    verification = VerificationPolicy.FOREGROUND_PACKAGE
)
```

It does **not** assume a fixed engine order. It asks the registry for engines capable of this specific operation, scores them, executes the best plan, verifies the postcondition, and falls back only when necessary.

Example:

```text
LAUNCH_INSTANCE(user=10, package=com.whatsapp)

Candidate plan:
1. ShizukuEngine.launchForUser()          score 92
2. RootEngine.launchForUser()             score 76
3. StandardAndroidEngine.launchCurrent()   score 40

Selected:
ShizukuEngine

Postcondition:
Foreground package == com.whatsapp AND resolved user/profile == 10

If verification fails:
health penalty -> retry policy -> next eligible engine
```

### 2.2 Engine model

All engines implement a narrow interface:

```kotlin
interface ExecutionEngine {
    val id: EngineId

    fun capabilities(): Set<EngineCapability>

    suspend fun probe(): EngineProbeResult

    suspend fun execute(
        request: ExecutionRequest,
        context: ExecutionContext
    ): EngineExecutionResult
}
```

Initial engines:

- `ShizukuEngine`
- `RootEngine`
- `StandardAndroidEngine`
- `AccessibilityEngine`

Accessibility is intentionally still a first-class engine. Shizuku and Root do not replace Accessibility-tree reading.

---

## 3. Capability-driven routing

Each engine advertises only what it can actually prove.

Example capabilities:

```text
ENUMERATE_USERS
ENUMERATE_PACKAGES_FOR_USER
RESOLVE_INSTANCE
LAUNCH_PACKAGE_CURRENT_USER
LAUNCH_PACKAGE_FOR_USER
FORCE_STOP_PACKAGE
BRING_TASK_TO_FRONT
READ_FOREGROUND_PACKAGE
ACCESSIBILITY_TREE
ACCESSIBILITY_GESTURE
ACCESSIBILITY_GLOBAL_ACTION
VERIFY_SCREEN
RECOVER_TO_HOME
```

The orchestrator chooses per operation.

Typical mappings:

- Discover WhatsApp installations across users:
  `Shizuku` preferred, `Root` secondary, Standard current-profile discovery fallback.
- Launch exact WhatsApp in a known Android user:
  `Shizuku` preferred, `Root` secondary.
- Read chat/group UI:
  `Accessibility` only.
- Click/scroll/gesture:
  `Accessibility` only.
- Recover a stuck package:
  Shizuku/Root for task/package recovery, followed by Accessibility verification.
- Current-profile ordinary package launch:
  Standard Android is preferred when no privilege is needed.

This minimizes privileged execution and keeps behavior predictable.

---

## 4. Shizuku integration

### 4.1 Dependency

Pin the official Shizuku API and provider libraries:

```kotlin
implementation("dev.rikka.shizuku:api:13.1.5")
implementation("dev.rikka.shizuku:provider:13.1.5")
```

The version must remain pinned in the release branch.

### 4.2 Provider

Add the official `ShizukuProvider` to the manifest using the application ID authority.

The provider is used only for Shizuku/Sui Binder acquisition. It does not make the app privileged by itself.

### 4.3 Runtime lifecycle

Create:

```text
ShizukuRuntime
ShizukuState
ShizukuPermissionController
ShizukuBinderSupervisor
```

State model:

```text
NOT_INSTALLED
BINDER_UNAVAILABLE
BINDER_ALIVE_NO_PERMISSION
PERMISSION_REQUESTING
READY
BINDER_DEAD
UNSUPPORTED
ERROR
```

The UI must never show `READY` merely because the Shizuku package exists.

`READY` requires:

```text
binder alive
AND API supported
AND permission granted
AND runtime probe succeeded
```

### 4.4 Binder lifecycle

Register official listeners for:

```text
Binder received
Binder dead
Permission result
```

Binder death invalidates in-flight privileged plans and triggers replanning.

### 4.5 Privileged execution

Do not scatter Shizuku calls around the app.

Use one adapter boundary:

```text
ShizukuSystemGateway
```

The gateway supports only explicitly required operations for the application.

Initial scope:

```text
get Android users/profiles
resolve package for user
check package installed/enabled
launch package/activity for user
read package/task state where supported
controlled package/task recovery
```

No Shizuku operation is considered successful until its postcondition is verified.

---

## 5. Root integration

Root is optional.

`RootEngine` must distinguish:

```text
NOT_PRESENT
SU_PRESENT_PERMISSION_UNKNOWN
PERMISSION_DENIED
READY
DEGRADED
ERROR
```

A file named `/system/xbin/su` is not sufficient proof.

The probe executes a short bounded command and verifies the returned UID.

Root commands are centralized in:

```text
RootCommandGateway
```

Rules:

- strict timeout
- explicit argument escaping
- no shell command construction from raw UI text
- no arbitrary user-supplied command execution
- command whitelist for app operations
- stdout/stderr size bounds
- process kill on timeout
- telemetry for exit code and duration

Root is used only for system operations the app actually requires. It is not used to bypass Knox/Secure Folder security boundaries.

---

## 6. Standard Android engine

`StandardAndroidEngine` handles operations available without elevated privilege:

```text
launch current-profile package
resolve launcher activity
bring own app UI forward
open accessibility settings
open overlay settings
open Shizuku app
read current package visibility allowed by Android
```

Using Standard Android first when privilege is unnecessary reduces fragility.

---

## 7. Accessibility redesign

### 7.1 AccessibilityRuntimeSupervisor

The current binary distinction:

```text
enabled / connected
```

is expanded to:

```text
DISABLED
ENABLED_WAITING_BIND
BINDER_CONNECTED
EVENT_CHANNEL_ALIVE
WINDOW_ACCESS_READY
INTERRUPTED
DISCONNECTED
STALE
```

The runtime records separate timestamps:

```text
enabledDetectedAt
serviceConnectedAt
lastAccessibilityEventAt
lastWindowRootAt
lastSuccessfulActionAt
```

### 7.2 Startup grace policy

`enabled=true + connected=false` must not instantly become a permanent hard failure.

Policy:

```text
freshly enabled:
  short grace window
  refresh lifecycle
  observe service connection
  open Accessibility settings only when user action is actually required

previously connected then lost:
  mark degraded
  cancel unsafe UI action
  preserve checkpoint
  try rebind/recovery path
```

The app still must **not** fake a connected Accessibility service. If Android never binds the service, automation remains blocked, but the diagnosis becomes specific and actionable.

### 7.3 First-event proof

Connection alone is not enough for UI automation.

Before starting a destructive navigation workflow, prove:

```text
service connected
AND event channel alive
AND rootInActiveWindow obtainable when target app is foreground
```

---

## 8. EngineHealthMonitor

Every engine maintains a health record:

```kotlin
data class EngineHealth(
    val state: HealthState,
    val successRate: Double,
    val consecutiveFailures: Int,
    val latencyEwmaMs: Long?,
    val lastSuccessAt: Long?,
    val lastFailureAt: Long?,
    val circuitOpenUntil: Long?,
)
```

Health states:

```text
HEALTHY
DEGRADED
UNHEALTHY
CIRCUIT_OPEN
UNAVAILABLE
```

Scoring considers:

```text
capability match
permission/readiness
target user/profile
recent success/failure
latency
circuit state
verification history
operation risk
```

An engine that repeatedly times out automatically loses priority.

---

## 9. Circuit breaker and retry policy

Retries are bounded per **operation step**, not infinite.

Example:

```text
attempt 1 -> same engine retry for transient binder failure
attempt 2 -> re-probe
attempt 3 -> fallback engine
persistent failure -> checkpoint + pause/fail safe
```

Circuit breakers are maintained separately for:

```text
Shizuku Binder
Root shell
Accessibility action dispatch
Package launch
Profile enumeration
```

This prevents one broken subsystem from poisoning all automation.

---

## 10. Operation Planner

The existing workflow should request outcomes rather than directly invoking implementation mechanisms.

Example synchronization start:

```text
Resolve instance
Launch correct profile/package
Prove target foreground
Prove Accessibility UI access
Navigate to chat list
Open groups filter
Verify groups filter
Enumerate rows
Persist viewport
Continue until stable end proof
```

The planner marks every step with:

```text
required capabilities
preferred engines
timeout
retry policy
postcondition
rollback/recovery policy
```

This gives deterministic diagnostics.

---

## 11. Profile-aware WhatsApp identity

The current unique `packageName` model must be migrated.

New stable identity:

```text
InstanceKey =
    androidUserId
    + profileSerial/profile kind where observable
    + packageName
    + install identity where available
```

New `WhatsAppInstanceEntity` fields:

```text
id
packageName
label
kind
androidUserId
profileType
profileLabel
installSource
launchStrategy
lastResolvedEngine
enabled
reachable
lastSeenAt
lastSuccessfulLaunchAt
```

Unique database key:

```text
(androidUserId, packageName)
```

not:

```text
packageName
```

Room database moves from schema version 2 to version 3 with a controlled migration.

Existing v7.2 current-user records map to the current Android user.

---

## 12. Work Profile, Dual Messenger, Secure Folder

### Work Profile

If Shizuku/Root can enumerate the Android user and package, represent it as a separate instance.

Launch only after the orchestrator proves the package/user combination is reachable.

### Dual Messenger / cloned packages

Represent separate package identities when the OEM exposes different package IDs.

If the OEM uses the same package name in another Android user, differentiate by `androidUserId`.

### Secure Folder / Knox

Do **not** claim support solely because a package can be seen.

Possible states:

```text
DETECTED
REACHABLE
LAUNCHABLE
UI_AUTOMATION_AVAILABLE
PLATFORM_RESTRICTED
```

If Knox prevents cross-profile execution, report `PLATFORM_RESTRICTED`.

The application must not attempt to defeat platform security boundaries.

---

## 13. Runtime trace model

Every top-level user operation gets a `traceId`.

Every step gets:

```text
traceId
stepId
operation
instanceId
androidUserId
engine
attempt
startedAt
durationMs
result
verification
failureClass
fallbackTo
```

Example:

```text
TRACE_START trace=...
INSTANCE_RESOLVED user=10 package=com.whatsapp
ENGINE_SELECTED operation=LAUNCH engine=SHIZUKU score=94
ENGINE_EXECUTED result=SUCCESS durationMs=83
POSTCONDITION_FAILED reason=FOREGROUND_MISMATCH
ENGINE_HEALTH_DEGRADED engine=SHIZUKU
FALLBACK_SELECTED engine=ROOT
POSTCONDITION_PASS
ACCESSIBILITY_WINDOW_READY
SYNC_STAGE_START stage=GROUP_LIST
```

Logs are structured internally and rendered to human-readable diagnostic text for export.

---

## 14. Failure taxonomy

Replace generic failures with stable machine-readable classes:

```text
SHIZUKU_NOT_INSTALLED
SHIZUKU_BINDER_UNAVAILABLE
SHIZUKU_PERMISSION_DENIED
SHIZUKU_BINDER_DIED
SHIZUKU_OPERATION_TIMEOUT
ROOT_NOT_AVAILABLE
ROOT_PERMISSION_DENIED
ROOT_COMMAND_TIMEOUT
PROFILE_NOT_REACHABLE
PACKAGE_NOT_INSTALLED_FOR_USER
PACKAGE_NOT_LAUNCHABLE
ACCESSIBILITY_DISABLED
ACCESSIBILITY_WAITING_BIND
ACCESSIBILITY_BIND_TIMEOUT
ACCESSIBILITY_EVENT_CHANNEL_STALE
ACCESSIBILITY_WINDOW_UNAVAILABLE
POSTCONDITION_FAILED
ENGINE_CIRCUIT_OPEN
NO_VALID_EXECUTION_PLAN
PLATFORM_RESTRICTED
```

No fallback should erase the original failure cause.

---

## 15. Concurrency model

System operations run through a serialized operation coordinator per target instance.

Rules:

```text
one active navigation mutation per WhatsApp instance
read-only health probes may run concurrently
binder callbacks never perform heavy work directly
all engine transitions carry a generation token
stale generations cannot commit state
stop invalidates the operation lease
pause preserves a durable checkpoint
```

Accessibility events are conflated by meaningful screen state instead of blindly dropping all events while `eventGuard` is occupied.

---

## 16. Performance

Avoid frequent full accessibility-tree flattening.

Introduce:

```text
screen snapshot caching
node query helpers
event coalescing
bounded diagnostic queue
asynchronous log persistence
adaptive polling
per-engine latency tracking
```

No fixed multi-second sleeps in the navigation engine.

Waits are condition-driven with bounded deadlines.

---

## 17. Security

The application must never expose a generic privileged shell.

Shizuku and Root gateways expose only app-defined operations.

Sensitive logs must not store:

```text
full message text unless user explicitly enables it
tokens/passwords
raw shell secrets
private file contents
```

Package/user identifiers may be logged because they are necessary to diagnose instance routing.

---

## 18. UI behavior

System status should display real execution state:

```text
Accessibility
Enabled / Binding / Connected / Window Ready / Error

Shizuku
Not installed / Binder unavailable / Permission required / Ready / Degraded

Root
Unavailable / Permission required / Ready / Degraded

Execution mode
Adaptive Multi-Engine

Selected WhatsApp
Personal • User 0 • com.whatsapp
Business • User 0 • com.whatsapp.w4b
Work • User 10 • com.whatsapp
```

The UI should include one action when necessary:

```text
Grant Shizuku permission
Reconnect Accessibility
Open Shizuku
Retry system probe
```

No misleading green status.

---

## 19. Planned source layout

New packages/files:

```text
runtime/engine/ExecutionEngine.kt
runtime/engine/ExecutionOrchestrator.kt
runtime/engine/ExecutionModels.kt
runtime/engine/EngineCapability.kt
runtime/engine/EngineHealthMonitor.kt
runtime/engine/EngineCircuitBreaker.kt
runtime/engine/OperationPlanner.kt

runtime/engine/shizuku/ShizukuRuntime.kt
runtime/engine/shizuku/ShizukuEngine.kt
runtime/engine/shizuku/ShizukuSystemGateway.kt

runtime/engine/root/RootEngine.kt
runtime/engine/root/RootCommandGateway.kt

runtime/engine/standard/StandardAndroidEngine.kt

runtime/engine/accessibility/AccessibilityEngine.kt
runtime/engine/accessibility/AccessibilityRuntimeSupervisor.kt

whatsapp/ProfileAwareInstanceResolver.kt
whatsapp/InstanceTarget.kt
```

Existing components to modify:

```text
MainViewModel.kt
WaAccessibilityService.kt
CapabilityManager.kt
AccessibilityConnectionMonitor.kt
WhatsAppInstanceDetector.kt
InstanceInventoryPolicy.kt
Entities.kt
Daos.kt
AppDatabase.kt
AppUi.kt
AndroidManifest.xml
app/build.gradle.kts
release/runtime smoke tests
GitHub Actions release gates
```

---

## 20. Database migration v2 -> v3

Migration requirements:

1. Create a new WhatsApp instance table with profile-aware columns and composite uniqueness.
2. Preserve existing IDs where safe.
3. Map all v2 records to current user.
4. Preserve groups, sessions, queue items, occurrences, and links.
5. Rebuild foreign/index relationships without data loss.
6. Add an automated migration test.

No destructive migration is permitted.

---

## 21. Test strategy

### Layer A — pure Kotlin tests

Test:

```text
engine scoring
fallback selection
circuit breaker
health decay/recovery
retry classification
operation planning
profile identity
trace propagation
generation tokens
```

### Layer B — Android JVM/instrumentation

Test:

```text
Shizuku state reducer
permission state flow
Accessibility supervisor
Room 2->3 migration
manifest/provider configuration
profile-aware DAO behavior
foreground/runtime service integration
```

### Layer C — physical-device runtime certification

A release candidate cannot be declared runtime-verified until a real device test proves:

```text
Accessibility bind and first event
Shizuku Binder alive
Shizuku permission grant
Shizuku Binder death/recovery
normal WhatsApp launch
WhatsApp Business launch
group synchronization
deep extraction
new-only extraction
pause/resume
process recreation
fallback from Shizuku to Standard/Accessibility
fallback from Shizuku to Root on rooted test device where available
profile-aware instance routing
no duplicate links after retry
```

Work Profile/Dual/Secure Folder certifications are marked individually according to the device capabilities available for testing.

---

## 22. Release gates

CI success alone means:

```text
SOURCE_VERIFIED
```

not:

```text
DEVICE_VERIFIED
```

Release states:

```text
SOURCE_VERIFIED
APK_BUILT
INSTALL_VERIFIED
RUNTIME_CORE_VERIFIED
PROFILE_VERIFIED
RELEASE_CANDIDATE
```

A green GitHub Action must not be presented as proof that WhatsApp runtime automation works.

---

## 23. Implementation sequence

Phase 1 introduces the engine abstractions and tests without changing existing behavior.

Phase 2 adds real Shizuku Binder/permission lifecycle.

Phase 3 adds profile-aware discovery and Room v3 migration.

Phase 4 implements Shizuku system operations and verification.

Phase 5 adds Root and Standard engines.

Phase 6 integrates the orchestrator with synchronization/extraction startup.

Phase 7 introduces Accessibility lifecycle recovery and event-channel health.

Phase 8 adds adaptive health scoring, circuit breakers, and structured traces.

Phase 9 performs UI capability/status updates.

Phase 10 completes CI tests and real-device certification.

Each phase must leave the application buildable and preserve the previous working workflow.

---

## 24. Acceptance criteria

The feature is considered successfully implemented only when:

- Shizuku is a real runtime dependency, not package detection.
- `READY` means Binder alive + permission + successful probe.
- Shizuku Binder death does not corrupt a session.
- Root is validated by an execution probe, not file presence.
- Package identity supports multiple Android users.
- Existing v2 data migrates without loss.
- Accessibility enabled-but-not-connected has a supervised recovery state.
- Normal v7.2 Accessibility behavior remains available when Shizuku/Root are unavailable.
- Every privileged action has a bounded timeout and postcondition.
- Fallback is recorded in a structured trace.
- Sync/extraction retain durable checkpoints.
- The application does not claim Secure Folder support when the platform blocks it.
- CI verifies source and APK creation.
- Real-device runtime tests separately certify operational behavior.

---

## 25. Non-goals for v7.3.0-rc1

The release will not:

- expose a generic shell terminal;
- bypass Samsung Knox or Android profile security;
- rely on undocumented package-name guesses as proof of profile identity;
- replace Accessibility with Shizuku;
- treat an installed Shizuku APK as proof the API is usable;
- mark runtime testing as passed based only on static source checks.

---

## Decision

**Recommended architecture:** Adaptive Multi-Engine Runtime Orchestrator.

This is the preferred architecture because it separates privileged system operations from WhatsApp UI automation, supports graceful degradation, makes profile routing explicit, and provides measurable verification for every state-changing action.
