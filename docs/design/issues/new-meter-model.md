---
action: create
title: "[MVP] Meter model: meter definitions, readings, baselines, reset"
milestone: "Phase 2 — Journal + profiles"
labels: []
---

Split from #12 (D2 §5: "#12 (meter model → MVP · projection policy → NEXT)"). Also identified in D2 §4 item 12 (Data model: multiple meters per asset, meter reset/rollover, standalone "log a reading").

**Re-tiered to MVP**, because #4's MVP usage schedules and #3's oil-change acceptance example both depend on it, while its origin issue #12 stays NEXT for the Todoist half.

## Goal

The domain model for engine hours, mileage, cycles and any other monotonic counter, so usage-based and "whichever comes first" schedules have something real to compute against.

## Scope

### Meters are measurement definitions (D4 §6)

There is **no separate meter table**. A meter is a `measurement_definition` with `is_meter = 1`: asset-scoped, with `key` (e.g. `engine_hours`), `label`, `unit`, `value_type`, `decimals`, and optional range. This is what keeps #16's telemetry direction open without a migration — a telemetry reading is the same row a person would type.

Multiple meters per asset are supported by construction: one asset can carry `engine_hours` and `odometer_miles` as two definitions, and different schedules point at different ones.

### Readings

A reading is an ordinary `measurement` row on an ordinary event — including a standalone `MEASUREMENT` event, which is the **"log a reading"** flow (no maintenance implied). The **current meter value is derived**, never stored on the definition: the reading with the greatest `(occurred_on, occurred_time, created_at)`. It is cached in `schedule_state.current_meter` for schedules that use it (D4 §6, §8).

Readings lower than the previous one are **accepted with a warning** (meters get misread); the engine uses the latest by date, not the maximum (D5 §3).

### Baselines

- After any completion, `last_completed_meter` is known, because the completion form makes the meter field required when the schedule has a meter rule (a `QUICK` completion shows a single "current hours" prompt).
- Before the first completion, the baseline is the schedule's `anchor_meter` ("last serviced at 120 h"), entered at schedule creation.
- With neither, the schedule is `NO_DATA` and raises a health finding (D3 §7.3, `new-reminder-health`).

### Meter reset / rollover

New engine, replaced hour meter: **archive the old definition, create a new one, re-point the schedules, set the new `anchor_meter`.** There is deliberately no reset flag and no rollover arithmetic — history stays attached to the definition that produced it, and a definition with recorded data can never be deleted, only archived (`RESTRICT` on `measurement.definition_id`).

## Out of scope

- The rules that consume meters (`meter_interval`, `meter_lead`, combined time-OR-meter, completion advancing both sides) belong to **#4** (D5 §3, §4).
- The Todoist projection policy for usage schedules stays in **#12** (NEXT, Phase 5).
- Automatic or telemetry-sourced readings are **#16** (FUTURE); this model only refuses to assume readings are always manual.

## Acceptance criteria

1. An asset can carry two meter definitions and two schedules pointing at different ones.
2. The current meter value is the latest reading by date, not the maximum; a lower-than-previous reading is accepted with a warning and becomes current.
3. A standalone "log a reading" event updates the current meter without creating a maintenance event or completing anything.
4. With an `anchor_meter` and no completion, the meter side computes a due threshold; with neither, the schedule reports `NO_DATA` and a health finding appears.
5. Archiving a meter definition and re-pointing a schedule to a new one leaves the old readings intact and starts the new baseline from `anchor_meter`.
6. A definition with recorded measurements cannot be deleted.

## Design references

D2 §4 item 12 · D2 §5 (split of #12; meter model → MVP) · D4 §5 (`measurement`), §6 (`measurement_definition`, `is_meter`, derived current value, meter reset), §8 (`anchor_meter`, `schedule_state.current_meter`) · D5 §3 (meter rules), §4 (combined rule) · D7 Phase 2 · D8 risk 14 · testing doc §2 ("Usage").
