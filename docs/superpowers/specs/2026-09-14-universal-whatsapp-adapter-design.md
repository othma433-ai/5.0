# Universal WhatsApp Adapter Design

## Goal
Extend WA Al-Othmany Link Bot v7.1 so Personal WhatsApp, WhatsApp Business, Dual/Work-profile instances, and structurally compatible discoverable variants can share one resilient automation engine without relying on one package-specific resource-ID table.

## Design
1. Keep the existing static adapter registry as the first source of truth.
2. Add package-neutral resource-ID suffix knowledge for Groups, All, Chats, Select All, and conversation-row controls. A suffix such as `conversations_filter_debug_view_id_groups` is matched regardless of the package prefix exposed by Accessibility.
3. Add an instance-scoped adaptive selector store backed by local SharedPreferences. Only control metadata is stored: resource-ID suffixes and normalized control labels. Conversation text is never stored by the selector learner.
4. During runtime, selector lookup order is: learned selector -> adapter suffix selector -> adapter full-ID selector -> label/peer-cluster selector -> structural fallback.
5. Learn only after an action is verified. Never learn a candidate merely because it was clicked.
6. Generic/discoverable variants use the same package-neutral suffixes but require stronger structural evidence before group rows are persisted.
7. Unsupported or inaccessible profiles remain visible as capability/discovery evidence but are not treated as verified-operational instances until launch + Accessibility foreground verification succeeds.
8. Keep current conservative fallback behavior: if Groups cannot be verified, ALL_CHATS_CLASSIFY may be used only on a verified chats surface and must never mark unseen groups missing.

## Safety and Stability Rules
- No long-lived AccessibilityNodeInfo references.
- No full message text in selector learning storage.
- No blind click loops; every action waits for a fresh accessibility snapshot or adaptive probe.
- A learned selector can be ignored after repeated verification failures and static/structural discovery must remain available.
- Runtime learning is scoped by `instanceId`, so the same package in different Android profiles can evolve independently.

## Verification
- Pure Kotlin tests for suffix normalization, hint generation, adaptive selector confidence, and per-instance separation.
- Static integration checks that service lookup uses learned + suffix hints.
- Existing release audit remains green.
- Android Gradle test/lint/assembleDebug are attempted; network/SDK failures are reported separately from source failures.
