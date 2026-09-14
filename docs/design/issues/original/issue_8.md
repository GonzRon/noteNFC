# Issue #8: [NEXT] Add backup/export/import for local maintenance data
state=open created=2026-09-13T23:27:26Z updated=2026-09-13T23:27:26Z labels= milestone=none comments=0

Add a durable escape hatch for the local-first database.

## Requirements
- Export all assets, schedules, maintenance history, and external-link metadata.
- Human-readable structured format where practical (JSON and/or CSV bundle).
- Import/restore with duplicate/conflict handling.
- Manual backup to Android document storage.
- Preserve stable asset IDs so existing NFC tags continue resolving after restore.

## Why this matters
The physical NFC tags will outlive phones. A phone replacement, app reinstall, or storage failure must not orphan every tag or erase years of maintenance history.

Cloud synchronization can be considered later; reliable portable backup should come first.

