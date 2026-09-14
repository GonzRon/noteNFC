---
action: rewrite
number: 10
title: "[NEXT] Add two-way Todoist synchronization and completion reconciliation"
milestone: "Phase 5 — Todoist"
labels: []
---

> Revised 2026-09-14 per the approved design package (`docs/design/`, review 1). Original text is preserved in this issue's edit history and in `docs/design/issues/original/issue_10.md`. Design references: D2 §1 (row 10), D2 §3 (C3), D2 §5; D3 §8; D4 §5, §10; D7 Phase 5.

Kept for the **sync engine**. Two review-1 additions; one section moved out.

## Goal

Reconcile actions taken in Todoist back into noteNFC without making Todoist authoritative for maintenance state. Ships in the same Phase 5 slice as #9.

Depends on #9.

## Pull-based synchronization (unchanged)

- Periodic and on-launch pull sync; no server component is introduced for this feature.
- On launch (foreground, throttled to 15 minutes), a `PeriodicWorkRequest` every 6 hours with a network constraint, and a manual "Sync now" (D3 §8).
- Persist provider revision state so syncs are incremental: `sync_token` for items, an activity-log cursor for completions (`integration_account.sync_cursor`, D4 §10).

## Completion detection — two paths (added in review 1)

The original issue assumed a completed task is `checked = true`. That is only true for `MANAGED_OCCURRENCE` tasks.

- **`MANAGED_OCCURRENCE`** — incremental sync returns the item with `checked = true` and `completed_at`.
- **`NATIVE_RECURRING`** — completing a recurring task in Todoist **keeps the same task id and advances its date**; the task never appears as checked. Completions are read from `GET /api/v1/activities?object_event_types=["item:completed"]`, cursor-paginated since the last consumed event, and from `completed_info`. Each activity event is consumed exactly once via `reminder_projection.last_remote_completion_ref`.

Either path maps the task back to its schedule and creates a local event with `source = TODOIST_SYNC` and `source_ref = <task id>:<activity event id>`; `UNIQUE(source, source_ref)` on `asset_event` is what makes replay safe (D4 §5).

## Reconciliation of Todoist's post-completion date (added in review 1)

After **every** completion, from either side, Todoist's resulting `due.date` is compared with noteNFC's `computed_due_on`. If they differ, noteNFC issues `SYNC_DUE` (`item_update` with the canonical date; the recurrence string is left unchanged). noteNFC never adopts Todoist's date. Repeated divergence raises `PROJECTION_DUE_DRIFT` (`new-reminder-health`).

This matters because Todoist recomputes a recurring task from the completion date whenever the task was manually rescheduled before completion — documented behaviour that would otherwise silently convert a series.

## Interpretation of other remote changes (D3 §8)

| Remote change | Local effect |
|---|---|
| remote `due.date` differs from the projection, **no** completion | **postpone current occurrence** to that date; flag `EXTERNALLY_MODIFIED` |
| remote `due.date` differs **after** a completion | `SYNC_DUE` back to canonical |
| remote recurrence string edited | overwritten on the next reconcile; never adopted |
| `is_deleted` | `MISSING` finding; Repair recreates once |
| content edits | ignored |
| both sides changed since the last successful sync | `CONFLICT`, surfaced for the user to resolve — never guessed |

Remote content is treated as data: displayed as text, never linkified inside noteNFC, never interpreted as a command (security doc, "Todoist content").

## Idempotency (unchanged, made concrete)

Repeated syncs must not create duplicate events, repeatedly advance a schedule, or recreate deleted tasks. Mechanisms: command `uuid` per outbox op, `UNIQUE(source, source_ref)` on events, `last_remote_completion_ref` on the projection, `content_hash` to skip no-op updates, `UNIQUE(provider, schedule_id)` against duplicate tasks.

## Visibility (unchanged)

Per schedule: in sync / awaiting sync / externally modified / awaiting maintenance details / conflicted.

## Moved out of this issue

**Simple vs rich completion text** → merged into **#4** as the schedule property `completion_mode` (`QUICK | FORM`). This issue keeps only the consequence: an externally-originated completion of a `FORM` schedule creates the event with `details_pending = 1` and prompts the user to finish the record; it must not silently fabricate a complete maintenance record (D4 §5, §8).

## Webhooks

Still a later optimisation only. No backend is added to obtain them.

## Acceptance criteria

1. Completing a `NATIVE_RECURRING` task in Todoist produces exactly one local event after two consecutive syncs.
2. Completing a `MANAGED_OCCURRENCE` task in Todoist produces one local event and the task is re-dated by the next sync.
3. Rescheduling a task in Todoist without completing it postpones the current occurrence and does not change the recurrence rule.
4. Editing the recurrence string in Todoist is overwritten on the next reconcile.
5. A simulated crash between outbox write and ack recovers without duplicating the remote command.
6. Both sides changed since the last sync → a conflict is surfaced, not resolved automatically.

## Design references

D2 §1 row 10 · D2 §3 C3 · D2 §5 (merge `completion_mode` into #4; add activity-log detection and canonical verification) · D3 §8 (interpretation table, idempotency, rate limits) · D4 §5 (`source`, `source_ref`, `details_pending`), §10 (`reminder_projection`, `provider_op`, `integration_account`) · D7 Phase 5 · D8 spike S6, ledger A13, A20 · testing doc §3 (`TodoistInterpretationTest`) · `11-review-1-changes.md` §2.
