# D5 — Scheduling semantics and state machine

Status: design-phase document, 2026-09-14. This is the behavioural specification of the
`core.scheduling` engine. Every rule here is a pure function of (schedule config, completion
events, meter readings, today's date) and is the primary target of the deterministic test suite
(testing document §2). Column names refer to D4 §8.

Vocabulary:

- **Rule**: the canonical recurrence configuration on `maintenance_schedule`.
- **Current occurrence**: the single next thing to do. There is never more than one.
- **Effective due date** `E = postponed_due_on ?: computed_due_on`.
- **Today** `T`: `LocalDate.now(deviceZone)`; passed in explicitly everywhere (never read inside the engine).
- **Completion**: an `asset_event` whose `schedule_id` points at the schedule. `C` = its `occurred_on`.

---

## 1. Status (derived, never stored)

```
status(schedule, state, T):
  if schedule.status == ARCHIVED          → (excluded from all lists)
  if schedule.status == PAUSED            → PAUSED
  if seasonFollows && !inSeason(asset, T) → INACTIVE_SEASON
  if timeRule && state.computed_due_on == null && meterRule && state.computed_due_meter == null
                                          → NO_DATA           (no baseline yet)
  timeStatus  = E == null ? OK
              : E <  T                    ? OVERDUE
              : E == T                    ? DUE
              : E <= T + lead_days        ? DUE_SOON
              : OK
  meterStatus = computed_due_meter == null || current_meter == null ? OK
              : current_meter >= computed_due_meter                  ? DUE      (meters have no "overdue" degree; show "+N over")
              : current_meter >= computed_due_meter - meter_lead      ? DUE_SOON
              : OK
  return worst(timeStatus, meterStatus)   // OVERDUE > DUE > DUE_SOON > OK
```

Snooze does not appear here: it only suppresses notifications (§7).

## 2. Time rules

### 2.1 FIXED basis ("every N units from the scheduled cadence")

All due dates lie on the series `anchor_on + k·interval`, k ≥ 0, computed **from the anchor with
a multiplier**, never iteratively, so month-end and leap-day clamping cannot drift:

```
seriesDate(k) = anchor_on.plus(k * interval, unit)     // java.time: Jan 31 + 1 month = Feb 28/29; Feb 29 + 1 year = Feb 28
```

Advancement on completion `C` when the current occurrence is due `D`:

```
nextDue = smallest seriesDate(k) such that seriesDate(k) > max(D, C)
```

- Early completion (`C < D`): next is the series date after `D`. Completing the April inspection in
  March does not move July.
- Late completion (`D < C < nextSeries`): next is the series date after `D`, which is also after
  `C`; unchanged.
- Very late completion (`C` beyond one or more series dates): skips forward to the first series date
  after `C`. **No backlog of missed occurrences is ever created.** This is deliberate and matches
  the "do not build months of meaningless missed occurrences" requirement; it also differs from
  Todoist's `every` which only advances one step.
- Initial state (no completions): `computed_due_on = anchor_on` if `anchor_on >= T` at creation,
  otherwise the first series date `>= T` (a newly created schedule anchored in the past is not
  immediately overdue).

### 2.2 COMPLETION basis ("every N units after I actually did it")

```
computed_due_on = (last_completed_on ?: anchor_on).plus(interval, unit)      // if last_completed_on == null, due = anchor_on
```

More precisely: with no completion, `computed_due_on = anchor_on` (the user states when it is
first due). With a completion, `computed_due_on = C.plus(interval)`. Early and late completions
both move the whole series; this is Todoist's `every!`.

### 2.3 Calendar arithmetic rules (tested)

| Case | Rule |
|---|---|
| Month/year overflow | `LocalDate.plusMonths/plusYears` clamp to the last valid day (Jan 31 + 1 month → Feb 28 or 29) |
| Leap day anchors | Feb 29 + 1 year → Feb 28 in a common year, Feb 29 in the next leap year (computed from the anchor, so it returns to Feb 29) |
| Year wrap-around | plain date arithmetic; no special case |
| Weeks | `plusWeeks`; days `plusDays` |
| Time zones / DST | irrelevant to dates; the engine never sees an instant. The device zone enters only when a reminder instant is derived (§11) |
| "Today" boundary | `T` is the device-local date at evaluation; a schedule due `T` is DUE all day |

## 3. Meter (usage) rules

```
computed_due_meter = last_completed_meter + meter_interval
current_meter      = latest reading of meter_definition_id across the asset's events
                     (ordered by occurred_on, occurred_time, created_at)
```

- The completion form makes the meter field **required** when the schedule has a meter rule;
  `QUICK` completion on such a schedule shows a single "current hours" prompt. Therefore
  `last_completed_meter` is always known after a completion.
- With no completion yet, the baseline is `anchor_meter` (a config value entered at creation:
  "last serviced at 120 h"). If neither exists → `NO_DATA` and a health finding.
- A reading is any measurement of the meter definition on any event, including a standalone
  `MEASUREMENT` event ("log hours"). Readings lower than the previous one are accepted with a
  warning (meters can be misread); the engine uses the latest by date, not the maximum.
- Meter reset (new engine): archive the definition, create a new one, re-point the schedule, set
  `anchor_meter` for the new baseline.

## 4. Combined rule: time OR meter, whichever first

Both sides are computed independently; the schedule is due when either is (status = worst of the
two). Completion advances **both** sides from the same event: `computed_due_on` from `C` (or from
the series for FIXED) and `computed_due_meter` from the meter reading on the completion event.
`effective_due_on` stays the time side; the dashboard shows "due at 170 h (now 165 h) or by
2027-04-10".

## 5. Recompute from history (the only write path into `schedule_state`)

```
rebuild(schedule, events(asset), T):
  completions = events where schedule_id == schedule.id, ordered by occurred_on, created_at
  last = completions.lastOrNull()
  last_completed_on      = last?.occurred_on
  last_completed_meter   = last?.measurementOf(meter_definition_id) ?: schedule.anchor_meter
  current_meter          = latestReading(meter_definition_id, events)
  computed_due_on        = timeRule ? (FIXED ? nextSeriesAfter(prevDue, last) : completionRule(last)) : null
  computed_due_meter     = meterRule && last_completed_meter != null ? last_completed_meter + meter_interval : null
  apply season re-entry adjustment (§6)
  effective_due_on       = schedule.postponed_due_on ?: computed_due_on
  season_active, next_season_start_on
  computed_for_on = T
```

For FIXED, `prevDue` (the occurrence the last completion satisfied) is reconstructed as the
largest series date `<= last_completed_on`, or the anchor if none, so the rebuild is a pure
function of config + events with no hidden state. Consequence: editing or deleting a completion
event, importing a backup, or changing the rule all converge to the same state by calling
`rebuild`. There is no "advance" operation with its own logic; "advance on completion" is simply
"insert the completion event, then rebuild".

## 6. Seasonal activation

Window lives on the asset (`season_start_mmdd`, `season_end_mmdd`); year-round when both null.
Wrap-around windows (Oct 15 → Apr 15) are allowed. Each schedule chooses
`season_behavior = FOLLOW_ASSET | IGNORE`.

```
inSeason(asset, T): both null → true; start <= end → start <= T.mmdd <= end; else T.mmdd >= start || T.mmdd <= end
```

While `FOLLOW_ASSET` and out of season:
- status = `INACTIVE_SEASON` (distinct from OVERDUE, PAUSED, ARCHIVED);
- no local notifications; Todoist projection is `PARKED` with its due date set to the re-entry due
  date (computable), not deleted; a seasonal schedule is never projected as `NATIVE_RECURRING`
  because Todoist cannot express the window;
- `computed_due_on` is still computed (it may lie inside the off-season) but is not surfaced as due;
- no missed occurrences accumulate because there are no occurrences, only one current one.

Re-entry (first evaluation with `inSeason == true` after a period out of season, or the daily
job noticing the boundary):

| `season_reentry` | Rule | Use |
|---|---|---|
| `AT_START` (+ `season_reentry_offset_days`) | `computed_due_on = seasonStart(thisCycle) + offset` if the computed date is outside the season or earlier than that; otherwise keep the computed date | "Test water when the tub is opened", "3 days after start" |
| `RESUME_CLAMPED` | `computed_due_on = max(computedFromRule, seasonStart(thisCycle))` | Long intervals that may legitimately land inside the coming season ("deep-clean filter every 200 days after last") |

Deferred (not MVP): `MANUAL_STARTUP` — cadence begins only when a `SEASON_START` event is logged.
It can be emulated today: set the schedule's `anchor_on` when logging the startup event (the
startup profile offers "reset these schedules' anchors").

Annual season tasks ("winter startup" on Oct 10, "fall storage prep" on Nov 5) are ordinary
FIXED yearly schedules with `season_behavior = IGNORE`. They **must** ignore the window because
their dates fall outside it; the schedule editor warns when a FOLLOW_ASSET schedule's anchor lies
outside the asset's window.

Season boundaries and status are evaluated on `T` only; nothing is stored at "season end". Turning
a window on or off is a rule edit (§8.6) and triggers `rebuild`.

## 7. The four operations that are not aliases

| Operation | Changes | Does **not** change | Event created? | Projection effect (Todoist) |
|---|---|---|---|---|
| **Complete** (now or early) | inserts completion event; `rebuild`; clears `postponed_due_on` and `snoozed_until_at` | the rule | yes (`QUICK`: minimal; `FORM`: profile form) | `MANAGED_OCCURRENCE`: `SYNC_DUE` (task re-dated to the new due, reopened first if it was closed). `NATIVE_RECURRING`: `COMPLETE` (`item_close` advances the remote task by its own rule) followed by `SYNC_DUE` if the remote date differs from the canonical one |
| **Snooze** | `snoozed_until_at` (instant) | due date, rule, state | no | none (Todoist owns its own snooze; a local snooze is local) |
| **Postpone occurrence** | `postponed_due_on` | rule; `computed_due_on` (kept for audit) | no | `SYNC_DUE` with the new due (both representations; for native recurring the recurrence string is retained) |
| **Edit recurrence** | rule columns, `anchor_on`; clears `postponed_due_on`; `rebuild` | history | no | `UPSERT`; the representation is re-evaluated (a schedule that stops being time-only/completion-relative/year-round drops from native to managed, and vice versa) |

Snooze semantics: notifications for this schedule are suppressed until the instant; the dashboard
still shows OVERDUE with a "snoozed until" badge. Snooze never survives a completion or a postpone
(both clear it). Postpone semantics: the current occurrence's date moves; on completion the next
occurrence is computed from the **rule** (FIXED: the series; COMPLETION: the completion date), not
from the postponed date, and the override is cleared. This is exactly the distinction issue #4
demands and issue #11 restates.

## 8. State transitions

```
                     create
                       │
                       ▼
   ┌──────────────── ACTIVE ────────────────┐
   │   (status derived: OK/DUE_SOON/DUE/    │
   │    OVERDUE/INACTIVE_SEASON/NO_DATA)    │
   │                                        │
   │ complete   → insert event, rebuild     │
   │ snooze     → snoozed_until_at          │
   │ postpone   → postponed_due_on          │
   │ edit rule  → rule, clear postpone,     │
   │              rebuild                    │
   │ season out → INACTIVE_SEASON (derived) │
   │ season in  → re-entry rule, rebuild    │
   └───────┬───────────────────────┬────────┘
     pause │                       │ archive
           ▼                       ▼
        PAUSED ── resume ──▶ ACTIVE   ARCHIVED ── unarchive ──▶ ACTIVE
        (no due, no notify,           (hidden; projection WITHDRAWN;
         projection PARKED)            events keep schedule_id)
                                          │ delete (after WITHDRAW op DONE)
                                          ▼
                                       (row purged; events SET NULL)
```

`pause` keeps overrides; `resume` runs `rebuild` (a paused schedule that became overdue while
paused is shown overdue on resume, which is the honest answer; the user can postpone).

## 9. Event edits and deletions

- Deleting the latest completion event → `rebuild` → the previous completion becomes the anchor
  (COMPLETION) or the series recomputes (FIXED). If no completion remains, `anchor_on` applies.
- Changing an event's `occurred_on` or its meter measurement → `rebuild`.
- Re-pointing an event to another schedule (`schedule_id`) → `rebuild` both.
- An external completion (`source = TODOIST_SYNC`) is an ordinary completion event with
  `details_pending = 1` when the schedule is `FORM` mode; it advances the schedule immediately and
  a "needs details" badge remains until edited. Duplicate delivery is prevented by
  `UNIQUE(source, source_ref)`.

## 10. Worked examples (all dates 2026 unless noted; `lead_days = 14` unless noted)

### 10.1 FIXED quarterly inspection, anchor 2026-01-01, every 3 months

Series: Jan 1, Apr 1, Jul 1, Oct 1, 2027-01-01 …

| Event | Result |
|---|---|
| Created on Feb 10 | current due Apr 1 (first series date ≥ T); OK until Mar 18; DUE_SOON Mar 18–31; DUE Apr 1 |
| Completed Mar 20 (early) | next = first series date > max(Apr 1, Mar 20) = **Jul 1** |
| Instead completed Apr 20 (late) | next = first > max(Apr 1, Apr 20) = **Jul 1** |
| Instead completed Jul 15 (very late) | next = first > max(Apr 1, Jul 15) = **Oct 1**; the July occurrence is skipped, no backlog |
| Delete the Jul 15 event | rebuild: last completion none → due = first series date ≥ T; if T = Aug 1 → **Oct 1** |

### 10.2 COMPLETION UPS load test every 90 days

`anchor_on = 2026-06-08` (last performed, entered at creation).

| Event | Result |
|---|---|
| Created | due **Sep 6** |
| Completed Sep 13 | due **Dec 12** (Sep 13 + 90) |
| Instead completed Aug 30 (early) | due **Nov 28** |
| Postponed to Sep 20 on Sep 7, then completed Sep 25 | postponed cleared; due **Dec 24** (Sep 25 + 90), the postponement did not shift the rule |
| Snoozed 3 days on Sep 7 | still OVERDUE; no notification until Sep 10 09:00 local; due Sep 6 unchanged |

### 10.3 Mower oil change: every 50 h OR 12 months, whichever first

Meter definition `engine_hours`, `meter_lead = 5`, basis COMPLETION.

| Event | State |
|---|---|
| Created Apr 10 with "last changed at 120 h on Apr 10" | `computed_due_on` 2027-04-10; `computed_due_meter` 170 |
| Reading 165 h logged Aug 1 | meter DUE_SOON (165 ≥ 170 − 5); time OK → **DUE_SOON** |
| Reading 171 h logged Aug 15 | meter DUE → **DUE** ("171 / 170 h") |
| Completed Aug 20 with 172 h, 2.0 qt oil, filter | due_on 2027-08-20; due_meter **222**; both sides advanced from one event |
| Alternative: no readings, T = 2027-04-11 | time OVERDUE → **OVERDUE** regardless of meter |

### 10.4 Winter hot tub: test water every! 3 days, window Oct 15 → Apr 15

Schedule `FOLLOW_ASSET`, `AT_START`, offset 0.

| Event | State |
|---|---|
| Completed Apr 13 | computed due Apr 16 |
| T = Apr 16 | out of season → **INACTIVE_SEASON**; no notification; Todoist task PARKED with due Oct 15 |
| T = Jul 1 | still INACTIVE_SEASON; nothing accumulates |
| T = Oct 15 | in season; re-entry AT_START → due **Oct 15** → DUE |
| Completed Oct 15 | due Oct 18 |

Same asset, "deep-clean filter every! 200 days", `RESUME_CLAMPED`: completed Apr 10 → computed
Oct 27 → inside the coming season → due stays **Oct 27** (AT_START would have said Oct 15). Same
schedule completed Mar 1 → computed Sep 17 (off-season) → clamped to **Oct 15**.

"Winter startup" yearly FIXED anchored Oct 10, `IGNORE` season: due Oct 10 every year even though
Oct 10 is outside the window. The editor warns if someone sets it to FOLLOW_ASSET.

Pool in Florida: no window on the asset → every schedule behaves as year-round; `FOLLOW_ASSET` is a
no-op.

### 10.5 Recurrence edit

UPS load test every 90 days → edited to every 60 days on Sep 20 with last completion Sep 13:
`rebuild` → due **Nov 12**. Postponement (if any) cleared. FIXED quarterly → edited to monthly
with a new anchor Oct 1: due = first series date ≥ T.

### 10.6 Pause / archive / season deactivation, side by side

| | Due date | Notifications | Dashboard | Todoist |
|---|---|---|---|---|
| Overdue | past | yes (subject to snooze/fatigue rules) | red | task overdue |
| Snoozed | unchanged | suppressed until instant | red + badge | unchanged |
| INACTIVE_SEASON | computed but not surfaced | none | grey "inactive until Oct 15" | PARKED (re-dated to re-entry; seasonal schedules are always `MANAGED_OCCURRENCE`) |
| PAUSED | none | none | grey "paused" | PARKED (managed: undated; native recurring: due cleared, recurrence string kept; restored on resume) |
| ARCHIVED | none | none | hidden | WITHDRAWN (task deleted) |

## 11. From due dates to reminder instants (the only place zones enter)

The local provider fires a **daily digest** at a user-chosen local time (default 09:00). The alarm
instant is `ZonedDateTime.of(T_next, reminderTime, deviceZone).toInstant()`, recomputed by the
alarm receiver itself after firing and by receivers for `BOOT_COMPLETED`, `TIME_SET`,
`TIMEZONE_CHANGED`, `DATE_CHANGED`. Non-existent local times on spring-forward days resolve
forward per `java.time`; ambiguous times on fall-back days resolve to the earlier offset. Because
due dates are dates, a zone change never changes *what* is due, only *when* the phone says so.

Snooze durations are instants (`now + 1 day` etc.) so that "snooze 1 day" at 23:00 does not
expire at midnight.

## 12. Invariants (assertions in the engine and properties in tests)

1. At most one current occurrence per schedule; `effective_due_on` is null only when there is no time rule.
2. `rebuild` is idempotent: `rebuild(rebuild(s)) == rebuild(s)`.
3. `rebuild` is a pure function of (config, events, T): the same inputs on another device give the same state (backup/restore equivalence).
4. For FIXED, every `computed_due_on` lies on the series `anchor_on + k·interval`.
5. For COMPLETION, `computed_due_on == last_completed_on + interval` whenever a completion exists.
6. Completion always clears both overrides.
7. Snooze never changes any `*_on` column.
8. INACTIVE_SEASON never produces a notification or a due-count on the dashboard.
9. A rule with both sides due reports the worst status; completing advances both.
10. Status is monotone in `T` between events: as `T` increases, status never goes from OVERDUE back to OK without an event or edit (season boundaries excepted).
