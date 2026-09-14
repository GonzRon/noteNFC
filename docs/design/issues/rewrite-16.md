---
action: rewrite
number: 16
title: "[FUTURE] Explore Home Assistant, InfluxDB, and Grafana integrations for asset telemetry/history"
milestone: "Phase 7 — Extension points"
labels: []
---

> Revised 2026-09-14 per the approved design package (`docs/design/`, review 1). Original text is preserved in this issue's edit history and in `docs/design/issues/original/issue_16.md`. Design references: D2 §1 (row 16), D2 §5; D3 §2; D4 §5, §6; D7 Phase 7.

Kept as **FUTURE exploration**. Nothing is committed to. The one addition: the two invariants the core model keeps so that this stays possible without a schema change later.

## Purpose of this issue

Record a possible future direction — importing equipment telemetry as measurements and exposing noteNFC's structured history to existing home-automation and time-series tools — and, more importantly, pin down what the MVP model must *not* foreclose.

Depends conceptually on #3 (structured measurements). Related: #13 (profiles), #4 and #12 (schedules and meters).

## The two invariants the core model keeps for this

These are already in the MVP schema, so no migration is needed if an adapter is ever built:

1. **Measurement provenance.** Every event carries `source ∈ {MANUAL, SCHEDULE_QUICK_COMPLETE, TODOIST_SYNC, IMPORT, TELEMETRY}` plus `source_ref`, with `UNIQUE(source, source_ref)` for idempotent import. `TELEMETRY` exists in the enum from day one precisely so an imported reading is always distinguishable from a hand-entered one (D4 §5, defined in #3).
2. **Meter readings are ordinary measurements.** A meter is a `measurement_definition` with `is_meter = 1`; its readings are plain `measurement` rows on ordinary events, and the current value is derived as the latest reading. There is no separate meter table and no assumption that readings can only be typed by a human, so a telemetry source can feed the scheduling engine by inserting the same rows a person would (D4 §6, D5 §3).

A `MeasurementSource` port stub in Phase 7 (telemetry → measurements with `source = TELEMETRY`) is the only code this direction needs before an actual adapter exists (D3 §2, D7 Phase 7).

## Exploration directions (unchanged, uncommitted)

- **Home Assistant** — associate an asset with HA entities; import selected sensor states as measurement events; use HA state transitions as triggers; expose due/overdue state back to HA; complement HA's own NFC tag handling. No hard dependency.
- **InfluxDB** — optionally write measurements to a bucket; optionally read selected series as measurement or meter inputs; map series and fields explicitly to an asset + metric rather than importing arbitrary buckets; preserve provenance. Never authoritative for maintenance data.
- **Grafana** — a visualisation consumer. A stable export or data-source path plus stable field/tag conventions may be all that is needed; link from a panel back to the relevant asset.
- **Telemetry-driven maintenance** — "generator runtime crosses 100 hours → schedule becomes due" must reuse the canonical scheduling model, never an integration-specific rule.

## Principles any future implementation must follow

Opt-in; works without cloud services when unused; tolerates the external system being unavailable; makes provenance visible; never silently overwrites manual history; idempotent import and export.

## Explicitly out of scope

Implementation commitment, automatic chemical dosing, AI or predictive maintenance, building a home-automation platform inside noteNFC, requiring any of these systems for normal operation.

## Expected outcome

When the core MVP is stable, revisit and decide whether one narrowly scoped adapter — most likely Home Assistant or an InfluxDB export — delivers enough value to justify implementation.

## Design references

D2 §1 row 16 · D2 §5 · D3 §2 (`MeasurementSource` port, future) · D4 §5 (`source`, `source_ref`), §6 (`is_meter`, derived current value) · D5 §3 (meter rules) · D7 Phase 7.
