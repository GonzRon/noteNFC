---
action: create
title: "[NEXT] Todoist deep-link actions into noteNFC"
milestone: "Phase 5 — Todoist"
labels: []
---

Split from #11 (D2 §5: "#11 (local quick actions · fatigue controls · Todoist link actions)"). Ships with #9 in Phase 5.

## Goal

Get from a Todoist task to the exact maintenance operation in noteNFC in one tap, given that noteNFC cannot inject its own buttons into Todoist's notifications.

## The constraint

Todoist owns its notification UI. noteNFC cannot add domain-specific action buttons to it. The only mechanism available is a **link in the task** — which is why the `notenfc://` contract is foundational and was pulled out of #9 into `new-deeplink-contract`.

## Scope

- Every projected task's description carries a `notenfc://schedule/<id>` link, plus a one-line "Open noteNFC → Reminders" fallback in case custom-scheme links are not tappable in Todoist for Android (spike S3).
- Where practical, offer `Open asset`, `Open checklist` (the schedule's completion form) and `Complete maintenance` as link targets from the task context. All of them are **navigation**: the completion itself is a tap inside noteNFC, never something a URL performs (security doc, "Deep links").
- An **`Open in Todoist`** action inside noteNFC for schedules that have a live projection, in the opposite direction.
- When maintenance is completed in noteNFC, the Todoist representation is synchronised through #9 and #10; this issue adds no completion semantics of its own.

## Dependencies and the S3 outcome

Spike S3 asks whether `notenfc://…` — plain, and as a Markdown link — is tappable in Todoist for Android. If it is not, the description fallback carries the weight and the case for HTTPS App Links (ruling R-4) strengthens; the projection content changes, nothing else does.

## Out of scope

Local notification quick actions are **#11**. The four operations' semantics are **#4**. The projection itself is **#9**.

## Acceptance criteria

1. Every projected task contains a working link to its schedule plus the textual fallback.
2. Tapping the link opens the schedule in noteNFC, or — when the schedule no longer exists — lands on Home with a toast.
3. No link target completes, snoozes or postpones anything; every mutation still requires an in-app tap.
4. `Open in Todoist` appears only on schedules with a live projection and opens the correct task.
5. S3's result is recorded and the description format reflects it.

## Design references

D2 §5 (split of #11) · D3 §8 (task content), §13 (deep links) · D7 Phase 5 · D8 spike S3, ruling R-4, risk 6 · security doc, "Deep links".
