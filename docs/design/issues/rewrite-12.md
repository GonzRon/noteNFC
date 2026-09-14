---
action: rewrite
number: 12
title: "[NEXT] Todoist projection policy for usage-based maintenance"
milestone: "Phase 5 — Todoist"
labels: []
---

> Revised 2026-09-14 per the approved design package (`docs/design/`, review 1). Original text is preserved in this issue's edit history and in `docs/design/issues/original/issue_12.md`. Design references: D2 §1 (row 12), D2 §3 (C5), D2 §5; D3 §8; D4 §6; D5 §3, §4; D7 Phase 5.

**Split.** The domain half — the meter model — was re-tiered to MVP and moved to `new-meter-model` (Phase 2). What remains here is the **Todoist projection policy** for usage-based schedules: NEXT, Phase 5.

Depends on #9, #10 and `new-meter-model`.

## Goal

Make usage-based and "whichever comes first" maintenance visible in Todoist at the right time, without asking Todoist to model engine hours.

Todoist is excellent at calendar recurrence and owns no domain state: engine hours, mileage, battery cycles, runtime hours, pressure differentials and measured thresholds are computed entirely by noteNFC (D5 §3, §4).

## Moved out of this issue

The canonical usage-aware schedule model — meter definitions, last-completed meter, current meter, next threshold, calendar fallback, due-soon/due/overdue state, manual readings, meter reset — is **`new-meter-model`** (definitions) plus **#4** (the rules that consume them). It is MVP, because #4's MVP usage schedules and #3's oil-change acceptance example both depend on it (D2 §1 row 12). Do not restate the model here.

## Scope: the projection policy

A usage-based or combined schedule is never eligible for `NATIVE_RECURRING` — Todoist cannot express a meter rule — so it is always projected as `MANAGED_OCCURRENCE` (D3 §8, #9).

- **Undated while far away, dated when close.** A usage-only schedule is projected as an undated managed task whose description carries the meter state; it gains a due date as the threshold approaches. A combined time-OR-meter schedule uses `effective_due_on` (the time side) as the due date from the start.
- **Description carries the state:**

```
Change mower oil

Due soon: 148 / 150 engine hours
Calendar limit: 2027-04-01

Open maintenance record:
notenfc://schedule/<schedule-id>
```

- **Debounce to avoid churn.** The description is refreshed only when the status changes (`OK → DUE_SOON`, `DUE_SOON → DUE`), when the displayed reading changes by at least 10 % of the meter interval, or when the user explicitly syncs. `content_hash` on the projection suppresses no-op updates (D3 §8).
- **Completion.** On completion in noteNFC, the meter reading recorded on the completion event advances the meter side and the time side is recomputed; the projection is re-dated or closed accordingly, with the canonical due date verified against Todoist's afterwards (#9, #10).
- Todoist's due date is a notification convenience and never becomes the authoritative representation of a meter-based rule.

Contradiction C5 is resolved here: usage schedules exist from Phase 3 with the local provider only, and are projected into Todoist only once this policy ships.

## Acceptance criteria

1. `every 50 engine hours OR 12 months, whichever comes first` is calculated entirely by noteNFC, appears in Todoist at the appropriate time, and stays correct although Todoist has no concept of engine hours.
2. A usage-only schedule appears as an undated Todoist task carrying its meter state and is dated as the threshold approaches.
3. Saving several small meter readings in a row produces at most one Todoist update.
4. Completing in noteNFC advances both sides and updates or closes the projection once.

## Design references

D2 §1 row 12 · D2 §3 C5 · D2 §5 (split; meter model → MVP) · D3 §8 (representation selection, usage-only schedules, debounce) · D4 §6 (`measurement_definition`, `is_meter`) · D5 §3 (meter rules), §4 (combined rule) · D7 Phase 5.
