# Issue #13: [MVP] Add configurable event/measurement profiles with hot-tub and power-equipment templates
state=open created=2026-09-13T23:53:51Z updated=2026-09-13T23:53:51Z labels= milestone=none comments=0

## Goal
Build a configurable profile/template layer on top of the generic asset-event journal in #3 so common assets can have fast, purpose-built data-entry screens without hard-coding one schema per asset type.

Hot-tub water care and lawn/power-equipment maintenance are primary validation use cases for this feature.

Depends on: #3
Related: #4

## Profile concept
A profile defines:
- event/action name
- event type
- structured fields/measurements to collect
- units
- optional target/range metadata
- optional consumables that are commonly used
- optional linkage to one or more maintenance schedules
- display/order/required-vs-optional metadata

Profiles should be editable/configurable by the user. Built-in templates are starter defaults, not mandatory global schemas.

## Hot-tub profile
Provide a starter `Hot Tub` asset profile with quick actions such as:
- Test water
- Add chemicals / balance water
- Shock / sanitize
- Clean/rinse filter
- Deep-clean/replace filter
- Drain/refill
- Open/close for season

### Water-test event
Configurable measurement fields should include common examples such as:
- pH
- free chlorine and/or bromine
- total chlorine where relevant
- total alkalinity
- calcium hardness
- cyanuric acid where relevant
- water temperature
- optional free-form/custom measurements

Do not force chlorine-specific fields on a bromine user, or vice versa. The user must be able to enable/disable fields and change units/labels where sensible.

### Target ranges
Allow optional user-configured acceptable ranges per measurement.

Example:
```text
pH                  target 7.2–7.8
Total alkalinity    target 80–120 ppm
```

When logging a reading, show whether it is below/in/above the configured range, but do not make treatment/dosing recommendations automatically in the MVP.

### Chemical-treatment event
Allow one event to pair test results with zero or more chemical additions, for example:

```text
pH:                  7.8
Free chlorine:       0.8 ppm
Alkalinity:          110 ppm

Added:
Chlorine granules    1.0 oz
pH reducer           0.5 oz
```

Chemical additions should use #3's generic consumable-usage model and optionally decrement lightweight stock if the supplies feature is enabled.

## Lawn mower / generator / power-equipment profile
Provide starter quick actions such as:
- Oil change
- Oil/filter change
- Air-filter service
- Spark-plug service
- Blade/belt inspection or replacement
- Seasonal startup/shutdown
- General inspection

Oil-change logging should support structured fields such as:
- engine/runtime hours
- oil type/grade
- oil quantity
- oil filter / part number
- notes

This profile must work with #4's time/usage schedules such as:

```text
Change oil every 50 engine hours OR annually,
whichever comes first.
```

## Quick-entry UX
The value of profiles is speed.

Scanning an NFC-tagged asset should allow:

```text
Hot Tub -> Test Water
```

or:

```text
Mower -> Oil Change
```

without presenting a generic database form.

Users should still be able to create custom event types and fields for equipment not covered by built-in starter profiles.

## Longitudinal history
Structured readings must remain queryable as time series so future UI can show trends such as pH over time, UPS runtime degradation, or generator runtime/service intervals.

Charts/analytics beyond a basic history view are not required by this issue; the important requirement is to preserve the structured data needed for them.

## Acceptance criteria
1. A hot-tub user can configure the water metrics they care about, scan the tub, log a water test plus chemicals/amounts, and later retrieve those values as structured history.
2. A mower/power-equipment user can scan the equipment, log an oil change with engine hours/oil/filter details, and have that event advance the associated maintenance schedule.
3. Neither workflow requires a hot-tub-specific or mower-specific database table.

