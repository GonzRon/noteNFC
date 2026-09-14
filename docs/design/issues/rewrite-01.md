---
action: rewrite
number: 1
title: "[EPIC] Evolve noteNFC into an NFC-first maintenance tracker"
milestone: ""
labels: []
---

> Revised 2026-09-14 per the approved design package (`docs/design/`, review 1). Original text is preserved in this issue's edit history and in `docs/design/issues/original/issue_1.md`. Design references: D2 §1 (row 1), D2 §5; `docs/design/README.md` ("The architecture in plain language", "Phase sequence"); D7 (phase sequence); D3 §1–§2.

Umbrella issue. No code lands here; it tracks the reconciled scope and the phase order.

## Goal

Preserve noteNFC's existing NFC-to-external-note behaviour while adding an asset-centric maintenance system.

> Scan the physical asset → immediately see what it is, what has been done to it, and what needs to happen next.

## Product principles (unchanged)

- Local-first; useful with no account and no server.
- An NFC tag stores only a stable identifier, never mutable asset data.
- Maintenance history is auditable: events keep `created_at`, nothing is overwritten implicitly, and schedule state is recomputed from events rather than authored (D5 §5).
- The existing Joplin share → write → scan → launch workflow must keep working, including for tags written by the shipped 1.0 APK (D6).
- Useful for UPS batteries, lawn equipment, generators, HVAC, vehicles, tools, hot tubs.

## Primary workflows (unchanged)

1. Create an asset and write/bind an NFC tag.
2. Scan a tag to open the asset directly.
3. Record a maintenance event in a few taps.
4. Define recurring maintenance by elapsed time and/or usage.
5. Receive local reminders when maintenance is due.
6. Mark maintenance complete and automatically calculate the next due date.
7. Launch an external note when desired, preserving the original noteNFC use case.

## Reconciled MVP set

This replaces the original single "MVP" list. Tiers and phases are D2 §1 and D7.

**Phase 0 — Foundation**

- `new-phase0-repo-hygiene` — clone-buildable repo, AGP 9 toolchain, `:core` module, CI
- `new-signing-key-investigation` — the installed APK's certificate and the upgrade path
- `new-testing-and-ci-strategy` — testing pyramid and CI gates

**Phase 1 — Tag survival (M1)**

- #2 Room + Asset entity
- `new-tag-payload-v1-legacy-resolver` — noteNFC tag payload format v1 and the legacy `md5_short` resolver
- `new-tag-bind-rebind-ux` — bind, rebind, revoke, unknown-tag flows
- #6 generalised external links
- #8 backup / export / import (re-tiered to MVP-1)
- `new-deeplink-contract` — the `notenfc://` contract
- `new-untrusted-input-policy` — tag payloads, deep links, stored URIs

**Phase 2 — Journal + profiles**

- #3 asset event journal with measurements and consumables
- #13 configurable event/measurement profiles
- `new-meter-model` — meter definitions, readings, baselines, reset
- `new-date-semantics` — calendar-date semantics
- `new-asset-templates` — templates that seed definitions, profiles, and default schedules

**Phase 3 — Scheduling + local reminders**

- #4 scheduling engine and state model
- `new-reminder-provider-interface` — the `ReminderProvider` port
- `new-local-reminder-provider` — daily digest alarm, WorkManager backstop, boot receivers, channels
- `new-platform-permissions-scheduling` — POST_NOTIFICATIONS, no exact alarms, BOOT/TIME/TIMEZONE
- `new-reminder-health` — health findings and idempotent repair
- #11 local notification quick actions
- #14 seasonal activation windows
- #5 asset dashboard (a minimal list ships in Phase 1C)

## NEXT (explicitly out of the MVP)

- **Todoist** — #9 projection, #10 pull sync, #12 projection policy for usage-based schedules, `new-todoist-authentication`, `new-provider-selection-ux`, `new-todoist-link-actions`. Phase 5. Todoist is a projection of noteNFC's canonical state, never the system of record (D3 §8).
- **Attachments** — #7. Phase 4 (ruling R-6: first of the three post-MVP slices).
- **Supplies** — #15. Phase 6.
- **Extension points** — #16 (FUTURE), `new-reminder-fatigue-controls`. Phase 7.

## Out of scope (unchanged)

Cloud service / mandatory login; live IoT telemetry; predictive maintenance / AI diagnosis; multi-user fleet management; purchasing / accounting; large parts inventory.

## Design references

- D2 §1 row 1 (tier: Epic, keep open, no code), D2 §5 ("rewrite as epic").
- `docs/design/README.md` — architecture summary, phase sequence, the twelve consequential decisions.
- D7 — the phase-by-phase sequence with falsifiable exit criteria.
