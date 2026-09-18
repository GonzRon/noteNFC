# ServiceTag 2.8 — An Empty Phone Is Only Asked to Confirm, and an Inspect Inspects Implementation Plan

> **Released as ServiceTag 2.7.1, not 2.8** (owner ruling 2026-09-18, before publication): the repository adopted semantic `MAJOR.MINOR.PATCH` versioning, #40 and #41 are corrective work against existing behaviour, so the pending release became `2.7.1` with `versionCode = 10`. Every "2.8" below is this plan's original name for that release; the code and the runbook say 2.7.1. Policy: `docs/versioning.md`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two owner-approved changes, released together. **#40** — the Backup screen stops demanding the typed `REPLACE` word on a phone that has nothing to replace: a new `:core` use case answers "does this install hold any records at all?" over the five repositories that carry them, and a picked data archive gets either the existing typed dialog (any row exists) or a plain confirm (the store is empty). **#41** — Settings → Utilities → Read / inspect tag stops auto-opening a tag that is already bound to an asset: in inspect mode the sheet *names* the asset and offers **Open asset**, so a deliberate look is a look, the inspect screen stays on top, and the activity's one reader-mode session stays held until the owner leaves. The ambient one-tap path — tap a tag anywhere and the phone opens the right place — is untouched by construction. Ship it as `versionCode` 10 / `versionName` 2.8.

**Architecture:** Four tasks. **Task 1 (#40)** adds `core/usecase/StoreIsEmpty.kt` over the five existing repository ports (`assets`, `tags`, `events`, `attachments`, `links`) — no port gains a member, because each of the five already has the query needed — wires it once in `AppGraph`, injects it into `BackupViewModel`, and branches the Backup screen's confirmation on it. **Task 2 (#41)** adds one defaulted parameter, `inspecting: Boolean = false`, to `TagResultSheet`; `ScanScreen` passes `true`, every other caller keeps the default, and in inspect mode the `OpensAsset` branch drops its `LaunchedEffect` auto-navigation and grows two actions. **Task 3** is the version bump, two README clauses and the runbook's physical row **R2**. **Task 4** is the controller-run proof set. Tasks 1 and 2 are independent: different modules, different files, no shared type, and each compiles and gates on its own.

**Tech Stack:** the estate's pins — AGP 9.4.0, Kotlin 2.4.20, Gradle 9.7.1, JDK 17; `nfc-tag-core` at `nfc-tag-core-v0.1.0` (`7e0377a`) as a pinned submodule under `libs/`, **untouched**; Room 3 with `AppDatabase` at schema version **5, unchanged**; Compose + Navigation 3; JUnit 5 + `kotlin.test` in `:core` (`useJUnitPlatform()`, `core/build.gradle.kts:29`), JUnit 4 + Compose test in `:app`; `apksigner`/`aapt2` from build-tools 36.0.0 for `tools/release-dry-run.sh`.

**Spec:** GitHub issues [#40](https://github.com/GonzRon/ServiceTag/issues/40) and [#41](https://github.com/GonzRon/ServiceTag/issues/41), and the owner's ratified wording quoted verbatim in Global Constraints below. #41's field observation is recorded at `docs/architecture/product-split-evidence.md:1096` (physical row R1's incidental finding, 2026-09-18, on the 2.7 release). The previous release's plan — the house example for shape and depth — is `docs/superpowers/plans/2026-09-18-servicetag-2.7-dashboard-and-inspect.md`.

---

## The two issues, as scoped

**#40.** `BackupScreen` always raises the typed-`REPLACE` dialog after a data file is picked (`BackupScreen.kt:172`–`208`): title "Replace everything?", the body "Every asset, tag and link on this phone is deleted and replaced with what is in the file. This cannot be undone.", and a confirm button disabled until the owner types `REPLACE`. That word exists to make the owner spell out that they accept losing what is on the phone (R-9). On a phone with nothing on it the word authorises the loss of nothing, and the warning describes data that does not exist. Wanted: a plain confirm in that one case, and the typed dialog — unchanged — in every other.

**#41.** `TagResultSheet`'s `TagResult.OpensAsset` branch runs `LaunchedEffect(result) { onOpenAsset(result.asset.id.value) }` (`TagResultSheet.kt:102`) and draws "Opening asset…". That is right for the ambient trampoline, which exists to open the right place from one tap. It is wrong for Read / inspect tag, whose whole purpose is to look at a tag: the owner holds a bound tag to see what it is and the phone navigates away to the asset instead. The 2.7 physical run recorded exactly this, as R1's incidental finding. Worse, navigating away takes the inspect screen off the top of the back stack, and the hold on the activity's one reader-mode session is scoped to the routes that read tags (`Route.readsTags()`) — so the auto-open releases NFC with the tag still against the phone, which is the family of defect 2.7 spent a release removing. Wanted: in inspect mode the sheet names the asset and waits for a tap.

## Global Constraints

- **The six ratified strings of the release, verbatim and in full.** These are the only new user-visible strings 2.8 adds. Four are the plain restore dialog (#40); two are the inspect sheet (#41):
  - `Restore this backup?`
  - `This phone has no records yet, so there is nothing to replace.`
  - `Restore`
  - `Cancel`
  - `ServiceTag tag`
  - `Open asset`

  `Cancel` and `Restore` already exist elsewhere in the app as words on buttons; they are listed because the owner ratified them for these two dialogs, and they must appear exactly as written here. `ServiceTag tag` is an **eyebrow**, and `NfcSheet` renders an eyebrow through `eyebrow.uppercase()` (`TagResultSheet.kt:256`), so the pixels read `SERVICETAG TAG`; the source string is `ServiceTag tag` and nothing else.
- **Every other user-visible sentence stays byte-identical to `d69cf07`.** No existing string is edited, deleted or re-worded. In particular, unchanged and re-rendered from the same call sites: `Replace everything?`, `Every asset, tag and link on this phone is deleted and replaced with what is in the file. This cannot be undone.`, `Type REPLACE to confirm` (the `"Type $REPLACE_WORD to confirm"` label), `Replace`, `Backup`, `Last backup`, `Never`, `Export backup set`, `Restore data`, `Restore files`, `Restoring the data deletes everything on this phone first, then loads the file.`, `Tag detected`, `Opening asset…`, `Reading tag`, `Looking this tag up…`, `Unregistered tag`, `This tag is not assigned to anything yet.`, `Tag marked lost`, `Tag retired`, `This tag was taken out of service. Binding it again puts it back to work.`, `This ServiceTag tag is not in this phone's records.`, `Not a ServiceTag tag`, `This tag holds something else.`, `Bind to asset`, `Write a new tag over it`, `BIND THIS TAG TO`, `READY TO SCAN`, `READING TAG`, `Hold the top of your phone near the equipment tag.`, `Read / inspect tag`, `This tag points at a note link from before the product split. ServiceTag no longer opens links; NoteTag does.` (the string the `PRE_SPLIT_LINK_SENTENCE` constant holds, `ScanViewModels.kt:64`), and the three `Scanning needs the app's own window.` / `This phone has no NFC hardware.` / `NFC is turned off. Enable it in system settings, then come back.` lines.
- **`libs/` is untouched.** No file under `libs/nfc-tag-core` is modified or re-pinned; the gitlink stays at `7e0377a` (`git ls-tree HEAD libs/nfc-tag-core` → `160000 commit 7e0377ac99d7a4fee95ca6b88551daaa6330e52f`). `tools/check-submodule-pin.sh` must pass.
- **No schema and no backup-format bump.** `AppDatabase` stays at 5, `AppGraph.SCHEMA_VERSION` stays 5 (`AppGraph.kt:211`), `BackupManifest.formatVersion` stays 5, `app/schemas/` is not re-exported, no migration is added or edited. `StoreIsEmpty` only reads.
- **The 2.6 tombstones are untouched.** `LinkRepository` keeps exactly `upsert`, `get`, `all`, `deleteAll` (`core/ports/Repositories.kt:57`–`62`); `external_link` is still exported and restored byte-for-byte; nothing above the DAO gains a way to *display* a link. `StoreIsEmpty` calls `links.all()` and counts the rows — a tombstone row is a record this phone holds, so a phone carrying only tombstones is **not** empty.
- **`nfc/NfcDispatchActivity.kt` is untouched**, and so is `MainActivity.kt`, `AndroidManifest.xml` and the `Route.TagResult` entry in `ui/nav/ServiceTagRoot.kt`. The ambient path is the app's spine and no part of 2.8 may change it.
- **Version.** `app/build.gradle.kts`: `versionCode` 9 → **10**, `versionName` "2.7" → **"2.8"**. **Nothing else in that file changes** — in particular no dependency is added, so every connected test here works with what `app/build.gradle.kts:133`–`138` already declares (the Compose BOM `platform` plus five `androidTestImplementation` artifacts). `activity-compose`, `androidx.core:core-ktx` and `compose-ui-test-junit4` all reach androidTest through the tested variant, which is why the `ActivityResultRegistry` seam in Task 1 Step 8 needs nothing new.
- **Commits.** One commit per task, on `master`. Single casual subject line; **no body, no trailers, no attribution of any kind** — no `Co-Authored-By`, no `Generated-with`, no `Signed-off-by`. Author **GonzRon**, with the owner's own git identity, set on the command line — and read out of `master` rather than written down here, so no e-mail address is stored in this plan (every commit on `master` already carries exactly one, and `git log -1 --format='%ae' master` at `d69cf07` returns it):
  ```bash
  AUTHOR_EMAIL=$(git log -1 --format='%ae' master)
  git -c user.name=GonzRon -c user.email="$AUTHOR_EMAIL" commit -m "<subject>"
  ```
  Every commit step below repeats those two lines; `AUTHOR_EMAIL` is re-derived each time, because a task's shell does not outlive the task.
- **Device rule.** `ANDROID_SERIAL=emulator-5554` goes on the **same command line** as every `adb` and every Gradle device command. Instrumented suites wipe app data (`clearInstall()`, `AppSmokeTest.kt:56`), which is why. **The phone is never addressed**: it holds the owner's real records, `adb devices` is not consulted for it, and nothing in this change installs on, reads from or writes to it.
- **Test-fixture convention for Room-backed view-model tests.** Quoted exactly as the tree does it. `BackupViewModelTest.kt:63`–`69`:
  ```kotlin
  private val scheduler = TestCoroutineScheduler()
  private lateinit var graph: FakeGraph

  @Before fun setUp() {
      Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
      graph = FakeGraph(queryContext = StandardTestDispatcher(scheduler))
  }
  ```
  and the reason, from `TagWriteControllerTest.kt`'s KDoc (lines 39–42): *"One dispatcher carries the controller's scope, its io hop AND Room's query context, so provision, inspect, write and complete all land on the single test scheduler and `advanceUntilIdle()` is a real settle rather than a hope (`inMemoryDb()` puts Room on `Dispatchers.Default`, which would leave the row write racing the assertion)."* `TestDb.kt:9`–`15` says the same from the other side: *"A ViewModel or controller fixture that put a test dispatcher on `Dispatchers.Main` should pass a `StandardTestDispatcher` on that *same* `TestCoroutineScheduler` instead."* So: Main is `UnconfinedTestDispatcher(scheduler)`, Room is `StandardTestDispatcher(scheduler)` on that same scheduler, the graph is `FakeGraph(queryContext = …)`, and **no `runBlocking` in setup** — every suspending fixture call happens inside `runTest`.
- **No `TagIo` seam is added to `AppGraph`.** `ScanViewModel`'s primary constructor already takes one (`ScanViewModels.kt:87`–`92`); a connected test builds the model with its own `TagIo` and seeds it into the `ViewModelStore` under the key `ScanScreen` resolves with, exactly as `InspectBackDismissesTheAnswerTest` does. Production code gains no test-only parameter.
- **Hygiene.** No e-mail address, no absolute home path (write `~`), no device id other than `emulator-5554`, no tag UID, no note id in any file this change touches — source, test or document. Test fixtures use invented names (`Hot tub`, `Pool pump`, `Guide.pdf`) and the invented canonical UUID already used by `ResolveTagTest.kt:30` and `PreSplitLinkTagSheetTest.kt:35`, `123e4567-e89b-12d3-a456-426614174000`.
- **Per-task gate**, all four tasks: `./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain`. `:app` is never knowingly red between tasks.
- **The evidence file is not touched by any task.** `docs/architecture/product-split-evidence.md` is the controller's to write **after** Task 4's proofs. Two things about it are decided here so nobody guesses later: the 2.7 entry's known-limit note at line 1096 — *"a ServiceTag-bound tag scanned in Read / inspect tag auto-opens the asset — undesirable inspect-mode behaviour, filed as #41 for 2.8"* — is **history and is not rewritten**; 2.8's own evidence section adds a supersession note (*"resolved by #41 in 2.8"*) and nothing else about it.
- **Not in this change:** no `git push`, no tag, no `gh` mutation, no phone install, no physical NFC, no edit to `docs/architecture/product-split-evidence.md`, no edit under `docs/design/**` (history — never rewritten), no edit to `docs/architecture/product-split-target.md` (2.8 changes no identity, no filter and no division of labour), no schedules work (Phase 3), no change to the owner-action budget note at `docs/architecture/product-split-migration.md:977`–`985` (see Task 3 Step 4 for why the count is unchanged).

## File map

```
core/src/main/kotlin/com/loosecannon/servicetag/core/
  usecase/StoreIsEmpty.kt                NEW      class StoreIsEmpty, suspend fun run(): Boolean
  ports/Repositories.kt                  UNTOUCHED  every query it needs already exists
  model/**, backup/**, journal/**        UNTOUCHED

core/src/test/kotlin/com/loosecannon/servicetag/core/
  usecase/StoreIsEmptyTest.kt            NEW      6 cases over the five in-memory fakes
  testing/InMemoryRepositories.kt        UNTOUCHED  no fake gains a member

app/src/main/kotlin/com/loosecannon/servicetag/
  di/AppGraph.kt                         MODIFY   one import, one val: storeIsEmpty
  ui/backup/BackupViewModel.kt           MODIFY   ctor param, graph ctor arg, isStoreEmpty()
  ui/backup/BackupScreen.kt              MODIFY   the KDoc, emptyStore state, the pick, the branch,
                                                  RestoreEmptyStoreDialog
  ui/scan/TagResultSheet.kt              MODIFY   inspecting: Boolean = false; the OpensAsset branch
  ui/scan/ScanScreen.kt                  MODIFY   inspecting = true at the one call site
  ui/scan/{ScanViewModels,TagResultWire,TagWriteController,WriteTagScreen}.kt   UNTOUCHED
  ui/nav/{Route,ServiceTagRoot}.kt       UNTOUCHED  the ambient TagResult entry keeps the default
  ui/nfc/ReaderMode.kt                   UNTOUCHED
  MainActivity.kt, nfc/NfcDispatchActivity.kt, AndroidManifest.xml              UNTOUCHED

app/src/test/kotlin/com/loosecannon/servicetag/
  ui/backup/BackupViewModelTest.kt       MODIFY   fixture takes StoreIsEmpty; 2 new cases (20 → 22)
  testing/FakeGraph.kt                   UNTOUCHED  the fixture builds the use case inline
  ui/scan/*, backup/RestoreProofTest.kt  UNTOUCHED

app/src/androidTest/kotlin/com/loosecannon/servicetag/
  ui/backup/EmptyStoreRestorePromptTest.kt   NEW  the plain dialog, on the emulator
  ui/scan/InspectNamesABoundTagTest.kt       NEW  inspect names it; the default still auto-opens
  ui/scan/ReadScopedSheetOwnerTest.kt        MODIFY  one stale KDoc sentence (Task 2 Step 7)
  ui/scan/{InspectBackDismissesTheAnswer,PreSplitLinkTagSheet,ReaderModeHold}Test.kt  UNTOUCHED
  ui/AppSmokeTest.kt and every other class  UNTOUCHED

app/build.gradle.kts                          MODIFY  versionCode 10, versionName "2.8"
README.md                                     MODIFY  the inspect clause; the typed-confirm clause
docs/architecture/product-split-migration.md  MODIFY  §E row R2 only
libs/**, app/schemas/**, docs/design/**,
docs/architecture/product-split-evidence.md,
docs/architecture/product-split-target.md     UNTOUCHED
```

---

### Task 1 (#40): an empty phone is asked to confirm, not to type REPLACE

**Files:**
- Create: `core/src/main/kotlin/com/loosecannon/servicetag/core/usecase/StoreIsEmpty.kt`
- Create test: `core/src/test/kotlin/com/loosecannon/servicetag/core/usecase/StoreIsEmptyTest.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/di/AppGraph.kt` (import block; after line 157)
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/ui/backup/BackupViewModel.kt` (import block; lines 84–96; after 207)
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/ui/backup/BackupScreen.kt` (lines 62–66 KDoc, 80–81, 99–105, 172–209, and one new private composable)
- Modify test: `app/src/test/kotlin/com/loosecannon/servicetag/ui/backup/BackupViewModelTest.kt` (lines 76–79, plus 2 new cases)
- Create test: `app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/backup/EmptyStoreRestorePromptTest.kt`
- **Untouched, and checked at Step 11:** `core/src/main/kotlin/com/loosecannon/servicetag/core/ports/Repositories.kt`, `app/src/main/kotlin/com/loosecannon/servicetag/data/room/**`, `core/src/test/kotlin/com/loosecannon/servicetag/core/testing/InMemoryRepositories.kt`, `app/src/test/kotlin/com/loosecannon/servicetag/testing/FakeGraph.kt`, `app/schemas/**`, `libs/**`

**Interfaces:**
- Consumes (all present at `d69cf07`): `AssetRepository.all(): List<Asset>`, `TagRepository.all(): List<TagBinding>`, `EventRepository.all(): List<AssetEvent>`, `AttachmentRepository.count(): Int`, `LinkRepository.all(): List<ExternalLink>` — `core/ports/Repositories.kt` lines 24, 43, 88, 107 and 60 respectively. `AppGraph.assets/tags/events/attachments/links` (`AppGraph.kt:90`–`97`). `FakeGraph.assets/tags/events/attachments/links` (`FakeGraph.kt:87`–`93`). The five `InMemory*Repository` fakes in `core/src/test/.../testing/InMemoryRepositories.kt`.
- Produces (Tasks 3 and 4 rely on these names):
  - `class StoreIsEmpty(assets: AssetRepository, tags: TagRepository, events: EventRepository, attachments: AttachmentRepository, links: LinkRepository)` with `suspend fun run(): Boolean`, in package `com.loosecannon.servicetag.core.usecase`.
  - `AppGraph.storeIsEmpty: StoreIsEmpty` — the one construction site in production.
  - `BackupViewModel(exportBackupSet, importBackupReplace, restoreArtifacts, storeIsEmpty, storage, prefs, clock)` — the new parameter is **fourth**, and `BackupViewModel(graph: AppGraph)` is unchanged in signature.
  - `BackupViewModel.isStoreEmpty(): Boolean` — a `suspend fun`, deliberately **not** named `storeIsEmpty()`, so it cannot be confused with the property of that name it delegates to.
  - `private fun RestoreEmptyStoreDialog(onDismiss: () -> Unit, onRestore: () -> Unit)` — a `@Composable` in `BackupScreen.kt`, holding the four ratified #40 strings.

**Decisions, and what lost:**

1. **A `:core` use case, not a query on the screen or a helper on `AppGraph`.** "Is there anything here a restore would replace?" is a rule about the domain's records, it has an opinion about which tables count, and it is the kind of opinion that has to be pinned by a test with fakes. `:core` has no Android under it, so `StoreIsEmptyTest` is a JVM test over the existing in-memory fakes. A `BackupViewModel` private method lost: the view model would then own the list of tables that count and only a Room-backed `:app` test could reach it.
2. **Five repositories, and exactly five.** `assets`, `tags`, `events`, `attachments`, `links`. `definitions` and `profiles` are **deliberately absent, and that is a decision**: `MeasurementDefinition.assetId` and `EventProfile.assetId` are both non-null (`core/model/Journal.kt:10` and `:30`) and the schema's foreign key is what enforces it, so a definition or a profile cannot exist without the asset it names — `assets.all().isEmpty()` already answers for them. Adding them would read as thoroughness and would in fact be two more table scans that cannot change the answer.
3. **No port gains a member.** Each of the five already has the cheapest query it has: `AttachmentRepository.count(): Int` (`Repositories.kt:107`) for attachments, and `all()` for the other four, which is the only row-returning member `LinkRepository` has at all after 2.6 narrowed it. So no interface, no Room adapter, no fake and no `app/schemas/**` file is touched. A new `suspend fun any(): Boolean` on all five lost: it would mean editing an interface, seven Room repositories and the in-memory fakes to make an answer that is already reachable, on a call that happens once per file pick.
4. **`&&`, so the answer short-circuits.** The common case on a phone with records is one `SELECT` that comes back non-empty, and the other four never run. The order is cheapest-to-most-likely-populated: assets first, because a phone with anything on it has an asset.
5. **No `UnitOfWork.read` around it.** The question is asked once, immediately after the owner picked a file, on a store nothing else is writing to; there is no invariant spanning the five reads to protect, and a read transaction would force all five and defeat decision 4. Stated so the omission is legible as a choice rather than an oversight.
6. **The store is asked *before* the dialog is raised, not while it is showing.** The launcher callback suspends in `rememberCoroutineScope()` and only then sets `confirming`, so exactly one dialog is ever shown for one pick. Keying a `LaunchedEffect` off `confirming` lost: it would leave a frame or two in which neither dialog can honestly be drawn, or — worse, if defaulted the other way — would flash the typed dialog and replace it.
7. **The typed dialog's own code is not rewritten.** Every string, every parameter and the `enabled = typed == REPLACE_WORD` gate stay exactly as they are; the only change to it is that its `AlertDialog(` call now sits in the `else` arm of the new branch, so the block moves from 8 columns of indentation to 12. (#41's `else` arm, by contrast, does not move at all — see Task 2 Step 3.) The new dialog is a separate private composable because it closes over nothing, which keeps the `confirming?.let` block readable.
8. **`isStoreEmpty()` is a `suspend fun` on the view model, not a state flow.** A flow would keep five queries hot for the life of the Backup screen to answer a question that is asked at most once per visit, and the answer would still have to be read at the moment of the pick. There is no `withContext(Dispatchers.IO)` on it either: Room already runs its queries on the graph's query context (`AppGraph.kt:83`), which is what lets the JVM fixture put them on the shared test scheduler.

- [ ] **Step 1: Write the failing `:core` test first.**

Create `core/src/test/kotlin/com/loosecannon/servicetag/core/usecase/StoreIsEmptyTest.kt`:

```kotlin
package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.model.Asset
import com.loosecannon.servicetag.core.model.AssetEvent
import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.Attachment
import com.loosecannon.servicetag.core.model.AttachmentId
import com.loosecannon.servicetag.core.model.AttachmentKind
import com.loosecannon.servicetag.core.model.AttachmentOwner
import com.loosecannon.servicetag.core.model.EventId
import com.loosecannon.servicetag.core.model.EventKind
import com.loosecannon.servicetag.core.model.EventSource
import com.loosecannon.servicetag.core.model.ExternalLink
import com.loosecannon.servicetag.core.model.LinkId
import com.loosecannon.servicetag.core.model.LinkKind
import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagBinding
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.core.testing.InMemoryAssetRepository
import com.loosecannon.servicetag.core.testing.InMemoryAttachmentRepository
import com.loosecannon.servicetag.core.testing.InMemoryEventRepository
import com.loosecannon.servicetag.core.testing.InMemoryLinkRepository
import com.loosecannon.servicetag.core.testing.InMemoryTagRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 2.8 (#40) — "is there anything on this phone a restore would replace?"
 *
 * One case per kind of record, each on its own, because the question the Backup screen asks is
 * whether *anything at all* is here: a phone holding one tombstone link row and nothing else has
 * something to lose, and offering it a plain confirm would be a lie. The five kinds are the five
 * the use case reads; definitions and profiles are not among them because neither can exist
 * without the asset it names (`Journal.kt:10`, `Journal.kt:30`), so the asset check answers for
 * both.
 */
class StoreIsEmptyTest {

    private val assets = InMemoryAssetRepository()
    private val tags = InMemoryTagRepository()
    private val events = InMemoryEventRepository()
    private val attachments = InMemoryAttachmentRepository()
    private val links = InMemoryLinkRepository()
    private val storeIsEmpty = StoreIsEmpty(assets, tags, events, attachments, links)

    /** An invented canonical UUID, the same one `ResolveTagTest` uses. Never a real tag's id. */
    private val tagKey = "123e4567-e89b-12d3-a456-426614174000"

    private val asset = Asset(AssetId("a1"), "Hot tub", createdAt = 1L, updatedAt = 1L)

    private val tag = TagBinding(
        id = TagId(tagKey),
        payloadFormat = PayloadFormat.V1,
        payloadKey = tagKey,
        target = TagTarget.AssetTarget(AssetId("a1")),
        createdAt = 1L,
        updatedAt = 1L,
    )

    private val event = AssetEvent(
        id = EventId("e1"), assetId = AssetId("a1"), kind = EventKind.MAINTENANCE,
        title = "Filter change", profileId = null, occurredOn = "2026-09-18",
        occurredTime = null, tzId = "UTC", notes = "", source = EventSource.MANUAL,
        sourceRef = null, createdAt = 1L, updatedAt = 1L,
        measurements = emptyList(), consumables = emptyList(),
    )

    private val attachment = Attachment(
        id = AttachmentId("att-1"),
        owner = AttachmentOwner.OfAsset(AssetId("a1")),
        kind = AttachmentKind.DOCUMENT,
        displayName = "Guide.pdf",
        mimeType = "application/pdf",
        sizeBytes = 8L,
        sha256 = "0".repeat(64),
        storageLocator = "assets/a1/att-1.pdf",
        capturedOn = null,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private val link = ExternalLink(
        id = LinkId("l1"),
        kind = LinkKind.JOPLIN,
        label = "note",
        uri = "joplin://x",
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test fun anUntouchedInstallIsEmpty() = runTest {
        assertTrue(storeIsEmpty.run())
    }

    @Test fun oneAssetIsEnoughToMakeItNotEmpty() = runTest {
        assets.upsert(asset)
        assertFalse(storeIsEmpty.run())
    }

    @Test fun oneTagRowIsEnoughToMakeItNotEmpty() = runTest {
        tags.upsert(tag)
        assertFalse(storeIsEmpty.run())
    }

    @Test fun oneEventIsEnoughToMakeItNotEmpty() = runTest {
        events.upsert(event)
        assertFalse(storeIsEmpty.run())
    }

    @Test fun oneAttachmentRowIsEnoughToMakeItNotEmpty() = runTest {
        attachments.upsert(attachment)
        assertFalse(storeIsEmpty.run())
    }

    /** 2.6's tombstone: nothing displays it, and it is still a record this phone holds. */
    @Test fun oneTombstoneLinkRowIsEnoughToMakeItNotEmpty() = runTest {
        links.upsert(link)
        assertFalse(storeIsEmpty.run())
    }
}
```

- [ ] **Step 2: Run it and watch it fail to compile.**

Run: `./gradlew :core:test --tests '*StoreIsEmptyTest' --console=plain`
Expected: **FAILURE** — `Unresolved reference: StoreIsEmpty`. That is the red.

- [ ] **Step 3: Write the use case.**

Create `core/src/main/kotlin/com/loosecannon/servicetag/core/usecase/StoreIsEmpty.kt`:

```kotlin
package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.ports.AssetRepository
import com.loosecannon.servicetag.core.ports.AttachmentRepository
import com.loosecannon.servicetag.core.ports.EventRepository
import com.loosecannon.servicetag.core.ports.LinkRepository
import com.loosecannon.servicetag.core.ports.TagRepository

/**
 * Whether this phone holds any records at all (2.8, issue #40).
 *
 * The one caller is the Backup screen, deciding which confirmation a picked data archive gets. The
 * typed `REPLACE` word exists to make the owner spell out that they accept losing what is here
 * (R-9); on a phone with nothing here it authorises the loss of nothing and warns about data that
 * does not exist. So "empty" has to mean *nothing at all*, and a single row of any kind is enough
 * to make the answer false.
 *
 * **Five kinds, and exactly five.** Assets, tag bindings, journal events, attachment rows, and the
 * 2.6 tombstone link rows — the last because nothing in ServiceTag displays a link any more, and a
 * row nobody can see is still a record a restore would delete. `MeasurementDefinition` and
 * `EventProfile` are deliberately not read: both carry a non-null `assetId` and the schema's
 * foreign key enforces it, so neither can exist without the asset it names and [assets] already
 * answers for them.
 *
 * **Cheapest query each, and short-circuiting.** [AttachmentRepository.count] is a count; the other
 * four ports expose no count at all, so `all()` it is — and for [LinkRepository], narrowed to four
 * members in 2.6, `all()` is the only row-returning member there is. The `&&` chain means the usual
 * answer on a populated phone is one query that comes back non-empty and four that never run.
 *
 * There is no read transaction: the question is asked once, on a store nothing else is writing to,
 * and no invariant spans the five reads. A transaction would force all five and buy nothing.
 */
class StoreIsEmpty(
    private val assets: AssetRepository,
    private val tags: TagRepository,
    private val events: EventRepository,
    private val attachments: AttachmentRepository,
    private val links: LinkRepository,
) {
    suspend fun run(): Boolean =
        assets.all().isEmpty() &&
            tags.all().isEmpty() &&
            events.all().isEmpty() &&
            attachments.count() == 0 &&
            links.all().isEmpty()
}
```

- [ ] **Step 4: Run the `:core` test and watch it pass.**

Run: `./gradlew :core:test --tests '*StoreIsEmptyTest' --console=plain`
Expected: `BUILD SUCCESSFUL`, 6 cases passing. `core/build/reports/tests/test/classes/com.loosecannon.servicetag.core.usecase.StoreIsEmptyTest.html` lists `anUntouchedInstallIsEmpty`, `oneAssetIsEnoughToMakeItNotEmpty`, `oneTagRowIsEnoughToMakeItNotEmpty`, `oneEventIsEnoughToMakeItNotEmpty`, `oneAttachmentRowIsEnoughToMakeItNotEmpty`, `oneTombstoneLinkRowIsEnoughToMakeItNotEmpty`.

- [ ] **Step 5: Write the failing view-model test.**

In `app/src/test/kotlin/com/loosecannon/servicetag/ui/backup/BackupViewModelTest.kt`, add one import to the existing block. Insert it **after line 15** (`import com.loosecannon.servicetag.core.usecase.AttachmentResult`) and **before line 16** (`import com.loosecannon.servicetag.testing.FakeGraph`), which is its alphabetical position:

```kotlin
import com.loosecannon.servicetag.core.usecase.StoreIsEmpty
```

Then replace the fixture at lines 76–79. This is the change forced by the new dependency; it is the only edit to anything the 20 existing cases touch, and it changes none of their behaviour:

```kotlin
    private fun viewModel() = BackupViewModel(
        graph.exportBackupSet, graph.importBackupReplace, graph.restoreArtifacts,
        StoreIsEmpty(graph.assets, graph.tags, graph.events, graph.attachments, graph.links),
        graph.attachmentStorage, graph.prefs, graph.clock,
    )
```

`FakeGraph` gains no member: the use case is built here from the five repositories the fake already exposes (`FakeGraph.kt:87`–`93`), which keeps the fake exactly as `d69cf07` has it and keeps this fixture honest — it is the real `StoreIsEmpty` over the real Room repositories on the in-memory database.

Then add the two new cases at the end of the class, immediately before the closing brace (after `aPreservedRetiredPrefixArchiveStillImports`, line 763), under a new section comment:

```kotlin
    // --- the restore prompt (#40) -----------------------------------------------------------

    /**
     * #40 — a phone with nothing on it. The Backup screen asks this before it raises a dialog, and
     * the answer is what decides between the typed `REPLACE` confirmation and a plain one. Nothing
     * is seeded here, so the fixture *is* the empty install.
     */
    @Test fun anUntouchedInstallReportsAnEmptyStore() = runTest {
        assertTrue(viewModel().isStoreEmpty())
    }

    /**
     * #40, the other branch — one asset is enough. The typed word exists to make the owner accept
     * losing what is here, and now there is something here to lose.
     */
    @Test fun oneAssetIsEnoughToReportANonEmptyStore() = runTest {
        graph.createAsset.run("Pool pump", "Water")
        assertFalse(viewModel().isStoreEmpty())
    }
```

`assertFalse` is not yet imported in this file. Add it to the `org.junit.Assert` group, in alphabetical position — after `import org.junit.Assert.assertEquals` (line 41) and before `import org.junit.Assert.assertNotNull` (line 42):

```kotlin
import org.junit.Assert.assertFalse
```

Run: `./gradlew :app:testDebugUnitTest --tests '*BackupViewModelTest' --console=plain`
Expected: **FAILS to compile** — `Too many arguments for public constructor BackupViewModel` and `Unresolved reference: isStoreEmpty`. That is the red.

- [ ] **Step 6: Wire the graph and the view model.**

In `app/src/main/kotlin/com/loosecannon/servicetag/di/AppGraph.kt`, add one import in alphabetical position — after line 51 (`import com.loosecannon.servicetag.core.usecase.SaveProfile`) and before line 52 (`import com.loosecannon.servicetag.core.usecase.UpdateAsset`):

```kotlin
import com.loosecannon.servicetag.core.usecase.StoreIsEmpty
```

and add the one construction site immediately after `importBackupReplace` (after line 157), inside the backup block:

```kotlin
    /**
     * #40 — is there anything on this phone a restore would replace? The Backup screen asks once,
     * per picked file, and the answer chooses the confirmation. Definitions and profiles are not
     * read: neither can exist without its asset, so `assets` answers for both.
     */
    val storeIsEmpty: StoreIsEmpty = StoreIsEmpty(assets, tags, events, attachments, links)
```

In `app/src/main/kotlin/com/loosecannon/servicetag/ui/backup/BackupViewModel.kt`, add one import in alphabetical position — after line 19 (`import com.loosecannon.servicetag.core.usecase.RestoreArtifacts`) and before line 20 (`import com.loosecannon.servicetag.di.AppGraph`):

```kotlin
import com.loosecannon.servicetag.core.usecase.StoreIsEmpty
```

Replace the class header and its secondary constructor (lines 84–96) with:

```kotlin
class BackupViewModel(
    private val exportBackupSet: ExportBackupSet,
    private val importBackupReplace: ImportBackupReplace,
    private val restoreArtifacts: RestoreArtifacts,
    private val storeIsEmpty: StoreIsEmpty,
    private val storage: AttachmentStorage,
    private val prefs: AppPrefs,
    private val clock: Clock,
) : ViewModel() {

    constructor(graph: AppGraph) : this(
        graph.exportBackupSet, graph.importBackupReplace, graph.restoreArtifacts,
        graph.storeIsEmpty, graph.attachmentStorage, graph.prefs, graph.clock,
    )
```

and add the exposed question immediately after `restoreFiles` (after line 207), before `exportSetTo`:

```kotlin
    /**
     * Whether this phone holds any records at all (#40) — what the restore confirmation turns on.
     *
     * Deliberately not called `storeIsEmpty()`: that is the name of the use case this delegates to,
     * and a property and a function sharing a name would read as one thing. Deliberately a suspend
     * function rather than a state flow: it is asked at most once per visit to the screen, and the
     * answer that matters is the one true at the moment a file was picked. No `withContext` either
     * — Room already runs these five reads on the graph's own query context, which is what lets the
     * JVM fixture settle them on its shared test scheduler.
     */
    suspend fun isStoreEmpty(): Boolean = storeIsEmpty.run()
```

Run: `./gradlew :app:testDebugUnitTest --tests '*BackupViewModelTest' --console=plain`
Expected: `BUILD SUCCESSFUL`, **22** cases — the 20 that existed and the 2 new ones.

- [ ] **Step 7: The screen.**

In `app/src/main/kotlin/com/loosecannon/servicetag/ui/backup/BackupScreen.kt`. **No import changes**: `AlertDialog`, `Text`, `TextButton`, `Column`, `Arrangement`, `remember`, `mutableStateOf`, `getValue`, `setValue`, `ControlShape` and `kotlinx.coroutines.launch` are all already imported.

First, the file's own KDoc. Replace the second paragraph of the `BackupScreen` KDoc (lines 62–66) — the wording about the typed word gains its one exception:

```kotlin
 * Export is filled and safe. Restore data is outlined and asks the user to type [REPLACE_WORD]
 * first, because it deletes everything that is not in the file (R-9) — unless there is nothing
 * here to delete, in which case it asks for a plain confirmation instead (#40): a phone with no
 * assets, tags, events, attachments or tombstone link rows has nothing to accept the loss of, and
 * spelling out REPLACE over an empty database warns about data that does not exist. Restore files
 * is outlined but has no dialog at all: it adds bytes the data archive only listed and deletes
 * nothing, and it refuses an archive belonging to a different set rather than mixing two backups
 * together. There is no wipe here — that is the debug harness's job, not the product's.
```

Second, one more piece of remembered state. Replace lines 80–81 with:

```kotlin
    var confirming by remember { mutableStateOf<Uri?>(null) }
    var typed by remember { mutableStateOf("") }
    // Which confirmation the picked file gets (#40). Written before [confirming], so one pick
    // raises exactly one dialog and the owner never sees one replaced by the other.
    var emptyStore by remember { mutableStateOf(false) }
```

Third, the pick. Replace lines 99–105 with:

```kotlin
    val restoreDataFrom = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // Picking the file is not agreeing to lose what is here: the dialog is the agreement.
        //
        // #40 — and which agreement is a question about this phone, not about the file. It is asked
        // once per pick and answered before the dialog goes up, so there is never a frame in which
        // the wrong confirmation is on screen. A cancelled pick asks nothing.
        typed = ""
        if (uri == null) {
            confirming = null
        } else {
            scope.launch {
                emptyStore = model.isStoreEmpty()
                confirming = uri
            }
        }
    }
```

`typed = ""` stays **outside** the branch, exactly where `BackupScreen.kt:103` performs it today: a cancelled pick must still clear a half-typed word, and moving it inside `scope.launch` would leave it standing after a cancel. That is the one thing in this step that would have been a silent behaviour change, so it is deliberately not one.

Fourth, the branch. Replace lines 172–**209** — the whole `confirming?.let { uri -> … }` block **and** `BackupScreen`'s own closing brace on line 209 — with the following, which ends with that same closing brace at column 0. The `AlertDialog(` call in the `else` arm is the existing one, character for character, moved from 8 spaces of indentation to 12; no string, no parameter and not the `enabled = typed == REPLACE_WORD` gate changes:

```kotlin
    confirming?.let { uri ->
        if (emptyStore) {
            RestoreEmptyStoreDialog(
                onDismiss = { confirming = null },
                onRestore = {
                    confirming = null
                    model.restoreDataFrom(SafBackupIO(resolver, uri))
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { confirming = null },
                title = { Text("Replace everything?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Every asset, tag and link on this phone is deleted and replaced with what " +
                                "is in the file. This cannot be undone.",
                        )
                        OutlinedTextField(
                            value = typed,
                            onValueChange = { typed = it },
                            singleLine = true,
                            label = { Text("Type $REPLACE_WORD to confirm") },
                            shape = ControlShape,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirming = null
                            model.restoreDataFrom(SafBackupIO(resolver, uri))
                        },
                        enabled = typed == REPLACE_WORD,
                    ) {
                        Text("Replace")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirming = null }) { Text("Cancel") }
                },
                shape = ControlShape,
            )
        }
    }
}
```

Fifth, the new dialog. Add it immediately after the closing brace of `BackupScreen`, before `lastBackupLine` (which is currently at line 212):

```kotlin
/**
 * The confirmation an empty phone gets (#40). No typed word, because there is nothing to authorise
 * the loss of: `REPLACE` exists so that the owner has to spell out that they accept losing what is
 * on this phone (R-9), and on a phone with no assets, tags, events, attachments or tombstone link
 * rows there is nothing to lose. The restore itself is the same call either way — a wipe-and-load
 * of an empty database is a load.
 */
@Composable
private fun RestoreEmptyStoreDialog(onDismiss: () -> Unit, onRestore: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore this backup?") },
        text = { Text("This phone has no records yet, so there is nothing to replace.") },
        confirmButton = { TextButton(onClick = onRestore) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = ControlShape,
    )
}
```

- [ ] **Step 8: The emulator test for the plain dialog.**

Create `app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/backup/EmptyStoreRestorePromptTest.kt`:

```kotlin
package com.loosecannon.servicetag.ui.backup

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loosecannon.servicetag.ui.app
import com.loosecannon.servicetag.ui.awaitText
import com.loosecannon.servicetag.ui.clearInstall
import com.loosecannon.servicetag.ui.theme.ServiceTagTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** How long an assertion waits for a dialog to appear or go away. */
private const val SETTLE_MILLIS = 5_000L

/**
 * 2.8 (#40) — a phone with no records is asked to confirm, and is never asked to type REPLACE.
 *
 * **How the pick is driven without the SAF picker.** `rememberLauncherForActivityResult` resolves
 * its registry through `LocalActivityResultRegistryOwner`, which is the documented seam for exactly
 * this: the registry provided here answers a `launch` by dispatching a document URI straight back,
 * so no picker activity is started and `BackupScreen`'s callback runs precisely as it does after a
 * real pick. No seam is added to production code and no test dependency is added to the build.
 *
 * The URI is never opened. This case asserts which dialog came up and then cancels it; the restore
 * path itself is proved by `BackupViewModelTest`, `RestoreProofTest` and `PreservedSetRestoreTest`.
 *
 * Emulator only — the suite wipes app data.
 */
@RunWith(AndroidJUnit4::class)
class EmptyStoreRestorePromptTest {

    @get:Rule val rule = createComposeRule()

    /** A document that does not exist, because nothing here reads one. */
    private val picked = "content://com.loosecannon.servicetag.test/ServiceTag-data.zip".toUri()

    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                @Suppress("UNCHECKED_CAST")
                dispatchResult(requestCode, picked as O)
            }
        }
    }

    @Before fun freshInstall() = clearInstall()

    @Test fun anEmptyStoreIsAskedToConfirmAndNeverToTypeReplace() {
        rule.setContent {
            ServiceTagTheme {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                    BackupScreen(graph = app.graph, onBack = {})
                }
            }
        }

        rule.awaitText("Restore data")
        rule.onNodeWithText("Restore data").performClick()

        // The plain confirmation, in the owner's ratified words.
        rule.awaitText("Restore this backup?")
        rule.onNodeWithText("This phone has no records yet, so there is nothing to replace.")
            .assertIsDisplayed()
        // The confirm button, matched by exact text *and* a click action. `SectionHeader` uppercases
        // its title (`SectionHeader.kt:28`), so the "Restore" heading above the two restore buttons
        // is a `RESTORE` node and could not collide anyway — the click action says which node this
        // is about even if that ever changes.
        rule.onNode(hasText("Restore") and hasClickAction()).assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()

        // And not the typed one: neither its title nor the field that gates it is anywhere.
        rule.onAllNodesWithText("Replace everything?").assertCountEquals(0)
        rule.onAllNodesWithText("Type REPLACE to confirm").assertCountEquals(0)

        // Cancel closes it and restores nothing.
        rule.onNodeWithText("Cancel").performClick()
        rule.waitUntil(SETTLE_MILLIS) {
            rule.onAllNodesWithText("Restore this backup?").fetchSemanticsNodes().isEmpty()
        }
    }
}
```

Run it on the emulator (one class per invocation — a comma-separated list silently runs only the first):

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.loosecannon.servicetag.ui.backup.EmptyStoreRestorePromptTest \
  --console=plain
```

Expected: `BUILD SUCCESSFUL`; `app/build/reports/androidTests/connected/` shows `EmptyStoreRestorePromptTest` 1/1.

- [ ] **Step 9: The gate.**

Run: `./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`. `core/build/reports/tests/test/` shows `StoreIsEmptyTest` 6/6; `app/build/reports/tests/testDebugUnitTest/` shows `BackupViewModelTest` 22/22 and every other class at its previous count.

- [ ] **Step 10: Stage the work, so the verification can see all of it.**

```bash
git add -A
git status --porcelain
```

This step exists because of how the next one's commands read the repository. `git diff` with no revision compares the work tree with the **index**, and `git grep` searches **tracked** files — so an untracked new file is invisible to both. Three of this task's seven files are new (`StoreIsEmpty.kt`, `StoreIsEmptyTest.kt`, `EmptyStoreRestorePromptTest.kt`), and left unstaged they would make `git diff --name-only` print four paths instead of seven and `git grep -n 'StoreIsEmpty('` print one line instead of two — a verification that passes by not looking. Staging first, and using `--cached`, is what makes Step 11's counts mean what they say.

Expected: `git status --porcelain` lists exactly seven entries, all `A ` or `M `, and nothing with a `??`.

- [ ] **Step 11: Verify the blast radius.**

```bash
git diff --cached --stat -- libs app/schemas docs README.md app/build.gradle.kts app/src/main/AndroidManifest.xml
git diff --cached --stat -- core/src/main/kotlin/com/loosecannon/servicetag/core/ports core/src/test/kotlin/com/loosecannon/servicetag/core/testing app/src/test/kotlin/com/loosecannon/servicetag/testing app/src/main/kotlin/com/loosecannon/servicetag/data
git diff --cached --name-only
git grep --cached -c 'REPLACE_WORD' -- app/src/main
git grep --cached -n 'Restore this backup?' -- app/src/main
git grep --cached -n 'StoreIsEmpty(' -- app/src/main core/src/main
```

Expected: **no output** from both `git diff --cached --stat` commands — no port, no Room adapter, no fake, no schema, no doc and no build file changed. `git diff --cached --name-only` lists exactly seven paths: `StoreIsEmpty.kt`, `StoreIsEmptyTest.kt`, `AppGraph.kt`, `BackupViewModel.kt`, `BackupScreen.kt`, `BackupViewModelTest.kt`, `EmptyStoreRestorePromptTest.kt`. `REPLACE_WORD` is still 4 occurrences in 1 file (`BackupScreen.kt`) — the constant (`:53`), its one KDoc mention (`:62`), the dialog's label (`:186`) and the `enabled = typed == REPLACE_WORD` gate (`:198`), exactly as at `d69cf07`. `Restore this backup?` is 1 line. `StoreIsEmpty(` is **exactly 2 lines** in production: the class declaration in `:core`, and `AppGraph`'s single construction — "wired once".

- [ ] **Step 12: Commit.**

Everything is already staged by Step 10; if the gate or the verification made you edit anything since, stage it again first.

```bash
AUTHOR_EMAIL=$(git log -1 --format='%ae' master)
git add -A
git -c user.name=GonzRon -c user.email="$AUTHOR_EMAIL" commit -m "an empty phone doesn't need to type REPLACE"
```

---

### Task 2 (#41): inspect mode inspects

**Files:**
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/ui/scan/TagResultSheet.kt` (lines 56–78 KDoc and signature; lines 100–112, the `OpensAsset` branch)
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/ui/scan/ScanScreen.kt` (lines 69–71 KDoc; lines 142–163, the sheet's comment, its `TagResultSheet(` call and the function's closing brace)
- Create test: `app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/scan/InspectNamesABoundTagTest.kt`
- Modify test: `app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/scan/ReadScopedSheetOwnerTest.kt` (one KDoc sentence, line 37)
- **Untouched, and checked at Step 9:** `app/src/main/kotlin/com/loosecannon/servicetag/ui/scan/ScanViewModels.kt`, `.../TagResultWire.kt`, `.../TagWriteController.kt`, `.../WriteTagScreen.kt`, `.../ui/nfc/ReaderMode.kt`, `.../ui/nav/Route.kt`, `.../ui/nav/ServiceTagRoot.kt`, `.../MainActivity.kt`, `.../nfc/NfcDispatchActivity.kt`, `app/src/main/AndroidManifest.xml`, `libs/**`, `core/**`

**Two notes before any code is written.**

1. **`TagResultSheet.kt` is modified deliberately this release.** It was on 2.7's untouched list — 2.7's whole point was that the sheet was reused *verbatim* while its host changed, and the 2.7 plan's Task 2 named it under "Untouched". 2.8 changes it, and that is the release's substance: the sheet is now drawn in two roles and has to behave differently in one of them. Nothing else about it moves.
2. **`ScanViewModels.kt` stays untouched.** `TagResult.OpensAsset` (`ScanViewModels.kt:149`), `Resolution.OpenAsset`, `TagResultViewModel` and `TagBinding.identityLine()` (`ScanViewModels.kt:299`) are all exactly as `d69cf07` has them. #41 is a rendering decision about a result that already exists, not a new result.

**Why the ambient path is untouched, stated from the code.** There are exactly three call sites of `TagResultSheet(` in the tree (`git grep -n 'TagResultSheet(' -- app/src` → four lines, one of which is the declaration at `TagResultSheet.kt:70`):

- `ui/nav/ServiceTagRoot.kt:229`, inside `entry<Route.TagResult>`. This is where the **ambient trampoline** lands: `NfcDispatchActivity` reads the tag, resolves it, and hands `MainActivity` the (format, key) pair (`NfcDispatchActivity.kt:85`–`96`); `MainActivity` turns that into a `Route.TagResult` push. The whole point of that path is "tap a tag anywhere and the phone opens the right place", and its auto-open is what makes that sentence true. It keeps the default, so its behaviour is byte-identical.
- `ui/scan/ScanScreen.kt:152`, the deliberate Read / inspect tag screen, which since 2.7 draws the sheet itself over its own content. This one passes `inspecting = true`.
- `app/src/androidTest/.../PreSplitLinkTagSheetTest.kt:51`, a 2.6 regression case about a link tag. It keeps the default, because the branch it exercises is `PreSplitLink` and `inspecting` cannot reach it.

All three call sites use named arguments, so a new parameter cannot silently shift any of them.

**Decisions, and what lost:**

1. **A defaulted `Boolean` parameter on the sheet, not a second composable and not a subtype of `TagResult`.** The two roles differ in exactly one branch of seven, and every string, glyph, accent and action in the other six is identical; a second composable would duplicate two hundred lines so that one `when` arm could differ, and the duplicate would drift. A flag on the result type lost for a sharper reason: the result is what a tag *is*, decided by `ResolveTag` and re-resolved from a durable (format, key) pair — how it is *presented* is the caller's business, and putting the caller's business in the model would put it in the backup-surviving wire format's neighbourhood.
2. **`inspecting = false` is the default, so the ambient path is unchanged by omission.** The riskiest thing 2.8 could do is alter the one-tap spine. Defaulting to today's behaviour means the ambient entry and the 2.6 regression test need no edit at all, and the diff shows exactly one call site opting in.
3. **`FilledAction("Open asset")` first, `TextAction("Cancel", onDismiss)` second** — the G1 §1.4 stacking every other sheet in this file already uses (filled first, text last, `Cancel` aligned end). `Unregistered`, `Revoked`, `NotInRecords`, `NotOurs` and `PreSplitLink` all end in `TextAction("Cancel", onDismiss)`; the inspect sheet is the sixth to do so, so nothing new is invented about how a sheet is dismissed.
4. **A new eyebrow, `ServiceTag tag`, rather than reusing `Tag detected`.** "Tag detected" is the ambient branch's line and belongs to the moment of detection; on the inspect screen the owner already knows a tag was detected — they are holding it there on purpose — and what they asked for is *what it is*. The owner ratified the new word. The accent stays the okay accent and the glyph stays `ServiceTagIcons.NfcTag`: this is still a good outcome, and nothing about the tag changed.
5. **No status line in the inspect branch.** The `else` branch's `QuietLine("Opening asset…")` is a progress line for a navigation that is about to happen; in inspect mode nothing is happening, and a line saying so would be a seventh string nobody ratified. The `supporting` slot is simply not passed.
6. **The consequence, stated in full, because it is the actual fix.** With no auto-navigation, the inspect screen stays the top entry of the back stack. `ServiceTagRoot` holds the activity's one reader-mode session for as long as the top entry `readsTags()`, and `Route.Scan` does — so the hold is never released while the answer is on screen, and a tag still against the phone is never handed back to the platform to be re-discovered and dispatched to another app. **Open asset** navigates through the very `onOpenAsset` the screen already wires (`ScanScreen.kt:158`: `onOpenAsset = { id -> clearAnswer(); onOpenAsset(id) }`), which clears the answer and then pushes `Route.AssetDetail` — so the hold is released when, and only when, the owner leaves. `Cancel` and the system back both clear the answer and leave the owner on READY TO SCAN with the hold intact (`ScanScreen.kt:106`, proved by `InspectBackDismissesTheAnswerTest`).
7. **The 2.7 spin hazard stays closed.** 2.7's Task 2 decision 4 kept `Route.TagResult` out of the hold set because the sheet's auto-open would release reader mode with the tag in the field and invite a re-dispatch loop. That reasoning is unchanged for the ambient path — `Route.TagResult` is still not in the hold set, and `Route.readsTags()` is untouched. What 2.8 removes is the *inspect* screen's exposure to it.

**Interfaces:**
- Consumes (present at `d69cf07`): `TagResult.OpensAsset(tag: TagBinding, asset: Asset)`, `TagBinding.identityLine(): String`, `NfcSheet(eyebrow, sentence, accent, container, border, glyph, identifier, problem, actions, supporting)`, `ColumnScope.FilledAction(label, onClick)`, `ColumnScope.TextAction(label, onClick)`, `ServiceTagIcons.NfcTag`, `ServiceTagTheme.semanticColors.maintenanceOkay.foreground`, `ScanViewModel(resolveTag, io, codec, ioDispatcher)`, `ReaderMode(controlFor)`, `ReaderModeControl`, `NdefCodec.encodeV1(tagId)`, `TagInspection(uid, read, maxSize, writable, needsFormat, canLock)`, `TagRead.Readable(records)`, `BindTag.run(format, key, target, label)`, `CreateAsset.run(name, category, …)`.
- Produces: `TagResultSheet(graph: AppGraph, format: String, key: String, inspecting: Boolean = false, onDismiss: () -> Unit, onWriteTag: (Route.WriteTag) -> Unit, onOpenAsset: (String) -> Unit, onNewAsset: () -> Unit)` — one parameter added, defaulted, fourth in the list. Plus the two strings Task 3's README and runbook wording describe: `ServiceTag tag` and `Open asset`.

- [ ] **Step 1: Write the failing connected tests first.**

Create `app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/scan/InspectNamesABoundTagTest.kt`. Note before typing it: this file declares a file-level `private const val SETTLE_MILLIS`, and so does its neighbour `InspectBackDismissesTheAnswerTest.kt` in the same package (line 35). That is legal and intended — `private` on a top-level declaration means *file*-scoped, so two files in one package may each hold one, and each compiles to a static on its own `…Kt` class. The name is kept the same on purpose, so the two inspect-screen cases wait the same way.

```kotlin
package com.loosecannon.servicetag.ui.scan

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loosecannon.nfc.tagcore.NdefRecordData
import com.loosecannon.nfc.tagcore.android.TagHandle
import com.loosecannon.nfc.tagcore.android.TagInspection
import com.loosecannon.nfc.tagcore.android.TagIo
import com.loosecannon.nfc.tagcore.android.TagRead
import com.loosecannon.nfc.tagcore.android.WriteResult
import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.core.nfc.NdefCodec
import com.loosecannon.servicetag.ui.app
import com.loosecannon.servicetag.ui.awaitText
import com.loosecannon.servicetag.ui.clearInstall
import com.loosecannon.servicetag.ui.nfc.ReaderMode
import com.loosecannon.servicetag.ui.nfc.ReaderModeControl
import com.loosecannon.servicetag.ui.theme.ServiceTagTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** How long an assertion waits for a delivery, a resolve or a navigation to settle. */
private const val SETTLE_MILLIS = 5_000L

/** An invented canonical UUID, the same one `PreSplitLinkTagSheetTest` uses. Never a real tag's id. */
private const val TAG_KEY = "123e4567-e89b-12d3-a456-426614174000"

/**
 * 2.8 (#41) — a deliberate inspect inspects, and the ambient tap still opens.
 *
 * **How a real answer is produced without NFC.** `ScanViewModel`'s primary constructor already
 * takes a `TagIo` (`ScanViewModels.kt:87`); what has no seam is the path from `ScanScreen`, which
 * is handed only an `AppGraph`. So the model is built here with a `TagIo` that reads back the very
 * v1 record the seeded row is bound to, and seeded into the `ViewModelStore` this test provides
 * under the key `ScanScreen` resolves with — `viewModel(key = "scan")` returns the stored instance
 * rather than calling its initializer. `ScanViewModels.kt` is not touched and no seam is added to
 * production code. The records come from the production `NdefCodec`, so they are the bytes a real
 * tag would carry, and `ResolveTag` really does find the row and return `OpenAsset`.
 *
 * **Why the screen is composed directly.** Inside `ServiceTagRoot` the nav entry decorator owns
 * `LocalViewModelStoreOwner`, so the seeded store cannot reach the screen and no answer can be put
 * on screen at all. What the real back stack does with the navigation is covered by
 * `ReaderModeHoldTest`; what back does with a showing answer, by `InspectBackDismissesTheAnswerTest`.
 *
 * Emulator only — the suite wipes app data.
 */
@RunWith(AndroidJUnit4::class)
class InspectNamesABoundTagTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val control = CountingControl()
    private val readerMode = ReaderMode { _ -> control }

    /** Every asset id the host was asked to navigate to, in order. */
    private val opened = mutableListOf<String>()

    @Before fun freshInstall() = clearInstall()

    @Test fun aBoundTagIsNamedAndOpensOnlyWhenTheOwnerTapsIt() {
        val graph = app.graph
        val tubId = runBlocking {
            val tub = graph.createAsset.run("Hot tub", "Water")
            graph.bindTag.run(PayloadFormat.V1, TAG_KEY, TagTarget.AssetTarget(tub.id))
            tub.id.value
        }

        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        ViewModelProvider.create(
            owner,
            viewModelFactory {
                initializer { ScanViewModel(graph.resolveTag, ReadsABoundTag(graph.ndefCodec), graph.ndefCodec) }
            },
        )["scan", ScanViewModel::class.java]

        rule.setContent {
            ServiceTagTheme {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    ScanScreen(
                        graph = graph,
                        readerMode = readerMode,
                        onOpenAsset = { id -> opened += id },
                        onNewAsset = { error("a bound tag never makes an asset") },
                        onWriteTag = { error("a bound tag never writes a tag") },
                        onBack = {},
                    )
                }
            }
        }

        rule.awaitText("READY TO SCAN")
        rule.waitUntil(SETTLE_MILLIS) { readerMode.sinkCount == 1 }

        rule.runOnIdle { readerMode.deliver(Bound) }

        // The answer names the asset and offers to open it. Nothing has navigated.
        //
        // `sentence = result.asset.name` is the same in both arms, so "Hot tub" alone would pass
        // either way: the two assertions that actually tell the arms apart are the ratified eyebrow
        // — `ServiceTag tag`, rendered uppercase by `NfcSheet` (`TagResultSheet.kt:256`) — and the
        // absence of the other arm's `Opening asset…` status line.
        rule.awaitText("Hot tub")
        rule.onNodeWithText("SERVICETAG TAG").assertIsDisplayed()
        rule.onAllNodesWithText("Opening asset…").assertCountEquals(0)
        rule.onNodeWithText("Open asset").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
        rule.onNodeWithText("READY TO SCAN").assertIsDisplayed()
        assertEquals("an inspect must not navigate on its own", emptyList<String>(), opened)
        // Which is the whole point: the screen is still on top and still reading. The stop count is
        // the house form (`InspectBackDismissesTheAnswerTest.kt:124`) and satisfies the ratified
        // "zero reader-mode stops" literally — but note what it is not: `ReaderMode.hold()` is the
        // only caller of `ReaderModeControl.stop()` (`ui/nfc/ReaderMode.kt:86`–`91`) and the nav
        // shell is what calls `hold`, and the shell is not composed here. The real hold across a
        // navigation is `ReaderModeHoldTest`'s; this line is a guard, not the proof.
        assertEquals("the screen is still the sink", 1, readerMode.sinkCount)
        assertEquals("and nothing in this composition handed NFC back", 0, control.stops)

        // The owner's tap is what opens it, and it opens the asset the tag is bound to.
        rule.onNodeWithText("Open asset").performClick()
        rule.waitUntil(SETTLE_MILLIS) { opened.isNotEmpty() }
        assertEquals(listOf(tubId), opened)
    }

    /**
     * The ambient path, pinned. With `inspecting` left at its default the bound-tag branch still
     * navigates by itself, with no tap: `ServiceTagRoot`'s `Route.TagResult` entry — where the
     * `NfcDispatchActivity` trampoline lands — is exactly this call, so "tap a tag anywhere and the
     * phone opens the right place" is unchanged by #41.
     */
    @Test fun theAmbientSheetStillOpensABoundTagWithoutATap() {
        val graph = app.graph
        val tubId = runBlocking {
            val tub = graph.createAsset.run("Hot tub", "Water")
            graph.bindTag.run(PayloadFormat.V1, TAG_KEY, TagTarget.AssetTarget(tub.id))
            tub.id.value
        }

        rule.setContent {
            ServiceTagTheme {
                TagResultSheet(
                    graph = graph,
                    format = PayloadFormat.V1.name,
                    key = TAG_KEY,
                    onDismiss = {},
                    onWriteTag = { error("a bound tag never writes a tag") },
                    onOpenAsset = { id -> opened += id },
                    onNewAsset = { error("a bound tag never makes an asset") },
                )
            }
        }

        rule.waitUntil(SETTLE_MILLIS) { opened.isNotEmpty() }
        assertEquals(listOf(tubId), opened)
        // No action was offered, because none was needed.
        rule.onAllNodesWithText("Open asset").assertCountEquals(0)
    }

    /** A handle the test can build; [ReadsABoundTag] never looks inside it. */
    private object Bound : TagHandle {
        override val uid: String = "04a1"
    }

    /**
     * Reads back the one v1 record [TAG_KEY] is written as, through the production codec, so
     * `ScanViewModel` classifies `TagPayload.V1` and `ResolveTag` finds the seeded row and returns
     * `Resolution.OpenAsset`. The three write members are unreachable from the inspect screen.
     */
    private class ReadsABoundTag(private val codec: NdefCodec) : TagIo {
        override fun inspect(tag: TagHandle): TagInspection = TagInspection(
            uid = tag.uid,
            read = TagRead.Readable(codec.encodeV1(TagId(TAG_KEY))),
            maxSize = 492,
            writable = true,
            needsFormat = false,
            canLock = true,
        )

        override fun format(tag: TagHandle): WriteResult = error("the inspect screen never writes")
        override fun write(tag: TagHandle, records: List<NdefRecordData>, lock: Boolean): WriteResult =
            error("the inspect screen never writes")
        override fun lock(tag: TagHandle, expected: List<NdefRecordData>): Boolean =
            error("the inspect screen never locks")
    }

    private class CountingControl : ReaderModeControl {
        @Volatile var starts = 0
        @Volatile var stops = 0
        override val available: Boolean = true
        override val enabled: Boolean = true
        override fun start() { starts++ }
        override fun stop() { stops++ }
    }
}
```

- [ ] **Step 2: Run them and watch the first one fail.**

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.loosecannon.servicetag.ui.scan.InspectNamesABoundTagTest \
  --console=plain
```

Expected: **FAILURE**, 1 of 2. `theAmbientSheetStillOpensABoundTagWithoutATap` **passes** at `d69cf07` — that is the point of it, it pins behaviour that already exists. `aBoundTagIsNamedAndOpensOnlyWhenTheOwnerTapsIt` **fails** at the assertion `an inspect must not navigate on its own expected:<[]> but was:<[…]>`, because the sheet auto-navigates today. (It may instead fail earlier, on `onNodeWithText("Open asset")` finding no node; either failure is the red.) That is the red.

- [ ] **Step 3: The parameter, and the branch.**

In `app/src/main/kotlin/com/loosecannon/servicetag/ui/scan/TagResultSheet.kt`. **No import changes** — every symbol the new branch needs (`FilledAction`, `TextAction`, `ServiceTagIcons`, `ServiceTagTheme`, `NfcSheet`) is already in this file.

First, the KDoc and the signature. Replace lines 56–78 with:

```kotlin
/**
 * What a scanned (format, key) pair turned out to be — including "not ours"
 * ([TagResultWire.FORMAT_NONE]).
 *
 * The sheets here are the D12 §11 / G1 §1.4 set: eyebrow, one sentence, the mono identifier,
 * actions stacked with the filled one first. None of them is an error: an unregistered tag and a
 * foreign tag are both offers, and only the wording and the glyph change.
 *
 * @param key the scanned tag's identifier — but only while [format] names a payload format we
 *   wrote. When [format] is [TagResultWire.FORMAT_NONE] there is no identifier to show and `key`
 *   carries a prose reason the tag could not be read, so nothing may present it as an id or look
 *   it up as one.
 * @param inspecting whether this sheet is answering a *deliberate* look at a tag (2.8, issue #41).
 *   It changes one branch of seven: a tag already bound to an asset. The ambient trampoline exists
 *   to open the right place from one tap, so with the default `false` a bound tag navigates by
 *   itself, exactly as it always has. Read / inspect tag exists to *look* at a tag, so with `true`
 *   the sheet names the asset and waits — which also means the inspect screen stays on top and the
 *   activity's one reader-mode session stays held, instead of being released with the tag still
 *   against the phone. Every other branch is identical either way.
 */
@Composable
fun TagResultSheet(
    graph: AppGraph,
    format: String,
    key: String,
    inspecting: Boolean = false,
    onDismiss: () -> Unit,
    onWriteTag: (Route.WriteTag) -> Unit,
    onOpenAsset: (String) -> Unit,
    onNewAsset: () -> Unit,
) {
```

Second, the branch. Replace lines 100–112 — the whole `is TagResult.OpensAsset -> { … }` arm — with:

```kotlin
            is TagResult.OpensAsset -> if (inspecting) {
                // 2.8 (#41) — a deliberate inspect inspects. The tag is named and opening the
                // asset is the owner's tap, so this screen stays on top: the activity's one
                // reader-mode session is held for as long as a tag-reading route is the top entry,
                // and auto-navigating away released it with the tag still in the field. "Open
                // asset" goes through the same callback the screen already wires, which clears the
                // answer and navigates — so the hold ends when the owner leaves, and not before.
                NfcSheet(
                    eyebrow = "ServiceTag tag",
                    accent = ServiceTagTheme.semanticColors.maintenanceOkay.foreground,
                    glyph = ServiceTagIcons.NfcTag,
                    sentence = result.asset.name,
                    identifier = result.tag.identityLine(),
                    actions = {
                        FilledAction("Open asset") { onOpenAsset(result.asset.id.value) }
                        TextAction("Cancel", onDismiss)
                    },
                )
            } else {
                // A bound tag needs no decision: the sheet says what it is and the screen moves on.
                LaunchedEffect(result) { onOpenAsset(result.asset.id.value) }
                NfcSheet(
                    eyebrow = "Tag detected",
                    accent = ServiceTagTheme.semanticColors.maintenanceOkay.foreground,
                    glyph = ServiceTagIcons.NfcTag,
                    sentence = result.asset.name,
                    identifier = result.tag.identityLine(),
                ) {
                    QuietLine("Opening asset…")
                }
            }
```

The `else` arm is the existing code, character for character, **at its original indentation** — the `-> if (inspecting) { … } else { … }` form puts both arms at the 16 columns the old block body already used, so nothing in it moves: same eyebrow, same accent, same glyph, same sentence, same identifier, same `LaunchedEffect(result)`, same `QuietLine("Opening asset…")` in the `supporting` slot.

- [ ] **Step 4: The one call site that opts in.**

In `app/src/main/kotlin/com/loosecannon/servicetag/ui/scan/ScanScreen.kt`, replace the comment and call at lines 142–**163** — the `format?.let { … }` block **and** `ScanScreen`'s own closing brace on line 163 — with the following, which ends with that same closing brace at column 0:

```kotlin
    // Over this screen, not on top of it. Each way out clears the answer first, so coming back to
    // the inspector shows READY TO SCAN and not the answer to a tag that is long gone.
    //
    // The sheet resolves its own view model, and resolves it once. Drawn inside this entry it would
    // resolve against the entry's store, which lives until the entry is popped — so binding a tag,
    // coming back and reading it again would be answered by the first read's model, which still
    // thinks the tag is unassigned. One store per read is what the pushed result entry used to give.
    //
    // 2.8 (#41): `inspecting = true` is what makes this screen an inspector rather than a second
    // ambient trampoline. A tag already bound to an asset is named here and opened on a tap, so the
    // screen is never taken out from under a tag that is still against the phone — which is also
    // what keeps the activity's reader-mode hold alive for the whole look.
    format?.let { shown ->
        val owner = rememberReadScopedOwner(readId)
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            TagResultSheet(
                graph = graph,
                format = shown,
                key = answerKey,
                inspecting = true,
                onDismiss = clearAnswer,
                onWriteTag = { route -> clearAnswer(); onWriteTag(route) },
                onOpenAsset = { id -> clearAnswer(); onOpenAsset(id) },
                onNewAsset = { clearAnswer(); onNewAsset() },
            )
        }
    }
}
```

And amend the screen's own KDoc — replace lines 69–71 (the `2.7 (#37)` paragraph) with:

```kotlin
 * 2.7 (#37): the screen owns neither the reader-mode session — the nav shell holds one for the
 * whole tag flow — nor a route for its answer. The answer is drawn over this screen, so an inspect
 * never takes the screen out from under a tag that is still against the phone. 2.8 (#41) finishes
 * the thought: a tag already bound to an asset is *named* here rather than opened, so the one case
 * that still navigated by itself no longer does.
```

- [ ] **Step 5: Run the connected tests and watch both pass.**

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.loosecannon.servicetag.ui.scan.InspectNamesABoundTagTest \
  --console=plain
```

Expected: `BUILD SUCCESSFUL`; `InspectNamesABoundTagTest` 2/2.

Then the two pre-existing classes the same files can affect, each its own invocation:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.loosecannon.servicetag.ui.scan.InspectBackDismissesTheAnswerTest \
  --console=plain
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.loosecannon.servicetag.ui.scan.PreSplitLinkTagSheetTest \
  --console=plain
```

Expected: `BUILD SUCCESSFUL` both times, 1/1 each. `InspectBackDismissesTheAnswerTest` still passes because its answer is the "not a ServiceTag tag" sheet, which `inspecting` does not touch; `PreSplitLinkTagSheetTest` still passes because it keeps the default and its branch is `PreSplitLink`.

- [ ] **Step 6: The gate.**

Run: `./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`, every JVM class at its previous count. #41 adds no JVM test: the branch is a composable `when` arm, there is no decision to make outside a composition, and inventing a JVM seam for it would be a seam nobody else needs.

- [ ] **Step 7: Correct the one KDoc sentence 2.8 makes stale.**

`app/src/androidTest/.../ui/scan/ReadScopedSheetOwnerTest.kt:37` currently ends its KDoc with:

```
 * has no NFC. `TagResultSheet.kt` and `ScanViewModels.kt` are untouched this release.
```

That sentence was true of 2.7 and is now misleading. Replace it with:

```
 * has no NFC. `ScanViewModels.kt` is untouched; `TagResultSheet.kt` gains only 2.8's `inspecting`
 * flag (#41), which this probe does not reach.
```

Nothing else in that file changes, and the assertions are untouched.

- [ ] **Step 8: Stage the work, so the verification can see all of it.**

```bash
git add -A
git status --porcelain
```

Same reason as Task 1's staging step: `git diff` with no revision compares the work tree with the **index** and `git grep` searches **tracked** files, so `InspectNamesABoundTagTest.kt` — new, and the file that carries the fifth `TagResultSheet(` call site — is invisible to both until it is staged. Left unstaged it would make the next step's counts pass by not looking.

Expected: `git status --porcelain` lists exactly four entries, one `A ` and three `M `, and nothing with a `??`.

- [ ] **Step 9: Verify the blast radius.**

```bash
git diff --cached --stat -- libs core app/schemas docs README.md app/build.gradle.kts app/src/main/AndroidManifest.xml
git diff --cached --stat -- app/src/main/kotlin/com/loosecannon/servicetag/ui/nav app/src/main/kotlin/com/loosecannon/servicetag/ui/nfc app/src/main/kotlin/com/loosecannon/servicetag/nfc app/src/main/kotlin/com/loosecannon/servicetag/MainActivity.kt app/src/main/kotlin/com/loosecannon/servicetag/ui/scan/ScanViewModels.kt
git diff --cached --name-only
git grep --cached -n 'TagResultSheet(' -- app/src/main
git grep --cached -n 'TagResultSheet(' -- app/src/androidTest
git grep --cached -c 'inspecting' -- app/src/main
git grep --cached -n 'inspecting = true,' -- app/src/main
git grep --cached -n 'inspecting' -- app/src/main/kotlin/com/loosecannon/servicetag/ui/nav/ServiceTagRoot.kt
git grep --cached -n 'LaunchedEffect(result)' -- app/src/main
git grep --cached -c 'Tag detected' -- app/src/main
```

Expected: **no output** from either `git diff --cached --stat` — the nav shell, the reader-mode owner, the trampoline, `MainActivity` and `ScanViewModels.kt` are all untouched, and so are `:core`, `libs/`, the schemas, the docs and the build file. `git diff --cached --name-only` lists exactly four paths: `TagResultSheet.kt`, `ScanScreen.kt`, `InspectNamesABoundTagTest.kt`, `ReadScopedSheetOwnerTest.kt`.

The two `TagResultSheet(` greps are deliberately split, because the claim being made is about **production** call sites and this task adds a test one:
- `app/src/main` → **3 lines**: the declaration (`TagResultSheet.kt:70`) and the two production call sites (`ServiceTagRoot.kt`, `ScanScreen.kt`). Unchanged from `d69cf07`.
- `app/src/androidTest` → **2 lines**: `PreSplitLinkTagSheetTest.kt` (2.6's link-tag case, keeping the default) and the new `InspectNamesABoundTagTest.kt`'s ambient pin. One more than `d69cf07`, and that one is this task's own.

`inspecting` appears in exactly **2 files** under `app/src/main` (`TagResultSheet.kt`, `ScanScreen.kt`). `inspecting = true,` — **with the trailing comma**, which is what makes it the named argument at `ScanScreen.kt` and not Step 4's explanatory comment, which writes the same two words without one — is exactly **1 line**: one caller opts in. `ServiceTagRoot.kt` yields **no line** for `inspecting`: the ambient entry keeps the default. `LaunchedEffect(result)` is exactly **1 line**, now inside the non-inspecting arm — the auto-open still exists, for the path that wants it. `Tag detected` is still 1 file, 2 occurrences (the `OpensAsset` `else` arm and the `PreSplitLink` branch), exactly as at `d69cf07`.

- [ ] **Step 10: Commit.**

Everything is already staged by Step 8; if the verification made you edit anything since, stage it again first.

```bash
AUTHOR_EMAIL=$(git log -1 --format='%ae' master)
git add -A
git -c user.name=GonzRon -c user.email="$AUTHOR_EMAIL" commit -m "inspect mode names a bound tag instead of opening it"
```

---

### Task 3: the version bump, the README, and the runbook's R2

**Files:**
- Modify: `app/build.gradle.kts` (lines 43–44)
- Modify: `README.md` (lines 22–23; lines 74–75)
- Modify: `docs/architecture/product-split-migration.md` (line 960, the **R2** row)
- **Not modified:** `docs/architecture/product-split-evidence.md` (the controller writes the 2.8 evidence after Task 4; the 2.7 known-limit note at line 1096 is history and is not rewritten), `docs/architecture/product-split-target.md` (2.8 changes no identity, no filter and no division of labour), `docs/design/**` (history — never rewritten), the runbook's **R1** row at line 959 (retired at 2.7 by owner ruling; the retirement note stays exactly as it is) and the owner-action budget note at lines 977–985 (see Step 4)

**Interfaces:**
- Consumes: Task 1's plain-confirm behaviour — the four ratified #40 strings and the rule that "empty" means no assets, no tags, no events, no attachments and no tombstone link rows (`StoreIsEmpty`). Task 2's inspect behaviour — `TagResultSheet(inspecting = true)` from `ScanScreen`, the eyebrow `ServiceTag tag`, the action `Open asset`, and the consequence that the hold is released only when the owner leaves the screen.
- Produces: `BuildConfig.VERSION_NAME` becomes `"2.8"`, which `SettingsScreen` shows and `ExportBackupSet` writes into `BackupManifest.appVersion` (`AppGraph.kt:151`); `tools/release-dry-run.sh` reads the same `versionName` out of `app/build.gradle.kts` and compares it with the built artifact's.

- [ ] **Step 1: The version bump.**

In `app/build.gradle.kts`, inside `defaultConfig` (lines 43–44):

```kotlin
        versionCode = 10
        versionName = "2.8"
```

Nothing else in that file changes — no dependency, no `compileSdk`, no `targetSdk`, no signing block. There is no `schemaVersion` here to touch; `AppGraph.SCHEMA_VERSION` stays 5.

- [ ] **Step 2: The README's inspect clause.**

In `README.md`, inside `## What it does today`, replace lines 22–23:

```markdown
tag left against the phone is not handed back to the system mid-look. A tag already bound to an
asset opens that asset, and leaving the screen is what hands NFC back.
```

with:

```markdown
tag left against the phone is not handed back to the system mid-look. An inspect inspects: a tag
already bound to an asset is named there, with an Open asset action, and opening it is what hands
NFC back — the ambient tap still opens a bound tag straight away.
```

The "Scan a tag" bullet (lines 52–58) is **not** touched: its "A tag bound to an asset opens that asset" is about the ambient tap, which is exactly what 2.8 leaves alone.

- [ ] **Step 3: The README's restore clause.**

Replace lines 74–75:

```markdown
  deletes nothing. Restoring the data replaces everything on the phone and makes you type `REPLACE`
  first.
```

with:

```markdown
  deletes nothing. Restoring the data replaces everything on the phone and makes you type `REPLACE`
  first — unless the phone has no records yet, in which case there is nothing to replace and it only
  asks you to confirm.
```

Both README edits are documentation prose, not app strings; the six in-app strings 2.8 adds are the ones listed in Global Constraints.

- [ ] **Step 4: The runbook's physical row R2.**

In `docs/architecture/product-split-migration.md` §E, replace the **R2** row (line 960) with the row below. The **T4** half is unchanged, word for word, because 2.8 changes nothing about a foreign tag; the **T2** half — both the instruction and the expected outcome — is rewritten for #41, and the pre-2.7 duplicate that the row used to accept as an outcome is retired with a reason. The row's last clause, `**ServiceTag only**, two owner actions, outside the 12-action budget for the same reason R1 is`, is kept **byte-identical**: the count is of tag holds, of which there are still two, and the screen taps and the back press were never counted — which is why the budget note at lines 977–985 needs no edit.

```markdown
| **R2** | with **ServiceTag** open on **Read / inspect tag**, hold **T4** (NoteTag's tag) against the phone and **leave it there** until the answer appears; then take it away. Then, back on **Read / inspect tag**, hold **T2** (a tag bound to a ServiceTag asset) the same way, leave it there until the answer appears, take it away, tap **Open asset**, and press **back** once | issue **#37**'s acceptance, and check 6 of §E repeated with the tag left in the field — which is what the 2026-09-17 gate observed going wrong (§E check 6's note, evidence P5): ServiceTag released reader mode ~200 ms after its read and the platform dispatched the tag to NoteTag, which opened the note. Pass: the inspect screen names the tag as another app's and **nothing else opens** — no NoteTag window, no note, no chooser, no second dispatch — and the answer appears on the inspect screen itself rather than on a screen pushed over it. 2.7 holds the activity's one session for as long as a tag-reading screen is on top and draws the answer in place, so the tag stays ServiceTag's until the owner leaves the screen. For **T2**, from **2.8** (issue **#41**): the asset **does not open by itself**. The answer names it — the asset's name, its `id · v1` line, an **Open asset** action and **Cancel** — and the phone stays on the inspect screen for as long as the owner leaves the tag there, so the hold is never released with a tag in the field. **Open asset** then opens that asset **once**, and **back** returns to **Read / inspect tag** showing READY TO SCAN. Pass: one asset screen, no duplicate, no chooser, and the inspect screen underneath it when back is pressed. The duplicate this row used to record as an acceptable outcome is gone by construction — there is no auto-open left to release the hold — so a second copy of the asset screen is now a **failure**, not a variant. **ServiceTag only**, two owner actions, outside the 12-action budget for the same reason R1 is |
```

- [ ] **Step 5: Verify.**

```bash
git diff --stat -- docs/design docs/architecture/product-split-evidence.md docs/architecture/product-split-target.md docs/architecture/product-split-archaeology.md
grep -n 'versionCode\|versionName' app/build.gradle.kts
git diff --stat -- app/build.gradle.kts
grep -c 'An inspect inspects' README.md
grep -c 'unless the phone has no records yet' README.md
grep -c 'Retired at 2.7 (owner ruling 2026-09-18)' docs/architecture/product-split-migration.md
grep -c 'from \*\*2.8\*\* (issue \*\*#41\*\*)' docs/architecture/product-split-migration.md
grep -c 'three further taps' docs/architecture/product-split-migration.md
./gradlew :app:assembleDebug --console=plain
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging app/build/outputs/apk/debug/app-debug.apk | grep -oE "versionCode='[^']*'|versionName='[^']*'"
```

Expected: **no output** from the docs `git diff --stat` — the evidence, the target doc, the archaeology and every design document are untouched. `versionCode = 10` and `versionName = "2.8"`, and `app/build.gradle.kts` shows `1 file changed, 2 insertions(+), 2 deletions(-)`. Each `grep -c` on README returns `1`. `Retired at 2.7 (owner ruling 2026-09-18)` returns `1` — R1's retirement note survived the R2 edit. The new R2 marker returns `1`. `three further taps` returns `1` — the budget note is as it was, because R2 is still two tag holds. `BUILD SUCCESSFUL`, then `versionCode='10'` and `versionName='2.8'`. (Per the standing note, `aapt2` is at build-tools **36.0.0**.)

- [ ] **Step 6: Commit.**

```bash
AUTHOR_EMAIL=$(git log -1 --format='%ae' master)
git add -A
git -c user.name=GonzRon -c user.email="$AUTHOR_EMAIL" commit -m "2.8: bump the version, readme and the runbook catch up"
```

---

### Task 4 (controller-run): the proofs

**Files:** none. This task runs and records; the evidence file is the controller's to write afterwards.

**Interfaces:**
- Consumes: everything Tasks 1–3 produced — `StoreIsEmpty`, `AppGraph.storeIsEmpty`, `BackupViewModel.isStoreEmpty`, `RestoreEmptyStoreDialog`, `StoreIsEmptyTest`, `EmptyStoreRestorePromptTest`, `TagResultSheet(inspecting = …)`, `InspectNamesABoundTagTest`, `versionCode` 10 / `versionName` 2.8, the README clauses and the runbook's R2.
- Produces: the recorded verdicts for proofs (1)–(6), each with the command that produced it, and the phase-end sentence in Step 7.

- [ ] **Step 1: Proof (1) — the unit gate, from scratch.**

```bash
./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug \
  --rerun-tasks --console=plain
```

Expected: `BUILD SUCCESSFUL`, 0 failures. The counts, derived from the 2.7 row recorded at `docs/architecture/product-split-evidence.md:1083` (648 tests: app 37 classes/256, core 30/332, nfc-core 6/50, nfc-android 3/10) plus what 2.8 adds — **6** cases in one new `:core` class and **2** cases in an existing `:app` class — are **656 tests: app 37/258, core 31/338, nfc-core 6/50, nfc-android 3/10**. Read them off the reports rather than assuming them: `StoreIsEmptyTest` 6/6 with `anUntouchedInstallIsEmpty`, `oneAssetIsEnoughToMakeItNotEmpty`, `oneTagRowIsEnoughToMakeItNotEmpty`, `oneEventIsEnoughToMakeItNotEmpty`, `oneAttachmentRowIsEnoughToMakeItNotEmpty` and `oneTombstoneLinkRowIsEnoughToMakeItNotEmpty` as passes; `BackupViewModelTest` 22/22 with `anUntouchedInstallReportsAnEmptyStore` and `oneAssetIsEnoughToReportANonEmptyStore` among them. A pre-existing class at a different count than 2.7 recorded is a regression to investigate, not a number to write down. Record the class list and the counts.

- [ ] **Step 2: Proof (2) — the whole connected suite on the emulator, with the preserved set staged.**

`PreservedSetRestoreTest` reads a real pre-split data archive from `/data/local/tmp`; without it staged that class skips its assertion and the format-5 restore is not actually proved. Push it first, run the suite, remove it after — nothing is written to the phone and nothing personal is printed. `0771` on `/data/local/tmp` gives others traverse, so `chmod 644` is what lets an app-uid test open it:

```bash
SET=~/Documents/Projects/AndroidStudioProjects/noteNFC-backups/transition-20260918-075440
adb -s emulator-5554 push "$SET/noteNFC-data-20260918-075301.zip" /data/local/tmp/servicetag-proof-data.zip
adb -s emulator-5554 shell chmod 644 /data/local/tmp/servicetag-proof-data.zip
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --console=plain
adb -s emulator-5554 shell rm -f /data/local/tmp/servicetag-proof-data.zip
```

`~` is what the committed plan says, and it is right for the owner's own shell. If the shell running this is not the owner's — an agent shell's `$HOME` is not theirs — expand `~` to the owner's home *in the shell only*, e.g. `SET=$(getent passwd "$(stat -c %U .)" | cut -d: -f6)/Documents/Projects/AndroidStudioProjects/noteNFC-backups/transition-20260918-075440`, and **never write the expanded path into a committed file or a report**. If the `adb push` fails, stop: `PreservedSetRestoreTest` reads `/data/local/tmp/servicetag-proof-data.zip` and without the archive its assertion does not run, so a green suite would not mean the format-5 restore was proved.

Expected: `BUILD SUCCESSFUL`. In `app/build/reports/androidTests/connected/`: `EmptyStoreRestorePromptTest` 1/1, `InspectNamesABoundTagTest` 2/2, `PreservedSetRestoreTest` 1/1, and every pre-existing class at its previous count — in particular `InspectBackDismissesTheAnswerTest` 1/1, `ReadScopedSheetOwnerTest` 1/1, `ReaderModeHoldTest` 3/3, `DashboardSearchTest` 2/2, `PreSplitLinkTagSheetTest` 1/1, `NavigationSmokeTest`, `WriteTagScreenConsentWordingTest`, `AssetModelDeviceProofTest` and `RemovedSurfacesTest` unchanged. Against the 79 tests / 21 classes the 2.7 row records (`product-split-evidence.md:1084`), 2.8 adds two classes and three cases: **82 tests, 23 classes**. There is no expected deletion — 2.8 removes no test. Record the class list, the counts and the run's timestamp. Confirm the `rm -f` ran.

- [ ] **Step 3: Proof (3) — the structural facts neither suite can assert.**

```bash
git grep -n 'TagResultSheet(' -- app/src/main                    # 3 lines: the declaration + 2 production call sites
git grep -n 'TagResultSheet(' -- app/src/androidTest             # 2 lines: PreSplitLinkTagSheetTest + the ambient pin
git grep -c 'inspecting' -- app/src/main                         # 2 files: TagResultSheet.kt, ScanScreen.kt
git grep -n 'inspecting = true,' -- app/src/main                 # 1 line, in ScanScreen.kt (the comma excludes the comment)
git grep -n 'inspecting' -- app/src/main/kotlin/com/loosecannon/servicetag/ui/nav/ServiceTagRoot.kt   # no output
git grep -n 'LaunchedEffect(result)' -- app/src/main             # 1 line, the non-inspecting arm
git grep -c 'REPLACE_WORD' -- app/src/main                       # 1 file, 4 occurrences
git grep -c 'Replace everything?' -- app/src/main                # 1 file, 1 occurrence
git grep -n 'StoreIsEmpty(' -- app/src/main core/src/main        # 2 lines: the class, and AppGraph's one construction
git diff --stat HEAD~3 -- libs core/src/main/kotlin/com/loosecannon/servicetag/core/ports app/schemas app/src/main/AndroidManifest.xml app/src/main/kotlin/com/loosecannon/servicetag/nfc app/src/main/kotlin/com/loosecannon/servicetag/MainActivity.kt app/src/main/kotlin/com/loosecannon/servicetag/ui/nav app/src/main/kotlin/com/loosecannon/servicetag/ui/nfc app/src/main/kotlin/com/loosecannon/servicetag/ui/scan/ScanViewModels.kt
git -C libs/nfc-tag-core status --porcelain | wc -l              # 0
git ls-tree HEAD libs/nfc-tag-core                               # 160000 commit 7e0377ac99d7a4fee95ca6b88551daaa6330e52f
bash tools/check-submodule-pin.sh
```

Expected: the counts as annotated, **no output** from the `git diff --stat` (the library, the repository ports, the schemas, the manifest, the trampoline, `MainActivity`, the nav shell, the reader-mode owner and `ScanViewModels.kt` are all unchanged across all three commits), the gitlink still at `7e0377a`, and the pin script passing. Together these say: the library did not change, the ports did not change, the ambient path did not change, one caller opted into inspect mode, the typed `REPLACE` dialog is still there, and the new use case is wired exactly once.

Two of these counts are easy to get wrong, so they are spelled out. The `TagResultSheet(` check is **split by source set** because the claim is about production call sites and this release adds a test one: `app/src/main` stays at **3** (declaration + `ServiceTagRoot.kt` + `ScanScreen.kt`), while `app/src/androidTest` goes from 1 to **2** (`PreSplitLinkTagSheetTest.kt` plus `InspectNamesABoundTagTest.kt`'s ambient pin) — an unsplit `-- app/src` would return 5 here and read as a regression. And `inspecting = true` is grepped **with its trailing comma**, because Task 2 Step 4's explanatory comment writes those same two words without one; the comma is what makes the match the named argument and keeps the answer at 1. The four `REPLACE_WORD` lines are the constant (`BackupScreen.kt:53`), its one KDoc mention (`:62`), the dialog's label (`:186`) and the `enabled = typed == REPLACE_WORD` gate (`:198`).

- [ ] **Step 4: Proof (4) — the version in the built artifact.**

```bash
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging app/build/outputs/apk/debug/app-debug.apk \
  | grep -oE "versionCode='[^']*'|versionName='[^']*'"
```

Expected: `versionCode='10'` and `versionName='2.8'`. Record both lines.

- [ ] **Step 5: Proof (5) — the release signing dry run.**

The expected fingerprint is the ServiceTag release signer's, already published in the evidence file, so it is read from there rather than typed:

```bash
RELEASE_CERT_SHA256=$(sed -n 's/.*certificate `\([0-9A-F]\{64\}\)`.*/\1/p' docs/architecture/product-split-evidence.md | tail -1) \
  bash tools/release-dry-run.sh
echo "exit: $?"
```

Expected: `RELEASE DRY RUN: PASS` with exit 0, `fingerprint compare: matches`, the built `versionName` compared against `app/build.gradle.kts` and found to be **2.8**, and exactly one signer. Record the verdict line, the version-comparison line and the exit code **only** — never a fingerprint, never a keystore path, never a password. A `PARTIAL` is **not a pass** for this release. If the script exits 3 (no signing material on this machine) that is a **BLOCKED** proof, not a pass, and the controller stops and says so.

- [ ] **Step 6: Proof (6) — hygiene, and nothing on the phone.**

```bash
git diff --stat HEAD~3
git log --oneline -3
git diff HEAD~3 | grep -nE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' || echo 'no e-mail address'
git diff HEAD~3 | grep -nE '/home/|/Users/' || echo 'no absolute home path'
git diff HEAD~3 | grep -nE 'emulator-[0-9]+' | grep -v 'emulator-5554' || echo 'no device but emulator-5554'
```

Expected: the three commits of Tasks 1–3 and no other path; each of the last three commands printing its `no …` line. The three commit subjects are single lines with no body and no trailer. Nothing in this change installs on, reads from or writes to the phone: every connected and `adb` command above carries `-s emulator-5554` or `ANDROID_SERIAL=emulator-5554` **on the same command line**, and `adb devices` is not consulted for the phone at all. **Do not hand the owner a manual checklist**; the one device row 2.8 asks for is **R2** in §E of the runbook, it is two tag holds and two screen taps, and it is the owner's to schedule.

- [ ] **Step 7: Record and hand back.**

Assemble one table — proof, command, expected, observed, verdict — for (1)–(6), plus the per-task gate counts and the connected run's class list. State, from the code:

- **#40's rule**: "empty" is no assets, no tags, no events, no attachments and no tombstone link rows; definitions and profiles are not read because neither can exist without the asset it names (`core/model/Journal.kt:10`, `:30`), so the asset check answers for both. `StoreIsEmpty` is wired exactly once, in `AppGraph`.
- **#41's fix and its consequence**: the auto-navigation is still there for the ambient trampoline and gone for the inspect screen, so the inspect screen stays the top entry, the activity's one reader-mode session stays held for the whole look, and the hold ends only when the owner leaves through the same `onOpenAsset` the screen already wired.
- **What 2.8's evidence section must and must not say**: it adds a supersession note — **"resolved by #41 in 2.8"** — against the 2.7 entry's known-limit note at `docs/architecture/product-split-evidence.md:1096`. That note is **history and is not rewritten**.

The phase ends **"ServiceTag 2.8 complete locally; an empty phone is only asked to confirm, and a deliberate inspect names a bound tag instead of opening it; physical row R2 and the push and `servicetag-v2.8` authorized once CI is green on the exact tip."** No push, no tag, no `gh` mutation.

---

## Self-review

| Requirement | Where |
|---|---|
| #40 — a new `core` use case, `suspend fun run(): Boolean` | Task 1 Step 3 (`core/usecase/StoreIsEmpty.kt`) |
| #40 — true only with no assets, tags, events, attachments or tombstone link rows | Task 1 Step 3 (the `&&` chain over the five ports), Step 1 (`StoreIsEmptyTest`: empty → true, and one case per kind alone → false) |
| #40 — over the five existing repositories in `AppGraph` | Task 1 Step 6 (`StoreIsEmpty(assets, tags, events, attachments, links)`), Interfaces block (the five port members with their line numbers) |
| #40 — cheapest existing query for "any row?"; a query added only if none exists | Task 1 decision 3 and Step 3's KDoc: `AttachmentRepository.count()` for attachments, `all()` for the other four; **no** port, Room adapter or fake is edited — checked by Task 1 Step 11's two empty `git diff --cached --stat`s |
| #40 — injected into `BackupViewModel`: constructor, `graph` constructor, `AppGraph` wiring | Task 1 Step 6 (all three, with the exact replacement text) |
| #40 — `BackupViewModel` exposes it | Task 1 Step 6 (`suspend fun isStoreEmpty(): Boolean`), decision 8 for the name |
| #40 — the screen asks once when a data file is picked | Task 1 Step 7 (the launcher callback's `scope.launch { … }`), decision 6 for why before the dialog and not after |
| #40 — the existing typed dialog whenever any row exists, unchanged | Task 1 Step 7 (the `else` arm, reproduced character for character), decision 7; pinned by Task 4 Step 3's `REPLACE_WORD` and `Replace everything?` counts |
| #40 — a plain confirm when the store is empty, in the four ratified words | Task 1 Step 7 (`RestoreEmptyStoreDialog`), Global Constraints (the strings verbatim) |
| #40 — the use case tested with fakes | Task 1 Step 1: `StoreIsEmptyTest`, 6 cases over the five `InMemory*Repository` fakes |
| #40 — `BackupViewModelTest`, both branches, 20 existing cases green | Task 1 Step 5 (two new cases → 22), the exact fixture change shown, `assertFalse` import named |
| #40 — one connected Compose case seeding an empty store and driving the pick | Task 1 Step 8: `EmptyStoreRestorePromptTest` — `clearInstall()`, `LocalActivityResultRegistryOwner`, the plain dialog's title and confirm button asserted, the typed dialog's title and field asserted absent |
| #41 — `TagResultSheet` gains `inspecting: Boolean = false` | Task 2 Step 3 (the signature and the `@param`) |
| #41 — `ScanScreen` passes `inspecting = true`; every other caller keeps the default | Task 2 Step 4; the three production call sites enumerated in Task 2's preamble; pinned by Task 2 Step 9 and Task 4 Step 3 (`inspecting = true,` — with the comma — exactly 1 line; no line in `ServiceTagRoot.kt`; `TagResultSheet(` split by source set, `app/src/main` 3 and `app/src/androidTest` 2) |
| #41 — why the ambient path is untouched, from the code | Task 2's "Why the ambient path is untouched": `NfcDispatchActivity.kt:85`–`96` hands `MainActivity` the pair, `MainActivity` pushes `Route.TagResult`, `ServiceTagRoot.kt:229` draws the sheet with the default |
| #41 — in inspect mode no auto-navigation, and the exact `NfcSheet` arguments | Task 2 Step 3: eyebrow `ServiceTag tag`, `sentence = result.asset.name`, `identifier = result.tag.identityLine()`, the okay accent, `ServiceTagIcons.NfcTag`, `FilledAction("Open asset")` then `TextAction("Cancel", onDismiss)`, no `supporting` slot |
| #41 — no status line, no other new string | Task 2 decision 5; Global Constraints lists the six strings and nothing else |
| #41 — the non-inspecting branch byte-identical | Task 2 Step 3 (the `else` arm), Task 4 Step 3 (`LaunchedEffect(result)` still exactly 1 line; `Tag detected` still 2 occurrences) |
| #41 — the consequence stated: the screen stays on top, the hold stays held, Open asset releases it by leaving | Task 2 decision 6, Task 2 Step 4's comment, Task 3 Step 4's R2 row, Task 4 Step 7 |
| #41 — a connected case with a seeded asset and bound tag, no navigation, zero stops, then the tap | Task 2 Step 1: `aBoundTagIsNamedAndOpensOnlyWhenTheOwnerTapsIt` — `opened` empty, `control.stops == 0`, `sinkCount == 1`, then `performClick()` and `opened == listOf(tubId)` |
| #41 — a case pinning the default-false auto-open | Task 2 Step 1: `theAmbientSheetStillOpensABoundTagWithoutATap` — `TagResultSheet` composed directly, no `inspecting`, `onOpenAsset` fires with no tap |
| #41 — the `TagIo` fake written out in full, and what it must return | Task 2 Step 1: `ReadsABoundTag` returns `TagInspection(read = TagRead.Readable(codec.encodeV1(TagId(TAG_KEY))), …)`; the store holds the asset plus `bindTag.run(PayloadFormat.V1, TAG_KEY, TagTarget.AssetTarget(tub.id))` |
| `TagResultSheet.kt` modified deliberately; `ScanViewModels.kt` untouched | Task 2's "Two notes before any code is written"; checked by Task 2 Step 9 |
| Version, README, runbook R2 | Task 3 Steps 1–4 |
| R1's retirement note survives; the budget note is not edited | Task 3 Step 4 (the reason: R2 is still two tag holds) and Step 5 (`grep -c 'Retired at 2.7 …'` → 1, `grep -c 'three further taps'` → 1) |
| The evidence file is touched by no task; the 2.7 known-limit note is history | Global Constraints, Task 3's "Not modified", Task 4 Step 7 |
| Controller proofs: gate from scratch, connected suite with the preserved set staged, structural greps, aapt2, dry run, hygiene | Task 4 Steps 1–6 |
| The phase-end sentence | Task 4 Step 7 |
| Six ratified strings, byte-identical wherever used | Global Constraints; Task 1 Step 7 (four); Task 2 Step 3 (two). All six are asserted on a device: `Restore this backup?`, the body sentence, `Restore` and `Cancel` by `EmptyStoreRestorePromptTest`; `Open asset` and the eyebrow — as the `SERVICETAG TAG` its `uppercase()` renders — by `InspectNamesABoundTagTest`, which also asserts `Opening asset…` absent so "no status line" is pinned too |
| `libs/` untouched at `7e0377a`; no schema or format bump; tombstones untouched; `NfcDispatchActivity` untouched | Global Constraints; Task 1 Step 11, Task 2 Step 9, Task 4 Step 3 |
| Commit style, author GonzRon, no attribution | Global Constraints; the three subjects in Tasks 1–3, each with the `git -c user.name=… -c user.email=…` form |
| Device rule, emulator only | Global Constraints; every connected and `adb` command in Tasks 1, 2 and 4 carries `emulator-5554` on the same line |
| Room-backed view-model fixture convention, quoted | Global Constraints, quoting `BackupViewModelTest.kt:63`–`69`, `TagWriteControllerTest.kt:39`–`42` and `TestDb.kt:9`–`15` |
| No `TagIo` seam on `AppGraph` | Global Constraints; Task 2 Step 1's KDoc seeds the model into a test-provided `ViewModelStore` instead |
| Hygiene: no e-mail, no absolute home path, no device but emulator-5554, no tag UID, no note id | Global Constraints; Task 4 Step 6's three greps; `~` used for the home directory in Task 4 Step 2; the invented UUID `123e4567-…` in both test files |

**Every type, function and file named in a later task is defined by an earlier one or exists in the tree at `d69cf07`.**

**New in this plan:** `StoreIsEmpty` (+ `run`), `StoreIsEmptyTest`, `AppGraph.storeIsEmpty`, `BackupViewModel.isStoreEmpty`, `RestoreEmptyStoreDialog`, `EmptyStoreRestorePromptTest`, `TagResultSheet`'s `inspecting` parameter, `InspectNamesABoundTagTest` (+ its `Bound`, `ReadsABoundTag`, `CountingControl`, `TAG_KEY`, `SETTLE_MILLIS`).

**Existing, read in the tree at `d69cf07`:** `AssetRepository.all`, `TagRepository.all`, `EventRepository.all`, `AttachmentRepository.count`, `LinkRepository.all`, `AssetRepository`/`TagRepository`/`EventRepository`/`AttachmentRepository`/`LinkRepository`, `AppGraph` (`assets`, `tags`, `events`, `attachments`, `links`, `uow`, `prefs`, `clock`, `ids`, `ndefCodec`, `resolveTag`, `bindTag`, `createAsset`, `exportBackupSet`, `importBackupReplace`, `restoreArtifacts`, `attachmentStorage`, `SCHEMA_VERSION`), `FakeGraph` (`assets`, `tags`, `events`, `attachments`, `links`, `createAsset`, `prefs`, `clock`, `exportBackupSet`, `importBackupReplace`, `restoreArtifacts`, `attachmentStorage`, `close`), `InMemoryAssetRepository`, `InMemoryTagRepository`, `InMemoryEventRepository`, `InMemoryAttachmentRepository`, `InMemoryLinkRepository`, `Asset`, `AssetId`, `AssetEvent`, `EventId`, `EventKind`, `EventSource`, `Attachment`, `AttachmentId`, `AttachmentKind`, `AttachmentOwner`, `ExternalLink`, `LinkId`, `LinkKind`, `TagBinding`, `TagId`, `TagTarget`, `PayloadFormat`, `BackupViewModel`, `BackupState`, `BackupSetSink`, `SafBackupIO`, `SafBackupSetWriter`, `REPLACE_WORD`, `IMPORT_TYPES`, `lastBackupLine`, `LabelValue`, `QuietLine`, `SectionHeader`, `ControlShape`, `ExportBackupSet`, `ImportBackupReplace`, `RestoreArtifacts`, `AttachmentStorage`, `AppPrefs`, `Clock`, `TagResultSheet`, `TagResultViewModel`, `TagResult` (`Loading`, `OpensAsset`, `PreSplitLink`, `Unregistered`, `Revoked`, `NotInRecords`, `NotOurs`), `TagResultEvent`, `TagResultWire` (`FORMAT_NONE`, `wordFor`, `payloadOf`), `NfcSheet`, `SheetHost`, `FilledAction`, `OutlinedAction`, `TextAction`, `BindTargets`, `ServiceTagIcons` (`NfcTag`, `Contactless`), `ServiceTagTheme.semanticColors.maintenanceOkay`, `SheetShape`, `SheetSentence`, `MonoText`, `PlateShape`, `TagBinding.identityLine`, `identityLine(key)`, `ScanScreen`, `ScanViewModel`, `ScanState`, `ScanEvent.Show`, `rememberReadScopedOwner`, `Resolution.OpenAsset`, `ResolveTag`, `BindTag.run`, `CreateAsset.run`, `NdefCodec.encodeV1`, `TagPayload.V1`, `ReaderMode`, `ReaderModeControl`, `SessionControl`, `rememberReaderMode`, `TagSinkEffect`, `Route.Scan`, `Route.TagResult`, `Route.WriteTag`, `Route.readsTags`, `ServiceTagRoot`, `NfcDispatchActivity`, `MainActivity`, `TagHandle`, `NfcTagHandle`, `TagIo`, `TagInspection`, `TagRead.Readable`, `WriteResult`, `NdefRecordData`, `app`, `clearInstall`, `awaitText`, `exportedDataArchive`, `createComposeRule`, `createAndroidComposeRule`, `InspectBackDismissesTheAnswerTest`, `PreSplitLinkTagSheetTest`, `ReadScopedSheetOwnerTest`, `ReaderModeHoldTest`, `DashboardSearchTest`, `PreservedSetRestoreTest`, `inMemoryDb`, `TestCoroutineScheduler`, `UnconfinedTestDispatcher`, `StandardTestDispatcher`, `tools/release-dry-run.sh`, `tools/check-submodule-pin.sh`.
