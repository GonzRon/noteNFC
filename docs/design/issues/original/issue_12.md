# Issue #12: [NEXT] Project usage-based maintenance into Todoist without giving Todoist schedule authority
state=open created=2026-09-13T23:39:18Z updated=2026-09-13T23:39:18Z labels= milestone=none comments=0

## Goal
Support maintenance that becomes due from usage, meter readings, or `whichever comes first` rules while still using Todoist as the human reminder/task surface.

Depends on: #9
Related: #10, #11

## Problem
Todoist is excellent at calendar recurrence but does not own domain state such as:
- engine hours
- mileage
- battery cycles
- runtime hours
- filter pressure differential
- measured thresholds

noteNFC must calculate this maintenance state itself.

Examples:

```text
Change mower oil every 50 engine hours OR annually,
whichever comes first.
```

```text
Service generator every 100 runtime hours.
```

Todoist should notify the user that work is due; it should not be asked to model the underlying usage rule.

## Requirements

### Canonical noteNFC schedule
Support usage-aware maintenance definitions such as:
- every N hours
- every N miles
- every N cycles
- threshold-based conditions where appropriate
- time OR usage, whichever comes first

Track:
- last completed meter value
- current meter value
- next meter threshold
- calendar fallback/limit where applicable
- due-soon/due/overdue state

### Todoist projection
When noteNFC determines that a usage-based schedule is approaching or has crossed its threshold:
- create or update a linked Todoist task
- make the task description explain the current state
- include a deep link into noteNFC

Example:

```text
Change mower oil

Due soon: 148 / 150 engine hours
Calendar limit: 2027-04-01

Open maintenance record:
notenfc://asset/<asset-id>/maintenance/<schedule-id>
```

Todoist's due date may be used as a notification convenience, but must never become the authoritative representation of the meter-based rule.

### Projection policy
Allow useful policies such as:
- create Todoist task only when `due soon`
- create task immediately but keep it undated until threshold approaches
- update task description as meter readings change
- close/advance projection when maintenance is completed in noteNFC

### Meter updates
Initially support manual meter readings.

Future integrations/telemetry are out of scope, but the domain model should not assume readings can only come from manual entry.

### Completion
On maintenance completion:
- record the actual meter value when available
- reset/advance the usage threshold from that value
- recompute the calendar side of `whichever comes first`
- update/complete the Todoist projection accordingly

### Avoid notification churn
Small meter changes must not cause excessive Todoist edits or repeated notifications.

Introduce sensible update thresholds/debouncing, e.g. update the Todoist task when:
- status changes from OK -> due soon
- status changes from due soon -> due
- a materially useful displayed meter value changes
- the user explicitly refreshes/synchronizes

## Acceptance criterion
A maintenance rule such as `every 50 engine hours OR 12 months, whichever comes first` is calculated entirely by noteNFC, becomes visible in Todoist at the appropriate time, and remains correct even though Todoist itself has no understanding of engine hours.

