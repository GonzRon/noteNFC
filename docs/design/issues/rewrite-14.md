---
action: rewrite
number: 14
title: "[MVP] Add seasonal activation windows for assets and schedules"
milestone: "Phase 3 — Scheduling + local reminders"
labels: []
---

> Revised 2026-09-14 per the approved design package (`docs/design/`, review 1). Original text is preserved in this issue's edit history and in `docs/design/issues/original/issue_14.md`. Design references: D2 §1 (row 14), D2 §3 (C6), D2 §5; D4 §4, §8; D5 §1, §6; D7 Phase 3; D8 ruling R-8.

**Split.** This issue is now **seasonal windows only**. Reminder health moved to `new-reminder-health`. The two were unrelated features sharing one issue.

## Goal

Support assets that are only active for part of the year, without accumulating meaningless overdue occurrences while they are dormant.

Primary examples: a winter-only hot tub, spring-through-fall lawn equipment, a winter-only snowblower, a pool that is seasonal in some climates and year-round in others.

## Where the window lives (contradiction C6, decided)

The original issue allowed the window on "each maintenance schedule, or optionally an asset-level default", with the inheritance left unspecified. **Decided (ledger A11, D5 §6):**

- The window lives on the **asset**: `season_start_mmdd`, `season_end_mmdd` (both null = year-round; `end < start` wraps the year, e.g. Oct 15 → Apr 15).
- Each schedule chooses `season_behavior = FOLLOW_ASSET | IGNORE`.

No per-schedule windows and no inheritance rules. Windows are explicit configurable month/day values; no astronomical or climate seasons are hard-coded.

```
inSeason(asset, T): both null → true; start <= end → start <= T.mmdd <= end; else T.mmdd >= start || T.mmdd <= end
```

## Inactive-season semantics (D5 §1, §6)

While `FOLLOW_ASSET` and out of season:

- Status is **`INACTIVE_SEASON`** — a distinct value from `OVERDUE`, `PAUSED`, `ARCHIVED` and `DISABLED`.
- No local notifications; the Todoist projection is `PARKED` (task re-dated to the computable re-entry date, never deleted), and a seasonal schedule is never projected as `NATIVE_RECURRING` because Todoist cannot express the window (#9).
- `computed_due_on` is still computed (it may legitimately fall inside the off-season) but is not surfaced as due.
- **No missed occurrences accumulate**, because the model has no occurrence backlog at all — only one current occurrence (ledger A9, A10).
- History and recurrence configuration are preserved untouched.

## Re-entry policies (MVP set, ruling R-8)

Evaluated on the first evaluation with `inSeason == true` after a period out of season, or when the daily job notices the boundary:

| `season_reentry` | Rule | Use |
|---|---|---|
| `AT_START` (+ `season_reentry_offset_days`) | `computed_due_on = seasonStart(thisCycle) + offset` when the computed date is outside the season or earlier than that; otherwise keep the computed date | "Test water when the tub is opened", "3 days after start" |
| `RESUME_CLAMPED` | `computed_due_on = max(computedFromRule, seasonStart(thisCycle))` | Long intervals that may legitimately land inside the coming season |

`MANUAL_STARTUP` (cadence begins only when a `SEASON_START` event is logged) is **deferred**, not MVP. It is emulable today: the startup profile offers "reset these schedules' anchors", which sets `anchor_on` when the startup event is logged.

## Seasonal startup and shutdown tasks

"Open the hot tub for winter", "fall mower storage prep", "snowblower pre-winter inspection", "generator storm-season readiness" are ordinary **FIXED yearly** schedules with `season_behavior = IGNORE`. They must ignore the window, because their dates fall outside it by design. The schedule editor warns when a `FOLLOW_ASSET` schedule's anchor lies outside the asset's window.

## Split out of this issue

**Reminder health and integrity checks** → `new-reminder-health` (local findings and repair in Phase 3; the Todoist findings arrive with Phase 5). The per-schedule "reminders enabled" flag it depends on is `maintenance_schedule.reminders_enabled`, defined in **#4** (D4 §8), not here.

## Acceptance criteria

1. A winter-only hot-tub user receives water-care reminders during their configured winter season and none of them during summer.
2. A year-round pool user can leave the same class of schedules active all year.
3. A mower can carry spring startup and fall storage reminders plus operating-season maintenance.
4. A winter-only hot tub shows `INACTIVE_SEASON` in July and `DUE` on Oct 15 with an injected `Today` (D7 Phase 3 exit criterion 4).
5. Coming back into season produces exactly one current occurrence, never a backlog.
6. Wrap-around windows (Oct 15 → Apr 15) and boundary days behave correctly in tests.

## Visual design

D12 (Apollo Service Binder) §5 semantic states — **OUT OF SEASON** (`calendar_month`, de-emphasised placement) and **PAUSED** (`pause_circle`, with its reason), distinct from **OVERDUE**, and alongside **REMINDER FAILED** / **SYNC ISSUE** on the health surface. Acceptance: a seasonally inactive schedule is visually distinguishable from an overdue and from a paused one in grayscale.

## Design references

D2 §1 row 14 · D2 §3 C6 · D2 §5 (split) · D4 §4 (`asset` season window), §8 (`season_behavior`, `season_reentry`) · D5 §1 (status), §6 (seasonal activation), §10.4, §10.6 (worked examples) · D7 Phase 3 · D8 ruling R-8, ledger A11 · testing doc §2 ("Seasons").
