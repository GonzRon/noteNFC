---
action: create
title: "[MVP] Platform permissions and scheduling constraints (POST_NOTIFICATIONS, no exact alarms, BOOT/TIME/TIMEZONE)"
milestone: "Phase 3 — Scheduling + local reminders"
labels: []
---

Identified in D2 §4 item 8 (Platform: runtime permissions and OS constraints owned by one issue). Not present in issues #1–#16, which assume local notifications simply work.

## Goal

One issue that owns every Android platform constraint the reminder path depends on, so no feature lane re-derives them and none of them is discovered late.

## Scope

### `POST_NOTIFICATIONS` (API 33+)

Requested **on first schedule creation, with a rationale** — not at app launch, where the user has no context for it. Denial is not fatal: the dashboard keeps working and `new-reminder-health` reports `NOTIFICATIONS_BLOCKED` with a repair that opens system settings.

### No exact alarms (ledger A12)

`SCHEDULE_EXACT_ALARM` is denied by default on API 34+ and `USE_EXACT_ALARM` is Play-restricted to alarm and calendar apps. noteNFC therefore **never requests either**. "Due today" is a date, not an instant, so an inexact daily alarm is sufficient (`new-local-reminder-provider`).

### Notification channels (API 26+)

`maintenance_due`, `maintenance_overdue`, `supplies`, `sync_problems`, created once with sensible importances. Channel importance set to NONE by the user is detectable and surfaces as a health finding.

### Receiver registrations

`BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` and `DATE_CHANGED` receivers re-arm the digest alarm, because alarms are cleared on shutdown. These receivers are **not exported** and are addressed with explicit intents and `FLAG_IMMUTABLE` PendingIntents.

### Background execution reality

- WorkManager survives reboot and force-stop; alarms do not — hence the 12 h backstop.
- App standby bucket `RESTRICTED` and battery-optimisation "restricted" are detectable (`APP_RESTRICTED`) and explained with a link to settings; noteNFC does not nag for an exemption it does not need.
- **Android 17 no longer dispatches NFC to apps in the stopped state** (after force-stop); the health screen explains this rather than pretending it is a bug.

### Activity-trampoline rule (API 31+)

A notification action may not start an activity from a service or a broadcast receiver. `Done` on a `FORM` schedule therefore uses `PendingIntent.getActivity` directly; `Done` on a `QUICK` schedule uses a broadcast that does its work without launching anything (#11).

### minSdk / targetSdk

minSdk 26 (ruling R-11), targetSdk 36 for Phases 0–6. targetSdk 37 plus `android:permission="android.permission.DISPATCH_NFC_MESSAGE"` on the dispatch activity is **Phase 7** work, listed here so it is not forgotten.

### Spike

S4 answers whether `setAndAllowWhileIdle` at the user's hour fires within an acceptable window on this OEM under Doze and battery saver, and whether the backstop worker runs within 12 h. It runs before Phase 3.

## Acceptance criteria

1. `POST_NOTIFICATIONS` is requested at first schedule creation with a rationale, and denial leaves the app usable with a health finding.
2. The app declares neither `SCHEDULE_EXACT_ALARM` nor `USE_EXACT_ALARM` — asserted against the merged manifest.
3. All four channels exist with their intended importance, and a channel muted by the user is detected.
4. The digest alarm is re-armed after each of `BOOT_COMPLETED`, `TIME_SET` and `TIMEZONE_CHANGED`.
5. No boot, time or quick-action receiver is exported, and every PendingIntent is `FLAG_IMMUTABLE`.
6. A notification action never starts an activity from a receiver on API 31+.
7. S4's result is recorded before Phase 3 implementation begins.

## Design references

D2 §4 item 8 · D3 §7.2 (local provider constraints table), §7.3 (health findings), §9 (NFC dispatch and Android 17) · D7 Phase 3 · D8 spike S4, risk 3, rulings R-11, ledger A12 · security doc, "Exported components" and "Notification actions" · testing doc §4.
