---
action: rewrite
number: 11
title: "[MVP] Local notification quick actions for maintenance schedules"
milestone: "Phase 3 — Scheduling + local reminders"
labels: []
---

> Revised 2026-09-14 per the approved design package (`docs/design/`, review 1). Original text is preserved in this issue's edit history and in `docs/design/issues/original/issue_11.md`. Design references: D2 §1 (row 11), D2 §3 (C8), D2 §5; D3 §7.2; D5 §7; security doc ("Notification actions").

**Narrowed to local notification quick actions and re-tiered to MVP (Phase 3)**; retitled `[MVP]`. The duplicated domain semantics were removed; two sections were split out.

## Goal

Make the common maintenance actions one tap from a local notification, and send the richer work straight into the right form — without the notification layer owning any scheduling semantics.

## Scope: three actions

For the local reminder provider (`new-local-reminder-provider`):

```
UPS load test due
[DONE]  [SNOOZE 1D]  [OPEN]
```

- **Done** — for `completion_mode = QUICK`, a broadcast to `QuickActionReceiver` that inserts the completion event and calls `rebuild`. For `completion_mode = FORM`, a `PendingIntent.getActivity` straight into the completion form. (Android 12 forbids service/receiver → activity trampolines; activity PendingIntents are fine.)
- **Snooze 1d** — sets `snoozed_until_at`. Suppresses notifications only; the dashboard still shows OVERDUE with a "snoozed until" badge.
- **Open** — opens the schedule via `notenfc://schedule/<id>` (`new-deeplink-contract`).

Actions carry a random per-notification nonce, stored in-process/DataStore and checked by the receiver, so a forged broadcast cannot complete a schedule. The receiver is not exported (security doc).

Which actions appear follows the schedule's `completion_mode` and whether a meter rule exists (a meter schedule's Done prompts for the current reading). Per-schedule action customisation beyond that is not in this phase.

## Semantics are owned by #4 — do not restate them here

The original issue restated complete-early / snooze / postpone / change-recurrence. Those four operations are now defined once, in **#4** (D5 §7), and this issue links to them. In particular:

- Snooze never changes a due date and never creates an event.
- Postpone moves the current occurrence only; after a postpone, completion still computes the next occurrence from the rule.
- Completing clears both overrides.

The original issue's third flavour — "for fixed cadence, preserve the original cadence **unless the schedule explicitly says otherwise**" — is **not adopted** (contradiction C8). There are two bases, FIXED and COMPLETION; a user who wants "reset on early completion" chooses COMPLETION.

Simple vs rich completion is likewise not defined here: it is #4's `completion_mode`.

## Split out of this issue

- **Reminder-fatigue controls** (per-schedule repeat interval, maximum frequency, quiet period after a postpone, whether overdue reminders repeat at all, preferred quick actions) → `new-reminder-fatigue-controls`, NEXT, Phase 7. The MVP digest policy in the meantime is conservative and fixed: one summary notification per digest run listing DUE/OVERDUE, per-item notifications only for DUE/OVERDUE that are not snoozed, and overdue re-notification every 3 days (D3 §7.2).
- **Todoist-side link actions** (`Open asset`, `Open checklist`, `Complete maintenance` from a Todoist task) → `new-todoist-link-actions`, with #9 in Phase 5. noteNFC cannot inject custom buttons into Todoist's own notifications; a deep link is the mechanism.

## Acceptance criteria

1. Tapping **Done** on a `QUICK` schedule's notification creates exactly one completion event and the notification clears.
2. Tapping **Done** on a `FORM` schedule opens the completion form and creates nothing until the form is saved.
3. Tapping **Snooze 1d** changes no due date and creates no event; the dashboard still shows the schedule as overdue, badged as snoozed.
4. A broadcast with a stale or missing nonce is rejected and creates nothing.
5. Overdue maintenance stays visible on the dashboard even when repeat notifications are suppressed.

## Visual design

D12 (Apollo Service Binder) §5 semantic states (**DUE** `event`, **OVERDUE** `warning` — wording plus icon plus container, never colour alone) and §10 attention hierarchy for the digest ordering. Acceptance: notification content and its in-app counterpart use the semantic tokens from `new-design-system-foundation`, and the due/overdue distinction survives grayscale.

## Design references

D2 §1 row 11 · D2 §3 C8 · D2 §5 (split; delete duplicated semantics) · D3 §7.2 (local provider, quick actions, digest policy) · D5 §7 (the four operations, owned by #4) · D3 §13 (deep links) · security doc, "Notification actions" · D7 Phase 3.
