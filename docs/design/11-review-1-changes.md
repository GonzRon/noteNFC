# Review 1 — change log (2026-09-14)

Every architectural or requirements change made to the package in response to the first
architecture-gate review, with the evidence checked before applying it. A raw unified diff of all
files against the pre-review snapshot is available on request (it was generated with
`diff -ru` at revision time); this document is the readable version.

## 0. Verification of the review's factual claims

| Claim | Checked against | Result |
|---|---|---|
| Room 3.0.3 is stable as of 2026-09-09; Room 2.8.5 also stable | `developer.android.com/jetpack/androidx/releases/room3` and `/room` | **Confirmed.** The Room 3 page lists 3.0.0, 3.0.1, 3.0.2 (2026-08-26) and 3.0.3; Room 2.8.5 is dated 2026-09-09. The package's earlier statement that Room 3 was alpha came from a March 2026 blog post and was out of date. |
| Navigation 3 stable is 1.1.7, not the 1.0 line | `/releases/navigation3` | **Confirmed.** 1.1.0 stable 2026-04-08; 1.1.7 on 2026-08-26; 1.2.0-rc01 pending. |
| AGP 9.4 is current and stable | user's Gradle cache + release notes | Confirmed (already in D1). |
| Todoist supports recurring due dates and advances them on completion | Todoist API v1 docs and help centre | Confirmed (already in the research lane). |
| Additional finding while verifying | Todoist help, "Complete a rescheduled task" | **New evidence that shapes change 2:** "When you manually reschedule a recurring task and then complete it, Todoist calculates the next occurrence from the completion date, not the original schedule." This applies to plain `every` rules, so a FIXED noteNFC schedule projected as native recurrence can silently become completion-relative after a Todoist-side postpone. Consequence: FIXED rules stay `MANAGED_OCCURRENCE` unless spike S8 shows the verification step makes this harmless. For COMPLETION-basis rules the behaviour matches ours exactly, which is why they are the native-eligible set. |

## 1. Persistence: Room 3.0 replaces Room 2.8 as the starting line

| Where | Was | Now |
|---|---|---|
| D3 §5 | "Room 2.8.x … Room 3.0 (alpha) … migrate when stable" | Room 3.0.x (`androidx.room3`, 3.0.3) from the first schema; Room 2.8.5 only as the S1 fallback on concrete evidence; JVM-only DAO/migration tests via the bundled SQLite driver noted as a benefit |
| D3 §15 | Room 2.8.x, Navigation 3 1.0.x | Room 3.0.x + KSP 2 + bundled driver; Navigation 3 1.1.x |
| D3 §16 | A18 "Room 2.8.x now; 3.0 when stable" | A18 revised with the corrected premise and rejection ground |
| D4 §15 | Room 2.8 annotation model implied | note that the schema is Room-3-compatible and that Room 3 is the starting line |
| D7 Phase 0/1A, D8 S1 | S1 evaluated Room 2.8 | S1 evaluates Room 3.0.x first (Gradle plugin `schemaDirectory`, bundled driver, migration test helper) |
| D8 §4 | — | new risk 10 (Room 3 early-adopter issues) with mitigation |
| Testing doc §4 | Robolectric for all Room tests | Room tests as plain JVM tests where the bundled driver allows; Robolectric only where Android classes are needed |

## 2. Todoist projection: capability-based representation replaces "non-recurring only"

| Where | Was | Now |
|---|---|---|
| D3 §8 | "One non-recurring task per schedule, re-dated by noteNFC; Todoist's `every`/`every!` grammar is not used" | Representation selected per schedule: `NATIVE_RECURRING` when time-only, `COMPLETION` basis, day/week/month/year unit, and no seasonal window applies; `MANAGED_OCCURRENCE` otherwise (usage, time-OR-meter, seasonal, and FIXED pending S8). Native tasks are created with `due: {date, string: "every! N unit"}`; completion in Todoist is read from the activity log; after any completion the adapter compares Todoist's due date with the canonical computation and emits `SYNC_DUE` on mismatch. The invariant "noteNFC is canonical; Todoist's recurrence is a representation, not authority" is stated explicitly. |
| D3 §7.1 | `ReminderSubject` had no rule facts | `rule: RuleFacts?` added so a provider can decide whether its own recurrence engine can carry the subject |
| D3 §7.3 | Todoist findings listed | `PROJECTION_DUE_DRIFT` finding added (auto-corrected, canonical wins) |
| D3 §16 | A13 "one non-recurring task per schedule" | A13 revised; A20 added (per-schedule representation + mandatory verification) |
| D4 §10 | projection row had no representation | `representation`, `projected_recurrence`, `last_remote_completion_ref` columns; `SYNC_DUE` outbox op; note that native completions do not close the task |
| D5 §7 | projection effects assumed managed tasks | per-representation effects for complete/postpone/edit; representation re-evaluated on rule edit |
| D5 §6, §10.6 | parking described for managed tasks | seasonal schedules never native; paused native tasks keep their recurrence string with the due cleared |
| D2 #9, #10, C3, C5 | "replace Todoist recurrence string with non-recurring task" | capability-based representation; activity-log completion detection; canonical verification; usage rules managed only |
| D7 Phase 5 | Todoist tests/exit criteria for managed tasks | representation and verification tests; exit criteria cover a native `every! 90 days` schedule, a managed time-or-meter schedule, and the reschedule-then-complete correction |
| D8 §2 | S6 covered managed commands | S6 extended (native `item_close`, due object with string, activity-log cursor); **S8 added** (FIXED rules as native recurrence) |
| D8 §4 | risk 5 "projection drift" | risk 5 now names the native-recurrence divergence and its mitigations |
| Security doc | "only `checked`, `due.date`, `is_deleted` interpreted" | adds activity-log completions and the recurrence string (verified, never adopted); remote due dates are corrected, not trusted |
| Testing doc §3 | one interpretation test | `TodoistRepresentationTest` + expanded `TodoistInterpretationTest`; S8 listed as manual |

## 3. Reminder providers: multi-provider model, single-choice MVP UI

| Where | Was | Now |
|---|---|---|
| D4 §8 | `maintenance_schedule.reminder_provider` enum `LOCAL | TODOIST | NONE` | column removed; new `schedule_provider(schedule_id, provider, enabled)` join table with `PRIMARY KEY (schedule_id, provider)`; `reminders_enabled` stays as the master switch |
| D4 §1, §12, §13 | — | table listed; canonical; pause semantics per representation |
| D3 §7.1 | provider port only | subjects built per enabled provider from `schedule_provider`; single-choice is a UI constraint |
| D3 §16 | — | A19 added |
| D8 §3, §4 | R-12 open | R-12 recorded; risk 17 (double notifications if both enabled later) with a Phase 7 design note |

## 4. Phase order and Phase 1 structure (rulings R-6 and the 1A/1B/1C request)

| Where | Was | Now |
|---|---|---|
| D7 | Phase 4 supplies, 5 attachments, 6 Todoist; Phase 1 as one slice | Phase 4 attachments, 5 Todoist, 6 supplies; Phase 1 split into 1A (persistence + migration + backup), 1B (NDEF v1 + resolver + device proof), 1C (Compose shell + UX + restore proof), each with its own exit criteria; M1 = 1C's criteria |
| D7 Phase 0 | — | R-1 keystore investigation added as prerequisite and exit criterion |
| D2 §1, §2, D3 §7.3/§8/§11, D4 §1/§9/§10/§11/§15, security and testing docs | old phase numbers | renumbered consistently; Room version plan v4 attachments, v5 projections, v6 supplies (supply FK columns added in v6) |

## 5. Rulings recorded

D8 §3 now lists R-1..R-13 with their consequences. Design changes beyond the three above that
follow from rulings: R-1 investigation in Phase 0; R-4 "revisit before Phase 5" in Phase 5
prerequisites and A17; everything else confirmed existing decisions.

## 6. Explicitly unchanged

Tag binding (A3), controlled EAV (A14), derived schedule state and `rebuild` (A9), calendar-date
semantics (A8), seasonal model (A11), inexact local reminders + WorkManager backstop (A12),
attachment-provider boundary (A15), backup design (A16), Compose (A5), manual DI (A7), the
`:core`/`:app` split (A6), backup in Milestone 1, legacy compatibility plan (D6), and the
deterministic scheduling suite.
