---
action: rewrite
number: 9
title: "[NEXT] Add optional Todoist integration as the external reminder/task provider"
milestone: "Phase 5 — Todoist"
labels: []
---

> Revised 2026-09-14 per the approved design package (`docs/design/`, review 1). Original text is preserved in this issue's edit history and in `docs/design/issues/original/issue_9.md`. Design references: D2 §1 (row 9), D2 §3 (C3, C5), D2 §5; D3 §8; D4 §10; D7 Phase 5; D8 rulings R-3, R-4, R-12, ledger A13, A20.

**Re-tiered from MVP to NEXT (Phase 5)** and retitled `[NEXT]`, and narrowed to the **projection** itself. Four sections were moved out (below).

## Goal

Use Todoist as an optional reminder/task-delivery surface for noteNFC maintenance schedules, without making Todoist the maintenance system of record.

noteNFC remains authoritative for assets, NFC identity, schedules, history, completion dates and meter state. Todoist owns task presentation, reminders, Today/Upcoming, its own snooze UX and its own multi-device delivery.

## The invariant

**noteNFC is canonical.** A native Todoist recurrence is an optimised provider *representation*, never schedule authority. After every completion, from either side, the adapter verifies Todoist's resulting due date against the canonical computation and corrects Todoist if they differ (D3 §8, ledger A20).

## Capability-based representation (review-1 correction)

The representation is chosen per schedule from the rule's capabilities and re-evaluated on every rule edit (D3 §8, ledger A13):

- **`NATIVE_RECURRING`** when **all** hold: a time rule exists, no meter rule, `time_basis = COMPLETION`, `time_unit ∈ {DAY, WEEK, MONTH, YEAR}`, and no seasonal window applies (asset year-round or `season_behavior = IGNORE`). Projected as one task with `due: {date: <effective_due_on>, string: "every! <N> <unit>"}` — Todoist's completion-relative grammar matches the COMPLETION basis exactly. Completing in Todoist advances the task immediately, even if noteNFC's background sync is delayed.
- **`MANAGED_OCCURRENCE`** for everything else: usage-based, time-OR-meter, seasonal, paused-with-placeholder, and — until spike S8 says otherwise — FIXED-basis rules, because Todoist recomputes a rescheduled recurring task from its completion date and would silently turn a fixed series into a rolling one. Projected as an ordinary dated task that noteNFC re-dates after each completion.

A change of representation is an `UPSERT` that rewrites the task's due object.

### Canonical due verification (mandatory in both representations)

- Completion in noteNFC on a native task → `item_close` (Todoist advances by its own rule) → compare Todoist's new `due.date` with `computed_due_on` → `SYNC_DUE` (`item_update` with the canonical date, recurrence string unchanged) when they differ.
- Completion in Todoist on a native task → detected from the activity log (`item:completed`), not from `checked` — see #10 — → local completion event → `rebuild` → the same comparison.
- Persistent divergence raises the `PROJECTION_DUE_DRIFT` health finding (`new-reminder-health`).

## Projection scope

- One task per schedule; `UNIQUE(provider, schedule_id)` prevents duplicates.
- `reminder_projection` stores the remote task id, the representation, the content hash and the last consumed remote completion reference (D4 §10); `provider_op` is the outbox, with a command `uuid` per op for idempotency.
- Task content: title "Load test — CyberPower Rack UPS"; description carrying last performed, meter state, a `notenfc://schedule/<id>` link and a one-line "Open noteNFC → Reminders" fallback (spike S3).
- Parking: season-inactive → managed task re-dated to the re-entry date (`PARKED`); paused → due cleared, recurrence string retained; archived → task deleted (`WITHDRAWN`).
- Disconnect: the user chooses "delete projected tasks" or "leave them"; projections become `WITHDRAWN` either way.
- Transport: `POST /api/v1/sync` for writes and reads, `GET /api/v1/activities` for native completions, REST `GET /tasks/{id}` for spot checks. Rate limits and batching per D3 §8.

## Moved out of this issue

- **OAuth / token storage** → `new-todoist-authentication` (personal API token first, ruling R-3; OAuth deferred pending a domain, ruling R-4).
- **The `notenfc://` deep-link contract** → `new-deeplink-contract`. It is foundational: local notifications consume it in Phase 3, long before Todoist exists.
- **The per-schedule provider radio** → `new-provider-selection-ux`. The model allows several providers per schedule (`schedule_provider` rows); single-choice is a UI constraint only (ruling R-12, ledger A19).
- **The `ReminderProvider` interface** → `new-reminder-provider-interface`, owned by #4's phase, because the *local* provider needs it first.
- **The four-operation semantics** (complete early / snooze / postpone occurrence / edit recurrence) → owned by **#4**. Do not restate them here; link to #4.
- **`Open in Todoist` / Todoist-side link actions** → `new-todoist-link-actions`.

## Still out of scope for this issue

Two-way sync (#10), usage-based projection policy (#12), webhooks and any server component, multi-account or shared-project support, custom buttons inside Todoist notifications.

Contradiction C3 is resolved by shipping this issue **together with** #10 in Phase 5 rather than shipping a push-only projection first; contradiction C5 by projecting usage schedules only as `MANAGED_OCCURRENCE` once #12's policy ships.

## Acceptance criteria (D7 Phase 5)

1. With a real account, a `NATIVE_RECURRING` schedule (UPS load test, `every! 90 days`) reproduces the original issue's acceptance sentence end to end: completing in Todoist shows the next occurrence immediately and noteNFC records exactly one event after two syncs.
2. A `MANAGED_OCCURRENCE` schedule (mower oil, time-or-meter) is visible in Todoist with its meter state and is re-dated after completion in noteNFC.
3. Manually rescheduling the native task in Todoist and then completing it produces a Todoist date that noteNFC corrects to the canonical value on the next sync.
4. Deleting the task in Todoist yields a `MISSING` finding and Repair recreates it exactly once.
5. An airplane-mode completion in noteNFC lands in Todoist after reconnecting, without duplicates.

## Design references

D2 §1 row 9 · D2 §3 C3, C5 · D2 §5 (split, re-tier, incorporate review-1 corrections) · D3 §8 (Todoist adapter boundary) · D4 §10 (`reminder_projection`, `provider_op`, `integration_account`) · D5 §7 (the four operations, owned by #4) · D7 Phase 5 · D8 rulings R-3, R-4, R-12, spikes S3, S6, S8, ledger A13, A20 · `11-review-1-changes.md` §2.
