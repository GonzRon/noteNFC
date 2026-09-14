---
action: rewrite
number: 2
title: "[MVP] Room persistence and the Asset entity (Phase 1A)"
milestone: "Phase 1 — Tag survival (M1)"
labels: []
---

> Revised 2026-09-14 per the approved design package (`docs/design/`, review 1). Original text is preserved in this issue's edit history and in `docs/design/issues/original/issue_2.md`. Design references: D2 §1 (row 2), D2 §3 (C1), D2 §5; D3 §5; D4 §3, §4; D6 §3; D7 Phase 1A.

Narrowed. This issue is now **persistence and the Asset entity only**. Tag encoding and tag UX were split out (D2 §5).

## Goal

Replace the Joplin-specific `SharedPreferences` lookup as the primary persistence mechanism with Room 3.0 as the sole canonical store, and introduce the `asset` entity.

## Scope

- Room 3.0.x (`androidx.room3`) from the first schema, KSP 2, exported schema JSON committed, the bundled `androidx.sqlite` driver so DAO tests run on the JVM (D3 §5, D3 §15, ledger A18; review-1 correction — Room 3.0 replaces 2.8 as the starting line, with 2.8.5 only as the spike S1 fallback).
- `AppDatabase` v1 with `asset`, `nfc_tag`, `external_link` (the minimal Phase 1A set; the full `asset` column list lands in Phase 2 per D7).
- `asset`: create / edit / archive. Columns per D4 §4 — name, description, category, `template_key`, manufacturer, model, serial number, purchase/in-service date, vendor, location, warranty, notes, `status` (`ACTIVE | ARCHIVED | RETIRED`), `parent_asset_id`, season window, audit timestamps. UUID text primary keys (ledger A2).
- `LegacyPrefsMigration`: on first launch, if `noteNFCURLs` exists, import every entry as a standalone `external_link` plus a `LEGACY_MD5` `nfc_tag` row, inside one transaction, after snapshotting the prefs file; the prefs file is left untouched for two releases; a one-time migration report is shown (D6 §3).
- Repository ports defined in `:core` (`AssetRepository`, and the tag/link repositories), Room implementations in `:app` (D4 §14).
- Lifecycle semantics per D4 §13: archive-first, hard delete behind typed confirmation with an automatic pre-delete snapshot, `RESTRICT` on parents with children (ruling R-9).

## Tag identity is not asset identity

The original text said "write that stable identifier to an NFC tag". **That is replaced.** A tag carries a *tag* identity; a separate `nfc_tag` row binds that identity to an asset or to a standalone link (D4 §3, ruling R-2, ledger A3). This resolves contradiction C1 in D2 §3: the original issue asked both for the asset id on the tag *and* for a lost tag to be revocable, which the binding table makes possible (old row → `LOST`, new row created) and the asset-id-on-tag scheme does not.

The binding table also gives: multiple tags per asset, pre-written `UNBOUND` tags bound on first scan, standalone link tags as first-class targets, and retargeting a tag without rewriting it.

## Split out of this issue

- `new-tag-payload-v1-legacy-resolver` — the NDEF record format, the codec, and the resolver for both payload formats.
- `new-tag-bind-rebind-ux` — bind, rebind, revoke, unknown-tag and re-link flows.

## Acceptance criteria

1. Assets can be created, edited and archived; archived assets keep their history and stay out of the default list.
2. A fixture `noteNFCURLs` file migrates into `external_link` + `LEGACY_MD5` `nfc_tag` rows whose `payload_key` equals the fixture keys, and the prefs file is left byte-identical (D7 Phase 1A exit criterion 2).
3. The exported Room schema JSON is committed and a no-op migration test passes.
4. DAO tests run as plain JVM tests via the bundled driver.

## Visual design

D12 (Apollo Service Binder) §8 Asset Identity Plate, §11 NFC states — an unknown tag is **not an error** and a legacy tag reads as a **migration opportunity**, not damaged data. The asset screen opens with the identity plate structure (asset type, manufacturer/model, friendly name, serial and NFC id in the technical face). Acceptance: asset and tag surfaces use the semantic tokens from `new-design-system-foundation`, never raw colours, and remain identifiable in grayscale.

## Design references

D2 §1 row 2 · D2 §3 C1 · D2 §5 (split) · D3 §5 (persistence) · D4 §3 (`nfc_tag`, why tag identity ≠ asset identity) · D4 §4 (`asset`, `external_link`) · D4 §13 (lifecycle) · D4 §14 (aggregates and repositories) · D6 §3 (automatic migration) · D7 Phase 1A · D8 rulings R-2, R-9.
