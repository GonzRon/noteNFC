# Issue #16: [FUTURE] Explore Home Assistant, InfluxDB, and Grafana integrations for asset telemetry/history
state=open created=2026-09-13T23:54:39Z updated=2026-09-13T23:54:39Z labels= milestone=none comments=0

## Goal
Record a possible future integration direction for automatically importing equipment telemetry/measurements and exposing noteNFC's structured asset history to existing home-automation/time-series tools.

This is intentionally exploratory and **not MVP scope**.

Depends conceptually on: #3 structured measurements/events
Related: #13 measurement profiles, #4/#12 maintenance schedules

## Why this could be useful
noteNFC is primarily a physical-asset journal/reminder system, but many home assets already expose measurements elsewhere.

Examples:
- hot-tub/pool temperature, pump state, sanitizer sensors, power use
- UPS battery/load/runtime/voltage data
- generator runtime/status
- HVAC temperatures/pressures/runtime
- RO/water-system pressure/TDS where sensors exist
- smart plugs / energy monitors

Rather than force users to re-enter every measurement manually, future adapters could ingest selected readings or events while keeping noteNFC's asset/event model canonical for maintenance records.

## Home Assistant integration ideas
Potential directions to investigate:
- associate a noteNFC asset with one or more Home Assistant entities/devices
- import selected HA sensor states into #3 measurement events
- use HA state transitions as triggers for noteNFC events (e.g. generator started, pump runtime threshold crossed)
- expose noteNFC maintenance due/overdue state back to Home Assistant as entities/sensors
- provide a `scan NFC -> open noteNFC asset` workflow alongside HA's existing NFC/tag capabilities

Avoid creating a hard dependency on Home Assistant; this should be an optional adapter.

## InfluxDB integration ideas
Potential directions:
- optionally write structured noteNFC measurements/events to an InfluxDB bucket
- optionally read selected time-series values from InfluxDB as measurements/meter inputs
- map series/fields explicitly to a noteNFC asset + metric rather than importing arbitrary buckets
- preserve source metadata so imported readings are distinguishable from manual readings

Do not make InfluxDB the authoritative maintenance/event database.

## Grafana integration ideas
Grafana itself would likely be a visualization consumer rather than the primary integration target.

Potential directions:
- document/query a stable export/API/data-source path so Grafana can chart noteNFC measurements
- dashboards for hot-tub chemistry trends, UPS runtime degradation, generator hours, etc.
- link from a Grafana panel back to the relevant noteNFC asset/event where practical

If InfluxDB export exists, Grafana may require little or no noteNFC-specific code beyond stable field/tag conventions.

## Triggering maintenance from telemetry
A later extension could feed telemetry into the maintenance engine, e.g.:

```text
Generator runtime crosses 100 hours
      -> noteNFC schedule becomes due
      -> Todoist/local reminder projection created
```

or:

```text
UPS runtime test value trends below configured threshold
      -> flag inspection / battery replacement candidate
```

This should reuse the canonical scheduling/event model rather than creating integration-specific maintenance rules.

## Security / reliability principles
Any future implementation should:
- be opt-in
- work without cloud services when integrations are not used
- tolerate the external system being unavailable
- make source/provenance visible
- avoid silently overwriting manual maintenance history
- use idempotent import/export behavior

## Explicitly out of scope for this ticket
- implementation commitment
- automatic chemical dosing
- arbitrary AI/predictive maintenance
- building a full home-automation platform inside noteNFC
- requiring InfluxDB/Grafana/Home Assistant for normal operation

## Outcome expected from this ticket
When the core MVP is stable, revisit this issue and determine whether one narrowly-scoped adapter (most likely Home Assistant or InfluxDB export) delivers enough value to justify implementation.

