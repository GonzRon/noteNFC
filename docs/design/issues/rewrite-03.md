---
action: rewrite
number: 3
title: "[MVP] Add asset event journal with structured measurements and consumable usage"
milestone: "Phase 2 — Journal + profiles"
labels: []
---

> Revised 2026-09-14 per the approved design package (`docs/design/`, review 1). Original text is preserved in this issue's edit history and in `docs/design/issues/original/issue_3.md`. Design references: D2 §1 (row 3), D2 §3 (C2), D2 §5; D4 §5, §6, §12; D5 §5, §9; D7 Phase 2.

Kept, with three adjustments: the queryable-time-series storage requirement moves here from #13, quick actions are declared to be instances of #13 profiles, and the `source` enum is defined here.

## Goal

Replace a maintenance-only free-text history with a generic **asset event journal** that captures maintenance, inspections, measurements, treatments and failures without a bespoke schema per asset type.

## Core model (D4 §5)

`asset_event` is the aggregate root: `id`, `asset_id`, `kind` (`MAINTENANCE | INSPECTION | MEASUREMENT | TREATMENT | INCIDENT | REPLACEMENT | SEASON_START | SEASON_END | NOTE | CUSTOM`), `title`, `profile_id`, `schedule_id` (the completion link), `occurred_on` (required calendar date, backdating allowed), optional `occurred_time`, `tz_id`, `notes`, `cost_minor`/`currency`, `source`, `source_ref`, `details_pending`, `created_at`, `updated_at`.

Children: `measurement` and `consumable_usage` rows cascade from the event. Attachments are modelled here as a relationship but delivered by #7 in Phase 4.

### Provenance: the `source` enum (defined here)

```
source ∈ { MANUAL, SCHEDULE_QUICK_COMPLETE, TODOIST_SYNC, IMPORT, TELEMETRY }
```

`source_ref` carries the external identity (a Todoist completion id, an import batch id). `UNIQUE(source, source_ref)` is what makes external-completion sync idempotent (D4 §5). `TELEMETRY` exists so #16's future adapters do not need a schema change (D2 §1 row 16).

### Append-oriented vs editable (contradiction C2)

Events are editable in place with `updated_at`; there is no supersede chain. Auditability comes from keeping `created_at`, never overwriting history implicitly, and recomputing schedule state from events so an edit or deletion cannot leave a stale due date (D4 §5, D5 §9).

## Structured measurements

Measurements are controlled EAV (ledger A14): `measurement` rows point at an asset-scoped `measurement_definition` (typed catalogue row), values live in typed columns (`value_num` / `value_text`), and the `unit` is snapshotted at entry time so later edits to the definition do not rewrite history (D4 §5, §6). Range metadata (`range_low`, `range_high`) lives on the definition; below/in/above classification happens at read time.

## Queryable time series (moved here from #13)

**Storage requirement:** structured readings must remain queryable as a time series. The query is a join `measurement → asset_event` on `definition_id`, ordered by `occurred_on, occurred_time`, supported by the `(definition_id, event_id)` and `(asset_id, occurred_on DESC, created_at DESC)` indexes (D4 §5). Charts and analytics are not required by this issue; preserving the structured data for them is.

## Consumables

`consumable_usage`: optional `supply_id`, a required `name` snapshot, `quantity`, `unit`, `affects_stock`. The journal must not depend on inventory being enabled; stock decrement is behind #15 (Phase 6).

## Quick actions are profiles, not code

The original quick-action list (`Battery replaced`, `Oil changed`, `Filter changed`, `Inspected`, `Cleaned`, `Water tested`, `Chemicals added`, `Custom`) is **not hard-coded**. Each is an instance of an `event_profile` from #13, seeded by `new-asset-templates`. What is compiled is the single generic entry form, validation, range classification and the schedule-completion hook (D3 §6, D4 §7).

## Completion hook

Saving an event with a `schedule_id` does not "advance" anything by itself: it inserts the event and then calls `ScheduleRecompute.rebuild`, which is the only write path into `schedule_state` (D5 §5). Editing or deleting the event recomputes the same way.

## Acceptance criteria

1. A hot-tub water test records pH / free chlorine / total alkalinity / calcium hardness / temperature as typed measurements with units, retrievable later as structured history, not free text.
2. A mower oil change records engine hours as a measurement of a meter definition and advances the linked schedule through `rebuild`.
3. Correcting an event preserves `created_at` and updates `updated_at`; the linked schedule's due date changes accordingly.
4. "pH over time" is answerable with one query against `measurement` joined to `asset_event`.
5. No hot-tub-specific or mower-specific table exists.

## Visual design

D12 (Apollo Service Binder) §8 Service Ledger, §9 Instrument Measurement — history is a durable record with dates, measurements, parts and notes in aligned structure, and a reading renders as large value, small unit, explicit status and reference interval. Acceptance: range classification is shown as **LOW / IN RANGE / HIGH** wording plus icon plus the reference range, never colour alone, and the journal is legible in grayscale.

## Design references

D2 §1 row 3 · D2 §3 C2 · D2 §5 · D3 §6 (data-driven vs compiled) · D4 §5 (`asset_event`, `measurement`, `consumable_usage`) · D4 §6 (definitions and the EAV trade-off) · D4 §12 (canonical vs derived) · D5 §5 (rebuild) · D5 §9 (event edits and deletions) · D7 Phase 2.
