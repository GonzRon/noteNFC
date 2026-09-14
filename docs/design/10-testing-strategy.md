# Testing strategy

Status: design-phase document, 2026-09-14. Today the project has no test source set (and the
directories are gitignored). The pyramid below is sized for one developer and CI on GitHub
Actions.

## 1. The pyramid

| Layer | Where | Runner | Share | What it proves |
|---|---|---|---|---|
| Pure domain unit tests | `:core/src/test` | JUnit 5 + kotlin.test, no Android | ~70 % of tests, < 10 s | recurrence, seasons, status, codecs, link policy, backup format, projection diffing, interpretation rules |
| Android unit tests | `:app/src/test` | Robolectric + Room in-memory + WorkManager test utils + `ShadowAlarmManager` | ~25 % | DAOs and queries, migrations, prefs migration, alarm re-arm, notification content, health detectors, backup I/O |
| Instrumented smoke | `:app/src/androidTest` | device/emulator, Compose test rule | a handful | NFC dispatch intent routing, Compose screens render, share-sheet entry |
| Manual device checklist | per phase exit criteria | the developer's NFC phone | short list | real tag write/read-back, notification delivery after reboot, Todoist round trip |

Rules: tests before code on every use case (RED/GREEN); `:core` stays Android-free so its tests
run in milliseconds; no assertion depends on wall-clock time (a `Today`/`Clock` port is injected
everywhere); fakes over mocks (in-memory repository fakes in `:core/testFixtures`).

## 2. Deterministic scheduling suite (the crown jewel)

Each row is at least one test; the D5 worked examples are encoded verbatim as tests.

| Area | Cases |
|---|---|
| FIXED cadence | anchor in future/past at creation; early, late, very-late completion; multiple skipped series dates; day/week/month/year units; k-multiplier vs iterative drift (Jan 31 + 3 × 1 month) |
| COMPLETION cadence | first due = anchor; completion moves series; early/late; edit interval after completion |
| Calendar arithmetic | month-end clamp, Feb 29 anchor across leap/common years, year wrap, 53-week years |
| Usage | baseline from anchor_meter vs from completion; DUE_SOON/DUE thresholds; readings out of order by date; lower-than-previous reading; NO_DATA |
| Combined OR | time due first, meter due first, both; completion advances both |
| Status | OK/DUE_SOON/DUE/OVERDUE boundaries inclusive/exclusive; lead 0; worst-of |
| Seasons | inside/outside for normal and wrap-around windows; boundary days; year-round; AT_START with offsets 0/3; RESUME_CLAMPED inside vs outside coming season; IGNORE schedules with anchors outside the window; season toggled on/off mid-year |
| Operations | snooze changes no date; postpone changes effective date only; completion clears both; edit clears postpone; pause/resume; archive |
| Rebuild | idempotent; pure (same inputs → same output); after event date edit; after event delete; after re-pointing an event to another schedule |
| Properties (jqwik or kotest-property, optional) | status monotone in T between events; FIXED due dates always on the series; rebuild(rebuild(x)) == rebuild(x); backup round-trip is identity on canonical tables |

## 3. Other `:core` suites

- `NdefCodecTest`: v1 encode/decode round-trip, exact byte layout, unknown version, wrong length,
  non-zero flags, legacy valid/invalid keys, foreign records, empty message, extra records.
- Legacy codec decode tests in `NdefCodecTest` (kept, best-effort recognition); `LegacyKeyTest` is retired with `LegacyKey` in Phase 1B (vectors remain documented in D1/D6).
- `LinkLaunchPolicyTest`: kind detection, blocked schemes, URI extraction from share text with a
  title, malformed URIs.
- `BackupCodecTest`: round-trip, ID preservation, unknown tables tolerated, newer format refused,
  older format upgraded, manifest hash mismatch detected, Merge policy (newer `updated_at` wins,
  conflicts reported).
- `TemplateApplierTest`: applying a template twice is idempotent.
- `StockLedgerTest`: COUNT/DELTA arithmetic and ordering.
- `TodoistRepresentationTest` (Phase 5): eligibility for `NATIVE_RECURRING` (completion-relative,
  time-only, year-round, supported units) vs `MANAGED_OCCURRENCE`; representation flips on rule
  edit; recurrence-string generation (`every! 90 days`).
- `TodoistInterpretationTest` (Phase 5): remote change → local effect table for both
  representations; native completion detected from an activity-log event and consumed exactly
  once; Todoist-computed next date verified against the canonical one and a `SYNC_DUE` emitted on
  mismatch; replay safety.

## 4. `:app` suites

- Room: every migration (Room's migration test helper against exported schemas), DAO queries (time
  series ordering, dashboard sort by `effective_due_on`, unique constraints raise as expected).
  With Room 3.0 and the bundled SQLite driver these run as plain JVM tests without Robolectric
  (spike S1 confirms); Robolectric remains for tests that need Android framework classes.
- `ResolveTagTest` for every resolution outcome, including unknown legacy tag → "Legacy tag" screen (no migration/re-link tests: dropped by D13).
- Reminders: alarm armed after `BOOT_COMPLETED`/`TIME_SET`/`TIMEZONE_CHANGED`; digest content for a
  fixture DB; quick-action receiver creates exactly one event; nonce check rejects a forged
  broadcast; health detectors each produce their finding under the simulated condition.
- Backup I/O: export to a temp `content://` (Robolectric `ContentResolver` shadow) and import back.
- Attachments: store contract test executed against `LocalAttachmentStore` and a fake tree
  provider; permission-lost finding.
- Todoist client: `MockWebServer` scenarios — command uuid reuse, 401/477, 429 backoff, sync token
  continuity, activity-log pagination for `item:completed`, outbox crash-between-write-and-ack
  recovery.

## 5. Process gates

- A change that touches `src/` without touching tests is rejected in review.
- Failing-set diff, not net count, when comparing test runs.
- Guards are re-proven: each health detector test also asserts the detector is silent when the
  condition is absent (positive and negative control).
- CI: `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug` on push; instrumented smoke is
  manual or on a self-hosted device when available.

## 6. What is intentionally not automated

- Real NFC hardware behaviour (tag capacity, lock bits, OEM NFC stacks): manual checklist in the
  phase exit criteria; the codec seam keeps this surface to two functions (`readRaw`, `writeRaw`).
- Notification delivery timing under Doze on specific OEMs: manual; the backstop worker is the
  design answer, not a test.
- Todoist UI rendering of custom-scheme links: spike S3 in D8, manual.
- Todoist's own recurrence arithmetic for FIXED rules (late and rescheduled completions): spike S8
  in D8, manual against a real account; the automated suite only tests our verification logic.
