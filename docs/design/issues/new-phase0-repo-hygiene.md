---
action: create
title: "[MVP] Phase 0: clone-buildable repo, AGP 9 toolchain, :core module, CI"
milestone: "Phase 0 — Foundation"
labels: []
---

Identified in D2 §4 item 1 (Foundational: toolchain and repository hygiene). Not present in issues #1–#16.

## Goal

A clone-buildable, CI-tested repository on the modern toolchain with the legacy behaviour characterised by tests, and **no behaviour change for users**.

## Scope

### Repository hygiene (D1 §8 debt T1)

- `.gitignore`: stop ignoring the Gradle wrapper, `settings.gradle.kts`, `gradle/`, and the test source directories; start ignoring `build/`.
- Remove the tracked build outputs from the tree (`app/build/outputs/…/app-debug.apk` and the committed release APK); keep them in a GitHub release if wanted.
- Commit the wrapper, `settings.gradle.kts`, `gradle.properties`, and `gradle/libs.versions.toml`.

### Toolchain (D3 §15, spike S1)

- AGP 9.4.x with built-in Kotlin (2.4.x), Gradle 9.7.1 wrapper, KSP 2.
- JDK 17 for `compileOptions` / `jvmTarget`.
- minSdk 26 (ruling R-11) / targetSdk 36 / compileSdk 37. targetSdk 37 and the `DISPATCH_NFC_MESSAGE` permission are **Phase 7**, not here.
- Version catalog pinning Room 3.0.x (`androidx.room3`), Compose BOM 2026.08.00, Material3 1.4.x, Navigation 3 1.1.x, WorkManager 2.11.x, DataStore, kotlinx-serialization, kotlinx-coroutines.
- Fallbacks are named, not assumed: Room 2.8.5 only on a concrete S1 deficiency; Navigation-Compose 2.x; AGP 8.13 for this phase only if AGP 9 fights back.

### Manifest

Remove the `package` attribute; declare `android:exported` on every component; add `dataExtractionRules`; add `<queries>`.

### The `:core` module (D3 §3, ledger A6)

Create the pure-Kotlin `:core` module and move `LegacyKey` and `NdefCodec` (legacy decode only) into it, used by the three existing activities **with no behaviour change**. `:core` must stay Android-free so its tests run in milliseconds.

### CI

GitHub Actions running `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug` on every push. The gate detail is `new-testing-and-ci-strategy`.

### Schema and user-visible behaviour

No schema. Nothing changes for users; version 1.1 (versionCode 2).

## Explicitly not in Phase 0

No Compose colours, typography, shapes, components, mockups or screen redesigns — Phase 0 stays behaviour-preserving (D12 "Scope rule"). No Room schema, no new features.

## Acceptance criteria (D7 Phase 0 exit criteria)

1. `git clone && ./gradlew :core:test :app:testDebugUnitTest` passes on a machine without Android Studio.
2. A tag written by the shipped 1.0 APK resolves on a 1.1 build in situation A (if the key was found), or the legacy decode test proves the same bytes decode identically.
3. `git status` is clean after `assembleDebug`.
4. `LegacyKeyTest` (known vectors), `NdefCodecTest` (legacy decode) and a Robolectric characterisation test that `MainActivity` stores `MD5[0:8] → text` all pass in CI.

## Design references

D2 §4 item 1 · D1 §5 (current toolchain), §8 (debt register T1) · D3 §3 (package layout), §15 (build and toolchain targets), ledger A6 · D7 Phase 0 · D8 spike S1, risk 4, ruling R-11 · testing doc §5 · D12 "Scope rule" (Phase 0 stays behaviour-preserving).
