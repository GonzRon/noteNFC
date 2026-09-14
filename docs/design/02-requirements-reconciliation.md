# D2 — Requirements reconciliation (GitHub issues #1–#16)

Status: design-phase document, 2026-09-14. Read-only analysis; no issue was edited.

Source of truth for this document: the full text of issues #1–#16 as they stood on 2026-09-14
(all open, all authored 2026-09-13 23:26–23:54 UTC, no labels, no milestones, no comments, no
projects). The only planning structure that exists is the title prefix `[MVP]` / `[NEXT]` /
`[FUTURE]`.

The issues are treated as **product requirements and design inputs, not as a settled
architecture**. Where an issue prescribes a mechanism (e.g. "asset id on the tag"), the
recommendation column says whether the target design keeps it.

---

## 1. Reconciliation matrix

Tier legend: **MVP-1** = required for the smallest useful end-to-end milestone (see D7 Phase 1),
**MVP** = required before calling the maintenance product usable, **NEXT** = after MVP,
**FUTURE** = extension point only. "Owner in target design" names the D3/D4 component that
absorbs the requirement.

| # | Title (short) | Requirement / domain | Depends on (explicit → implicit) | Issue tier → Recommended tier | Overlaps / conflicts | Owner in target design | Suggested adjustment |
|---|---|---|---|---|---|---|---|
| 1 | Umbrella: NFC-first maintenance tracker | Product principles; 7 workflows; out-of-scope list | none | MVP → **Epic (keep open, no code)** | Lists only *local* reminders; does not mention Todoist although #9 is tagged MVP | Whole system | Rewrite as an epic that links the reconciled MVP set (#2 #3 #4-a #5 #6 #13 #14-a + new foundational issues); state Todoist is NEXT |
| 2 | Asset records + NFC tag binding | Asset entity; Room; stable ID on tag; rebind lost tags; detect unknown tags; migrate Joplin prefs | none → **root of everything** | MVP → **MVP-1** | Joplin migration also claimed by #6. "Asset id on tag" conflicts with its own "rebind a lost tag" (cannot revoke) | `data` (Room), `nfc` codec/resolver, `TagBinding` | Split: (a) Room + Asset entity, (b) tag payload format v1 + resolver + legacy compatibility (new issue), (c) tag bind/rebind/unknown-tag UX. Change "asset id on tag" to "tag identity on tag, resolved through a binding table" (D4 §3) |
| 3 | Asset event journal with measurements and consumables | `AssetEvent` + `measurements[]` + `consumables[]` + `attachments[]`; append-oriented; correct-in-place with timestamps; quick actions; completion advances schedule | #7 (attachments), #15 (stock) | MVP → **MVP** | "Append-oriented" (#1) vs "correct an event in place" (#3): resolved as mutable-with-`updated_at` (D4 §5). Quick-action list overlaps #13 profiles. `attachments[]` dangles (#7 covers asset-level docs only) | `domain.journal`, `data` | Move "queryable time series" (from #13) here as a storage requirement. Declare #3's quick actions to be *instances of #13 profiles*, not hard-coded. Define the `source` enum here |
| 4 | Canonical recurring schedules + due/overdue reminders | One provider-neutral recurrence model: fixed, completion-relative, usage, whichever-first, seasonal; status; lead time; local notifications; complete/snooze/postpone/edit distinct | #3, #9, #11, #14 → **#12's meter model** (usage schedules need it) | MVP → **MVP (domain) + MVP (local provider)** | Four-operation semantics restated in #4, #9, #11. Provider abstraction restated in #4 and #9. #11 introduces a third flavour ("fixed unless the schedule says otherwise") | `core.scheduling` engine; `reminders.local` | Split into (a) scheduling engine + state model, (b) local reminder provider (alarms, boot, permissions, channels), (c) `ReminderProvider` interface (move from #9). Make #4 the single owner of the four-operation semantics; #11 keeps UX only |
| 5 | Asset dashboard with maintenance status | Home screen: overdue/due/due-soon, list/search, quick actions, archived hidden | → #2, #3, #4 (status), #12 (meter due values) | MVP → **MVP** (a minimal list ships in MVP-1) | none | `ui.home` | Note that "due value" for usage schedules requires the meter model (#12 domain half) |
| 6 | Generalise external links beyond Joplin | 0..n named links per asset; generic `ACTION_VIEW`; validate/preview URI; standalone link records; keep original share→write→scan→launch flow | #2 | MVP → **MVP-1** | Joplin migration also in #2. "Standalone link record" vs #1/#2 "tag points to an Asset" | `ExternalLink` entity; `LinkLaunchPolicy` | Define "validate" concretely: scheme allowlist, handler-present check via `<queries>`, confirmation for unknown schemes (D3 §10, security doc). Decide standalone links are first-class tag targets (D4 §3) |
| 7 | Photos, receipts, manuals, specs, parts references | Asset documentation; reference side of parts (vs #15 stock); ideas list only | #2, #15 | NEXT → **NEXT (Phase 4, first after the MVP core per ruling R-6)**, and the **data model and storage-provider boundary are decided in MVP design** (D4 §11, D3 §11) | Shares a part/consumable identity with #15 (neither owns the entity) | `Attachment` + `AttachmentStore`; `SupplyItem` (identity) | Rewrite from "ideas" into scope: (a) attachment metadata + LOCAL + SAF-tree providers, (b) referenced (non-copied) documents, (c) structured spec fields, (d) backup inclusion. Assign the shared part entity to #15 |
| 8 | Backup / export / import | Export all data; JSON bundle; import with conflict handling; document storage; preserve IDs | every data-bearing issue | NEXT → **MVP-1** | Export list omits profiles, seasons, projections, supplies, attachments; must *exclude* tokens; no format version | `backup` | Promote to MVP-1: tags outlive phones and the current install may not be upgradeable in place (D1 §6). Add format versioning, REPLACE vs MERGE semantics, automatic snapshots, attachment bundle |
| 9 | Optional Todoist provider | OAuth for public client; secure token storage; `ReminderProvider`; projection with task IDs; recurrence mapping (`every`/`every!`); deep links; per-schedule provider choice | #4 → **deep-link contract (#new)** | MVP → **NEXT (Phase 5)** | Owns `ReminderProvider` interface that the *local* provider (#4) also needs. Owns `notenfc://` links consumed by #4, #11, #12, #14, #16. Excludes usage-based sync while #4 requires usage schedules in MVP | `integrations.todoist` | Demote to NEXT; extract `ReminderProvider` interface into #4-c and the deep-link contract into a new foundational issue. Make the projection **capability-based**: `NATIVE_RECURRING` (Todoist's own `every!` recurrence) for completion-relative, time-only, year-round schedules; `MANAGED_OCCURRENCE` (ordinary dated task re-dated by noteNFC) for everything else; noteNFC stays canonical and verifies Todoist's due state on every sync (D3 §8). Personal API token first (R-3). The per-schedule provider radio is UI-only; the model allows several providers per schedule (R-12) |
| 10 | Two-way Todoist sync | Pull-based; simple vs rich completion; external reschedule interpretation; conflicts; idempotency; visibility states | #9 | NEXT → **NEXT (Phase 5, same slice as #9)** | Simple/rich completion also in #11 | `integrations.todoist.sync` | Merge the simple/rich completion attribute into #4 (it is a schedule property: `completion_mode`). Keep #10 for the sync engine, and add: completions of `NATIVE_RECURRING` tasks are detected from the activity log / `completed_info` (the task stays open), and Todoist's post-completion due date is reconciled against the canonical computation |
| 11 | Maintenance-aware actions, pre-emptive completion | Notification action buttons; four operations; simple vs rich; reminder-fatigue controls | #9 | NEXT → **NEXT (Phase 3b for local actions; fatigue controls NEXT)** | Restates #4 semantics; restates #10 simple/rich; adds "fixed unless schedule says otherwise" | `reminders.local` notifications; `ui` | Split: (a) local notification quick actions (Done / Snooze / Open) → Phase 3, (b) fatigue controls → NEXT, (c) Todoist link actions → with #9. Delete the duplicated semantics text; link to #4 |
| 12 | Usage-based maintenance projected into Todoist | Meter model (last/current/next threshold, calendar limit); projection policies; debounce; manual readings first | #9 | NEXT → **Split: meter model = MVP (with #4); Todoist projection policy = NEXT** | The domain half is a prerequisite of #4's MVP usage schedules and of #3's oil-change acceptance example | `core.scheduling` (meters), `integrations.todoist` (policy) | Split as stated. Meter definitions become asset-scoped `MeasurementDefinition` rows with `is_meter` (D4 §6) |
| 13 | Configurable event/measurement profiles + templates | Profile = event name/type, fields, units, ranges, consumables, schedule link, ordering, required; Hot Tub + Power Equipment starters; in/below/above range; quick entry | #3, #4 → **#14 (seasonal quick actions)**, #15 (optional decrement) | MVP → **MVP** | Quick-action list overlaps #3; "queryable time series" belongs in #3 | `EventProfile`, `ProfileField`, generic entry form | Keep. Move time-series requirement to #3. State that stock decrement is feature-flagged behind #15 |
| 14 | Seasonal windows + reminder health | Windows per schedule or asset default; inactive ≠ overdue ≠ disabled; re-entry policies; startup/shutdown tasks; health checks; self-repair | #4, #9, #11 → **per-schedule "reminders enabled" flag that only #11 defines** | MVP → **Split: seasonal = MVP (Phase 3); health = MVP for local checks (Phase 3), Todoist checks with Phase 5** | Two unrelated features. Inheritance between asset-level and schedule-level windows unspecified | `core.scheduling.season`; `reminders.health` | Split into two issues. Decide: window lives on the Asset, schedules choose FOLLOW_ASSET or IGNORE (D5 §6). Re-entry MVP = AT_START(+offset) and RESUME_CLAMPED |
| 15 | Lightweight supplies + low-stock reminders | Supply item fields; approximate stock; optional decrement from events; one low-stock reminder; manual correction | #3, #7 | NEXT → **NEXT (Phase 6, last of the three post-MVP slices per ruling R-6)** | Shares part identity with #7 | `SupplyItem`, `AssetSupply`, `StockLedger`; reminder subject | Make #15 the owner of the shared part/supply entity. Specify the ledger (D4 §9) so corrections and event deletion are reversible |
| 16 | HA / InfluxDB / Grafana exploration | Extension points only | #3, #13, #4/#12 | FUTURE → **FUTURE** | none | `MeasurementSource` port (future) | Keep as exploration. Record the two invariants the core model must keep: measurement provenance (`source`) and meter readings as ordinary measurements (D4 §6) |

## 2. Dependency graph (reconciled)

```
Phase 0  toolchain/repo hygiene  (new issue)
   │
Phase 1  #2a Room+Asset  ──┬── #2b tag payload format v1 + legacy resolver (new)
         #6  ExternalLink  ┤
         #8  Backup/restore┘   #new deep-link contract
   │
Phase 2  #3 journal ── #13 profiles ── #12(domain) meters
   │
Phase 3  #4a engine ── #4b local provider ── #4c ReminderProvider iface
         #14a seasons  #14b health(local)  #11a notification actions  #5 dashboard(full)
   │
Phase 4  #7 attachments                       (ruling R-6: attachments first)
   │
Phase 5  #9 Todoist provider ── #10 sync ── #12(projection) ── #14b health(Todoist) ── #11c
   │
Phase 6  #15 supplies
   │
Future   #16

(Phases 4, 5, 6 remain architecturally independent; R-6 fixes only the execution order.)
```

## 3. Contradictions and how the target design resolves them

| # | Contradiction | Resolution in target design |
|---|---|---|
| C1 | #2: asset id on tag **and** rebind/revoke lost tags | Tag carries a **tag identity** (random UUID); `nfc_tag` binds it to an asset or a link. Lost tag → mark row LOST (revoked); new tag → new row. Multiple tags per asset supported (D4 §3) |
| C2 | #1 append-only history vs #3 edit-in-place | Events are mutable with `created_at`/`updated_at`; no supersede chain (YAGNI). Schedule state is **recomputed from events**, so an edit or deletion cannot leave a stale due date (D5 §9) |
| C3 | Todoist owns snooze/postpone UX (#9) while noteNFC owns the due date (#4), with no sync in MVP | Todoist is demoted to NEXT and shipped together with pull sync (#10). A remote due-date change is interpreted as *postpone current occurrence*; a completion of a `NATIVE_RECURRING` task is a completion whose Todoist-computed next date is verified against the canonical one and corrected if different; anything else is a CONFLICT the user resolves (D3 §8) |
| C4 | MVP scope inflation (9 of 16 issues) | MVP-1 is narrowed to the tag-survival slice; Todoist and supplies move out of MVP (D7) |
| C5 | #4 requires usage schedules in MVP; #9 excludes usage sync | Usage schedules exist from Phase 3 with the local provider only; the Todoist provider projects them only as `MANAGED_OCCURRENCE` once the #12 projection policy ships |
| C6 | #14 window on schedule *or* asset with undefined inheritance | Window on the **Asset** only; schedule field `season_behavior = FOLLOW_ASSET \| IGNORE` |
| C7 | #6 standalone link vs #1/#2 "tag points to an Asset" | Both are first-class tag targets. A link-tag scan launches the link immediately (original behaviour); an asset-tag scan opens the asset |
| C8 | #11 "fixed cadence unless the schedule explicitly says otherwise" | Not adopted. Two bases only: FIXED (skip-forward, no backlog) and COMPLETION. A user who wants "reset on early completion" chooses COMPLETION |

## 4. Requirements missing from the issue set

These are covered by the target design and should become issues (grouped; each is a candidate
new issue unless folded into the split noted above).

**Foundational**
1. Toolchain and repository hygiene: commit wrapper/settings, stop ignoring test directories, remove tracked build outputs, CI running unit tests, raise targetSdk (Phase 0).
2. Tag payload format v1 specification (record type, version byte, AAR, unknown-version handling) and the legacy `md5_short` resolver. Nothing in the issues says how a scanner tells a legacy link tag from an asset tag.
3. `notenfc://` deep-link contract (navigation-only, validated, versioned) as its own issue, consumed by local notifications long before Todoist exists.
4. Signing-key and in-place-upgrade risk: the installed release APK is signed with a key absent from the repo; the migration plan must work through uninstall/reinstall (D6 §2).
5. Room schema versioning/migration policy and backup format versioning.

**NFC**
6. Tag write robustness: read-back verification, capacity check, already-bound tag confirmation, foreign NDEF content handling, read-only tags, optional lock with warning.
7. Reader-mode vs foreground-dispatch decision, scanning while inside another screen, NFC-disabled prompt.

**Platform**
8. Runtime permissions and OS constraints owned by one issue: POST_NOTIFICATIONS, no exact alarms, BOOT/TIME/TIMEZONE re-registration, notification channels, battery-optimisation guidance.
9. Date semantics: due dates and event dates are calendar dates, not instants; reminder time-of-day preference; zone changes.

**Data model**
10. Backdated events and date-only precision.
11. Event edit/delete recompute of schedule state.
12. Meter model: multiple meters per asset, meter reset/rollover (new engine), standalone "log a reading".
13. Asset lifecycle: retired/sold/replaced, child assets (components), location.
14. Asset templates that pre-create profiles, measurement definitions, and default schedules when an asset is created from a category.
15. Units and number formatting preferences (°F/°C, qt/L); i18n is deferred.

**Security / privacy**
16. Untrusted input policy: tag payloads, deep links, stored URIs (scheme allowlist), Todoist content.
17. Backup contents exclude secrets; optional backup encryption; the backup is the tag-survival mechanism and must be prominent in the UI.

**Product**
18. Per-asset export (history for resale or a technician) — later.
19. Onboarding and sample data; a "restore from backup" first-run path.
20. Household use: a second phone restoring the same backup is the supported multi-device story; live sync is explicitly unsupported.
21. Testing strategy (no test source set exists today) — see the testing document.

## 5. Recommended issue actions (for your ruling; nothing has been edited)

| Action | Issues |
|---|---|
| Rewrite as epic with reconciled MVP list | #1 |
| Split | #2 (Room/Asset · tag payload format v1 + legacy resolver · bind UX), #4 (engine · local provider · provider interface), #9 (OAuth/token · projection · deep links → foundational · provider UX), #11 (local quick actions · fatigue controls · Todoist link actions), #12 (meter model → MVP · projection policy → NEXT), #14 (seasons · reminder health) |
| Merge | simple/rich completion text from #10 and #11 into #4 as `completion_mode`; four-operation semantics from #9 and #11 into #4 |
| Re-tier | #8 → MVP-1; #9 → NEXT; #7 → NEXT but data model decided now; #12 domain half → MVP |
| Convert ideas list into scope | #7 |
| Incorporate review-1 corrections before filing | #9/#10/#12: capability-based Todoist representation (`NATIVE_RECURRING` vs `MANAGED_OCCURRENCE`), activity-log completion detection, canonical due verification; #2a: Room 3.0 as the starting persistence line; #4-c/#9: provider selection is UI single-choice over a multi-provider model |
| New issues | Phase 0 hygiene; tag payload format v1 + legacy resolver; deep-link contract; platform permissions/scheduling; signing-key/upgrade path (non-destructive keystore investigation first); date semantics; meter model; asset templates; untrusted-input policy; testing/CI |

Rulings received 2026-09-14 (R-1..R-13, recorded in D8 §3) approve this plan; the issue edits
themselves are still pending your final go-ahead after the revised package is approved.
