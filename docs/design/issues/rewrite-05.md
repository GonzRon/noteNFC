---
action: rewrite
number: 5
title: "[MVP] Add asset dashboard with maintenance status"
milestone: "Phase 3 — Scheduling + local reminders"
labels: []
---

> Revised 2026-09-14 per the approved design package (`docs/design/`, review 1). Original text is preserved in this issue's edit history and in `docs/design/issues/original/issue_5.md`. Design references: D2 §1 (row 5), D2 §5; D4 §8; D5 §1; D7 Phase 1C and Phase 3.

Kept as written, with one dependency noted and one scheduling note.

## Ships in two steps

A **minimal asset and link list** ships in Phase 1C as part of the Compose shell (D7 Phase 1C), because the app must be usable before schedules exist. The **full dashboard with maintenance status** is Phase 3, once #4's engine can supply status and `effective_due_on`.

## Requirements (unchanged)

- List and search all active assets.
- Group/filter by category and maintenance status.
- Surface `overdue`, `due` and `due soon` prominently.
- Show the next maintenance item and its due date/value on each asset row.
- Tap an asset to open its detail/history page.
- Quick actions for `scan tag`, `add asset`, `log maintenance`.
- Archived assets stay out of the default view but retain their history (D4 §13).

## Meter model dependency

"Due value" for usage-based schedules ("due at 170 h, now 165 h") requires the meter model — `new-meter-model` (the domain half split out of #12, re-tiered to MVP per D2 §5). Without it the dashboard can show time-based due dates only.

## Implementation notes from the design

- Sort key is `schedule_state.effective_due_on`; status is computed at read time from the row plus today's date and is never stored, so the dashboard can never show stale state (D4 §8, D5 §1).
- `INACTIVE_SEASON` is a distinct status from `OVERDUE`, `PAUSED` and `NO_DATA` and must render differently (#14, D5 §1).
- A red badge appears on Home when any reminder-health finding has severity ≥ WARN (`new-reminder-health`, D3 §7.3).

## Keep it intentionally simple

Not a fleet-management analytics dashboard. Its job is to answer: what needs attention, which physical asset is it, when was it last serviced, what should I do next.

## Acceptance criteria

1. Overdue, due and due-soon schedules appear above everything else, ordered by `effective_due_on`.
2. A seasonally inactive schedule is shown as inactive, not overdue.
3. Archived assets are absent from the default list and their history is intact when opened directly.

## Visual design

D12 (Apollo Service Binder) §10 attention hierarchy — the dashboard orders primarily by required attention, not by asset category, with §5's semantic states (OK / DUE SOON / DUE / OVERDUE / OUT OF SEASON / PAUSED / NO BASELINE) carrying wording and icon alongside colour. Acceptance: a **grayscale-obvious hierarchy** — with colour removed, what needs attention is still unmistakable.

## Design references

D2 §1 row 5 · D2 §5 · D4 §8 (`schedule_state`) · D4 §13 (archive) · D5 §1 (status) · D3 §7.3 (health badge) · D7 Phase 1C, Phase 3.
