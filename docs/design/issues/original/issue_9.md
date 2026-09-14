# Issue #9: [MVP] Add optional Todoist integration as the external reminder/task provider
state=open created=2026-09-13T23:38:09Z updated=2026-09-13T23:38:09Z labels= milestone=none comments=0

## Goal
Use Todoist as an optional reminder/task-delivery backend for noteNFC maintenance schedules without making Todoist the maintenance system of record.

noteNFC remains authoritative for assets, maintenance definitions, due state, maintenance history, and actual completion records. Todoist is a projection of that state into a mature task/reminder UX.

## Product boundary

```text
noteNFC
  owns:
    assets
    NFC identity
    maintenance schedules
    maintenance history
    actual completion dates
    usage/meter state

        |
        | Todoist adapter
        v

Todoist
  owns:
    task presentation
    reminders/notifications
    Today / Upcoming workflow
    snooze/postpone UX
    desktop/mobile delivery
```

Todoist must be optional. noteNFC should continue to support a simple local reminder provider for users who do not use Todoist.

## MVP requirements

### Authentication
- Add Todoist as an optional integration in Settings.
- Authenticate using the current supported Todoist OAuth flow suitable for an Android/public client.
- Provide connect, connection-status, and disconnect controls.
- Store credentials/tokens using Android-appropriate secure storage.

### Provider abstraction
Introduce a reminder/task provider boundary rather than coupling maintenance schedules directly to Todoist.

Conceptually:

```kotlin
interface ReminderProvider {
    suspend fun create(schedule: MaintenanceSchedule): ExternalReminder?
    suspend fun update(schedule: MaintenanceSchedule)
    suspend fun complete(schedule: MaintenanceSchedule, completedAt: Instant)
    suspend fun cancel(schedule: MaintenanceSchedule)
    suspend fun sync()
}
```

Initial providers:
- local noteNFC reminder provider
- Todoist reminder provider

The exact interface may differ, but maintenance-domain code must not depend directly on Todoist API objects.

### Create/project maintenance tasks into Todoist
For a maintenance schedule configured to use Todoist:
- create a Todoist task
- save the returned Todoist task ID on the local integration/projection record
- include enough context in the task to identify the asset and maintenance operation
- include a noteNFC deep link back to the relevant asset or maintenance schedule

Example task content:

```text
Load test UPS - Server Rack

Asset: CyberPower Rack UPS
Last performed: 2026-06-08

Open asset:
notenfc://asset/<asset-id>/maintenance/<schedule-id>
```

### Basic recurring schedules
Support the time-based recurrence modes required by the noteNFC MVP:
- every N days/weeks/months/years from the scheduled cadence
- every N days/weeks/months/years from actual completion date

Where appropriate, translate these to Todoist recurring-date syntax (for example fixed cadence vs completion-relative recurrence such as Todoist's `every` / `every!` behavior).

The noteNFC schedule remains canonical; the Todoist recurrence string is a provider-specific representation.

### noteNFC -> Todoist actions
When maintenance is completed inside noteNFC:
- create the local maintenance-history event first
- advance the canonical noteNFC schedule
- complete/update the corresponding Todoist task so its external representation stays aligned

When a schedule is edited, disabled, archived, or deleted in noteNFC, update or close/remove its Todoist projection as appropriate.

### Deep linking
Add stable noteNFC application links such as:

```text
notenfc://asset/<asset-id>
notenfc://asset/<asset-id>/maintenance/<schedule-id>
```

Todoist tasks should contain a link back to noteNFC.

Where supported, noteNFC should expose an `Open in Todoist` action for linked schedules/tasks.

### UX
Per maintenance schedule allow:

```text
Reminder provider:
( ) noteNFC
( ) Todoist
```

Show Todoist connection/projection status on schedules that use Todoist.

## Important semantics
The following must remain distinct concepts:
- snooze reminder: still due; remind me later
- postpone current occurrence: move the current planned date
- modify recurrence: permanently change the maintenance schedule
- complete early: record maintenance now and, for completion-relative recurrence, roll forward from the actual completion date

The domain model must not collapse those operations into a single `reschedule` state.

## Explicitly out of scope for this MVP
- Todoist -> noteNFC automatic completion synchronization
- webhooks / server component
- automatic creation of rich maintenance-history events from Todoist checkboxes
- usage/hour/mileage-driven Todoist synchronization
- custom maintenance-specific buttons inside Todoist notifications
- conflict-resolution UI for changes made independently in both apps
- multi-account / shared Todoist-project support

These should be handled in follow-on issues.

## Acceptance criterion
A user can connect Todoist, configure a time-based maintenance schedule in noteNFC to use Todoist, see and receive that recurring task through Todoist, jump back into the physical asset in noteNFC, complete the maintenance in noteNFC, and see the Todoist representation advance correctly without Todoist becoming the authoritative maintenance database.

Related: #4 recurring maintenance/reminders. This issue should reuse the same canonical schedule model rather than creating a second recurrence model specifically for Todoist.

