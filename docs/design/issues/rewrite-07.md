---
action: rewrite
number: 7
title: "[NEXT] Add photos, receipts, manuals, specifications, and parts references to assets"
milestone: "Phase 4 — Attachments"
labels: []
---

> Revised 2026-09-14 per the approved design package (`docs/design/`, review 1). Original text is preserved in this issue's edit history and in `docs/design/issues/original/issue_7.md`. Design references: D2 §1 (row 7), D2 §5; D3 §11; D4 §11; D7 Phase 4; security doc ("Attachments").

The original "Ideas" list is converted into scope. Tier stays NEXT; execution order is Phase 4, the **first** post-MVP slice (ruling R-6), and the data model plus the storage-provider boundary are decided now rather than later (D2 §1 row 7).

## Goal

Scanning the equipment surfaces the maintenance information normally scattered across labels, stickers, receipts, manuals, bookmarks and notes, without needing to remember a model or part number first.

## Scope

### (a) Attachment metadata + LOCAL and SAF-tree providers

`attachment` rows (D4 §11): exactly one of `asset_id` / `event_id`; `kind` (`PHOTO | LABEL_PHOTO | RECEIPT | MANUAL | WARRANTY | DOCUMENT | OTHER`); `mode`; `display_name`, `mime_type`, `size_bytes`, `sha256`; `storage_provider`; `storage_locator`; `captured_on`, `notes`.

Bytes are **never** stored in Room. They go through the `AttachmentStore` port (D3 §11) with two implementations for this phase: `LocalAttachmentStore` (`filesDir/attachments/…`, always available) and `SafTreeAttachmentStore` (a tree the user picked with `ACTION_OPEN_DOCUMENT_TREE`, permission persisted). Locators are provider-relative (`assets/<asset-id>/<attachment-id>.<ext>`), so the store root is one setting and "move attachments to another store" is a copy loop over rows, not a schema change. The UI must handle providers that cannot host a folder and fall back.

### (b) Referenced (non-copied) documents

`mode = REFERENCE` keeps a persisted single-document `content://` URI (`storage_provider = SAF_DOCUMENT`). The bytes are never copied; the file stays wherever the user keeps it. Persisted URI grants are capped at 512 per package — references count one each and the UI warns near the cap. Reference grants are listed and revocable in settings.

### (c) Structured specification fields

The reference/documentation side of parts and specifications: battery chemistry, quantity, voltage/Ah, replacement SKU, oil type and capacity, filter part numbers, spark-plug type and gap, belt and blade numbers, filter model, tub volume, sanitizer system, cover and pump/heater model references, vendor/support URL, warranty reference, installation and purchase records.

### (d) Backup inclusion

Managed bytes are included in the backup ZIP under `attachments/`; references are exported as metadata only and are listed as "not available on this device" after a restore onto another phone (D3 §12).

## What this issue does not own

The shared **part/supply entity** belongs to **#15**. #15 owns `supply_item` identity ("what is the correct part") as well as stock ("do I have enough"); this issue links to it and must not create a second product-identity table. Until #15 ships, specification fields here are asset-scoped documentation.

## Acceptance criteria (D7 Phase 4)

1. `attachment` rows are unchanged after switching the store from LOCAL to a SAF tree and migrating the bytes (locators unchanged).
2. A referenced cloud PDF still opens after a reboot.
3. A restore on a second phone restores managed photos and lists references as "not available on this device".
4. The store contract test suite passes against both store implementations (put / open / delete / exists round-trip, locator relativity).

## Design references

D2 §1 row 7 · D2 §5 (convert ideas into scope; assign the shared part entity to #15) · D3 §11 (`AttachmentStore`, storage-provider boundary) · D3 §12 (backup bundle) · D4 §11 (`attachment`) · D7 Phase 4 · security doc, "Attachments" · D8 ruling R-6.
