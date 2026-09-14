# Issue #11: [NEXT] Add maintenance-aware actions and pre-emptive completion flows
state=open created=2026-09-13T23:39:03Z updated=2026-09-13T23:39:03Z labels= milestone=none comments=0

## Goal
Make maintenance actions fast and semantically correct whether they originate from noteNFC itself or from a Todoist-linked workflow.

Depends on: #9
Related: #10

## Product requirement
Maintenance reminders should not become nagging notifications that force the user through a full asset screen for every action. Common operations should be one tap where safe, while richer work should deep-link directly into the relevant maintenance form.

## Required domain actions
Treat these as distinct operations:

### Complete now / complete early
- Allow a maintenance occurrence to be completed before its due date.
- Record the actual completion timestamp.
- For completion-relative recurrence, calculate the next occurrence from the actual completion date.
- For fixed cadence, preserve the original cadence unless the schedule explicitly says otherwise.

### Snooze reminder
- Delay notification only.
- Do not change the canonical due date.
- Do not create a maintenance event.

### Postpone occurrence
- Move the current planned occurrence.
- Do not silently alter the permanent recurrence rule.

### Change recurrence
- Explicitly edit the permanent maintenance rule.
- Make clear that this affects future occurrences.

## Local notification actions
For the local noteNFC reminder provider, support configurable action buttons where appropriate, e.g.:

```text
UPS load test due

[PASS] [FAIL] [SNOOZE] [OPEN]
```

or:

```text
Generator exercise due

[COMPLETE] [7 DAYS] [OPEN CHECKLIST]
```

Actions must be maintenance-type-aware and configurable so reminders do not become noisy or overloaded.

## Todoist-linked actions
Todoist controls its own native notification UI, so noteNFC cannot assume it can inject arbitrary domain-specific buttons into Todoist notifications.

Instead:
- include a deep link from the Todoist task into the exact maintenance operation in noteNFC
- allow `Open asset`, `Open checklist`, or `Complete maintenance` links/actions from the task context where practical
- when noteNFC completes the maintenance, synchronize the Todoist representation through #9/#10

## Simple vs rich completion
Support schedule-level completion behavior:

### Simple
One tap is enough to create the maintenance event.
Examples:
- visual inspection
- clean filter
- test smoke detector
- exercise generator

### Rich
Completion opens a short form/checklist first.
Examples:
- oil change: hours, oil/filter, notes
- UPS battery replacement: battery SKU/quantity, test result, notes
- RO membrane replacement: filter type, pressure/TDS readings

## Reminder-fatigue controls
Per maintenance schedule allow configuration such as:
- reminder enabled/disabled
- lead time
- repeat reminder interval while overdue
- maximum reminder frequency
- quiet/suppressed period after user postpones
- whether overdue reminders repeat at all
- preferred quick actions

The default behavior should be conservative: overdue maintenance should remain visible in the dashboard even when repeated push notifications are suppressed.

## Acceptance criterion
A user can complete maintenance early, snooze a reminder without moving the due date, postpone one occurrence without changing the permanent recurrence, and perform common maintenance directly from a local notification or Todoist deep link without reminder spam or schedule drift.

