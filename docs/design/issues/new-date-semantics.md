---
action: create
title: "[MVP] Calendar-date semantics for due and occurred dates"
milestone: "Phase 2 — Journal + profiles"
labels: []
---

Identified in D2 §4 item 9 (Platform: date semantics — due dates and event dates are calendar dates, not instants; reminder time-of-day preference; zone changes). Not present in issues #1–#16.

## Goal

Fix, once and in one place, that everything the user calls a date is a **calendar date**, and that instants appear only where the platform genuinely needs one.

This is ledger decision A8 and one of the twelve consequential decisions in the design README.

## The rule

- `asset_event.occurred_on`, `maintenance_schedule.anchor_on`, `postponed_due_on`, `schedule_state.computed_due_on` / `effective_due_on`, `asset.purchase_on` / `in_service_on` / `retired_on` / `warranty_expires_on`, and the season window `MM-DD` values are **calendar dates**. The scheduling engine never sees an instant (D5 §2.3).
- Instants (`created_at`, `updated_at`, `recorded_at`, `snoozed_until_at`, `last_scanned_at`) are audit or platform values, not domain dates.
- `asset_event.occurred_time` is an optional local `HH:MM`; `tz_id` records the zone at entry so an instant can be reconstructed when something genuinely needs one.
- Backdating is allowed and normal; date-only precision is the default (D2 §4 item 10).

## Calendar arithmetic (D5 §2.3, tested)

| Case | Rule |
|---|---|
| Month/year overflow | `plusMonths` / `plusYears` clamp to the last valid day (Jan 31 + 1 month → Feb 28 or 29) |
| Leap-day anchors | Feb 29 + 1 year → Feb 28 in a common year, Feb 29 in the next leap year — computed from the anchor, so it returns to Feb 29 |
| FIXED series | `anchor + k·interval` with a multiplier, never iterative, so clamping cannot drift |
| Year wrap | plain date arithmetic, no special case |
| Time zones / DST | irrelevant to dates |
| "Today" | `T` is the device-local date at evaluation; a schedule due `T` is DUE all day |

## Where zones actually enter (D5 §11)

Exactly one place: turning a due **date** into a reminder **instant**. The daily digest fires at the user's configured hour in the device's current zone. Consequences that must be handled:

- A **reminder time-of-day preference** (one app-level setting, used by the local provider).
- **Zone changes and clock changes** re-arm the alarm — `TIME_SET`, `TIMEZONE_CHANGED`, `DATE_CHANGED`, `BOOT_COMPLETED` (owned by `new-platform-permissions-scheduling` and `new-local-reminder-provider`).
- Todoist due dates are dates too; a date that differs because of a zone difference is a drift to correct, not a semantic difference (#9, #10).

## Display

Dates render in the device locale; the technical readout style is D12's concern, not this issue's. Unit and number formatting preferences (°F/°C, qt/L) are a separate, later concern (D2 §4 item 15) and are not in scope here; i18n is deferred.

## Acceptance criteria

1. No domain date is stored as an instant, and the scheduling engine's API accepts and returns dates only — asserted by the `:core` type signatures and tests.
2. `Jan 31 + 3 × 1 month` computed from the anchor equals the same result as `anchor.plusMonths(3)`, and does not drift to Apr 28.
3. A Feb 29 anchor yields Feb 28 in the next common year and Feb 29 in the next leap year.
4. Changing the device time zone does not change any due date; it re-arms the reminder instant.
5. A schedule due today is DUE for the whole local day, regardless of the hour of evaluation.
6. An event can be backdated with a date and no time, and every downstream computation still works.

## Design references

D2 §4 items 9 and 10 · D4 §5 (`occurred_on`, `occurred_time`, `tz_id`), §8 (date columns) · D5 §2.3 (calendar arithmetic), §5 (rebuild), §11 (from due dates to reminder instants) · D3 §16 ledger A8 · `docs/design/README.md` decision 3 · testing doc §2 ("Calendar arithmetic").
