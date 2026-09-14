# Issue #14: [MVP] Add seasonal activation windows and reminder-health checks
state=open created=2026-09-13T23:54:09Z updated=2026-09-13T23:54:09Z labels= milestone=none comments=0

## Goal
Support assets that are only active for part of the year and verify that every tracked maintenance schedule actually has a functioning reminder path when it is supposed to.

Primary examples:
- hot tub used only during winter
- lawn mower / yard equipment used spring through fall
- snowblower used only in winter
- pool used seasonally in some climates but year-round in others

Related: #4 recurring schedules, #9 Todoist provider, #11 reminder actions

## Seasonal activation windows
Allow each maintenance schedule, or optionally an asset-level default, to define whether it is:
- active year-round
- active only during a configurable seasonal window

Examples:

```text
Hot tub water testing
Active: October 15 through April 15
```

```text
Lawn mower service reminders
Active: April 1 through November 1
```

```text
Pool chemistry
Active: year-round
```

Do not hard-code astronomical or climate seasons. Use explicit configurable date/month windows so users in different climates can define their own operating season.

## Inactive-season semantics
A recurring task that is intentionally inactive must not accumulate months of meaningless overdue occurrences.

When outside its active season:
- suppress normal recurring reminders/notifications
- represent the schedule as `seasonally inactive`, not overdue
- do not create a backlog of missed repetitions
- preserve history and recurrence configuration
- keep explicit off-season tasks possible (e.g. inspect winter cover)

On re-entry into the active season, recompute the next occurrence using an explicit policy rather than pretending the dormant months were normal missed cycles.

Support sensible policies such as:
- due immediately at season start
- due N days after season start
- resume from last completion using recurrence but clamp to active season
- explicit startup task controls activation

The exact UI can be simplified initially, but the domain model must distinguish `inactive because of season` from `overdue` and `disabled`.

## Seasonal startup/shutdown tasks
Allow one-off recurring annual tasks tied to season boundaries, for example:

Hot tub:
- open/prep hot tub for winter season
- inspect cover / refill / initial water balance
- close or summerize at season end

Power equipment:
- spring mower startup inspection
- fall mower storage prep
- snowblower pre-winter inspection
- generator storm-season readiness check

These can be ordinary yearly maintenance schedules; the seasonal feature should make it easy to associate them with the beginning/end of an asset's active season.

## Reminder-health / integrity check
Because maintenance reminders are safety/reliability mechanisms, noteNFC should periodically verify that schedules expected to notify the user actually have a healthy delivery path.

For each active schedule with reminders enabled, verify enough provider state to detect obvious failures such as:
- local notification permission disabled
- local scheduling/alarm registration missing where applicable
- Todoist integration disconnected
- Todoist-backed schedule missing its projected task
- projection is stale or failed
- schedule has no reminder provider configured
- asset has active maintenance schedules but reminders are globally disabled

Surface a compact status such as:

```text
Reminder health
12 schedules healthy
1 needs attention

Hot Tub - Test Water
Todoist task missing
[REPAIR]
```

## Self-healing where safe
Where an external projection is obviously missing and noteNFC has enough canonical state to recreate it safely, offer or perform an idempotent repair.

Do not silently repair ambiguous schedule conflicts.

## Interaction with Todoist
Todoist may represent seasonal startup/shutdown tasks as normal annual recurring tasks.

For high-frequency tasks such as `test hot-tub water every! 3 days`, noteNFC remains responsible for the seasonal activation rule. Todoist is only the reminder/task projection and should not become canonical merely because its recurrence grammar is richer.

When a seasonal schedule becomes inactive, the Todoist adapter should suppress/close/park its projection according to an idempotent provider policy; when the season activates, recreate/reactivate it without generating duplicate tasks.

## Acceptance criteria
1. A winter-only hot-tub user receives water-care reminders during their configured winter season and none of those repetitive reminders during summer.
2. A year-round pool user can leave the same class of schedules active all year.
3. A mower can have spring startup and fall storage reminders plus operating-season maintenance.
4. The app can tell the user whether all reminder-enabled schedules currently have a valid delivery path, including Todoist-backed schedules.

