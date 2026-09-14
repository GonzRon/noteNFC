---
action: create
title: "[MVP] Local reminder provider: daily digest alarm, WorkManager backstop, boot receivers, channels"
milestone: "Phase 3 — Scheduling + local reminders"
labels: []
---

Split from #4 (D2 §5: "#4 (engine · local provider · provider interface)").

## Goal

Deliver noteNFC's own reminders reliably on a modern Android device, with no account, no cloud and no exact alarms.

Implements `ReminderProvider` from `new-reminder-provider-interface`; the schedule semantics it delivers belong to #4.

## Scope (D3 §7.2)

| Concern | Decision |
|---|---|
| Timing | One **inexact daily digest alarm** at the user's configured hour, via `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP)` (or `setWindow` with a 30-minute window), re-armed by its own receiver |
| Exact alarms | **Not used.** `SCHEDULE_EXACT_ALARM` is denied by default on API 34+ and `USE_EXACT_ALARM` is Play-restricted to alarm/calendar apps; "due today" needs no second precision |
| Backstop | A `PeriodicWorkRequest` every 12 h (flex 4 h) that recomputes all `schedule_state`, posts anything missed, and **re-arms the alarm if it is absent**. WorkManager survives reboot and force-stop; alarms do not |
| Reboot and clock | `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `DATE_CHANGED` receivers re-arm the alarm |
| Channels | `maintenance_due`, `maintenance_overdue`, `supplies`, `sync_problems` |
| Digest policy (MVP) | One summary notification per digest run listing DUE and OVERDUE (plus DUE_SOON on first entry); per-item notifications only for DUE/OVERDUE that are not snoozed; overdue re-notification every 3 days by default |
| Usage-only schedules | Evaluated when a meter reading is saved — an immediate notification if it crosses DUE — not by the clock, because there is no date to alarm on |
| Snooze | `snoozed_until_at` suppresses this provider's notifications only; it never touches a due date (#4, D5 §7) |
| Parking | A `PARKED` subject posts nothing and clears any standing notification (seasonal, #14; paused, #4) |

`reconcile` is the whole write surface: given the desired subject list, the provider re-arms the alarm and posts or clears notifications, so running it twice changes nothing the second time.

## Dependencies

- **Permissions and OS constraints** — `POST_NOTIFICATIONS`, the no-exact-alarms decision, the receiver registrations and battery-optimisation guidance are owned by `new-platform-permissions-scheduling`.
- **Notification quick actions** (Done / Snooze / Open, and the nonce that protects them) are **#11**.
- **Health findings** it raises (`DIGEST_ALARM_MISSING`, `BACKSTOP_WORK_MISSING`, `NOTIFICATIONS_BLOCKED`, `APP_RESTRICTED`) are rendered and repaired by `new-reminder-health`.
- The reminder hour preference and zone-change behaviour come from `new-date-semantics` (D5 §11).

## Out of scope

Reminder-fatigue controls (repeat interval, maximum frequency, quiet period, per-schedule action choice) are `new-reminder-fatigue-controls`, NEXT. The MVP policy above is deliberately fixed and conservative.

## Visual design

D12 §5 semantic states (DUE / OVERDUE wording, icon and container per state) and §10 attention hierarchy — notification and surface treatment uses the semantic tokens, never improvised colours, and must remain readable in grayscale.

## Acceptance criteria (D7 Phase 3)

1. On the device, a schedule due tomorrow produces **exactly one** notification at the configured hour without the app being opened, and again after a reboot.
2. The alarm is re-armed after `BOOT_COMPLETED`, `TIME_SET` and `TIMEZONE_CHANGED` (`ShadowAlarmManager` tests).
3. The backstop worker re-arms a deliberately cancelled alarm within its period and posts anything missed.
4. A snoozed schedule produces no notification while snoozed and its due date is unchanged.
5. A seasonally inactive schedule produces no notification and any standing one is cleared.
6. Saving a meter reading that crosses a usage threshold notifies immediately, without waiting for the next digest.
7. Running `reconcile` twice posts nothing a second time.

## Design references

D2 §5 (split of #4) · D3 §7.1 (port), §7.2 (local provider), §7.3 (health findings) · D4 §8 (`schedule_provider`, `snoozed_until_at`, `schedule_state`) · D5 §6 (parking), §7 (snooze), §11 (due dates to reminder instants) · D7 Phase 3 · D8 spike S4, risk 3, ledger A12 · testing doc §4 ("Reminders") · D12 §5, §10.
