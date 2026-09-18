# ServiceTag 2.6 — Remove the Note-Link Functionality (Option B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the note-link product from ServiceTag at the behavioural level — the Links list and detail screens, the share target, every way to create or bind a tag to an external link, the `servicetag://link/…` deep link, the Joplin/Obsidian/Logseq/generic launch policy, the link branches of the scan and dispatch paths, and the tests that exist only for that feature — while the `external_link` table and the format-5 `externalLinks` backup field stay as **compatibility tombstones** that export and restore byte-for-byte as today. An old link-bound tag fails safely with one clear pre-split sentence and launches nothing. Ship it as `versionCode` 8 / `versionName` 2.6.

**Architecture:** Four tasks. **Task 1 (`:core` half)** turns `TagTarget.LinkTarget` from a live target into a dead end: `ResolveTag` returns a new `Resolution.PreSplitLink` and never reads a link row, `requireTargetExists` refuses a `LinkTarget` with a typed failure so `BindTag` and `ProvisionTag` both refuse it and write nothing, `OpenLink`/`SaveLink`/`DeleteLink`/`LinkLaunchPolicy` are deleted, `DeepLinkRoute` drops the `link` host, and `LinkRepository` narrows to the four members backup export/restore and the tombstone round-trip need. **Task 2 (`:app` half)** deletes `ShareActivity`, `ui/share/`, `ui/links/`, the `link` manifest `<data>` line, the whole `ShareActivity` manifest element and the note-scheme `<queries>`, removes the Links route/entries/actions and the link surfaces of the dashboard, asset and bind-picker screens, renders the one new sentence for the pre-split outcome in the existing result sheet, refuses `Route.WriteTag("link", …)` at the nav boundary, and adds the app unit tests plus two emulator tests. **Task 3** is the README, the version bump and the two design-doc amendments. **Task 4** is the controller-run proof set. Tasks 1 and 2 are two commits but only the pair compiles `:app`: Task 1's gate is `:core` alone, exactly as the Phase G `:core` half was gated, and Task 2 restores the full gate.

**Tech Stack:** the estate's pins — AGP 9.4.0, Kotlin 2.4.20, Gradle 9.7.1, JDK 17; `nfc-tag-core` at `nfc-tag-core-v0.1.0` (`7e0377a`) as a pinned submodule under `libs/`, untouched; Room 3 with `AppDatabase` at schema version **5, unchanged**; Compose + Navigation 3; JUnit 5 in `:core`, JUnit 4 + Compose test in `:app`; `apksigner`/`aapt2` from build-tools 36.0.0 for `tools/release-dry-run.sh`.

**Spec:** the owner's ruling of 2026-09-18 (quoted in Global Constraints) and docs/architecture/product-split-target.md §2/§4.7

---

## The owner's ruling, quoted (2026-09-18)

> Option B, version 2.6. Remove completely, at the product level: the Links list/detail UI and navigation; the share target/flow that accepts external note or web links for NFC writing; any ability to create or bind a tag to an external link; `servicetag://link/...` deep-link handling; the Joplin/Obsidian/Logseq/generic external-link launch policy that exists solely for the old note-link product; link-specific branches in the NFC scan/dispatch path; any settings, menu items, strings, icons, intents, manifest entries or Compose routes exposing it; product-facing tests whose only purpose is the removed feature — retain or replace tests needed to prove old link-shaped data/tags are handled safely rather than launched. The `external_link` Room table and the format-5 `externalLinks` backup field stay as **compatibility tombstones**: no new code path may create, modify, display or launch them; backup export must still carry them and restore must still write them, byte-for-byte as today, so the just-proven format-5 migration contract stands. An old link-bound tag (an `nfc_tag` row whose target is a link, or a tag payload resolving to one) must fail safely with a clear unsupported/pre-split message; it must never launch anything and never be reinterpreted as an asset. The owner's one historical link row on the phone stays untouched and invisible. Do NOT bump the Room schema or the backup format, do NOT discard link rows on upgrade or restore, do NOT add a cleanup-only migration.

## Global Constraints

- **Tombstone rule.** The `external_link` table, `ExternalLinkEntity`, `ExternalLinkDao`, `Mappers`' link mapping, `ExternalLink`, `LinkId`, `LinkKind`, `TagTarget.LinkTarget`, `ExternalLinkDto`, `NfcTagDto.linkId`, `BackupData.externalLinks`, `BackupCodec`'s link sort/count/validation, `ExportBackupSet` and `ImportBackupReplace` are **untouched**. No new code path may create, modify, display or launch a link row; nothing discards one.
- **No schema or format bump.** `AppDatabase` stays at version 5, `AppGraph.SCHEMA_VERSION` stays 5, `BackupManifest.formatVersion` stays 5, `app/schemas/` is not re-exported, and **no migration is added or edited**.
- **No row discard.** Upgrade writes nothing to `external_link`; restore still writes every `externalLinks` row; `DeleteAsset` keeps today's behaviour exactly (it never names links — the schema's `external_link.asset_id` CASCADE takes them with a deleted asset, as it did at 2.5; that is neither a launch nor a display path, so it stands).
- **The single new sentence of the release**, verbatim, the only new user-facing string:
  `This tag points at a note link from before the product split. ServiceTag no longer opens links; NoteTag does.`
- **Every other user-visible sentence stays byte-identical**, with exactly one stated exception, forced by the ruling's own "any … strings … exposing it": the bind action label **`"Bind to asset or note"` becomes `"Bind to asset"`** in its three occurrences in `TagResultSheet.kt`. That is a word deletion from an existing label that offers the removed feature, not a new sentence. No other existing string is edited, and no eyebrow, accent, glyph or action label is invented: the new state reuses `"Tag detected"`, `dueSoon.foreground`, `Icons.Outlined.Info` and `"Cancel"`.
- **Version.** `app/build.gradle.kts`: `versionCode` 7 → **8**, `versionName` "2.5" → **"2.6"**. Nothing else in that file changes.
- **Commits.** One commit per task, on `master`. Single casual subject line; **no body, no trailers, no attribution of any kind** (no `Co-Authored-By`, no `Generated-with`). Author GonzRon.
- **No personal data** in tracked files or reports: `~` for the home directory, `emulator-5554` only, no device ids, no tag UIDs, no note ids, no e-mail addresses. The owner's historical link row's `uri` and `label` are never printed into a file, a report or a test fixture.
- **`libs/` is untouched.** No file under `libs/nfc-tag-core` is read for edit, modified, or re-pinned; the gitlink stays at `7e0377a`.
- **No instrumented suite on the phone.** Every `connectedDebugAndroidTest`, `installDebug` and `adb` call carries `ANDROID_SERIAL=emulator-5554` on the same command line. The suites wipe app data, which is why. The phone's data — including the one historical link row — is never touched by this work.
- **Per-task gate.** Task 1: `./gradlew :core:test --console=plain` (`:app` is knowingly red between Tasks 1 and 2 — the `Resolution.LaunchLink` and `OpenLink` removals are not shimmed). Tasks 2–4: `./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain`.
- **The eight pre-tag proofs**, all discharged in Task 4: (1) no user-facing link/note feature reachable; (2) no share intent or link deep-link handler registered; (3) ServiceTag cannot create a link-bound NFC tag; (4) old link-bound records/tags fail safely rather than launch; (5) existing asset/service data and attachments on the phone unchanged; (6) format-5 backup/restore still passes; (7) ordinary CI green; (8) release signing dry run PASS.
- **Not in this change:** no `git push`, no tag, no `gh` mutation, no phone install, no physical NFC, no edit to `docs/architecture/product-split-evidence.md` (the controller writes evidence after the proofs).

## File map

```
core/src/main/kotlin/com/loosecannon/servicetag/core/
  usecase/ResolveTag.kt            MODIFY  LaunchLink → PreSplitLink; no LinkRepository
  usecase/TagTargets.kt            MODIFY  LinkTargetUnsupported; no LinkRepository
  usecase/BindTag.kt               MODIFY  no LinkRepository
  usecase/ProvisionTag.kt          MODIFY  no LinkRepository
  ports/Repositories.kt            MODIFY  LinkRepository narrowed to 4 members
  links/DeepLinkRoute.kt           MODIFY  DeepLink.Link and the "link" host dropped
  usecase/{OpenLink,SaveLink,DeleteLink}.kt   DELETE
  links/LinkLaunchPolicy.kt        DELETE
  backup/*, usecase/{Export,Import}*.kt, model/{ExternalLink,TagBinding}.kt   UNTOUCHED

app/src/main/
  AndroidManifest.xml              MODIFY  link <data>, ShareActivity element, 5 scheme <queries>
  kotlin/…/ShareActivity.kt        DELETE
  kotlin/…/ui/share/               DELETE (2 files)
  kotlin/…/ui/links/               DELETE (3 files)
  kotlin/…/links/LinkLauncher.kt   UNTOUCHED (SettingsScreen's project URL)
  kotlin/…/MainActivity.kt         MODIFY  DeepLink.Link arm gone
  kotlin/…/nfc/NfcDispatchActivity.kt   MODIFY  the launch branch gone
  kotlin/…/di/AppGraph.kt          MODIFY  saveLink/openLink/deleteLink gone; 3 ctors narrowed
  kotlin/…/ui/nav/{Route,ServiceTagRoot}.kt   MODIFY
  kotlin/…/ui/scan/{ScanViewModels,ScanScreen,TagResultSheet,WriteTagScreen}.kt   MODIFY
  kotlin/…/ui/dashboard/DashboardViewModel.kt, DashboardScreen.kt   MODIFY
  kotlin/…/ui/asset/{AssetViewModels,AssetDetailScreen}.kt          MODIFY
  data/room/**                     UNTOUCHED (entity, DAO, mappers, migrations, schemas)

app/src/debug/kotlin/…/debug/DebugBackupActivity.kt   MODIFY  seed/wipe stop making link rows
```

---

### Task 1 (`:core` half): the link target becomes a dead end, and the launch policy goes

**Files:**
- Modify: `core/src/main/kotlin/com/loosecannon/servicetag/core/usecase/ResolveTag.kt`, `.../usecase/TagTargets.kt`, `.../usecase/BindTag.kt`, `.../usecase/ProvisionTag.kt`, `.../ports/Repositories.kt`, `.../links/DeepLinkRoute.kt`
- Delete: `core/src/main/kotlin/com/loosecannon/servicetag/core/usecase/OpenLink.kt`, `.../usecase/SaveLink.kt`, `.../usecase/DeleteLink.kt`, `.../links/LinkLaunchPolicy.kt`
- Modify tests: `core/src/test/kotlin/com/loosecannon/servicetag/core/testing/InMemoryRepositories.kt`, `.../usecase/ResolveTagTest.kt`, `.../usecase/TagBindingUseCasesTest.kt`, `.../usecase/BackupUseCasesTest.kt`, `.../links/DeepLinkRouteTest.kt`
- Delete tests: `core/src/test/kotlin/com/loosecannon/servicetag/core/links/LinkLaunchPolicyTest.kt`, `.../usecase/LinkUseCasesTest.kt`, `.../usecase/DeleteLinkTest.kt`
- **Untouched, and checked at Step 6:** `core/src/main/kotlin/com/loosecannon/servicetag/core/backup/{BackupCodec,BackupFormat,BackupErrors,ArtifactsCodec}.kt`, `.../usecase/ExportBackupSet.kt`, `.../usecase/ImportBackupReplace.kt`, `.../usecase/DeleteAsset.kt`, `.../model/ExternalLink.kt`, `.../model/TagBinding.kt`, `core/src/test/.../backup/BackupCodecTest.kt`

**Interfaces** (what Task 2 relies on):
- `Resolution.PreSplitLink(val tag: TagBinding)` replaces `Resolution.LaunchLink`. There is no other change to `Resolution`.
- `class LinkTargetUnsupported : IllegalArgumentException` in `com.loosecannon.servicetag.core.usecase`.
- `ResolveTag(tags, assets, uow, clock)`, `BindTag(tags, assets, uow, clock)`, `ProvisionTag(tags, assets, uow, ids, clock)` — the `LinkRepository` parameter is gone from all three.
- `LinkRepository` = `upsert`, `get`, `all`, `deleteAll` only.
- `DeepLink` = `Asset`, `Tag`, `Malformed`. `DeepLinkRoute.parse("servicetag", "link", …)` returns `null`.
- `OpenLink`, `SaveLink`, `DeleteLink`, `LinkStillBound`, `LinkRefused`, `LinkNeedsConfirmation`, `LinkLaunchPolicy`, `LinkCheck` no longer exist.

- [ ] **Step 1: Write the failing tests first.**

In `core/src/test/kotlin/com/loosecannon/servicetag/core/usecase/ResolveTagTest.kt`, replace the whole `boundToALinkLaunchesIt` case with the pre-split case, and drop the link fixtures. The class header becomes:

```kotlin
class ResolveTagTest {
    private val assets = InMemoryAssetRepository()
    private val tags = InMemoryTagRepository()
    private val uow = FakeUnitOfWork(assets, tags)
    private val clock = Clock { 9_000L }
    private val resolve = ResolveTag(tags, assets, uow, clock)

    private val v1Id = TagId("123e4567-e89b-12d3-a456-426614174000")
    private val asset = Asset(AssetId("a1"), "Hot tub", createdAt = 1L, updatedAt = 1L)
```

and the replacement case — note that the row is stamped and kept, and that no asset is invented for it:

```kotlin
    /**
     * 2.6 — a tag bound to a pre-split link resolves to its own outcome. No link row is read (the
     * resolver has no `LinkRepository` at all), nothing is launched, and it is never mistaken for
     * an asset even when an asset with the link's id exists.
     */
    @Test fun aLinkBoundTagIsPreSplitAndNeverAnAsset() = runTest {
        assets.rows["l1"] = Asset(AssetId("l1"), "Not this", createdAt = 1L, updatedAt = 1L)
        tags.rows[v1Id.value] = row(v1Id.value, PayloadFormat.V1, v1Id.value, TagTarget.LinkTarget(LinkId("l1")))
        val r = resolve.run(TagPayload.V1(v1Id))
        assertIs<Resolution.PreSplitLink>(r)
        assertEquals(v1Id.value, r.tag.payloadKey)
        assertEquals(9_000L, r.tag.lastScannedAt)
        // the scan is still recorded; the row is not rewritten, retargeted or deleted
        assertEquals(TagTarget.LinkTarget(LinkId("l1")), tags.rows[v1Id.value]!!.target)
        assertEquals(1L, tags.rows[v1Id.value]!!.updatedAt)
    }
```

Delete the now-unused `link` property and the `ExternalLink`/`LinkKind`/`InMemoryLinkRepository` imports; keep the `LinkId` import.

In `core/src/test/kotlin/com/loosecannon/servicetag/core/usecase/TagBindingUseCasesTest.kt`: narrow the fixtures (`private val uow = FakeUnitOfWork(assets, tags)`, `bind = BindTag(tags, assets, uow, clock)`, `provision = ProvisionTag(tags, assets, uow, ids, clock)`; the `links` field and the `ExternalLink`/`LinkKind`/`InMemoryLinkRepository` imports go), rewrite `bindingAKnownRowRetargetsItAndReactivates` to retarget onto a **second asset** instead of a link:

```kotlin
    @Test fun bindingAKnownRowRetargetsItAndReactivates() = runTest {
        seedAsset()
        assets.rows["a2"] = Asset(AssetId("a2"), "Mower", createdAt = 1L, updatedAt = 1L)
        tags.rows[scanned.value] = TagBinding(scanned, PayloadFormat.V1, scanned.value, TagTarget.None, TagStatus.UNBOUND, label = "spare", createdAt = 1L, updatedAt = 1L)
        val row = bind.run(PayloadFormat.V1, scanned.value, TagTarget.AssetTarget(AssetId("a2")))
        assertEquals(TagTarget.AssetTarget(AssetId("a2")), row.target)
        assertEquals(TagStatus.ACTIVE, row.status)
        assertEquals("spare", row.label)          // label kept when none is given
        assertEquals(1L, row.createdAt)
        assertEquals(7_000L, row.updatedAt)
        assertEquals(1, tags.rows.size)
    }
```

drop the `LinkTarget` line from `bindingToAMissingTargetFailsAndWritesNothing`, and add the two refusal cases:

```kotlin
    /** 2.6 — binding a link target is refused by type, and the refusal writes nothing. */
    @Test fun bindingALinkTargetIsRefusedAndWritesNothing() = runTest {
        assertFailsWith<LinkTargetUnsupported> {
            bind.run(PayloadFormat.V1, scanned.value, TagTarget.LinkTarget(LinkId("l1")))
        }
        assertTrue(tags.rows.isEmpty())
        assertEquals(0, uow.commits)
    }

    /** The same refusal on the provisioning side: no row is minted for a link. */
    @Test fun provisioningALinkTargetIsRefusedAndWritesNothing() = runTest {
        assertFailsWith<LinkTargetUnsupported> { provision.begin(TagTarget.LinkTarget(LinkId("l1")), "x") }
        assertTrue(tags.rows.isEmpty())
        assertEquals(0, uow.commits)
    }
```

In `core/src/test/kotlin/com/loosecannon/servicetag/core/links/DeepLinkRouteTest.kt`, replace `linkRoute` with its inverse and drop the `LinkId` import:

```kotlin
    /** 2.6 — the `link` host is withdrawn; it is now simply not a route this app answers. */
    @Test fun theLinkHostIsNoLongerOurs() {
        assertNull(DeepLinkRoute.parse("servicetag", "link", listOf(id)))
        assertNull(DeepLinkRoute.parse("servicetag", "link", emptyList()))
    }
```

In `core/src/test/kotlin/com/loosecannon/servicetag/core/usecase/BackupUseCasesTest.kt`, change only the one assertion that used a removed port member (line ~264) — the tombstone claim is unchanged, it is read off the fake's rows instead:

```kotlin
            assertEquals(1, target.links.rows.values.count { it.assetId == null })
```

Add, to the same class, the explicit tombstone case the ruling asks for (no existing case asserts it by name):

```kotlin
    /**
     * 2.6 — the `externalLinks` tombstone. Nothing in the app creates a link any more, so this is
     * the only proof left that an old set's rows survive a replace-import intact and come back out
     * of the next export unchanged.
     */
    @Test
    fun `external link rows round-trip through export and replace-import unchanged`() {
        val source = Fakes()
        runBlocking {
            source.assets.upsert(asset("a1"))
            source.links.upsert(link("l1", "a1"))
            source.links.upsert(link("l3", null))
        }
        val before = runBlocking { source.links.all().sortedBy { it.id.value } }
        val target = Fakes()
        importInto(target, exportOf(source))
        runBlocking {
            assertEquals(before, target.links.all().sortedBy { it.id.value })
        }
        val decoded = BackupCodec.decode(exportOf(target))
        assertEquals(listOf("l1", "l3"), decoded.data.externalLinks.map { it.id })
    }
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:test --console=plain` → compilation failures naming `Resolution.PreSplitLink`, `LinkTargetUnsupported`, the four-argument `ResolveTag` and `InMemoryLinkRepository.rows` on a removed-member call.

- [ ] **Step 3: `ResolveTag.kt`** — drop the `ExternalLink` and `LinkRepository` imports and the `links` parameter; replace `LaunchLink` with `PreSplitLink` and the arm with a branch that reads nothing:

```kotlin
/** Every way a scan can end (D3 §9). The UI switches on this and nothing else. */
sealed interface Resolution {
    data class OpenAsset(val tag: TagBinding, val asset: Asset) : Resolution

    /**
     * 2.6 — the tag names a link from before the product split. ServiceTag does not read the link
     * row, let alone launch it: the row is a tombstone and this outcome is the whole answer. It is
     * deliberately not `Unbound`, which offers a bind, and not `NotOurs`, which it is not.
     */
    data class PreSplitLink(val tag: TagBinding) : Resolution
    data class Unbound(val tag: TagBinding) : Resolution
    data class Revoked(val tag: TagBinding) : Resolution
    data class UnknownV1(val tagId: TagId) : Resolution
    data class NeedsNewerApp(val version: Int) : Resolution
    data class NotOurs(val payload: TagPayload) : Resolution
}

class ResolveTag(
    private val tags: TagRepository,
    private val assets: AssetRepository,
    private val uow: UnitOfWork,
    private val clock: Clock,
) {
```

and inside `known`:

```kotlin
            else -> when (val t = tag.target) {
                is TagTarget.AssetTarget -> assets.get(t.assetId)?.let { Resolution.OpenAsset(tag, it) } ?: Resolution.Unbound(tag)
                is TagTarget.LinkTarget -> Resolution.PreSplitLink(tag)
                TagTarget.None -> Resolution.Unbound(tag)
            }
```

- [ ] **Step 4: `TagTargets.kt`** — the guard both `BindTag` and `ProvisionTag` already share becomes the one place a link target is refused, so neither of them needs a branch of its own and neither needs a `LinkRepository`:

```kotlin
package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.core.ports.AssetRepository

class UnknownTarget(target: TagTarget) : IllegalArgumentException("target does not exist: $target")

/**
 * 2.6 — a link target is pre-split data. `external_link` survives as a tombstone, so the *shape*
 * is still expressible; binding or provisioning one is not. Refused here, in the one guard both
 * write paths run, so there is no second place to forget.
 */
class LinkTargetUnsupported : IllegalArgumentException("ServiceTag no longer binds a tag to a link")

/** A binding may only point at an asset that exists; `None` is always fine. */
internal suspend fun requireTargetExists(target: TagTarget, assets: AssetRepository) {
    when (target) {
        is TagTarget.AssetTarget -> assets.get(target.assetId) ?: throw UnknownTarget(target)
        is TagTarget.LinkTarget -> throw LinkTargetUnsupported()
        TagTarget.None -> Unit
    }
}
```

The refusal is a typed failure raised **inside** `uow.write { }` before any `upsert`, so the transaction rolls back and nothing is written; the existing `runCatching` in `TagResultViewModel.bind` means it can never reach the UI as a crash. After Task 2 no UI path can produce a `LinkTarget` at all, so its message is developer-facing and renders nowhere.

- [ ] **Step 5: `BindTag.kt`, `ProvisionTag.kt`, `Repositories.kt`, `DeepLinkRoute.kt`, and the four deletions.**

`BindTag.kt`: delete the `LinkRepository` import and the `links` constructor parameter; `requireTargetExists(target, assets)`. Change the `require` message, which names the removed feature, to `"bind needs an asset"`.
`ProvisionTag.kt`: the same two removals; `requireTargetExists(target, assets)`.

`Repositories.kt` — replace the `LinkRepository` block with the narrowed port; `AssetId` stays imported for the other repositories:

```kotlin
/**
 * 2.6 — the tombstone port. `external_link` is still exported and restored byte-for-byte, so the
 * three members the backup path uses stay; `get` stays because the round-trip proofs read a row
 * back by id. Everything that *displayed* a link — `forAsset`, `standalone`, `observeAll`,
 * `observeForAsset` — and `delete`, which only `DeleteLink` called, are gone: the queries still
 * exist on `ExternalLinkDao` for the DAO-level tombstone tests, and nothing above the DAO can
 * reach a link row to show it.
 */
interface LinkRepository {
    suspend fun upsert(link: ExternalLink)
    suspend fun get(id: LinkId): ExternalLink?
    suspend fun all(): List<ExternalLink>
    suspend fun deleteAll()
}
```

`DeepLinkRoute.kt`: delete the `LinkId` import, the `data class Link(val id: LinkId) : DeepLink` member and the `"link" -> …` branch. An unknown host already falls to `else -> null`, which is the existing unknown-deep-link handling; `MainActivity` pushes nothing for a null. Update the KDoc's host list to `asset`, `tag`.

Deletions: `git rm core/src/main/kotlin/com/loosecannon/servicetag/core/usecase/{OpenLink,SaveLink,DeleteLink}.kt core/src/main/kotlin/com/loosecannon/servicetag/core/links/LinkLaunchPolicy.kt core/src/test/kotlin/com/loosecannon/servicetag/core/links/LinkLaunchPolicyTest.kt core/src/test/kotlin/com/loosecannon/servicetag/core/usecase/{LinkUseCasesTest,DeleteLinkTest}.kt`

`InMemoryRepositories.kt` — `InMemoryLinkRepository` keeps `rows`, `snapshot()`, `witness`, `failOnUpsert`, `upsert`, `get`, `all`, `deleteAll` and drops `forAsset`, `standalone`, `delete`, `observeAll`, `observeForAsset` and the now-unused `version` flow, `Flow`/`map`/`MutableStateFlow` uses inside that class and the `AssetId` import if nothing else in the file needs it (`InMemoryAssetRepository` does — keep it).

- [ ] **Step 6: Run to verify they pass, and check the tombstone is untouched**

Run: `./gradlew :core:test --console=plain` → `BUILD SUCCESSFUL`. Record from `core/build/test-results/test/*.xml`: `ResolveTagTest` 8, `TagBindingUseCasesTest` 16, `DeepLinkRouteTest` 5, `BackupUseCasesTest` (its count + 1), `BackupCodecTest` unchanged; the `:core` total drops by `LinkLaunchPolicyTest` 9 + `LinkUseCasesTest` 9 + `DeleteLinkTest` 2 = 20 and rises by 3.
Run: `git diff --stat -- core/src/main/kotlin/com/loosecannon/servicetag/core/backup core/src/main/kotlin/com/loosecannon/servicetag/core/usecase/ExportBackupSet.kt core/src/main/kotlin/com/loosecannon/servicetag/core/usecase/ImportBackupReplace.kt core/src/main/kotlin/com/loosecannon/servicetag/core/usecase/DeleteAsset.kt core/src/main/kotlin/com/loosecannon/servicetag/core/model/ExternalLink.kt core/src/main/kotlin/com/loosecannon/servicetag/core/model/TagBinding.kt core/src/test/kotlin/com/loosecannon/servicetag/core/backup` → **no output**.
Run: `git grep -nE 'LinkLaunchPolicy|LinkCheck|OpenLink|SaveLink|DeleteLink|LinkStillBound' -- 'core/**'` → 0 lines.
Run: `git grep -n 'externalLinks' -- 'core/src/main/**'` → the four unchanged `BackupCodec`/`BackupFormat` lines only.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "core: a link target is a dead end now, not a launch"
```

---

### Task 2 (`:app` half): the surfaces go, and a pre-split tag says so

**Files:**
- Delete: `app/src/main/kotlin/com/loosecannon/servicetag/ShareActivity.kt`, `app/src/main/kotlin/com/loosecannon/servicetag/ui/share/{ShareFlow,ShareCardScreen}.kt`, `app/src/main/kotlin/com/loosecannon/servicetag/ui/links/{LinksScreen,LinkDetailScreen,LinkViewModels}.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/com/loosecannon/servicetag/{MainActivity.kt,nfc/NfcDispatchActivity.kt,di/AppGraph.kt}`, `.../ui/nav/{Route.kt,ServiceTagRoot.kt}`, `.../ui/scan/{ScanViewModels.kt,ScanScreen.kt,TagResultSheet.kt,WriteTagScreen.kt}`, `.../ui/dashboard/{DashboardScreen.kt,DashboardViewModel.kt}`, `.../ui/asset/{AssetViewModels.kt,AssetDetailScreen.kt}`, `app/src/debug/kotlin/com/loosecannon/servicetag/debug/DebugBackupActivity.kt`
- Create: `app/src/test/kotlin/com/loosecannon/servicetag/ui/scan/TagResultViewModelTest.kt`, `app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/RemovedSurfacesTest.kt`, `app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/scan/PreSplitLinkTagSheetTest.kt`
- Modify tests: `app/src/test/kotlin/com/loosecannon/servicetag/testing/FakeGraph.kt`, `.../ui/scan/{ScanViewModelTest,TagWriteControllerTest}.kt`, `.../ui/dashboard/DashboardViewModelTest.kt`, `.../ui/nav/RouteTest.kt`, `.../ui/asset/AssetViewModelsTest.kt`, `.../nfc/TagUseCasesRoomTest.kt`, `.../backup/RestoreProofTest.kt`, `.../data/room/{RoomRepositoriesTest,RepositoryFlowsTest,Migration1To5Test}.kt`, `app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/AppSmokeTest.kt`
- **Untouched, and checked at Step 8:** `app/src/main/kotlin/com/loosecannon/servicetag/data/room/**` (entity, `ExternalLinkDao`, `Mappers.kt`, `Migrations.kt`, `AppDatabase.kt`), `app/schemas/**`, `app/src/main/kotlin/com/loosecannon/servicetag/links/LinkLauncher.kt`, `app/src/test/.../data/room/{ExternalLinkDaoTest,AssetDaoTest,NfcTagDaoTest}.kt`

**Interfaces** (what Tasks 3–4 rely on):
- `internal const val PRE_SPLIT_LINK_SENTENCE` in `ui/scan/ScanViewModels.kt` holds the single new sentence.
- `TagResult.PreSplitLink(val tag: TagBinding)`; `ScanEvent` has one member, `Show`.
- `Route.WriteTag.isSupported(): Boolean` in `ui/nav/Route.kt`.
- The APK registers **no** `ACTION_SEND text/plain` handler and **no** `servicetag://link` host; `servicetag://asset` still resolves to `MainActivity`.

- [ ] **Step 1: Write the failing tests first.**

`app/src/test/kotlin/com/loosecannon/servicetag/ui/scan/ScanViewModelTest.kt` — drop the `OpenLink` import, take it out of the two-argument construction (`ScanViewModel(ResolveTag(graph.tags, graph.assets, graph.uow, graph.clock), FakeIo, graph.ndefCodec, dispatcher)`), and add the pre-split case. `FakeGraph` is Room-backed, so the row is seeded through the real repositories and the resolver sees exactly what the device would:

```kotlin
    /**
     * 2.6 — a link-bound tag is handed to the result sheet as an ordinary (format, key) pair and
     * nothing else happens. `ScanEvent` has no launch member any more, so "never launches" is a
     * compile-time fact; what this pins is that the tag is not swallowed, not turned into an asset
     * and not reported as unreadable.
     */
    @Test fun aLinkBoundTagIsHandedToTheSheetAndNothingElse() = runTest(dispatcher) {
        val key = "123e4567-e89b-12d3-a456-426614174000"
        graph.uow.write {
            graph.links.upsert(ExternalLink(LinkId("l1"), null, LinkKind.JOPLIN, "note", "joplin://x", 1L, null, 1L))
            graph.tags.upsert(
                TagBinding(TagId(key), PayloadFormat.V1, key, TagTarget.LinkTarget(LinkId("l1")), TagStatus.ACTIVE, createdAt = 1L, updatedAt = 1L),
            )
        }
        val viewModel = ScanViewModel(
            ResolveTag(graph.tags, graph.assets, graph.uow, graph.clock),
            ReadableTagIo(graph.ndefCodec.encodeV1(TagId(key))),
            graph.ndefCodec,
            dispatcher,
        )
        val seen = mutableListOf<ScanEvent>()
        val job = launch { viewModel.events.collect { seen += it } }
        viewModel.onTag(FakeHandle)
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf<ScanEvent>(ScanEvent.Show(Route.TagResult("V1", key))), seen)
        assertNull("nothing is reported as a read problem", viewModel.state.value.problem)
    }

    /**
     * A tag whose NDEF is exactly the records handed in: the second half of the case above.
     * `ScanViewModel` reads nothing but `inspection.read`, so the capacity fields are the
     * ordinary-writable-tag values `TagWriteControllerTest`'s fake uses.
     */
    private class ReadableTagIo(private val records: List<NdefRecordData>) : TagIo {
        override fun inspect(tag: TagHandle): TagInspection = TagInspection(
            "04a1",
            TagRead.Readable(records),
            maxSize = 137,
            writable = true,
            needsFormat = false,
            canLock = true,
        )
        override fun format(tag: TagHandle): WriteResult = error("not used")
        override fun write(tag: TagHandle, records: List<NdefRecordData>, lock: Boolean): WriteResult = error("not used")
        override fun lock(tag: TagHandle, expected: List<NdefRecordData>): Boolean = error("not used")
    }
```

(`TagInspection(uid, read, maxSize, writable, needsFormat, canLock)` and the four-member `TagIo` are the library's, read from `libs/nfc-tag-core/nfc-android/src/main/kotlin/com/loosecannon/nfc/tagcore/android/{TagWriter,TagIo}.kt` at `7e0377a`. `libs/` is read, never edited.)

New file `app/src/test/kotlin/com/loosecannon/servicetag/ui/scan/TagResultViewModelTest.kt` — the sheet's state machine and the byte-for-byte sentence:

```kotlin
package com.loosecannon.servicetag.ui.scan

import com.loosecannon.servicetag.core.model.ExternalLink
import com.loosecannon.servicetag.core.model.LinkId
import com.loosecannon.servicetag.core.model.LinkKind
import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagBinding
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.model.TagStatus
import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.core.usecase.BindTag
import com.loosecannon.servicetag.core.usecase.ResolveTag
import com.loosecannon.servicetag.testing.FakeGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 2.6 — the result sheet's answer for a tag written before the split. The row stays, the link row
 * stays, and the only thing that happens is one sentence. The sentence is asserted verbatim here
 * because it is the release's single new user-facing string; `PreSplitLinkTagSheetTest` proves the
 * sheet actually draws it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TagResultViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
    private lateinit var graph: FakeGraph
    private val key = "123e4567-e89b-12d3-a456-426614174000"

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        graph = FakeGraph(queryContext = StandardTestDispatcher(scheduler))
    }

    @After fun tearDown() {
        graph.close()
        Dispatchers.resetMain()
    }

    @Test fun aLinkBoundTagResolvesToThePreSplitStateAndTheRowIsLeftAlone() = runTest(dispatcher) {
        graph.uow.write {
            graph.links.upsert(ExternalLink(LinkId("l1"), null, LinkKind.JOPLIN, "note", "joplin://x", 1L, null, 1L))
            graph.tags.upsert(
                TagBinding(TagId(key), PayloadFormat.V1, key, TagTarget.LinkTarget(LinkId("l1")), TagStatus.ACTIVE, createdAt = 1L, updatedAt = 1L),
            )
        }
        val model = TagResultViewModel(
            ResolveTag(graph.tags, graph.assets, graph.uow, graph.clock),
            BindTag(graph.tags, graph.assets, graph.uow, graph.clock),
            graph.assets,
            PayloadFormat.V1.name,
            key,
        )
        advanceUntilIdle()

        val state = model.state.value
        assertTrue("state was $state", state is TagResult.PreSplitLink)
        assertEquals(TagTarget.LinkTarget(LinkId("l1")), (state as TagResult.PreSplitLink).tag.target)
        assertEquals(1, graph.links.all().size)
    }

    @Test fun theSentenceIsTheOwnersWords() {
        assertEquals(
            "This tag points at a note link from before the product split. " +
                "ServiceTag no longer opens links; NoteTag does.",
            PRE_SPLIT_LINK_SENTENCE,
        )
    }
}
```

`app/src/test/kotlin/com/loosecannon/servicetag/ui/nav/RouteTest.kt` — add the nav-boundary case:

```kotlin
    /**
     * 2.6 — the write route still carries a target *kind* as a string, because a serialised back
     * stack can hold one written by 2.5. "link" is no longer a kind this app writes, and it is
     * refused here, at the boundary, rather than inside a screen that would have to decide what a
     * link write means.
     */
    @Test fun aLinkKindedWriteRouteIsNotSupported() {
        assertFalse(Route.WriteTag("link", "l1", null).isSupported())
        assertTrue(Route.WriteTag("asset", "a1", null).isSupported())
        assertTrue(Route.WriteTag("none", null, null).isSupported())
    }
```

New emulator test `app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/RemovedSurfacesTest.kt` — proofs (2) asked of the platform, not of the source tree:

```kotlin
package com.loosecannon.servicetag.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.6 — the surfaces that are gone, asked of the platform. A grep proves the source tree; this
 * proves the manifest the APK actually shipped, which is what decides whether a share sheet or a
 * `servicetag://link/…` URI can reach ServiceTag at all. The asset host is asserted in the same
 * breath so a manifest that lost everything fails as loudly as one that kept the link host.
 */
@RunWith(AndroidJUnit4::class)
class RemovedSurfacesTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun handlersHere(intent: Intent): Int =
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .count { it.activityInfo.packageName == context.packageName }

    @Test fun nothingInThisPackageAcceptsSharedText() {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "https://example.invalid/x")
        assertEquals(0, handlersHere(send))
    }

    @Test fun theLinkHostIsUnregisteredAndTheAssetHostStillIsNot() {
        val id = "123e4567-e89b-12d3-a456-426614174000"
        assertEquals(0, handlersHere(Intent(Intent.ACTION_VIEW, Uri.parse("servicetag://link/$id"))))
        assertEquals(1, handlersHere(Intent(Intent.ACTION_VIEW, Uri.parse("servicetag://asset/$id"))))
    }
}
```

New emulator test `app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/scan/PreSplitLinkTagSheetTest.kt` — proof (4) as the owner would see it. It uses `clearInstall()` and `awaitText` from `AppSmokeTest.kt`, which are `internal` to the androidTest source set:

```kotlin
package com.loosecannon.servicetag.ui.scan

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loosecannon.servicetag.core.model.ExternalLink
import com.loosecannon.servicetag.core.model.LinkId
import com.loosecannon.servicetag.core.model.LinkKind
import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagBinding
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.model.TagStatus
import com.loosecannon.servicetag.core.model.TagTarget
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
 * 2.6 — what the owner sees when an old link tag is read: one sentence, no launch, no bind offer.
 * The row and the link row are both still there afterwards; the sheet is the whole of the app's
 * response. Emulator only — the suite wipes app data.
 */
@RunWith(AndroidJUnit4::class)
class PreSplitLinkTagSheetTest {

    @get:Rule val rule = createComposeRule()

    private val key = "123e4567-e89b-12d3-a456-426614174000"

    @Before fun freshInstall() = clearInstall()

    @Test fun anOldLinkTagSaysSoAndOffersNothing() {
        val graph = app.graph
        runBlocking {
            graph.uow.write {
                graph.links.upsert(ExternalLink(LinkId("l1"), null, LinkKind.JOPLIN, "note", "joplin://x", 1L, null, 1L))
                graph.tags.upsert(
                    TagBinding(TagId(key), PayloadFormat.V1, key, TagTarget.LinkTarget(LinkId("l1")), TagStatus.ACTIVE, createdAt = 1L, updatedAt = 1L),
                )
            }
        }
        rule.setContent {
            ServiceTagTheme {
                TagResultSheet(
                    graph = graph,
                    format = PayloadFormat.V1.name,
                    key = key,
                    onDismiss = {},
                    onWriteTag = {},
                    onOpenAsset = { error("a pre-split link tag must never open an asset") },
                    onNewAsset = {},
                )
            }
        }

        rule.awaitText(
            "This tag points at a note link from before the product split. " +
                "ServiceTag no longer opens links; NoteTag does.",
        )
        rule.onAllNodesWithText("Bind to asset").assertCountEquals(0)
        rule.onAllNodesWithText("Write a new tag over it").assertCountEquals(0)
        runBlocking { check(graph.links.all().size == 1) { "the tombstone row was touched" } }
    }
}
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :app:testDebugUnitTest --console=plain` → compilation failures naming `PRE_SPLIT_LINK_SENTENCE`, `TagResult.PreSplitLink`, `isSupported` and the narrowed constructors.

- [ ] **Step 3: The manifest.** In `app/src/main/AndroidManifest.xml`: delete the five scheme `<intent>` blocks from `<queries>` (joplin, obsidian, logseq, http, https) and keep the `content` + `*/*` one; the block's comment becomes `<!-- "Open with" for an attachment: is there anything on this device that can view it? (API 30+ package visibility) -->`. Delete the line `<data android:scheme="servicetag" android:host="link" />` and change the filter's comment to `<!-- servicetag://asset|tag: navigation only, validated by shape then by existence. -->`. Delete the whole `<activity android:name="com.loosecannon.servicetag.ShareActivity">` element and its comment. Nothing else in the file changes — in particular `NfcDispatchActivity`'s single `NDEF_DISCOVERED` filter and the `${ndefTagPath}` placeholder stay exactly as they are.

  Removing the `http`/`https` queries is safe: `LinkLauncher` never asks `PackageManager` whether a handler exists — it calls `startActivity` and catches `ActivityNotFoundException`/`SecurityException`. The one `resolveActivity` call in the app is in `ui/attachments/AttachmentPickers.kt` and is a `content:` URI, which the surviving query covers.

- [ ] **Step 4: The scan and dispatch paths.**

`ui/scan/ScanViewModels.kt`:
- Delete the `ExternalLink`, `LinkId`, `LinkRepository` and `OpenLink` imports.
- Add the sentence beside `describe()`, where this file's other prose already lives:

```kotlin
/**
 * The release's one new sentence (owner ruling 2026-09-18). It names the product that does own the
 * job rather than only refusing: an owner holding a tag they wrote in 2025 needs to know where the
 * note went, not merely that this app will not open it.
 */
internal const val PRE_SPLIT_LINK_SENTENCE: String =
    "This tag points at a note link from before the product split. " +
        "ServiceTag no longer opens links; NoteTag does."
```

- `asTagResult()`: replace the `LaunchLink` arm with `is Resolution.PreSplitLink -> Route.TagResult(TagResultWire.wordFor(tag.payloadFormat), tag.payloadKey)` and drop the "`LaunchLink` has no route on purpose" paragraph from the KDoc, replacing it with: *"A pre-split link tag takes the same route as any other row we hold: the pair, and one sheet."*
- `ScanEvent`: delete `Launch`, leaving `data class Show(val route: Route.TagResult) : ScanEvent` as the only member.
- `ScanViewModel`: delete the `openLink` parameter, the `graph.openLink` argument in the secondary constructor and the whole `private suspend fun launch(id: LinkId)`; the resolution handling becomes one line:

```kotlin
                _events.tryEmit(ScanEvent.Show(resolveTag.run(payload).asTagResult()))
```

- `TagResult`: replace `LaunchesLink` with

```kotlin
    /** A tag bound to a pre-split note link: one sentence, and nothing to do (2.6). */
    data class PreSplitLink(val tag: TagBinding) : TagResult
```

- `BindTargets`: `data class BindTargets(val assets: List<Asset> = emptyList())`.
- `TagResultViewModel`: drop the `openLink` and `links` parameters (and `graph.openLink`, `graph.links` from the secondary constructor), drop `launched()`, make `targets` a single-source flow, and map the new outcome:

```kotlin
    val targets: StateFlow<BindTargets> = assets.observeAll()
        .map { BindTargets(assets = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), BindTargets())
```
```kotlin
            is Resolution.PreSplitLink -> TagResult.PreSplitLink(resolution.tag)
```

  (`combine` is no longer used here; `map` is — adjust the imports.)
- `WriteTagViewModel`: drop the `links` parameter and `graph.links`; the naming lookup becomes

```kotlin
            val named = when (target) {
                is TagTarget.AssetTarget -> assets.get(target.assetId)?.name
                is TagTarget.LinkTarget, TagTarget.None -> null
            }
```

`ui/scan/TagResultSheet.kt`:
- Delete the `com.loosecannon.servicetag.ui.links.host` import; keep the `LinkLauncher` import only if `ScanScreen`… no — delete `import com.loosecannon.servicetag.links.LinkLauncher` from this file, it is used only by the removed arm.
- Replace the `is TagResult.LaunchesLink -> { … }` arm with:

```kotlin
            is TagResult.PreSplitLink -> NfcSheet(
                eyebrow = "Tag detected",
                accent = ServiceTagTheme.semanticColors.dueSoon.foreground,
                border = ServiceTagTheme.semanticColors.dueSoon.foreground,
                glyph = Icons.Outlined.Info,
                sentence = PRE_SPLIT_LINK_SENTENCE,
                identifier = result.tag.identityLine(),
                actions = { TextAction("Cancel", onDismiss) },
            )
```

  No bind offer and no overwrite offer: the ruling's "take no other action". The eyebrow, accent, glyph and action label are all existing strings and values.
- Change `"Bind to asset or note"` to `"Bind to asset"` in all three occurrences (the `Unregistered`, `Revoked` and `NotInRecords` arms).
- `BindTargetPicker`: delete the `targets.links.forEach { … }` block, change the empty check to `if (targets.assets.isEmpty())`, and change the KDoc's first line to *"Where a tag may point: an asset, or an asset that does not exist yet — and for that last one the honest answer is to go and make it, then scan the tag again."*

`ui/scan/ScanScreen.kt`: delete the `LinkLauncher` import, and collapse the event collector to

```kotlin
    LaunchedEffect(model) { model.events.collect { event -> onResolved(event.route) } }
```

  and drop `val activity = LocalActivity.current` if nothing else in the file uses it (check; the reader-mode session does — keep it if so). Change the KDoc clause "— except a link tag, which launches its note with no sheet" to "—" so the sentence reads "…`ResolveTag` and shown on the result sheet."

`ui/scan/WriteTagScreen.kt`: delete the `LinkId` import and the `"link" -> …` line from `Route.WriteTag.target()`, leaving `"asset"` and `else -> TagTarget.None`.

`nfc/NfcDispatchActivity.kt`: delete the `ExternalLink`, `OpenLink` and `LinkLauncher` imports, delete `private fun launch(link: ExternalLink)`, and replace `route`:

```kotlin
    /** Every resolution becomes the very route the foreground scanner would have produced, so the
     *  two paths say the same words about a tag. Nothing is launched from here (2.6). */
    private fun route(r: Resolution) = r.asTagResult().let { handOff(it.format, it.key) }
```

  and drop "a link tag launches straight away (R-7) and" from the class KDoc, so it reads "It has no UI at all: everything is handed to `MainActivity` as a (format, key) pair, …".

`MainActivity.kt`: delete the `is DeepLink.Link -> Route.LinkDetail(link.id.value)` arm. The `null -> null` arm already handles the withdrawn host, and the manifest no longer routes it here at all.

- [ ] **Step 5: Navigation and the removed screens.**

`ui/nav/Route.kt`: delete `Links` and `LinkDetail`, and append the boundary predicate:

```kotlin
/**
 * The target kinds this app writes. 2.6 removed "link": a serialised back stack or an old process
 * can still carry `WriteTag("link", …)`, and it is refused at the nav boundary — no screen, no
 * provisioned row — rather than inside the write screen.
 */
internal val SupportedWriteTargetKinds: Set<String> = setOf("asset", "none")

internal fun Route.WriteTag.isSupported(): Boolean = targetKind in SupportedWriteTargetKinds
```

`ui/nav/ServiceTagRoot.kt`: delete the `LinkDetailScreen`/`LinksScreen` imports and the `entry<Route.Links>` and `entry<Route.LinkDetail>` blocks; delete `onOpenLinks = { backStack.add(Route.Links) },` from both the `Route.Dashboard` and the `Route.AssetDetail` entries; and guard the write entry:

```kotlin
                entry<Route.WriteTag> { key ->
                    if (key.isSupported()) {
                        WriteTagScreen(graph = graph, key = key, onDone = { backStack.removeLastOrNull() })
                    } else {
                        // 2.6: a link-kinded route is pre-split navigation. No screen, nothing
                        // provisioned, nothing written — it simply leaves the stack.
                        LaunchedEffect(key) { backStack.removeLastOrNull() }
                    }
                }
```

Delete the five screen files: `git rm app/src/main/kotlin/com/loosecannon/servicetag/ShareActivity.kt app/src/main/kotlin/com/loosecannon/servicetag/ui/share/ShareFlow.kt app/src/main/kotlin/com/loosecannon/servicetag/ui/share/ShareCardScreen.kt app/src/main/kotlin/com/loosecannon/servicetag/ui/links/LinksScreen.kt app/src/main/kotlin/com/loosecannon/servicetag/ui/links/LinkDetailScreen.kt app/src/main/kotlin/com/loosecannon/servicetag/ui/links/LinkViewModels.kt` (this takes `host()`, `LinkKind.label()` and `kindLabel()` with them — after Step 4 nothing outside `ui/links` calls any of the three).

- [ ] **Step 6: Dashboard, asset screen, graph, debug harness.**

`ui/dashboard/DashboardViewModel.kt`: delete the `LinkRepository` import, the `links` parameter and `graph.links`; the flow becomes a two-way combine and the nudge asks about assets alone:

```kotlin
    val state: StateFlow<DashboardState> =
        combine(assets.observeAll(), refreshes) { rows, _ ->
            val last = prefs.lastBackupAt
            val active = rows.filter { it.status == AssetStatus.ACTIVE }
            val inService = active.filterNot { it.isRetired }
            DashboardState(
                assets = inService,
                // An empty install has nothing to lose, and a nudge over an empty dashboard is
                // noise: the offer only means something once there is something to survive the
                // phone change.
                needsBackup = last == null && active.isNotEmpty(),
                lastBackupAt = last,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), DashboardState())
```

`ui/dashboard/DashboardScreen.kt`: delete the `onOpenLinks: () -> Unit,` parameter, the two KDoc lines about it, and the now-unneeded `@Suppress("UNUSED_PARAMETER")` if `onOpenLinks` was its only reason (check the other parameters first; if another is unused, keep it).

`ui/asset/AssetViewModels.kt`: delete the `ExternalLink` and `LinkRepository` imports, the `links` parameter, `graph.links`, and `val links: List<ExternalLink> = emptyList(),` from `AssetDetailState`; the combine loses its link flow and the `links = linkRows,` line:

```kotlin
        combine(rows, tags.observeForAsset(id), journal) { all, tagRows, j ->
```

  and the KDoc's "Six flows feed the state and `combine` takes five" becomes "Five flows feed the state and `combine` takes three".

`ui/asset/AssetDetailScreen.kt`: delete the `ExternalLink` import, the `onOpenLinks` parameter (both on `AssetDetailScreen` and on `detailActions`), the `onOpenLinks = onOpenLinks,` argument, the `add(ActionSpec("Links", documents, outlined = false, onClick = onOpenLinks))` line, the `LinksSection(current.links)` call and the whole `private fun LinksSection`. `val documents = ServiceTagIcons.Description` is still used by nothing else in `detailActions` — delete that `val` too (the attachments section has its own icon). Change the class KDoc's "(category, name, description, tags and links)" to "(category, name, description and tags)". `NotesSection` stays: it draws the asset's own free-text notes, which is maintenance data, not a note link.

`di/AppGraph.kt`: delete the `DeleteLink`, `OpenLink` and `SaveLink` imports; delete `val saveLink`, `val openLink` and `val deleteLink` with the "A link is a pointer…" comment; narrow the three constructions:

```kotlin
    val resolveTag: ResolveTag = ResolveTag(tags, assets, uow, clock)
    val bindTag: BindTag = BindTag(tags, assets, uow, clock)
    val provisionTag: ProvisionTag = ProvisionTag(tags, assets, uow, ids, clock)
```

  `val links: LinkRepository = RoomLinkRepository(db.externalLinkDao())` **stays**, with its two consumers `exportBackupSet` and `importBackupReplace` unchanged; add the comment `// The tombstone port: backup export and restore, and nothing else (2.6).` above it. `SCHEMA_VERSION = 5` and the four `addMigrations` entries are untouched.

`app/src/debug/kotlin/.../debug/DebugBackupActivity.kt`: the debug harness must not be a way to create link rows. Delete the `ExternalLink`, `LinkId`, `LinkKind` imports, the `linkIds` val, both `g.links.upsert(…)` blocks with their comment, and change the third seeded tag from `target = TagTarget.LinkTarget(linkIds[1])` to `TagTarget.None` with `status = TagStatus.UNBOUND` — the seed then covers the two live shapes (asset, none). Change the report string to `"seeded 2 assets, 3 tags"` and the seed comment to `// one tag on an asset, two unbound spares`. `wipe()`'s `g.links.deleteAll()` and `refreshCounts`' `links ${g.links.all().size}` **stay**: both are tombstone reads, and the counts line is how the restore proof is eyeballed.

- [ ] **Step 7: The remaining test amendments.**

- `testing/FakeGraph.kt`: delete the `DeleteLink` import and `val deleteLink`; `ProvisionTag(tags, assets, uow, ids, clock)`. `val links: LinkRepository = RoomLinkRepository(db.externalLinkDao())` and both backup use cases stay.
- `ui/scan/TagWriteControllerTest.kt`: `ProvisionTag(provision, graph.assets, graph.uow, graph.ids, graph.clock)`.
- `nfc/TagUseCasesRoomTest.kt`: delete the two `val links = RoomLinkRepository(db.externalLinkDao())` lines and the `RoomLinkRepository` import; `ProvisionTag(tags, assets, uow, UuidGenerator, Clock { 42L })`, `ResolveTag(tags, assets, uow, Clock { 43L })`, `ProvisionTag(tags, assets, uow, UuidGenerator, Clock { 1L })`.
- `ui/dashboard/DashboardViewModelTest.kt`: `DashboardViewModel(graph.assets, graph.prefs)`; delete `standaloneLink()`, its KDoc, the `graph.links.upsert(standaloneLink())` line and the `ExternalLink`/`LinkId`/`LinkKind` imports. If the case that used it asserted the nudge for a links-only install, replace that case with its true 2.6 statement: an install with no assets and nothing else shows **no** nudge —

```kotlin
    @Test fun anInstallWithNothingInItIsNotNudged() = runTest {
        val model = viewModel()
        model.refresh()
        assertFalse(model.state.value.needsBackup)
    }
```
- `ui/asset/AssetViewModelsTest.kt`: `AssetDetailViewModel(graph.assets, graph.tags, graph.definitions, …)` (drop `graph.links`) and delete `assertTrue(state.links.isEmpty())`.
- `data/room/RoomRepositoriesTest.kt`: keep both link cases, moving the removed-member calls onto the untouched DAO. In `tagWithLinkTargetAndNoneTargetRoundTrip` the `links.upsert(...)` and `tags.forLink(...)` calls are unchanged (`upsert` survives; `forLink` is on `TagRepository`). In `linkRoundTripsAndPartitionsStandaloneFromOwned` replace `links.forAsset(AssetId("a1"))`, `links.standalone()` and `links.delete(LinkId("l2"))` with `db.externalLinkDao().forAsset("a1").map { it.id }`, `db.externalLinkDao().standalone().map { it.id }` and `db.externalLinkDao().delete("l2")`, comparing ids rather than domain objects for those three, and add a line to the case's KDoc: *"The partition queries are asked of the DAO now: 2.6 narrowed `LinkRepository` to what the backup path needs, and the table itself is a tombstone the DAO still describes in full."*
- `data/room/RepositoryFlowsTest.kt`: delete `linksObserveAllEmitsOnDelete`, the `link(...)` helper and the `ExternalLink`/`LinkId`/`LinkKind`/`RoomLinkRepository` imports — it tested `observeAll`, a display path that no longer exists. `ExternalLinkDaoTest` already covers the table's reads.
- `data/room/Migration1To5Test.kt`: change the one repository call to the DAO — `assertEquals(listOf("l1"), db.externalLinkDao().forAsset("a1").map { it.id })` — and delete the `LinkId`, `AssetId` (if now unused) and `RoomLinkRepository` imports. **The migration itself, the SQL fixtures and the `external_link` insert stay exactly as they are.**
- `backup/RestoreProofTest.kt`: the fixtures, the two seeded links, the link-bound tag and every count stay. Change only the two removed-member reads at the end of `forgetEverythingAndRebuildTheSameGraph`:

```kotlin
            assertEquals(1, g2.db.externalLinkDao().forAsset("asset-furnace").size)
            assertEquals(listOf("link-standalone"), g2.db.externalLinkDao().standalone().map { it.id })
```
  and, in `Graph`, narrow `ResolveTag`/`BindTag`/`ProvisionTag` if it builds any (it builds the two backup use cases only — check and leave alone if so).
- `app/src/androidTest/.../ui/AppSmokeTest.kt`: delete the `ShareActivity` import and the whole `ShareActivitySmokeTest` class with its KDoc. `clearInstall()`'s `graph.links.deleteAll()` **stays** — the fixture must still be able to clear a tombstone row a restore left behind. `DeepLinkSmokeTest` stays as it is.

- [ ] **Step 8: Run to verify they pass, and check the tombstone is untouched**

Run: `./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`. Record every `:app` class's count from `app/build/test-results/testDebugUnitTest/*.xml`; `ExternalLinkDaoTest` must still be 5 and `Migration1To5Test` 1.
Run: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --console=plain` → `BUILD SUCCESSFUL`, with `RemovedSurfacesTest` 2 and `PreSplitLinkTagSheetTest` 1 in `app/build/reports/androidTests/connected/`.
Run: `git diff --stat -- app/src/main/kotlin/com/loosecannon/servicetag/data/room app/schemas app/src/main/kotlin/com/loosecannon/servicetag/links app/src/test/kotlin/com/loosecannon/servicetag/data/room/ExternalLinkDaoTest.kt app/src/test/kotlin/com/loosecannon/servicetag/data/room/AssetDaoTest.kt app/src/test/kotlin/com/loosecannon/servicetag/data/room/NfcTagDaoTest.kt` → **no output**.
Run: `git grep -nE 'ShareActivity|ShareFlow|ShareCardScreen|LinksScreen|LinkDetailScreen|LinkViewModels|Route\.Links|Route\.LinkDetail|onOpenLinks|LaunchesLink|LaunchLink|ScanEvent\.Launch|OpenLink|SaveLink|LinkLaunchPolicy' -- 'app/**' 'core/**'` → 0 lines.
Run: `git grep -nE 'joplin|obsidian|logseq' -- 'app/src/main/**' 'core/src/main/**'` → 0 lines.
Run: `git grep -n 'Bind to asset or note' -- 'app/**'` → 0 lines.
Run: `git grep -c 'links' app/src/main/kotlin/com/loosecannon/servicetag/di/AppGraph.kt` → the two backup lines plus the declaration only.

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "drop the links and share surfaces; an old link tag just says so"
```

---

### Task 3: README, the version bump, and the two design documents

**Files:**
- Modify: `README.md`, `app/build.gradle.kts`, `docs/architecture/product-split-target.md`, `docs/architecture/product-split-migration.md`
- **Not modified:** `docs/architecture/product-split-evidence.md` (the controller writes the evidence after Task 4), `docs/design/**` (history — never rewritten), `docs/architecture/product-split-archaeology.md`

**Interfaces:** `BuildConfig.VERSION_NAME` becomes `"2.6"`, which `SettingsScreen` shows and `ExportBackupSet` writes into `BackupManifest.appVersion`; `tools/release-dry-run.sh` reads the same `versionName` out of `app/build.gradle.kts`.

- [ ] **Step 1: The version bump.** In `app/build.gradle.kts`, inside `defaultConfig`:

```kotlin
        versionCode = 8
        versionName = "2.6"
```

  Nothing else in the file changes. There is no `schemaVersion` here to touch; `AppGraph.SCHEMA_VERSION` stays 5.

- [ ] **Step 2: The README.** Delete lines 73–87 — the `### Carried over from before the split` heading and both paragraphs under it — and put in their place, between the Settings bullet (ending line 71) and `## Building`:

```markdown
### Note links are NoteTag's

Sharing a note or a web link to an NFC tag is not part of ServiceTag. That utility lives in
[NoteTag](https://github.com/GonzRon/NoteTag), and ServiceTag 2.6 removed it from this app: there is
no Links screen, no share target, no way to point a tag at a link, and no outbound-link allowlist.

Old data is kept, not discarded. A backup written by any earlier version still carries its
`externalLinks` rows and restores them unchanged — the format is untouched at 5 — but nothing in
ServiceTag creates, shows or opens one. A tag written by an older version to point at a link reads
as a tag from before the split and does nothing else.
```

  Two further edits, so the product description is strictly assets/service/maintenance:
  - line 27: `identity plate (category, name, description, tags and links), a create/edit form, and archive` → `identity plate (category, name, description and tags), a create/edit form, and archive`
  - line 65: `asset, tag binding, link and attachment record with its original id, and a second holding the` → `asset, tag binding and attachment record with its original id, and a second holding the`

  Line 71's "a link to the project" is a hyperlink, not the feature — it stays byte-identical, as do lines 181–194, which describe the split as history.

- [ ] **Step 3: `docs/architecture/product-split-target.md`.** Two edits, both in place, no section renumbering.

  The deep-link row of the **§3 Android identities** table (line 139 — the row the brief calls §2's hosts row; the hosts live in §3's table):

```markdown
| Deep-link scheme | `notetag` — **reserved; no `VIEW` filter declared at reconstruction** (ratified P4). Held for #6/#36 | `servicetag`, hosts `asset`, `tag` — the `link` host was **withdrawn at 2.6** (owner ruling 2026-09-18, Option B): the manifest no longer declares it and `DeepLinkRoute` no longer parses it | O1 / O3 |
```

  The **§5 What deliberately stays app-specific** table (the "what stays in each app" statement the brief points at, whose §4.7 companion row needs no change): amend the two note-link rows and add the tombstone row after them.

```markdown
| ~~**Share flow** — `ShareActivity`, `ShareFlow`, `ShareCardScreen`~~ | **NoteTag's only, from ServiceTag 2.6.** NoteTag's is the whole product (§23); ServiceTag's was one way to create a link and is **deleted** — no `ACTION_SEND` filter, no activity, no flow. The `EXTRA_TEXT`-as-`CharSequence` pattern survives in NoteTag's codebase alone |
| ~~**Link-launch policy** — `LinkLaunchPolicy`, `OpenLink`, `SaveLink`~~ | **NoteTag's only, from ServiceTag 2.6.** "The allowlist is a **product decision**, not a mechanism" (arch §6.1) — and at 2.6 ServiceTag's decision is that it has no outbound link product at all, so its `{joplin, obsidian, logseq, http, https}` allowlist, its save-time and launch-time gates and its five manifest `<queries>` entries are **deleted**. NoteTag writes its own from first principles and diverges under #6/#36. `LinkLauncher` **stays in ServiceTag**: it is a crash-safe `ACTION_VIEW` wrapper that Settings uses for the project URL, keeping the `ActivityNotFoundException` **and** `SecurityException` catch the 2024 crash bought (arch §2.5) |
| **The note-link data shape, as a tombstone** (ServiceTag, from 2.6) | The behaviour is gone; the *shape* stays until an explicit cleanup, because the format-5 migration contract was proven at §C.6 and must keep standing: `external_link`, `ExternalLinkEntity`, `ExternalLinkDao`, `TagTarget.LinkTarget`, `LinkKind`, `ExternalLinkDto` and `BackupData.externalLinks` are unchanged, export still carries every row and restore still writes every row byte-for-byte. `LinkRepository` is narrowed to `upsert`/`get`/`all`/`deleteAll` — the backup path and nothing else — and no code path creates, modifies, displays or launches a row. A tag whose target is a link resolves to `Resolution.PreSplitLink` and says one sentence. No cleanup migration exists, and discarding the rows would be its own ruled change |
```

- [ ] **Step 4: `docs/architecture/product-split-migration.md` §G.** Insert one row directly after the `**ServiceTag's first tag**` row (line 1037):

```markdown
| **ServiceTag's second tag** | **`servicetag-v2.6`** on the commit that passes the eight 2.6 proofs: the note-link product removed from the app at the behavioural level (Option B, owner ruling 2026-09-18), `versionCode` 8 / `versionName` 2.6, `external_link` and the format-5 `externalLinks` field kept as compatibility tombstones — **no schema bump, no format bump, no cleanup migration**. Published by the same tag-only `release.yml` as 2.5, which still refuses to publish unless the tag's version equals the built `versionName` (G-4) |
```

- [ ] **Step 5: Verify**

Run: `git diff --stat -- docs/design docs/architecture/product-split-archaeology.md docs/architecture/product-split-evidence.md` → **no output**.
Run: `grep -n 'Carried over from before the split' README.md` → 0 lines; `grep -c 'NoteTag](https://github.com/GonzRon/NoteTag)' README.md` → at least 1.
Run: `grep -n 'versionCode\|versionName' app/build.gradle.kts` → `versionCode = 8`, `versionName = "2.6"`.
Run: `./gradlew :app:assembleDebug --console=plain` then `"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging app/build/outputs/apk/debug/app-debug.apk | grep -o "versionName='[^']*'"` → `versionName='2.6'`. (Per the memory note, `aapt2` is at build-tools **36.0.0**.)
Run: `grep -n "hosts \`asset\`, \`tag\`" docs/architecture/product-split-target.md` → 1 line; `grep -n 'servicetag-v2.6' docs/architecture/product-split-migration.md` → 1 line.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "2.6: readme says links are NoteTag's, bump the version, note it in the design"
```

---

### Task 4 (controller-run): the eight proofs

**Files:** none. This task runs and records; the evidence file is the controller's to write afterwards.

**Interfaces:** the recorded verdicts for proofs (1)–(8), each with the command that produced it.

- [ ] **Step 1: Proofs (1) and (3) — nothing reachable, nothing bindable.** From the repository root:

```bash
git grep -nE 'ShareActivity|ui\.share|ui\.links|Route\.Links|Route\.LinkDetail|onOpenLinks|LinkLaunchPolicy|OpenLink|SaveLink|DeleteLink|LaunchLink|LaunchesLink' -- 'app/src/main/**' 'core/src/main/**' | wc -l   # 0
git grep -nE 'joplin|obsidian|logseq' -- 'app/src/main/**' 'core/src/main/**' | wc -l                                  # 0
git grep -n 'host="link"' app/src/main/AndroidManifest.xml | wc -l                                                     # 0
./gradlew :core:test :app:testDebugUnitTest --console=plain
```

Expected: the four counts are 0, `BUILD SUCCESSFUL`, and in the XML `TagBindingUseCasesTest` carries `bindingALinkTargetIsRefusedAndWritesNothing` and `provisioningALinkTargetIsRefusedAndWritesNothing` as passes, and `RouteTest` carries `aLinkKindedWriteRouteIsNotSupported` — proof (3) is those three cases plus the type-level fact that `BindTag`/`ProvisionTag` no longer hold a `LinkRepository`.

- [ ] **Step 2: Proofs (2) and (4) — the manifest and the safe fail, on the emulator.**

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --console=plain
```

Expected: `BUILD SUCCESSFUL`; in `app/build/reports/androidTests/connected/` `RemovedSurfacesTest` is 2/2 (0 share handlers, 0 `servicetag://link` handlers, 1 `servicetag://asset` handler) and `PreSplitLinkTagSheetTest` is 1/1 (the exact sentence displayed, no bind action, no overwrite action, the tombstone row still present). Record the class list, counts and the run's timestamp. `AppSmokeTest`'s `ShareActivitySmokeTest` is gone from the report — that is the expected delta, not a regression.

- [ ] **Step 3: Proof (6) — format-5 backup/restore from the preserved 2026-09-18 set, on the emulator.** Resolve the set's file names first, then push the data archive world-readable into `/data/local/tmp` (mode 0771 gives others traverse, so an app-uid test can open a 0644 file there; nothing is written to the phone and nothing personal is printed):

```bash
SET=~/Documents/Projects/AndroidStudioProjects/noteNFC-backups/transition-20260918-075440
ls -1 "$SET" | sed -n '1,20p'                     # names the preserved data and artifacts archives
DATA=$(ls "$SET"/*data*.zip | head -1)
adb -s emulator-5554 push "$DATA" /data/local/tmp/servicetag-proof-data.zip
adb -s emulator-5554 shell chmod 644 /data/local/tmp/servicetag-proof-data.zip
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.loosecannon.servicetag.backup.PreservedSetRestoreTest \
  --console=plain
adb -s emulator-5554 shell rm -f /data/local/tmp/servicetag-proof-data.zip
```

The test is the one file this task creates if it is not already present from a prior proof round — write it before the run, at `app/src/androidTest/kotlin/com/loosecannon/servicetag/backup/PreservedSetRestoreTest.kt`:

```kotlin
package com.loosecannon.servicetag.backup

import com.loosecannon.servicetag.core.backup.BackupCodec
import com.loosecannon.servicetag.ui.app
import com.loosecannon.servicetag.ui.clearInstall
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * 2.6 proof (6) — the real pre-split set, restored into a 2.6 install. The link row the owner has
 * had since before the split must arrive, stay, and leave again in the next export's
 * `externalLinks` array byte-for-byte. Nothing from the set is printed: the assertions compare, the
 * report counts.
 *
 * Skipped unless the controller has pushed the archive; ordinary CI never runs connected tests.
 */
class PreservedSetRestoreTest {

    private val archive = File("/data/local/tmp/servicetag-proof-data.zip")

    @Before fun freshInstall() {
        assumeTrue("the preserved set was not pushed", archive.canRead())
        clearInstall()
    }

    /** `BackupCodec.readEntries` is private, so the archive's `data.json` is unzipped here. */
    private fun dataJson(archive: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == BackupCodec.DATA_ENTRY) return zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        error("no ${BackupCodec.DATA_ENTRY} in the archive")
    }

    /** The `externalLinks` array as it appears in a data.json, sliced by its neighbours' keys. */
    private fun linksSlice(json: String): String =
        json.substring(json.indexOf("\"externalLinks\""), json.indexOf("\"measurementDefinitions\""))

    @Test fun thePreservedSetRestoresAndItsLinkRowsComeBackOutUnchanged() {
        val bytes = archive.readBytes()
        val preserved = BackupCodec.decode(bytes)
        assertEquals(BackupCodec.FORMAT_VERSION, preserved.manifest.formatVersion)
        assertEquals(5, preserved.manifest.formatVersion)

        val graph = app.graph
        val report = runBlocking { graph.importBackupReplace.run(bytes) }
        assertEquals(preserved.data.externalLinks.size, report.links)
        assertEquals(preserved.data.externalLinks.size, runBlocking { graph.links.all().size })

        // `ExportBackupSet.run(): BackupSet(data, plan)` — the data archive's bytes are `data`.
        val reexported = runBlocking { graph.exportBackupSet.run() }
        val out = BackupCodec.decode(reexported.data)
        assertEquals(preserved.data.externalLinks, out.data.externalLinks)
        // The manifest legitimately differs (backupSetId, createdAt, appVersion — the C.8 fields);
        // the tombstone slice of data.json must not.
        assertEquals(linksSlice(dataJson(bytes)), linksSlice(dataJson(reexported.data)))
    }
}
```

Expected: `formatVersion` 5, the imported link count equal to the preserved set's, the domain rows equal, and the two `externalLinks` JSON slices identical. Record the counts only — never the URI or the label.

- [ ] **Step 4: Proof (5) — the phone's data is untouched.** Nothing in this change installs on, reads from or writes to the phone. Record it as a **procedural** claim with its evidence: `git log --oneline -3` shows the three commits; `history | grep -c 'adb .*-s [^e]'` — or the session's own command log — shows no `adb` invocation without `ANDROID_SERIAL=emulator-5554`; and `adb devices` is not consulted for the phone at all. The owner's historical link row is untouched because the row is only reachable through the phone's own database, which this change never opens. **Do not offer the owner a manual checklist**; if a device-side confirmation is ever wanted it is one later export-and-compare, ruled separately.

- [ ] **Step 5: Proof (7) — ordinary CI.** The repository's `ci.yml` runs on every push. Because nothing is pushed here, run its exact command locally and record it as the local equivalent, to be re-recorded from the runner when the controller pushes:

```bash
bash tools/check-submodule-pin.sh
./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain
git -C libs/nfc-tag-core status --porcelain | wc -l     # 0 — libs/ untouched
git ls-tree HEAD libs/nfc-tag-core                      # 7e0377a…
```

Expected: the pin script passes, `BUILD SUCCESSFUL`, `0`, and the gitlink still at `7e0377a`.

- [ ] **Step 6: Proof (8) — the release signing dry run.**

```bash
bash tools/release-dry-run.sh
```

Expected: `RELEASE DRY RUN: PASS` with exit 0, the built `versionName` compared against `app/build.gradle.kts` and found to be **2.6**, exactly one signer, and the certificate matching the configured fingerprint. Record the verdict line and the exit code only — never a fingerprint, a path or a password. A `PARTIAL` is not a pass for this release: 2.5 established the ServiceTag key and the `RELEASE_CERT_SHA256` value, so the compare must happen. If the script exits 3 (no signing material on this machine), that is a **BLOCKED** proof, not a pass, and the controller stops and says so.

- [ ] **Step 7: Record and hand back.** Assemble one table — proof, command, expected, observed, verdict — for (1)–(8), plus the per-task gates' test counts and the connected run's class list. The phase ends **"ServiceTag 2.6 complete locally; the note-link product is removed and the tombstone stands; push and `servicetag-v2.6` pending owner authorization."** No push, no tag, no `gh` call.

---

## Self-review against the ruling

| Requirement | Where |
|---|---|
| Links list/detail UI and navigation | Task 2 Step 5 (files deleted, `Route.Links`/`LinkDetail` and both entries gone) |
| Share target and flow | Task 2 Steps 3, 5 (manifest element + `ShareActivity` + `ui/share/`) |
| No way to create or bind a tag to a link | Task 1 Step 4 (`LinkTargetUnsupported` in the shared guard), Task 2 Steps 4–5 (picker rows gone, `Route.WriteTag("link", …)` refused, `target()` branch gone) |
| `servicetag://link/...` handling | Task 1 Step 5 (`DeepLink.Link` and the host), Task 2 Steps 3–4 (manifest `<data>`, `MainActivity` arm) |
| The Joplin/Obsidian/Logseq/generic launch policy | Task 1 Step 5 (`LinkLaunchPolicy`, `OpenLink`, `SaveLink` deleted), Task 2 Step 3 (the five `<queries>`) |
| Link branches in scan/dispatch | Task 2 Step 4 (`ScanViewModels`, `ScanScreen`, `TagResultSheet`, `NfcDispatchActivity`) |
| Settings, menu items, strings, icons, intents, manifest entries, Compose routes | Task 2 Steps 3–6; `SettingsScreen` has no link surface (its `LinkLauncher` use is the project URL and stays); no icon is deleted because `ic_description` is also the attachments glyph |
| Feature-only tests deleted | Task 1 Step 5 (`LinkLaunchPolicyTest`, `LinkUseCasesTest`, `DeleteLinkTest`), Task 2 Step 7 (`ShareActivitySmokeTest`, `RepositoryFlowsTest.linksObserveAllEmitsOnDelete`) |
| Tests retained/replaced to prove safe handling | Task 1 Step 1 (`ResolveTagTest`, `TagBindingUseCasesTest`, `BackupUseCasesTest` + the new tombstone case), Task 2 Step 1 (`ScanViewModelTest`, `TagResultViewModelTest`, `RouteTest`, `RemovedSurfacesTest`, `PreSplitLinkTagSheetTest`), Task 4 Step 3 (`PreservedSetRestoreTest`) |
| Tombstone paths untouched | `BackupCodec`, `BackupFormat`, `ExportBackupSet`, `ImportBackupReplace`, `ExternalLinkEntity`, `ExternalLinkDao`, `Mappers`, `Migrations`, `app/schemas/`, `ExternalLinkDaoTest`, `AssetDaoTest`, `NfcTagDaoTest`, `BackupCodecTest` — named in Task 1 and Task 2's **Untouched** lists and asserted by the two empty `git diff --stat` checks |
| Safe fail, never a launch, never an asset | `Resolution.PreSplitLink` (Task 1 Step 3) + `TagResult.PreSplitLink` and the sheet arm (Task 2 Step 4); `ScanEvent.Launch` and `TagResult.LaunchesLink` deleted, so "never launches" is a compile-time fact; `ResolveTagTest.aLinkBoundTagIsPreSplitAndNeverAnAsset` seeds an asset under the link's id to pin "never an asset" |
| No schema/format bump, no discard, no cleanup migration | Global Constraints; `AppGraph.SCHEMA_VERSION = 5` and the four migrations untouched (Task 2 Step 6); `Migrations.kt` and `app/schemas/` in the untouched checks |
| README section and product description | Task 3 Step 2 |
| `versionCode` 8 / `versionName` 2.6 | Task 3 Step 1 |
| Design docs | Task 3 Steps 3–4 |
| The eight proofs | Task 4 |

Every type named above was read in the tree at `dbe4cc2` or is defined by a task: `Resolution.PreSplitLink`, `LinkTargetUnsupported`, `TagResult.PreSplitLink`, `PRE_SPLIT_LINK_SENTENCE`, `SupportedWriteTargetKinds`, `Route.WriteTag.isSupported`, `RemovedSurfacesTest`, `PreSplitLinkTagSheetTest`, `TagResultViewModelTest` and `PreservedSetRestoreTest` are new; everything else — `TagTarget.LinkTarget`, `LinkKind`, `ExternalLink`, `LinkRepository`, `requireTargetExists`, `UnknownTarget`, `NfcSheet`, `TextAction`, `ServiceTagIcons.Description`, `ServiceTagTheme.semanticColors.dueSoon`, `TagResultWire.wordFor`, `MainActivity.EXTRA_TAG_FORMAT`, `clearInstall`, `awaitText`, `FakeGraph`, `tools/release-dry-run.sh` — exists today.
