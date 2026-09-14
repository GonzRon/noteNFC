# Issue #10: [NEXT] Add two-way Todoist synchronization and completion reconciliation
state=open created=2026-09-13T23:38:34Z updated=2026-09-13T23:38:34Z labels= milestone=none comments=0

## Goal
Extend the Todoist MVP so actions taken in Todoist can be reconciled back into noteNFC without making Todoist authoritative for maintenance state.

Depends on: #9

## Problem
In the MVP, noteNFC pushes/project maintenance schedules into Todoist and noteNFC-originated completion updates Todoist. A user will naturally also check tasks complete, postpone them, or edit dates from Todoist itself.

We need a safe synchronization model for those external changes.

## Requirements

### Pull-based synchronization first
- Implement periodic/on-launch pull synchronization using Todoist's current API.
- Do not introduce a server component solely for this feature.
- Use Android WorkManager or equivalent for opportunistic background reconciliation.
- Persist provider-side revision/sync metadata so syncs can be incremental where supported.

### Completion reconciliation
When a Todoist maintenance task is completed externally:
- detect the completion
- map the task back to the canonical noteNFC maintenance schedule
- record the external completion timestamp/source
- decide whether this can safely create a maintenance event automatically

Support two completion classes:

#### Simple completion
Suitable for tasks such as:
- inspect UPS
- clean filter
- exercise generator
- visual inspection

Todoist completion may create a minimal maintenance-history event automatically:

```text
completed_at: <Todoist completion time>
source: TODOIST
```

#### Rich completion
Suitable for maintenance that normally captures structured details:
- oil change
- UPS battery replacement
- mower service
- RO membrane replacement

Do not silently fabricate a complete maintenance record. Mark it as `completion pending details` or equivalent and prompt the user to finish the record in noteNFC.

### External rescheduling
Detect externally changed due dates/recurrence where practical.

Do not blindly overwrite the canonical noteNFC schedule. Differentiate:
- snoozed/reminder-only change
- postponed current occurrence
- recurrence changed
- task manually edited in a way noteNFC cannot safely interpret

If the intent is ambiguous, surface a reconciliation state rather than guessing.

### Conflict handling
Track enough state to detect when both sides changed since the previous successful sync.

Provide simple conflict choices such as:
- keep noteNFC schedule
- accept Todoist occurrence date
- inspect changes

The maintenance history itself remains local/domain-authoritative.

### Idempotency
Repeated syncs must not:
- create duplicate maintenance events
- repeatedly advance a recurring schedule
- recreate deleted tasks unexpectedly

Provider task ID + occurrence/completion identity should be sufficient to make processing idempotent.

### Visibility
Show the user when a Todoist-backed schedule is:
- in sync
- awaiting sync
- externally modified
- awaiting maintenance details
- conflicted

## Webhooks: later optimization
Todoist webhook support may eventually provide near-real-time change notifications, but a webhook receiver implies an internet-accessible service. Do not add a backend simply to obtain webhooks.

If noteNFC later gains an optional sync/backend service, webhook-driven synchronization can be added behind the same reconciliation layer.

## Acceptance criterion
A user can complete or postpone a linked maintenance task in Todoist, later open noteNFC, and have the external action reconciled safely and idempotently into the canonical maintenance schedule/history with ambiguity surfaced instead of silently guessed.

