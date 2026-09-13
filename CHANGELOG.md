# Changelog

## 7.2.0-rc1 — 2026-09-14

- Prevent overlapping/stale automation starts with generation-tokened atomic operation ownership (ABA-safe).
- Harden Stop-during-startup handling and session-before-queue persistence ordering.
- Add performance-aware bounded navigation probe policy.
- Remove legacy fixed 500 ms navigation retry loops.
- Expand smoke suite from 37 baseline checks to 39 checks.
- Add v7.2 integration audit.
- Add Gradle 8.9 wrapper metadata and verified bootstrap.
- Add GitHub build artifact and tag-release workflows.
- Add dynamic version-derived APK naming and SHA-256 output.

## 7.1.0-rc1

- Hardened semantic filter detection, nested-screen recovery, extraction navigation, Select-All fallback, unread-only traversal, and accessibility child-order handling.
