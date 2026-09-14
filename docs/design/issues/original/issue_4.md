# Issue #4: [MVP] Add canonical recurring maintenance schedules and due/overdue reminders
state=open created=2026-09-13T23:27:02Z updated=2026-09-13T23:54:54Z labels= milestone=none comments=0

## Goal
Implement the canonical provider-neutral maintenance scheduling engine. Local notifications and Todoist (#9) should project this model rather than each implementing their own recurrence semantics.

## Schedule types
- calendar interval: every N days/weeks/months/years
- fixed annual/date-based recurrence
- usage interval: every N hours/miles/cycles (manual meter entry initially)
- whichever comes first: time OR usage
- seasonal activation constraint supplied by #14

## Recurrence basis
Calendar intervals must distinguish at least:

### Fixed/scheduled cadence
Next occurrence is calculated from the scheduled cadence, even if the user completes the task early or late.

Useful for things such as:
- quarterly inspection dates
- annual/pre-season checks
- calendar-anchored tasks

### Completion-relative / rolling cadence
Next occurrence is calculated from the date/time the user actually completed the maintenance.

Useful for things such as:
- test UPS every 90 days after the last actual test
- test hot-tub water every N days while in season
- service an item N months after it was actually serviced

Todoist can map these concepts to provider-specific recurrence behavior such as `every` vs `every!`, but noteNFC owns the canonical semantic distinction.

## Required actions/semantics
The scheduling model must preserve the distinction between:
- complete now / complete early
- snooze reminder only
- postpone current occurrence
- change permanent recurrence

See #11 for enhanced UX/actions. These operations must not collapse into one generic `reschedule` operation.

## Requirements
- Define one or more maintenance schedules per asset.
- Track last completed date/value and calculate next due date/value.
- Status: OK, due soon, due, overdue.
- Support manual completion before the due date.
- Configurable reminder lead time.
- Local notifications; no account/cloud dependency.
- Notification opens directly to the asset/task.
- Marking maintenance complete creates an event in #3 and rolls the schedule forward according to its recurrence basis.
- Snooze/remind-later without falsely marking maintenance complete or moving the canonical due date unless the user explicitly postpones the occurrence.
- Support an optional reminder provider abstraction so #9 can project the same schedule into Todoist.

## Seasonal behavior
Season-specific activation/suppression is specified in #14. The canonical schedule must be able to represent `seasonally inactive` separately from `disabled` and `overdue` and must not accumulate meaningless missed occurrences while intentionally inactive.

## Reminder integrity
#14 adds reminder-health checks to verify that an active schedule with reminders enabled actually has a functioning local/Todoist delivery path.

## Examples
### UPS battery
- Replace battery every 3 years.
- Perform/load-test every! 90 days from actual completion.
- Notify 14 days before each task.

### Lawn mower / power equipment
- Change oil every 50 engine hours OR annually, whichever occurs first.
- Record actual engine hours at service completion.

### Winter-only hot tub
- Test water every! 3 days while the asset is seasonally active.
- Clean/rinse filter every! 30 days while active.
- Drain/refill on configured interval or seasonal startup policy.
- Do not produce summer reminder spam when the hot tub is configured inactive.

## Acceptance criterion
There is exactly one canonical maintenance-schedule model capable of representing fixed, completion-relative, usage-based, whichever-comes-first, and seasonally constrained maintenance. Local Android reminders and Todoist use adapters/projections of that same model.

