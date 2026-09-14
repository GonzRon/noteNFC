---
action: create
title: "[NEXT] Reminder-fatigue controls per schedule"
milestone: "Phase 7 — Extension points"
labels: []
---

Split from #11 (D2 §5: "#11 (local quick actions · fatigue controls · Todoist link actions)"). Tier NEXT; listed in D7 Phase 7 as an explicit item, not an open-ended polish bucket.

## Goal

Let a user tune how insistent a given schedule's reminders are, once there is enough real usage to know which defaults are wrong.

## Why this is not MVP

The MVP local provider ships a deliberately fixed, conservative policy (D3 §7.2): one summary notification per digest run listing DUE and OVERDUE, per-item notifications only for DUE/OVERDUE that are not snoozed, and overdue re-notification every 3 days. Shipping per-schedule knobs before that policy has been lived with would be tuning a system nobody has used.

## Scope

Per-schedule configuration:

- reminder enabled / disabled (this one already exists as `maintenance_schedule.reminders_enabled`, #4)
- lead time (already `lead_days` / `meter_lead`, #4)
- repeat-reminder interval while overdue
- maximum reminder frequency
- a quiet / suppressed period after the user postpones
- whether overdue reminders repeat at all
- preferred quick actions for this schedule's notifications (#11 ships a fixed Done / Snooze / Open set)

## The invariant this must not break

**Overdue maintenance stays visible on the dashboard even when repeated push notifications are suppressed.** Suppressing a notification is never the same as suppressing the state — that distinction is exactly why snooze, postpone and disable are three different things in #4 (D5 §7).

Fatigue controls affect delivery only. They must not touch a due date, must not create or skip an event, and must not alter `schedule_state`.

## Out of scope

Todoist's own notification behaviour: Todoist owns its delivery and its snooze UX, and noteNFC does not attempt to control it (#9).

## Acceptance criteria

1. Setting "do not repeat overdue reminders" stops repeat notifications while the schedule still shows as OVERDUE on the dashboard.
2. A quiet period after a postpone suppresses notifications for its duration and expires on its own.
3. A maximum frequency is honoured across both the digest and the per-item path — no schedule notifies more often than configured.
4. No fatigue setting changes any due date, any event, or any `schedule_state` column.
5. Defaults for an existing schedule are unchanged by the introduction of these controls.

## Design references

D2 §1 row 11 · D2 §5 (split of #11) · D3 §7.2 (MVP digest policy) · D4 §8 (`reminders_enabled`, `lead_days`, `meter_lead`) · D5 §7 (snooze vs postpone vs disable) · D7 Phase 7.
