# Issue #3: [MVP] Add asset event journal with structured measurements and consumable usage
state=open created=2026-09-13T23:26:55Z updated=2026-09-13T23:53:33Z labels= milestone=none comments=0

## Goal
Replace a maintenance-only free-text history with a generic, append-oriented **asset event journal** that can capture maintenance, inspections, measurements, treatments, tests, and failures without requiring a special database schema for every asset type.

This is foundational for two primary noteNFC use cases:
- hot-tub water testing / chemical treatment history
- lawn mower / generator / power-equipment maintenance such as oil changes and runtime-based service

## Core model
Use a general `AssetEvent` concept rather than assuming every recorded occurrence is strictly maintenance.

Conceptually:

```text
AssetEvent
  id
  asset_id
  event_type
  occurred_at
  notes
  cost (optional)
  source
  created_at / updated_at

  measurements[]
  consumables[]
  attachments[]   # attachment implementation may be delivered by #7
```

Possible event types include:
- maintenance
- inspection
- measurement/test
- treatment
- failure/incident
- custom

These may be represented by one table + typed payloads rather than separate tables; implementation detail is open.

## Structured measurements
An event can contain zero or more structured measurements rather than burying readings in free text.

Each measurement should support at minimum:
- metric/key
- display label
- numeric or text value as appropriate
- unit
- optional target/range metadata supplied by the event/profile layer

### Hot-tub example

```text
Water Test — 2026-09-13

pH                    7.3
Free chlorine         2.1 ppm
Total chlorine        2.5 ppm
Total alkalinity      90 ppm
Calcium hardness      180 ppm
Water temperature     102 °F
```

Additional chemistry fields such as bromine/CYA/etc. should be configurable rather than globally mandatory.

### Other examples
UPS:
- battery voltage
- runtime minutes
- load percentage
- output voltage

Generator / mower:
- engine hours
- output voltage/frequency where applicable
- oil level or other readings

RO / water system:
- feed pressure
- tank pressure
- input TDS
- output TDS

## Consumables / quantities used
An event can contain zero or more consumed items with quantity + unit.

Examples:

Hot tub:
```text
Chlorine granules      1.0 oz
pH reducer             0.5 oz
Alkalinity increaser   1.5 oz
```

Mower / generator:
```text
Synthetic oil          2.0 qt
Oil filter             1 ea
```

RO:
```text
Sediment cartridge     1 ea
Carbon cartridge       2 ea
```

Consumable usage recorded here should be compatible with a future lightweight supply/stock tracker; the event journal must not depend on inventory being enabled.

## Maintenance/service logging
Preserve the original #3 behavior:
- from an asset screen, record an event in a few taps
- show newest-first history
- allow correcting an event while preserving created/updated timestamps
- quick actions such as `Battery replaced`, `Oil changed`, `Filter changed`, `Inspected`, `Cleaned`, `Water tested`, `Chemicals added`, and `Custom`
- after saving an event, update any related maintenance schedule

## Hot-tub workflow acceptance example
A user scans the NFC tag on a hot tub, taps `Test water`, records pH/alkalinity/calcium/sanitizer readings, optionally records chemicals added and amounts, saves the event, and can later see the exact longitudinal history instead of parsing free-text notes.

## Power-equipment workflow acceptance example
A user scans a mower, taps `Oil change`, records current engine hours, oil type/quantity and filter used, saves the event, and the related time/usage maintenance schedule advances correctly.

## UX principle
The physical scan should get the user from the equipment to `record what I measured or did` with minimal friction. Structured data must not turn common logging into a long generic form; event/profile-specific quick-entry UI should be supported by follow-on/template work.

