# Split Phase F — nfc-tag-core extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the product-neutral NFC mechanism both apps already run — the external-record envelope, the byte↔UUID helper, the read-before-write policy, reader mode, and the inspect / format / write / verify / lock adapter — into a NEW local repository `nfc-tag-core` that builds and tests standalone, with a provenance table in place of inherited history, a forbidden-knowledge scan as its first gate, and **no remote, no tag and no push** (those are §B.1, owner-gated).

**Architecture:** Nine commits on `master` of `~/Documents/Projects/AndroidStudioProjects/nfc-tag-core`, in runbook §A.3's row order: skeleton (row 1) → `nfc-core` in two halves (row 2) → `nfc-android` (row 3) → the scan (row 4) → `README.md` (row 5) → `ci.yml` (row 6) → the emulator suite (row 7) → the whole-phase proof and the evidence section. Every library file is copied from the **ServiceTag tree at product-split `63602d1`** (the post-Phase-D state; the target's provenance table was written against `ac523d7` and both lineages are recorded), then changed exactly as target §4.6's "Change on extraction" column says. One thing comes from NoteTag: the pure-JVM size arithmetic (F-2). The consumers are **not** touched: Phase G (§A.4) is where ServiceTag and NoteTag adopt the library.

**Tech Stack:** the estate's pins, unchanged — AGP 9.4.0 (Kotlin built in; **never apply `org.jetbrains.kotlin.android`**), Kotlin 2.4.20, Gradle 9.7.1 wrapper copied from ServiceTag, JDK 17 toolchain via foojay with the same daemon-JVM pin file, JUnit 5.11.4 + `kotlin.test` in `nfc-core`, JUnit 4.13.2 + androidx.test runner 1.7.0 / ext-junit 1.3.0 in `nfc-android`. `nfc-core` has **zero** third-party, application or framework runtime dependencies (Kotlin stdlib only). `nfc-android` depends on `nfc-core` and nothing else.

**Spec:** `docs/architecture/product-split-migration.md` **§A.3** (sequence F: the seven-row table, the whole-phase verify and rollback) and `docs/architecture/product-split-target.md` **§3.1** (names, packages, plugins, version), **§4.1** (layout), **§4.2** (the public API), **§4.3** (invariants 1–9 and 13 are the library's), **§4.4** (the scan), **§4.5** (the test plan), **§4.6** (provenance), **§4.7** (what stays out), **§6.1** (the catalog-alias rule; repositories only in the root settings file), **§9** (CI shape), **§10.3** (versioning). Where this plan and those sections disagree, **the plan loses** — except for the four amendments below, which exist precisely because the target is silent or inconsistent, and which the owner rules on at this review.

---

## Owner decisions requested at this review (rule before RELEASE)

| # | Proposal | Why | If refused |
|---|---|---|---|
| **F-1** | `WriteResult.Failed(reason: String, cause: Throwable? = null)` — `TagWriter.write`/`format` fold tag I/O into `Failed` **and keep the exception** | Phase E residual R4: nothing in either consumer can log the underlying `TagLostException`/`IOException` once it has been folded into a sentence; `inspect` already propagates (invariant 8), `write` did not. Additive; the target's `Failed(reason)` call shape still compiles | drop the parameter in Task 4; R4 is then dispositioned by consumers logging at `inspect` only |
| **F-2** | `NdefSize.serialisedSize(records: List<NdefRecordData>): Int` in **`nfc-core`** (pure arithmetic: per record `1 + 1 + (1 if payload < 256 else 4) + type + payload`, no TLV), lifted from NoteTag `b0ec89c`; `nfc-android` keeps target §4.2's `List<NdefRecordData>.serialisedSize()` as the platform figure; the emulator suite pins the two equal and equal to `toNdefMessage().toByteArray().size` | Target §4.2 puts `serialisedSize()` in `nfc-android` (it needs `NdefMessage`), but §4.5 lists "generic payload limits … `serialisedSize()` … asserted to be exactly `toNdefMessage().toByteArray().size`" under the **`nfc-core` JVM** suite, which cannot call Android. Two consumers need the JVM figure: NoteTag's `WritePlanner` decides off-device (Phase E, `b0ec89c`), and ServiceTag's design-time budget test currently sums `3 + type + payload` **plus a TLV allowance the target calls wrong** (§4.3 invariant 7) | keep the arithmetic in NoteTag; `nfc-core`'s limits test asserts the 51/95/49 B figures by hand-summing and the emulator pins only the `nfc-android` extension |
| **F-3** | `TagInspection.route(needed: Int): WriteRoute` in `nfc-android` (pure Kotlin, no `android.*` import): `Format` when `needsFormat`, else `ReadOnly` when `!writable`, else `TooSmall(maxSize, needed)` when `needed > maxSize`, else `Write` | §4.5's `nfc-android` unit tier says "the `-1` formatable case **routes to `format` and computes no verdict** against a fake `TagIo`", which presupposes a routing function the API list does not name. Both consumers make exactly this decision today, in the same order (ServiceTag `TagWriteController`, NoteTag `NoteTagWriteController`), and it is the one place invariant 7's two paths and invariant 9's "format computes no verdict" are expressible on the JVM | the unit tier shrinks to `TagInspection` defaults and the two-tap fake; each consumer keeps its own routing |
| **F-4** | The library's branch is **`master`**, pinned by `git init -b master` in Task 1, so all three repositories share one convention (noteNFC/ServiceTag and NoteTag are on `master`). Runbook §B.1's `git push -u origin main` becomes `master`, and its `gh repo create` line is struck: the empty remote already exists (owner, 2026-09-17) | The owner asked that the branch be chosen and pinned before the first push rather than inherited from GitHub's `default_branch` setting | `git init -b main` in Task 1; §B.1 unchanged |

One more small shape, **not** an amendment because the target already names the tokens: `OverwritePolicy.reason(existing, isSameIdentity): OverwriteReason` produces all six tokens (`EMPTY_TAG`, `SAME_TAG` included) and `decide(...)` maps the first two to `Proceed` and the rest to `Confirm(reason, detail)`. §4.5 asks for "the full matrix → the six tokens", which a `Proceed` that carries no reason could not satisfy.

On RELEASE the controller amends target §4.2/§4.5 and runbook §B.1 to match the accepted rows (one docs commit on `product-split`, before Task 1), so the plan never argues against the design it implements.

## Phase E residuals as design inputs (owner, 2026-09-17: "requirements Phase G must consciously disposition")

These are not Phase E reopeners. Each row says what **this phase** builds so the residual becomes unrepresentable or visible, and what **Phase G** must then do in each consumer. The README (Task 6) carries the Phase G column as consumer obligations.

| Residual (Phase E ledger) | Phase F — the library | Phase G — the consumers |
|---|---|---|
| **R1** orphan unconfirmed `LOCAL_REF` row per `NdefFormatable` tag: the format tap persisted uuid A, the second tap planned uuid B | `TagWriter.format(tag)` calls `NdefFormatable.format(null)` and returns **`Formatted`**: no payload is ever offered on the format path, and `Written` exists only with a verified read-back (there is no `verified = false`; target §4.2). `TagIo.format` is a separate operation from `TagIo.write`, so a consumer cannot hand a message to the format path by accident. `route()` (F-3) returns `Format` **before** any capacity figure exists, so nothing downstream can plan against it | NoteTag: when `route()` is `Format`, call `format` and persist **nothing** — no uuid is planned, no mapping is written, so there is nothing to orphan; the second tap plans and persists as today. ServiceTag: the `Verifying`/`awaitingVerify` branch becomes "formatted, hold it again to write"; the provisioned row's lifetime rule (deleted unless a verified write claimed it) is unchanged |
| **R2** the A1 test's fake kept the same `existing` across both taps, so its asserted `Confirm` shape was the fake's | Task 4's unit tier includes a **two-tap fake** whose inspection flips from `needsFormat = true, maxSize = -1` to `needsFormat = false, maxSize = N` after `format()`, proving the `Format` → `Formatted` → `Write` → `Written` sequence at the seam; the README documents the two-tap shape | each consumer's fake models the second tap's inspection (a formatted tag is `Empty`, not "our own content", because `format(null)` wrote nothing); NoteTag's controller test asserts the real `Confirm` shape |
| **R3** "finish writing the link" overstated what remained | `Formatted` carries no bytes, so the honest sentence is available | NoteTag's sentence becomes "Formatted the tag. Hold it to the phone again to write the link." (consumer wording, ratified at the Phase G review); ServiceTag's "Formatted and written (N bytes)…" is retired with the branch that produced it |
| **R4** the underlying read exception was swallowed and never logged | `Failed.cause` (F-1); `inspect` keeps propagating `IOException` (invariant 8) so the consumer sees the exception itself | both consumers `Log.w` the exception at the catch that turns it into a sentence, and rethrow `CancellationException` first (NoteTag's `onTag` catch is widened to match its A4/A12 siblings) |

## Global Constraints

- **Where the work happens.** A NEW local repository at `~/Documents/Projects/AndroidStudioProjects/nfc-tag-core`, created by Task 1 with `git init -b master`, author `GonzRon` set **locally** (repo config, the same name and e-mail the other two repositories use), **no remote, no tag, no push, no `gh` call**. `GonzRon/nfc-tag-core` on GitHub exists empty and stays untouched until §B.1. `git remote | wc -l` = 0 and `git tag | wc -l` = 0 at every task's end, and at FINAL.
- **The consumers are not touched.** No commit on `product-split` except this plan, the amendment commit and the evidence (Task 9); no commit in the NoteTag repository. `libs/nfc-tag-core`, the `android-library` alias in the apps' catalogs, `settings.gradle.kts` blocks, submodule pins: all §A.4 / Phase G.
- **Layout and names (target §3.1, §4.1).** `rootProject.name = "nfc-tag-core"`; modules `:nfc-core` (`kotlin.jvm`, `jvmToolchain(17)`) and `:nfc-android` (**`com.android.library` only**, `namespace = "com.loosecannon.nfc.tagcore.android"`, `minSdk 26`, `compileSdk 37`, `jvmTarget` inside `android { kotlin { compilerOptions { … } } }`); Kotlin roots `com.loosecannon.nfc.tagcore` and `com.loosecannon.nfc.tagcore.android`; `nfc-android` → `api(project(":nfc-core"))` and no other dependency; **no dependency back into either app, ever** (O15).
- **`nfc-core` is stdlib-only.** Its `main` imports only `kotlin.*`, `java.nio.ByteBuffer` and `java.util.UUID`; `./gradlew :nfc-core:dependencies --configuration runtimeClasspath` lists **only** `org.jetbrains.kotlin:kotlin-stdlib` (and nothing under it but the stdlib's own `annotations`). No coroutines, no serialization, no Android.
- **Repositories only in the root `settings.gradle.kts`** (`FAIL_ON_PROJECT_REPOS`), never in a module script — a module-level `repositories {}` block is green standalone and red when consumed (target §6.1).
- **Catalog discipline.** The library's `gradle/libs.versions.toml` pins **the same `agp` and `kotlin` as the apps** (`9.4.0`, `2.4.20`) and declares only aliases whose **names** exist in both apps' catalogs (`kotlin-jvm`, `junit-bom`, `junit-jupiter`, `junit-platform-launcher`, `kotlin-test`, `junit4`, `androidx-test-runner`, `androidx-test-ext-junit`) plus the one alias no app has yet, **`android-library`** — which Phase G adds to the apps. No `kotlin-android` alias anywhere.
- **No sentence in the library** (invariant 13). Return values carry tokens and raw evidence (`detail`, `description`, `reason` strings that are the *input* evidence, never a composed user sentence). `Failed.reason` is diagnostic prose for a log or a parenthesis, as it is today; consumers wrap it.
- **Invariants 1–9 and 13 (target §4.3) are tested in the library**; 10–12 are protocol and stay in the consumers (`TagWriteSession` deferred, §4.7). In particular: type gate before body parse (1), AAR never first and absent by default (2), structural read-back equality with the record id outside it (3), never `FLAG_READER_SKIP_NDEF_CHECK` (4), lower-case external type (5), capacity = exact serialised message with no TLV allowance, `ReadOnly` before `TooSmall`, `format(null)` then measure on the next tap (7, 9), asymmetric failure reporting (8).
- **The forbidden scan is verbatim target §4.4** — the same `WORDS` pattern, the same paths (`nfc-core/src`, `nfc-android/src`, `settings.gradle.kts`), `--include` for `*.kt *.kts *.xml *.toml *.md`. It runs before the build in CI and as a `check` dependency **wired only from the library's own root script**, so the wiring is invisible to an app build that includes the modules as subprojects. Every allow entry carries a reason and is inspected, not accepted; the expected entry count at the end of this phase is **zero**. Test fixtures count as code: neither `com.loosecannon.servicetag` nor `com.loosecannon.notetag` nor "Joplin" appears anywhere under `src/` — the byte totals a test asserts (51 / 95 / 49 B) are reached with **neutral identities of the same length** (`com.example.twentysixchars` is 26 characters, `com.example.twentythree` is 23) and the README, which is outside the scan, says whose figures they are.
- **Provenance is a table, kept twice.** The README's table (Task 6) records, for every library file: the ServiceTag path it was copied from at product-split `63602d1`, the `ac523d7`-era path target §4.6 names, the `git log --follow` starting commit in the ServiceTag repository (or the NoteTag repository for `NdefSize`), and the change made on extraction. Task 9 proves every hash resolves. A file's first line never claims verbatim-ness it does not have.
- **Instrumented runs are emulator-only.** Every `connectedDebugAndroidTest` and `adb` call carries `ANDROID_SERIAL=emulator-5554` (or `adb -s emulator-5554`) on the same command line; the phone is never a target. The library's test APK is `com.loosecannon.nfc.tagcore.android.test`; installing it touches no app's data. Anything that needs a real chip (`inspect`, `write`, `format`, `lock` on a `Tag`) is a physical-tag row in the runbook's §D, never an automated test.
- **No personal data in tracked files or reports.** No owner paths (write `~`), no device serials other than `emulator-5554`, no usernames, no e-mail addresses in the plan or README, no tag UIDs. Commit messages are single casual subject lines: no body, no trailers, no attribution of any kind.
- **Per-task gate:** `./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :nfc-android:assembleDebug --console=plain` from the library root, plus `bash tools/forbidden-scan.sh` from Task 5 on. Each task is one commit on `master`. **Rollback** (§A.3): delete the directory; nothing references it yet.
- **What this phase does not claim.** No gate passes here. The `nfc-tag-core-v0.1.0` tag is cut at §B.1 once the owner releases the remote step, and is final only when both apps are green against it (§A.4, §B.6, target §10.3). The phase ends recorded as "**Phase F local extraction complete; the v0.1.0 tag and the push wait for §B.1**".

## The names, typed once

| Thing | Value | Where |
|---|---|---|
| repository directory | `~/Documents/Projects/AndroidStudioProjects/nfc-tag-core` | Task 1 |
| branch | `master` (F-4) | `git init -b master`, Task 1 |
| `rootProject.name` | `nfc-tag-core` | `settings.gradle.kts` |
| Gradle paths | `:nfc-core`, `:nfc-android` | `settings.gradle.kts` |
| Kotlin roots | `com.loosecannon.nfc.tagcore` / `com.loosecannon.nfc.tagcore.android` | every `package` line |
| Android namespace | `com.loosecannon.nfc.tagcore.android` | `nfc-android/build.gradle.kts` |
| test APK id | `com.loosecannon.nfc.tagcore.android.test` | AGP default |
| SDK levels | `minSdk 26`, `compileSdk 37` | `nfc-android/build.gradle.kts` |
| neutral identities in tests | `com.example.app` (+ AAR `com.example.app`), `com.example.other`, `com.example.twentysixchars` (26 chars → 51 / 95 B), `com.example.twentythree` (23 chars → 49 B with a 19-byte body) | test sources |
| provisional NTAG213 budget | `NTAG213_MAX_MESSAGE_BYTES = 137` **[unobserved]**, a test constant, re-pinned from the physical §D Session 1 tap 2 | `EnvelopeLimitsTest` |
| version | none yet; `nfc-tag-core-v0.1.0` is §B.1's | — |

## File structure

```
nfc-tag-core/
  settings.gradle.kts  build.gradle.kts  gradle/libs.versions.toml  gradle.properties
  gradle/gradle-daemon-jvm.properties  gradlew  gradlew.bat  gradle/wrapper/*  .gitignore   (Task 1)
  nfc-core/build.gradle.kts                                                              (Task 1)
    src/main/kotlin/com/loosecannon/nfc/tagcore/
      NdefRecordData.kt        verbatim (Task 2)
      TagIdentity.kt           verbatim minus one KDoc paragraph (Task 2)
      TagContent.kt            Recognised(body) / Foreign(description) / Empty (Task 2)
      NdefEnvelope.kt          encode / decode / applicationRecord over a TagIdentity parameter (Task 2)
      UuidBytes.kt             toBytes / fromBytes / requireCanonical (Task 3)
      OverwritePolicy.kt       ExistingContent, OverwriteReason, OverwriteDecision, OverwritePolicy (Task 3)
      NdefSize.kt              serialisedSize, pure arithmetic (F-2) (Task 3)
    src/test/kotlin/com/loosecannon/nfc/tagcore/
      NdefEnvelopeTest.kt  TagIdentityTest.kt  (Task 2)
      UuidBytesTest.kt  OverwritePolicyTest.kt  NdefSizeTest.kt  EnvelopeLimitsTest.kt  (Task 3)
  nfc-android/build.gradle.kts  src/main/AndroidManifest.xml                              (Task 1)
    src/main/kotlin/com/loosecannon/nfc/tagcore/android/
      NdefBridge.kt            verbatim + serialisedSize() (Task 4)
      NfcReaderModeSession.kt  verbatim, doc comment intact (Task 4)
      TagWriter.kt             TagInspection, WriteResult, TagWriter: inspect / write / format / lock (Task 4)
      TagIo.kt                 TagHandle, NfcTagHandle, TagIo, RealTagIo (Task 4)
      WriteRoute.kt            route(needed) (F-3) (Task 4)
    src/test/kotlin/com/loosecannon/nfc/tagcore/android/
      WriteRouteTest.kt  TwoTapFakeTest.kt  FakeTagIo.kt  (Task 4)
    src/androidTest/AndroidManifest.xml  (TestActivity)                                    (Task 8)
    src/androidTest/kotlin/com/loosecannon/nfc/tagcore/android/
      TestActivity.kt  NdefBridgeDeviceTest.kt  NfcReaderModeSessionDeviceTest.kt          (Task 8)
  tools/forbidden-scan.sh  tools/forbidden-scan.allow                                      (Task 5)
  README.md                                                                                (Task 6)
  .github/workflows/ci.yml                                                                 (Task 7)
```

## Provenance — the table the README carries (target §4.6, re-read against the tree actually copied)

Every "from" path is in the ServiceTag worktree (`product-split` at `63602d1`, code tree unchanged since `df14670`) unless marked NoteTag. Start points were re-derived with `git log --follow` in that worktree on 2026-09-17; where Phase D created a file the target's table describes as "NEW type", both facts are given.

| NEW file | Copied from (product-split `63602d1`) | `ac523d7`-era origin (target §4.6) | `git log --follow` start | Change on extraction |
|---|---|---|---|---|
| `nfc-core/…/NdefRecordData.kt` | `core/src/main/kotlin/com/loosecannon/servicetag/core/nfc/NdefCodec.kt:7-12` | `core/…/core/nfc/NdefCodec.kt:8-12` | `76b751a` | own file; package line only |
| `nfc-core/…/TagIdentity.kt` | `core/…/servicetag/core/nfc/TagIdentity.kt` (whole file, 34 lines) | NEW type parameterising `NdefCodec.DOMAIN` / `V1_TYPE_NAME` / `PACKAGE_NAME` | `5c075ea` (Phase D created the file); the constants it replaced start at `76b751a` (`DOMAIN`, normalised `b9f7e51`) and `f92a391` | package line; the KDoc paragraph "Phase F moves this type verbatim…" removed |
| `nfc-core/…/TagContent.kt` | `NdefCodec.kt:14-24` (`TagPayload`) | `NdefCodec.kt:21-26` | `76b751a` | `Recognised(body)` replaces `V1`/`NewerVersion`/`Malformed`; `Foreign`/`Empty` kept. `Malformed` and `NewerVersion` stay per app (§4.6, §4.7) |
| `nfc-core/…/NdefEnvelope.kt` | `NdefCodec.kt:32-43` (`decode`), `:61` (`encodeV1`), `:63-72` (`v1Record`), `:79-85` (`applicationRecord`), `:87-95` (constants) | `NdefCodec.decode :56-65`, `encodeV1 :90`, `v1Record :93-102`, `applicationRecord :104-109` | `76b751a`, `f92a391` | identity is a parameter of every call; the type switch collapses to mine / not mine; the body is returned unparsed; the AAR is appended only when `aarPackage != null`; the v1 layout (`version|flags|UUID`) does **not** come along |
| `nfc-core/…/UuidBytes.kt` | `NdefCodec.kt:55-56, 65-70, 98-106` | `decodeV1`/`v1Record` halves + `requireCanonicalUuid :111-120` | `f92a391` | takes `String`/`ByteArray`, not `TagId`; the layout it lived in stays behind |
| `nfc-core/…/OverwritePolicy.kt` | `core/…/servicetag/core/nfc/OverwritePolicy.kt` (whole file, 26 lines) | same path | `f92a391` | `decide(existing: TagPayload, intended: TagId)` → `decide(existing: ExistingContent, isSameIdentity: Boolean)`; the five sentences become tokens + `detail` |
| `nfc-core/…/NdefSize.kt` | **NoteTag** `core/src/main/kotlin/com/loosecannon/notetag/core/nfc/NdefSize.kt` (whole file, 14 lines) | — (Phase E) | NoteTag `b0ec89c` | package line; KDoc says "writer" not "planner" |
| `nfc-android/…/NdefBridge.kt` | `app/src/main/kotlin/com/loosecannon/servicetag/nfc/NdefBridge.kt` (whole file, 43 lines) | same path | `dc1bb1c` | package + import; `serialisedSize()` added; `Intent.nfcTag()` becomes live API |
| `nfc-android/…/NfcReaderModeSession.kt` | `app/…/servicetag/nfc/NfcReaderModeSession.kt` (whole file, 39 lines) | same path | `dc1bb1c`, corrected `e2cf1d0` | package line only; the doc comment is carried verbatim |
| `nfc-android/…/TagWriter.kt` | `app/…/servicetag/nfc/TagWriter.kt` (whole file, 126 lines) | same path | `dc1bb1c`; throw contracts `bdcc475`; lock-after-read-back and the unlocked format path `e2cf1d0` | the `codec.decode` call goes (the caller classifies); `TagInspection.existing` → `unreadable: String?`; `Written` loses `verified` (verified-only); `format(tag)` = `format(null)` → `Formatted`; `write` refuses a formatable-only tag; `Failed.cause` (F-1) |
| `nfc-android/…/TagIo.kt` | `app/src/main/kotlin/com/loosecannon/servicetag/ui/scan/TagWriteController.kt:27-65` | same path `:32-65` | `c808b49` | moved out of a UI file; `RealTagIo` takes no codec; `format` added to the seam |
| `nfc-android/…/WriteRoute.kt` | the routing both controllers do inline (`TagWriteController`, NoteTag `NoteTagWriteController`) | — | `c808b49`; NoteTag `840e6ba` | NEW (F-3) |
| `nfc-core/src/test/…/NdefEnvelopeTest.kt` | `core/src/test/…/servicetag/core/nfc/NdefCodecTest.kt` (37 lines) + the sibling cases of `NdefEnvelopeIsolationTest.kt` (63 lines) + `NdefCodecV1Test.kt`'s envelope cases | `NdefCodecTest.kt`; `NdefCodecV1Test.kt` | `76b751a` (legacy cases trimmed `26ec9d0`); `6bf38e8` (isolation, Phase D); `f92a391` | product identities → neutral ones; `evernoteEraTypeIsForeign`/`aNoteTagRecordIsForeign` become the parameterised sibling-isolation case; the version/flags/`Malformed` cases stay with the app |
| `nfc-core/src/test/…/TagIdentityTest.kt` | `core/src/test/…/servicetag/core/nfc/TagIdentityTest.kt` (29 lines) | — (Phase D) | `b4b016b` | package line; two cases added (`externalType`, `aarPackage` default) |
| `nfc-core/src/test/…/UuidBytesTest.kt`, `EnvelopeLimitsTest.kt` | `NdefCodecV1Test.kt` (`refusesNonCanonicalIdOnEncode`, `decodedIdIsCanonicalLowercase`, `exactByteLayout`, `theWholeMessageIs95Bytes`, `fitsAnNtag213`) | `NdefCodecV1Test.kt` | `f92a391` | the helper and limit cases are extracted; totals reached with neutral identities of the same length; the TLV `+ 3` is dropped (invariant 7) |
| `nfc-core/src/test/…/OverwritePolicyTest.kt` | `core/src/test/…/servicetag/core/nfc/OverwritePolicyTest.kt` (24 lines) | same path | `f92a391` | asserts tokens instead of sentences; the full matrix |
| `nfc-core/src/test/…/NdefSizeTest.kt` | **NoteTag** `core/src/test/…/notetag/core/nfc/NdefSizeTest.kt` (37 lines) | — | NoteTag `b0ec89c` | neutral identity; the 49 B case keeps its body length |
| `nfc-android/src/androidTest/…/NdefBridgeDeviceTest.kt` | **NoteTag** `app/src/androidTest/…/notetag/nfc/NdefSizeDeviceTest.kt` (84 lines) — the four platform-agreement cases | — | NoteTag `f037060` | generalised: no product codec; adds the bridge round-trips, the `Intent` extras and the AAR pin from target §4.5 |

Deliberately **not** moved (target §4.6): `LegacyKey.compute`, `TagRoute`/`DeepLinkRoute`/`LinkLaunchPolicy`, `TagWriteSession`; and the per-app body schemes, `Malformed`, `NewerVersion`.

---

### Task 1 (§A.3 row 1): the skeleton — two empty modules that build standalone

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, `.gitignore`, `nfc-core/build.gradle.kts`, `nfc-android/build.gradle.kts`, `nfc-android/src/main/AndroidManifest.xml`
- Copy from the ServiceTag worktree (`~/Documents/Projects/AndroidStudioProjects/ServiceTag-split`): `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/gradle-daemon-jvm.properties`

**Interfaces:**
- Produces: the two Gradle paths `:nfc-core` and `:nfc-android`, the catalog aliases every later task uses, the per-task gate command.

- [ ] **Step 1: The repository**

```bash
cd ~/Documents/Projects/AndroidStudioProjects
test ! -e nfc-tag-core || { echo "nfc-tag-core already exists; stop"; exit 1; }
mkdir nfc-tag-core && cd nfc-tag-core
git init -b master
git config user.name "$(git -C ../ServiceTag-split config user.name)"
git config user.email "$(git -C ../ServiceTag-split config user.email)"
git config user.name          # expected: GonzRon
mkdir -p gradle/wrapper nfc-core/src/main/kotlin/com/loosecannon/nfc/tagcore nfc-core/src/test/kotlin/com/loosecannon/nfc/tagcore \
         nfc-android/src/main/kotlin/com/loosecannon/nfc/tagcore/android nfc-android/src/test/kotlin/com/loosecannon/nfc/tagcore/android tools
cp ../ServiceTag-split/gradlew ../ServiceTag-split/gradlew.bat .
cp ../ServiceTag-split/gradle/wrapper/gradle-wrapper.jar ../ServiceTag-split/gradle/wrapper/gradle-wrapper.properties gradle/wrapper/
cp ../ServiceTag-split/gradle/gradle-daemon-jvm.properties gradle/
grep distributionUrl gradle/wrapper/gradle-wrapper.properties   # expected: gradle-9.7.1-bin.zip
```

- [ ] **Step 2: `settings.gradle.kts`** (repositories live here and nowhere else — target §6.1)

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Read only when this library IS the root build. An app that includes the two modules as
// subprojects reads its own settings file and never sees this one (target §4.1, §6.1).
rootProject.name = "nfc-tag-core"
include(":nfc-core", ":nfc-android")
```

- [ ] **Step 3: `gradle/libs.versions.toml`** — the same `agp`/`kotlin` pins as both apps; only alias names that exist in both apps' catalogs, plus `android-library`

```toml
[versions]
agp = "9.4.0"
kotlin = "2.4.20"
junit = "5.11.4"
junit4 = "4.13.2"
androidxTestRunner = "1.7.0"
androidxTestExtJunit = "1.3.0"

[libraries]
junit-bom = { group = "org.junit", name = "junit-bom", version.ref = "junit" }
junit-jupiter = { group = "org.junit.jupiter", name = "junit-jupiter" }
junit-platform-launcher = { group = "org.junit.platform", name = "junit-platform-launcher" }
kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test", version.ref = "kotlin" }
junit4 = { group = "junit", name = "junit", version.ref = "junit4" }
androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "androidxTestRunner" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestExtJunit" }

[plugins]
# `android-library` exists in no app catalog yet; Phase G adds it to both (target §6.1, §A.4 task 1).
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

- [ ] **Step 4: root `build.gradle.kts`** — plugins `apply false`; the scan task is registered here in Task 5, so the file is short for now

```kotlin
plugins {
    base
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
```

- [ ] **Step 5: `gradle.properties`** (the apps' five lines; `android.useAndroidX` is what the androidx.test artifacts need)

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 6: `.gitignore`**

```gitignore
# build output
build/
/captures
.externalNativeBuild
.cxx

# IDE and local machine
.idea/
*.iml
.DS_Store
local.properties

# gradle local state (the wrapper, settings and version catalog ARE tracked)
.gradle/
.kotlin/

# superpowers scratch
.superpowers/
```

- [ ] **Step 7: `nfc-core/build.gradle.kts`** — `kotlin.jvm`, JUnit 5, and **no runtime dependency line at all**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

// Runtime: the Kotlin stdlib the plugin adds, and nothing else (target §4.1, invariant 13).
dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

- [ ] **Step 8: `nfc-android/build.gradle.kts`** — `com.android.library` only; `jvmTarget` inside the android block exactly as `app/build.gradle.kts` does; `api(project(":nfc-core"))` and nothing else at runtime

```kotlin
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.loosecannon.nfc.tagcore.android"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP 9.4 carries Kotlin built in: no kotlin.android plugin anywhere (target §3.1).
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    testOptions {
        unitTests.all { it.jvmArgs("--enable-native-access=ALL-UNNAMED") }
    }
}

dependencies {
    api(project(":nfc-core"))

    testImplementation(libs.junit4)

    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
```

- [ ] **Step 9: `nfc-android/src/main/AndroidManifest.xml`** — empty on purpose: the NFC permission and every intent filter belong to the consuming app (target §5)

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 10: Verify the skeleton builds standalone**

Run: `./gradlew projects --console=plain`
Expected: the tree lists `Project ':nfc-android'` and `Project ':nfc-core'` under `Root project 'nfc-tag-core'`.

Run: `./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :nfc-android:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL` (no sources yet; the point is that both plugins configure and the alias set resolves).

Run: `./gradlew :nfc-core:dependencies --configuration runtimeClasspath --console=plain | grep -- '---'`
Expected: exactly one dependency line, `\--- org.jetbrains.kotlin:kotlin-stdlib:2.4.20`, possibly with `org.jetbrains:annotations` beneath it and nothing else.

Run: `git status --short | grep -c 'gradle-wrapper.jar'`
Expected: `1` (the wrapper jar is tracked, as in both apps).

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "skeleton: two empty modules that build on their own"
git remote | wc -l    # expected: 0
```

---

### Task 2 (§A.3 row 2, first half): `NdefRecordData`, `TagIdentity`, `TagContent`, `NdefEnvelope` and their JVM suite

**Files:**
- Create: `nfc-core/src/main/kotlin/com/loosecannon/nfc/tagcore/{NdefRecordData,TagIdentity,TagContent,NdefEnvelope}.kt`
- Test: `nfc-core/src/test/kotlin/com/loosecannon/nfc/tagcore/{NdefEnvelopeTest,TagIdentityTest}.kt`

**Interfaces:**
- Consumes: nothing but the skeleton.
- Produces: `NdefRecordData(tnf: Int, type: ByteArray, payload: ByteArray)` (value equality over bytes); `TagIdentity(externalDomain, typeName, aarPackage: String? = null)` with `externalType`; `TagContent.Recognised(body)` / `Foreign(description)` / `Empty`; `NdefEnvelope.TNF_EXTERNAL_TYPE = 0x04`, `AAR_TYPE = "android.com:pkg"`, `encode(identity, body): List<NdefRecordData>`, `decode(identity, records): TagContent`, `applicationRecord(packageName): NdefRecordData`.

- [ ] **Step 1: The two verbatim files.** Copy `NdefCodec.kt` lines 7–12 into `NdefRecordData.kt` under the new package, and `TagIdentity.kt` whole, dropping the one paragraph that talks about Phase F:

```bash
S=~/Documents/Projects/AndroidStudioProjects/ServiceTag-split
D=nfc-core/src/main/kotlin/com/loosecannon/nfc/tagcore
{ echo 'package com.loosecannon.nfc.tagcore'; echo; sed -n '7,12p' $S/core/src/main/kotlin/com/loosecannon/servicetag/core/nfc/NdefCodec.kt; } > $D/NdefRecordData.kt
sed -e 's/^package com.loosecannon.servicetag.core.nfc$/package com.loosecannon.nfc.tagcore/' \
    -e '/^ \* Phase F moves this type verbatim/,/^ \*$/d' \
    $S/core/src/main/kotlin/com/loosecannon/servicetag/core/nfc/TagIdentity.kt > $D/TagIdentity.kt
grep -c 'Phase F' $D/TagIdentity.kt   # expected: 0
sed -n '1,8p' $D/TagIdentity.kt        # the KDoc now runs straight from "the caller supplies them." to "@param externalDomain"
```

`NdefRecordData.kt` must read exactly:

```kotlin
package com.loosecannon.nfc.tagcore

/** Android-free view of one NDEF record (mirrors android.nfc.NdefRecord's tnf/type/payload). */
data class NdefRecordData(val tnf: Int, val type: ByteArray, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is NdefRecordData && tnf == other.tnf && type.contentEquals(other.type) && payload.contentEquals(other.payload)
    override fun hashCode(): Int = 31 * (31 * tnf + type.contentHashCode()) + payload.contentHashCode()
}
```

- [ ] **Step 2: Write the failing envelope tests** — `NdefEnvelopeTest.kt`. Every identity is neutral; the sibling case is the template each app copies (invariant 1).

```kotlin
package com.loosecannon.nfc.tagcore

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The envelope's own rules, on identities that belong to no real product. */
class NdefEnvelopeTest {
    private val ours = TagIdentity("com.example.app", "tag", "com.example.app")
    private val lone = TagIdentity("com.example.app", "tag")            // no AAR: the default (O13)
    private val body = byteArrayOf(0x01, 0x00, 0x7f, 0x00, 0x10)

    private fun external(type: String, payload: ByteArray) =
        NdefRecordData(NdefEnvelope.TNF_EXTERNAL_TYPE, type.toByteArray(Charsets.US_ASCII), payload)

    // ---- round trip and layout ------------------------------------------------------------

    @Test fun roundTripsByteForByte() {
        val decoded = NdefEnvelope.decode(ours, NdefEnvelope.encode(ours, body))
        assertIs<TagContent.Recognised>(decoded)
        assertContentEquals(body, decoded.body)
    }

    @Test fun theRecordIsTnfFourAsciiTypeAndTheBodyVerbatim() {
        val rec = NdefEnvelope.encode(ours, body).first()
        assertEquals(0x04, rec.tnf)
        assertContentEquals("com.example.app:tag".toByteArray(Charsets.US_ASCII), rec.type)
        assertContentEquals(body, rec.payload)
    }

    @Test fun theBodyComesBackUnparsed() {
        // A one-byte body and an empty body are both handed back as-is: what a body means is the caller's business.
        val one = NdefEnvelope.decode(ours, listOf(external("com.example.app:tag", byteArrayOf(0x09))))
        assertIs<TagContent.Recognised>(one); assertContentEquals(byteArrayOf(0x09), one.body)
        val none = NdefEnvelope.decode(ours, listOf(external("com.example.app:tag", ByteArray(0))))
        assertIs<TagContent.Recognised>(none); assertEquals(0, none.body.size)
    }

    // ---- record count and order (invariant 2, O13) ---------------------------------------

    @Test fun anIdentityWithoutAnAarWritesExactlyOneRecord() {
        assertEquals(1, NdefEnvelope.encode(lone, body).size)
    }

    @Test fun anIdentityWithAnAarWritesOursFirstThenTheAar() {
        val msg = NdefEnvelope.encode(ours, body)
        assertEquals(2, msg.size)
        assertContentEquals("com.example.app:tag".toByteArray(Charsets.US_ASCII), msg[0].type)
        assertEquals(0x04, msg[1].tnf)
        assertContentEquals("android.com:pkg".toByteArray(Charsets.US_ASCII), msg[1].type)
        assertContentEquals("com.example.app".toByteArray(Charsets.US_ASCII), msg[1].payload)
    }

    /** Pinned bytes: the platform's `NdefRecord.createApplicationRecord` produces exactly these (re-asserted on the emulator in Task 8). */
    @OptIn(ExperimentalStdlibApi::class)
    @Test fun theApplicationRecordBytesArePinned() {
        val aar = NdefEnvelope.applicationRecord("com.example.app")
        assertEquals(0x04, aar.tnf)
        assertEquals("616e64726f69642e636f6d3a706b67", aar.type.toHexString())
        assertEquals("636f6d2e6578616d706c652e617070", aar.payload.toHexString())
    }

    // ---- the type gate (invariant 1, C8) -------------------------------------------------

    @Test fun emptyMessageIsEmpty() = assertEquals(TagContent.Empty, NdefEnvelope.decode(ours, emptyList()))

    @Test fun onlyFirstRecordMatters() {
        val mine = NdefEnvelope.encode(lone, body).single()
        val foreign = external("com.example.other:tag", body)
        assertIs<TagContent.Recognised>(NdefEnvelope.decode(ours, listOf(mine, foreign)))
        assertIs<TagContent.Foreign>(NdefEnvelope.decode(ours, listOf(foreign, mine)))
    }

    @Test fun anotherDomainIsForeignAndTheDescriptionNamesTheTypeItSaw() {
        val decoded = NdefEnvelope.decode(ours, listOf(external("com.example.other:tag", body)))
        assertIs<TagContent.Foreign>(decoded)
        assertTrue(decoded.description.contains("com.example.other:tag"), "the refusal names the type, so a caller can say what the tag is")
    }

    @Test fun anotherTypeNameInOurDomainIsForeign() {
        assertIs<TagContent.Foreign>(NdefEnvelope.decode(ours, listOf(external("com.example.app:other", body))))
    }

    /** The dangerous case: a sibling record whose body is byte-for-byte a valid body of ours. Only the type gate tells them apart. */
    @Test fun aSiblingRecordCarryingOurOwnBodyIsStillForeign() {
        val ourBody = NdefEnvelope.encode(ours, body).first().payload
        assertIs<TagContent.Foreign>(NdefEnvelope.decode(ours, listOf(external("com.example.other:tag", ourBody))))
    }

    @Test fun ourTypeUnderTheWrongTnfIsForeign() {
        val rec = NdefRecordData(0x02, "com.example.app:tag".toByteArray(Charsets.US_ASCII), body)
        assertIs<TagContent.Foreign>(NdefEnvelope.decode(ours, listOf(rec)))
    }

    @Test fun aUriRecordIsForeign() {
        val uri = NdefRecordData(tnf = 0x01, type = byteArrayOf('U'.code.toByte()), payload = byteArrayOf(0x01) + "example.com".toByteArray())
        assertIs<TagContent.Foreign>(NdefEnvelope.decode(ours, listOf(uri)))
    }

    @Test fun anApplicationRecordAloneIsForeign() {
        assertIs<TagContent.Foreign>(NdefEnvelope.decode(ours, listOf(NdefEnvelope.applicationRecord("com.example.app"))))
    }

    /** A sibling's AAR in second place changes nothing: the platform reads the first record, and so do we. */
    @Test fun aSiblingAarDoesNotMakeATagOurs() {
        val sibling = external("com.example.other:tag", body)
        val siblingAar = NdefEnvelope.applicationRecord("com.example.other")
        assertIs<TagContent.Foreign>(NdefEnvelope.decode(ours, listOf(sibling, siblingAar)))
    }

    // ---- hostile input never throws --------------------------------------------------------

    @Test fun nonAsciiTypeBytesAreForeignNotAnException() {
        val rec = NdefRecordData(0x04, byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0x00), body)
        assertIs<TagContent.Foreign>(NdefEnvelope.decode(ours, listOf(rec)))
    }

    @Test fun anEmptyTypeIsForeign() {
        assertIs<TagContent.Foreign>(NdefEnvelope.decode(ours, listOf(NdefRecordData(0x04, ByteArray(0), body))))
    }

    @Test fun recognisedBodyIsACopyNotTheCallersArray() {
        val payload = body.copyOf()
        val decoded = NdefEnvelope.decode(ours, listOf(external("com.example.app:tag", payload)))
        payload[0] = 0x55
        assertIs<TagContent.Recognised>(decoded)
        assertEquals(0x01, decoded.body[0].toInt())
    }

    @Test fun recognisedEqualityIsOverBytes() {
        assertEquals(TagContent.Recognised(byteArrayOf(1, 2)), TagContent.Recognised(byteArrayOf(1, 2)))
        assertEquals(TagContent.Recognised(byteArrayOf(1, 2)).hashCode(), TagContent.Recognised(byteArrayOf(1, 2)).hashCode())
    }
}
```

- [ ] **Step 3: Write the failing identity tests** — `TagIdentityTest.kt`: the three ServiceTag cases verbatim (package line changed) plus two:

```kotlin
package com.loosecannon.nfc.tagcore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The identity's own guards. `TagIdentity` is the only product knowledge the envelope holds, so a
 * caller that hands it something that could never match the bytes on a tag has to fail at
 * construction, not quietly write a record nothing will ever decode (arch §2.9).
 */
class TagIdentityTest {

    @Test fun aBlankDomainIsRefused() {
        assertFailsWith<IllegalArgumentException> { TagIdentity("", "tag") }
    }

    @Test fun aBlankTypeNameIsRefused() {
        assertFailsWith<IllegalArgumentException> { TagIdentity("com.example.app", "   ") }
    }

    /**
     * `NdefRecord.createExternal` lower-cases both halves before joining, so an upper-case letter
     * here would name a type that is not the one on the tag.
     */
    @Test fun anUpperCaseExternalTypeIsRefused() {
        assertFailsWith<IllegalArgumentException> { TagIdentity("com.example.App", "tag") }
        assertFailsWith<IllegalArgumentException> { TagIdentity("com.example.app", "Tag") }
    }

    @Test fun theExternalTypeIsDomainColonName() {
        assertEquals("com.example.app:tag", TagIdentity("com.example.app", "tag").externalType)
    }

    @Test fun theAarPackageDefaultsToNone() {
        assertNull(TagIdentity("com.example.app", "tag").aarPackage)
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `./gradlew :nfc-core:test --console=plain`
Expected: compilation failure naming `TagContent` and `NdefEnvelope` (unresolved references).

- [ ] **Step 5: `TagContent.kt`**

```kotlin
package com.loosecannon.nfc.tagcore

/** What the envelope made of a message, before any product body parse. */
sealed interface TagContent {
    /**
     * The first record is ours: same TNF, same external type. [body] is the raw payload, unparsed
     * and copied — what it means is the caller's business, and only the caller can call it malformed.
     */
    class Recognised(body: ByteArray) : TagContent {
        val body: ByteArray = body.copyOf()
        override fun equals(other: Any?): Boolean = other is Recognised && body.contentEquals(other.body)
        override fun hashCode(): Int = body.contentHashCode()
        override fun toString(): String = "Recognised(${body.size} bytes)"
    }

    /** Not ours. [description] keeps the full offending type string so the caller can name it. */
    data class Foreign(val description: String) : TagContent

    data object Empty : TagContent
}
```

- [ ] **Step 6: `NdefEnvelope.kt`** — `NdefCodec.decode`/`encodeV1`/`v1Record`/`applicationRecord` with the identity as a parameter and no body scheme

```kotlin
package com.loosecannon.nfc.tagcore

/**
 * The external-record envelope, identity-parameterised. Android `NdefRecord` objects are built only
 * in `nfc-android`; this object never sees one. It knows no product: every call takes the
 * [TagIdentity] the caller supplies, and the body it carries is opaque bytes in both directions.
 */
object NdefEnvelope {
    const val TNF_EXTERNAL_TYPE: Int = 0x04

    /** Platform constant, not identity. */
    const val AAR_TYPE: String = "android.com:pkg"

    /**
     * The message for one tag: the external record carrying [body], plus an Application Record
     * appended **only** when `identity.aarPackage != null`. One record is the default (O13); the
     * AAR is never first — put it first and the tag stops matching the NDEF_DISCOVERED filter
     * (invariant 2).
     */
    fun encode(identity: TagIdentity, body: ByteArray): List<NdefRecordData> =
        listOfNotNull(
            NdefRecordData(TNF_EXTERNAL_TYPE, identity.externalType.toByteArray(Charsets.US_ASCII), body.copyOf()),
            identity.aarPackage?.let(::applicationRecord),
        )

    /**
     * First record of the message only, as the platform does. TNF gate, then exact-type gate, and
     * only then is the body handed back — unparsed (invariant 1, C8). Never throws on hostile bytes.
     */
    fun decode(identity: TagIdentity, records: List<NdefRecordData>): TagContent {
        val first = records.firstOrNull() ?: return TagContent.Empty
        val type = String(first.type, Charsets.US_ASCII)
        if (first.tnf != TNF_EXTERNAL_TYPE) return TagContent.Foreign("tnf=${first.tnf} type=$type")
        // A sibling product's record must come back Foreign even though its body might also parse.
        if (type != identity.externalType) return TagContent.Foreign("tnf=${first.tnf} type=$type")
        return TagContent.Recognised(first.payload)
    }

    /** The optional AAR a consumer may append itself. Byte-identical to `NdefRecord.createApplicationRecord`. */
    fun applicationRecord(packageName: String): NdefRecordData =
        NdefRecordData(
            tnf = TNF_EXTERNAL_TYPE,
            type = AAR_TYPE.toByteArray(Charsets.US_ASCII),
            payload = packageName.toByteArray(Charsets.US_ASCII),
        )
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :nfc-core:test --console=plain`
Expected: `BUILD SUCCESSFUL`; `nfc-core/build/test-results/test/` holds `TEST-com.loosecannon.nfc.tagcore.NdefEnvelopeTest.xml` (19 tests) and `TEST-…TagIdentityTest.xml` (5 tests), `failures="0"` in both.

Run: `grep -rn '^import' nfc-core/src/main | grep -v 'import kotlin\.' | sort -u`
Expected: no output (these four files import nothing).

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "nfc-core: the envelope, the identity and the record, with their suite"
```

---

### Task 3 (§A.3 row 2, second half): `UuidBytes`, `OverwritePolicy`, `NdefSize`, the limits suite, and the stdlib-only proof

**Files:**
- Create: `nfc-core/src/main/kotlin/com/loosecannon/nfc/tagcore/{UuidBytes,OverwritePolicy,NdefSize}.kt`
- Test: `nfc-core/src/test/kotlin/com/loosecannon/nfc/tagcore/{UuidBytesTest,OverwritePolicyTest,NdefSizeTest,EnvelopeLimitsTest}.kt`

**Interfaces:**
- Consumes: `NdefRecordData`, `TagIdentity`, `NdefEnvelope` (Task 2).
- Produces: `UuidBytes.LENGTH = 16`, `toBytes(UUID): ByteArray`, `fromBytes(bytes, offset = 0): UUID`, `requireCanonical(String): UUID`; `ExistingContent.Empty` / `Ours(detail)` / `OursUnsupported(detail)` / `Foreign(description)` / `Unreadable(reason)`; `enum OverwriteReason { EMPTY_TAG, SAME_TAG, OTHER_TAG_SAME_PRODUCT, SAME_PRODUCT_UNSUPPORTED, FOREIGN, UNREADABLE }`; `OverwriteDecision.Proceed` / `Confirm(reason, detail)`; `OverwritePolicy.reason(existing, isSameIdentity): OverwriteReason` and `decide(existing, isSameIdentity): OverwriteDecision`; `NdefSize.serialisedSize(records): Int` (F-2).

- [ ] **Step 1: Write the failing tests**

`UuidBytesTest.kt`:

```kotlin
package com.loosecannon.nfc.tagcore

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UuidBytesTest {
    private val canonical = "123e4567-e89b-12d3-a456-426614174000"

    @OptIn(ExperimentalStdlibApi::class)
    @Test fun theLayoutIsBigEndianMsbThenLsb() {
        assertEquals("123e4567e89b12d3a456426614174000", UuidBytes.toBytes(UUID.fromString(canonical)).toHexString())
        assertEquals(UuidBytes.LENGTH, UuidBytes.toBytes(UUID.fromString(canonical)).size)
    }

    @Test fun roundTripsRandomUuids() {
        repeat(20) {
            val u = UUID.randomUUID()
            assertEquals(u, UuidBytes.fromBytes(UuidBytes.toBytes(u)))
        }
    }

    @Test fun roundTripsTheEdges() {
        for (u in listOf(UUID(0L, 0L), UUID(-1L, -1L), UUID(Long.MIN_VALUE, Long.MAX_VALUE))) {
            assertEquals(u, UuidBytes.fromBytes(UuidBytes.toBytes(u)))
        }
    }

    @Test fun readsAtAnOffset() {
        val u = UUID.fromString(canonical)
        val framed = byteArrayOf(0x01, 0x00, 0x00) + UuidBytes.toBytes(u) + byteArrayOf(0x7f)
        assertEquals(u, UuidBytes.fromBytes(framed, offset = 3))
    }

    @Test fun refusesTooFewBytes() {
        assertFailsWith<IllegalArgumentException> { UuidBytes.fromBytes(ByteArray(15)) }
        assertFailsWith<IllegalArgumentException> { UuidBytes.fromBytes(ByteArray(16), offset = 1) }
        assertFailsWith<IllegalArgumentException> { UuidBytes.fromBytes(ByteArray(16), offset = -1) }
    }

    @Test fun requireCanonicalAcceptsTheCanonicalForm() {
        assertEquals(UUID.fromString(canonical), UuidBytes.requireCanonical(canonical))
    }

    @Test fun requireCanonicalRefusesEverythingElse() {
        assertFailsWith<IllegalArgumentException> { UuidBytes.requireCanonical("123E4567-E89B-12D3-A456-426614174000") }
        assertFailsWith<IllegalArgumentException> { UuidBytes.requireCanonical("not-a-uuid") }
        assertFailsWith<IllegalArgumentException> { UuidBytes.requireCanonical("") }
        assertFailsWith<IllegalArgumentException> { UuidBytes.requireCanonical("123e4567e89b12d3a456426614174000") }
    }
}
```

`OverwritePolicyTest.kt` — the full `ExistingContent × isSameIdentity` matrix, tokens not sentences:

```kotlin
package com.loosecannon.nfc.tagcore

import kotlin.test.Test
import kotlin.test.assertEquals

class OverwritePolicyTest {
    private val ours = ExistingContent.Ours("123e4567-e89b-12d3-a456-426614174000")

    private fun confirm(reason: OverwriteReason, detail: String) = OverwriteDecision.Confirm(reason, detail)

    @Test fun anEmptyTagProceedsWhoeverIsWriting() {
        for (same in listOf(true, false)) {
            assertEquals(OverwriteReason.EMPTY_TAG, OverwritePolicy.reason(ExistingContent.Empty, same))
            assertEquals(OverwriteDecision.Proceed, OverwritePolicy.decide(ExistingContent.Empty, same))
        }
    }

    @Test fun theSameIdentityIsARetryAndProceeds() {
        assertEquals(OverwriteReason.SAME_TAG, OverwritePolicy.reason(ours, isSameIdentity = true))
        assertEquals(OverwriteDecision.Proceed, OverwritePolicy.decide(ours, isSameIdentity = true))
    }

    @Test fun anotherTagOfTheSameProductCostsOneConfirmation() {
        assertEquals(OverwriteReason.OTHER_TAG_SAME_PRODUCT, OverwritePolicy.reason(ours, isSameIdentity = false))
        assertEquals(confirm(OverwriteReason.OTHER_TAG_SAME_PRODUCT, ours.detail), OverwritePolicy.decide(ours, isSameIdentity = false))
    }

    @Test fun aBodyThisBuildCannotParseCostsOneConfirmation() {
        val newer = ExistingContent.OursUnsupported("version 2")
        for (same in listOf(true, false)) {
            assertEquals(OverwriteReason.SAME_PRODUCT_UNSUPPORTED, OverwritePolicy.reason(newer, same))
            assertEquals(confirm(OverwriteReason.SAME_PRODUCT_UNSUPPORTED, "version 2"), OverwritePolicy.decide(newer, same))
        }
    }

    @Test fun foreignContentCostsOneConfirmation() {
        val foreign = ExistingContent.Foreign("tnf=1 type=U")
        for (same in listOf(true, false)) {
            assertEquals(OverwriteReason.FOREIGN, OverwritePolicy.reason(foreign, same))
            assertEquals(confirm(OverwriteReason.FOREIGN, "tnf=1 type=U"), OverwritePolicy.decide(foreign, same))
        }
    }

    @Test fun unreadableContentCostsOneConfirmation() {
        val unreadable = ExistingContent.Unreadable("NDEF on tag could not be parsed")
        for (same in listOf(true, false)) {
            assertEquals(OverwriteReason.UNREADABLE, OverwritePolicy.reason(unreadable, same))
            assertEquals(confirm(OverwriteReason.UNREADABLE, "NDEF on tag could not be parsed"), OverwritePolicy.decide(unreadable, same))
        }
    }

    /** The detail is the evidence the caller handed in, verbatim: the library adds no words to it (invariant 13). */
    @Test fun theDetailIsTheEvidenceVerbatim() {
        val d = (OverwritePolicy.decide(ExistingContent.Foreign("x y z"), false) as OverwriteDecision.Confirm).detail
        assertEquals("x y z", d)
    }

    @Test fun everyTokenIsReachable() {
        val seen = OverwriteReason.entries.associateWith { false }.toMutableMap()
        for (c in listOf(ExistingContent.Empty, ours, ExistingContent.OursUnsupported("v"), ExistingContent.Foreign("f"), ExistingContent.Unreadable("u"))) {
            for (same in listOf(true, false)) seen[OverwritePolicy.reason(c, same)] = true
        }
        assertEquals(emptyList<OverwriteReason>(), seen.filterValues { !it }.keys.toList(), "unreached tokens")
    }
}
```

`NdefSizeTest.kt` — NoteTag's five cases (`b0ec89c`) on a neutral identity of the same length as the one that produced the 49 B figure:

```kotlin
package com.loosecannon.nfc.tagcore

import kotlin.test.Test
import kotlin.test.assertEquals

class NdefSizeTest {
    /** 23 characters, the length of the applicationId whose one-record message is 49 bytes with a 19-byte body. */
    private val identity = TagIdentity("com.example.twentythree", "tag")
    private val type = identity.externalType.toByteArray(Charsets.US_ASCII)   // 27 bytes

    @Test fun aNineteenByteBodyOnATwentySevenByteTypeIsFortyNineBytes() {
        val records = NdefEnvelope.encode(identity, ByteArray(19))
        assertEquals(49, NdefSize.serialisedSize(records))
    }

    @Test fun aShortRecordWithA255BytePayloadIsThreePlusTypePlusPayload() {
        val record = NdefRecordData(NdefEnvelope.TNF_EXTERNAL_TYPE, type, ByteArray(255))
        assertEquals(3 + 27 + 255, NdefSize.serialisedSize(listOf(record)))
    }

    @Test fun aLongRecordWithA256BytePayloadIsSixPlusTypePlusPayload() {
        val record = NdefRecordData(NdefEnvelope.TNF_EXTERNAL_TYPE, type, ByteArray(256))
        assertEquals(6 + 27 + 256, NdefSize.serialisedSize(listOf(record)))
    }

    @Test fun twoRecordsSum() {
        val a = NdefRecordData(NdefEnvelope.TNF_EXTERNAL_TYPE, type, ByteArray(10))    // 1+1+1+27+10 = 40
        val b = NdefRecordData(NdefEnvelope.TNF_EXTERNAL_TYPE, type, ByteArray(20))    // 1+1+1+27+20 = 50
        assertEquals(40, NdefSize.serialisedSize(listOf(a)))
        assertEquals(50, NdefSize.serialisedSize(listOf(b)))
        assertEquals(90, NdefSize.serialisedSize(listOf(a, b)))
    }

    @Test fun theEmptyListIsZero() = assertEquals(0, NdefSize.serialisedSize(emptyList()))
}
```

`EnvelopeLimitsTest.kt` — the figures both consumers assert today, without either consumer's name, and without the TLV allowance:

```kotlin
package com.loosecannon.nfc.tagcore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Ndef.getMaxSize()` is the maximum NDEF **message** size a tag can hold, so every capacity
 * comparison is message size against message size — never a Type-2 TLV header or terminator,
 * which belong to Android and to the tag (invariant 7).
 *
 * The budget is a provisional seed only: `[unobserved]`, to be re-pinned from the measured
 * `Ndef.maxSize` of a physical NTAG213 (runbook §D Session 1, tap 2). Nothing branches on it;
 * this is a limits test, and its job is to fail loudly if a record ever grows past a small tag.
 * The README says which consumer each identity length stands for; this file does not.
 */
private const val NTAG213_MAX_MESSAGE_BYTES = 137

class EnvelopeLimitsTest {
    /** 26 characters: one consumer's applicationId length, used as both domain and AAR package. */
    private val withAar = TagIdentity("com.example.twentysixchars", "tag", "com.example.twentysixchars")
    /** 23 characters: the other consumer's, which writes no AAR. */
    private val withoutAar = TagIdentity("com.example.twentythree", "tag")

    @Test fun aRecordWithAnEighteenByteBodyIsFiftyOneBytes() {
        val record = NdefEnvelope.encode(withAar, ByteArray(18)).first()
        assertEquals(30, record.type.size)
        assertEquals(51, NdefSize.serialisedSize(listOf(record)))
    }

    @Test fun thatRecordPlusItsAarIsNinetyFiveBytes() {
        val msg = NdefEnvelope.encode(withAar, ByteArray(18))
        assertEquals(2, msg.size)
        assertEquals(95, NdefSize.serialisedSize(msg))
        assertEquals(44, NdefSize.serialisedSize(listOf(msg[1])), "the AAR alone: 3 + 15 + 26")
    }

    @Test fun aOneRecordMessageWithANineteenByteBodyIsFortyNineBytes() {
        val msg = NdefEnvelope.encode(withoutAar, ByteArray(19))
        assertEquals(1, msg.size)
        assertEquals(49, NdefSize.serialisedSize(msg))
    }

    @Test fun bothConsumersMessagesFitAnNtag213() {
        for (msg in listOf(NdefEnvelope.encode(withAar, ByteArray(18)), NdefEnvelope.encode(withoutAar, ByteArray(19)))) {
            val needed = NdefSize.serialisedSize(msg)
            assertTrue(needed <= NTAG213_MAX_MESSAGE_BYTES, "the message needs $needed bytes; the seed is $NTAG213_MAX_MESSAGE_BYTES")
        }
    }

    /** No TLV allowance anywhere: the figure is the message, not the message plus framing. */
    @Test fun theSizeIsTheMessageAndNothingElse() {
        val record = NdefEnvelope.encode(withoutAar, ByteArray(19)).single()
        assertEquals(1 + 1 + 1 + record.type.size + record.payload.size, NdefSize.serialisedSize(listOf(record)))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :nfc-core:test --console=plain`
Expected: compilation failure naming `UuidBytes`, `OverwritePolicy`, `ExistingContent`, `NdefSize`.

- [ ] **Step 3: `UuidBytes.kt`** — the `ByteBuffer`/`UUID` halves of `decodeV1`/`v1Record` and `requireCanonicalUuid`, taking a `String`/`ByteArray` instead of a `TagId`

```kotlin
package com.loosecannon.nfc.tagcore

import java.nio.ByteBuffer
import java.util.UUID

/**
 * Generic byte↔UUID conversion and canonical-form checking. Application-neutral: NOT a payload
 * layout. The `version|flags|UUID` scheme this used to live inside stays in the app that owns it.
 */
object UuidBytes {
    const val LENGTH: Int = 16

    /** Big-endian: the most significant 8 bytes, then the least significant 8. */
    fun toBytes(uuid: UUID): ByteArray =
        ByteBuffer.allocate(LENGTH)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()

    fun fromBytes(bytes: ByteArray, offset: Int = 0): UUID {
        require(offset >= 0 && bytes.size - offset >= LENGTH) {
            "a UUID needs $LENGTH bytes; ${bytes.size - maxOf(offset, 0)} available at offset $offset"
        }
        val bb = ByteBuffer.wrap(bytes, offset, LENGTH)
        return UUID(bb.long, bb.long)
    }

    /** Refuses a non-UUID and a UUID that is not the canonical lower-case form. */
    fun requireCanonical(value: String): UUID {
        val uuid = try {
            UUID.fromString(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("not a UUID: '$value'", e)
        }
        require(uuid.toString() == value) { "not the canonical lower-case UUID form: '$value'" }
        return uuid
    }
}
```

- [ ] **Step 4: `OverwritePolicy.kt`** — `ExistingContent`, the six tokens, the decision, the policy

```kotlin
package com.loosecannon.nfc.tagcore

/** What the CALLER's classifier made of what the tag already holds. No product words. */
sealed interface ExistingContent {
    data object Empty : ExistingContent

    /** Our external type, body understood. [detail] is the caller's evidence (an id, say). */
    data class Ours(val detail: String) : ExistingContent

    /** Our external type, body this build will not parse (a version or kind above ours). */
    data class OursUnsupported(val detail: String) : ExistingContent

    data class Foreign(val description: String) : ExistingContent

    data class Unreadable(val reason: String) : ExistingContent
}

enum class OverwriteReason { EMPTY_TAG, SAME_TAG, OTHER_TAG_SAME_PRODUCT, SAME_PRODUCT_UNSUPPORTED, FOREIGN, UNREADABLE }

sealed interface OverwriteDecision {
    data object Proceed : OverwriteDecision

    /** [reason] is a token; [detail] is the raw evidence handed in (a type string, an id). NEVER a sentence. */
    data class Confirm(val reason: OverwriteReason, val detail: String) : OverwriteDecision
}

/**
 * Read-before-write: write without asking ONLY over an empty tag, or over the very identity being
 * written (a retry). Everything else costs exactly one confirmation. The rule is identical for
 * every consumer; only the sentences differ, and this object builds none (invariant 13).
 */
object OverwritePolicy {
    fun reason(existing: ExistingContent, isSameIdentity: Boolean): OverwriteReason = when (existing) {
        ExistingContent.Empty -> OverwriteReason.EMPTY_TAG
        is ExistingContent.Ours -> if (isSameIdentity) OverwriteReason.SAME_TAG else OverwriteReason.OTHER_TAG_SAME_PRODUCT
        is ExistingContent.OursUnsupported -> OverwriteReason.SAME_PRODUCT_UNSUPPORTED
        is ExistingContent.Foreign -> OverwriteReason.FOREIGN
        is ExistingContent.Unreadable -> OverwriteReason.UNREADABLE
    }

    fun decide(existing: ExistingContent, isSameIdentity: Boolean): OverwriteDecision =
        when (val r = reason(existing, isSameIdentity)) {
            OverwriteReason.EMPTY_TAG, OverwriteReason.SAME_TAG -> OverwriteDecision.Proceed
            else -> OverwriteDecision.Confirm(r, evidence(existing))
        }

    private fun evidence(existing: ExistingContent): String = when (existing) {
        ExistingContent.Empty -> ""
        is ExistingContent.Ours -> existing.detail
        is ExistingContent.OursUnsupported -> existing.detail
        is ExistingContent.Foreign -> existing.description
        is ExistingContent.Unreadable -> existing.reason
    }
}
```

- [ ] **Step 5: `NdefSize.kt`** — NoteTag's file (`b0ec89c`), package line changed, one word in the KDoc

```kotlin
package com.loosecannon.nfc.tagcore

/**
 * The size of the serialised NDEF message — exactly what `NdefMessage.toByteArray().size` returns
 * on Android, computed here without Android so a writer can decide off-device (invariant 7).
 * Per record: 1 header byte + 1 type-length byte + 1 payload-length byte (short record, payload
 * < 256) or 4 (long record) + the type + the payload. No ID field (IL = 0), no TLV framing, no
 * terminator: the Type-2 framing belongs to Android and the tag, never to this arithmetic.
 * `nfc-android`'s `serialisedSize()` is the platform's own figure; the emulator suite pins the two equal.
 */
object NdefSize {
    fun serialisedSize(records: List<NdefRecordData>): Int = records.sumOf { r ->
        1 + 1 + (if (r.payload.size < 256) 1 else 4) + r.type.size + r.payload.size
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass, and prove the runtime classpath**

Run: `./gradlew :nfc-core:test --console=plain`
Expected: `BUILD SUCCESSFUL`; six XML files under `nfc-core/build/test-results/test/`, every one `failures="0" errors="0"`; the totals: `NdefEnvelopeTest` 19, `TagIdentityTest` 5, `UuidBytesTest` 7, `OverwritePolicyTest` 8, `NdefSizeTest` 5, `EnvelopeLimitsTest` 5 = **49**.

Run: `grep -rhn '^import' nfc-core/src/main | sed 's/^[0-9]*://' | sort -u`
Expected: exactly two lines, `import java.nio.ByteBuffer` and `import java.util.UUID` (target §4.1, review addition 5).

Run: `./gradlew :nfc-core:dependencies --configuration runtimeClasspath --console=plain | grep -E '^[\\+|]' `
Expected: one top-level line, `\--- org.jetbrains.kotlin:kotlin-stdlib:2.4.20`, and beneath it at most `org.jetbrains:annotations`. Nothing else: no coroutines, no serialization, no Android.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "nfc-core: uuid bytes, the overwrite tokens and the size arithmetic; stdlib only on the runtime classpath"
```

---

### Task 4 (§A.3 row 3): `nfc-android` — the bridge, reader mode, the writer, the seam, the route

**Files:**
- Create: `nfc-android/src/main/kotlin/com/loosecannon/nfc/tagcore/android/{NdefBridge,NfcReaderModeSession,TagWriter,TagIo,WriteRoute}.kt`
- Test: `nfc-android/src/test/kotlin/com/loosecannon/nfc/tagcore/android/{FakeTagIo,WriteRouteTest,TwoTapFakeTest}.kt`

**Interfaces:**
- Consumes: `NdefRecordData`, `NdefSize` (Tasks 2–3).
- Produces (target §4.2, plus F-1 and F-3): `NdefMessage?.toRecordData()`, `List<NdefRecordData>.toNdefMessage()`, `ByteArray?.toHexOrNull()`, `Intent.ndefRecords()`, `Intent.nfcTag()`, `List<NdefRecordData>.serialisedSize()`; `NfcReaderModeSession(activity, onTag)` with `available`, `enabled`, `start()`, `stop()`; `TagInspection(uid, existingRecords, maxSize, writable, needsFormat, canLock, unreadable = null)`; `WriteResult.Formatted` / `Written(readBack, bytes, locked)` / `TooSmall(maxSize, needed)` / `ReadOnly` / `Unsupported` / `VerifyMismatch(readBack)` / `Failed(reason, cause = null)`; `TagWriter.inspect(tag)`, `write(tag, records, lock)`, `format(tag)`, `lock(tag)`; `TagHandle`, `NfcTagHandle(tag)`, `TagIo { inspect; format; write; lock }`, `object RealTagIo`; `WriteRoute.Format` / `ReadOnly` / `TooSmall(maxSize, needed)` / `Write` and `TagInspection.route(needed)`.

- [ ] **Step 1: The two verbatim files, with only the package line and the import changed**

```bash
S=~/Documents/Projects/AndroidStudioProjects/ServiceTag-split/app/src/main/kotlin/com/loosecannon/servicetag/nfc
D=nfc-android/src/main/kotlin/com/loosecannon/nfc/tagcore/android
sed -e 's/^package com.loosecannon.servicetag.nfc$/package com.loosecannon.nfc.tagcore.android/' \
    -e 's/^import com.loosecannon.servicetag.core.nfc.NdefRecordData$/import com.loosecannon.nfc.tagcore.NdefRecordData/' \
    $S/NdefBridge.kt > $D/NdefBridge.kt
sed -e 's/^package com.loosecannon.servicetag.nfc$/package com.loosecannon.nfc.tagcore.android/' \
    $S/NfcReaderModeSession.kt > $D/NfcReaderModeSession.kt
diff <(tail -n +2 $S/NfcReaderModeSession.kt) <(tail -n +2 $D/NfcReaderModeSession.kt) && echo "reader-mode session verbatim below the package line"
grep -c 'FLAG_READER_SKIP_NDEF_CHECK' $D/NfcReaderModeSession.kt    # expected: 0 (invariant 4)
```

Then append `serialisedSize()` to the end of `NdefBridge.kt`:

```kotlin

/**
 * Size of the EXACT encoded NDEF message — `toNdefMessage().toByteArray().size` — which is the
 * `needed` of every capacity check, compared directly against `Ndef.getMaxSize()` (itself a maximum
 * *message* size). No Type-2 TLV header or terminator is added: that layer is Android's and the
 * tag's (invariant 7). `NdefSize.serialisedSize` in `nfc-core` is the same number computed without
 * Android; the emulator suite pins the two equal.
 */
fun List<NdefRecordData>.serialisedSize(): Int = toNdefMessage().toByteArray().size
```

- [ ] **Step 2: `TagWriter.kt`** — ServiceTag's file with the four extraction changes and F-1. The parts not shown (`lock`, the `Ndef` write path's body) are copied unchanged.

```kotlin
package com.loosecannon.nfc.tagcore.android

import android.nfc.FormatException
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.loosecannon.nfc.tagcore.NdefRecordData
import java.io.IOException

class TagInspection(
    val uid: String?,
    /** What the tag holds, as records; the caller classifies them with its own envelope and body scheme. */
    val existingRecords: List<NdefRecordData>,
    /** `Ndef.maxSize`, or -1 for a tag that still needs formatting: capacity unknown until then. */
    val maxSize: Int,
    val writable: Boolean,
    val needsFormat: Boolean,
    val canLock: Boolean,
    /** Set when the tag's NDEF could not be parsed; the tag is still overwritable. */
    val unreadable: String? = null,
)

sealed interface WriteResult {
    /** The tag was made NDEF-capable by `format(null)`; no application message exists on it yet. */
    data object Formatted : WriteResult

    /** An application message exists on the tag AND its read-back verified structurally. There is no "written but unverified" success. */
    data class Written(val readBack: List<NdefRecordData>, val bytes: Int, val locked: Boolean) : WriteResult

    data class TooSmall(val maxSize: Int, val needed: Int) : WriteResult
    data object ReadOnly : WriteResult
    data object Unsupported : WriteResult
    data class VerifyMismatch(val readBack: List<NdefRecordData>) : WriteResult

    /** [reason] is diagnostic prose; [cause] is the exception that was folded, kept so a consumer can log it. */
    data class Failed(val reason: String, val cause: Throwable? = null) : WriteResult
}

/**
 * Read-first, write, read-back. Every function BLOCKS on tag I/O: call from a worker thread, never
 * the main thread. Decisions (overwrite? which target?) are the caller's, made between [inspect]
 * and [write] while the tag stays in the field.
 *
 * The halves report failure differently on purpose: [write] and [format] never throw for tag I/O
 * and fold every such failure into [WriteResult.Failed]; [inspect] lets it propagate (invariant 8).
 */
object TagWriter {

    /**
     * What is on the tag right now, or null when it is neither `Ndef` nor `NdefFormatable`.
     * Performs its own fresh read (`Ndef.getNdefMessage()`), never the message the platform cached.
     *
     * @throws java.io.IOException (including `android.nfc.TagLostException`) when the tag leaves
     * the field or I/O fails mid-read; callers run this off the main thread inside a try/catch.
     */
    fun inspect(tag: Tag): TagInspection? {
        val uid = tag.id.toHexOrNull()
        Ndef.get(tag)?.let { ndef ->
            return try {
                ndef.connect()
                val records = try {
                    ndef.ndefMessage.toRecordData()
                } catch (e: FormatException) {
                    return TagInspection(uid, emptyList(), ndef.maxSize, ndef.isWritable, needsFormat = false, canLock = ndef.canMakeReadOnly(), unreadable = "NDEF on tag could not be parsed")
                }
                TagInspection(uid, records, ndef.maxSize, ndef.isWritable, needsFormat = false, canLock = ndef.canMakeReadOnly())
            } finally {
                runCatching { ndef.close() }
            }
        }
        NdefFormatable.get(tag) ?: return null
        return TagInspection(uid, emptyList(), maxSize = -1, writable = true, needsFormat = true, canLock = true)
    }

    /**
     * The `Ndef` path only: capacity-check [records] against `Ndef.maxSize`, write, read back and
     * compare structurally, then lock if asked. A tag that still needs formatting is NOT written
     * here — see [format]; calling this on one is answered with [WriteResult.Failed], not with a write.
     */
    fun write(tag: Tag, records: List<NdefRecordData>, lock: Boolean): WriteResult {
        val message = records.toNdefMessage()
        val needed = message.toByteArray().size
        val ndef = Ndef.get(tag)
            ?: return if (NdefFormatable.get(tag) != null) WriteResult.Failed("tag still needs formatting; format first, then write on the next tap")
            else WriteResult.Unsupported
        return try {
            ndef.connect()
            if (!ndef.isWritable) return WriteResult.ReadOnly
            if (ndef.maxSize < needed) return WriteResult.TooSmall(ndef.maxSize, needed)
            ndef.writeNdefMessage(message)
            val back = ndef.ndefMessage.toRecordData()
            if (back != records) return WriteResult.VerifyMismatch(back)
            var locked = false
            if (lock && ndef.canMakeReadOnly()) locked = ndef.makeReadOnly()
            WriteResult.Written(back, needed, locked = locked)
        } catch (e: TagLostException) {
            WriteResult.Failed("tag left the field", e)
        } catch (e: IOException) {
            WriteResult.Failed(e.message ?: "I/O error", e)
        } catch (e: FormatException) {
            WriteResult.Failed("tag rejected the message: ${e.message}", e)
        } finally {
            runCatching { ndef.close() }
        }
    }

    /**
     * Formats an `NdefFormatable` tag and NOTHING else: calls `format(null)`, leaving the tag
     * **empty and unlocked**.
     *
     * The platform's `NdefFormatable.format(firstMessage)` formats *and* writes that message in one
     * operation, and there is no `Ndef` — and therefore no `maxSize` — until after it has run. So
     * passing the intended message here would let a too-large message fail inside the format call,
     * before any capacity check could exist. Instead the caller formats with no payload, and the
     * next tap delivers the tag as `Ndef`, where [write] can measure `maxSize` first (invariants 7, 9).
     */
    fun format(tag: Tag): WriteResult {
        val formatable = NdefFormatable.get(tag)
            ?: return if (Ndef.get(tag) != null) WriteResult.Failed("tag is already formatted; write it") else WriteResult.Unsupported
        return try {
            formatable.connect()
            formatable.format(null)
            WriteResult.Formatted
        } catch (e: TagLostException) {
            WriteResult.Failed("tag left the field", e)
        } catch (e: IOException) {
            WriteResult.Failed(e.message ?: "I/O error while formatting", e)
        } catch (e: FormatException) {
            WriteResult.Failed("tag could not be formatted: ${e.message}", e)
        } finally {
            runCatching { formatable.close() }
        }
    }

    /** Permanent. Call only after a verified read-back; the returned value is the proof it took. Blocks; call off the main thread. */
    fun lock(tag: Tag): Boolean {
        val ndef = Ndef.get(tag) ?: return false
        return try {
            ndef.connect()
            ndef.canMakeReadOnly() && ndef.makeReadOnly()
        } catch (e: IOException) {
            false
        } finally {
            runCatching { ndef.close() }
        }
    }
}
```

- [ ] **Step 3: `TagIo.kt`** — `TagWriteController.kt:27-65` moved out of the UI file; no codec; `format` on the seam

```kotlin
package com.loosecannon.nfc.tagcore.android

import android.nfc.Tag
import com.loosecannon.nfc.tagcore.NdefRecordData

/**
 * One tap's handle on a physical tag. Reader mode hands out an `android.nfc.Tag`, which no JVM
 * test can build, so decision logic only ever sees this: the chip's UID, plus whatever the real
 * [TagIo] needs to hide behind it.
 */
interface TagHandle {
    /** The chip's hardware UID as lower-case hex, or null when the platform did not supply one. */
    val uid: String?
}

/** The reader-mode handle. Only [RealTagIo] ever looks inside it. */
class NfcTagHandle(val tag: Tag) : TagHandle {
    override val uid: String? = tag.id.toHexOrNull()
}

/**
 * The four blocking tag operations, as a seam. Everything above it — read first, ask before
 * overwriting, verify, lock last — is decision logic, and decision logic belongs in a JVM test.
 *
 * Implementations block on tag I/O; the caller is what guarantees they run off the main thread.
 */
interface TagIo {
    /** @throws java.io.IOException when the tag leaves the field mid-read (see [TagWriter.inspect]). */
    fun inspect(tag: TagHandle): TagInspection?

    /** `NdefFormatable.format(null)` → [WriteResult.Formatted]; never writes a payload (see [TagWriter.format]). */
    fun format(tag: TagHandle): WriteResult

    fun write(tag: TagHandle, records: List<NdefRecordData>, lock: Boolean): WriteResult

    fun lock(tag: TagHandle): Boolean
}

/** [TagWriter] behind the seam; the only place an `android.nfc.Tag` comes back out of a handle. */
object RealTagIo : TagIo {
    override fun inspect(tag: TagHandle): TagInspection? = TagWriter.inspect(tag.nfc())
    override fun format(tag: TagHandle): WriteResult = TagWriter.format(tag.nfc())
    override fun write(tag: TagHandle, records: List<NdefRecordData>, lock: Boolean): WriteResult =
        TagWriter.write(tag.nfc(), records, lock)
    override fun lock(tag: TagHandle): Boolean = TagWriter.lock(tag.nfc())

    private fun TagHandle.nfc(): Tag = (this as? NfcTagHandle)?.tag
        ?: error("RealTagIo only accepts a handle delivered by reader mode")
}
```

- [ ] **Step 4: `WriteRoute.kt`** (F-3) — pure Kotlin, no `android.*` import, so it is JVM-testable

```kotlin
package com.loosecannon.nfc.tagcore.android

/**
 * Where an inspected tag goes next, decided before any consent question and before any I/O.
 * The order is the platform's: a tag that still needs formatting has no capacity figure, so it is
 * routed to `format` and NO verdict is computed for it (invariant 9); a read-only tag is
 * `ReadOnly` before it can be `TooSmall` (invariant 7); the comparison is the exact serialised
 * message against `maxSize`, nothing added.
 */
sealed interface WriteRoute {
    /** Call [TagIo.format]; measure, write, verify and lock on the next tap. */
    data object Format : WriteRoute
    data object ReadOnly : WriteRoute
    data class TooSmall(val maxSize: Int, val needed: Int) : WriteRoute
    /** Call [TagIo.write] with the message that measured [needed] bytes. */
    data object Write : WriteRoute
}

/** @param needed the exact serialised size of the intended message (`NdefSize.serialisedSize` or `serialisedSize()`). */
fun TagInspection.route(needed: Int): WriteRoute = when {
    needsFormat -> WriteRoute.Format
    !writable -> WriteRoute.ReadOnly
    needed > maxSize -> WriteRoute.TooSmall(maxSize, needed)
    else -> WriteRoute.Write
}
```

- [ ] **Step 5: Write the failing unit tests** (JUnit 4, plain JVM — nothing here touches `android.*` at runtime)

`FakeTagIo.kt`:

```kotlin
package com.loosecannon.nfc.tagcore.android

import com.loosecannon.nfc.tagcore.NdefRecordData

/** A handle a JVM test can build. */
class FakeHandle(override val uid: String? = "04a1b2c3d4e5f6") : TagHandle

/**
 * A seam double whose [inspection] a test may change between taps — that is the whole point of
 * it: a tag that needed formatting on tap one is a different inspection on tap two.
 */
class FakeTagIo(
    var inspection: TagInspection?,
    var writeResult: WriteResult = WriteResult.Written(emptyList(), 0, locked = false),
    var formatResult: WriteResult = WriteResult.Formatted,
) : TagIo {
    var inspectCount = 0
    var formatCount = 0
    var writeAttempts = 0
    var lastWritten: List<NdefRecordData>? = null

    override fun inspect(tag: TagHandle): TagInspection? { inspectCount++; return inspection }
    override fun format(tag: TagHandle): WriteResult { formatCount++; return formatResult }
    override fun write(tag: TagHandle, records: List<NdefRecordData>, lock: Boolean): WriteResult {
        writeAttempts++; lastWritten = records; return writeResult
    }
    override fun lock(tag: TagHandle): Boolean = true
}
```

`WriteRouteTest.kt`:

```kotlin
package com.loosecannon.nfc.tagcore.android

import org.junit.Assert.assertEquals
import org.junit.Test

class WriteRouteTest {
    private fun ndef(maxSize: Int, writable: Boolean = true) =
        TagInspection(uid = "04a1", existingRecords = emptyList(), maxSize = maxSize, writable = writable, needsFormat = false, canLock = true)
    private val formatable =
        TagInspection(uid = "04a1", existingRecords = emptyList(), maxSize = -1, writable = true, needsFormat = true, canLock = true)

    @Test fun aTagThatNeedsFormattingIsRoutedToFormatWhateverTheMessageSize() {
        assertEquals(WriteRoute.Format, formatable.route(needed = 0))
        assertEquals(WriteRoute.Format, formatable.route(needed = 49))
        assertEquals(WriteRoute.Format, formatable.route(needed = 10_000))
    }

    @Test fun aReadOnlyTagIsReadOnlyBeforeItCanBeTooSmall() {
        assertEquals(WriteRoute.ReadOnly, ndef(maxSize = 137, writable = false).route(needed = 49))
        assertEquals(WriteRoute.ReadOnly, ndef(maxSize = 10, writable = false).route(needed = 49))
    }

    @Test fun exactlyMaxSizeIsAcceptedAndOneMoreIsTooSmall() {
        assertEquals(WriteRoute.Write, ndef(maxSize = 137).route(needed = 137))
        assertEquals(WriteRoute.TooSmall(137, 138), ndef(maxSize = 137).route(needed = 138))
    }

    @Test fun aMessageThatFitsIsWritten() {
        assertEquals(WriteRoute.Write, ndef(maxSize = 137).route(needed = 49))
    }

    @Test fun anUnreadableTagIsStillRoutedByCapacity() {
        val unreadable = TagInspection("04a1", emptyList(), maxSize = 137, writable = true, needsFormat = false, canLock = true, unreadable = "NDEF on tag could not be parsed")
        assertEquals(WriteRoute.Write, unreadable.route(needed = 49))
    }

    @Test fun theInspectionDefaultsToReadable() {
        assertEquals(null, ndef(137).unreadable)
    }
}
```

`TwoTapFakeTest.kt` — the two-tap shape (Phase E residual R2), at the seam:

```kotlin
package com.loosecannon.nfc.tagcore.android

import com.loosecannon.nfc.tagcore.NdefRecordData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A formatable tag takes two taps. Tap one: `route` says Format, `format` returns Formatted, and
 * NOTHING is written. Tap two: the same chip comes back as Ndef with a real `maxSize`, `route`
 * says Write, and only then does a payload reach the tag. A consumer's fake must model the second
 * inspection, because the first one carries no capacity and no content.
 */
class TwoTapFakeTest {
    private val handle = FakeHandle()
    private val message = listOf(NdefRecordData(0x04, "com.example.app:tag".toByteArray(Charsets.US_ASCII), ByteArray(18)))
    private val needed = 51

    @Test fun tapOneFormatsAndWritesNothingTapTwoWrites() {
        val io = FakeTagIo(
            inspection = TagInspection(handle.uid, emptyList(), maxSize = -1, writable = true, needsFormat = true, canLock = true),
            writeResult = WriteResult.Written(message, needed, locked = false),
        )

        // tap one
        val first = io.inspect(handle)!!
        assertEquals(WriteRoute.Format, first.route(needed))
        assertEquals(WriteResult.Formatted, io.format(handle))
        assertEquals(0, io.writeAttempts)

        // the chip is rediscovered as Ndef: empty, with a measured capacity
        io.inspection = TagInspection(handle.uid, emptyList(), maxSize = 137, writable = true, needsFormat = false, canLock = true)

        // tap two
        val second = io.inspect(handle)!!
        assertTrue(second.existingRecords.isEmpty())          // format(null) wrote nothing
        assertEquals(WriteRoute.Write, second.route(needed))
        val r = io.write(handle, message, lock = false)
        assertEquals(WriteResult.Written(message, needed, locked = false), r)
        assertEquals(1, io.writeAttempts)
        assertEquals(2, io.inspectCount)
        assertEquals(1, io.formatCount)
    }

    @Test fun aTagTooSmallOnTapTwoIsNeverWritten() {
        val io = FakeTagIo(inspection = TagInspection(handle.uid, emptyList(), maxSize = 48, writable = true, needsFormat = false, canLock = true))
        assertEquals(WriteRoute.TooSmall(48, needed), io.inspect(handle)!!.route(needed))
        assertEquals(0, io.writeAttempts)
    }
}
```

- [ ] **Step 6: Run the unit tests to verify they fail, then pass**

Run: `./gradlew :nfc-android:testDebugUnitTest --console=plain`
Expected before Step 4's file exists: compilation failure naming `route`/`WriteRoute`. After: `BUILD SUCCESSFUL`; `nfc-android/build/test-results/testDebugUnitTest/` holds `TEST-…WriteRouteTest.xml` (6) and `TEST-…TwoTapFakeTest.xml` (2), `failures="0"`.

Run: `./gradlew :nfc-android:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`; `nfc-android/build/outputs/aar/nfc-android-debug.aar` exists.

Run: `grep -rln 'servicetag\|notetag\|ServiceTag\|NoteTag' nfc-core/src nfc-android/src | wc -l`
Expected: `0` (no dependency back into either app, not even in a comment — O15; Task 5 makes this a gate).

Run: `grep -n '^import' nfc-android/src/main/kotlin/com/loosecannon/nfc/tagcore/android/WriteRoute.kt | wc -l`
Expected: `0` (the route is pure Kotlin).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "nfc-android: bridge, reader mode, the writer with format(null), the seam and the route"
```

---

### Task 5 (§A.3 row 4): the forbidden-knowledge scan, wired as a `check` dependency

**Files:**
- Create: `tools/forbidden-scan.sh`, `tools/forbidden-scan.allow`
- Modify: `build.gradle.kts` (root) — the `forbiddenScan` task and the `check` wiring

**Interfaces:**
- Produces: `bash tools/forbidden-scan.sh` exits 0 on a clean tree and 1 with the hits printed; `./gradlew check` runs it first.

- [ ] **Step 1: `tools/forbidden-scan.sh`** — target §4.4's script, with one addition: comment and blank lines in the allow file are not patterns

```bash
#!/usr/bin/env bash
# nfc-tag-core/tools/forbidden-scan.sh -- a CI gate, run BEFORE the build (target §4.4, §22).
# The library may DESCRIBE its consumers in README.md (outside these paths) and must never
# DEPEND on them in code or fixtures (inside them). False positives go in forbidden-scan.allow
# with a reason, never into a weakened pattern.
set -euo pipefail
cd "$(dirname "$0")/.."
WORDS='[Jj]oplin|[Oo]bsidian|[Ll]ogseq|[Ee]vernote|[Nn]otion|OneNote|[Tt]odoist|note[ _-]?id|noteNFC|notenfc|NoteTag|notetag|ServiceTag|servicetag|Asset|AssetEvent|Measurement|Profile|Schedule|Reminder|Attachment|Room|room3|androidx\.room|Compose|compose|[Bb]ackup|[Jj]ournal|[Nn]avigation|deep[ _-]?link|md5|MD5'
ALLOW=$(grep -v '^[[:space:]]*#' tools/forbidden-scan.allow | sed '/^[[:space:]]*$/d' || true)
HITS=$(grep -RInE "$WORDS" \
        --include='*.kt' --include='*.kts' --include='*.xml' --include='*.toml' --include='*.md' \
        nfc-core/src nfc-android/src settings.gradle.kts \
        | { if [ -n "$ALLOW" ]; then grep -vFf <(printf '%s\n' "$ALLOW"); else cat; fi; } || true)
if [ -n "$HITS" ]; then echo "$HITS"; echo "forbidden knowledge in the library"; exit 1; fi
echo "forbidden-scan: clean"
```

`chmod +x tools/forbidden-scan.sh`.

- [ ] **Step 2: `tools/forbidden-scan.allow`** — header only; the expected entry count is zero, and any entry an implementer adds must name its reason on the line above it

```text
# One exact `path:fragment` per accepted hit, each preceded by a `# reason` line. Inspected, not
# accepted: a Gradle source-set convention, a prose phrase and a KDoc example are all rewritten
# rather than allowed (target §4.4). Comment and blank lines are not patterns.
```

- [ ] **Step 3: Run the scan and inspect every hit**

Run: `bash tools/forbidden-scan.sh; echo "exit=$?"`
Expected: `forbidden-scan: clean` and `exit=0`. If a hit prints, it is a defect in a Task 2–4 file (the likeliest: a prose word such as "room" capitalised, "compose", "profile", "navigation", "backup" in a KDoc): **rewrite the prose in that file**, do not add an allow entry, and note the rewrite in the report.

- [ ] **Step 4: Wire it into `check` — root `build.gradle.kts` only** (an app that includes the modules as subprojects never reads this file, so the wiring cannot break a consumer build)

```kotlin
plugins {
    base
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

// The forbidden-knowledge scan (target §4.4): the first gate in CI and a `check` dependency here.
// Registered in the library's own root script on purpose, so it is invisible to an app build that
// includes :nfc-core and :nfc-android as subprojects (that build has its own root).
val forbiddenScan by tasks.registering(Exec::class) {
    group = "verification"
    description = "Fails on product vocabulary inside nfc-core/src, nfc-android/src or settings.gradle.kts"
    workingDir = layout.projectDirectory.asFile
    commandLine("bash", "tools/forbidden-scan.sh")
    inputs.dir("nfc-core/src")
    inputs.dir("nfc-android/src")
    inputs.files("settings.gradle.kts", "tools/forbidden-scan.sh", "tools/forbidden-scan.allow")
    outputs.upToDateWhen { false }
}

tasks.named("check") { dependsOn(forbiddenScan) }
subprojects {
    tasks.matching { it.name == "check" }.configureEach { dependsOn(forbiddenScan) }
}
```

Run: `./gradlew :nfc-core:check --console=plain 2>&1 | grep -E 'forbiddenScan|forbidden-scan: clean|BUILD'`
Expected: `> Task :forbiddenScan`, `forbidden-scan: clean`, `BUILD SUCCESSFUL`.

Run: `./gradlew check --console=plain 2>&1 | grep -cE '^> Task :forbiddenScan$'`
Expected: `1` (once, even though three `check` tasks depend on it).

- [ ] **Step 5: Prove the gate bites** — a throwaway fixture with a forbidden word must fail the scan, then be removed

```bash
printf 'package com.loosecannon.nfc.tagcore\n// this sentence mentions Joplin on purpose\n' > nfc-core/src/test/kotlin/com/loosecannon/nfc/tagcore/Scratch.kt
bash tools/forbidden-scan.sh; echo "exit=$?"      # expected: the hit line, "forbidden knowledge in the library", exit=1
rm nfc-core/src/test/kotlin/com/loosecannon/nfc/tagcore/Scratch.kt
bash tools/forbidden-scan.sh; echo "exit=$?"      # expected: forbidden-scan: clean, exit=0
git status --short | grep -c Scratch              # expected: 0
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "forbidden scan: the word list from the design, wired into check, zero allow entries"
```

---

### Task 6 (§A.3 row 5): `README.md` — the provenance table, the invariants, the API, the two patterns, and what is deliberately absent

**Files:**
- Create: `README.md`

**Interfaces:** none (documentation). The README is **outside** the scan and may name ServiceTag, NoteTag, Joplin and `TagBinding`; it may describe the consumers and must not depend on them.

- [ ] **Step 1: Write `README.md`** with exactly these sections, in this order. Prose is the implementer's, facts are these:

1. **`# nfc-tag-core`** — one paragraph: the product-neutral NFC tag mechanism shared by ServiceTag and NoteTag: an identity-parameterised external-record envelope, a byte↔UUID helper, the read-before-write policy as tokens, reader mode, and a blocking inspect / format / write / verify / lock adapter. Two modules, `nfc-core` (pure Kotlin/JVM, Kotlin stdlib only) and `nfc-android` (`com.android.library`, depends on `nfc-core` and nothing else). Consumed as a pinned git submodule at `libs/nfc-tag-core/`, included as ordinary subprojects `:nfc-core` and `:nfc-android`; no Maven, no publication (target §6). Versioned by annotated tags `nfc-tag-core-v<major>.<minor>.<patch>`; the first, `nfc-tag-core-v0.1.0`, is cut when the standalone build is green and is final only when both consumers are green against it (target §10.3).
2. **`## Provenance`** — the sentence "This repository has no inherited history; this table is its provenance. Every trace starts with `git log --follow -- <path>` in the ServiceTag repository (the renamed continuation of noteNFC), or in the NoteTag repository where marked." followed by **the table from this plan's "Provenance" section, all 18 rows, verbatim in content** (the implementer may reflow columns), and the "Deliberately not moved" line.
3. **`## Public API`** — the signatures of Tasks 2–4 exactly as the "Produces" blocks list them, grouped by module, each with its one-line KDoc summary.
4. **`## Invariants`** — target §4.3's 1–9 and 13, each in one or two sentences, each naming the test that proves it: 1 `NdefEnvelopeTest.aSiblingRecordCarryingOurOwnBodyIsStillForeign`; 2 `anIdentityWithAnAarWritesOursFirstThenTheAar`, `anIdentityWithoutAnAarWritesExactlyOneRecord`; 3 the structural comparison in `TagWriter.write` (records, not bytes; id outside) — proven on a physical tag, runbook §D; 4 `NfcReaderModeSessionDeviceTest` + the grep in Task 4 Step 1; 5 `TagIdentityTest.anUpperCaseExternalTypeIsRefused`; 6 the consumer's binding test (target §4.8) — not the library's; 7 `WriteRouteTest.exactlyMaxSizeIsAcceptedAndOneMoreIsTooSmall`, `aReadOnlyTagIsReadOnlyBeforeItCanBeTooSmall`, `NdefBridgeDeviceTest` (the platform pin), `EnvelopeLimitsTest.theSizeIsTheMessageAndNothingElse`; 8 `TagWriter`'s KDoc contract (inspect throws, write/format fold) — physical row; 9 `WriteRouteTest.aTagThatNeedsFormattingIsRoutedToFormatWhateverTheMessageSize`, `TwoTapFakeTest`; 13 `OverwritePolicyTest.theDetailIsTheEvidenceVerbatim` and the scan. State that 10–12 are protocol invariants each consumer implements.
5. **`## The two-tap shape`** — a formatable tag is formatted with `format(null)` on tap one (`Formatted`, nothing written, no capacity known, nothing to persist) and measured, written, verified and optionally locked on tap two; a consumer's fake must model the second inspection (`existingRecords` empty, a real `maxSize`). Sentence for the consumer's UI is the consumer's.
6. **`## Two patterns the library does not own`** — (a) the reader-mode lifecycle idiom `LifecycleResumeEffect { session.start(); onPauseOrDispose { session.stop() } }` with `onTag` on a binder thread; (b) the mint → write → verify → complete-else-abandon provisioning protocol (single-flight, one confirmation remembered against content, abandon on close). Both live in the consumers today; the second is `TagWriteSession`, **deferred**, promoted as `v0.2.0` only if, after NoteTag's writer is diffed against ServiceTag's, both need identical *decisions* (target §4.7, verbatim criterion).
7. **`## Consumer obligations carried from Phase E`** — the four rows of this plan's residual table, Phase G column, one bullet each (R1 nothing persisted on a format tap; R2 fakes model the second inspection; R3 the consumer's own honest sentence; R4 log the `Failed.cause` / the propagated `IOException`, rethrow cancellation first).
8. **`## Building`** — `./gradlew build` standalone; the CI task list `tools/forbidden-scan.sh && ./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :nfc-android:assembleDebug`; the emulator suite `ANDROID_SERIAL=emulator-5554 ./gradlew :nfc-android:connectedDebugAndroidTest` (local only, never CI); the alias-name rule and the "repositories only in the root settings file" rule (target §6.1); the same `agp`/`kotlin` pins as the consumers, with the one-line `diff` from target §6.1.
9. **`## What is deliberately absent`** — target §4.7's table in prose: the versioned payload layouts (both differ), `TagWriteSession`, version/kind negotiation, foreign/malformed wording; plus `LegacyKey`, `TagRoute`/`LinkLaunchPolicy`; plus: the NFC permission and every intent filter (the library manifest is empty; consumers own them).

- [ ] **Step 2: Check the README against the tree** (every file the table names exists; every hash in it resolves)

```bash
# the table abbreviates package paths with …, so the file check works on basenames
for b in $(grep -oE '[A-Za-z]+\.kt' README.md | sort -u); do find nfc-core nfc-android -name "$b" | grep -q . || echo "README names a file that does not exist: $b"; done
for h in $(grep -oE '`[0-9a-f]{7}`' README.md | tr -d '`' | sort -u); do
  git -C ~/Documents/Projects/AndroidStudioProjects/ServiceTag-split cat-file -e "$h^{commit}" 2>/dev/null \
  || git -C ~/Documents/Projects/AndroidStudioProjects/NoteTag cat-file -e "$h^{commit}" 2>/dev/null \
  || echo "unresolvable hash in README: $h"
done
```

Expected: no output from either loop.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "readme: the provenance table is the history"
```

---

### Task 7 (§A.3 row 6): `.github/workflows/ci.yml`

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:** none. Authored now, first run at §B.1 ("green once the remote exists"). No `secrets.*`, no absolute path, no device id (target §9, §26).

- [ ] **Step 1: The workflow** — the apps' shape; the scan runs **before** the build so a violation is the first thing a reader sees

```yaml
name: ci
on:
  push:
  pull_request:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: android-actions/setup-android@v3
        with:
          packages: platform-tools
          accept-android-sdk-licenses: true
      - uses: gradle/actions/setup-gradle@v4
      - name: forbidden-knowledge scan
        run: bash tools/forbidden-scan.sh
      - name: unit tests and debug library
        run: ./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :nfc-android:assembleDebug --console=plain
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results
          path: |
            nfc-core/build/test-results
            nfc-android/build/test-results
```

- [ ] **Step 2: Verify the file says what §9 says, locally**

Run: `grep -c 'secrets\.' .github/workflows/ci.yml` → `0`. Run: `grep -c 'connected\|androidTest' .github/workflows/ci.yml` → `0` (no instrumented step in CI). Run: `grep -n 'forbidden-scan' .github/workflows/ci.yml` → the scan step precedes the Gradle step (its line number is smaller). Run the two `run:` lines by hand from the repository root: both succeed.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: scan first, then the two suites and the debug aar"
```

---

### Task 8 (§A.3 row 7): the emulator suite for the adapter — local only, never CI

**Files:**
- Create: `nfc-android/src/androidTest/AndroidManifest.xml`, `nfc-android/src/androidTest/kotlin/com/loosecannon/nfc/tagcore/android/{TestActivity,NdefBridgeDeviceTest,NfcReaderModeSessionDeviceTest}.kt`

**Interfaces:**
- Consumes: everything from Task 4 and `NdefEnvelope`/`NdefSize`/`TagIdentity` from Tasks 2–3.
- Produces: the platform pins — `serialisedSize()` = `NdefSize.serialisedSize` = `toByteArray().size`; `applicationRecord` = `NdefRecord.createApplicationRecord`; the bridge round-trips; reader mode unavailable and inert on the emulator.

**Before anything runs:** `adb devices` must list `emulator-5554	device`. If it does not, the task is **BLOCKED** (the owner starts the emulator); the implementer never starts, addresses or lists any other device. `--tests` is rejected by `connectedDebugAndroidTest`; a single class runs with `-Pandroid.testInstrumentationRunnerArguments.class=<fqn>`.

- [ ] **Step 1: The test manifest and its activity** — reader mode needs an `Activity`; the library has none, so the test APK declares one

`nfc-android/src/androidTest/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity android:name="com.loosecannon.nfc.tagcore.android.TestActivity" android:exported="false" />
    </application>
</manifest>
```

`TestActivity.kt`:

```kotlin
package com.loosecannon.nfc.tagcore.android

import android.app.Activity

/** An empty activity for the test APK only: `NfcReaderModeSession` takes an Activity. */
class TestActivity : Activity()
```

- [ ] **Step 2: `NdefBridgeDeviceTest.kt`** — NoteTag's four platform-agreement cases (`f037060`) generalised, plus the bridge round-trips, the `Intent` extras and the AAR pin from target §4.5

```kotlin
package com.loosecannon.nfc.tagcore.android

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.os.Parcelable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loosecannon.nfc.tagcore.NdefEnvelope
import com.loosecannon.nfc.tagcore.NdefRecordData
import com.loosecannon.nfc.tagcore.NdefSize
import com.loosecannon.nfc.tagcore.TagIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pins that make the JVM arithmetic the platform's (invariant 7), and the bridge's
 * round-trips. `NdefMessage` is a serialiser, not a radio: no NFC hardware is needed, and none is
 * present on the emulator. Emulator only.
 */
@RunWith(AndroidJUnit4::class)
class NdefBridgeDeviceTest {
    /** 23 characters: a one-record message with a 19-byte body is 49 bytes. */
    private val identity = TagIdentity("com.example.twentythree", "tag")
    private val type = identity.externalType.toByteArray(Charsets.US_ASCII)

    private fun assertPlatformAgrees(records: List<NdefRecordData>) {
        val platform = records.toNdefMessage().toByteArray().size
        assertEquals("serialisedSize() must be the platform's own figure", platform, records.serialisedSize())
        assertEquals("NdefSize must equal what Android serialises", platform, NdefSize.serialisedSize(records))
    }

    @Test fun aNineteenByteBodyMessageIsFortyNineBytesOnTheDeviceToo() {
        val records = NdefEnvelope.encode(identity, ByteArray(19))
        assertPlatformAgrees(records)
        assertEquals(49, records.serialisedSize())
    }

    /** 255 payload bytes: still a short record, one byte of payload length. */
    @Test fun aShortRecordAgreesWithThePlatform() {
        val records = listOf(NdefRecordData(NdefEnvelope.TNF_EXTERNAL_TYPE, type, ByteArray(255)))
        assertPlatformAgrees(records)
    }

    /** 300 payload bytes: past 255, so the payload length field is four bytes, not one. */
    @Test fun aLongRecordAgreesWithThePlatform() {
        val records = listOf(NdefRecordData(NdefEnvelope.TNF_EXTERNAL_TYPE, type, ByteArray(300)))
        assertPlatformAgrees(records)
    }

    /** Two records in one message: the MB/ME flags live in the header byte each record already pays for. */
    @Test fun aTwoRecordMessageAgreesWithThePlatform() {
        val records = NdefEnvelope.encode(identity, ByteArray(19)) +
            NdefRecordData(NdefEnvelope.TNF_EXTERNAL_TYPE, "com.example.twentysixchars:tag".toByteArray(Charsets.US_ASCII), ByteArray(18))
        assertEquals(2, records.size)
        assertPlatformAgrees(records)
    }

    @Test fun theBridgeRoundTripsAndWritesNoRecordId() {
        val records = NdefEnvelope.encode(TagIdentity("com.example.app", "tag", "com.example.app"), byteArrayOf(1, 2, 3))
        val message = records.toNdefMessage()
        assertEquals(records, message.toRecordData())
        assertTrue("the record id is always empty (invariant 3 keeps it outside the comparison)", message.records.all { it.id.isEmpty() })
        assertEquals(emptyList<NdefRecordData>(), (null as NdefMessage?).toRecordData())
    }

    /**
     * On an API 33+ emulator this exercises the typed `getParcelableArrayExtra` branch; the
     * pre-33 branch is compile-covered only, and identical in shape.
     */
    @Test fun anIntentCarryingMessagesYieldsTheFirstMessagesRecords() {
        val first = NdefEnvelope.encode(identity, ByteArray(19))
        val second = listOf(NdefRecordData(NdefEnvelope.TNF_EXTERNAL_TYPE, type, ByteArray(3)))
        val intent = Intent().putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf<Parcelable>(first.toNdefMessage(), second.toNdefMessage()))
        assertEquals(first, intent.ndefRecords())
        assertNull(Intent().ndefRecords())
    }

    @Test fun anIntentWithoutATagYieldsNull() {
        assertNull(Intent().nfcTag())
    }

    @Test fun hexIsLowerCaseAndNullForNothing() {
        assertNull((null as ByteArray?).toHexOrNull())
        assertNull(ByteArray(0).toHexOrNull())
        assertEquals("04a1ff", byteArrayOf(0x04, 0xa1.toByte(), 0xff.toByte()).toHexOrNull())
    }

    /** The pinned vector in `NdefEnvelopeTest` re-asserted against the real platform call. */
    @Test fun theApplicationRecordIsBytesIdenticalToThePlatforms() {
        val platform = NdefMessage(arrayOf(NdefRecord.createApplicationRecord("com.example.app"))).toRecordData().single()
        assertEquals(platform, NdefEnvelope.applicationRecord("com.example.app"))
    }
}
```

- [ ] **Step 3: `NfcReaderModeSessionDeviceTest.kt`**

```kotlin
package com.loosecannon.nfc.tagcore.android

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** The emulator has no NFC adapter: the session must say so and `start()`/`stop()` must be safe no-ops (target §4.5). */
@RunWith(AndroidJUnit4::class)
class NfcReaderModeSessionDeviceTest {

    @Test fun withoutAnAdapterTheSessionIsUnavailableAndInert() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, TestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent)
        try {
            var taps = 0
            val session = NfcReaderModeSession(activity) { taps++ }
            assertFalse("the emulator image has no NFC adapter", session.available)
            assertFalse(session.enabled)
            session.start()
            session.stop()
            assertEquals(0, taps)
        } finally {
            activity.finish()
        }
    }
}
```

- [ ] **Step 4: Run it, pinned to the emulator**

Run: `adb devices | grep -c '^emulator-5554[[:space:]]*device$'` → `1`, else BLOCKED.

Run: `ANDROID_SERIAL=emulator-5554 ./gradlew :nfc-android:connectedDebugAndroidTest --console=plain`
Expected: `BUILD SUCCESSFUL`; the XML under `nfc-android/build/outputs/androidTest-results/connected/debug/` reports **10** tests (`NdefBridgeDeviceTest` 9, `NfcReaderModeSessionDeviceTest` 1), `failures="0"`; the test APK `com.loosecannon.nfc.tagcore.android.test` is installed and removed by Gradle. Nothing else on the emulator is touched.

Run: `bash tools/forbidden-scan.sh` → `forbidden-scan: clean` (the androidTest tree is inside the scanned paths).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "emulator suite: the platform pins for size and the aar, the bridge round trips, reader mode inert without a chip"
```

---

### Task 9: the whole-phase proof (§A.3 "Verify") and the evidence section

**Files:**
- Modify (ServiceTag worktree, `product-split`): `docs/architecture/product-split-evidence.md` — a new `## Phase F — nfc-tag-core extraction (§A.3)` section after the Phase E one
- The library repository gets **no new commit** unless Step 1 forces a fix; if it does, that fix is one commit and FINAL is its hash.

**Interfaces:** none.

- [ ] **Step 0: FINAL is fixed before the proof.** `FINAL=$(git rev-parse --short HEAD)` in the library; every number below is measured at that commit, and the evidence names it. A fix during Step 1 restarts this step.

- [ ] **Step 1: The repository's own gates, from the root**

```bash
cd ~/Documents/Projects/AndroidStudioProjects/nfc-tag-core
git status --short | wc -l                                  # expected: 0
./gradlew build --console=plain 2>&1 | tail -3               # expected: BUILD SUCCESSFUL (assemble + check for both modules, the scan included)
bash tools/forbidden-scan.sh                                 # expected: forbidden-scan: clean
grep -cv '^[[:space:]]*#' tools/forbidden-scan.allow         # expected: 0 (no allow entries; if not 0, each entry and its reason are quoted in the report)
./gradlew :nfc-core:dependencies --configuration runtimeClasspath --console=plain | grep -E '^[\\+|]'   # expected: kotlin-stdlib only
git remote | wc -l; git tag | wc -l                          # expected: 0 and 0
git rev-list --count HEAD                                    # expected: 8 (Tasks 1–8), or 9 with one Step-1 fix
```

If `./gradlew build` fails on **lint** (the only task it adds over the per-task gate), fix the code the way lint asks — never with `abortOnError = false` or a baseline — commit it as one fix, and restart Step 0.

- [ ] **Step 2: The clean clone builds and tests on its own** — a directory that has never held the project; the build cache off so the suites really run

```bash
SCRATCH=$(mktemp -d "${TMPDIR:-/tmp}/nfc-tag-core-clone.XXXXXX")   # the session scratchpad when one is given; throwaway either way
git clone --no-local ~/Documents/Projects/AndroidStudioProjects/nfc-tag-core "$SCRATCH/nfc-tag-core"
cd "$SCRATCH/nfc-tag-core"
git rev-parse --short HEAD                                   # expected: $FINAL
export ANDROID_HOME=~/Android/Sdk                            # a fresh clone has no local.properties
./gradlew build --no-build-cache --console=plain 2>&1 | grep -E 'FROM-CACHE|BUILD'   # expected: BUILD SUCCESSFUL and NO "FROM-CACHE" on any test task
bash tools/forbidden-scan.sh                                 # expected: forbidden-scan: clean
for x in nfc-core/build/test-results/test/*.xml nfc-android/build/test-results/testDebugUnitTest/*.xml; do
  grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$x" | head -1
done                                                          # expected: 49 across six nfc-core files, 8 across two nfc-android files, failures="0" everywhere
cd - >/dev/null && rm -rf "$SCRATCH"
```

- [ ] **Step 3: Every provenance start point resolves** — the README loop from Task 6 Step 2, run once more and quoted in the report (expected: no output).

- [ ] **Step 4: The emulator figure at FINAL** — Task 8's run is repeated only if FINAL moved in Step 1; otherwise its XML at FINAL is the figure. Record `emulator-5554`, the count (10), `failures="0"`.

- [ ] **Step 5: The evidence section** — append to `docs/architecture/product-split-evidence.md` in the ServiceTag worktree, headed `## Phase F — nfc-tag-core extraction (§A.3)`, same header discipline as Phases D and E ("Fingerprints only — no secrets, no physical-device ids, no owner paths"; nothing to fingerprint here, so say so). It carries, as fact lines and one table:
  - repository `~/Documents/Projects/AndroidStudioProjects/nfc-tag-core`, branch `master` (F-4), FINAL `<hash>`, `<n>` commits, `<n>` tracked files, no remote, no tag, no push; `GonzRon/nfc-tag-core` exists empty on GitHub since 2026-09-17 and was not touched.
  - the layout as built (two modules, plugins, namespace, SDK levels, packages); `nfc-core` runtime classpath = kotlin-stdlib only (the `dependencies` line quoted); `nfc-core/src/main` imports = `java.nio.ByteBuffer`, `java.util.UUID`.
  - suites at FINAL: `nfc-core` 49 (six classes named with counts), `nfc-android` unit 8 (two classes), connected 10 on `emulator-5554` (two classes); 0 failures everywhere; the clean-clone assertion (`--no-build-cache`, no FROM-CACHE).
  - the scan: word list = target §4.4 verbatim; allow entries = 0; wired as `check` in the root script only; CI file authored, **not run** (no remote).
  - provenance: 18 rows, every hash resolved in the ServiceTag or NoteTag repository (loop quoted, no output).
  - the four amendments as **ruled** at the plan review (F-1 … F-4, accepted or refused), and the Phase E residual dispositions carried into the README's consumer-obligations section.
  - what did not happen and why (no consumer touched; no tag; §B.1 owner-gated; the physical rows of §D untouched).
  - the closing line, verbatim: **Phase F local extraction complete; the v0.1.0 tag and the push wait for §B.1.**

```bash
cd ~/Documents/Projects/AndroidStudioProjects/ServiceTag-split
git add docs/architecture/product-split-evidence.md
git commit -m "evidence: phase f, the library stands on its own"
git status --short | wc -l    # expected: 0
```

- [ ] **Step 6: Report** — FINAL, the commit count, every suite count with its XML path, the clone assertion, the scan line, the provenance loop, the evidence commit, and every deviation from this plan with the reason.

---

## Hand-off to Phase G (not this plan's work; recorded so nobody looks for it here)

Phase G (§A.4) is where both apps consume the library, and it needs §B.1 first (a submodule needs a URL and the `nfc-tag-core-v0.1.0` tag). Its plan will, per consumer: add `android-library` to the catalog; add the submodule at `libs/nfc-tag-core` pinned to the tag; add target §6.2's settings block and `implementation(project(":nfc-android"))`; delete the interim copies (NoteTag: `git grep -l 'Interim copy'` → six files; ServiceTag: `app/…/nfc/{NdefBridge,NfcReaderModeSession,TagWriter}.kt`, the seam in `TagWriteController.kt:27-65`, and `core/…/nfc/{NdefRecordData (in NdefCodec.kt), TagIdentity, OverwritePolicy}.kt`); rebuild each app's body codec (`NdefCodec` → the v1 `version|flags|UUID` layout over `NdefEnvelope` + `UuidBytes`; NoteTag's `NoteTagCodec` over `NdefEnvelope`), keeping `Malformed`/`NewerVersion` app-side; map `ExistingContent` from each app's decoded content and its sentences from `OverwriteReason` tokens; adopt `route()`, `Formatted`, `Failed.cause`; carry the four Phase E residual obligations (README §7); switch ServiceTag's design-time budget tests to `NdefSize` (dropping the TLV `+ 3`) and keep its named-application tests (`NdefCodecV1Test`, `NdefEnvelopeIsolationTest`, marked `TODO(Phase F)`) in ServiceTag, retargeted at its body codec; and re-run every device proof of Phases D and E against the library-backed builds.

## Amendments the controller applies on RELEASE (one docs commit on `product-split`, before Task 1)

For each accepted row of the owner's ruling: **target §4.2** gains `Failed(reason, cause: Throwable? = null)` (F-1), `NdefSize` in the `nfc-core` block and the sentence "`nfc-android`'s `serialisedSize()` is the platform figure; the emulator suite pins the two equal" (F-2), `WriteRoute` + `TagInspection.route(needed)` in the `nfc-android` block (F-3), and `OverwritePolicy.reason(...)` beside `decide(...)`; **target §4.5** names `WriteRouteTest`/`TwoTapFakeTest` as the `nfc-android` unit tier and `NdefSize` as the JVM figure the limits group asserts; **target §4.6** gains the `TagIdentity` (`5c075ea`), `NdefSize` (NoteTag `b0ec89c`) and `WriteRoute` rows and the note that extraction happened from product-split `63602d1`; **runbook §B.1** strikes its `gh repo create` line (the repository exists, empty, since 2026-09-17), changes `git push -u origin main` to `master` (F-4), and keeps the tag step. A refused row leaves the design text as it is and removes the matching lines from Tasks 3–4 and 8 of this plan.
