# Split Phase G — both apps consume nfc-tag-core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make ServiceTag and NoteTag consume the **exact** `nfc-tag-core-v0.1.0` tag as a pinned git submodule included as ordinary subprojects, delete every interim or library-owned NFC file from both apps, rebuild each app's body codec and write controller on the ratified v0.1.0 API (`TagRead`, `Failed.attempted`, `lock(tag, expected)`, two-stage `route()`/`fit()`, `format(null)`/`Formatted`), disposition the four Phase E residuals in both consumers, prove both apps locally green on the same tag with the runbook's four negative tests and a local clean clone each, and — at the tail, before any push — install each app's final two GitHub workflows (`ci.yml`, tag-only `release.yml`) with the design and runbook amended to the two-workflow trust model. **Nothing is pushed, tagged, renamed, installed on the phone or tapped in this phase.**

**Architecture:** Twelve tasks in three parts. **Part A (Tasks 1–4)** converts ServiceTag on `product-split` in the worktree; **Part B (Tasks 5–8)** converts NoteTag on its `master`; each part is catalog + submodule + settings → `:core` (body codec over the library envelope) → `:app` (adapter deleted, controller on the library seam) → CI file, pin script, negative tests, local clone. **Part C (Tasks 9–12)** proves both on one tag, installs the release pipeline with a local dry run, amends the design/runbook, and writes the evidence. The library is never modified: if integration forces a library change, the phase stops and the tag is re-cut under runbook §B.1's rule.

**Tech Stack:** the estate's pins — AGP 9.4.0, Kotlin 2.4.20, Gradle 9.7.1, JDK 17; the library at `nfc-tag-core-v0.1.0` (`7e0377a`): `:nfc-core` (stdlib-only JVM) and `:nfc-android` (`com.android.library`); JUnit 5 in the `:core` modules, JUnit 4 + Compose test in the `:app` modules; `apksigner` from build-tools 36.0.0 for the release dry run; GitHub Actions with an environment-scoped release secret store (designed here, provisioned at K/L).

**Spec:** `docs/architecture/product-split-migration.md` **§A.4** (sequence G: catalog alias, submodule at the tag, settings block, CI submodule steps, delete what the library owns, the four verifications) and **§B.1–§B.6** (what K/L will need ready); `docs/architecture/product-split-target.md` **§3.1**, **§4.2** (as amended: the v0.1.0 API), **§4.3** invariants, **§4.5** (the consumer-side tests), **§4.7/§5** (what stays app-specific), **§4.8** (the binding tests), **§4.9** (NoteTag's format and the `LOCAL_REF` rules), **§6.1–§6.4** (subproject consumption, the settings block, the CI snippet, bumps), **§8** and **§9** (signing and CI — **superseded for the apps by the owner's two-workflow ruling of 2026-09-17**, amended by Task 11), **§10.3**; the library README's "Consumer obligations carried from Phase E" section (R1–R4); the Phase E plan's binding UX sentences. Where this plan and those disagree, **the plan loses** — except the owner decisions below.

---

## Owner decisions — ruled 2026-09-17 (HOLD with corrections → scoped review → RELEASE)

All seven ACCEPTED (G-2 and G-6 as amended by the corrections below). The table keeps the proposals as argued; the last column is the owner's ruling.

| # | Proposal | Why | Ruling |
|---|---|---|---|
| **G-1** | Each app's **`:core` module depends on `:nfc-core`** (`implementation(project(":nfc-core"))`) in addition to `:app` → `:nfc-android` | Target §6.2 shows only `:app` → `:nfc-android`, but both body codecs (`NdefCodec`, `NoteTagCodec`), `OverwritePolicy`'s consumers, `WritePlanner` (`NdefSize`) and `ResolveTap` live in the pure-JVM `:core` modules, which cannot see `:app`'s dependencies. `:nfc-core` is stdlib-only JVM, so the edge is clean and keeps `:core` Android-free | **ACCEPT** |
| **G-2** | The release workflow **materialises the existing local signing mechanism** on the runner: it writes `$HOME/.config/<servicetag|notenfc>/keystore.properties` and decodes the keystore from secrets to the path that file names, then runs the unchanged `assembleRelease`; no Gradle change, and the same four-key `hasSigningKeys` guard applies | one signing mechanism in both places; a runner without the secrets **fails before the release build starts** (the four-secret presence check), and a build that somehow produced an unsigned or wrongly signed APK fails at `apksigner verify` + the fingerprint compare — nothing unsigned is ever published | **ACCEPT as amended** (correction 3, and the prose in this row) |
| **G-3** | Names, fixed here: GitHub environment **`release`**; secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`; repository **variable** (not secret) `RELEASE_CERT_SHA256` = the certificate's SHA-256 fingerprint (colon-separated upper-case hex, as `apksigner` prints it). Provisioning is **an owner manual step at K/L** (`gh secret set … --env release`, `gh variable set`), one of the ≤3 | the workflow needs stable names before K/L; the fingerprint is public by the owner's rule and is the independent post-build identity check | **ACCEPT** — provisioned at K/L, not Phase G |
| **G-4** | **NoteTag's product tag namespace follows its `versionName`**: NoteTag ships `versionName = "2.0"` (Phase E, past the historical 1.1), so the final product tag is **`notetag-v2.0`**, not the runbook's `notetag-v1.0`; `release.yml` **fails closed unless the tag's version equals the built `versionName`** (parsed from `aapt2 dump badging`), for both apps (`servicetag-v2.5` ↔ `"2.5"`) | a release whose tag and APK disagree on the version is exactly the inconsistency the owner asked the workflow to refuse; runbook §G (§28) still reserves `notetag-v1.0` | **ACCEPT** — every target/runbook occurrence of `notetag-v1.0` is amended (Task 11); no competing first-tag names |
| **G-5** | The two consumer **"formatted" sentences** (R3, consumer wording): NoteTag **"Formatted the tag. Hold it to the phone again to write the link."**; ServiceTag **"Formatted. Lift the tag off and hold it again to write."** | R3 said the old sentence overstated what remained; both now say exactly what the next tap does | **ACCEPT** — pinned by test |
| **G-6** | ServiceTag's controller **locks only through `write(lock = true)`** in Phase G; the standalone verify-then-lock branch (`awaitingVerify`) is deleted with the unverified-format path that needed it. The library's `lock(tag, expected)` stays available and unused by ServiceTag | the format path no longer writes, so the second tap is a normal write, which locks after its own verified read-back (invariant 9 by code) | **ACCEPT in principle, with correction 1** (consent is recorded, never written through the sheet's handle) |
| **G-7** | NoteTag keeps its emulator `NdefSizeDeviceTest` as **the product's own 49-byte pin**, retargeted to the library types, rather than relying only on the library's generic pin | the number is NoteTag's (its type string length), and the test costs nothing | **ACCEPT** — the library proves the arithmetic, NoteTag proves its own envelope's 49 bytes |

Task 11 carries G-3/G-4 into the design and runbook — including every `notetag-v1.0` occurrence.

## The four Phase E residuals, dispositioned here (owner: "requirements Phase G must consciously disposition")

| Residual | ServiceTag (Task 3) | NoteTag (Task 7) | Proof |
|---|---|---|---|
| **R1** orphan mapping per formatable tag | the ServiceTag row is provisioned on the first **writable** tap, never on a format-only tap (the `Format` branch returns before `ProvisionTag.begin`); once provisioned it is reused across retries and abandoned on close unless a verified write claims it | when `route()` is `Format`: call `tagIo.format(tag)`, **plan nothing, mint no uuid, persist nothing**; the second tap plans against the real `maxSize` | NoteTag test: on the Format tap `newUuid` is never invoked and the store is untouched; ServiceTag test: the Format tap leaves `writeAttempts == 0` and `provision.begun == 0` (no row at all) |
| **R2** fakes model the second tap | `FakeTagIo` (library seam) with a mutable inspection; the two-tap test flips `read` from `Readable(empty)`/`needsFormat` to `Readable(empty)`/`Writable(maxSize)` | the same, and the second-tap `Confirm` shape asserted is the one a formatted (empty) tag yields | both controller tests |
| **R3** honest sentence | G-5 sentence | G-5 sentence | pinned by test |
| **R4** the read exception | `onTag` catch: rethrow `CancellationException`, `Log.w(TAG, "inspect failed", t)`, then the fixed sentence; `Failed.cause` logged at the catch that turns it into a sentence; `TagRead.Unreadable.cause` logged when classified | the same, and NoteTag's `onTag` catch widened to rethrow cancellation (its A4/A12 siblings already do) | grep: every `Failed` and `Unreadable` arm logs `cause`; the cancellation rethrow precedes each broad catch |

## Global Constraints

- **Where.** ServiceTag on `product-split` in `~/Documents/Projects/AndroidStudioProjects/ServiceTag-split` (origin is still `GonzRon/noteNFC`; **nothing is pushed**); NoteTag on `master` in `~/Documents/Projects/AndroidStudioProjects/NoteTag` (**no remote is added**). The library `~/Documents/Projects/AndroidStudioProjects/nfc-tag-core` is **read-only** in this phase; the submodule fetches `https://github.com/GonzRon/nfc-tag-core.git` (a read, not a mutation) and is checked out **detached at `nfc-tag-core-v0.1.0`**, never a branch.
- **Not in this phase (owner):** no `git push` of either app, no tag, no `gh` mutation of any repository, no noteNFC rename, no issue migration, no phone install, no real-data migration, no physical NFC. The release workflows are **authored and dry-run locally, never triggered**. GitHub secrets/variables are **not** provisioned here.
- **The same exact tag in both apps.** `git -C libs/nfc-tag-core describe --exact-match --match 'nfc-tag-core-v*' --tags HEAD` prints `nfc-tag-core-v0.1.0` in both; `git ls-tree HEAD libs/nfc-tag-core` names `7e0377a…` in both; the catalog `agp`/`kotlin` diff against the library's catalog is empty in both. If integration needs a library change, **stop**: the fix goes to the library, the tag is re-cut per §B.1, and this phase resumes at Task 1 — never a patch inside `libs/`.
- **Delete, do not shadow.** After Tasks 2–3 (ServiceTag) and 6–7 (NoteTag) no app source defines `NdefRecordData`, `TagIdentity`, `NdefEnvelope`, `EnvelopeContent`, `NdefSize`, `OverwritePolicy`/`OverwriteDecision`/`ExistingContent`, `NdefBridge`, `NfcReaderModeSession`, `TagWriter`, `TagInspection`, `WriteResult`, `TagHandle`, `NfcTagHandle`, `TagIo`, `RealTagIo`: `git grep -n 'Interim copy'` = 0 in NoteTag; `git grep -nE 'class (NdefBridge|TagWriter|TagInspection|NfcReaderModeSession|RealTagIo)|interface (TagIo|TagHandle)|object (NdefSize|OverwritePolicy)' -- '*.kt'` = 0 in both apps outside `libs/`.
- **What stays app-side (target §5, §4.7):** the body layouts and their meaning (`TagPayload` with `V1`/`NewerVersion`/`Foreign`/`Malformed`/`Empty`; `NoteTagContent` with its kinds and `Malformed`/`NewerVersion`/`UnknownKind`), every user-facing sentence, the write protocols (each controller), link policy, routes, the trampolines, the stores and use cases.
- **The v0.1.0 API is consumed as ratified:** an inspection's `read` is matched exhaustively (`Readable` → the app's codec decodes; `Unreadable(reason, cause)` → the app's unreadable arm, never `Empty`); `route()` is called before any planning and takes no size; `fit(needed)` runs on a `Writable` with `needed = NdefSize.serialisedSize(records)`; `Formatted` is a format, never a write; `Failed.attempted` decides retain (true) versus remove (false) in NoteTag and the wording in both — never `reason` text or the exception class; `lock` only through `write(lock = true)` (G-6).
- **Binding UX sentences unchanged** (Phase E): `OverwriteWording.DEVICE_BOUND` = "This tag needs this phone to open. Back up NoteTag to protect the link."; "Written · This phone only"; "Saved as a this-phone-only tag."; "This phone only"; the two sibling refusals — the ambient-tap message "This tag belongs to ServiceTag, not NoteTag." (`ResolveTap`) and the overwrite question's "This tag belongs to ServiceTag." (`OverwriteWording`) — each unchanged in its own place; retained-is-not-written (`writtenAt` null until a verified `Written`); exactly two screens. ServiceTag's sentences unchanged except where a state disappears (G-6), R3 applies (G-5), or correction 1 adds the one post-consent sentence "Overwrite confirmed. Hold the same tag to the phone again to write."
- **Instrumented runs are emulator-only:** every `connectedDebugAndroidTest`, `installDebug`, `adb` call carries `ANDROID_SERIAL=emulator-5554` on the same command line; the phone is never a target; the suites wipe app data, which is why.
- **Signing hygiene:** keystore files and passwords are never printed, copied into a repository, rotated or replaced; the release dry run compares fingerprints **without printing them** — only `matches` / `differs` / `skipped (no expected value configured)` / `PARTIAL` / `BLOCKED` appear in reports; the ServiceTag key does not exist yet (owner keytool step, target §8), so ServiceTag's dry run reports `BLOCKED` and that is the expected result.
- **No personal data** in tracked files or reports: `~` for the home directory; `emulator-5554` only; no tag UIDs, note ids, serials, e-mails.
- **Per-task gate** (ServiceTag): `./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain` from the worktree root; NoteTag: the same list from its root. Each task is one commit on its branch. Commit messages: single casual subject, no body, no trailers, no attribution. Author GonzRon.
- **Gates not claimed.** The phase ends "**Phase G local consumption complete; both apps green on `nfc-tag-core-v0.1.0`; K/L pending owner authorization**". Gate 6 (NoteTag §23 end to end) and gate 10 need K/L, the phone and the physical sessions.
- **Manual owner tasks (≤3), explicit:** (1) generate, back up and restore-test the ServiceTag signing key (target §8; needed before ServiceTag's release dry run can pass, not before anything else); (2) at K/L, provision the `release` environment secrets and the `RELEASE_CERT_SHA256` variable in both app repositories (G-3). No third.

## File map

```
ServiceTag-split (product-split)                         NoteTag (master)
  gradle/libs.versions.toml     + android-library          gradle/libs.versions.toml     + android-library
  .gitmodules, libs/nfc-tag-core @ nfc-tag-core-v0.1.0     .gitmodules, libs/nfc-tag-core @ nfc-tag-core-v0.1.0
  settings.gradle.kts           + the §6.2 block           settings.gradle.kts           + the §6.2 block
  core/build.gradle.kts         + :nfc-core (G-1)          core/build.gradle.kts         + :nfc-core (G-1)
  app/build.gradle.kts          + :nfc-android             app/build.gradle.kts          + :nfc-android
  core/…/core/nfc/NdefCodec.kt       body codec only       core/…/core/nfc/NdefEnvelope.kt     DELETED
  core/…/core/nfc/OverwriteReasons.kt NEW (tokens→words)   core/…/core/nfc/NdefRecordData.kt   DELETED
  core/…/core/nfc/TagIdentity.kt     DELETED               core/…/core/nfc/TagIdentity.kt      DELETED
  core/…/core/nfc/OverwritePolicy.kt DELETED               core/…/core/nfc/NdefSize.kt         DELETED
  app/…/nfc/{NdefBridge,NfcReaderModeSession,TagWriter}.kt DELETED   core/…/core/nfc/OverwriteWording.kt   over tokens
  app/…/ui/scan/TagWriteController.kt rewritten            core/…/core/tag/NoteTagCodec.kt     over the library envelope
  app/…/di/AppGraph.kt             library TagIdentity     app/…/nfc/{NdefBridge,NfcReaderModeSession,TagWriter,TagIo}.kt DELETED
  tools/check-submodule-pin.sh   NEW                       app/…/write/NoteTagWriteController.kt rewritten
  tools/release-dry-run.sh       NEW                       tools/check-submodule-pin.sh, tools/release-dry-run.sh NEW
  .github/workflows/ci.yml       final                     .github/workflows/ci.yml       final
  .github/workflows/release.yml  NEW (servicetag-v*)       .github/workflows/release.yml  NEW (notetag-v*)
docs (product-split): target §8/§9/§6.3, runbook front matter, §B.3a/§B.4/§B.6, §G, §J — Task 11; evidence Phase G — Task 12
```

---

## Part A — ServiceTag (worktree `ServiceTag-split`, branch `product-split`)

### Task 1 (§A.4 task 1 + the submodule): catalog alias, submodule at the tag, settings block, module edges

**Files:**
- Modify: `gradle/libs.versions.toml` (one alias), `settings.gradle.kts` (the §6.2 block), `build.gradle.kts` (root: the library plugin `apply false`), `core/build.gradle.kts` (G-1), `app/build.gradle.kts` (one line)
- Create: `.gitmodules` and the gitlink `libs/nfc-tag-core` (by `git submodule add`)

**Interfaces:**
- Produces: Gradle paths `:nfc-core` and `:nfc-android` inside the ServiceTag build; `:core` sees `com.loosecannon.nfc.tagcore.*`; `:app` sees `com.loosecannon.nfc.tagcore.android.*`.

- [ ] **Step 1: The alias** — append to `[plugins]` in `gradle/libs.versions.toml`:

```toml
android-library = { id = "com.android.library", version.ref = "agp" }
```

- [ ] **Step 2: The submodule, detached at the tag**

```bash
cd ~/Documents/Projects/AndroidStudioProjects/ServiceTag-split
git submodule add https://github.com/GonzRon/nfc-tag-core.git libs/nfc-tag-core
git -C libs/nfc-tag-core fetch --tags --quiet
git -C libs/nfc-tag-core checkout --quiet nfc-tag-core-v0.1.0
git -C libs/nfc-tag-core describe --exact-match --match 'nfc-tag-core-v*' --tags HEAD   # nfc-tag-core-v0.1.0
git -C libs/nfc-tag-core rev-parse --short HEAD                                         # 7e0377a
git add .gitmodules libs/nfc-tag-core
cat .gitmodules     # path = libs/nfc-tag-core, url = https://github.com/GonzRon/nfc-tag-core.git, NO branch line
```

- [ ] **Step 3: `settings.gradle.kts`** — append after `include(":app", ":core")`, verbatim from target §6.2:

```kotlin

// ---- NEW (O15): the pinned shared library, as ordinary subprojects of THIS build ----
require(file("libs/nfc-tag-core/nfc-core/build.gradle.kts").isFile) {
    """
    libs/nfc-tag-core is missing or uninitialised.
    Clone with --recurse-submodules, or run:  git submodule update --init --recursive
    """.trimIndent()
}
include(":nfc-core", ":nfc-android")
project(":nfc-core").projectDir    = file("libs/nfc-tag-core/nfc-core")
project(":nfc-android").projectDir = file("libs/nfc-tag-core/nfc-android")
```

- [ ] **Step 4: The edges** — in `core/build.gradle.kts` add `implementation(project(":nfc-core"))` as the first line of `dependencies { }` (G-1); in `app/build.gradle.kts` add `implementation(project(":nfc-android"))` directly after `implementation(project(":core"))`; and in the ROOT `build.gradle.kts` add `alias(libs.plugins.android.library) apply false` beside the existing `alias(libs.plugins.android.application) apply false` — AGP's application and library plugins must be declared on the one root plugin classpath, or configuration fails with a plugin-classpath conflict (found in execution 2026-09-17; the design's §6.2 shows only the settings side).

- [ ] **Step 5: Verify the library builds inside this build**

Run: `./gradlew projects --console=plain | grep -E "Project ':(nfc-core|nfc-android|core|app)'"` → four lines.
Run: `./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL` (nothing in the app uses the library yet; the point is that the alias set resolves, `FAIL_ON_PROJECT_REPOS` is not tripped, and the library's 60 JVM tests run under the app's catalog).
Run: `diff <(grep -E '^(agp|kotlin) =' gradle/libs.versions.toml) <(grep -E '^(agp|kotlin) =' libs/nfc-tag-core/gradle/libs.versions.toml)` → no output.
Run: `git -C libs/nfc-tag-core status --porcelain | wc -l` → 0; `git status --short` shows exactly `.gitmodules`, `libs/nfc-tag-core`, the four edited files.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "wire in nfc-tag-core at v0.1.0 as subprojects"
```

---

### Task 2 (§A.4 "delete what the library owns", `:core` half): `NdefCodec` becomes the body codec; `OverwritePolicy` and `TagIdentity` come from the library

**Files:**
- Modify: `core/src/main/kotlin/com/loosecannon/servicetag/core/nfc/NdefCodec.kt` (body codec over `NdefEnvelope` + `UuidBytes`; `NdefRecordData` and the envelope logic removed)
- Create: `core/src/main/kotlin/com/loosecannon/servicetag/core/nfc/OverwriteReasons.kt` (the app's mapping `TagPayload` → `ExistingContent`, and `OverwriteReason` → ServiceTag's sentence)
- Delete: `core/src/main/kotlin/com/loosecannon/servicetag/core/nfc/{TagIdentity,OverwritePolicy}.kt`, `core/src/test/kotlin/com/loosecannon/servicetag/core/nfc/{TagIdentityTest,NdefCodecTest}.kt` (library-owned cases; the two body-relevant cases of `NdefCodecTest` move into `NdefCodecV1Test`)
- Modify tests: `core/src/test/…/core/nfc/NdefCodecV1Test.kt` (sizes via `NdefSize`, the TLV `+ 3` dropped; `onlyFirstRecordMatters` and `uriRecordIsForeign` absorbed), `NdefEnvelopeIsolationTest.kt` (imports), `OverwritePolicyTest.kt` → `OverwriteReasonsTest.kt`; `core/src/main/…/core/{links/DeepLinkRoute,usecase/BindTag,usecase/ResolveTag}.kt` and `core/src/test/…/core/{links/DeepLinkRouteTest,usecase/ResolveTagTest,usecase/TagBindingUseCasesTest}.kt` (imports only: `TagIdentity` → `com.loosecannon.nfc.tagcore.TagIdentity`; `TagPayload`/`NdefCodec` unchanged)

**Interfaces:**
- Consumes: `com.loosecannon.nfc.tagcore.{NdefRecordData, TagIdentity, TagContent, NdefEnvelope, UuidBytes, NdefSize, ExistingContent, OverwriteReason, OverwriteDecision, OverwritePolicy}`.
- Produces (unchanged names, so `MainActivity`, `ScanViewModels`, `TagResultWire`, `ResolveTag`, `DeepLinkRoute` compile untouched): `TagPayload.{V1(tagId), NewerVersion(version), Foreign(description), Malformed(reason), Empty}`; `NdefCodec(identity: com.loosecannon.nfc.tagcore.TagIdentity)` with `decode(records): TagPayload`, `encodeV1(tagId): List<NdefRecordData>`, `v1Record(tagId)`, `applicationRecord(): NdefRecordData?`, `companion { V1_VERSION, V1_FLAGS, V1_PAYLOAD_LENGTH, requireCanonicalUuid(tagId) }`; NEW `OverwriteReasons.existing(p: TagPayload): ExistingContent`, `OverwriteReasons.decide(existing: TagPayload, intended: TagId): OverwriteDecision`, `OverwriteReasons.sentence(c: OverwriteDecision.Confirm): String`.

- [ ] **Step 1: Write the failing tests.** `NdefCodecV1Test` keeps every body case verbatim and changes only these: the imports (`NdefRecordData` from the library), the size assertions, and two absorbed cases:

```kotlin
    /** The exact bytes a ServiceTag tag carries: 3 + 30 + 18 = 51 B for the record (H2) — the serialised record, no TLV. */
    @Test fun exactByteLayout() {
        val rec = codec.v1Record(id)
        assertEquals(0x04, rec.tnf)
        assertContentEquals("com.loosecannon.servicetag:tag".toByteArray(Charsets.US_ASCII), rec.type)
        assertEquals(18, rec.payload.size)
        assertContentEquals(byteArrayOf(0x01, 0x00) + idBytes, rec.payload)
        assertEquals(51, NdefSize.serialisedSize(listOf(rec)), "the :tag record is 51 bytes")
    }

    /** 51 B for the record plus 3 + 15 + 26 = 44 B for the AAR: a 95 B message (H2). */
    @Test fun theWholeMessageIs95Bytes() = assertEquals(95, NdefSize.serialisedSize(codec.encodeV1(id)))

    /** The budget is the exact message; the design-time `+ 3` TLV allowance is gone (target §4.3 invariant 7). */
    @Test fun fitsAnNtag213() {
        val onTag = NdefSize.serialisedSize(codec.encodeV1(id))
        assertTrue(onTag <= NTAG213_MAX_MESSAGE_BYTES, "the message needs $onTag bytes; the seed is $NTAG213_MAX_MESSAGE_BYTES")
    }

    @Test fun onlyFirstRecordMatters() {
        val ours = codec.v1Record(id)
        val foreign = NdefRecordData(0x04, "com.example.other:tag".toByteArray(Charsets.US_ASCII), "whatever".toByteArray())
        assertIs<TagPayload.V1>(codec.decode(listOf(ours, foreign)))
        assertIs<TagPayload.Foreign>(codec.decode(listOf(foreign, ours)))
    }

    @Test fun uriRecordIsForeign() {
        val uri = NdefRecordData(tnf = 0x01, type = byteArrayOf('U'.code.toByte()), payload = byteArrayOf(0x01) + "example.com".toByteArray())
        assertIs<TagPayload.Foreign>(codec.decode(listOf(uri)))
    }
```

`OverwriteReasonsTest.kt` (replaces `OverwritePolicyTest.kt`; the sentences are the five the app has always shown):

```kotlin
package com.loosecannon.servicetag.core.nfc

import com.loosecannon.nfc.tagcore.ExistingContent
import com.loosecannon.nfc.tagcore.OverwriteDecision
import com.loosecannon.nfc.tagcore.OverwriteReason
import com.loosecannon.servicetag.core.model.TagId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OverwriteReasonsTest {
    private val mine = TagId("123e4567-e89b-12d3-a456-426614174000")
    private val other = TagId("00000000-0000-4000-8000-000000000001")

    @Test fun theMappingToExistingContent() {
        assertEquals(ExistingContent.Empty, OverwriteReasons.existing(TagPayload.Empty))
        assertEquals(ExistingContent.Ours(mine.value), OverwriteReasons.existing(TagPayload.V1(mine)))
        assertEquals(ExistingContent.OursUnsupported("3"), OverwriteReasons.existing(TagPayload.NewerVersion(3)))
        assertEquals(ExistingContent.Foreign("tnf=1 type=U"), OverwriteReasons.existing(TagPayload.Foreign("tnf=1 type=U")))
        assertEquals(ExistingContent.Unreadable("x"), OverwriteReasons.existing(TagPayload.Malformed("x")))
    }

    @Test fun emptyTagProceeds() = assertEquals(OverwriteDecision.Proceed, OverwriteReasons.decide(TagPayload.Empty, mine))
    @Test fun sameV1IdProceeds() = assertEquals(OverwriteDecision.Proceed, OverwriteReasons.decide(TagPayload.V1(mine), mine))
    @Test fun differentV1IdConfirms() {
        val c = assertIs<OverwriteDecision.Confirm>(OverwriteReasons.decide(TagPayload.V1(other), mine))
        assertEquals(OverwriteReason.OTHER_TAG_SAME_PRODUCT, c.reason)
        assertEquals("a different ServiceTag tag (${other.value})", OverwriteReasons.sentence(c))
    }
    @Test fun newerVersionConfirms() {
        val c = assertIs<OverwriteDecision.Confirm>(OverwriteReasons.decide(TagPayload.NewerVersion(3), mine))
        assertEquals("a ServiceTag tag written by a newer app (format 3)", OverwriteReasons.sentence(c))
    }
    @Test fun foreignConfirms() {
        val c = assertIs<OverwriteDecision.Confirm>(OverwriteReasons.decide(TagPayload.Foreign("tnf=1 type=U"), mine))
        assertEquals("foreign NDEF content (tnf=1 type=U)", OverwriteReasons.sentence(c))
    }
    @Test fun malformedConfirms() {
        val c = assertIs<OverwriteDecision.Confirm>(OverwriteReasons.decide(TagPayload.Malformed("x"), mine))
        assertEquals("unreadable NDEF content (x)", OverwriteReasons.sentence(c))
    }
    /** The question names what is on the tag, not just that something is. */
    @Test fun reasonNamesTheTagThatIsThere() {
        assertTrue(other.value in OverwriteReasons.sentence(OverwriteReasons.decide(TagPayload.V1(other), mine) as OverwriteDecision.Confirm))
    }
}
```

`NdefEnvelopeIsolationTest` keeps all four cases; only its `external(...)` helper and imports change to the library's `NdefRecordData` and `NdefEnvelope.TNF_EXTERNAL_TYPE`.

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:test --console=plain` → compilation failures naming `OverwriteReasons` and the library types.

- [ ] **Step 3: `NdefCodec.kt`** — the body codec, envelope-free:

```kotlin
package com.loosecannon.servicetag.core.nfc

import com.loosecannon.nfc.tagcore.NdefEnvelope
import com.loosecannon.nfc.tagcore.NdefRecordData
import com.loosecannon.nfc.tagcore.TagContent
import com.loosecannon.nfc.tagcore.TagIdentity
import com.loosecannon.nfc.tagcore.UuidBytes
import com.loosecannon.servicetag.core.model.TagId
import java.util.UUID

sealed interface TagPayload {
    /** Tag payload format v1: the tag carries a random tag id and nothing else (D4 §3). */
    data class V1(val tagId: TagId) : TagPayload

    /** A `:tag` record whose version byte is above what this build understands. Never parsed. */
    data class NewerVersion(val version: Int) : TagPayload

    data class Foreign(val description: String) : TagPayload
    data class Malformed(val reason: String) : TagPayload
    data object Empty : TagPayload
}

/**
 * ServiceTag's v1 BODY codec: `version | flags | 16-byte UUID` inside the envelope nfc-tag-core
 * owns. The type gate (TNF, then the exact external type) is the library's `NdefEnvelope.decode`;
 * everything after the gate — the version byte, the flags, the length, the UUID — is this product's
 * and stays here (target §4.7, §5). Android `NdefRecord` objects are built only in the library's
 * adapter.
 */
class NdefCodec(val identity: TagIdentity) {

    /** Android dispatches on the first record of the first message; so does the envelope. */
    fun decode(records: List<NdefRecordData>): TagPayload = when (val c = NdefEnvelope.decode(identity, records)) {
        TagContent.Empty -> TagPayload.Empty
        is TagContent.Foreign -> TagPayload.Foreign(c.description)
        is TagContent.Recognised -> decodeV1(c.body)
    }

    private fun decodeV1(payload: ByteArray): TagPayload {
        if (payload.isEmpty()) return TagPayload.Malformed("empty :tag payload")
        val version = payload[0].toInt() and 0xFF
        if (version > V1_VERSION) return TagPayload.NewerVersion(version)
        if (version != V1_VERSION) return TagPayload.Malformed("version byte 0x%02x".format(version))
        if (payload.size != V1_PAYLOAD_LENGTH) {
            return TagPayload.Malformed("payload is ${payload.size} bytes, expected $V1_PAYLOAD_LENGTH")
        }
        val flags = payload[1].toInt() and 0xFF
        if (flags != V1_FLAGS) return TagPayload.Malformed("flags byte 0x%02x, expected 0x00".format(flags))
        return TagPayload.V1(TagId(UuidBytes.fromBytes(payload, offset = 2).toString()))
    }

    /** The whole message for a v1 tag: the `:tag` record, then the AAR when this identity has one. */
    fun encodeV1(tagId: TagId): List<NdefRecordData> = NdefEnvelope.encode(identity, v1Body(tagId))

    fun v1Record(tagId: TagId): NdefRecordData = encodeV1(tagId).first()

    /** Null when this identity carries no AAR; otherwise byte-identical to the platform's record. */
    fun applicationRecord(): NdefRecordData? = identity.aarPackage?.let(NdefEnvelope::applicationRecord)

    private fun v1Body(tagId: TagId): ByteArray =
        byteArrayOf(V1_VERSION.toByte(), V1_FLAGS.toByte()) + UuidBytes.toBytes(requireCanonicalUuid(tagId))

    companion object {
        const val V1_VERSION: Int = 0x01
        const val V1_FLAGS: Int = 0x00
        const val V1_PAYLOAD_LENGTH: Int = 18

        /** A tag id must be the canonical lower-case UUID string so that `nfc_tag.id == payload_key` (D4 §3). */
        fun requireCanonicalUuid(tagId: TagId): UUID = try {
            UuidBytes.requireCanonical(tagId.value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("tag id must be the canonical lowercase UUID form: '${tagId.value}'", e)
        }
    }
}
```

- [ ] **Step 4: `OverwriteReasons.kt`**

```kotlin
package com.loosecannon.servicetag.core.nfc

import com.loosecannon.nfc.tagcore.ExistingContent
import com.loosecannon.nfc.tagcore.OverwriteDecision
import com.loosecannon.nfc.tagcore.OverwritePolicy
import com.loosecannon.nfc.tagcore.OverwriteReason
import com.loosecannon.servicetag.core.model.TagId

/**
 * ServiceTag's side of the read-before-write rule: the library decides (`OverwritePolicy`, tokens
 * and evidence); this product classifies what its codec read and turns a token into its own sentence
 * (target §4.2 invariant 13 — the library builds no sentence).
 */
object OverwriteReasons {
    fun existing(p: TagPayload): ExistingContent = when (p) {
        TagPayload.Empty -> ExistingContent.Empty
        is TagPayload.V1 -> ExistingContent.Ours(p.tagId.value)
        is TagPayload.NewerVersion -> ExistingContent.OursUnsupported(p.version.toString())
        is TagPayload.Foreign -> ExistingContent.Foreign(p.description)
        is TagPayload.Malformed -> ExistingContent.Unreadable(p.reason)
    }

    fun decide(existing: TagPayload, intended: TagId): OverwriteDecision =
        OverwritePolicy.decide(existing(existing), isSameIdentity = existing is TagPayload.V1 && existing.tagId == intended)

    /** The five sentences the confirmation sheet has always shown; the token picks, the detail fills. */
    fun sentence(c: OverwriteDecision.Confirm): String = when (c.reason) {
        OverwriteReason.OTHER_TAG_SAME_PRODUCT -> "a different ServiceTag tag (${c.detail})"
        OverwriteReason.SAME_PRODUCT_UNSUPPORTED -> "a ServiceTag tag written by a newer app (format ${c.detail})"
        OverwriteReason.FOREIGN -> "foreign NDEF content (${c.detail})"
        OverwriteReason.UNREADABLE -> "unreadable NDEF content (${c.detail})"
        OverwriteReason.EMPTY_TAG, OverwriteReason.SAME_TAG -> error("${c.reason} never asks a question")
    }
}
```

- [ ] **Step 5: Delete and retarget** — `git rm` the two main files and the two test files named above; change the `TagIdentity` import in `DeepLinkRoute.kt`? (no — it imports `TagPayload`/`TagRoute`, unchanged), `BindTag.kt` (`NdefCodec` unchanged), `ResolveTag.kt` (unchanged), `TagBindingUseCasesTest.kt` (`TagIdentity` → library). Grep to be sure: `git grep -n 'core.nfc.TagIdentity\|core.nfc.OverwritePolicy\|core.nfc.OverwriteDecision\|core.nfc.NdefRecordData' -- 'core/**' 'app/**'` → 0 after edits (Task 3 finishes `app/`).

- [ ] **Step 6: Run to verify they pass** — `./gradlew :core:test --console=plain` → `BUILD SUCCESSFUL`; record from the XML: `NdefCodecV1Test` (its count), `NdefEnvelopeIsolationTest` 4, `OverwriteReasonsTest` 8, `TagRouteTest` 2, plus every other `:core` class unchanged; the `:core` total is the evidence's number. `git grep -c 'class NdefRecordData\|class TagIdentity(' -- 'core/**'` → 0.

- [ ] **Step 7: Commit** — `git add -A && git commit -m "core: the body codec is ours, the envelope and the overwrite rule are the library's"`. (The `:app` module does not compile until Task 3 — run only `:core:test` here; the per-task gate resumes at Task 3.)

---

### Task 3 (§A.4 "delete what the library owns", `:app` half): the adapter goes, the controller consumes the seam

**Files:**
- Delete: `app/src/main/kotlin/com/loosecannon/servicetag/nfc/{NdefBridge,NfcReaderModeSession,TagWriter}.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/ui/scan/TagWriteController.kt` (the seam classes deleted from it; the controller rewritten — full text below), `app/…/di/AppGraph.kt` (`TagIdentity` from the library; no `RealTagIo(codec)`), `app/…/nfc/NfcDispatchActivity.kt`, `app/…/ui/scan/{ScanScreen,WriteTagScreen,ScanViewModels}.kt` (imports → `com.loosecannon.nfc.tagcore.android.*`), `app/…/MainActivity.kt` (unchanged unless it imports a moved type)
- Modify tests: `app/src/test/…/ui/scan/TagWriteControllerTest.kt` (rewritten against the library seam — cases below), `app/src/test/…/testing/FakeGraph.kt`, `app/src/test/…/nfc/{TagIdentityBindingTest,TagUseCasesRoomTest}.kt`, `app/src/androidTest/…/ui/NfcIdentityDeviceProofTest.kt`, `app/src/androidTest/…/nfc/TagIdentityDispatchTest.kt` (imports only)

**Interfaces:**
- Consumes: `com.loosecannon.nfc.tagcore.android.{NfcReaderModeSession, TagHandle, NfcTagHandle, TagIo, RealTagIo, TagInspection, TagRead, WriteResult, WriteRoute, CapacityVerdict, route, fit, ndefRecords, nfcTag, toHexOrNull}`; `com.loosecannon.nfc.tagcore.NdefSize`; Task 2's `NdefCodec`, `OverwriteReasons`.
- Produces: `TagWriteController(provisionTag, appScope, io: TagIo, codec, target, label, scope, ioDispatcher)` with the same `WriteState` minus `Verifying` (G-6) and the same public functions (`setLock`, `onTag`, `confirmOverwrite`, `keepIt`, `abandonIfUnwritten`, `state`, `lock`). `confirmOverwrite` performs no tag I/O (correction 1); the row is provisioned on the first writable tap, not on a format-only tap (correction 2).

- [ ] **Step 1: Write the failing controller tests** — `TagWriteControllerTest` keeps its existing cases (adapted to the library's `TagInspection(uid, read, maxSize, writable, needsFormat, canLock)` and `WriteResult.Written(readBack, bytes, locked)`) and adds these, over a `FakeTagIo` that implements the library's four-operation `TagIo` (mutable `inspection`, `writeResult`, `formatResult`, counters `inspectCount`/`formatCount`/`writeAttempts`, `lastWriteLock`, `lastLockExpected`, and an `inspectFailure: Throwable?`):

```kotlin
    /** R1/R2 — a formatable tag: tap one formats and writes nothing; tap two writes. */
    @Test fun aFormatableTagIsFormattedOnTapOneAndWrittenOnTapTwo() = runTest {
        io.inspection = TagInspection("04a1", TagRead.Readable(emptyList()), maxSize = -1, writable = true, needsFormat = true, canLock = true)
        controller.onTag(handle); advanceUntilIdle()
        assertEquals(1, io.formatCount); assertEquals(0, io.writeAttempts)
        assertEquals(WriteState.Idle("Formatted. Lift the tag off and hold it again to write."), controller.state.value)
        assertEquals(0, provision.begun)                       // a format-only tap creates no row (correction 2)
        io.inspection = TagInspection("04a1", TagRead.Readable(emptyList()), maxSize = 137, writable = true, needsFormat = false, canLock = true)
        io.writeResult = WriteResult.Written(intended, 95, locked = false)
        controller.onTag(handle); advanceUntilIdle()
        assertEquals(1, io.writeAttempts); assertIs<WriteState.Written>(controller.state.value); assertEquals(1, provision.begun); assertTrue(provision.completed)
    }

    /** C1 — unreadable NDEF is never treated as an empty tag. */
    @Test fun anUnreadableTagAsksBeforeItIsOverwritten() = runTest {
        io.inspection = TagInspection("04a1", TagRead.Unreadable("NDEF on tag could not be parsed", null), maxSize = 137, writable = true, needsFormat = false, canLock = true)
        controller.onTag(handle); advanceUntilIdle()
        assertEquals(WriteState.Confirm("unreadable NDEF content (NDEF on tag could not be parsed)"), controller.state.value)
        assertEquals(0, io.writeAttempts)
    }

    /** Invariant 7 — the exact message against the measured capacity, before any consent question. */
    @Test fun aTagTooSmallForTheMessageIsRefusedWithoutWriting() = runTest {
        io.inspection = TagInspection("04a1", TagRead.Readable(emptyList()), maxSize = 94, writable = true, needsFormat = false, canLock = true)
        controller.onTag(handle); advanceUntilIdle()
        assertEquals(WriteState.Error("Tag too small: it holds 94 bytes, the message needs 95."), controller.state.value)
        assertEquals(0, io.writeAttempts)
    }

    /** I1 — attempted says which sentence, never the reason text. */
    @Test fun aRefusedWriteAndAnIndeterminateWriteAreWordedDifferently() = runTest {
        io.inspection = writable137
        io.writeResult = WriteResult.Failed("tag still needs formatting", attempted = false)
        controller.onTag(handle); advanceUntilIdle()
        assertEquals(WriteState.Error("Nothing was written (tag still needs formatting). Hold the tag still and try again."), controller.state.value)
        io.writeResult = WriteResult.Failed("tag left the field", cause = IOException("lost"), attempted = true)
        controller.onTag(handle); advanceUntilIdle()
        assertEquals(WriteState.Error("The write may not have finished (tag left the field). Lift the tag off and hold it to the phone again."), controller.state.value)
    }

    /** G-6 — the lock rides on the write; the standalone lock is never called. */
    @Test fun lockIsAppliedByTheWriteItself() = runTest {
        controller.setLock(true)
        io.inspection = writable137
        io.writeResult = WriteResult.Written(intended, 95, locked = true)
        controller.onTag(handle); advanceUntilIdle()
        assertEquals(true, io.lastWriteLock); assertEquals(null, io.lastLockExpected)
        assertEquals(WriteState.Written(rowId, locked = true), controller.state.value)
    }

    /** Invariant 10 — consent is recorded, never written through the sheet's (stale) handle. */
    @Test fun confirmingRecordsConsentAndPerformsNoTagIo() = runTest {
        io.inspection = holdingOtherTag                      // Readable(records of another ServiceTag id), writable, 137
        controller.onTag(handle); advanceUntilIdle()
        assertIs<WriteState.Confirm>(controller.state.value)
        controller.confirmOverwrite(); advanceUntilIdle()
        assertEquals(0, io.writeAttempts)
        assertEquals(WriteState.Idle("Overwrite confirmed. Hold the same tag to the phone again to write."), controller.state.value)
    }

    /** The fresh tap carrying the SAME content consumes the consent and writes through its own handle. */
    @Test fun theNextTapWithTheSameContentWritesThroughTheFreshHandle() = runTest {
        io.inspection = holdingOtherTag
        controller.onTag(handle); advanceUntilIdle(); controller.confirmOverwrite(); advanceUntilIdle()
        io.writeResult = WriteResult.Written(intended, 95, locked = false)
        val fresh = FakeHandle(uid = "04a1-second-discovery")
        controller.onTag(fresh); advanceUntilIdle()
        assertEquals(1, io.writeAttempts); assertEquals(fresh, io.lastWriteHandle)
        assertIs<WriteState.Written>(controller.state.value)
    }

    /** A fresh tap carrying DIFFERENT content discards the consent and asks again. */
    @Test fun theNextTapWithDifferentContentAsksAgain() = runTest {
        io.inspection = holdingOtherTag
        controller.onTag(handle); advanceUntilIdle(); controller.confirmOverwrite(); advanceUntilIdle()
        io.inspection = holdingAThirdTag                     // a different ServiceTag id
        controller.onTag(handle); advanceUntilIdle()
        assertEquals(0, io.writeAttempts)
        assertIs<WriteState.Confirm>(controller.state.value)
        // and that second question, once confirmed, is honoured on the next matching tap
        controller.confirmOverwrite(); advanceUntilIdle()
        io.writeResult = WriteResult.Written(intended, 95, locked = false)
        controller.onTag(handle); advanceUntilIdle()
        assertEquals(1, io.writeAttempts)
    }

    /** R4 — a read failure is one fixed sentence and the next tap is still handled. */
    @Test fun aTagThatCannotBeReadIsOneSentenceAndTheNextTapStillWorks() = runTest {
        io.inspectFailure = IOException("lost")
        controller.onTag(handle); advanceUntilIdle()
        assertEquals(WriteState.Error("Could not read the tag. Hold it still and try again."), controller.state.value)
        io.inspectFailure = null; io.inspection = writable137; io.writeResult = WriteResult.Written(intended, 95, locked = false)
        controller.onTag(handle); advanceUntilIdle()
        assertEquals(2, io.inspectCount); assertIs<WriteState.Written>(controller.state.value)
    }
```

(`writable137`, `holdingOtherTag`, `holdingAThirdTag`, `intended`, `rowId`, `provision` are the fixture's existing names or their obvious additions; `FakeHandle(uid)` is a constructible handle class in ServiceTag's test file, so two distinct handles can be told apart; `FakeTagIo` records `lastWriteHandle`; the implementer keeps the file's fixture style.)

- [ ] **Step 2: Run to verify they fail** — `./gradlew :app:testDebugUnitTest --console=plain` → compilation failures (the library types are not yet imported; `Verifying` still exists).

- [ ] **Step 3: `TagWriteController.kt`** — the whole file:

```kotlin
package com.loosecannon.servicetag.ui.scan

import android.util.Log
import com.loosecannon.nfc.tagcore.NdefRecordData
import com.loosecannon.nfc.tagcore.NdefSize
import com.loosecannon.nfc.tagcore.OverwriteDecision
import com.loosecannon.nfc.tagcore.android.CapacityVerdict
import com.loosecannon.nfc.tagcore.android.TagHandle
import com.loosecannon.nfc.tagcore.android.TagInspection
import com.loosecannon.nfc.tagcore.android.TagIo
import com.loosecannon.nfc.tagcore.android.TagRead
import com.loosecannon.nfc.tagcore.android.WriteResult
import com.loosecannon.nfc.tagcore.android.WriteRoute
import com.loosecannon.nfc.tagcore.android.fit
import com.loosecannon.nfc.tagcore.android.route
import com.loosecannon.servicetag.core.model.TagBinding
import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.core.nfc.NdefCodec
import com.loosecannon.servicetag.core.nfc.OverwriteReasons
import com.loosecannon.servicetag.core.nfc.TagPayload
import com.loosecannon.servicetag.core.usecase.ProvisionTag
import com.loosecannon.servicetag.di.AppGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the write screen draws. One state at a time; the lock switch is separate state. */
sealed interface WriteState {
    /** Nothing has happened yet, the tag was only formatted, or the last tap deliberately left it alone. */
    data class Idle(val message: String) : WriteState

    /** The tag already holds something; [reason] names it, in this product's words. */
    data class Confirm(val reason: String) : WriteState

    data class Written(val tagId: String, val locked: Boolean) : WriteState
    data class Error(val message: String) : WriteState
}

/**
 * The Phase 1B write flow on the nfc-tag-core seam: read first, route before planning, confirm
 * before overwriting anything but an empty tag or the same id, write off the main thread, and let
 * the write itself lock after its own verified read-back (invariant 9 is the library's). A tag that
 * still needs formatting is formatted and nothing else — `format(null)`, no payload — and the next
 * tap is an ordinary write against the capacity that now exists (invariant 7).
 *
 * A ServiceTag row is provisioned on the first writable tap — a format-only tap creates no
 * product state at all — and is reused across retries; [abandonIfUnwritten] deletes it if the
 * screen closes before a verified write claims it, so no phantom tag is left behind.
 */
class TagWriteController(
    private val provisionTag: ProvisionTag,
    /** Outlives the screen: abandoning a row must finish even though the screen is going away. */
    private val appScope: CoroutineScope,
    private val io: TagIo,
    private val codec: NdefCodec,
    private val target: TagTarget,
    private val label: String?,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(graph: AppGraph, io: TagIo, target: TagTarget, label: String?, scope: CoroutineScope) :
        this(graph.provisionTag, graph.appScope, io, graph.ndefCodec, target, label, scope)

    private val _state = MutableStateFlow<WriteState>(InitialState)
    val state: StateFlow<WriteState> = _state.asStateFlow()

    private val _lock = MutableStateFlow(false)

    /** Whether the user has armed "lock permanently"; the warning dialog lives on the screen. */
    val lock: StateFlow<Boolean> = _lock.asStateFlow()

    @Volatile private var pending: TagBinding? = null

    /**
     * What the user agreed to overwrite. The handle captured before the confirmation sheet can go
     * stale while it is up (the NFC service re-discovers the tag and then refuses the old handle
     * with "Tag is out of date" — seen on an Android 17 phone), so a confirmation is remembered as
     * consent for *this content* and honoured on the next tap of a tag carrying it (invariant 10).
     */
    @Volatile private var confirmedOverwrite: TagPayload? = null
    @Volatile private var done = false
    @Volatile private var busy = false

    /**
     * The content the confirmation sheet is asking about; it owns [busy] until it is answered. Only
     * the content is kept: the handle that raised the question is exactly the one that may be
     * stale by the time the answer arrives, so it is never written through (owner, 2026-09-17).
     */
    @Volatile private var awaitingAnswer: TagPayload? = null

    fun setLock(value: Boolean) { _lock.value = value }

    /** Reader mode calls this from a binder thread; nothing here touches the main thread (invariant 11). */
    fun onTag(tag: TagHandle) {
        if (busy || done) return
        busy = true
        scope.launch {
            var sheetOwnsBusy = false
            try {
                sheetOwnsBusy = handle(tag)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The platform's message is not the user's business; the exception is the log's (R4).
                Log.w(TAG, "inspect failed", e)
                _state.value = WriteState.Error("Could not read the tag. Hold it still and try again.")
            } finally {
                if (!sheetOwnsBusy) busy = false
            }
        }
    }

    /** Returns true when the confirmation sheet now owns the [busy] flag. */
    private suspend fun handle(tag: TagHandle): Boolean {
        val inspection = withContext(ioDispatcher) { io.inspect(tag) }
        if (inspection == null) {
            _state.value = WriteState.Error("This tag does not support NDEF. Use an NTAG213/215/216 or similar.")
            return false
        }
        // Route BEFORE planning, and without a message size: a tag that needs formatting has no
        // capacity yet, and a read-only tag is refused before capacity is even a question (F-3).
        val writable = when (val r = inspection.route()) {
            WriteRoute.Format -> { format(tag); return false }
            WriteRoute.ReadOnly -> { _state.value = WriteState.Error("This tag is read-only (locked). Nothing written."); return false }
            is WriteRoute.Writable -> r
        }
        val row = pending ?: provisionTag.begin(target, label).also { pending = it }
        val intended = codec.encodeV1(row.id)
        when (val v = writable.fit(NdefSize.serialisedSize(intended))) {
            is CapacityVerdict.TooSmall -> {
                _state.value = WriteState.Error("Tag too small: it holds ${v.maxSize} bytes, the message needs ${v.needed}.")
                return false
            }
            CapacityVerdict.Write -> Unit
        }
        val existing = existingOn(inspection)
        val consent = confirmedOverwrite
        if (consent != null) {
            confirmedOverwrite = null                       // consent is consumed by this tap, either way
            if (consent == existing) {
                // The user already agreed to replace exactly this content; THIS tap's handle is
                // fresh, so the write goes ahead through it without asking twice.
                write(tag, intended, row)
                return false
            }
            // a different tag, or the same tag changed under the sheet: ask again
        }
        return when (val d = OverwriteReasons.decide(existing, row.id)) {
            OverwriteDecision.Proceed -> { write(tag, intended, row); false }
            is OverwriteDecision.Confirm -> {
                awaitingAnswer = existing
                _state.value = WriteState.Confirm(OverwriteReasons.sentence(d))
                true
            }
        }
    }

    /** What the tag holds, in this product's terms. Unreadable NDEF is unreadable — never "empty" (C1). */
    private fun existingOn(inspection: TagInspection): TagPayload = when (val read = inspection.read) {
        is TagRead.Readable -> codec.decode(read.records)
        is TagRead.Unreadable -> {
            read.cause?.let { Log.w(TAG, "tag NDEF unreadable: ${read.reason}", it) }
            TagPayload.Malformed(read.reason)
        }
    }

    /** `format(null)`: the tag is made NDEF-capable, left empty and unlocked, and nothing is planned or written (R1). */
    private suspend fun format(tag: TagHandle) {
        when (val r = withContext(ioDispatcher) { io.format(tag) }) {
            WriteResult.Formatted -> _state.value = WriteState.Idle("Formatted. Lift the tag off and hold it again to write.")
            is WriteResult.Failed -> {
                r.cause?.let { Log.w(TAG, "format failed: ${r.reason}", it) }
                _state.value = WriteState.Error("Could not format the tag (${r.reason}). Hold it still and try again.")
            }
            WriteResult.Unsupported -> _state.value = WriteState.Error("This tag does not support NDEF.")
            is WriteResult.Written, is WriteResult.TooSmall, WriteResult.ReadOnly, is WriteResult.VerifyMismatch ->
                _state.value = WriteState.Error("Unexpected result while formatting. Hold the tag still and try again.")
        }
    }

    /**
     * "Overwrite" on the confirmation sheet: record consent for the content that was asked about
     * and release the sheet. NO tag I/O here — the handle that raised the question may be stale;
     * the next tap re-inspects through a fresh handle and, if the content still matches, writes
     * through that one (invariant 10).
     */
    fun confirmOverwrite() {
        val asked = awaitingAnswer ?: return
        awaitingAnswer = null
        confirmedOverwrite = asked
        busy = false
        _state.value = WriteState.Idle("Overwrite confirmed. Hold the same tag to the phone again to write.")
    }

    /** "Keep it", and the same thing a dismissed sheet means: the tag is left exactly as it was. */
    fun keepIt() {
        if (awaitingAnswer == null) return
        awaitingAnswer = null
        confirmedOverwrite = null
        busy = false
        _state.value = WriteState.Idle("Not written. The tag was left as it was.")
    }

    private suspend fun write(tag: TagHandle, intended: List<NdefRecordData>, row: TagBinding) {
        val wantLock = _lock.value
        when (val r = withContext(ioDispatcher) { io.write(tag, intended, wantLock) }) {
            // A Written is verified by construction; the lock, if asked for, rode on it (invariant 9).
            is WriteResult.Written -> finishWrite(row, tag.uid, r.locked)
            is WriteResult.TooSmall ->
                _state.value = WriteState.Error("Tag too small: it holds ${r.maxSize} bytes, the message needs ${r.needed}.")
            WriteResult.ReadOnly ->
                _state.value = WriteState.Error("This tag is read-only (locked). Nothing written.")
            WriteResult.Unsupported ->
                _state.value = WriteState.Error("This tag does not support NDEF.")
            WriteResult.Formatted ->
                _state.value = WriteState.Error("Unexpected result while writing. Hold the tag still and try again.")
            is WriteResult.VerifyMismatch ->
                _state.value = WriteState.Error("Read-back differs from what was written. Nothing recorded — try again.")
            is WriteResult.Failed -> {
                r.cause?.let { Log.w(TAG, "write failed: ${r.reason}", it) }
                // `attempted` — not the reason text — says whether the radio was reached (I1).
                _state.value = WriteState.Error(
                    if (!r.attempted) "Nothing was written (${r.reason}). Hold the tag still and try again."
                    else "The write may not have finished (${r.reason}). Lift the tag off and hold it to the phone again.",
                )
            }
        }
    }

    private suspend fun finishWrite(row: TagBinding, uid: String?, locked: Boolean) {
        val completed = provisionTag.complete(row.id, uid)
        done = true
        _state.value = WriteState.Written(completed.id.value, locked)
    }

    /**
     * Deletes the provisioned row unless a verified write already claimed it. Returns the job so
     * a test can wait for it; the screen fires and forgets, on a scope that outlives it.
     */
    fun abandonIfUnwritten(): Job? {
        val row = pending
        if (done || row == null) return null
        return appScope.launch { provisionTag.abandon(row.id) }
    }

    private companion object {
        const val TAG = "TagWriteController"
        val InitialState = WriteState.Idle("Hold a blank or reusable tag to the back of the phone.")
    }
}
```

`describe()` and `WriteState.Verifying` are gone with the branch that used them; if `WriteTagScreen` renders `Verifying`, delete that arm.

- [ ] **Step 4: Graph, trampoline, screens** — `AppGraph.kt`: `import com.loosecannon.nfc.tagcore.TagIdentity`; the `RealTagIo(ndefCodec)` construction (wherever the screen builds its `TagIo`) becomes the library's `RealTagIo` object; `NfcDispatchActivity.kt`: `import com.loosecannon.nfc.tagcore.android.ndefRecords`; `ScanScreen.kt`, `WriteTagScreen.kt`: `import com.loosecannon.nfc.tagcore.android.NfcReaderModeSession` and `NfcTagHandle`; `ScanViewModels.kt`: whatever it imports of `TagInspection`/`TagWriter` (line 103 builds a `Malformed` when a tag has no NDEF — keep the sentence, take `TagRead` into account if it reads an inspection). `FakeGraph.kt`, `TagUseCasesRoomTest.kt`, `TagIdentityBindingTest.kt`, `NfcIdentityDeviceProofTest.kt`, `TagIdentityDispatchTest.kt`: `TagIdentity` from the library; nothing else changes in them (the binding test still asserts the lower-case external type and `aarPackage == BuildConfig.APPLICATION_ID`).

- [ ] **Step 5: Delete** — `git rm app/src/main/kotlin/com/loosecannon/servicetag/nfc/NdefBridge.kt app/src/main/kotlin/com/loosecannon/servicetag/nfc/NfcReaderModeSession.kt app/src/main/kotlin/com/loosecannon/servicetag/nfc/TagWriter.kt`. Then the constraint greps: `git grep -nE 'class (NdefBridge|TagWriter|TagInspection|NfcReaderModeSession|RealTagIo)|interface (TagIo|TagHandle)|object (NdefSize|OverwritePolicy)' -- 'app/**' 'core/**'` → 0; `git grep -n 'servicetag.nfc.\(NdefBridge\|TagWriter\|NfcReaderModeSession\|TagInspection\|WriteResult\)' -- 'app/**' 'core/**'` → 0; `git grep -n 'Verifying\|awaitingVerify' -- 'app/**'` → 0.

- [ ] **Step 6: Gate and the emulator** — the per-task gate green; record `:app:testDebugUnitTest`'s XML total and `TagWriteControllerTest`'s count. Then `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --console=plain` → the Phase D suites (13 classes) still green on the library-backed build; record the total (Phase D's was 66) and any change with its reason.

- [ ] **Step 7: Commit** — `git add -A && git commit -m "app: the write flow rides the shared seam; format is not a write, lock rides the write, unreadable is not empty"`.

---

### Task 4 (§A.4 verify 2–4, target §6.3): ServiceTag's CI file, the pin script, the four negative tests, the local clean clone

**Files:**
- Create: `tools/check-submodule-pin.sh`
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: `tools/check-submodule-pin.sh`** — the assertion from target §6.3 as a script, usable locally and by CI:

```bash
#!/usr/bin/env bash
# The shared library is consumed only at an exact nfc-tag-core-v* tag, initialised, clean, and on
# the same agp/kotlin pins as this app (target §6.3, §19). Exit 1 names the first thing that is not so.
set -euo pipefail
cd "$(dirname "$0")/.."
test -f libs/nfc-tag-core/nfc-core/build.gradle.kts \
  || { echo "libs/nfc-tag-core is not initialised"; exit 1; }
pinned=$(git ls-tree HEAD libs/nfc-tag-core | awk '{print $3}')
actual=$(git -C libs/nfc-tag-core rev-parse HEAD)
[ "$pinned" = "$actual" ] \
  || { echo "submodule is at $actual but this commit pins $pinned"; exit 1; }
git -C libs/nfc-tag-core fetch --tags --force --quiet || true
git -C libs/nfc-tag-core describe --exact-match --match 'nfc-tag-core-v*' --tags HEAD \
  || { echo "submodule is not at an exact nfc-tag-core-v* tag (mutable HEAD)"; exit 1; }
diff <(grep -E '^(agp|kotlin) =' gradle/libs.versions.toml) \
     <(grep -E '^(agp|kotlin) =' libs/nfc-tag-core/gradle/libs.versions.toml) \
  || { echo "library catalog pins a different agp/kotlin than this app"; exit 1; }
[ -z "$(git -C libs/nfc-tag-core status --porcelain)" ] \
  || { echo "submodule working tree is dirty"; exit 1; }
echo "submodule pin ok: $(git -C libs/nfc-tag-core describe --exact-match --match 'nfc-tag-core-v*' --tags HEAD)"
```

`chmod +x`; run it → `submodule pin ok: nfc-tag-core-v0.1.0`.

- [ ] **Step 2: `.github/workflows/ci.yml`** — the ordinary, unprivileged pipeline (owner ruling: no signing key, no passwords, no release secrets, no Release, no signed APK):

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
        with:
          submodules: recursive
          fetch-depth: 0
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: android-actions/setup-android@v3
        with:
          packages: platform-tools
          accept-android-sdk-licenses: true
      - uses: gradle/actions/setup-gradle@v4
      - name: assert the shared library is initialised at the pinned tag
        run: bash tools/check-submodule-pin.sh
      - name: unit tests and debug build
        run: ./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results
          path: |
            libs/nfc-tag-core/nfc-core/build/test-results
            libs/nfc-tag-core/nfc-android/build/test-results
            core/build/test-results
            app/build/test-results
```

Run the two `run:` lines by hand from the worktree root; both succeed. `grep -c 'secrets\.' .github/workflows/ci.yml` → 0.

- [ ] **Step 3: The four negative tests, each demonstrated failing and then restored** (§A.4 verify 3) — quote every transcript:

```bash
# (a) missing submodule → the settings require fires at configuration time with the fix command
SCRATCH=$(mktemp -d)   # the session scratchpad; never a literal /tmp path in a tracked file
mv libs/nfc-tag-core/nfc-core/build.gradle.kts "$SCRATCH/guard"
./gradlew projects --console=plain 2>&1 | grep -A2 'libs/nfc-tag-core is missing'; mv "$SCRATCH/guard" libs/nfc-tag-core/nfc-core/build.gradle.kts; rm -rf "$SCRATCH"
# (b) wrong commit → the script's FIRST failing check speaks: "submodule is at <sha> but this commit pins <sha>" (the sha-equality check precedes the tag check; the "not at an exact nfc-tag-core-v* tag" message needs a gitlink that itself points at an untagged commit — found in execution 2026-09-17)
git -C libs/nfc-tag-core checkout --quiet HEAD~1; bash tools/check-submodule-pin.sh; echo "exit=$?"; git -C libs/nfc-tag-core checkout --quiet nfc-tag-core-v0.1.0
# (c) dirty submodule → "submodule working tree is dirty"
touch libs/nfc-tag-core/nfc-core/src/main/kotlin/x; bash tools/check-submodule-pin.sh; echo "exit=$?"; rm libs/nfc-tag-core/nfc-core/src/main/kotlin/x
# (d) a catalog alias the library uses removed → a configuration-time failure naming the alias
sed -i 's/^android-library = .*$//' gradle/libs.versions.toml; ./gradlew projects --console=plain 2>&1 | grep -m1 -i 'android.library\|android-library'; git checkout gradle/libs.versions.toml
git status --short | wc -l   # 0 after the four restores
```

(`mktemp -d` resolves to the session scratchpad in the agent shell; the transcript quotes the real path with `~`.)

- [ ] **Step 4: The local clean clone** (§A.4 verify 4) — `git clone --recurse-submodules ~/Documents/Projects/AndroidStudioProjects/ServiceTag-split <scratch>/ServiceTag` (a never-used directory in the session scratchpad; `-b product-split`), `export ANDROID_HOME=~/Android/Sdk`, `bash tools/check-submodule-pin.sh`, then the CI task list with `--no-build-cache`; `BUILD SUCCESSFUL`, no `FROM-CACHE` on a test task; XML counts equal the in-place ones; delete the clone.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "ci: checkout the submodule, assert the pin, run the library suites too"`.

---

## Part B — NoteTag (repository `NoteTag`, branch `master`)

### Task 5: catalog alias, submodule at the tag, settings block, module edges — NoteTag

Identical to Task 1 with `rootProject.name = "NoteTag"`: Step 1 the alias; Step 2 `git submodule add https://github.com/GonzRon/nfc-tag-core.git libs/nfc-tag-core` + checkout `nfc-tag-core-v0.1.0` (`7e0377a`) + `git add .gitmodules libs/nfc-tag-core`; Step 3 the §6.2 block verbatim; Step 4 `core/build.gradle.kts` gains `implementation(project(":nfc-core"))` (G-1), `app/build.gradle.kts` gains `implementation(project(":nfc-android"))`, and the root `build.gradle.kts` gains `alias(libs.plugins.android.library) apply false` (the AGP plugin-classpath rule found in Task 1); Step 5 the same four verifications (`projects` shows four modules; the gate `./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain` green; the `agp`/`kotlin` diff empty; the submodule clean). **No remote is added to NoteTag** — `git remote | wc -l` stays 0; the submodule's own remote is the library's URL and is a read.

- [ ] Commit: `git add -A && git commit -m "wire in nfc-tag-core at v0.1.0 as subprojects"`.

---

### Task 6 (`:core` half): the four interim files go; `NoteTagCodec`, `OverwriteWording`, `WritePlanner`, `ResolveTap` consume the library

**Files:**
- Delete: `core/src/main/kotlin/com/loosecannon/notetag/core/nfc/{NdefEnvelope,NdefRecordData,NdefSize,TagIdentity}.kt`; `core/src/test/kotlin/com/loosecannon/notetag/core/nfc/{NdefEnvelopeTest,NdefSizeTest}.kt` (library-owned cases; the 49-byte JOPLIN_NOTE case moves to `NoteTagCodecTest`)
- Modify: `core/…/core/tag/NoteTagCodec.kt` (over `com.loosecannon.nfc.tagcore.NdefEnvelope`/`TagContent`/`UuidBytes`), `core/…/core/nfc/OverwriteWording.kt` (its sentences keyed by the library's `OverwriteReason`, its decision by `OverwritePolicy`), `core/…/core/write/WritePlanner.kt` and `core/…/core/resolve/ResolveTap.kt` (imports), their tests (`NoteTagCodecTest`, `OverwriteWordingTest`, `WritePlannerTest`, `ResolveTapTest`: imports, plus the cases named below)

**Interfaces:**
- Produces (unchanged names): `NoteTagCodec(identity: com.loosecannon.nfc.tagcore.TagIdentity)` with `decode(records): NoteTagContent`, `encode(content: Writable): List<NdefRecordData>`, `body(content)`, the constants; `OverwriteWording.reason(existing: NoteTagContent, intended: NoteTagContent.Writable): String?` (null = proceed) and `OverwriteWording.DEVICE_BOUND` — same signature, now implemented as `OverwritePolicy.decide(existingContent(existing), isSameIdentity = existing == intended)` mapped to NoteTag's sentences; NEW `OverwriteWording.existingContent(c: NoteTagContent): ExistingContent`.

- [ ] **Step 1: Write the failing tests.** `NoteTagCodecTest` gains (from the deleted `NdefSizeTest`) `aJoplinNoteMessageIsFortyNineBytes` using `com.loosecannon.nfc.tagcore.NdefSize`. `OverwriteWordingTest` keeps every sentence assertion verbatim and adds:

```kotlin
    @Test fun theMappingToExistingContent() {
        assertEquals(ExistingContent.Empty, OverwriteWording.existingContent(NoteTagContent.Empty))
        assertEquals(ExistingContent.Ours("JOPLIN_NOTE 0123456789abcdeffedcba9876543210"), OverwriteWording.existingContent(NoteTagContent.JoplinNote("0123456789abcdeffedcba9876543210")))
        assertEquals(ExistingContent.OursUnsupported("version 2"), OverwriteWording.existingContent(NoteTagContent.NewerVersion(2)))
        assertEquals(ExistingContent.OursUnsupported("kind 4"), OverwriteWording.existingContent(NoteTagContent.UnknownKind(4)))
        assertEquals(ExistingContent.Foreign("tnf=4 type=com.loosecannon.servicetag:tag"), OverwriteWording.existingContent(NoteTagContent.Foreign("tnf=4 type=com.loosecannon.servicetag:tag")))
        assertEquals(ExistingContent.Unreadable("flags 0x01 are reserved"), OverwriteWording.existingContent(NoteTagContent.Malformed("flags 0x01 are reserved")))
    }

    /** The same content is a retry: no question (SAME_TAG). A different note is a question (OTHER_TAG_SAME_PRODUCT). */
    @Test fun sameContentProceedsDifferentContentAsks() {
        val a = NoteTagContent.JoplinNote("0123456789abcdeffedcba9876543210")
        val b = NoteTagContent.JoplinNote("aabbccdd11223344eeff556677889900")
        assertNull(OverwriteWording.reason(a, a))
        assertNotNull(OverwriteWording.reason(a, b))
    }
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:test --console=plain` → compilation failures.

- [ ] **Step 3: `NoteTagCodec.kt`** — replace the envelope field and the two envelope calls; the parse and body logic is unchanged:

```kotlin
class NoteTagCodec(private val identity: TagIdentity) {

    fun decode(records: List<NdefRecordData>): NoteTagContent = when (val c = NdefEnvelope.decode(identity, records)) {
        TagContent.Empty -> NoteTagContent.Empty
        is TagContent.Foreign -> NoteTagContent.Foreign(c.description)
        is TagContent.Recognised -> parse(c.body)
    }

    fun encode(content: NoteTagContent.Writable): List<NdefRecordData> = NdefEnvelope.encode(identity, body(content))
```

with `LocalRef` bytes via `UuidBytes.toBytes(content.uuid)` and `UuidBytes.fromBytes(kindBody)`; imports `com.loosecannon.nfc.tagcore.{NdefEnvelope, NdefRecordData, TagContent, TagIdentity, UuidBytes}`; `java.nio.ByteBuffer` no longer imported.

- [ ] **Step 4: `OverwriteWording.kt`** — the sentences stay byte-identical to today's file; only the decision moves to the library:

```kotlin
package com.loosecannon.notetag.core.nfc

import com.loosecannon.nfc.tagcore.ExistingContent
import com.loosecannon.nfc.tagcore.OverwriteDecision
import com.loosecannon.nfc.tagcore.OverwritePolicy
import com.loosecannon.nfc.tagcore.OverwriteReason
import com.loosecannon.notetag.core.tag.NoteTagContent

/** Read-before-write: what NoteTag says when the tag already holds something. Null means write without asking. */
object OverwriteWording {
    const val SIBLING_DOMAIN = "com.loosecannon.servicetag"

    /** Shown BEFORE a LOCAL_REF write, as one of the confirmation's reasons (owner, 2026-09-17). */
    const val DEVICE_BOUND = "This tag needs this phone to open. Back up NoteTag to protect the link."

    /** NoteTag's classification of what its codec read, in the library's product-neutral terms. */
    fun existingContent(c: NoteTagContent): ExistingContent = when (c) {
        NoteTagContent.Empty -> ExistingContent.Empty
        is NoteTagContent.JoplinNote -> ExistingContent.Ours("JOPLIN_NOTE ${c.id}")
        is NoteTagContent.Uri -> ExistingContent.Ours("URI ${c.uri}")
        is NoteTagContent.LocalRef -> ExistingContent.Ours("LOCAL_REF ${c.uuid}")
        is NoteTagContent.NewerVersion -> ExistingContent.OursUnsupported("version ${c.version}")
        is NoteTagContent.UnknownKind -> ExistingContent.OursUnsupported("kind ${c.kind}")
        is NoteTagContent.Foreign -> ExistingContent.Foreign(c.description)
        is NoteTagContent.Malformed -> ExistingContent.Unreadable(c.reason)
    }

    /**
     * The library decides (`OverwritePolicy`: one confirmation for anything but an empty tag or the
     * very content being written); NoteTag says it, in the sentences Phase E ratified.
     */
    fun reason(existing: NoteTagContent, intended: NoteTagContent.Writable): String? =
        when (val d = OverwritePolicy.decide(existingContent(existing), isSameIdentity = existing == intended)) {
            OverwriteDecision.Proceed -> null
            is OverwriteDecision.Confirm -> when (d.reason) {
                OverwriteReason.OTHER_TAG_SAME_PRODUCT -> "This NoteTag tag already points somewhere else."
                OverwriteReason.SAME_PRODUCT_UNSUPPORTED -> when (existing) {
                    is NoteTagContent.NewerVersion -> "This tag was written by a newer NoteTag (format ${existing.version})."
                    is NoteTagContent.UnknownKind -> "This NoteTag tag holds a kind this version does not know (${existing.kind})."
                    else -> error("SAME_PRODUCT_UNSUPPORTED is only NewerVersion or UnknownKind")
                }
                OverwriteReason.FOREIGN ->
                    if (d.detail.contains("type=$SIBLING_DOMAIN:")) "This tag belongs to ServiceTag."
                    else "This tag holds something else (${d.detail})."
                OverwriteReason.UNREADABLE -> "This tag holds unreadable NoteTag content (${d.detail})."
                OverwriteReason.EMPTY_TAG, OverwriteReason.SAME_TAG -> error("${d.reason} never asks")
            }
        }
}
```

`OverwriteWordingTest`'s existing assertions are the proof the sentences did not change; `d.detail` is the description/reason the classification handed in, verbatim (library invariant 13), so the sibling check and the parenthesised reasons read exactly as before.

- [ ] **Step 5: `WritePlanner.kt`, `ResolveTap.kt`** — imports only (`NdefRecordData`, `NdefSize` from `com.loosecannon.nfc.tagcore`). `WritePlan.Refused.records` stays `emptyList()` and is never sized (the library's `NdefSize` refuses an empty list); `WritePlannerTest` gains one case asserting the planner never calls `serialisedSize` for a `Refused` plan (a refused plan is returned before any size is computed — assert by ordering: an unparseable share text yields `Refused` with no exception).

- [ ] **Step 6: Delete, grep, run** — `git rm` the four main and two test files; `git grep -n 'Interim copy' -- 'core/**'` → 0; `./gradlew :core:test --console=plain` green; record every class's count (the `:core` total drops by the two deleted classes' 12 — NdefEnvelopeTest 7, NdefSizeTest 5 — and rises by the four moved/added cases: 78 → 70).

- [ ] **Step 7: Commit** — `git add -A && git commit -m "core: the envelope, the size and the overwrite rule come from the library; the words stay ours"`.

---

### Task 7 (`:app` half): the interim adapter goes; the controller consumes the seam with R1–R4 dispositioned

**Files:**
- Delete: `app/src/main/kotlin/com/loosecannon/notetag/nfc/{NdefBridge,NfcReaderModeSession,TagWriter,TagIo}.kt`
- Modify: `app/…/write/NoteTagWriteController.kt` (rewritten — full text below), `app/…/NoteTagApp.kt` (`RealTagIo` object; library `TagIdentity`), `app/…/nfc/NfcDispatchActivity.kt`, `app/…/ui/WriteScreen.kt` (imports), `app/src/test/…/write/FakeTagIo.kt` and `app/src/androidTest/…/ui/FakeTagIo.kt` (the library's four-operation seam), `app/src/test/…/write/NoteTagWriteControllerTest.kt` (cases below), `app/src/test/…/ui/MainViewModelTest.kt`, `app/src/androidTest/…/{ui/WriteScreenDeviceBoundTest,ui/AppSmokeTest,nfc/NdefSizeDeviceTest,nfc/TagIdentityDispatchTest,nfc/AmbientDispatchDeviceProofTest}.kt` (imports; `NdefSizeDeviceTest` kept per G-7 over the library's `NdefSize`/`serialisedSize()`)

**Interfaces:**
- Produces: `NoteTagWriteController(tagIo: TagIo, codec, store, sharedText, scope, cleanupScope, ioDispatcher, clock, newUuid)` — same `WriteState` (`Waiting`, `Confirm(reasons, action)`, `Writing`, `Written(entry, deviceBound)`, `Refused`, `Error`), same public functions.

- [ ] **Step 1: Write the failing tests** — `NoteTagWriteControllerTest` keeps every existing case, retargeted to the library's `TagInspection(uid, read, maxSize, writable, needsFormat, canLock)` / `WriteResult.Written(readBack, bytes, locked)`; the former "unverified format is not a write" case becomes the two-tap case; and these are added or reshaped:

```kotlin
    /** R1 — the Format tap plans nothing, mints no uuid, persists nothing. */
    @Test fun aFormatableTagIsFormattedAndNothingIsPlannedOrPersisted() = runTest {
        var minted = 0
        val c = controller(newUuid = { minted++; REF })
        io.inspection = TagInspection(FakeHandle.uid, TagRead.Readable(emptyList()), maxSize = -1, writable = true, needsFormat = true, canLock = true)
        c.onTag(FakeHandle); advanceUntilIdle()
        assertEquals(WriteState.Waiting("Formatted the tag. Hold it to the phone again to write the link."), c.state.value)
        assertEquals(1, io.formatCount); assertEquals(0, io.writeAttempts); assertEquals(0, minted)
        assertEquals(emptyList(), store.all())                                   // nothing persisted, confirmed or not
        // R2 — the chip comes back as Ndef, empty, with a real capacity: the second tap plans against it
        io.inspection = TagInspection(FakeHandle.uid, TagRead.Readable(emptyList()), maxSize = 60, writable = true, needsFormat = false, canLock = true)
        io.writeResult = written()
        c.onTag(FakeHandle); advanceUntilIdle()
        assertEquals(WriteState.Confirm(listOf(OverwriteWording.DEVICE_BOUND), "Write"), c.state.value)   // the URI does not fit 60: LOCAL_REF, warned BEFORE the write
        assertEquals(1, minted)
    }

    /** C1 — unreadable NDEF is a question, never "empty". */
    @Test fun anUnreadableTagIsAQuestionNotAnEmptyTag() = runTest {
        io.inspection = TagInspection(FakeHandle.uid, TagRead.Unreadable("NDEF on tag could not be parsed", null), maxSize = 137, writable = true, needsFormat = false, canLock = true)
        controller.onTag(FakeHandle); advanceUntilIdle()
        val s = assertIs<WriteState.Confirm>(controller.state.value)
        assertEquals("Write over it", s.action); assertEquals(0, io.writeAttempts)
    }

    /** I1 — attempted decides retain vs remove for a persisted LOCAL_REF mapping. */
    @Test fun aRefusedWriteRemovesTheMappingAnIndeterminateOneRetainsIt() = runTest {
        // (a) attempted = false: nothing reached the tag → the mapping goes
        io.inspection = deviceBoundInspection; io.writeResult = WriteResult.Failed("tag still needs formatting", attempted = false)
        drive(controller)          // tap → Confirm(DEVICE_BOUND) → confirm() → tap
        assertNull(store.get(REF_KEY))
        assertEquals(WriteState.Error("Nothing was written (tag still needs formatting). Hold the tag still and try again."), controller.state.value)
        // (b) attempted = true: the write may have landed → retained, unconfirmed
        val c2 = controller(); io.writeResult = WriteResult.Failed("tag left the field", cause = IOException("lost"), attempted = true)
        drive(c2)
        assertNotNull(store.get(REF_KEY)); assertNull(store.get(REF_KEY)!!.writtenAt); assertEquals(emptyList(), store.list())
        assertEquals(WriteState.Error("Writing may not have finished (tag left the field). If the tag was touched it may already hold the link; hold the same tag again."), c2.state.value)
    }

    /** Invariant 7 — fit() before consent; too small removes a pre-persisted mapping and writes nothing. */
    @Test fun aTagTooSmallEvenForTheLocalRefIsRefusedBeforeAnything() = runTest {
        io.inspection = TagInspection(FakeHandle.uid, TagRead.Readable(emptyList()), maxSize = 40, writable = true, needsFormat = false, canLock = true)  // LOCAL_REF needs 49
        controller.onTag(FakeHandle); advanceUntilIdle()
        assertEquals(WriteState.Error("This tag is too small: it holds 40 bytes and this needs 49."), controller.state.value)
        assertEquals(0, io.writeAttempts); assertEquals(emptyList(), store.all())
    }
```

(`store.all()` is the fake store's unfiltered view; the fixture helpers `written()`, `deviceBoundInspection`, `drive()` follow the file's existing style.) `FakeTagIo` (both source sets): `inspection`, `inspectFailure`, `writeResult`, `formatResult = Formatted`, `lockResult = true`, counters, `recordsWritten`, `lastWriteLock`, `lastLockExpected`.

- [ ] **Step 2: Run to verify they fail.**

- [ ] **Step 3: `NoteTagWriteController.kt`** — the whole file:

```kotlin
package com.loosecannon.notetag.write

import android.util.Log
import com.loosecannon.nfc.tagcore.NdefSize
import com.loosecannon.nfc.tagcore.android.CapacityVerdict
import com.loosecannon.nfc.tagcore.android.TagHandle
import com.loosecannon.nfc.tagcore.android.TagInspection
import com.loosecannon.nfc.tagcore.android.TagIo
import com.loosecannon.nfc.tagcore.android.TagRead
import com.loosecannon.nfc.tagcore.android.WriteResult
import com.loosecannon.nfc.tagcore.android.WriteRoute
import com.loosecannon.nfc.tagcore.android.fit
import com.loosecannon.nfc.tagcore.android.route
import com.loosecannon.notetag.core.nfc.OverwriteWording
import com.loosecannon.notetag.core.store.TagEntry
import com.loosecannon.notetag.core.store.TagStore
import com.loosecannon.notetag.core.tag.NoteTagCodec
import com.loosecannon.notetag.core.tag.NoteTagContent
import com.loosecannon.notetag.core.write.WritePlan
import com.loosecannon.notetag.core.write.WritePlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

sealed interface WriteState {
    data class Waiting(val message: String) : WriteState
    /**
     * Every sentence in [reasons] is shown verbatim, with exactly two actions: [action] ("Write over
     * it" when the tag holds something, else "Write") and Cancel (P11). The device-bound sentence
     * (OverwriteWording.DEVICE_BOUND) is one of the reasons whenever the plan is LOCAL_REF — the
     * binding warning BEFORE the write (owner, 2026-09-17).
     */
    data class Confirm(val reasons: List<String>, val action: String) : WriteState
    data object Writing : WriteState
    data class Written(val entry: TagEntry, val deviceBound: Boolean) : WriteState
    data class Refused(val reason: String) : WriteState
    data class Error(val message: String) : WriteState
}

class NoteTagWriteController(
    private val tagIo: TagIo,
    private val codec: NoteTagCodec,
    private val store: TagStore,
    private val sharedText: String?,
    private val scope: CoroutineScope,
    /** Where a mapping's cleanup runs; outlives [scope] on purpose (a torn-down screen must still undo a persisted mapping). */
    private val cleanupScope: CoroutineScope = scope,
    /** Where the blocking [TagIo] calls run; a screen's scope dispatches on the main thread. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newUuid: () -> UUID = UUID::randomUUID,
) {
    private val _state = MutableStateFlow<WriteState>(WriteState.Waiting("Hold a tag to the phone."))
    val state: StateFlow<WriteState> = _state
    private val busy = AtomicBoolean(false)
    private var pending: Pending? = null      // a confirmation awaits the next tap of the same tag content
    @Volatile private var done = false

    /** Consent is for THIS existing content and THIS plan kind (invariant 10). */
    private class Pending(val plan: WritePlan, val existing: NoteTagContent, val consented: Boolean)

    /** Runs on the reader-mode binder thread; every state change is a flow emission (invariant 11). */
    fun onTag(tag: TagHandle) {
        if (done || !busy.compareAndSet(false, true)) return
        scope.launch {
            try { handle(tag) }
            catch (t: CancellationException) { throw t }
            // One sentence, never the platform's; the exception goes to the log, not the user (R4).
            catch (t: Throwable) { Log.w(TAG, "inspect failed", t); _state.value = WriteState.Error("Could not read the tag. Hold it still and try again.") }
            finally { busy.set(false) }
        }
    }

    private suspend fun handle(tag: TagHandle) {
        val inspection = withContext(ioDispatcher) { tagIo.inspect(tag) }
            ?: run { _state.value = WriteState.Error("This tag type is not supported."); return }
        // Route first, with no message size: a tag that needs formatting has no capacity and gets
        // no plan, no uuid and no mapping (R1); a read-only tag is refused before capacity matters.
        val writable = when (val r = inspection.route()) {
            WriteRoute.Format -> { format(tag); return }
            WriteRoute.ReadOnly -> { _state.value = WriteState.Error("This tag is read-only."); return }
            is WriteRoute.Writable -> r
        }
        val plan = WritePlanner.plan(sharedText, writable.maxSize, codec, newUuid)
        if (plan is WritePlan.Refused) { _state.value = WriteState.Refused(plan.reason); return }
        when (val v = writable.fit(NdefSize.serialisedSize(plan.records))) {
            is CapacityVerdict.TooSmall -> { _state.value = WriteState.Error("This tag is too small: it holds ${v.maxSize} bytes and this needs ${v.needed}."); return }
            CapacityVerdict.Write -> Unit
        }
        val existing = existingOn(inspection)
        val prior = pending
        pending = null
        val reasons = listOfNotNull(
            OverwriteWording.reason(existing, contentOf(plan)),
            if (plan is WritePlan.DeviceBound) OverwriteWording.DEVICE_BOUND else null,   // the warning BEFORE the write
        )
        val sameQuestion = prior?.consented == true && prior.existing == existing && prior.plan::class == plan::class
        if (reasons.isNotEmpty() && !sameQuestion) {
            pending = Pending(plan, existing, consented = false)
            _state.value = WriteState.Confirm(reasons, action = if (reasons.first() != OverwriteWording.DEVICE_BOUND) "Write over it" else "Write")
            return
        }
        write(tag, plan)
    }

    /** What the tag holds, in NoteTag's terms. Unreadable NDEF is unreadable — a question, never "empty" (C1). */
    private fun existingOn(inspection: TagInspection): NoteTagContent = when (val read = inspection.read) {
        is TagRead.Readable -> codec.decode(read.records)
        is TagRead.Unreadable -> { read.cause?.let { Log.w(TAG, "tag NDEF unreadable: ${read.reason}", it) }; NoteTagContent.Malformed(read.reason) }
    }

    /** `format(null)`: NDEF-capable, empty, unlocked; the link is planned and written on the next tap (R1, R3). */
    private suspend fun format(tag: TagHandle) {
        when (val r = withContext(ioDispatcher) { tagIo.format(tag) }) {
            WriteResult.Formatted -> _state.value = WriteState.Waiting("Formatted the tag. Hold it to the phone again to write the link.")
            is WriteResult.Failed -> { r.cause?.let { Log.w(TAG, "format failed: ${r.reason}", it) }; _state.value = WriteState.Error("Could not format the tag. Hold it still and try again.") }
            WriteResult.Unsupported -> _state.value = WriteState.Error("This tag type is not supported.")
            is WriteResult.Written, is WriteResult.TooSmall, WriteResult.ReadOnly, is WriteResult.VerifyMismatch ->
                _state.value = WriteState.Error("Could not format the tag. Hold it still and try again.")
        }
    }

    /** The user pressed Write / Write over it: remember it for the next tap of the same tag (the handle went stale under the sheet). */
    fun confirm() {
        pending = pending?.let { Pending(it.plan, it.existing, consented = true) }
        _state.value = WriteState.Waiting("Hold the same tag to the phone again to write it.")
    }
    fun cancel() { val p = pending; pending = null; p?.let { cleanupScope.launch { forget(it.plan) } }; _state.value = WriteState.Waiting("Cancelled. Hold a tag to the phone to try again.") }

    /**
     * The LOCAL_REF sequence (target §4.9): persist first, UNCONFIRMED (writtenAt = null); confirm
     * only on a verified Written; retain — still unconfirmed, still resolvable — when the write may
     * have landed; remove only when the library says nothing reached the tag (`attempted == false`,
     * or a pre-write refusal).
     */
    private suspend fun write(tag: TagHandle, plan: WritePlan) {
        _state.value = WriteState.Writing
        val entry = entryFor(plan)                                       // writtenAt == null for every plan
        if (plan is WritePlan.DeviceBound) {
            try { store.put(entry) }                                     // (a) durably stored BEFORE the write
            catch (t: CancellationException) { throw t }
            catch (t: Throwable) { Log.w(TAG, "store.put failed", t); _state.value = WriteState.Error("Could not save the link on this phone; nothing was written to the tag."); return }
        }
        when (val r = withContext(ioDispatcher) { tagIo.write(tag, plan.records, lock = false) }) {
            // A Written is verified by construction (there is no "written but unverified" success).
            is WriteResult.Written -> {
                val at = clock()
                withContext(NonCancellable) {
                    if (plan is WritePlan.DeviceBound) runCatching { store.confirm(entry.uuid, at) }   // the read-back is the proof
                    else runCatching { store.put(entry.copy(writtenAt = at)) }                          // convenience only: never load-bearing
                }
                done = true
                _state.value = WriteState.Written(entry.copy(writtenAt = at), deviceBound = plan is WritePlan.DeviceBound)
            }
            // pre-write refusals: no bytes can have reached the tag, so the mapping may go
            is WriteResult.TooSmall -> { forget(plan); _state.value = WriteState.Error("This tag is too small: it holds ${r.maxSize} bytes and this needs ${r.needed}.") }
            WriteResult.ReadOnly -> { forget(plan); _state.value = WriteState.Error("This tag is read-only.") }
            WriteResult.Unsupported -> { forget(plan); _state.value = WriteState.Error("This tag type is not supported.") }
            WriteResult.Formatted -> { forget(plan); _state.value = WriteState.Error("Could not write the tag. Hold it still and try again.") }
            // ambiguous: the write may have landed. RETAIN (rule b).
            is WriteResult.VerifyMismatch -> _state.value = WriteState.Error("The tag did not read back what was written. Try again with the same tag.")
            is WriteResult.Failed -> {
                r.cause?.let { Log.w(TAG, "write failed: ${r.reason}", it) }
                if (!r.attempted) {                                       // the library refused before any I/O: nothing changed
                    forget(plan)
                    _state.value = WriteState.Error("Nothing was written (${r.reason}). Hold the tag still and try again.")
                } else {                                                  // the radio was reached: retain, unconfirmed
                    _state.value = WriteState.Error("Writing may not have finished (${r.reason}). If the tag was touched it may already hold the link; hold the same tag again.")
                }
            }
        }
    }

    /** Leaving the screen before any write: a pending LOCAL_REF mapping may go, on [cleanupScope]. */
    fun abandon(): Job? { val p = pending; pending = null; return p?.let { cleanupScope.launch { forget(it.plan) } } }

    private suspend fun forget(plan: WritePlan) {
        if (plan is WritePlan.DeviceBound) withContext(NonCancellable) {
            runCatching { store.remove(plan.content.uuid.toString()) }
        }
    }

    private fun contentOf(plan: WritePlan): NoteTagContent.Writable = when (plan) {
        is WritePlan.Compact -> plan.content; is WritePlan.FullUri -> plan.content
        is WritePlan.DeviceBound -> plan.content; is WritePlan.Refused -> error("refused plans are not written")
    }

    /** Never confirmed here: [writtenAt] stays null until a verified read-back. */
    private fun entryFor(plan: WritePlan): TagEntry = when (plan) {
        is WritePlan.Compact -> TagEntry(newUuid().toString(), "JOPLIN_NOTE", plan.content.id, null, writtenAt = null)
        is WritePlan.FullUri -> TagEntry(newUuid().toString(), "URI", plan.content.uri, null, writtenAt = null)
        is WritePlan.DeviceBound -> TagEntry(plan.content.uuid.toString(), "LOCAL_REF", plan.target, plan.target, writtenAt = null)
        is WritePlan.Refused -> error("refused plans are not written")
    }

    private companion object { const val TAG = "NoteTagWriteController" }
}
```

`UNMEASURED` is gone: a formatable tag never reaches the planner. The Phase E residual R1 is closed by construction (the Format branch returns before `WritePlanner.plan`, so `newUuid` cannot run); R2 by the two-tap test; R3 by the G-5 sentence; R4 by the three `Log.w` sites and the cancellation rethrow.

- [ ] **Step 4: Graph, trampoline, screens, fakes** — `NoteTagApp.kt`: `val tagIo: TagIo = RealTagIo`, `TagIdentity` from the library, the controller factory unchanged; `NfcDispatchActivity.kt`: `import com.loosecannon.nfc.tagcore.android.ndefRecords` (it must still never call `nfcTag()` — the existing grep stays 0); `WriteScreen.kt`: `NfcReaderModeSession`/`NfcTagHandle` from the library; both `FakeTagIo`s implement the library seam (the `androidTest` one stays a deliberate duplicate); `MainViewModelTest`, `WriteScreenDeviceBoundTest`, `AppSmokeTest`, `NdefSizeDeviceTest` (G-7: `NdefSize` and `serialisedSize()` from the library; the 49-byte assertion unchanged), `TagIdentityDispatchTest`, `AmbientDispatchDeviceProofTest`: imports.

- [ ] **Step 5: Delete, grep** — `git rm` the four adapter files; `git grep -n 'Interim copy'` → 0; `git grep -nE 'class (NdefBridge|TagWriter|TagInspection|NfcReaderModeSession|RealTagIo)|interface (TagIo|TagHandle)' -- 'app/**' 'core/**'` → 0; `git grep -n 'nfcTag()' -- 'app/src/main/**'` → 0; `git grep -n 'UNMEASURED' -- 'app/**'` → 0.

- [ ] **Step 6: Gate and the emulator** — the per-task gate green; XML counts recorded (`:core`, `:app` unit, `NoteTagWriteControllerTest`'s count); then `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --console=plain` → the Phase E device suites green on the library-backed build (Phase E's was 19; record the total and any change with its reason; `WriteScreenDeviceBoundTest` still shows the device-bound sentence before the write and "Written · This phone only" after).

- [ ] **Step 7: Commit** — `git add -A && git commit -m "app: the writer rides the shared seam; a format tap plans nothing, unreadable asks, attempted decides"`.

---

### Task 8: NoteTag's CI file, the pin script, the four negative tests, the local clean clone

Identical to Task 4 in the NoteTag repository: `tools/check-submodule-pin.sh` (the same script, verbatim), `.github/workflows/ci.yml` (the same file — the task list already names both apps' module set: `:nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug`), the four negative tests demonstrated and restored, the local clean clone of `~/Documents/Projects/AndroidStudioProjects/NoteTag` with `--recurse-submodules` and `--no-build-cache` green. NoteTag's CI has **never run** (runbook §A.2 row 3) and still does not run here; it runs from scratch at §B.4.

- [ ] Commit: `git add -A && git commit -m "ci: checkout the submodule, assert the pin, run the library suites too"`.

---

## Part C — both on one tag, the release pipeline, the design, the evidence

### Task 9 (§A.4 verify 2): both apps on the same exact tag, and the device proofs of D and E repeated on the library-backed builds

- [ ] **Step 1: One tag, two apps** — in each repository: `bash tools/check-submodule-pin.sh` → `submodule pin ok: nfc-tag-core-v0.1.0`; `git ls-tree HEAD libs/nfc-tag-core | awk '{print $3}'` → the same 40-hex sha in both, and it is `7e0377a…`; the catalog diff empty in both.
- [ ] **Step 2: Device proofs** — if Tasks 3 and 7 already ran the connected suites at their final commits, cite those XMLs; otherwise run them now, pinned to `emulator-5554`. Both totals go into the evidence with the Phase D/E baselines beside them.
- [ ] **Step 3: The library's own emulator suite under each app build** (optional, one line each): `ANDROID_SERIAL=emulator-5554 ./gradlew :nfc-android:connectedDebugAndroidTest --console=plain` from each app root → 10 tests; proves the library's test APK builds under the app's catalog.
- No commit (a proof task); its results are Task 12's.

---

### Task 10 (owner ruling 2026-09-17, tail of Phase G): the two-workflow release pipeline in both apps, authored and dry-run locally, never triggered

**Files (each app):**
- Create: `.github/workflows/release.yml`, `tools/release-dry-run.sh`
- Modify: `README.md` (one paragraph: how a release is cut; the secret names; the fail-closed rules)

**Trust model (binding):** `ci.yml` (Task 4/8) is the ordinary, unprivileged pipeline. `release.yml` runs **only** on the product tag namespace, under the `release` environment, and fails closed on: a tag that is not on the pushed commit's ancestry, a submodule not at an exact `nfc-tag-core-v*` tag, a failing test gate, a tag version that differs from the built `versionName` (G-4), missing signing material, an APK whose certificate fingerprint differs from `RELEASE_CERT_SHA256`, or an unsigned APK. It never falls back to an unsigned APK, never echoes a secret, and creates the GitHub Release only after every check.

- [ ] **Step 1: `release.yml`** — ServiceTag's; NoteTag's differs in the four marked lines:

```yaml
name: release
'on':                                       # quoted: PyYAML's YAML-1.1 loader reads a bare `on` as the boolean True (owner correction 4)
  push:
    tags:
      - 'servicetag-v*'                     # NoteTag: 'notetag-v*'
permissions:
  contents: write
jobs:
  release:
    runs-on: ubuntu-latest
    environment: release
    env:
      APP_DIR: servicetag                   # NoteTag: notenfc  (the historical directory the build script reads)
      APK_BASENAME: ServiceTag              # NoteTag: NoteTag
    steps:
      - uses: actions/checkout@11d5960a326750d5838078e36cf38b85af677262 # v4.4.0
        with:
          submodules: recursive
          fetch-depth: 0
      - name: the tag is on this commit, and the shared library is pinned at an exact tag
        run: |
          set -euo pipefail
          [ "${GITHUB_REF_TYPE}" = "tag" ] || { echo "not a tag push"; exit 1; }
          git tag --points-at HEAD --format='%(refname:short)' | grep -Fxq -- "$GITHUB_REF_NAME" || { echo "pushed tag is not exactly at HEAD"; exit 1; }
          bash tools/check-submodule-pin.sh
      - uses: actions/setup-java@cf277c60eb25467037889841efdb72551f06f6c3 # v4.9.1
        with:
          distribution: temurin
          java-version: '17'
      - uses: android-actions/setup-android@9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407 # v3.2.2
        with:
          packages: platform-tools build-tools;36.0.0
          accept-android-sdk-licenses: true
      - uses: gradle/actions/setup-gradle@ed408507eac070d1f99cc633dbcf757c94c7933a # v4.4.3
      - name: the complete test gate
        run: ./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest --console=plain
      - name: restore the release signing material (never echoed)
        env:
          RELEASE_KEYSTORE_BASE64: ${{ secrets.RELEASE_KEYSTORE_BASE64 }}
          RELEASE_STORE_PASSWORD: ${{ secrets.RELEASE_STORE_PASSWORD }}
          RELEASE_KEY_ALIAS: ${{ secrets.RELEASE_KEY_ALIAS }}
          RELEASE_KEY_PASSWORD: ${{ secrets.RELEASE_KEY_PASSWORD }}
        run: |
          set -euo pipefail
          for v in RELEASE_KEYSTORE_BASE64 RELEASE_STORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD; do
            [ -n "${!v:-}" ] || { echo "release secret $v is missing; refusing to build a release"; exit 1; }
          done
          umask 077                                   # BEFORE any key file exists (owner correction 3)
          d="$HOME/.config/$APP_DIR"; mkdir -p "$d"
          printf '%s' "$RELEASE_KEYSTORE_BASE64" | base64 -d > "$d/release.jks"
          printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=%s\nkeyPassword=%s\n' "$d/release.jks" "$RELEASE_STORE_PASSWORD" "$RELEASE_KEY_ALIAS" "$RELEASE_KEY_PASSWORD" > "$d/keystore.properties"
          chmod 700 "$d"
          chmod 600 "$d/release.jks" "$d/keystore.properties"
      - name: build the signed release
        run: ./gradlew :app:assembleRelease --console=plain
      - name: the APK is signed by the expected certificate, and its version is the tag's
        env:
          RELEASE_CERT_SHA256: ${{ vars.RELEASE_CERT_SHA256 }}
        run: |
          set -euo pipefail
          apk=app/build/outputs/apk/release/app-release.apk
          [ -f "$apk" ] || { echo "no release APK was produced (unsigned builds are named differently and are refused)"; exit 1; }
          bt="$ANDROID_HOME/build-tools/36.0.0"
          "$bt/apksigner" verify --print-certs "$apk" > certs.txt
          actual=$(grep -m1 'SHA-256 digest' certs.txt | awk '{print $NF}' | tr -d ':' | tr 'a-f' 'A-F')
          expected=$(printf '%s' "${RELEASE_CERT_SHA256:-}" | tr -d ':' | tr 'a-f' 'A-F')
          [ -n "$expected" ] || { echo "RELEASE_CERT_SHA256 is not set; refusing to publish"; exit 1; }
          [ "$actual" = "$expected" ] || { echo "certificate fingerprint differs from RELEASE_CERT_SHA256; refusing to publish"; exit 1; }
          built=$("$bt/aapt2" dump badging "$apk" | grep -o "versionName='[^']*'" | cut -d"'" -f2)
          want="${GITHUB_REF_NAME#*-v}"
          [ "$built" = "$want" ] || { echo "tag says $want but the APK says versionName $built; refusing to publish"; exit 1; }
          out="${APK_BASENAME}-${built}.apk"; cp "$apk" "$out"; sha256sum "$out" > "$out.sha256"
          echo "OUT=$out" >> "$GITHUB_ENV"
      - name: create the GitHub Release for this exact tag
        env:
          GH_TOKEN: ${{ github.token }}
        run: gh release create "$GITHUB_REF_NAME" "$OUT" "$OUT.sha256" --title "$GITHUB_REF_NAME" --notes "Signed release build of $GITHUB_REF_NAME. Verify with sha256sum -c $OUT.sha256; the signing certificate's SHA-256 fingerprint is the repository variable RELEASE_CERT_SHA256."
      - name: remove the signing material
        if: always()
        run: rm -rf "$HOME/.config/$APP_DIR"
```

Owner rulings 2026-09-17, applied to the block above and to ServiceTag's committed file: every external `uses:` is pinned to its full commit SHA with the version as a comment (checkout 11d5960a… v4.4.0, setup-java cf277c60… v4.9.1, setup-android 9fc6c4e9… v3.2.2, setup-gradle ed408507… v4.4.3 — resolved from the action repositories' tags on 2026-09-17; ordinary `ci.yml` is not pinned), and the tag-at-HEAD check is `git tag --points-at HEAD --format='%(refname:short)' | grep -Fxq -- "$GITHUB_REF_NAME"`, which handles a doubly-tagged commit and never treats the ref name as a pattern. The NoteTag copy carries both.

Notes the implementer must keep: `apksigner verify` exits non-zero for an unsigned or badly signed APK, so "unsigned" cannot pass; the fingerprint compare prints neither value; `grep -c 'secrets\.' .github/workflows/ci.yml` must stay 0 while `release.yml` is the only file that names them; `gh` is preinstalled on `ubuntu-latest`.

- [ ] **Step 2: `tools/release-dry-run.sh`** — the same checks against the **local** signing material (no secrets, no network, no Release): assert the submodule pin; run the test gate; `./gradlew :app:assembleRelease`; if `app/build/outputs/apk/release/app-release.apk` is absent, print `BLOCKED: no signing material (target §8)` and exit 3 (**ServiceTag's expected result until the owner's keytool step**); otherwise `apksigner verify --print-certs` into a scratch file and compare the certificate's SHA-256 with an expected value taken, in this order, from `${RELEASE_CERT_SHA256:-}` if set, else from `~/.config/<dir>/release-cert-sha256.txt` if the owner has placed one there, else no compare — printing **only** `fingerprint compare: matches` / `differs` / `skipped (no expected value configured)`, never a fingerprint (the keystore password is never put on a command line, so `keytool -list` is not used here); then `aapt2 dump badging` versionName against the `versionName` line of `app/build.gradle.kts`; `sha256sum` of the renamed APK into the scratch directory; the scratch directory deleted on exit via `trap`. **The verdict line is honest about what was checked** (owner, 2026-09-17): `RELEASE DRY RUN: PASS` only when the fingerprint compare ran and matched; `RELEASE DRY RUN: PARTIAL — signing identity not independently checked` when it was skipped (exit 0 still, because nothing failed; the real `release.yml` is the authoritative fail-closed identity gate once K/L provisions `RELEASE_CERT_SHA256`); exit 1 = a check failed or the fingerprint differs; exit 3 = BLOCKED (no signing material).

- [ ] **Step 3: Run it** — NoteTag: exit 0 with `RELEASE DRY RUN: PARTIAL — signing identity not independently checked` (no expected fingerprint is configured in Phase G; recorded as PARTIAL, never as PASS) and `version: 2.0 matches`, the checksum file produced then removed. ServiceTag: exit 3 `BLOCKED: no signing material (target §8)` — expected; recorded as such. `python3 -c 'import yaml; d=yaml.safe_load(open(".github/workflows/release.yml")); assert "on" in d and True not in d; assert list(d["on"]) == ["push"] and list(d["on"]["push"]) == ["tags"]; print(d["on"]["push"]["tags"])'` prints the one tag glob in both apps — the key is the **string** `on` (quoted in the file), never the boolean `True`, and the trigger is `push.tags` and nothing else. `ci.yml` keeps its bare `on:` (GitHub reads both); only the file the assertion inspects is quoted.

- [ ] **Step 4: README paragraph** (each app) — "Releases": a release is a tag `servicetag-v<versionName>` (NoteTag: `notetag-v<versionName>`) pushed to GitHub; the `release` workflow builds, tests, signs from the `release` environment's secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`), verifies the certificate against the public variable `RELEASE_CERT_SHA256` and the version against the tag, and publishes the signed APK with its SHA-256; ordinary CI never sees the signing material; the local equivalent is `tools/release-dry-run.sh`.

- [ ] **Step 5: Commit, each app** — `git add -A && git commit -m "release: a tag-only workflow that signs, verifies and publishes, and its local dry run"`.

---

### Task 11 (controller, docs commit on `product-split`): the design and the runbook say the two-workflow model

The controller applies these with a script that asserts each anchor matches once (as in Phase F):

- **Target §8**, the "In CI" row: "nothing. Release signing is entirely local; CI builds `assembleDebug` only and interpolates no `secrets.*`" → "**ordinary CI: nothing** — `ci.yml` builds `assembleDebug` only and interpolates no `secrets.*`. **Release: the tag-only `release.yml`** (owner ruling 2026-09-17) restores the keystore and its four values from the `release` environment's encrypted secrets, builds `assembleRelease`, verifies the APK's certificate against the public repository variable `RELEASE_CERT_SHA256` and its `versionName` against the tag, and publishes the signed APK with its SHA-256 as a GitHub Release. The GitHub copy of the key does not replace the owner's offline encrypted backup." Both columns.
- **Target §9**: the ServiceTag and NoteTag rows gain "and a second workflow, `release.yml`, on `servicetag-v*` / `notetag-v*` only (§8)"; the "No `secrets.*` anywhere" sentence becomes "No `secrets.*` in `ci.yml`; only `release.yml`, on a product tag, in the `release` environment"; the caveat paragraph gains "the first real `release.yml` executions happen only at the final product tags, after the phone and physical gates". The **nfc-tag-core** row is unchanged (no binary release; the tag is the release).
- **Target §6.3**: one sentence after the table: "The consumers' `release.yml` repeats the pin assertion before it signs anything."
- **Target §10.3** unchanged.
- **Runbook front matter**: after "K/L produce the first green CI…" add "K/L exercise ordinary CI only; the release workflows are installed at the tail of G and first execute at §G's product tags."
- **Runbook §B.3a verify 2 and §B.4 verify**: "green on the new workflow" → "green on the new `ci.yml`; `release.yml` is present and has not run (no product tag exists yet)"; **§B.6**: add "the `release` environment exists in both app repositories with its four secrets and the `RELEASE_CERT_SHA256` variable set (owner manual step; values never printed)".
- **Runbook §G (§28)**: "no published GitHub releases/APKs" → the two-workflow model: the final product tags `servicetag-v2.5` and **`notetag-v2.0`** (G-4) trigger `release.yml`, which publishes the signed APK and its checksum as the GitHub Release; **every** occurrence of `notetag-v1.0` in the target and the runbook (the §G tag table, §J gate rows, any prose) becomes `notetag-v2.0`, verified by `git grep -c 'notetag-v1.0' docs/` = 0 after the commit; **runbook §J gate 10** gains "the two `release.yml` workflows installed and dry-run locally (not executed)".
- **Runbook §A.4**: a closing note that Phase G ended with the workflows installed and the §A.4 verifications done, and that `:core` depends on `:nfc-core` (G-1).

- [ ] Commit: `design: the apps get a tag-only signed release workflow; ordinary ci stays unprivileged`. Owner copies refreshed.

---

### Task 12: the whole-phase proof and the Phase G evidence section

- [ ] **Step 0:** FINALs fixed: ServiceTag `product-split` HEAD and NoteTag `master` HEAD after Tasks 10/11; the library untouched at `7e0377a`.
- [ ] **Step 1:** in each app: clean tree; `bash tools/check-submodule-pin.sh`; the per-task gate; `bash tools/release-dry-run.sh` (NoteTag 0 = PARTIAL, recorded as such; ServiceTag 3 = BLOCKED, expected); the constraint greps of Tasks 3/7; the local clean clone once more with `--no-build-cache`.
- [ ] **Step 2:** `## Phase G — both apps consume nfc-tag-core (§A.4)` appended to `docs/architecture/product-split-evidence.md` after the Phase F section, mirroring the earlier sections: the two FINALs; the tag and gitlink sha in both; what was deleted (the file lists) and what replaced it; the four residual dispositions with their test names; the suite counts per app (JVM and connected, with the D/E baselines); the four negative tests' transcripts (one line each); the clean-clone lines; the workflows (installed, YAML-validated, dry-run results, never triggered); the G-1…G-7 rulings as ruled; the amendments commit; what did not happen (no push, no tag, no remote on NoteTag, no phone, no NFC; the ServiceTag key still the owner's step); the closing line **verbatim**: **Phase G local consumption complete; both apps green on `nfc-tag-core-v0.1.0`; K/L pending owner authorization.**
- [ ] Commit on `product-split`: `evidence: phase g, two apps on one tag`.

---

## What K/L will need from this phase (recorded so nobody looks for it here)

The rename `noteNFC → ServiceTag` (§B.2), the `--no-ff` merge of `product-split` into `master` and its push (§B.3a), NoteTag's `origin` and first push (§B.4), the issue moves (§B.5), and the three URL clean clones plus the second-workstation proof (§B.6) are all **owner-authorized steps after this phase**. Before §B.3a/§B.4 the owner provisions the `release` environment secrets and `RELEASE_CERT_SHA256` in each repository (manual task 2); before the first ServiceTag release, the ServiceTag key (manual task 1). The first `release.yml` executions are the final product tags after the phone and physical gates.

## Review status

- 2026-09-17: plan written (`5f4dac4`) → owner **HOLD with corrections**: G-1…G-7 accepted (G-2, G-6 as amended); correction 1 — ServiceTag's `confirmOverwrite` records consent only, never tag I/O through the sheet's stale handle, and the next fresh tap consumes consent only when the content still matches (three tests); correction 2 — the row is provisioned on the first writable tap, never on a format-only tap (prose and test); correction 3 — `umask 077` before any key file is created, and G-2's prose says missing secrets fail before the build; correction 4 — `release.yml` quotes `'on'` and the YAML assertion checks the string key; the dry run reports PARTIAL, never PASS, when the fingerprint compare is skipped → corrections applied in this revision → scoped review of exactly those items → scoped review (opus, `832d8be`): all seven checks PASS, internally consistent; four prose lags fixed by the controller (the hygiene word list, R1's proof cell, the sentence-exception clause, the `FakeHandle(uid)` fixture) → **Phase G plan RELEASED. Begin Task 1 under the existing implement → independent review → close discipline.** All prohibitions stand: no app push, no app tag, no rename, no secret provisioning, no phone, no physical NFC.
