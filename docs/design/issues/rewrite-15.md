---
action: rewrite
number: 15
title: "[NEXT] Add lightweight supplies/consumables tracking with low-stock reminders"
milestone: "Phase 6 — Supplies"
labels: []
---

> Revised 2026-09-14 per the approved design package (`docs/design/`, review 1). Original text is preserved in this issue's edit history and in `docs/design/issues/original/issue_15.md`. Design references: D2 §1 (row 15), D2 §5; D4 §5, §9, §13; D7 Phase 6; D8 ruling R-6.

Kept as NEXT; execution order is **Phase 6**, the last of the three post-MVP slices (ruling R-6). Two things are made explicit: this issue **owns the shared part/supply entity**, and the stock ledger is specified.

## Goal

A deliberately lightweight stock view for maintenance consumables — "what do I need to keep on hand for this equipment?" — without becoming an inventory or ERP system.

## This issue owns the shared part/supply entity

#7 owns the **reference/documentation** side ("what is the correct part for this asset?") and #15 owns **quantity on hand** ("do I have enough?"), but a single product identity must serve both rather than being duplicated. That identity is **`supply_item`, defined here** (D2 §1 rows 7 and 15, D2 §5). #7 links to it; it does not create a second table.

`supply_item` (D4 §9): `name`, `category`, `manufacturer`, `sku`, `preferred_unit`, `track_stock` (0 = reference only), `low_stock_threshold`, `reorder_quantity`, `vendor_url`, `notes`, `qty_on_hand_cache` + `qty_as_of_at` (derived), `archived_at`.

`asset_supply` answers "what part belongs to this asset" with `role` ("Oil filter", "Sanitizer"), `UNIQUE(asset_id, supply_id, role)` — and works even when `track_stock = 0`. That is exactly the "Kohler 52-050-02-S is the correct oil filter, and I happen to have two" distinction the original issue asked for.

## The stock ledger (specified)

Stock is **not** a mutable number on the supply. It is `stock_ledger` (D4 §9):

| Column | Notes |
|---|---|
| `supply_id` FK CASCADE | |
| `kind` | `COUNT` (absolute: "I have ~18 oz") or `DELTA` (relative: −1.0 usage, +32 purchase) |
| `amount`, `unit` | |
| `usage_id` FK `consumable_usage` **CASCADE**, nullable | |
| `note`, `recorded_at` | |

- **Quantity on hand = the latest `COUNT` plus every `DELTA` after it.** `qty_on_hand_cache` is a derived cache of that.
- **Manual correction is a new `COUNT`.** No historical reconciliation is ever required, which is what makes "rough stock" honest rather than sloppy.
- **Reversal is automatic.** The `usage_id` cascade means deleting or editing the event that consumed the item removes its ledger delta; nothing needs to be un-applied by hand (D4 §13).

Decrementing from #3's `consumable_usage` stays optional and configurable per the original issue; `consumable_usage.affects_stock` records whether a usage produced a ledger entry.

## Low-stock reminder

Low stock is a **derived condition** (`track_stock = 1 AND qty < threshold`), not a stored flag. It becomes a reminder subject for the provider layer (`new-reminder-provider-interface`), with the same deduplication rule as schedules, so two consecutive digest runs produce one notification and not two. Low-stock reminders use a single app-level provider preference rather than a per-supply table (D4 §8). The condition clears when stock is replenished. A "Supplies needed" view lists them.

## Out of scope (unchanged)

Purchase orders, accounting or cost valuation, barcode warehouse management, multi-location reconciliation, automatic online ordering, exact lot/serial tracking.

## Acceptance criteria

1. A hot-tub user can see which chemicals they normally use and roughly how much remains.
2. Logging 1 oz of chlorine decrements the supply exactly once; deleting the event restores it.
3. Two consecutive digest runs produce one low-stock notification, not two.
4. A manual `COUNT` overrides history without touching any event.
5. A mower owner can see both the correct oil-filter part number and whether a spare is on hand, whether or not stock tracking is enabled for that item.

## Design references

D2 §1 rows 7 and 15 · D2 §5 (assign the shared part entity to #15; specify the ledger) · D4 §5 (`consumable_usage`), §9 (`supply_item`, `asset_supply`, `stock_ledger`), §13 (deletion semantics) · D7 Phase 6 · D8 ruling R-6 · testing doc §3 (`StockLedgerTest`).
