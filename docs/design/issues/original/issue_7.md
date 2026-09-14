# Issue #7: [NEXT] Add photos, receipts, manuals, specifications, and parts references to assets
state=open created=2026-09-13T23:27:20Z updated=2026-09-13T23:55:03Z labels= milestone=none comments=0

Add richer asset documentation after the maintenance MVP is stable.

## Ideas
- asset photo / label photo
- battery model and replacement part number
- consumables and common parts references
- receipt/warranty reference
- manual URL or attached document reference
- vendor/support URL
- free-form specifications
- serial/model plate photos
- installation/purchase records

## Useful examples
For a UPS:
- battery chemistry
- quantity
- voltage/Ah
- replacement battery SKU
- installation date
- warranty

For a mower/power equipment:
- oil type/capacity
- oil-filter part number
- air-filter part number
- spark-plug type/gap
- belt/blade part numbers

For a hot tub:
- filter model/part number
- tub volume/capacity
- sanitizer system/type
- cover model
- pump/heater model references
- water-care/manual links

## Relationship to supplies tracking
This issue owns the **reference/documentation** side of parts and consumables: `what is the correct part/product/specification for this asset?`

#15 owns optional **quantity-on-hand / low-stock** tracking: `do I currently have enough of it?`

A single part/consumable record should eventually be reusable by both features rather than duplicating product identity.

## Goal
Scanning the equipment should surface the maintenance information normally scattered across labels, stickers, receipts, manuals, browser bookmarks, and notes, without requiring the user to remember a model/part number before searching for it.

