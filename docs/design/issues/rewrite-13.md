---
action: rewrite
number: 13
title: "[MVP] Add configurable event/measurement profiles with hot-tub and power-equipment templates"
milestone: "Phase 2 — Journal + profiles"
labels: []
---

> Revised 2026-09-14 per the approved design package (`docs/design/`, review 1). Original text is preserved in this issue's edit history and in `docs/design/issues/original/issue_13.md`. Design references: D2 §1 (row 13), D2 §5; D3 §6; D4 §6, §7; D7 Phase 2.

Kept. One requirement moved out, one dependency made explicit.

## Goal

A configurable profile layer on top of #3's journal, so common assets get fast, purpose-built entry screens without one schema per asset type.

Depends on #3. Related: #4 (schedule linkage), `new-asset-templates` (seeding), `new-meter-model` (meter definitions).

## Profile model (D4 §7)

- `event_profile`: `asset_id`, `name` ("Test water", "Oil change", "Load test"), `event_kind`, `default_title`, `template_key`, `sort_order`, `archived_at`.
- `profile_field`: links the profile to a `measurement_definition` with `required` and `sort_order`; `UNIQUE(profile_id, definition_id)`.
- `profile_consumable`: `name`, `default_quantity`, `unit`, optional `supply_id`, `sort_order`.
- `measurement_definition` (D4 §6) is asset-scoped and carries `key`, `label`, `unit`, `value_type`, `decimals`, `range_low`/`range_high`, `is_meter`.

Profiles are editable by the user. Built-in templates are starter defaults, never mandatory global schemas.

## The data-driven / compiled boundary (D3 §6)

**Data:** which fields, their order, labels, units, required flags, ranges, suggested consumables, the default event kind and title.

**Compiled:** the single generic entry form (numeric/text/boolean rows + consumables + notes + date), validation, range classification, and the schedule-completion hook.

**Not supported by design:** conditional fields, computed fields, custom widgets, expressions. This boundary is what keeps the profile layer from growing into a form framework (risk 7 in D8).

## Starter profiles

**Hot Tub** — Test water, Add chemicals / balance water, Shock / sanitize, Clean/rinse filter, Deep-clean/replace filter, Drain/refill, Open/close for season. Water-test fields include pH, free chlorine and/or bromine, total chlorine, total alkalinity, calcium hardness, cyanuric acid, water temperature, plus user-added fields. Chlorine-specific fields must not be forced on a bromine user: fields are enabled/disabled per asset, and labels and units are editable.

**Power equipment** — Oil change, Oil/filter change, Air-filter service, Spark-plug service, Blade/belt inspection or replacement, Seasonal startup/shutdown, General inspection. Oil-change fields include engine/runtime hours (a meter definition), oil type/grade, quantity, filter part number, notes.

Target ranges are optional per measurement ("pH target 7.2–7.8"); logging shows below / in / above range. **No treatment or dosing recommendations** are made in the MVP.

Seeding these profiles, their definitions and their default schedules when an asset is created from a category is `new-asset-templates`.

## Quick-entry UX

Scanning a tagged asset must allow `Hot Tub → Test Water` or `Mower → Oil Change` without a generic database form. #3's quick-action list is realised as profiles, not hard-coded buttons. Users can create custom event types and fields for equipment no starter profile covers.

## Moved out of this issue

- **"Structured readings must remain queryable as time series"** → moved to **#3** as a storage requirement, since it constrains the journal's schema and indexes, not the profile layer (D4 §5).
- **Stock decrement from chemical additions** → behind **#15** (Phase 6). Consumable usage is recorded through #3's `consumable_usage` regardless; `affects_stock` only does anything once #15 ships, and the profile layer must not depend on inventory being enabled.

## Acceptance criteria (from the original issue, kept)

1. A hot-tub user can configure the water metrics they care about, scan the tub, log a water test plus chemicals and amounts, and later retrieve those values as structured history.
2. A mower user can scan the equipment, log an oil change with engine hours / oil / filter details, and have that event advance the associated maintenance schedule.
3. Neither workflow requires a hot-tub-specific or mower-specific database table.
4. Applying a template twice is idempotent (`TemplateApplierTest`).

## Visual design

D12 (Apollo Service Binder) §8 Service Ledger, §9 Instrument Measurement — a profile's entry form and its saved readings render in the standard measurement presentation (large value, small unit, explicit status, reference interval), not a per-profile layout. Acceptance: range feedback is **LOW / IN RANGE / HIGH** wording plus icon plus the configured range, never colour alone.

## Design references

D2 §1 row 13 · D2 §5 · D3 §6 (data-driven vs compiled) · D4 §6 (definitions, ranges, `is_meter`), §7 (profiles) · D5 §5 (completion hook) · D7 Phase 2 · D8 risk 7 · testing doc §3.
