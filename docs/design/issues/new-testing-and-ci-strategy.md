---
action: create
title: "[MVP] Testing pyramid and CI gates"
milestone: "Phase 0 — Foundation"
labels: []
---

Identified in D2 §4 item 21 (Product: testing strategy — no test source set exists today). Not present in issues #1–#16.

## Goal

Establish the test layering, the injected-time discipline, and the CI gates that every later phase is held to — sized for one developer and GitHub Actions.

## Scope

### The pyramid (testing doc §1)

| Layer | Where | Runner | Share |
|---|---|---|---|
| Pure domain unit tests | `:core/src/test` | JUnit 5 + kotlin.test, no Android | ~70 %, < 10 s |
| Android unit tests | `:app/src/test` | Robolectric + Room in-memory + WorkManager test utils + `ShadowAlarmManager` | ~25 % |
| Instrumented smoke | `:app/src/androidTest` | device/emulator, Compose test rule | a handful |
| Manual device checklist | phase exit criteria | the developer's NFC phone | short list |

### Standing rules

- Tests before code on every use case (RED/GREEN).
- `:core` stays Android-free so its tests run in milliseconds; anything needing Android goes to `:app`.
- **No assertion depends on wall-clock time**: a `Today`/`Clock` port is injected everywhere. This is what makes the D5 scheduling suite deterministic.
- Fakes over mocks; in-memory repository fakes live in `:core/testFixtures`.
- Guards are re-proven: every health-detector test also asserts the detector is silent when the condition is absent (positive **and** negative control).

### CI gates (process gates, testing doc §5)

- `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug` on every push.
- **A change that touches `src/` without touching tests is rejected in review** (also a D7 cross-phase rule).
- Compare test runs by failing-set diff, not net count.
- Instrumented smoke runs manually or on a self-hosted device when one is available.

### Phase 0 deliverable

The test source sets exist and are no longer gitignored (`new-phase0-repo-hygiene`), CI is green, and the first three tests are in place: `LegacyKeyTest`, `NdefCodecTest` (legacy decode), and the Robolectric characterisation test for the current `MainActivity` behaviour.

### What later phases owe this issue

Each phase ships its migration test and its suite; the deterministic scheduling suite (testing doc §2) arrives with #4; `:core` suites for the codec, link policy, backup codec, templates, stock ledger and the Todoist representation/interpretation arrive with their phases.

## Explicitly not automated (testing doc §6)

Real NFC hardware behaviour, notification delivery timing under Doze on specific OEMs, Todoist's rendering of custom-scheme links, and Todoist's own recurrence arithmetic for FIXED rules. These are manual checklists and spikes (S2, S3, S4, S8), and the design answer for background delivery is the backstop worker, not a test.

## Acceptance criteria

1. `:core/src/test` and `:app/src/test` exist, are tracked by git, and run in CI on every push.
2. CI fails the build when `:core:test`, `:app:testDebugUnitTest` or `:app:lintDebug` fails.
3. The three Phase 0 tests pass and `:core` has no Android dependency on its test classpath.
4. A `Today`/`Clock` port exists in `:core` and no test reads the system clock.

## Design references

D2 §4 item 21 · testing doc §1–§6 · D7 Phase 0, cross-phase rules · D3 §3 (module boundary), ledger A6 · D8 spikes S2, S3, S4, S8.
