---
action: rewrite
number: 4
title: "[MVP] Canonical scheduling engine and schedule state model"
milestone: "Phase 3 — Scheduling + local reminders"
labels: []
---

> Revised 2026-09-14 per the approved design package (`docs/design/`, review 1). Original text is preserved in this issue's edit history and in `docs/design/issues/original/issue_4.md`. Design references: D2 §1 (row 4), D2 §3 (C5, C8), D2 §5; D4 §8; D5 §1–§9, §12; D7 Phase 3.

Narrowed to the **engine and state model**. Delivery was split out; ownership of the four operations and of `completion_mode` was merged in.

## Goal

One provider-neutral maintenance schedule model, with all due state derived from the journal. Local notifications and Todoist project this model; neither implements its own recurrence semantics.

## Scope

### Rule model (D4 §8)

`maintenance_schedule` carries the configuration plus exactly two user overrides:

- Time side: `time_interval` + `time_unit` (`DAY | WEEK | MONTH | YEAR`), `time_basis` (`FIXED | COMPLETION`), `anchor_on`, `lead_days`.
- Meter side: `meter_definition_id` (must be `is_meter = 1`, from `new-meter-model`), `meter_interval`, `anchor_meter`, `meter_lead`.
- Combined "whichever first" = both sides present; due when **either** is due.
- Season: `season_behavior` (`FOLLOW_ASSET | IGNORE`), `season_reentry`, `season_reentry_offset_days` — semantics owned by #14.
- `completion_mode` (see below), `reminders_enabled`, `status` (`ACTIVE | PAUSED | ARCHIVED`).
- Overrides: `postponed_due_on` (one-off date replacement) and `snoozed_until_at` (notification suppression instant).
- `CHECK (time_interval IS NOT NULL OR meter_definition_id IS NOT NULL)`.

### Recurrence basis (D5 §2)

- **FIXED** — all due dates lie on the series `anchor_on + k·interval`, computed from the anchor with a multiplier (never iteratively, so month-end and leap-day clamping cannot drift). On completion `C` of an occurrence due `D`: `nextDue = smallest seriesDate(k) > max(D, C)`. A very late completion **skips forward**; no backlog of missed occurrences is ever created (ledger A10).
- **COMPLETION** — `computed_due_on = C.plus(interval)`; with no completion yet, `computed_due_on = anchor_on`. This is Todoist's `every!`.

Contradiction C8 is resolved here: #11's third flavour ("fixed cadence unless the schedule explicitly says otherwise") is **not adopted**. There are two bases. A user who wants "reset on early completion" chooses COMPLETION.

### Derived state (D4 §8, D5 §5)

`schedule_state` is derived and fully recomputable. `ScheduleRecompute.rebuild(schedule, events(asset), T)` is the **only** write path into it; it runs after every event insert/update/delete, every schedule edit, every import, and in the daily job. Status (`OK | DUE_SOON | DUE | OVERDUE | INACTIVE_SEASON | PAUSED | NO_DATA`) is **not stored** — it is a pure function of the row plus today's date, so it can never be stale (D5 §1). Only `effective_due_on = postponed_due_on ?: computed_due_on` is materialised, for ordering.

### The four operations (merged here from #9 and #11) — D5 §7

This issue is now the **single owner** of these semantics; #9 and #11 link here instead of restating them.

| Operation | Changes | Does not change | Event? |
|---|---|---|---|
| **Complete** (now or early) | inserts the completion event, `rebuild`, clears `postponed_due_on` and `snoozed_until_at` | the rule | yes |
| **Snooze** | `snoozed_until_at` | due date, rule, state | no |
| **Postpone occurrence** | `postponed_due_on` | the rule; `computed_due_on` is kept for audit | no |
| **Edit recurrence** | rule columns and `anchor_on`; clears `postponed_due_on`; `rebuild` | history | no |

These must not collapse into one generic `reschedule`. After a postpone, completion computes the next occurrence from the **rule**, not from the postponed date.

### `completion_mode` (merged here from #10 and #11) — D4 §8

The "simple vs rich completion" attribute from #10 and #11 is a **schedule property**, not a sync concept and not a notification concept:

- `QUICK` — one tap creates a minimal event (plus a single "current hours" prompt when the schedule has a meter rule).
- `FORM` — completion opens the schedule's `profile_id` form; an externally-originated completion creates the event with `details_pending = 1`.

### Meter rules

`computed_due_meter = last_completed_meter + meter_interval`; `current_meter` is the latest reading of the meter definition across the asset's events. The completion form makes the meter field required when a meter rule exists; with no completion the baseline is `anchor_meter`; with neither, status is `NO_DATA` plus a health finding (D5 §3). The meter definition model itself is `new-meter-model`.

### Provider selection (model, not UI)

Which providers deliver a schedule's reminders is the `schedule_provider` join table (`schedule_id`, `provider`, `enabled`) — a set, not an enum column (review-1 correction, ruling R-12, ledger A19). The MVP editor writes at most one enabled row; that is a UI constraint, not a domain invariant. The port is `new-reminder-provider-interface`; the UI is `new-provider-selection-ux`.

## Split out of this issue

- `new-local-reminder-provider` — alarms, backstop, boot receivers, channels, notification delivery.
- `new-reminder-provider-interface` — the `ReminderProvider` port and the `reconcile` contract (moved here from #9).

## Acceptance criteria

1. All D5 §10 worked examples pass as tests (FIXED quarterly with early/late/very-late completion; COMPLETION 90-day UPS load test; mower 50 h OR 12 months; winter hot tub; recurrence edit; pause/archive/season side by side).
2. `rebuild` is pure and idempotent: `rebuild(rebuild(x)) == rebuild(x)`, and the same inputs always give the same output.
3. Deleting a completion event moves the due date back, observed in the UI.
4. Snooze changes no date; postpone changes only `effective_due_on`; completion clears both overrides; a recurrence edit clears the postpone.
5. A very late completion of a FIXED schedule produces exactly one next occurrence, not a backlog.
6. There is exactly one canonical schedule model capable of representing fixed, completion-relative, usage-based, whichever-comes-first and seasonally constrained maintenance.

## Design references

D2 §1 row 4 · D2 §3 C5, C8 · D2 §5 (split, merge) · D4 §8 (`maintenance_schedule`, `schedule_provider`, `schedule_state`) · D5 §1 (status), §2 (time rules), §3 (meter rules), §4 (combined), §5 (rebuild), §7 (the four operations), §8 (transitions), §9 (edits and deletions), §12 (invariants) · D7 Phase 3 · D8 rulings R-12 · D3 §16 ledger A9, A10, A19.
