---
action: create
title: "[MVP] Asset templates that seed definitions, profiles, and default schedules"
milestone: "Phase 2 — Journal + profiles"
labels: []
---

Identified in D2 §4 item 14 (Data model: asset templates that pre-create profiles, measurement definitions, and default schedules when an asset is created from a category). Related to #13, which owns the profile model itself.

## Goal

Creating an asset from a category should produce a usable asset — its measurement definitions, its quick-entry profiles and its default schedules — in one step, without hard-coding any of them.

## Scope

### Templates are data (D4 §7, D3 §6)

Seed templates are **JSON in app assets**: `hot_tub`, `power_equipment`, `ups`, `generic`. Applying one creates `measurement_definition`, `event_profile`, `profile_field`, `profile_consumable` and default `maintenance_schedule` rows for that asset. `asset.template_key` records which template seeded it, and `event_profile.template_key` supports "reset to default" for an individual profile.

**After application the rows belong to the asset and the template is never consulted again.** A user editing their hot tub's fields is editing their own rows, not a global schema; a later app update that changes a template does not silently rewrite existing assets.

### What each starter template seeds

- **`hot_tub`** — definitions for pH, free chlorine, bromine, total chlorine, total alkalinity, calcium hardness, cyanuric acid and water temperature with their ranges and units; profiles for Test water, Add chemicals, Shock/sanitize, Clean/rinse filter, Deep-clean/replace filter, Drain/refill, Open/close for season; default schedules for water testing and filter cleaning.
- **`power_equipment`** — an `engine_hours` meter definition (`is_meter = 1`, `new-meter-model`) plus oil type/grade, quantity and filter part number; profiles for Oil change, Air-filter service, Spark-plug service, Blade/belt work, Seasonal startup/shutdown, General inspection; a default "every 50 engine hours OR 12 months" schedule.
- **`ups`** — battery voltage, runtime minutes, load percentage, output voltage; profiles for Load test and Battery replacement; default completion-relative load-test and battery-replacement schedules.
- **`generic`** — a minimal profile set with no domain assumptions.

### Idempotency

Applying a template twice must not duplicate anything (`TemplateApplierTest`, testing doc §3). The applier lives in `:core` and is pure: given a template and an asset, it returns rows.

### Boundary

The template format carries **no expressions, no conditionals and no computed fields** — the same hard boundary as profiles (D3 §6). It seeds data; the compiled generic entry form renders it. This is the guard against the profile layer growing into a form framework (D8 risk 7).

## Out of scope

The profile and definition model itself is **#13**. The meter definition semantics are `new-meter-model`. The schedule rule model is **#4**; templates only create schedule rows, they do not define recurrence behaviour.

## Visual design

D12 §8 Asset Identity Plate, §9 Instrument Measurement — a seeded profile must render in the standard measurement presentation (value, unit, explicit LOW / IN RANGE / HIGH status, reference interval), never a template-specific layout.

## Acceptance criteria

1. Creating an asset from `hot_tub` produces its definitions, profiles and default schedules in one transaction, and the asset is immediately usable for a water test.
2. Creating an asset from `power_equipment` produces an `engine_hours` meter definition and a working "50 hours OR 12 months" schedule.
3. Applying a template twice to the same asset changes nothing the second time.
4. Editing a seeded profile affects only that asset; creating a second asset from the same template still gets the original defaults.
5. No template contains an expression, a conditional or a computed field.
6. No hot-tub-specific or mower-specific table exists.

## Design references

D2 §4 item 14 · D3 §6 (data-driven vs compiled) · D4 §4 (`asset.template_key`), §6 (definitions), §7 (profiles, seed templates), §8 (default schedules) · D7 Phase 2 · D8 risk 7 · testing doc §3 (`TemplateApplierTest`) · D12 §8, §9.
