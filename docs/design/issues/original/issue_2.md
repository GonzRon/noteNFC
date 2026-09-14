# Issue #2: [MVP] Add asset records and NFC tag binding
state=open created=2026-09-13T23:26:50Z updated=2026-09-13T23:26:50Z labels= milestone=none comments=0

Implement the foundational asset model and bind NFC tags to assets.

## Requirements
- Create/edit/archive an asset.
- Fields: name, category, manufacturer, model, serial number, purchase/in-service date, notes.
- Generate a stable internal asset identifier.
- Write that stable identifier to an NFC tag using noteNFC's existing external-record approach.
- Scan a bound tag and resolve directly to the corresponding asset.
- Allow replacing/rebinding a lost or damaged NFC tag without losing history.
- Detect unknown/unbound tags and offer to bind them.

## Technical direction
Replace the current Joplin-specific `SharedPreferences` lookup as the primary persistence mechanism with a proper local database (Room is the natural Android choice). Keep compatibility/migration for existing Joplin mappings.

The tag should contain identity only. Mutable metadata and maintenance records remain in the app database.

