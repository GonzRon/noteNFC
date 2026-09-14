---
action: create
title: "[NEXT] Per-schedule reminder provider selection (single-choice UI over a multi-provider model)"
milestone: "Phase 5 — Todoist"
labels: []
---

Split from #9 (D2 §5: "#9 (OAuth/token · projection · deep links → foundational · provider UX)"). Incorporates the review-1 correction that provider selection is a **UI single choice over a multi-provider model** (ruling R-12, ledger A19).

## Goal

Let the user choose, per schedule, where its reminders are delivered — without encoding "exactly one provider" as a schema invariant.

## The model / UI split

The model is a set: `schedule_provider(schedule_id, provider, enabled)`, primary key `(schedule_id, provider)`, where `provider ∈ {LOCAL, TODOIST}` and future providers add enum values rather than columns (D4 §8).

The MVP editor offers a **single-choice control**:

```
Reminder provider
( ) noteNFC
( ) Todoist
( ) None
```

and therefore writes at most one enabled row. That is a **UI constraint only**; nothing in the ports or the tables prevents enabling several later for a critical schedule. This is why the design rejected a single `reminder_provider` enum column — relaxing it later would have needed a migration (ledger A19).

## Scope

- The single-choice control in the schedule editor, writing `schedule_provider` rows.
- Todoist connection and projection status shown on schedules that use Todoist (connected / in sync / awaiting sync / externally modified / awaiting details / conflicted — the states #10 defines).
- Choosing `None` leaves the schedule visible on the dashboard but delivers nothing, and raises `SCHEDULE_NO_PROVIDER` when `reminders_enabled` is on (`new-reminder-health`).
- Selecting Todoist when no account is connected routes to `new-todoist-authentication` rather than failing.
- Changing a schedule's provider triggers `reconcile` for both the old and the new provider, so the old projection is withdrawn and the new one created (`new-reminder-provider-interface`).

## Why this is Phase 5

Until Todoist exists there is only one provider and nothing to choose. Before then, schedules get a `LOCAL` row by default. A multi-provider UI is a Phase 7 item if it is ever wanted; the double-notification question it would raise already has a recorded design answer (D8 risk 17).

## Acceptance criteria

1. The editor writes at most one enabled `schedule_provider` row, and the schema still accepts two.
2. Switching a schedule from Todoist to noteNFC withdraws the Todoist task and arms the local reminder, with no duplicate delivery in between.
3. Choosing `None` on a schedule with reminders enabled produces the `SCHEDULE_NO_PROVIDER` health finding.
4. Choosing Todoist without a connected account opens the connection flow instead of erroring.
5. A schedule using Todoist shows its live projection status.

## Design references

D2 §5 (split of #9; incorporate review-1 corrections) · D3 §7.1 (subject lists per enabled provider), §8 (projection states) · D4 §8 (`schedule_provider`) · D7 Phase 5 · D8 ruling R-12, ledger A19, risk 17 · `11-review-1-changes.md` §3.
