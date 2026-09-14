# GitHub issue restructuring — the files Task 2 applies

Status: drafted 2026-09-14 from the approved design package (`docs/design/`, review 1). These
files are the **source of truth for the issue edits**; nothing here has been applied to GitHub yet.

The plan they implement is D2 §5 ("Recommended issue actions"), the "GitHub issue actions" section
of `docs/design/README.md`, and the phase sequence in D7. Ruling **R-13** approved applying the
restructuring after the review-1 corrections were incorporated; those corrections are folded into
the bodies below.

## Contents

| | Count |
|---|---|
| `rewrite-NN.md` — one per existing issue #1–#16 | 16 |
| `new-<slug>.md` — one per new issue | 19 |
| `original/issue_N.md` — the verbatim text of issues #1–#16 as they stood on 2026-09-14 | 16 |
| this README | 1 |

## File format

Each `rewrite-*.md` and `new-*.md` file is a YAML front-matter block followed by the full Markdown
body to put in the issue:

```yaml
---
action: rewrite | create
number: 4                                        # rewrite only: the existing issue number
title: "[MVP] ..."                               # the final title (existing issues may be retitled)
milestone: "Phase 3 — Scheduling + local reminders"
labels: []                                       # always empty; no labels are introduced in Phase 0
---
```

Two conventions Task 2 must honour:

- **`milestone: ""`** means *no milestone*. Only `rewrite-01.md` (the epic) uses it.
- **New issues are referenced by slug before they have numbers.** Any body that needs to point at a
  new issue writes it as a backticked slug prefixed with `new-`, e.g. `` `new-meter-model` ``.
  Task 2 creates the new issues first, then substitutes each `` `new-<slug>` `` occurrence with the
  real `#N` across every body before posting. Existing issues are already referenced as `#N`.

## Milestones (create exactly these titles)

```
Phase 0 — Foundation
Phase 1 — Tag survival (M1)
Phase 2 — Journal + profiles
Phase 3 — Scheduling + local reminders
Phase 4 — Attachments
Phase 5 — Todoist
Phase 6 — Supplies
Phase 7 — Extension points
```

## Mapping table

### Existing issues

| Original issue | Action | Result file | Phase / milestone | Notes |
|---|---|---|---|---|
| #1 Umbrella: NFC-first maintenance tracker | Rewrite as epic | `rewrite-01.md` | none | Scope list replaced with the reconciled MVP set; Todoist and supplies stated as NEXT; retitled `[EPIC]` |
| #2 Asset records + NFC tag binding | Rewrite (narrowed) + split | `rewrite-02.md` | Phase 1 — Tag survival (M1) | Now Room + Asset only. "Write the asset id to the tag" removed; tag identity is a separate `nfc_tag` row (D4 §3). Codec/resolver → `new-tag-payload-v1-legacy-resolver`; bind UX → `new-tag-bind-rebind-ux` |
| #3 Asset event journal | Rewrite (kept) | `rewrite-03.md` | Phase 2 — Journal + profiles | Gains the queryable-time-series storage requirement from #13; quick actions declared to be #13 profiles; the `source` enum defined here |
| #4 Canonical recurring schedules | Rewrite (narrowed) + split + merge | `rewrite-04.md` | Phase 3 — Scheduling + local reminders | Engine and state model only. Single owner of the four operations and of `completion_mode` (merged from #10/#11). FIXED = skip-forward, COMPLETION = from the completion date. Provider → `new-local-reminder-provider`, `new-reminder-provider-interface` |
| #5 Asset dashboard | Rewrite (kept) | `rewrite-05.md` | Phase 3 — Scheduling + local reminders | Meter-model dependency noted; a minimal list ships in Phase 1C |
| #6 Generalise external links | Rewrite (kept) | `rewrite-06.md` | Phase 1 — Tag survival (M1) | "Validate" defined: scheme allowlist + handler-present check via `<queries>` + confirmation for unknown schemes. Standalone links are first-class tag targets |
| #7 Photos, receipts, manuals, specs, parts | Rewrite (ideas → scope) | `rewrite-07.md` | Phase 4 — Attachments | Four scope items: attachment metadata + LOCAL/SAF-tree providers, referenced documents, structured spec fields, backup inclusion. The shared part entity belongs to #15. Title prefix stays `[NEXT]` |
| #8 Backup / export / import | Rewrite + re-tier | `rewrite-08.md` | Phase 1 — Tag survival (M1) | Re-tiered to MVP-1, retitled `[MVP]`. Adds format versioning, Replace vs Merge, automatic snapshots, attachment bundle, exclusion of secrets |
| #9 Optional Todoist provider | Rewrite (narrowed) + re-tier + split | `rewrite-09.md` | Phase 5 — Todoist | Retitled `[NEXT]`. Projection only, with capability-based representation. OAuth → `new-todoist-authentication`; deep links → `new-deeplink-contract`; provider radio → `new-provider-selection-ux`; `ReminderProvider` → `new-reminder-provider-interface`; four-operation semantics → #4 |
| #10 Two-way Todoist sync | Rewrite (kept) + merge out | `rewrite-10.md` | Phase 5 — Todoist | Adds activity-log completion detection for native tasks and reconciliation of Todoist's post-completion date. Simple/rich completion text moved to #4 |
| #11 Maintenance-aware actions | Rewrite (narrowed) + split + re-tier | `rewrite-11.md` | Phase 3 — Scheduling + local reminders | Local notification quick actions (Done / Snooze / Open) only; retitled `[MVP]`. Fatigue controls → `new-reminder-fatigue-controls`; Todoist link actions → `new-todoist-link-actions`; duplicated semantics removed, links to #4 |
| #12 Usage-based maintenance in Todoist | Rewrite (narrowed) + split | `rewrite-12.md` | Phase 5 — Todoist | Todoist projection policy for usage-based schedules only. The meter model → `new-meter-model` (MVP, Phase 2) |
| #13 Event/measurement profiles + templates | Rewrite (kept) | `rewrite-13.md` | Phase 2 — Journal + profiles | Time-series requirement moved to #3; stock decrement stated to be behind #15 |
| #14 Seasonal windows + reminder health | Rewrite (narrowed) + split | `rewrite-14.md` | Phase 3 — Scheduling + local reminders | Seasonal windows only: window on the asset, `FOLLOW_ASSET \| IGNORE`, re-entry `AT_START(+offset)` and `RESUME_CLAMPED`. Reminder health → `new-reminder-health` |
| #15 Supplies + low-stock reminders | Rewrite (kept) | `rewrite-15.md` | Phase 6 — Supplies | Owns the shared part/supply entity; the stock ledger (COUNT/DELTA) specified |
| #16 HA / InfluxDB / Grafana exploration | Rewrite (kept as FUTURE) | `rewrite-16.md` | Phase 7 — Extension points | Records the two invariants the core model keeps: measurement provenance (`source`) and meter readings as ordinary measurements |

### New issues

| Origin | Action | Result file | Phase / milestone | Notes |
|---|---|---|---|---|
| D2 §4 item 1 | Create | `new-phase0-repo-hygiene.md` | Phase 0 — Foundation | `[MVP] Phase 0: clone-buildable repo, AGP 9 toolchain, :core module, CI` |
| D2 §4 item 4 | Create | `new-signing-key-investigation.md` | Phase 0 — Foundation | `[MVP] Investigate the installed APK's signing certificate and upgrade path` (ruling R-1, non-destructive) |
| D2 §4 item 21 | Create | `new-testing-and-ci-strategy.md` | Phase 0 — Foundation | `[MVP] Testing pyramid and CI gates` |
| Split from #2 (also D2 §4 item 2) | Create | `new-tag-payload-v1-legacy-resolver.md` | Phase 1 — Tag survival (M1) | `[MVP] noteNFC tag payload format v1 and legacy md5_short resolver` |
| Split from #2 | Create | `new-tag-bind-rebind-ux.md` | Phase 1 — Tag survival (M1) | `[MVP] Bind, rebind, revoke, and unknown-tag flows` |
| Split from #9 (also D2 §4 item 3) | Create | `new-deeplink-contract.md` | Phase 1 — Tag survival (M1) | `[MVP] notenfc:// deep-link contract (navigation-only, validated)` |
| D2 §4 item 16 | Create | `new-untrusted-input-policy.md` | Phase 1 — Tag survival (M1) | `[MVP] Untrusted input policy: tag payloads, deep links, stored URIs` |
| D12 "Scope rule" (Phase 1C) | Create | `new-design-system-foundation.md` | Phase 1 — Tag survival (M1) | `[MVP] Phase 1C: Compose design system foundation (Apollo Service Binder)`; gate G1 (D8 §2) before implementation |
| D2 §4 item 9 | Create | `new-date-semantics.md` | Phase 2 — Journal + profiles | `[MVP] Calendar-date semantics for due and occurred dates` |
| Split from #12 (also D2 §4 item 12) | Create | `new-meter-model.md` | Phase 2 — Journal + profiles | `[MVP] Meter model: meter definitions, readings, baselines, reset` — the domain half re-tiered to MVP |
| D2 §4 item 14 | Create | `new-asset-templates.md` | Phase 2 — Journal + profiles | `[MVP] Asset templates that seed definitions, profiles, and default schedules` |
| Split from #9 (owned by #4's phase) | Create | `new-reminder-provider-interface.md` | Phase 3 — Scheduling + local reminders | `[MVP] ReminderProvider port and reconcile contract` |
| Split from #4 | Create | `new-local-reminder-provider.md` | Phase 3 — Scheduling + local reminders | `[MVP] Local reminder provider: daily digest alarm, WorkManager backstop, boot receivers, channels` |
| D2 §4 item 8 | Create | `new-platform-permissions-scheduling.md` | Phase 3 — Scheduling + local reminders | `[MVP] Platform permissions and scheduling constraints (POST_NOTIFICATIONS, no exact alarms, BOOT/TIME/TIMEZONE)` |
| Split from #14 | Create | `new-reminder-health.md` | Phase 3 — Scheduling + local reminders | `[MVP] Reminder health and integrity checks with idempotent repair` |
| Split from #9 | Create | `new-provider-selection-ux.md` | Phase 5 — Todoist | `[NEXT] Per-schedule reminder provider selection (single-choice UI over a multi-provider model)` |
| Split from #9 | Create | `new-todoist-authentication.md` | Phase 5 — Todoist | `[NEXT] Todoist authentication: personal API token first, OAuth later` (ruling R-3) |
| Split from #11 | Create | `new-todoist-link-actions.md` | Phase 5 — Todoist | `[NEXT] Todoist deep-link actions into noteNFC` |
| Split from #11 | Create | `new-reminder-fatigue-controls.md` | Phase 7 — Extension points | `[NEXT] Reminder-fatigue controls per schedule` |

## Dependencies

Derived from the reconciled dependency graph in D2 §2 (and the phase prerequisites in D7).

**Phase 0 — Foundation**

- `new-phase0-repo-hygiene` depends on nothing.
- `new-signing-key-investigation` depends on nothing (it is non-destructive and runs before Phase 1).
- `new-testing-and-ci-strategy` depends on `new-phase0-repo-hygiene`.
- Everything below depends on `new-phase0-repo-hygiene`.

**Phase 1 — Tag survival (M1)**

- #2 depends on `new-phase0-repo-hygiene`.
- `new-tag-payload-v1-legacy-resolver` depends on #2.
- `new-tag-bind-rebind-ux` depends on #2 and `new-tag-payload-v1-legacy-resolver`.
- #6 depends on #2.
- #8 depends on #2 and #6.
- `new-deeplink-contract` depends on #2.
- `new-untrusted-input-policy` depends on `new-tag-payload-v1-legacy-resolver`, `new-deeplink-contract` and #6.
- `new-design-system-foundation` depends on `new-phase0-repo-hygiene` and on gate G1 (D8 §2).

**Phase 2 — Journal + profiles**

- #3 depends on #2.
- `new-date-semantics` depends on #3.
- #13 depends on #3.
- `new-meter-model` depends on #3.
- `new-asset-templates` depends on #13 and `new-meter-model`.

**Phase 3 — Scheduling + local reminders**

- #4 depends on #3, #13, `new-meter-model` and `new-date-semantics`.
- `new-reminder-provider-interface` depends on #4.
- `new-platform-permissions-scheduling` depends on `new-phase0-repo-hygiene`.
- `new-local-reminder-provider` depends on #4, `new-reminder-provider-interface` and `new-platform-permissions-scheduling`.
- #14 depends on #4.
- #11 depends on `new-local-reminder-provider` and `new-deeplink-contract`.
- `new-reminder-health` depends on #4 and `new-local-reminder-provider` (its Todoist findings additionally depend on #9).
- #5 depends on #2, #3, #4 and `new-meter-model`.

**Phase 4 — Attachments**

- #7 depends on #2 and #3 (event attachments additionally on #4's completion forms).

**Phase 5 — Todoist**

- `new-todoist-authentication` depends on `new-phase0-repo-hygiene`.
- #9 depends on #4, `new-reminder-provider-interface`, `new-todoist-authentication` and `new-deeplink-contract`.
- #10 depends on #9.
- #12 depends on #9, #10 and `new-meter-model`.
- `new-provider-selection-ux` depends on #9 and `new-reminder-provider-interface`.
- `new-todoist-link-actions` depends on #9 and `new-deeplink-contract`.

**Phase 6 — Supplies**

- #15 depends on #3, #13 and #7.

**Phase 7 — Extension points**

- `new-reminder-fatigue-controls` depends on #11.
- #16 depends on #3, #13, #4 and #12.

Phases 4, 5 and 6 remain architecturally independent of one another; ruling R-6 fixes only the
execution order (attachments → Todoist → supplies).

## Traceability

Every rewritten and every new body names its origin and the design it implements. Each
`rewrite-NN.md` opens with a revision note that states the origin issue number, points at the
preserved original, and lists the design-document sections it implements; each `new-*.md` opens
with either a "Split from #N" line or an "Identified in D2 §4 item N" line. Every body closes with
a "Design references" section citing the specific D-document sections, rulings and ledger
decisions behind its content, so any statement in an issue can be traced back to the document that
decided it. No body introduces scope that D2, D7, D12 or `docs/design/README.md` does not name.

The original issue texts are preserved twice: verbatim in `original/issue_1.md` … `issue_16.md`
in this repository, and in each issue's own edit history on GitHub once Task 2 applies the
rewrites. Nothing is lost by editing in place.

Terminology: the new NFC payload is **noteNFC tag payload format v1** (version byte `0x01`, record
type `com.loosecannon.notenfc:tag`), the new-generation replacement for the legacy `md5_short`
record. "v1" always refers to that payload version byte, never to an application generation.
