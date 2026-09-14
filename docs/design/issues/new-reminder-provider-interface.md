---
action: create
title: "[MVP] ReminderProvider port and reconcile contract"
milestone: "Phase 3 — Scheduling + local reminders"
labels: []
---

Split from #9 (D2 §5: "#4 (engine · local provider · provider interface)"; the interface itself is moved out of #9, which originally owned it, because the **local** provider needs it first).

## Goal

One provider-neutral port in `:core` that both the local reminder provider and the later Todoist provider implement, so maintenance-domain code never depends on a provider's API objects.

## Scope (D3 §7.1)

```kotlin
data class ReminderSubject(
    val key: SubjectKey,        // SCHEDULE(id) | SUPPLY(id)
    val title: String,
    val body: String,           // last done, meter state, notenfc:// link
    val dueOn: LocalDate?,      // effective due date; null for usage-only or parked
    val leadDays: Int,
    val state: SubjectState,    // ACTIVE | PARKED(reentryOn) | COMPLETED | WITHDRAWN
    val rule: RuleFacts?,       // basis, interval, unit, hasMeter, seasonal
    val contentHash: String,    // no-op detection
)

interface ReminderProvider {
    val id: ProviderId                                                      // LOCAL, TODOIST
    suspend fun reconcile(subjects: List<ReminderSubject>): ReconcileReport // idempotent
    suspend fun pullChanges(): List<RemoteChange>                           // LOCAL: empty
    suspend fun health(): List<HealthFinding>
}
```

### The `reconcile` contract

`reconcile` is the **whole write surface**. It receives the desired state of every subject the provider is responsible for and makes the provider match it. That makes both providers idempotent by construction: the local adapter re-arms its daily alarm and posts or clears notifications; the Todoist adapter diffs against `reminder_projection` and enqueues `provider_op` rows. There is no "create one reminder" call to get out of sync.

### `RuleFacts`

The subject carries the rule's *facts* — basis, interval, unit, whether a meter is involved, whether a season applies — so a provider can decide whether its own recurrence engine can carry the subject. This is exactly what the Todoist adapter's capability-based representation selection consumes (#9, D3 §8). The provider never sees the schedule entity itself.

### Subject sources

- Schedules, via `schedule_provider` rows (D4 §8): the use case builds one subject list per **enabled** provider. The MVP editor writes at most one enabled row per schedule, but the port and the tables permit several (ruling R-12, ledger A19) — the single-choice UI is `new-provider-selection-ux`.
- Supplies, in Phase 6: low stock becomes a `SUPPLY(id)` subject using a single app-level provider preference rather than a per-supply table (#15, D4 §9).

### `SubjectState`

`ACTIVE`; `PARKED(reentryOn)` for a seasonally inactive or paused schedule; `COMPLETED`; `WITHDRAWN` for archived or deleted. Parking is how #14's season semantics reach a provider without the provider knowing what a season is.

## Out of scope

The local implementation is `new-local-reminder-provider`; the Todoist implementation is #9. The health **findings** this port returns are rendered and repaired by `new-reminder-health`.

## Acceptance criteria

1. `:core` compiles with no Android or Todoist dependency and the port's types reference no provider-specific object.
2. Calling `reconcile` twice with the same subject list produces no second effect for either provider (idempotence test against a fake).
3. A subject's `contentHash` suppresses a no-op update.
4. A schedule with two enabled `schedule_provider` rows yields two subject lists, one per provider, with no change to the port.
5. A seasonally inactive schedule arrives as `PARKED(reentryOn)`, not as absent or overdue.

## Design references

D2 §5 (split of #9 / #4-c) · D3 §7.1 (provider port), §7.2 (local provider), §8 (Todoist adapter consuming `RuleFacts`) · D4 §8 (`schedule_provider`), §9 (supply subjects), §10 (`reminder_projection`, `provider_op`) · D5 §6 (season → PARKED) · D7 Phase 3 · D8 ruling R-12, ledger A19 · `11-review-1-changes.md` §3.
