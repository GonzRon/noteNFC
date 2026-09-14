# Issue #15: [NEXT] Add lightweight supplies/consumables tracking with low-stock reminders
state=open created=2026-09-13T23:54:22Z updated=2026-09-13T23:54:22Z labels= milestone=none comments=0

## Goal
Add a deliberately lightweight stock view for maintenance consumables so noteNFC can answer `what do I need to keep on hand for this equipment?` without becoming a full inventory/ERP system.

Primary examples:
- hot-tub chemicals
- mower/generator oil, filters, spark plugs
- UPS batteries
- RO/filter cartridges

Related: #3 structured consumable usage, #7 parts/documentation

## Scope
A supply item should support:
- name
- category
- optional manufacturer/SKU/part number
- preferred unit (oz, lb, qt, ea, etc.)
- approximate quantity on hand
- optional low-stock threshold
- optional preferred/reorder quantity
- optional vendor/purchase URL/reference
- optional notes
- assets/event profiles that commonly use the item

## Rough stock is acceptable
The feature should explicitly support approximate inventory rather than requiring transaction-grade precision.

Examples:

```text
Hot-tub chlorine granules
On hand: ~18 oz
Low-stock threshold: 8 oz
```

```text
Kohler oil filter 52-050-02-S
On hand: 2 ea
Low-stock threshold: 1 ea
```

The user should be able to correct stock manually at any time without reconciling every historical event.

## Event integration
When #3 records a consumable usage quantity, optionally decrement the linked supply item.

Example:

```text
Water treatment event
Chlorine granules: 1.0 oz used
```

can reduce the approximate on-hand amount by 1 oz.

This behavior should be optional/configurable because users may not log every usage precisely.

## Reorder / low-stock reminder
When an item falls at or below its threshold:
- show it on a `Supplies needed` view
- optionally create a local or Todoist-backed reminder/task using the same reminder-provider abstraction as maintenance
- do not repeatedly create duplicate reorder tasks
- clear/resolve the low-stock state when stock is replenished

## Asset linkage
From an asset, make it easy to see its common supplies/parts.

Examples:

Hot tub:
- sanitizer
- pH increaser/decreaser
- alkalinity increaser
- filter cleaner
- replacement filter

Mower:
- oil
- oil filter
- air filter
- spark plug
- blades/belts

UPS:
- battery model/quantity

## Distinguish reference vs stock
A part/consumable can be useful even when inventory tracking is disabled.

For example, `Kohler 52-050-02-S` may simply be the correct oil-filter part number recorded on the mower. If stock tracking is enabled for that item, noteNFC may additionally know that two are currently on hand.

## Explicitly out of scope
- purchase orders
- accounting/cost valuation
- barcode warehouse management
- multi-location inventory reconciliation
- automatic online ordering
- exact lot/serial tracking

## Acceptance criteria
1. A hot-tub user can see which chemicals/supplies they normally use and roughly how much remains.
2. Logging a chemical addition can optionally decrement the related supply.
3. Low stock can produce one actionable reminder without notification spam.
4. A mower owner can quickly see both the correct maintenance part number and whether a spare is on hand.

