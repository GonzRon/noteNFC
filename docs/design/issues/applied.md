# Applied issue restructuring — GonzRon/noteNFC

Date applied: 2026-09-14

Task 1 authored the plan as files under `docs/design/issues/`. Task 2 (this file) applied it to
GitHub: created 8 milestones, created 19 new issues, substituted every `` `new-<slug>` `` /
`#new-<slug>` token in every body with the real issue number, then rewrote the 16 existing issues.

## Milestones created

| # | Title |
|---|---|
| 1 | Phase 0 — Foundation |
| 2 | Phase 1 — Tag survival (M1) |
| 3 | Phase 2 — Journal + profiles |
| 4 | Phase 3 — Scheduling + local reminders |
| 5 | Phase 4 — Attachments |
| 6 | Phase 5 — Todoist |
| 7 | Phase 6 — Supplies |
| 8 | Phase 7 — Extension points |

## Issues

| number | action | title | milestone | url |
|---|---|---|---|---|
| 1 | rewrite | [EPIC] Evolve noteNFC into an NFC-first maintenance tracker | none | https://github.com/GonzRon/noteNFC/issues/1 |
| 2 | rewrite | [MVP] Room persistence and the Asset entity (Phase 1A) | Phase 1 — Tag survival (M1) | https://github.com/GonzRon/noteNFC/issues/2 |
| 3 | rewrite | [MVP] Add asset event journal with structured measurements and consumable usage | Phase 2 — Journal + profiles | https://github.com/GonzRon/noteNFC/issues/3 |
| 4 | rewrite | [MVP] Canonical scheduling engine and schedule state model | Phase 3 — Scheduling + local reminders | https://github.com/GonzRon/noteNFC/issues/4 |
| 5 | rewrite | [MVP] Add asset dashboard with maintenance status | Phase 3 — Scheduling + local reminders | https://github.com/GonzRon/noteNFC/issues/5 |
| 6 | rewrite | [MVP] Generalize external note/deep-link support beyond Joplin | Phase 1 — Tag survival (M1) | https://github.com/GonzRon/noteNFC/issues/6 |
| 7 | rewrite | [NEXT] Add photos, receipts, manuals, specifications, and parts references to assets | Phase 4 — Attachments | https://github.com/GonzRon/noteNFC/issues/7 |
| 8 | rewrite | [MVP] Add backup/export/import for local maintenance data | Phase 1 — Tag survival (M1) | https://github.com/GonzRon/noteNFC/issues/8 |
| 9 | rewrite | [NEXT] Add optional Todoist integration as the external reminder/task provider | Phase 5 — Todoist | https://github.com/GonzRon/noteNFC/issues/9 |
| 10 | rewrite | [NEXT] Add two-way Todoist synchronization and completion reconciliation | Phase 5 — Todoist | https://github.com/GonzRon/noteNFC/issues/10 |
| 11 | rewrite | [MVP] Local notification quick actions for maintenance schedules | Phase 3 — Scheduling + local reminders | https://github.com/GonzRon/noteNFC/issues/11 |
| 12 | rewrite | [NEXT] Todoist projection policy for usage-based maintenance | Phase 5 — Todoist | https://github.com/GonzRon/noteNFC/issues/12 |
| 13 | rewrite | [MVP] Add configurable event/measurement profiles with hot-tub and power-equipment templates | Phase 2 — Journal + profiles | https://github.com/GonzRon/noteNFC/issues/13 |
| 14 | rewrite | [MVP] Add seasonal activation windows for assets and schedules | Phase 3 — Scheduling + local reminders | https://github.com/GonzRon/noteNFC/issues/14 |
| 15 | rewrite | [NEXT] Add lightweight supplies/consumables tracking with low-stock reminders | Phase 6 — Supplies | https://github.com/GonzRon/noteNFC/issues/15 |
| 16 | rewrite | [FUTURE] Explore Home Assistant, InfluxDB, and Grafana integrations for asset telemetry/history | Phase 7 — Extension points | https://github.com/GonzRon/noteNFC/issues/16 |
| 17 | create | [MVP] Asset templates that seed definitions, profiles, and default schedules | Phase 2 — Journal + profiles | https://github.com/GonzRon/noteNFC/issues/17 |
| 18 | create | [MVP] Calendar-date semantics for due and occurred dates | Phase 2 — Journal + profiles | https://github.com/GonzRon/noteNFC/issues/18 |
| 19 | create | [MVP] notenfc:// deep-link contract (navigation-only, validated) | Phase 1 — Tag survival (M1) | https://github.com/GonzRon/noteNFC/issues/19 |
| 20 | create | [MVP] Phase 1C: Compose design system foundation (Apollo Service Binder) | Phase 1 — Tag survival (M1) | https://github.com/GonzRon/noteNFC/issues/20 |
| 21 | create | [MVP] Local reminder provider: daily digest alarm, WorkManager backstop, boot receivers, channels | Phase 3 — Scheduling + local reminders | https://github.com/GonzRon/noteNFC/issues/21 |
| 22 | create | [MVP] Meter model: meter definitions, readings, baselines, reset | Phase 2 — Journal + profiles | https://github.com/GonzRon/noteNFC/issues/22 |
| 23 | create | [MVP] Phase 0: clone-buildable repo, AGP 9 toolchain, :core module, CI | Phase 0 — Foundation | https://github.com/GonzRon/noteNFC/issues/23 |
| 24 | create | [MVP] Platform permissions and scheduling constraints (POST_NOTIFICATIONS, no exact alarms, BOOT/TIME/TIMEZONE) | Phase 3 — Scheduling + local reminders | https://github.com/GonzRon/noteNFC/issues/24 |
| 25 | create | [NEXT] Per-schedule reminder provider selection (single-choice UI over a multi-provider model) | Phase 5 — Todoist | https://github.com/GonzRon/noteNFC/issues/25 |
| 26 | create | [NEXT] Reminder-fatigue controls per schedule | Phase 7 — Extension points | https://github.com/GonzRon/noteNFC/issues/26 |
| 27 | create | [MVP] Reminder health and integrity checks with idempotent repair | Phase 3 — Scheduling + local reminders | https://github.com/GonzRon/noteNFC/issues/27 |
| 28 | create | [MVP] ReminderProvider port and reconcile contract | Phase 3 — Scheduling + local reminders | https://github.com/GonzRon/noteNFC/issues/28 |
| 29 | create | [MVP] Investigate the installed APK's signing certificate and upgrade path | Phase 0 — Foundation | https://github.com/GonzRon/noteNFC/issues/29 |
| 30 | create | [MVP] Bind, rebind, revoke, and unknown-tag flows | Phase 1 — Tag survival (M1) | https://github.com/GonzRon/noteNFC/issues/30 |
| 31 | create | [MVP] noteNFC tag payload format v1 and legacy md5_short resolver | Phase 1 — Tag survival (M1) | https://github.com/GonzRon/noteNFC/issues/31 |
| 32 | create | [MVP] Testing pyramid and CI gates | Phase 0 — Foundation | https://github.com/GonzRon/noteNFC/issues/32 |
| 33 | create | [NEXT] Todoist authentication: personal API token first, OAuth later | Phase 5 — Todoist | https://github.com/GonzRon/noteNFC/issues/33 |
| 34 | create | [NEXT] Todoist deep-link actions into noteNFC | Phase 5 — Todoist | https://github.com/GonzRon/noteNFC/issues/34 |
| 35 | create | [MVP] Untrusted input policy: tag payloads, deep links, stored URIs | Phase 1 — Tag survival (M1) | https://github.com/GonzRon/noteNFC/issues/35 |

## Differences from the approved plan

None. All 8 milestones were created byte-exact from the README's em-dash titles, all 19 new
issues were created before any rewrite (so slug references could be substituted with real
issue numbers), all 16 existing issues (#1-#16) were rewritten with the title/body/milestone
from their `rewrite-NN.md` file (issue #1 correctly received no milestone, per
`milestone: ""` in `rewrite-01.md`), and every `` `new-<slug>` ``/`#new-<slug>` token across
all 35 bodies was substituted with the corresponding `#<number>`. Verified no unresolved
`new-<slug>` reference token remains live anywhere (the one literal string `new-generation`
found in issue #31's body is an ordinary English compound adjective, not a slug reference,
and was left untouched by design). No issues are closed; total issue count is 35 (16 + 19),
consistent with the mid-task addition noted for this run.

## Verification performed

- `gh issue list --repo GonzRon/noteNFC --state all --limit 100 --json number,title,milestone,state`
  returned 35 issues, 0 closed, milestones matching the README mapping table exactly.
- Fetched all 35 issue bodies (`gh issue view N --json body`) and grepped for `new-`; the only
  hit was the non-slug word "new-generation" in issue #31.
- Spot-read issues #1, #9, and #23 with `gh issue view`; confirmed titles, milestones, and
  (#9) that slug substitutions resolved to the correct issue numbers (OAuth -> #33, deep-link
  contract -> #19, provider radio -> #25, ReminderProvider -> #28, link actions -> #34).

