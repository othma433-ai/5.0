# WA Al-Othmany Link Bot v7.3.0-rc1

## Adaptive multi-engine runtime

- Added a capability-driven `ExecutionOrchestrator` instead of a fixed launch/fallback chain.
- Added engine health scoring and per-engine circuit breakers.
- Added postcondition verification so command success alone cannot advance a workflow.
- Added structured trace IDs covering engine selection, execution, fallback and final result.

## Real Shizuku integration

- Added official Shizuku API/provider dependencies.
- Added Binder lifecycle, permission lifecycle and runtime readiness state.
- Added a typed privileged UserService instead of treating the Shizuku APK as readiness.
- Added explicit in-app permission action and verified READY state.

## Root and standard fallback

- Added optional Root engine with explicit user opt-in.
- Root readiness now requires a bounded `id -u` probe returning UID 0.
- Privileged commands are typed/validated; no generic shell is exposed.
- Added Standard Android engine for operations that do not need elevation.

## Accessibility reliability

- Added supervised Accessibility binding/event/window readiness states.
- Added recovery handling for the real-device `enabled=true / connected=false` failure mode.
- Preserved Accessibility as the authoritative engine for WhatsApp UI-tree reading and gestures.
- Added event/state verification before sync/extraction continues.

## Multi-profile WhatsApp identity

- Instance identity now includes Android user/profile instead of relying only on package name.
- Room schema upgraded to v3 with non-destructive v2->v3 migration.
- Supports separate routing for personal/work/clone installations where Android/OEM security permits it.
- Secure Folder/Knox restrictions are reported as platform restrictions rather than false support.

## Diagnostics and UI

- Added bounded asynchronous diagnostic logging to reduce synchronous disk work in automation paths.
- Added Shizuku runtime state, Root probe state, adaptive mode and profile/user identity to the UI.
- Added engine retry/probe controls and Root fallback toggle.

## Release engineering

- Repaired Gradle wrapper bootstrap: CI now installs Gradle 8.9 before generating/verifying the wrapper JAR.
- Added v7.3 release audit gates and APK SHA-256 artifact output.
- CI/build success remains distinct from physical-device runtime certification.
