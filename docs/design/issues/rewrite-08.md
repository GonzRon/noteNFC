---
action: rewrite
number: 8
title: "[MVP] Add backup/export/import for local maintenance data"
milestone: "Phase 1 — Tag survival (M1)"
labels: []
---

> Revised 2026-09-14 per the approved design package (`docs/design/`, review 1). Original text is preserved in this issue's edit history and in `docs/design/issues/original/issue_8.md`. Design references: D2 §1 (row 8), D2 §5; D3 §12; D4 §12; D6 §2; D7 Phase 1A; security doc ("Backups"); D8 ruling R-5.

**Re-tiered from NEXT to MVP-1** and retitled `[MVP]`. Reason: physical NFC tags outlive phones, and the installed release APK is signed with a certificate that is not in the repository, so the first install of the new app is an uninstall + reinstall that deletes the legacy mapping (D1 §6, D6 §2). Backup is the tag-survival mechanism, so it ships in the first milestone rather than after it.

## Goal

A durable, portable escape hatch for the local-first database, present from the first release.

## Format (D3 §12)

A ZIP:

```
manifest.json    { format_version, app_version, schema_version, created_at, device, table_counts, sha256(data.json) }
data.json        every canonical table + reminder_projection
attachments/<attachment-id>.<ext>   managed bytes only (references are metadata)
```

- IDs are preserved verbatim; dates as ISO strings; no derived tables (they are rebuilt after import).
- Canonical tables per D4 §12: `asset`, `nfc_tag`, `external_link`, `measurement_definition`, `event_profile`, `profile_field`, `profile_consumable`, `asset_event`, `measurement`, `consumable_usage`, `maintenance_schedule`, `schedule_provider`, `supply_item`, `asset_supply`, `stock_ledger`, `attachment`, plus `reminder_projection` so a restore can re-link Todoist tasks. Each table joins the export in the phase that introduces it; the importer always tolerates older exports (D7 cross-phase rules).
- This closes the original issue's omission: profiles, seasons, projections, supplies and attachments were missing from its export list.

## Additions to the original requirements

### Format versioning

`format_version` in the manifest. Older formats pass through `BackupUpgrader` steps before load; **newer formats are refused** with a clear message rather than partially parsed. `schema_version` is informational. A manifest `sha256` mismatch against `data.json` is detected and refused.

### Replace vs Merge (the "conflict handling" requirement, made concrete)

- **Replace** — wipe and load in one Room transaction; attachments written after commit. A failure leaves the previous data intact because the transaction rolls back.
- **Merge** — by ID: missing rows inserted; existing rows kept unless the import's `updated_at` is newer; conflicts are **reported, never guessed**.

After either mode: `rebuild` for every schedule, `reconcile` for every provider, then a health check.

### Automatic snapshots

Same format, app-private, keep the last 5. Taken before every destructive operation (asset delete, Replace import, legacy migration) and weekly. Android Auto Backup is kept as a best-effort extra under the 25 MB cap, with `dataExtractionRules` excluding the secret file — but it is signature-bound and therefore cannot be relied on here (D6 §2).

### Attachment bundle

Managed bytes travel inside the ZIP; referenced documents export as metadata only.

### Secrets are excluded

No tokens, ever (security doc; ruling R-5). Preferences are excluded except the reminder hour and unit choices. Export shows an explicit warning that the file contains serial numbers, purchase prices, locations and receipts. Backups are **unencrypted by default** with an optional passphrase as a NEXT item, because an encrypted backup with a forgotten passphrase defeats the purpose of tag survival (ruling R-5, ledger A16).

### Delivery

Export via `ACTION_CREATE_DOCUMENT` (any SAF provider, cloud included), an optional weekly auto-backup folder via `ACTION_OPEN_DOCUMENT_TREE`, and "share backup" through the share sheet. The UI shows a "no backup yet" nudge on Home until the first export succeeds (D7 Phase 1C).

## Acceptance criteria

1. Importing a backup produced by this build into an empty install yields identical `nfc_tag` / `asset` / `external_link` rows — byte-equal `data.json` after canonical ordering (D7 Phase 1A).
2. A tag written on phone X resolves on phone Y after restoring X's backup, entirely through the UI (D7 milestone M1).
3. A backup whose `format_version` is newer than the app's is refused with a clear message.
4. A Replace import that fails midway leaves the previous data intact.
5. A Merge import reports conflicts instead of resolving them silently.
6. No export contains a token.

## Design references

D2 §1 row 8 · D2 §5 (re-tier) · D3 §12 (backup, export, import) · D4 §12 (canonical vs derived) · D6 §2 (why the reinstall situation exists) · D7 Phase 1A, 1C · security doc, "Backups" · D8 ruling R-5, ledger A16.
