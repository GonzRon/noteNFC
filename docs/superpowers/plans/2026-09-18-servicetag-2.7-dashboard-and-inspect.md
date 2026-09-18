# ServiceTag 2.7 — The Dashboard Lists Systems, and an Inspect Keeps NFC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two owner-requested items. **#39** — the dashboard's asset list stops listing individual components (an `Asset` with a non-null `parentAssetId`) as if they were systems of their own, and gains a quick search box above the list that filters as you type; the search reaches components too, each one annotated with the asset it is part of, so a hidden component is one keystroke away. **#37** — Settings → Utilities → Read / inspect tag stops releasing reader mode ~200 ms after a read: the inspect screen holds the activity's one reader-mode session for as long as a tag-reading screen is on top and draws the read's answer on the screen the user is already on, so a tag still in the field is never handed back to the platform to be re-discovered and dispatched to another app. Runbook row **R1** — ServiceTag's two overlapping reader-mode sessions — is answered structurally by the same change: there is one session for the activity and the screens install a tag sink instead. Ship it as `versionCode` 9 / `versionName` 2.7.

**Architecture:** Four tasks. **Task 1 (#39)** is `:app` only and lives in `DashboardViewModel`/`DashboardScreen`: the view model gains a query flow and emits `DashboardRow`s (the asset plus the parent's name), the screen gains an `OutlinedTextField` above the list with a clear action. The asset detail screen's **Components** section already exists and already lists children with navigation (`AssetDetailScreen.kt:641`, `AssetViewModels.kt:298`), so the owner's scope check needs no code — only a recorded finding. **Task 2 (#37 + R1)** hoists the reader-mode session out of the two screens into the nav shell as one activity-wide `ReaderMode` object with a tag-sink stack, scopes the hold to the routes that read tags, and moves the read's result sheet from a pushed `Route.TagResult` entry onto the inspect screen itself; the pushed entry stays exactly as it is for the ambient trampoline. **Task 3** is the version bump, the README and the two physical-gate runbook rows. **Task 4** is the controller-run proof set. Tasks 1 and 2 are independent — different files, no shared type — and each compiles and gates on its own.

**Tech Stack:** the estate's pins — AGP 9.4.0, Kotlin 2.4.20, Gradle 9.7.1, JDK 17; `nfc-tag-core` at `nfc-tag-core-v0.1.0` (`7e0377a`) as a pinned submodule under `libs/`, **untouched**; Room 3 with `AppDatabase` at schema version **5, unchanged**; Compose + Navigation 3; JUnit 5 in `:core`, JUnit 4 + Compose test in `:app`; `apksigner`/`aapt2` from build-tools 36.0.0 for `tools/release-dry-run.sh`.

**Spec:** GitHub issues [#39](https://github.com/GonzRon/ServiceTag/issues/39) and [#37](https://github.com/GonzRon/ServiceTag/issues/37), and runbook row **R1** at `docs/architecture/product-split-migration.md:959`. #37's field observation is recorded at `docs/architecture/product-split-evidence.md:1040` (physical gate row P5).

---

## The two issues, as scoped

**#39.** The dashboard list must not show individual components that belong to a parent asset. Add a quick search box on the dashboard that filters the asset list as you type, and let the search reach components, so a hidden component is one keystroke away. Scope check the owner asked for: *does the asset detail screen list a parent's components?* — **it does**, see the finding in Task 1 Step 0; nothing is added there.

**#37.** Settings → Utilities → Read / inspect tag releases reader mode ~200 ms after reading a tag while the tag is still in the field; the platform re-discovers the tag in normal dispatch mode and dispatches it (observed: a NoteTag tag launched NoteTag from inside ServiceTag's inspect screen). Expected: the inspect screen keeps reader mode until the screen is left, so an inspect never turns into an ambient dispatch. Related: **R1** — ServiceTag composes two reader-mode sessions (`ScanScreen` and `WriteTagScreen`); on a nav transition that overlaps both, the surviving screen must still read.

## Global Constraints

- **The four new user-facing sentences of the release**, verbatim and in full — the only new in-app strings 2.7 adds, all four on the dashboard, all four for the owner to ratify:
  - `Search assets and components`
  - `Components are listed on the asset they belong to. Search to find one.`
  - `Nothing matches that.`
  - `Clear search`
- **Every other user-visible sentence stays byte-identical.** No existing string is edited, deleted or re-worded. The component row's subtitle reuses the existing `"Part of $parent"` phrasing already rendered at `AssetsScreen.kt:116` and `AssetDetailScreen.kt:590` — the same words, in a third place, not a new sentence. `"No schedule yet"`, `"Current"`, `"Nothing here yet"`, `"Add your first asset"`, `"Scan a tag"`, `"No backup yet"`, `"Tags survive a phone change only if you have one."`, `"Export now"`, `"READY TO SCAN"`, `"READING TAG"`, `"Hold the top of your phone near the equipment tag."`, `"Read / inspect tag"`, `"Write a tag"`, the three `"… needs the app's own window."` / `"This phone has no NFC hardware."` / `"NFC is turned off. Enable it in system settings, then come back."` lines and every sentence in `TagResultSheet.kt` are unchanged and are re-rendered from the same call sites.
- **No new glyph asset.** `Icons.Outlined.Search` and `Icons.Outlined.Clear` both ship in `material-icons-core`, which is already on the app's compile classpath (`app/build.gradle.kts:116`); verified present as `androidx/compose/material/icons/outlined/SearchKt.class` and `ClearKt.class`. Nothing is added to `ServiceTagIcons` and no vector drawable is added to `res/`.
- **`libs/` is untouched.** No file under `libs/nfc-tag-core` is modified or re-pinned; the gitlink stays at `7e0377a`. `NfcReaderModeSession` keeps its exact shape — `start()`, `stop()`, `available`, `enabled`, callback taken at construction — and the whole of #37's fix is in the app's *use* of it.
- **No schema or format bump.** `AppDatabase` stays at 5, `AppGraph.SCHEMA_VERSION` stays 5, `BackupManifest.formatVersion` stays 5, `app/schemas/` is not re-exported, no migration is added or edited. Neither issue touches `:core`, the Room layer, the backup path or the manifest.
- **`:core` is untouched.** Both fixes live in `:app`. No file under `core/src` is modified; `AssetRepository` keeps `all()`, `get()`, `observeAll()`, `upsert()`, `delete()`, `deleteAll()` exactly as they are, and the dashboard filter is a pass over rows the existing `observeAll()` already delivers — no new query, no new port member.
- **The manifest is untouched.** ServiceTag's only NFC filter stays the exact external-type path `vnd.android.nfc://ext/${ndefTagPath}`; #37 is not fixed by claiming anyone else's tag.
- **Version.** `app/build.gradle.kts`: `versionCode` 8 → **9**, `versionName` "2.6" → **"2.7"**. Nothing else in that file changes.
- **Commits.** One commit per task, on `master`. Single casual subject line; **no body, no trailers, no attribution of any kind** (no `Co-Authored-By`, no `Generated-with`). Author GonzRon.
- **No personal data** in tracked files or reports: `~` for the home directory, `emulator-5554` only, no device ids, no tag UIDs, no note ids, no e-mail addresses. Test fixtures use invented asset names (`Hot tub`, `Circulation pump`, `Pool pump`, `Mower`).
- **No instrumented suite on the phone.** Every `connectedDebugAndroidTest`, `installDebug` and `adb` call carries `ANDROID_SERIAL=emulator-5554` on the same command line. The suites wipe app data, which is why. The phone's data is never touched by this work.
- **The emulator has no NFC**, so no test here produces a tag. Every reader-mode assertion is made against a counting `ReaderModeControl` — the number of `enableReaderMode`/`disableReaderMode` calls the app makes, and which screen a delivered handle reaches. The two things only a phone can show are the physical-gate rows **R1** and **R2** added in Task 3.
- **Per-task gate**, all four tasks: `./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain`. `:app` is never knowingly red between tasks.
- **Not in this change:** no `git push`, no tag, no `gh` mutation, no phone install, no physical NFC, no edit to `docs/architecture/product-split-evidence.md` (the controller writes evidence after the proofs), no edit under `docs/design/**` (history — never rewritten), no schedules work (Phase 3), no change to the ambient trampoline (`NfcDispatchActivity`, `MainActivity`).

## File map

```
app/src/main/kotlin/com/loosecannon/servicetag/
  ui/dashboard/DashboardViewModel.kt    MODIFY  DashboardRow, query flow, matches(), anyInService
  ui/dashboard/DashboardScreen.kt       MODIFY  SearchBox, hint, empty-match line, CurrentRow(row)
  ui/nfc/ReaderMode.kt                  NEW     ReaderModeControl, ReaderMode, SessionControl,
                                                rememberReaderMode, TagSinkEffect
  ui/nav/Route.kt                       MODIFY  Route.readsTags()
  ui/nav/ServiceTagRoot.kt              MODIFY  one ReaderMode + the hold effect; Scan/WriteTag entries
  ui/scan/ScanScreen.kt                 MODIFY  no session of its own; the result sheet in place
  ui/scan/WriteTagScreen.kt             MODIFY  no session of its own
  ui/scan/{ScanViewModels,TagResultSheet,TagResultWire,TagWriteController}.kt   UNTOUCHED
  ui/asset/{AssetDetailScreen,AssetViewModels,AssetsScreen}.kt                  UNTOUCHED
  MainActivity.kt, nfc/NfcDispatchActivity.kt, AndroidManifest.xml              UNTOUCHED

app/src/test/kotlin/com/loosecannon/servicetag/
  ui/dashboard/DashboardViewModelTest.kt   MODIFY  4 new cases; 2 accessors follow DashboardRow
  ui/nfc/ReaderModeTest.kt                 NEW     the hold policy and the sink routing
  ui/nav/RouteTest.kt                      MODIFY  one new case
  ui/scan/{ScanViewModelTest,TagResultViewModelTest,TagWriteControllerTest}.kt  UNTOUCHED

app/src/androidTest/kotlin/com/loosecannon/servicetag/
  ui/dashboard/DashboardSearchTest.kt      NEW     the box, on the emulator
  ui/scan/ReaderModeHoldTest.kt            NEW     the hold across the tag flow, on the emulator

app/build.gradle.kts                       MODIFY  versionCode 9, versionName "2.7"
README.md                                  MODIFY  the Dashboard bullet, and one inspect sentence
docs/architecture/product-split-migration.md  MODIFY  R1 amended, R2 added, the budget note
core/**, libs/**, app/schemas/**, docs/design/**, docs/architecture/product-split-evidence.md  UNTOUCHED
```

---

### Task 1 (#39): the dashboard lists systems, and a search box reaches the components

**Files:**
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/ui/dashboard/DashboardViewModel.kt`, `app/src/main/kotlin/com/loosecannon/servicetag/ui/dashboard/DashboardScreen.kt`
- Modify tests: `app/src/test/kotlin/com/loosecannon/servicetag/ui/dashboard/DashboardViewModelTest.kt`
- New test: `app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/dashboard/DashboardSearchTest.kt`
- **Untouched, and checked at Step 6:** `app/src/main/kotlin/com/loosecannon/servicetag/ui/asset/**`, `core/**`

**Interfaces** (what the screen and the tests rely on):
- `data class DashboardRow(val asset: Asset, val parentName: String? = null)` — new, in `DashboardViewModel.kt`.
- `DashboardState(assets: List<DashboardRow>, query: String, anyInService: Boolean, hiddenComponents: Int, needsBackup: Boolean, lastBackupAt: Long?)` — `assets` changes element type from `Asset` to `DashboardRow`; `query`, `anyInService` and `hiddenComponents` are new; `needsBackup` and `lastBackupAt` are unchanged in meaning and default.
- `DashboardViewModel.onQueryChange(value: String)` and `DashboardViewModel.clearQuery()` — new. `refresh()` is unchanged.
- `internal fun Asset.matches(query: String): Boolean` — new, in `DashboardViewModel.kt`.
- Unchanged: `DashboardViewModel(assets: AssetRepository, prefs: AppPrefs)`, `DashboardViewModel(graph: AppGraph)`, `DashboardScreen(graph, onOpenAsset, onNewAsset, onBackup, onSettings, onScan)`.

**Decisions, and what lost:**

1. **Filtering is in the view model, not the screen or the query.** `DashboardState` carries the query and the already-filtered list, so the screen has no rule in it and a JVM test can pin every rule without a device. A Room `LIKE` query lost: the rows are already in hand from `observeAll()`, a second query would race the first on every keystroke, and `parentAssetId` is a column the existing flow already delivers.
2. **Which fields a query reaches: `name`, `category`, `manufacturer`, `model`, `serialNumber`, `location`.** The name is obvious; the category is already the Assets list's own subtitle, so someone who typed "pump" there expects it to work here; make, model and serial are what an owner reads off the machine's own nameplate when they cannot remember what they called it; and the location is how a component is found ("shed"). `description`, `notes`, `vendor` and `warrantyNotes` lost: they are paragraphs, and a row that shows a name and its parent could not explain a hit buried in one. Dates, prices, currencies, ids and `templateKey` lost: they are not words anyone types into a filter.
3. **Empty query shows top-level assets only; a non-empty query shows every match including components.** A component row carries its parent's name so the hit is understandable, and the parent's name is looked up across **all** rows rather than the in-service ones — a component of a retired machine is itself in service and still has to say whose component it is.
4. **The search box sits above the list, outside the `LazyColumn`.** That is `AssetsScreen`'s own shape (`Column { FilterChip(...); LazyColumn { ... } }`) and it keeps the filter visible after a scroll. Putting it inside the `LazyColumn` lost: a `TextField` in a lazy item is disposed when it scrolls out of view, which drops focus and the IME mid-word. The consequence, stated: the backup nudge no longer scrolls away either, because it stays above the list as it is today and only the rows scroll.
5. **`anyInService` is a state field, not `assets.isEmpty()`.** "Nothing here yet" and "nothing matches what you typed" are different facts, and the existing screen could only tell them apart while there was no query. Same reasoning as `AssetsScreen`'s `archivedCount`.
6. **The list order is unchanged** — whatever `AssetRepository.observeAll()` delivers, as today. Sorting the dashboard by name lost: it would change the order of the CURRENT list, which nobody asked for, and the ordering decision belongs to the schedules work in Phase 3.
7. **`DashboardRow` rather than a parallel `Map<String, String>` of parent names.** One row type keeps everything a row says in one place and the screen never performs a lookup. The cost is named and paid in Step 1: two existing assertions change `state.assets.map(Asset::name)` to `state.assets.map { it.asset.name }`. Their meaning is untouched and all four existing cases stay.
8. **No `Components` section is added to the asset detail screen** — see Step 0.

- [ ] **Step 0: Record the scope check the owner asked for. No code.**

Read and record, so the answer is evidenced rather than asserted:

```bash
sed -n '634,676p' app/src/main/kotlin/com/loosecannon/servicetag/ui/asset/AssetDetailScreen.kt
sed -n '294,315p' app/src/main/kotlin/com/loosecannon/servicetag/ui/asset/AssetViewModels.kt
grep -n 'componentsListChildrenWithOutOfRangeCounts\|componentsSectionOffersAddOnAChildlessAsset' app/src/test/kotlin/com/loosecannon/servicetag/ui/asset/AssetViewModelsTest.kt
grep -n 'aParentListsItsComponentsAndEachComponentNamesTheParent' app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/AssetModelDeviceProofTest.kt
```

Expected, and the finding to hand back: **the asset detail screen already lists a parent's components.** `ComponentsSection` (`AssetDetailScreen.kt:641`) renders a `SectionHeader(title = "Components")`, one clickable row per child carrying the child's name and `componentLine(child)` — its category and how many of its own readings are out of range — plus `+ Add component`; the empty case reads `No components`. The rows come from `AssetDetailState.components: List<ComponentRow>` built by `AssetDetailViewModel.componentsOf` (`AssetViewModels.kt:298`) over `AssetTree.children(all, id)`, and each row navigates through `onOpenAsset` (`AssetDetailScreen.kt:653`), wired in `ServiceTagRoot.kt:107` to `backStack.add(Route.AssetDetail(it))`. It is covered on the JVM by `AssetViewModelsTest.componentsListChildrenWithOutOfRangeCounts` (line 700) and `componentsSectionOffersAddOnAChildlessAsset` (line 905), and on the emulator by `AssetModelDeviceProofTest.aParentListsItsComponentsAndEachComponentNamesTheParent` (line 73). **Nothing is added, so a hidden component stays reachable without search: open its system and it is listed there.** `ComponentRow` carries `category` and `outOfRange` rather than a status word; the issue's "(name, status)" is already satisfied by the out-of-range count, which is the only status a component has before Phase 3's schedules, and inventing a second one would be a new sentence nobody ratified.

- [ ] **Step 1: Write the failing tests first.**

In `app/src/test/kotlin/com/loosecannon/servicetag/ui/dashboard/DashboardViewModelTest.kt`, add these imports to the existing block:

```kotlin
import com.loosecannon.servicetag.core.usecase.AssetCommand
```

Change the two existing accessors — the only edit to an existing case, forced by `DashboardRow`; the assertions say the same thing:

```kotlin
        assertEquals(listOf("Mower"), state.assets.map { it.asset.name })
```

in `assetsListedActiveOnly` (line 97) and in `dashboardExcludesRetired` (line 113). `Asset` stays imported — `AssetCommand`'s fixtures still name it? No: after this edit `Asset` is unused in the file, so **delete** the `import com.loosecannon.servicetag.core.model.Asset` line as well.

Then add the four new cases at the end of the class, before the closing brace:

```kotlin
    /**
     * #39 — the dashboard lists systems. A component is part of one of them and is listed there,
     * on its parent's own screen; listing it here again as though it were a system of its own is
     * what the owner asked to stop. It is still counted, so the screen can say where it went.
     */
    @Test fun componentsAreNotListedUntilTheyAreSearchedFor() = runTest {
        val tub = graph.createAsset.run(AssetCommand(name = "Hot tub", category = "Water"))
        graph.createAsset.run(AssetCommand(name = "Circulation pump", parentAssetId = tub.id))

        val vm = viewModel()
        backgroundScope.launch { vm.state.collect() }

        val hidden = vm.state.first { it.assets.isNotEmpty() }
        assertEquals(listOf("Hot tub"), hidden.assets.map { it.asset.name })
        assertEquals(1, hidden.hiddenComponents)
        assertTrue(hidden.anyInService)
        assertNull("a top-level asset is nobody's component", hidden.assets.single().parentName)

        // One keystroke away, and the hit names the system it belongs to.
        vm.onQueryChange("circ")
        val found = vm.state.first { it.query == "circ" && it.assets.isNotEmpty() }
        assertEquals(listOf("Circulation pump"), found.assets.map { it.asset.name })
        assertEquals("Hot tub", found.assets.single().parentName)
    }

    /**
     * #39 — the six fields a query reaches are the ones an owner types: the name, the category the
     * Assets list already shows, and the four they read off the machine itself. The prose fields
     * are deliberately out, because a row showing a name and its parent could not explain a hit
     * buried in a paragraph.
     */
    @Test fun theQueryReachesTheFieldsSomeoneWouldType() = runTest {
        graph.createAsset.run(
            AssetCommand(
                name = "Circulation pump",
                category = "Water",
                manufacturer = "Hayward",
                model = "SP2610X15",
                serialNumber = "A1B2C3",
                location = "Pool shed",
                notes = "the impeller was replaced last spring",
            ),
        )

        val vm = viewModel()
        backgroundScope.launch { vm.state.collect() }
        vm.state.first { it.assets.isNotEmpty() }

        // Case-insensitive substring, on every one of the six.
        for (typed in listOf("circ", "WATER", "hayward", "sp2610", "b2c3", "pool shed")) {
            vm.onQueryChange(typed)
            assertEquals(
                "typing \"$typed\" should find the pump",
                1,
                vm.state.first { it.query == typed }.assets.size,
            )
        }

        // And not on the prose.
        vm.onQueryChange("impeller")
        val none = vm.state.first { it.query == "impeller" }
        assertTrue("notes are not searched", none.assets.isEmpty())
        assertTrue("but the install is not empty either", none.anyInService)
    }

    /** #39 — clearing the box puts the components back out of sight, in one call. */
    @Test fun clearingTheQueryPutsTheComponentsBackOutOfSight() = runTest {
        val tub = graph.createAsset.run(AssetCommand(name = "Hot tub", category = "Water"))
        graph.createAsset.run(AssetCommand(name = "Circulation pump", parentAssetId = tub.id))

        val vm = viewModel()
        backgroundScope.launch { vm.state.collect() }
        vm.state.first { it.assets.isNotEmpty() }

        vm.onQueryChange("circ")
        assertEquals(listOf("Circulation pump"), vm.state.first { it.query == "circ" }.assets.map { it.asset.name })

        vm.clearQuery()
        val cleared = vm.state.first { it.query.isEmpty() && it.assets.isNotEmpty() }
        assertEquals(listOf("Hot tub"), cleared.assets.map { it.asset.name })
    }

    /**
     * #39 — "nothing here yet" and "nothing matches that" are different facts. The screen can only
     * tell them apart if the state says whether there is anything in service at all.
     */
    @Test fun aSearchThatFindsNothingIsNotAFirstRun() = runTest {
        graph.createAsset.run("Mower", "Yard")

        val vm = viewModel()
        backgroundScope.launch { vm.state.collect() }
        vm.state.first { it.assets.isNotEmpty() }

        vm.onQueryChange("zzz")
        val nothing = vm.state.first { it.query == "zzz" }
        assertTrue(nothing.assets.isEmpty())
        assertTrue(nothing.anyInService)
    }
```

Run: `./gradlew :app:testDebugUnitTest --tests '*DashboardViewModelTest' --console=plain` → **FAILS to compile** (`DashboardRow`, `query`, `anyInService`, `hiddenComponents`, `onQueryChange`, `clearQuery` do not exist). That is the red.

- [ ] **Step 2: The view model.**

Replace `DashboardViewModel.kt` lines 21–69 (from the `DashboardState` KDoc to the end of the file) with:

```kotlin
/**
 * One row of the dashboard list: the asset, and — for a component a search has surfaced — the name
 * of the asset it is part of. A component row on its own would be a name with no home, and the
 * whole point of letting the search reach components is that the hit can be understood.
 */
data class DashboardRow(val asset: Asset, val parentName: String? = null)

/**
 * What the dashboard draws. [needsBackup] is deliberately not `lastBackupAt == null` at the call
 * site: the screen should never have to work out what the absence of an instant means. [assets] is
 * likewise already filtered — by lifecycle, by [query], and by whether a row is a component of
 * something else — so no rule about what belongs in the list lives on the screen.
 *
 * [anyInService] and [hiddenComponents] exist because an empty list has three different meanings.
 * Nothing in service at all is a first run and gets the empty state; nothing *matching* is a search
 * that found nothing; and a list that is short because the components are on their own systems is
 * neither, and says so once.
 */
data class DashboardState(
    val assets: List<DashboardRow> = emptyList(),
    /** What the search box holds, verbatim. Blank means "the systems, and not their parts". */
    val query: String = "",
    /** Whether anything is in service at all, before [query] is applied. */
    val anyInService: Boolean = false,
    /** How many in-service components a blank query is not listing. */
    val hiddenComponents: Int = 0,
    val needsBackup: Boolean = false,
    val lastBackupAt: Long? = null,
)

/**
 * The fields a search reaches, and why these six (#39): the name; the category the Assets list
 * already shows as its own subtitle, so someone who typed "pump" there expects it to work here;
 * and the four an owner reads off the machine itself when they cannot remember what they called it
 * — make, model, serial, and where the thing is. `description`, `notes`, `vendor` and the warranty
 * prose are deliberately out: they are paragraphs, and a row that shows a name and its parent
 * could not explain a hit buried in one.
 */
private val SEARCHED_FIELDS: List<(Asset) -> String> = listOf(
    Asset::name,
    Asset::category,
    Asset::manufacturer,
    Asset::model,
    Asset::serialNumber,
    Asset::location,
)

/** Case-insensitive substring over [SEARCHED_FIELDS]. A blank query matches everything. */
internal fun Asset.matches(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return SEARCHED_FIELDS.any { field -> field(this).contains(needle, ignoreCase = true) }
}

/**
 * The landing screen's state: the assets that are in service, what the search box holds, and
 * whether a backup has ever been taken. Three different kinds of fact, so they arrive three
 * different ways — the rows from live repository flows, the query from the screen, the backup
 * instant from preferences, which nothing observes.
 *
 * [refresh] is what closes that last gap. The screen calls it when it comes back into composition,
 * so an export that happened while the user was on the backup screen puts the nudge out on the next
 * emission rather than on the next process start.
 *
 * The initial state says `needsBackup = false`: a nudge that flashes up before the preferences
 * have been read and then disappears is worse than a nudge that arrives a frame late.
 */
class DashboardViewModel(
    assets: AssetRepository,
    private val prefs: AppPrefs,
) : ViewModel() {

    constructor(graph: AppGraph) : this(graph.assets, graph.prefs)

    private val refreshes = MutableStateFlow(0)
    private val queries = MutableStateFlow("")

    val state: StateFlow<DashboardState> =
        combine(assets.observeAll(), refreshes, queries) { rows, _, query ->
            val last = prefs.lastBackupAt
            val active = rows.filter { it.status == AssetStatus.ACTIVE }
            val inService = active.filterNot { it.isRetired }
            // Parent names come from every row, not just the in-service ones: a component of a
            // retired machine is itself in service and still has to say whose component it is.
            val byId = rows.associateBy { it.id }
            val matching = inService.filter { it.matches(query) }
            // The list is the systems. A blank query keeps the parts on the systems they belong to
            // (#39); typing brings them back, because that is the one place a hidden part is asked
            // for by name. The order is whatever the repository delivered, exactly as before.
            val shown = if (query.isBlank()) matching.filter { it.parentAssetId == null } else matching
            DashboardState(
                assets = shown.map { row ->
                    DashboardRow(asset = row, parentName = row.parentAssetId?.let { byId[it]?.name })
                },
                query = query,
                anyInService = inService.isNotEmpty(),
                hiddenComponents = inService.count { it.parentAssetId != null },
                // An empty install has nothing to lose, and a nudge over an empty dashboard is
                // noise: the offer only means something once there is something to survive the
                // phone change.
                needsBackup = last == null && active.isNotEmpty(),
                lastBackupAt = last,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), DashboardState())

    /** Re-read the preferences and emit. Cheap: it is one `SharedPreferences` lookup. */
    fun refresh() = refreshes.update { it + 1 }

    /**
     * What the search box holds. Filtering is a pass over rows already in hand, so a keystroke runs
     * no query and needs no debounce; the one cost is that the preference lookup above happens
     * again per keystroke, which is the same single lookup [refresh] is built on.
     */
    fun onQueryChange(value: String) { queries.value = value }

    /** The clear action. Separate from `onQueryChange("")` so the screen states its intent. */
    fun clearQuery() { queries.value = "" }
}
```

The `SUBSCRIPTION_GRACE_MS` constant and the existing import block stay; the imports are already exactly what this needs (`Asset`, `AssetStatus`, `isRetired`, `AssetRepository`, `AppGraph`, `AppPrefs`, `MutableStateFlow`, `SharingStarted`, `StateFlow`, `combine`, `stateIn`, `update`) — `combine` with three flows is the same overload family, so no import changes.

- [ ] **Step 3: The screen.**

In `DashboardScreen.kt`, add three imports to the existing block, each in alphabetical position:

```kotlin
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.OutlinedTextField
```

Replace the `Scaffold` body (lines 84–104, from `) { padding ->` through the closing brace of the `Scaffold` call) with:

```kotlin
    ) { padding ->
        // The nudge and the box stay put; only the rows scroll. A search field inside a lazy list
        // is disposed the moment it scrolls out of view, which drops focus and the keyboard
        // mid-word — and a filter you have to scroll back to find is not a quick one.
        Column(modifier = Modifier.padding(padding)) {
            if (state.needsBackup) {
                BackupNudge(onExport = onBackup, modifier = Modifier.padding(16.dp))
            }
            if (!state.anyInService) {
                FirstRun(onNewAsset = onNewAsset, onScan = onScan)
            } else {
                SearchBox(
                    query = state.query,
                    onQueryChange = model::onQueryChange,
                    onClear = model::clearQuery,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                // Said once, and only while there is something it explains: a list that is short
                // because the parts are on their systems should say where they went.
                if (state.query.isBlank() && state.hiddenComponents > 0) {
                    QuietLine(
                        text = "Components are listed on the asset they belong to. Search to find one.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (state.assets.isEmpty()) {
                    QuietLine(text = "Nothing matches that.", modifier = Modifier.padding(16.dp))
                } else {
                    SectionHeader(title = "Current", modifier = Modifier.padding(horizontal = 16.dp))
                    LazyColumn {
                        items(state.assets, key = { it.asset.id.value }) { row ->
                            CurrentRow(row = row, onClick = { onOpenAsset(row.asset.id.value) })
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
```

Add `SearchBox` immediately after `DashboardScreen`, before `BackupNudge`:

```kotlin
/**
 * The quick filter (#39). An `OutlinedTextField`, not a Material 3 `SearchBar`: a `SearchBar`
 * expands over the screen and owns a results surface of its own, and what this needs is one line
 * that narrows the list already underneath it. The clear action appears only once there is
 * something to clear, so a first look is not two glyphs and a hint.
 */
@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        shape = ControlShape,
        placeholder = { Text("Search assets and components") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}
```

Replace `CurrentRow` (lines 159–195) with the row-taking version. Only the parameter and the second line change:

```kotlin
/**
 * One asset in service. 28dp glyph · text block · chevron, 11dp vertical padding (G1 §1.2). The
 * glyph is `onSurfaceVariant`, not a state colour: until schedules exist there is no state to
 * carry, and a row that looked OK by colour would be claiming something it does not know.
 *
 * A component — which is only ever here because a search asked for it (#39) — says whose component
 * it is in place of the schedule line. Neither row has a schedule until Phase 3, and of the two
 * facts the parentage is the one that makes the hit make sense.
 */
@Composable
private fun CurrentRow(row: DashboardRow, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.asset.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            QuietLine(row.parentName?.let { parent -> "Part of $parent" } ?: "No schedule yet")
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

`import com.loosecannon.servicetag.core.model.Asset` (line 39) is now unused — **delete** it.

Run: `./gradlew :app:testDebugUnitTest --tests '*DashboardViewModelTest' --console=plain` → **BUILD SUCCESSFUL**, 8 cases (the 4 that existed, the 4 new).

- [ ] **Step 4: The emulator test for the box.**

Create `app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/dashboard/DashboardSearchTest.kt`:

```kotlin
package com.loosecannon.servicetag.ui.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loosecannon.servicetag.core.usecase.AssetCommand
import com.loosecannon.servicetag.ui.app
import com.loosecannon.servicetag.ui.awaitText
import com.loosecannon.servicetag.ui.clearInstall
import com.loosecannon.servicetag.ui.theme.ServiceTagTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.7 (#39) — the dashboard lists systems, and the box is how a part of one is reached from here.
 * The JVM cases pin every rule; what only a device can show is that the field takes a keystroke,
 * that the list redraws from it, and that the clear glyph puts it all back.
 *
 * Emulator only — the suite wipes app data.
 */
@RunWith(AndroidJUnit4::class)
class DashboardSearchTest {

    @get:Rule val rule = createComposeRule()

    @Before fun freshInstall() = clearInstall()

    @Test fun aComponentIsHiddenUntilItIsSearchedForAndThenNamesItsSystem() {
        val graph = app.graph
        runBlocking {
            val tub = graph.createAsset.run(AssetCommand(name = "Hot tub", category = "Water"))
            graph.createAsset.run(AssetCommand(name = "Circulation pump", parentAssetId = tub.id))
        }

        rule.setContent {
            ServiceTagTheme {
                DashboardScreen(
                    graph = graph,
                    onOpenAsset = {},
                    onNewAsset = {},
                    onBackup = {},
                    onSettings = {},
                    onScan = {},
                )
            }
        }

        // The system is listed; its pump is not, and the screen says where it is instead.
        rule.awaitText("Hot tub")
        rule.onAllNodesWithText("Circulation pump").assertCountEquals(0)
        rule.awaitText("Components are listed on the asset they belong to. Search to find one.")

        // One keystroke away. The hit names its system, and the system itself drops out because it
        // does not match — which is what proves the list is filtered and not merely extended.
        rule.onNode(hasSetTextAction()).performTextInput("circ")
        rule.awaitText("Circulation pump")
        rule.awaitText("Part of Hot tub")
        rule.onAllNodesWithText("Hot tub").assertCountEquals(0)

        rule.onNodeWithContentDescription("Clear search").performClick()
        rule.awaitText("Hot tub")
        rule.onAllNodesWithText("Circulation pump").assertCountEquals(0)
    }
}
```

`onAllNodesWithText` matches exactly, not by substring, so `"Part of Hot tub"` is not a `"Hot tub"` node and the third assertion is honest.

- [ ] **Step 5: The gate.**

Run: `./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`. `app/build/reports/tests/testDebugUnitTest/` shows `DashboardViewModelTest` at 8/8 and no other class changed.

- [ ] **Step 6: Verify the blast radius.**

Run: `git diff --stat -- core libs app/schemas docs app/src/main/AndroidManifest.xml app/build.gradle.kts` → **no output**.
Run: `git diff --name-only` → exactly four paths: the two dashboard files, `DashboardViewModelTest.kt`, `DashboardSearchTest.kt`.
Run: `git grep -c 'Part of ' -- app/src/main` → 3 files (`AssetsScreen.kt`, `AssetDetailScreen.kt`, `DashboardScreen.kt`) — the same words in a third place, not a new sentence.
Run: `git grep -n 'SearchBar' -- app/src/main | wc -l` → `0`.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "dashboard: keep the parts off the list, add a quick search"
```

---

### Task 2 (#37, R1): one reader mode for the app, and the inspect screen keeps it

**Files:**
- New: `app/src/main/kotlin/com/loosecannon/servicetag/ui/nfc/ReaderMode.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/ui/nav/Route.kt`, `.../ui/nav/ServiceTagRoot.kt`, `.../ui/scan/ScanScreen.kt`, `.../ui/scan/WriteTagScreen.kt`
- New test: `app/src/test/kotlin/com/loosecannon/servicetag/ui/nfc/ReaderModeTest.kt`, `app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/scan/ReaderModeHoldTest.kt`
- Modify test: `app/src/test/kotlin/com/loosecannon/servicetag/ui/nav/RouteTest.kt`
- **Untouched, and checked at Step 6:** `libs/nfc-tag-core/**`, `app/src/main/kotlin/com/loosecannon/servicetag/ui/scan/{ScanViewModels,TagResultSheet,TagResultWire,TagWriteController}.kt`, `.../MainActivity.kt`, `.../nfc/NfcDispatchActivity.kt`, `app/src/main/AndroidManifest.xml`, `app/src/test/.../ui/scan/*`, `app/src/androidTest/.../ui/scan/{PreSplitLinkTagSheetTest,WriteTagScreenConsentWordingTest}.kt`

**The root cause, from the code.** Nothing calls `stop()` after a read: `git grep -n 'session?.stop()\|\.stop()' -- app/src/main` finds exactly two call sites, the `onPauseOrDispose` blocks of `ScanScreen.kt:80` and `WriteTagScreen.kt:76`, and `ScanViewModel` has no session reference at all. So it is not a one-shot stop. It is the navigation: `ScanViewModel.onTag` finishes a read and emits `ScanEvent.Show(route)` (`ScanViewModels.kt:113`), `ScanScreen.kt:86` hands that to `onResolved`, and `ServiceTagRoot.kt:188` runs `backStack.add(it)` — which makes `Route.TagResult` the top entry, so `NavDisplay` shows that entry's content and the `Route.Scan` entry's content leaves composition (and, where a decorator supplies a per-entry lifecycle, is paused first). Either ends `LifecycleResumeEffect(session)` and runs its `onPauseOrDispose { session?.stop() }` → `NfcAdapter.disableReaderMode(activity)`. Reader mode was the only thing overriding the platform's dispatch, so the tag still against the phone is re-discovered under normal dispatch; ServiceTag's sole NFC filter is the exact external-type path `vnd.android.nfc://ext/com.loosecannon.servicetag:tag` (`AndroidManifest.xml:52`), which a NoteTag tag does not match, so the dispatch goes to NoteTag. The ~200 ms is the read plus the resolve plus one frame.

**Decisions, and what lost:**

1. **One `NfcReaderModeSession` per activity, built in the nav shell, with the two screens installing a *tag sink* instead of a session.** Reader mode is an activity-wide switch — `enableReaderMode` takes an `Activity` and `disableReaderMode` turns off whatever is on — so two screens owning one switch each is R1's defect waiting to happen: on an overlapping transition the leaving screen's `stop()` can run after the arriving screen's `start()` and leave the survivor displayed with NFC handed back. With one session the count is one by construction, and "which screen reads" becomes a routing question the app answers itself. Re-pointing the library session's callback lost: `NfcReaderModeSession` takes `onTag` at construction and offers no setter, and `libs/` may not change.
2. **The hold is scoped to the routes that read tags — `Route.Scan` and `Route.WriteTag` — and is released only when the top of the back stack is neither.** A move between them changes nothing at all: no `enableReaderMode`, no `disableReaderMode`, no window. Two alternatives lost. *Until the tag leaves the field* is not implementable: `NfcReaderModeSession` exposes `start`, `stop`, `available` and `enabled` and nothing else — there is no presence callback and no `FLAG_READER_PRESENCE_CHECK_DELAY` surface — so "the tag has gone" is not a fact the app can learn without changing the pinned library. *Hold it for the whole foreground* lost because it breaks the app's spine: the ambient trampoline is how "tap a tag anywhere and the phone opens the right place" works, and reader mode on the Dashboard would swallow that tap.
3. **The read's answer is drawn on the inspect screen, not pushed as a route.** This is what actually fixes #37, because it means the inspect screen never leaves composition during an inspect and so has nothing to release. `TagResultSheet` is already a composable taking `(format, key)` and is reused verbatim; `Route.TagResult` and its entry stay exactly as they are, because that route is where the *ambient* trampoline lands and changing it would change ambient behaviour nobody asked about. What the sheet decides still navigates, through the same three callbacks the entry wired.
4. **`Route.TagResult` is deliberately *not* in the hold set**, and that is a decision, not an omission. Putting it in would fix the reported case the other way round but open a spin: the sheet auto-opens a bound asset (`TagResultSheet.kt:48`), which leaves the flow, which would release reader mode with the tag still on the phone, which would re-dispatch to ServiceTag's own trampoline, which would push another `TagResult`, which would hold again — a loop for as long as the tag sits there. Keeping the route out of the set leaves the ambient path byte-for-byte as it is at 2.6.
5. **A tag that arrives with no sink installed is dropped**, not queued and not handed back. Dropping costs the user one more hold; handing reader mode back mid-transition is the dispatch this whole task is about. The window is a few frames of a nav transition.
6. **`ReaderModeControl` is an interface and `SessionControl` is the only production implementation.** That is the seam that makes the hold policy and the routing provable on a JVM with no adapter and on an emulator with no NFC — the counts of `enableReaderMode`/`disableReaderMode` calls are exactly what regressed.
7. **The result is remembered as two saveable strings**, not a `Route.TagResult` in `rememberSaveable`: the route is `@Serializable`, not `Parcelable`, so a `Saver` would have to be written for it, and the two strings are precisely what the route carried.

**Interfaces** (what the screens and the tests rely on):
- `interface ReaderModeControl { val available: Boolean; val enabled: Boolean; fun start(); fun stop() }` — new.
- `class ReaderMode(controlFor: ((TagHandle) -> Unit) -> ReaderModeControl?)` — new; members `present`, `available`, `enabled`, `holding`, `sinkCount`, `hold(Boolean)`, `install((TagHandle) -> Unit)`, `uninstall((TagHandle) -> Unit)`, `deliver(TagHandle)`.
- `class SessionControl(activity: Activity, onTag: (TagHandle) -> Unit) : ReaderModeControl` — new.
- `@Composable fun rememberReaderMode(): ReaderMode` and `@Composable fun TagSinkEffect(readerMode: ReaderMode, onTag: (TagHandle) -> Unit)` — new.
- `internal fun Route.readsTags(): Boolean` — new, in `Route.kt`.
- `ServiceTagRoot(graph, deepLinks, snackbars, readerMode: ReaderMode = rememberReaderMode())` — one parameter added, defaulted, so `MainActivity` is untouched.
- `ScanScreen(graph, readerMode, onOpenAsset, onNewAsset, onWriteTag, onBack)` — `onResolved: (Route) -> Unit` is replaced by the three the sheet needs.
- `WriteTagScreen(graph, readerMode, key, onDone)` — one parameter added.
- Unchanged: `ScanViewModel`, `ScanEvent`, `TagResultViewModel`, `TagResultSheet`, `WriteTagViewModel`, `TagWriteController`, `Route.TagResult` and its entry, `TagResultWire`, `NfcDispatchActivity`, `MainActivity`.

- [ ] **Step 1: Write the failing tests first.**

Create `app/src/test/kotlin/com/loosecannon/servicetag/ui/nfc/ReaderModeTest.kt`:

```kotlin
package com.loosecannon.servicetag.ui.nfc

import com.loosecannon.nfc.tagcore.android.TagHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hold policy and the tag routing, with no Activity and no adapter (2.7). What these cases pin
 * is the number of `enableReaderMode`/`disableReaderMode` calls the app makes and which screen a
 * delivered tag reaches — the two facts #37 and runbook R1 turn on, and the two the emulator
 * cannot show because it has no NFC.
 */
class ReaderModeTest {

    private val control = CountingControl()
    private val readerMode = ReaderMode { _ -> control }

    /**
     * #37 and R1 — the inspect screen and the write screen are both inside the tag flow, so moving
     * from one to the other is not a hand-over: reader mode is never switched off with a tag
     * possibly still against the phone, and nothing has to race anything.
     */
    @Test fun aMoveWithinTheTagFlowIsNotAHandOver() {
        readerMode.hold(true)      // the inspect screen comes up
        readerMode.hold(true)      // ... and the write screen replaces it
        assertEquals(1, control.starts)
        assertEquals(0, control.stops)
        assertTrue(readerMode.holding)

        readerMode.hold(false)     // the flow is left
        assertEquals(1, control.starts)
        assertEquals(1, control.stops)
        assertFalse(readerMode.holding)
    }

    /** Leaving is idempotent too: a pause after a dispose must not stop a stopped session. */
    @Test fun leavingTwiceStopsOnce() {
        readerMode.hold(true)
        readerMode.hold(false)
        readerMode.hold(false)
        assertEquals(1, control.stops)
    }

    /** R1 — the arriving screen installs first, and it is the one that reads. */
    @Test fun theNewestSinkGetsTheTag() {
        val seen = mutableListOf<String>()
        val inspect: (TagHandle) -> Unit = { seen += "inspect" }
        val write: (TagHandle) -> Unit = { seen += "write" }

        readerMode.install(inspect)
        readerMode.install(write)
        assertEquals("both screens are up for the length of the transition", 2, readerMode.sinkCount)
        readerMode.deliver(Handle)

        readerMode.uninstall(inspect)
        assertEquals(1, readerMode.sinkCount)
        readerMode.deliver(Handle)

        assertEquals(listOf("write", "write"), seen)
    }

    /** R1, the other order — the leaving screen goes first, and the survivor still reads. */
    @Test fun theSurvivorReadsWhenTheLeavingScreenGoesFirst() {
        val seen = mutableListOf<String>()
        val inspect: (TagHandle) -> Unit = { seen += "inspect" }
        val write: (TagHandle) -> Unit = { seen += "write" }

        readerMode.install(inspect)
        readerMode.uninstall(inspect)
        readerMode.install(write)
        readerMode.deliver(Handle)

        assertEquals(listOf("write"), seen)
    }

    /**
     * The gap between those two orders. A tag that arrives with nobody listening is dropped, and
     * reader mode stays on: dropping costs the user one more hold, while handing NFC back to the
     * platform — the only other thing the app could do — is the dispatch #37 is about.
     */
    @Test fun aTagWithNoSinkIsDroppedAndReaderModeStaysOn() {
        readerMode.hold(true)
        readerMode.deliver(Handle)
        assertTrue(readerMode.holding)
        assertEquals(0, control.stops)
    }

    /** No Activity, no reader mode — and the three lines the screens draw say so, not throw. */
    @Test fun withNoActivityThereIsNoReaderModeAndNothingThrows() {
        val none = ReaderMode { _ -> null }
        none.hold(true)
        none.install { }
        none.deliver(Handle)
        assertFalse(none.present)
        assertFalse(none.available)
        assertFalse(none.enabled)
        assertFalse(none.holding)
    }

    private object Handle : TagHandle {
        override val uid: String = "04a1"
    }

    private class CountingControl : ReaderModeControl {
        var starts = 0
        var stops = 0
        override val available: Boolean = true
        override val enabled: Boolean = true
        override fun start() { starts++ }
        override fun stop() { stops++ }
    }
}
```

In `app/src/test/kotlin/com/loosecannon/servicetag/ui/nav/RouteTest.kt`, add one case before the closing brace:

```kotlin
    /**
     * 2.7 (#37) — reader mode belongs to the two screens that read tags, and is held while one of
     * them is on top. `TagResult` is not one of them: it reads nothing, it is where the ambient
     * trampoline lands, and the inspect screen no longer pushes it. Adding it here would put
     * reader mode on over an ambient result and release it again the moment the sheet opened its
     * asset — with the tag still on the phone, which is the dispatch this release removed.
     */
    @Test fun onlyTheTagScreensHoldReaderMode() {
        assertTrue(Route.Scan.readsTags())
        assertTrue(Route.WriteTag("asset", "a1", null).readsTags())
        assertTrue(Route.WriteTag("none", null, null).readsTags())
        assertFalse(Route.TagResult("V1", "k").readsTags())
        assertFalse(Route.Dashboard.readsTags())
        assertFalse(Route.Assets.readsTags())
        assertFalse(Route.AssetDetail("a1").readsTags())
        assertFalse(Route.Settings.readsTags())
    }
```

Run: `./gradlew :app:testDebugUnitTest --tests '*ReaderModeTest' --tests '*RouteTest' --console=plain` → **FAILS to compile** (`ReaderMode`, `ReaderModeControl`, `readsTags` do not exist). That is the red.

- [ ] **Step 2: The reader-mode owner.**

Create `app/src/main/kotlin/com/loosecannon/servicetag/ui/nfc/ReaderMode.kt`:

```kotlin
package com.loosecannon.servicetag.ui.nfc

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.loosecannon.nfc.tagcore.android.NfcReaderModeSession
import com.loosecannon.nfc.tagcore.android.NfcTagHandle
import com.loosecannon.nfc.tagcore.android.TagHandle
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The reader-mode switch this app needs, as an interface, so the policy below can be proved without
 * an Activity and on a device with no NFC. [SessionControl] is the only production implementation.
 */
interface ReaderModeControl {
    val available: Boolean
    val enabled: Boolean
    fun start()
    fun stop()
}

/**
 * The activity's one reader-mode session, and the routing in front of it (2.7 — issue #37 and
 * runbook R1).
 *
 * Reader mode is an activity-wide switch: `NfcAdapter.enableReaderMode` takes an `Activity` and
 * `disableReaderMode` turns off whatever is on. Two screens that each owned a session therefore
 * shared one switch, and on a nav transition that overlapped them the leaving screen's `stop()`
 * could run after the arriving screen's `start()` and leave the survivor displayed with NFC handed
 * back to the system (R1). There is exactly one session here instead, built once per activity, and
 * a screen says *where tags go* rather than whether NFC is on: it installs a sink while it is
 * resumed and takes it away again when it is not.
 *
 * [hold] is the other half, and it is #37. The library's session takes its callback at construction
 * and offers no "the tag has left the field" signal, so a read cannot honestly be made one-shot and
 * the hold has to be scoped to something a screen can see. It is scoped to the screens that read
 * tags (`Route.readsTags`): while one of them is on top of the back stack the switch stays on, and
 * a move from one to the other makes no platform call at all. Releasing it while a tag was still
 * against the phone is what let the platform re-discover that tag and dispatch it to another app
 * from inside ServiceTag's own inspect screen.
 *
 * @param controlFor builds the control around the tag callback. `null` when there is no Activity to
 *   own reader mode, which is the "needs the app's own window" line the two screens draw.
 */
class ReaderMode(controlFor: ((TagHandle) -> Unit) -> ReaderModeControl?) {

    /**
     * Declared before [control], because the lambda handed to [controlFor] reads it. The callback
     * arrives on a binder thread — the library's own documented contract — so this is copy-on-write
     * and [deliver] never takes a lock.
     */
    private val sinks = CopyOnWriteArrayList<(TagHandle) -> Unit>()

    private val control: ReaderModeControl? = controlFor { tag -> deliver(tag) }

    /** Main-thread only: the one caller is the nav shell's `LifecycleResumeEffect`. */
    private var on = false

    /** Whether an Activity owns reader mode at all. */
    val present: Boolean get() = control != null

    val available: Boolean get() = control?.available == true
    val enabled: Boolean get() = control?.enabled == true

    /** Whether reader mode is on right now. Read by the tests, and by nothing else. */
    val holding: Boolean get() = on

    /** How many screens are asking for tags: one normally, two for the length of a transition. */
    val sinkCount: Int get() = sinks.size

    /**
     * Turns reader mode on or off. Idempotent, which is the point: a move within the tag flow is
     * not a hand-over, so it must not become a `stop()` and a `start()` with a tag in between.
     */
    fun hold(wanted: Boolean) {
        val switch = control ?: return
        if (wanted == on) return
        on = wanted
        if (wanted) switch.start() else switch.stop()
    }

    fun install(sink: (TagHandle) -> Unit) { sinks.add(sink) }

    fun uninstall(sink: (TagHandle) -> Unit) { sinks.remove(sink) }

    /**
     * Hands [tag] to the newest installed sink — the screen on top. With none installed the tag is
     * dropped, which is the safe answer: dropping it costs the user one more hold, while handing
     * reader mode back to the platform is the dispatch #37 is about.
     */
    fun deliver(tag: TagHandle) { sinks.lastOrNull()?.invoke(tag) }
}

/**
 * `NfcReaderModeSession` as a [ReaderModeControl]. The library is pinned at `nfc-tag-core-v0.1.0`
 * and unchanged: it takes its callback at construction, which is exactly why the app builds one
 * session per activity and routes tags itself.
 */
class SessionControl(activity: Activity, onTag: (TagHandle) -> Unit) : ReaderModeControl {

    private val session = NfcReaderModeSession(activity) { tag -> onTag(NfcTagHandle(tag)) }

    override val available: Boolean get() = session.available
    override val enabled: Boolean get() = session.enabled
    override fun start() = session.start()
    override fun stop() = session.stop()
}

/** The activity's [ReaderMode], built once. No Activity means no session and no reader mode. */
@Composable
fun rememberReaderMode(): ReaderMode {
    val activity = LocalActivity.current
    return remember(activity) {
        ReaderMode { deliver -> activity?.let { host -> SessionControl(host, deliver) } }
    }
}

/**
 * Sends this screen's tags to [onTag] while it is resumed, and stops on pause or dispose. A screen
 * never starts or stops reader mode: the nav shell holds it for the whole tag flow, so a screen
 * that leaves during a transition takes only its own sink with it (#37, R1).
 */
@Composable
fun TagSinkEffect(readerMode: ReaderMode, onTag: (TagHandle) -> Unit) {
    val current by rememberUpdatedState(onTag)
    LifecycleResumeEffect(readerMode) {
        val sink: (TagHandle) -> Unit = { tag -> current(tag) }
        readerMode.install(sink)
        onPauseOrDispose { readerMode.uninstall(sink) }
    }
}
```

- [ ] **Step 3: The route predicate and the nav shell.**

In `Route.kt`, append after `isSupported()` (line 66):

```kotlin

/**
 * The screens that read tags, and so the screens reader mode belongs to (2.7, #37). It is held
 * while one of them is on top of the back stack, which makes a move between them — inspect to
 * write, or back — no hand-over at all.
 *
 * `Route.TagResult` is deliberately absent. It reads nothing; it is where the ambient trampoline
 * lands, with no reader mode, exactly as at 2.6; and the inspect screen no longer pushes it, since
 * its result is drawn on the screen the user is already on. Adding it here would hold reader mode
 * over an ambient result and release it again the instant the sheet opened its asset — with the tag
 * still on the phone, which is the re-dispatch this release removed.
 */
internal fun Route.readsTags(): Boolean = when (this) {
    Route.Scan, is Route.WriteTag -> true
    else -> false
}
```

In `ServiceTagRoot.kt`, add three imports:

```kotlin
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.loosecannon.servicetag.ui.nfc.ReaderMode
import com.loosecannon.servicetag.ui.nfc.rememberReaderMode
```

Change the signature (line 40) and add the hold, after `val current = backStack.lastOrNull()` (line 47):

```kotlin
@Composable
fun ServiceTagRoot(
    graph: AppGraph,
    deepLinks: SharedFlow<Route>,
    snackbars: SharedFlow<String>,
    readerMode: ReaderMode = rememberReaderMode(),
) {
```

```kotlin
    val current = backStack.lastOrNull()

    // #37 — one reader-mode session for the activity, held for as long as a tag-reading screen is
    // on top. The inspect screen used to end its own session the moment it pushed a result, with
    // the tag still against the phone: the platform re-discovered that tag, dispatched it under
    // normal dispatch, and an inspect inside ServiceTag opened another app. A move between the two
    // tag screens is now no hand-over at all, which is runbook R1.
    val readsTags = current is Route && current.readsTags()
    LifecycleResumeEffect(readerMode, readsTags) {
        readerMode.hold(readsTags)
        onPauseOrDispose { readerMode.hold(false) }
    }
```

Replace the `Route.Scan` entry (lines 185–191):

```kotlin
                entry<Route.Scan> {
                    ScanScreen(
                        graph = graph,
                        readerMode = readerMode,
                        // 2.7 (#37): the read's answer is drawn on this screen rather than pushed
                        // as a `Route.TagResult` — pushing it took the screen, and with it the
                        // reader mode, out from under the tag. What the answer decides still
                        // navigates, exactly as the pushed sheet's entry did; the entry itself
                        // stays for the ambient trampoline, which is the only thing that uses it.
                        onOpenAsset = { backStack.add(Route.AssetDetail(it)) },
                        onNewAsset = { backStack.add(Route.AssetEdit(null)) },
                        onWriteTag = { backStack.add(it) },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
```

And in the `Route.WriteTag` entry (line 207), pass the reader mode:

```kotlin
                        WriteTagScreen(
                            graph = graph,
                            readerMode = readerMode,
                            key = key,
                            onDone = { backStack.removeLastOrNull() },
                        )
```

`entry<Route.TagResult>` is **not** changed.

- [ ] **Step 4: The two screens.**

In `ScanScreen.kt`: delete the imports `androidx.activity.compose.LocalActivity`, `com.loosecannon.nfc.tagcore.android.NfcReaderModeSession` and `com.loosecannon.nfc.tagcore.android.NfcTagHandle`; add

```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.loosecannon.servicetag.ui.nfc.ReaderMode
import com.loosecannon.servicetag.ui.nfc.TagSinkEffect
```

Replace the function's KDoc, signature and head (lines 54–89) with:

```kotlin
/**
 * Foreground reader-mode scanning (D3 §9, G1 §1.4). The only `tertiaryContainer` surface in the
 * app sits here and the halo is the app's only animation; what a tag turns out to be is decided by
 * `ResolveTag` and shown on the result sheet.
 *
 * Reached as a pushed destination (Settings' Read / inspect tag row, or the dashboard's empty-state
 * action), never a tab (D12 §16 correction), so it always needs a way back.
 *
 * 2.7 (#37): the screen owns neither the reader-mode session — the nav shell holds one for the
 * whole tag flow — nor a route for its answer. The answer is drawn over this screen, so an inspect
 * never takes the screen out from under a tag that is still against the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    graph: AppGraph,
    readerMode: ReaderMode,
    onOpenAsset: (String) -> Unit,
    onNewAsset: () -> Unit,
    onWriteTag: (Route.WriteTag) -> Unit,
    onBack: () -> Unit,
) {
    val model: ScanViewModel = viewModel(key = "scan") { ScanViewModel(graph) }
    val state by model.state.collectAsStateWithLifecycle()

    // The activity owns the one session; this screen only says where its tags land while it is
    // resumed. Leaving it takes the sink away, and the nav shell decides about NFC itself.
    TagSinkEffect(readerMode) { tag -> model.onTag(tag) }

    // The answer, as the two strings the route used to carry — which is all it ever carried, and
    // which `rememberSaveable` can keep through process death without a `Saver` of its own.
    var format by rememberSaveable { mutableStateOf<String?>(null) }
    var key by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(model) {
        model.events.collect { event ->
            when (event) {
                is ScanEvent.Show -> { format = event.route.format; key = event.route.key }
            }
        }
    }
```

Then, inside the `Scaffold` body, the `Column` is unchanged except for the availability line:

```kotlin
            ReadyToScan(reading = state.reading)
            state.problem?.let { QuietLine(it) }
            NfcAvailability(readerMode)
```

and immediately after the `Scaffold` call's closing brace, before the end of `ScanScreen`:

```kotlin
    // Over this screen, not on top of it. Each way out clears the answer first, so coming back to
    // the inspector shows READY TO SCAN and not the answer to a tag that is long gone.
    format?.let { shown ->
        TagResultSheet(
            graph = graph,
            format = shown,
            key = key,
            onDismiss = { format = null },
            onWriteTag = { route -> format = null; onWriteTag(route) },
            onOpenAsset = { id -> format = null; onOpenAsset(id) },
            onNewAsset = { format = null; onNewAsset() },
        )
    }
}
```

Replace `NfcAvailability` (lines 180–190) — the three sentences are byte-identical:

```kotlin
/** What the phone can actually do, said once and quietly — never over the top of the card. */
@Composable
private fun NfcAvailability(readerMode: ReaderMode) {
    val line = when {
        !readerMode.present -> "Scanning needs the app's own window."
        !readerMode.available -> "This phone has no NFC hardware."
        !readerMode.enabled -> "NFC is turned off. Enable it in system settings, then come back."
        else -> null
    }
    line?.let { QuietLine(it) }
}
```

In `WriteTagScreen.kt`: delete the imports `androidx.activity.compose.LocalActivity`, `androidx.lifecycle.compose.LifecycleResumeEffect`, `com.loosecannon.nfc.tagcore.android.NfcReaderModeSession` and `com.loosecannon.nfc.tagcore.android.NfcTagHandle`; add

```kotlin
import com.loosecannon.servicetag.ui.nfc.ReaderMode
import com.loosecannon.servicetag.ui.nfc.TagSinkEffect
```

Amend the signature (lines 57–61) and replace lines 69–77:

```kotlin
fun WriteTagScreen(
    graph: AppGraph,
    readerMode: ReaderMode,
    key: Route.WriteTag,
    onDone: () -> Unit,
) {
```

```kotlin
    // The activity owns the one reader-mode session (#37, R1): arriving here from the inspect
    // screen is a change of sink, not a hand-over of NFC, so nothing can land between the two.
    TagSinkEffect(readerMode) { tag -> model.onTag(tag) }
```

and the availability line, at the call site and in the helper — again byte-identical sentences:

```kotlin
            NfcAvailabilityLine(readerMode)
```

```kotlin
@Composable
private fun NfcAvailabilityLine(readerMode: ReaderMode) {
    val line = when {
        !readerMode.present -> "Writing needs the app's own window."
        !readerMode.available -> "This phone has no NFC hardware."
        !readerMode.enabled -> "NFC is turned off. Enable it in system settings, then come back."
        else -> null
    }
    line?.let { QuietLine(it) }
}
```

The screen's `remember(key) { key.target() }`, its `viewModel(key = "write/…")`, `warnAboutLock`, the `Scaffold`, `OverwriteSheet`, `LockWarning` and `LockSwitch` are untouched. `WriteTagScreen`'s `key` parameter keeps its name; it is now the third parameter, and both call sites (`ServiceTagRoot` and `WriteTagScreenConsentWordingTest`, which composes `OverwriteSheet` and `WriteStatus` rather than the screen — confirm with `grep -n 'WriteTagScreen(' app/src/androidTest -r`) use named arguments.

Run: `./gradlew :app:testDebugUnitTest --tests '*ReaderModeTest' --tests '*RouteTest' --console=plain` → **BUILD SUCCESSFUL**, `ReaderModeTest` 6/6 and `RouteTest` 3/3.

- [ ] **Step 5: The emulator test for the hold.**

Create `app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/scan/ReaderModeHoldTest.kt`:

```kotlin
package com.loosecannon.servicetag.ui.scan

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loosecannon.servicetag.ui.app
import com.loosecannon.servicetag.ui.awaitText
import com.loosecannon.servicetag.ui.clearInstall
import com.loosecannon.servicetag.ui.nav.Route
import com.loosecannon.servicetag.ui.nav.ServiceTagRoot
import com.loosecannon.servicetag.ui.nfc.ReaderMode
import com.loosecannon.servicetag.ui.nfc.ReaderModeControl
import com.loosecannon.servicetag.ui.theme.ServiceTagTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** How long a hold assertion waits for a navigation and its effects to settle. */
private const val SETTLE_MILLIS = 5_000L

/**
 * 2.7 (#37, runbook R1) — the nav shell's hold, on a device with no NFC adapter. The session itself
 * cannot be exercised here; what can be, and what regressed, is the *number* of times the app turns
 * reader mode on and off as the back stack moves, so the control is a counter.
 *
 * The three routes arrive through the deep-link flow rather than through taps: the flow is the
 * activity's own way of pushing a destination, and it lets one case walk the whole tag flow without
 * depending on where a row sits on the settings screen.
 *
 * Emulator only — the suite wipes app data.
 */
@RunWith(AndroidJUnit4::class)
class ReaderModeHoldTest {

    @get:Rule val rule = createComposeRule()

    private val control = CountingControl()
    private val readerMode = ReaderMode { _ -> control }
    private val deepLinks = MutableSharedFlow<Route>(replay = 1, extraBufferCapacity = 4)
    private val snackbars = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)

    @Before fun freshInstall() = clearInstall()

    @Test fun theHoldSpansTheTagFlowAndEndsOnlyWhenItIsLeft() {
        rule.setContent {
            ServiceTagTheme {
                ServiceTagRoot(
                    graph = app.graph,
                    deepLinks = deepLinks,
                    snackbars = snackbars,
                    readerMode = readerMode,
                )
            }
        }

        // The dashboard reads no tags: ambient dispatch is what a tap on this screen is for.
        rule.awaitText("ServiceTag")
        assertEquals(0, control.starts)
        assertEquals(0, readerMode.sinkCount)

        // Read / inspect tag.
        rule.runOnIdle { deepLinks.tryEmit(Route.Scan) }
        rule.awaitText("READY TO SCAN")
        rule.waitUntil(SETTLE_MILLIS) { readerMode.sinkCount == 1 }
        assertEquals(1, control.starts)
        assertEquals(0, control.stops)

        // R1 — the write screen on top of the inspect screen. One session, and the hand-over makes
        // no platform call: the survivor's sink is the only one left afterwards.
        rule.runOnIdle { deepLinks.tryEmit(Route.WriteTag("none", null, null)) }
        rule.awaitText("Write a tag")
        rule.waitUntil(SETTLE_MILLIS) { readerMode.sinkCount == 1 }
        assertEquals("the transition is not a start", 1, control.starts)
        assertEquals("and not a stop either", 0, control.stops)

        // Leaving the flow is the one thing that hands NFC back to the system.
        rule.runOnIdle { deepLinks.tryEmit(Route.Settings) }
        rule.awaitText("Read / inspect tag")
        rule.waitUntil(SETTLE_MILLIS) { readerMode.sinkCount == 0 }
        assertEquals(1, control.stops)
        assertEquals(1, control.starts)
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

- [ ] **Step 6: The gate, and the blast radius.**

Run: `./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.
Run: `git grep -n 'NfcReaderModeSession' -- app/src` → exactly two lines, both in `ui/nfc/ReaderMode.kt` (the import and the construction inside `SessionControl`).
Run: `git grep -n 'disableReaderMode\|enableReaderMode' -- app/src | wc -l` → `0` (the app never calls the adapter itself).
Run: `git grep -n 'Route.TagResult' -- app/src/main/kotlin/com/loosecannon/servicetag/ui/nav/ServiceTagRoot.kt` → **1** line, the `entry<Route.TagResult>` declaration.
Run: `git diff --stat -- core libs app/schemas docs app/src/main/AndroidManifest.xml app/build.gradle.kts app/src/main/kotlin/com/loosecannon/servicetag/MainActivity.kt app/src/main/kotlin/com/loosecannon/servicetag/nfc` → **no output**.
Run: `git -C libs/nfc-tag-core status --porcelain | wc -l` → `0`.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "one reader mode for the app, and an inspect holds onto it"
```

---

### Task 3: the version bump, the README, and the two physical-gate rows

**Files:**
- Modify: `app/build.gradle.kts`, `README.md`, `docs/architecture/product-split-migration.md`
- **Not modified:** `docs/architecture/product-split-evidence.md` (the controller writes the evidence after Task 4), `docs/architecture/product-split-target.md` (nothing in 2.7 changes an identity, a filter or a division of labour), `docs/design/**` (history — never rewritten)

**Interfaces:** `BuildConfig.VERSION_NAME` becomes `"2.7"`, which `SettingsScreen` shows and `ExportBackupSet` writes into `BackupManifest.appVersion`; `tools/release-dry-run.sh` reads the same `versionName` out of `app/build.gradle.kts`.

- [ ] **Step 1: The version bump.** In `app/build.gradle.kts`, inside `defaultConfig` (lines 43–44):

```kotlin
        versionCode = 9
        versionName = "2.7"
```

Nothing else in that file changes. There is no `schemaVersion` here to touch; `AppGraph.SCHEMA_VERSION` stays 5.

- [ ] **Step 2: The README.** Two edits, both in `## What it does today`.

Replace the intro's last sentence (line 20) so the paragraph reads:

```markdown
A single-screen Compose app — Dashboard and Assets along the bottom, everything else one push deep —
around the one-tap flow that is still the spine: tap a tag anywhere and the phone opens the right
place. Read / inspect tag, for the rare deliberate look, lives under Settings, and it keeps NFC for
as long as you are on it: what a tag turns out to be is shown on the screen you are already on, so a
tag left against the phone is never handed back to the system halfway through a look.
```

Replace the Dashboard bullet (lines 22–26) with:

```markdown
- **Dashboard** — the assets in service, and, until the first export succeeds, a card that says
  there is no backup yet and offers to take one. The list is the systems themselves: a component of
  another asset is listed on that asset and not again here. A search box above the list filters as
  you type — over the name, the category, the make, the model, the serial and the location — and a
  component that matches comes back with the system it is part of named under it. The sections for
  what needs attention and what is coming up are drawn from schedules, which are Phase 3, so today
  the dashboard draws the current assets and the backup nudge.
```

These are documentation prose, not app strings; the four in-app sentences 2.7 adds are the ones listed in Global Constraints.

- [ ] **Step 3: The runbook's physical-gate rows.** In `docs/architecture/product-split-migration.md` §E, replace the **R1** row (line 959) with the amended row and add **R2** after it:

```markdown
| **R1** | with **ServiceTag** open, move between **Read / inspect tag** and the **write tag** screen — both directions — and tap **T2** on the screen that survives the transition | ServiceTag used to compose **two** reader-mode sessions (`ScanScreen` and `WriteTagScreen`); on a nav transition that overlapped both, one session's stop could leave the app with no reader mode. **From 2.7 this is structural**: one session belongs to the activity (`app/…/ui/nfc/ReaderMode.kt`), the two screens install a tag sink instead of a session of their own, and the hold spans both routes — so a transition makes no `enableReaderMode`/`disableReaderMode` call at all, proved on the JVM by `ReaderModeTest` and on the emulator by `ReaderModeHoldTest`. The tap is what remains: only the phone can show that the platform agrees, and that the surviving screen really does read the tag. Pre-existing ServiceTag behaviour, **ServiceTag only** — NoteTag has a single writer screen and no overlap to lose |
| **R2** | with **ServiceTag** open on **Read / inspect tag**, hold **T4** (NoteTag's tag) against the phone and **leave it there** until the answer appears; then take it away | issue **#37**'s acceptance, and check 6 of §E repeated with the tag left in the field — which is what the 2026-09-17 gate observed going wrong (§E check 6's note, evidence P5): ServiceTag released reader mode ~200 ms after its read and the platform dispatched the tag to NoteTag, which opened the note. Pass: the inspect screen names the tag as another app's and **nothing else opens** — no NoteTag window, no note, no chooser, no second dispatch — and the answer appears on the inspect screen itself rather than on a screen pushed over it. 2.7 holds the activity's one session for as long as a tag-reading screen is on top and draws the answer in place, so the tag stays ServiceTag's until the owner leaves the screen. **ServiceTag only**, one owner action, outside the 12-action budget for the same reason R1 is |
```

And amend the budget note (lines 977–980) so the count is honest:

```markdown
whole of the owner's involvement.** The pre-existing ServiceTag reader-mode rows **R1** and **R2**
above are two further taps and are deliberately outside this budget: they prove nothing about the
split, only that ServiceTag's own reader mode survives a nav transition (R1) and holds a tag through
an inspect (R2 — issue #37, fixed in 2.7). §C's data migration needs **four** further on-phone UI actions
```

- [ ] **Step 4: Verify**

Run: `git diff --stat -- docs/design docs/architecture/product-split-evidence.md docs/architecture/product-split-target.md docs/architecture/product-split-archaeology.md` → **no output**.
Run: `grep -n 'versionCode\|versionName' app/build.gradle.kts` → `versionCode = 9`, `versionName = "2.7"`.
Run: `grep -c 'A search box above the list filters as you type' README.md` → `1`.
Run: `grep -n '| \*\*R2\*\* |' docs/architecture/product-split-migration.md` → 1 line; `grep -c 'ReaderModeHoldTest' docs/architecture/product-split-migration.md` → `1`.
Run: `./gradlew :app:assembleDebug --console=plain` then `"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging app/build/outputs/apk/debug/app-debug.apk | grep -o "versionName='[^']*'"` → `versionName='2.7'`. (Per the memory note, `aapt2` is at build-tools **36.0.0**.)

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "2.7: bump the version, readme and the runbook catch up"
```

---

### Task 4 (controller-run): the proofs

**Files:** none. This task runs and records; the evidence file is the controller's to write afterwards.

**Interfaces:** the recorded verdicts for proofs (1)–(6), each with the command that produced it.

- [ ] **Step 1: Proof (1) — the gate.**

```bash
./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`. In `app/build/reports/tests/testDebugUnitTest/`: `DashboardViewModelTest` 8/8 with `componentsAreNotListedUntilTheyAreSearchedFor`, `theQueryReachesTheFieldsSomeoneWouldType`, `clearingTheQueryPutsTheComponentsBackOutOfSight` and `aSearchThatFindsNothingIsNotAFirstRun` as passes; `ReaderModeTest` 6/6; `RouteTest` 3/3 with `onlyTheTagScreensHoldReaderMode`. Record the class list and the counts.

- [ ] **Step 2: Proof (2) — the connected suite on the emulator.**

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --console=plain
```

Expected: `BUILD SUCCESSFUL`; in `app/build/reports/androidTests/connected/`, `DashboardSearchTest` 1/1 and `ReaderModeHoldTest` 1/1, and every pre-existing class at its previous count — in particular `NavigationSmokeTest` 3/3, `PreSplitLinkTagSheetTest` 1/1, `WriteTagScreenConsentWordingTest`, `AssetModelDeviceProofTest` and `RemovedSurfacesTest` unchanged. Record the class list, the counts and the run's timestamp. There is no expected delta: 2.7 deletes no test.

- [ ] **Step 3: Proof (3) — the structural facts neither suite can assert.**

```bash
git grep -n 'NfcReaderModeSession' -- app/src                                   # 2 lines, both in ui/nfc/ReaderMode.kt
git grep -nE 'enableReaderMode|disableReaderMode' -- app/src | wc -l            # 0
git grep -n 'Route.TagResult' -- app/src/main/kotlin/com/loosecannon/servicetag/ui/nav/ServiceTagRoot.kt   # 1 line
git grep -n 'SearchBar' -- app/src/main | wc -l                                 # 0
git diff --stat HEAD~3 -- core libs app/schemas app/src/main/AndroidManifest.xml # no output
git -C libs/nfc-tag-core status --porcelain | wc -l                              # 0
git ls-tree HEAD libs/nfc-tag-core                                              # 7e0377a…
bash tools/check-submodule-pin.sh
```

Expected: the counts above, no output from the `git diff --stat`, the gitlink still at `7e0377a`, and the pin script passing. Together these are "the library did not change and the app stopped owning more than one session"; the `ServiceTagRoot` count is "the inspect screen no longer pushes its own answer".

- [ ] **Step 4: Proof (4) — the version in the built artifact.**

```bash
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging app/build/outputs/apk/debug/app-debug.apk | grep -o "versionName='[^']*'"
```

Expected: `versionName='2.7'`. Record the line.

- [ ] **Step 5: Proof (5) — the release signing dry run.**

```bash
bash tools/release-dry-run.sh
```

Expected: `RELEASE DRY RUN: PASS` with exit 0, the built `versionName` compared against `app/build.gradle.kts` and found to be **2.7**, exactly one signer, and the certificate matching the configured fingerprint. Record the verdict line and the exit code only — never a fingerprint, a path or a password. A `PARTIAL` is not a pass for this release. If the script exits 3 (no signing material on this machine), that is a **BLOCKED** proof, not a pass, and the controller stops and says so.

- [ ] **Step 6: Proof (6) — nothing personal, and nothing on the phone.**

```bash
git diff --stat HEAD~3
git log --oneline -3
```

Expected: the three commits of Tasks 1–3 and no other path; no device id, tag UID, note id, absolute home path or e-mail address anywhere in the diff. Nothing in this change installs on, reads from or writes to the phone: every connected command above carries `ANDROID_SERIAL=emulator-5554` on the same command line, and `adb devices` is not consulted for the phone at all. **Do not offer the owner a manual checklist**; the two device rows that do exist are **R1** and **R2** in §E of the runbook, they are two taps, and they are the owner's to schedule.

- [ ] **Step 7: Record and hand back.** Assemble one table — proof, command, expected, observed, verdict — for (1)–(6), plus the per-task gate counts and the connected run's class list. State the #37 root cause as found in the code, and the #39 scope finding (the asset detail screen already lists components; nothing was added there). The phase ends **"ServiceTag 2.7 complete locally; the dashboard lists systems and the inspect screen holds reader mode; physical rows R1 and R2 and the push and `servicetag-v2.7` pending owner authorization."** No push, no tag, no `gh` call.

---

## Self-review

| Requirement | Where |
|---|---|
| #39 — the dashboard does not list components | Task 1 Step 2 (`shown = if (query.isBlank()) matching.filter { it.parentAssetId == null } else matching`), Task 1 Step 1 (`componentsAreNotListedUntilTheyAreSearchedFor`) |
| #39 — a quick search box that filters as you type | Task 1 Step 3 (`SearchBox`, an `OutlinedTextField` with a clear action), Task 1 Step 4 (`DashboardSearchTest`) |
| #39 — the search reaches components, one keystroke away | Task 1 Step 2 (a non-blank query drops the top-level filter), Steps 1 and 4 (the pump is found by "circ" and says `Part of Hot tub`) |
| #39 — which fields match, and why | Task 1 decision 2 and `SEARCHED_FIELDS`; pinned by `theQueryReachesTheFieldsSomeoneWouldType`, including the negative case for `notes` |
| #39 — a component hit is understandable | `DashboardRow.parentName`, `CurrentRow`'s `"Part of $parent"` — the existing phrasing, not a new sentence |
| #39 scope check — does the detail screen list components? | Task 1 Step 0: **yes**, `ComponentsSection` at `AssetDetailScreen.kt:641` over `AssetViewModels.kt:298`, navigable, already tested on the JVM and the emulator. No code added |
| #37 — root cause, from the code | Task 2's "The root cause, from the code": the only two `stop()` call sites are the two `onPauseOrDispose` blocks, so it is the nav push of `Route.TagResult` ending `ScanScreen`'s `LifecycleResumeEffect` |
| #37 — the inspect screen keeps reader mode until it is left | Task 2 Step 3 (`readsTags` + the shell's `LifecycleResumeEffect`) and Step 4 (the answer drawn in place, so the screen never leaves) |
| #37 — an inspect never becomes an ambient dispatch | the same two steps; proved by `ReaderModeHoldTest` on the emulator (no stop across the flow) and accepted physically by runbook **R2** |
| #37 — one-shot-until-the-tag-leaves considered | Task 2 decision 2: not implementable against the pinned `NfcReaderModeSession`, which has no presence signal and takes its callback at construction |
| R1 — exactly one session after a transition | Task 2 decision 1: one session per activity, built in the shell; `ReaderModeTest.theNewestSinkGetsTheTag` and `theSurvivorReadsWhenTheLeavingScreenGoesFirst` for the routing; `ReaderModeHoldTest` for zero platform calls across the transition; runbook R1 amended in Task 3 Step 3 |
| A JVM test where the logic is testable on the JVM | `ReaderModeTest` (6 cases), `RouteTest.onlyTheTagScreensHoldReaderMode`, `DashboardViewModelTest` (4 new cases) |
| An emulator test where feasible | `DashboardSearchTest`, `ReaderModeHoldTest` — both against a counting `ReaderModeControl` or plain Compose, neither needing NFC |
| Version, README, runbook | Task 3 Steps 1–3 |
| Every new user-facing sentence listed verbatim | Global Constraints, four sentences |
| `libs/` untouched, `:core` untouched, no schema or format bump | Global Constraints; checked by Task 2 Step 6 and Task 4 Step 3 |
| Commit style, no attribution, author GonzRon | Global Constraints; the three subjects in Tasks 1–3 |
| Emulator only, no instrumented suite on the phone | Global Constraints; every connected command carries `ANDROID_SERIAL=emulator-5554` |

Every type named above was read in the tree at `1afd412` or is defined by a task. **New:** `DashboardRow`, `Asset.matches`, `SEARCHED_FIELDS`, `SearchBox`, `ReaderModeControl`, `ReaderMode`, `SessionControl`, `rememberReaderMode`, `TagSinkEffect`, `Route.readsTags`, `ReaderModeTest`, `ReaderModeHoldTest`, `DashboardSearchTest`. **Existing:** `DashboardState`, `DashboardViewModel`, `DashboardScreen`, `BackupNudge`, `FirstRun`, `CurrentRow`, `SectionHeader`, `QuietLine`, `ControlShape`, `Asset`, `AssetStatus`, `Asset.isRetired`, `AssetRepository.observeAll`, `AppPrefs.lastBackupAt`, `AssetCommand`, `CreateAsset.run`, `AssetTree.children`, `ComponentRow`, `ComponentsSection`, `AssetDetailState.components`, `NfcReaderModeSession`, `NfcTagHandle`, `TagHandle`, `LifecycleResumeEffect`, `LocalActivity`, `ScanViewModel`, `ScanEvent.Show`, `TagResultSheet`, `TagResultWire`, `Route.Scan`, `Route.TagResult`, `Route.WriteTag`, `Route.WriteTag.isSupported`, `SupportedWriteTargetKinds`, `WriteTagViewModel`, `TagWriteController`, `ServiceTagRoot`, `FakeGraph`, `app`, `clearInstall`, `awaitText`, `Icons.Outlined.Search`, `Icons.Outlined.Clear`, `tools/release-dry-run.sh`, `tools/check-submodule-pin.sh`.
