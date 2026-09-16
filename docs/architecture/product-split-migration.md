# Product-split migration runbook — ServiceTag / noteNFC / nfc-tag-core

The ordered *how*. Companion to `docs/architecture/product-split-target.md` (the *what*) and
`docs/architecture/product-split-archaeology.md` (the evidence, cited as "arch §n").

**How to read this.** Every step has **Do**, **Verify**, and **Rollback**. No step is complete until
its Verify passes; no step may be started until the previous one's Verify has passed. The
controller's standing rule applies to every destructive or device-touching step: **snapshot before,
verify after** — and a step with no snapshot is not started.

**Standing constraints.**

- Nothing is pushed, renamed or transferred until the local split builds and passes (D7's own rule:
  "remote rename only after the local split and builds are proven").
- The attached phone holds the owner's **real data**. No instrumented suite runs on it — an
  instrumented run wipes app data. Automation targets the emulator; the phone gets only the steps
  in §C, §D and §E, and only with a fresh export in hand.
- `adb` in this runbook is always pinned to the intended device with `ANDROID_SERIAL` exported once
  by the operator; serials appear nowhere in this document.
- No secrets, no key material, no fingerprints reproduced, no home paths beyond `~`, no phone model
  or codename, no phone folder names.
- Claims about platform behaviour nobody has watched are marked **to observe on-device** with the
  archaeology's question number.
- **[P*n*]** markers continue the target document's proposal numbering; new ones start at P13 and
  are collected in §K.

**Preserved artifacts this runbook depends on** (all outside every working tree, all already in
place per the Phase A checkpoint):

| Artifact | Location | What it is |
|---|---|---|
| Rollback APK | `~/Documents/Projects/AndroidStudioProjects/noteNFC-releases/` | the signed 2.4 / versionCode 6 build of `ac523d7`, signer DN `CN=noteNFC, O=GonzRon`, certificate SHA-256 recorded in the checkpoint ledger (arch §4.5) |
| Pre-split backup set | `~/Documents/Projects/AndroidStudioProjects/noteNFC-backups/pre-split-2026-09-16/` | a data archive at format 5 (9 419 B) and an artifacts archive at artifacts format 1 (13 651 544 B) sharing one `backupSetId`, 8 entries hash-verified, plus a database + prefs snapshot (arch §7 intro, §7.5) |
| Pre-rewrite bundle | alongside the project directories | the history bundle taken before the 2026-09-14 `git filter-repo` rewrite (arch §4.1) |
| Recovery refs | on `origin` **and** locally | tag `pre-split-checkpoint` and branch `pre-split-master`, both at `ac523d7` (arch §4.1) |

---

## A. Git and local transition

Three local repositories are built and proven before anything remote moves. Order matters: the
library is extracted from ServiceTag *after* ServiceTag's identity conversion, so the extraction
happens once against final code and the provenance table in the target document's §4.6 stays true.

### A.0 Pre-flight

**Do.**

```bash
cd ~/Documents/Projects/AndroidStudioProjects/noteNFC
git fetch --all --tags
git rev-parse pre-split-checkpoint^{commit} refs/heads/pre-split-master origin/master
git -C ../noteNFC-split rev-parse HEAD        # the product-split worktree
```

**Verify.** All three refs resolve to `ac523d7`; the `product-split` worktree is on `product-split`
with the docs package committed; `git status` clean in both checkouts.

**Rollback.** None needed — read-only.

### A.1 ServiceTag identity conversion (on `product-split`)

An SDD-style task list. Each task is one commit, has its own verification, and is independently
revertible. The scale is the reason for the list: `com.loosecannon.notenfc` occurs **2 028 times
across 236 files** (arch §4.6, §9 D4), so this is a mechanical sweep with a handful of judgment
calls hidden in it — and the judgment calls are the tasks with their own numbers.

| # | Task | Files | Verify |
|---|---|---|---|
| **1** | `rootProject.name = "ServiceTag"` | `settings.gradle.kts` | configuration succeeds; `./gradlew projects` shows the new root name |
| **2** | `namespace` and `applicationId` → `com.loosecannon.servicetag`; keystore path → `~/.config/servicetag/keystore.properties`; `versionCode`/`versionName` bumped | `app/build.gradle.kts` | `./gradlew :app:assembleDebug`; `aapt2 dump badging` on the APK shows the new package |
| **3** | Move the Kotlin package roots: `app/src/{main,debug,test,androidTest}/kotlin/com/loosecannon/notenfc/…` → `…/servicetag/…` and `core/src/{main,test}/kotlin/com/loosecannon/notenfc/core/…` → `…/servicetag/core/…`; rewrite every `package`/`import` line | 233 `.kt` files + both manifests + `app/build.gradle.kts` (arch §9 D4) | `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug`; `git grep -l 'com\.loosecannon\.notenfc' -- app core gradle` is empty |
| **4** | `NdefCodec.DOMAIN` → `com.loosecannon.servicetag`; `PACKAGE_NAME` → `com.loosecannon.servicetag`. **These are two different things** — an NFC Forum external-type domain and an Android `applicationId` — and are edited as two separate decisions even though the literal is the same (arch §5.2, §6.3) | `core/…/core/nfc/NdefCodec.kt:37,46` | `NdefCodecV1Test` byte vectors updated and green; the AAR payload asserted equal to `BuildConfig.APPLICATION_ID` by a **new** test (target §4.3 invariant 6) |
| **5** | **Drop legacy `md5_short` entirely** (R3): the `LEGACY_TYPE`/`LEGACY_TYPE_NAME` constants, `legacyKeyPattern`, the `decodeLegacy` branch, `TagPayload.LegacyMd5`, `PayloadFormat.LEGACY_MD5`, `Resolution.UnknownLegacy`, the `Legacy` sheet in `TagResultSheet.kt`, the `"LEGACY_MD5"` trampoline wire value, and the matching tests | `core/…/core/nfc/NdefCodec.kt:50-53,82-88`, `core/…/core/model/TagBinding.kt`, `core/…/core/usecase/ResolveTag.kt`, `app/…/ui/scan/{ScanViewModels,TagResultSheet}.kt`, `core/src/test/…/NdefCodecTest.kt` | green suites; `git grep -i md5` over `app core` is empty (it already is for `MessageDigest`/MD5 in the modern tree — arch §6.4) |
| **6** | Manifest: **delete** the `md5_short` `NDEF_DISCOVERED` filter; change the surviving filter's `android:path` to `/com.loosecannon.servicetag:tag` (still an **exact** path, never `pathPrefix`); change the three `<data android:scheme="notenfc" …>` entries to `servicetag`; keep the manifest comment that says this is the only NFC-exported component and that there is no tech-discovered catch-all | `app/src/main/AndroidManifest.xml:56-58,84-93` | a **new** test asserts the manifest path string equals `NdefEnvelope`'s external type for the app's `TagIdentity`, closing the two-unlinked-literals gap (arch §6.3, §8.2 Q14; target §3) |
| **7** | Deep-link scheme literals → `servicetag` | `core/…/core/nfc/TagRoute.kt:10`, `core/…/core/links/DeepLinkRoute.kt:17`, and the six `androidTest` files that hard-code `notenfc://` (arch §4.6) | `TagRouteTest`, `DeepLinkRouteTest` green; `git grep -c 'notenfc://'` = 0 |
| **8** | `app_name` → `ServiceTag`; theme → `Theme.ServiceTag`; the `Application` subclass → `ServiceTagApp`; the nav-root composable → `ServiceTagRoot`, resolving the two-classes-named-`NoteNfcApp` collision (arch §4.6; target §3 **[P3]**); the debug manifest label → `ServiceTag Backup (debug)` | `res/values/{strings,themes}.xml`, `app/…/NoteNfcApp.kt`, `app/…/ui/nav/NoteNfcApp.kt`, both manifests | `./gradlew :app:assembleDebug :app:assembleRelease`; the launcher label read back from the built APK |
| **9** | `DB_NAME` → `servicetag.db`; `AppPrefs` file name → `servicetag` **[P5]**; export prefixes → `ServiceTag-data-<stamp>.zip` / `ServiceTag-artifacts-<stamp>.zip`, and the three test fixtures that hard-code the old prefix | `app/…/di/AppGraph.kt:201`, `app/…/prefs/AppPrefs.kt:13`, `app/…/backup/SafBackupSetIO.kt:60-67`, `AppSmokeTest`, `AttachmentsDeviceProofTest`, `BackupViewModelTest` (arch §7.7 item 2) | JVM suites green. **A fresh install has no old database file to find, and the importer never reads a file name** (arch §7.3, §7.7 item 4) — so none of this can affect restore correctness |
| **10** | Re-export the Room schema under the new `AppDatabase` FQN: `app/schemas/com.loosecannon.servicetag.data.room.AppDatabase/1.json`…`5.json`. Delete the old directory. Fix the resource paths in the migration-ladder tests that load `N.json` by path | `app/schemas/…`, the migration chain tests (arch §7.7 item 5) | every migration hop test plus the full 1→5 run is green; `5.json`'s `identityHash` is **unchanged** — it is a structural hash of the schema and encodes no package name (arch §7.1). **If that hash changes, stop**: the schema drifted and the phone's database will not open |
| **11** | Add the sibling-isolation test in ServiceTag's direction: a record typed `com.loosecannon.notenfc:tag` decodes as `Foreign` and never as a recognised payload (target §4.3 invariant 1) | `core/src/test/…/NdefEnvelopeIsolationTest.kt` (new) | green. This is the test that stops ServiceTag adopting a noteNFC tag |
| **12** | Add the **Migrate tag** tool (R7): inside Settings → Read / inspect tag, a reader-mode flow that recognises the pre-split external type `com.loosecannon.notenfc:tag` **only here**, confirms, rewrites as the new identity, verifies the read-back, and preserves the logical binding. No manifest filter is added for that type | `app/…/ui/scan/*`, `app/…/ui/setup/SettingsScreen.kt` | JVM tests over the migrate decision path; device rows in §D |
| **13** | Repository hygiene: `.gitignore`, `proguard-rules.pro` package references, the `<queries>` block reviewed (unchanged — the schemes are link targets, not identity) | as found | `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug` |

**Verify (whole phase).**

```bash
cd ~/Documents/Projects/AndroidStudioProjects/noteNFC-split
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain
git grep -lE 'com\.loosecannon\.notenfc|notenfc://|md5_short|noteNFC' -- app core gradle settings.gradle.kts
# expected: no output. Hits under docs/ are HISTORY and are left alone (§H).
```

Then a clean-checkout proof: clone the worktree's branch into a directory that has never held the
project and run the same command (§19 of the brief; target §9).

**Rollback.** Every task is one commit on `product-split`; `git revert` the task, or
`git reset --hard` the branch to `ac523d7`. Nothing outside the worktree has changed, `master` is
untouched, and the recovery refs are intact.

### A.2 noteNFC reconstruction (a new local repository from `c84b881`)

**Do.**

```bash
cd ~/Documents/Projects/AndroidStudioProjects
git clone --no-local noteNFC noteNFC-narrow
cd noteNFC-narrow
git remote remove origin                     # it will get its own remote in §B
git checkout -B master c84b881               # master IS the narrow endpoint
git for-each-ref --format='%(refname)' refs/heads refs/tags \
  | grep -v '^refs/heads/master$' | xargs -r -n1 git update-ref -d
git reflog expire --expire=now --all && git gc --prune=now
```

Then, as the reconstruction's **first commit** (R6):

```bash
git rm -r --cached docs                      # the inherited ServiceTag design package: 70 files
git rm -r docs
git rm app/release/app-release.apk           # 4 740 933 B, tracked, owner-signed
git commit -m "start the narrow product from its own history"
```

That single removal discharges all four scrub obligations at once, because three of the four
personal-data files live under `docs/` (arch §2.8): `docs/design/issues/applied.md` (37 lines with
the owner's GitHub handle and personal issue URLs), `docs/superpowers/plans/2026-09-14-phase-0-foundation.md`
(5 more), and `docs/design/phase-0-evidence.md` (the four lines recording the shipped APK signer's
SHA-256/SHA-1/MD5 certificate digests plus the debug signer's). The fourth is the tracked APK. The
digests survive only as described text — in the ServiceTag repository, where they belong.

Then the remaining commits, each with its own verification:

| # | Commit | Verify |
|---|---|---|
| **1** | the removal above | `git grep -lE 'GonzRon\|github\.com/\|SHA-?256:' ` is empty; `git ls-files \| grep -c '\.apk$'` = 0; the tree is 46 files (116 minus the 70 under `docs/` — arch §2.8) |
| **2** | normalise the Kotlin package root from `com.looseCannon.noteNFC` to `com.loosecannon.notenfc` and set `namespace`/`applicationId` to the lowercase id (R1, **[P2]**). Note the asymmetry being removed: at `c84b881`, `:core` already used the lowercase directory while `:app` did not (arch §2.10) | `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug` — all three tasks and the CI workflow already exist at `c84b881` and were **verified green** there (arch §2.3) |
| **3** | point release signing at `~/.config/notenfc/keystore.properties` using the same `Properties`-from-`user.home` mechanism as the modern build, and bump `versionCode`/`versionName` past the historical 2 / `1.1` | `./gradlew :app:assembleRelease` produces a signed APK; the certificate SHA-256 matches the existing noteNFC key's, recorded but not reproduced |
| **4** | modernise the toolchain floor only if the `c84b881` toolchain no longer resolves — it should, being AGP 9.4.0 / Kotlin 2.4.20 / wrapper 9.7.1, the same as master (arch §2.3, §4.4) | CI-equivalent command green from a clean clone |
| **5** | adopt `nfc-tag-core` for intentional reader-mode writes, replacing the 2024 `enableForegroundDispatch` + `FLAG_MUTABLE` self-targeting `PendingIntent` with `NfcReaderModeSession`, and the blind `messages[0].records[0].payload` write with read-before-write, capacity check and verified read-back (R6). **Requires §A.3 and §A.4** — so this commit lands last | JVM tests for the write decisions; emulator adapter tests |
| **6** | copy (do not share) a safe `ACTION_VIEW` launch policy: scheme allowlist, whitespace/control rejection, `ActivityNotFoundException` **and** `SecurityException` caught. The 2024 app passed stored text straight to `startActivity` with no `try/catch` and crashed on a missing Joplin (arch §2.5, §5.13) | a test per rejected scheme; a test that a missing handler produces a message, not a crash |
| **7** | malformed/foreign wording: "This tag belongs to another app", including the case where noteNFC was launched *by a pre-split AAR* naming `com.loosecannon.notenfc` (R6) | JVM tests; device row in §E |
| **8** | keep the legacy `md5_short` decoder and its hand-computed vectors exactly as they are. **This is the only place they exist as working code** — `LegacyKey.compute` was deleted from master at `26ec9d0` and there is no MD5 anywhere at `ac523d7` (arch §6.4) | `LegacyKeyTest`, `NdefCodecTest`, `LegacyLinkPolicyTest` green, unmodified |
| **9** | remove the 2024 `TECH_DISCOVERED` catch-all and `res/xml/nfc_tech_filter.xml`, which the reconstruction inherits from 2024 and master deleted at `26ec9d0`. Without this, noteNFC is offered in the chooser for *every* `NfcA`/`Ndef` tag and then silently finishes (arch §5.13) | `git grep -c nfc_tech_filter` = 0; the manifest declares exactly two `NDEF_DISCOVERED` filters, both exact-path |

**Verify (whole phase).** `git rev-list --count HEAD` is 30 plus the number of new commits;
`git rev-list --merges --count c84b881` = 0 and `git log --oneline | tail -1` is `5fb6aed` — the
2023 root — proving the ancestry is real and ungrafted (arch §2.7). Clean-clone build green.

**Rollback.** Delete `~/Documents/Projects/AndroidStudioProjects/noteNFC-narrow` and re-clone.
Nothing has been pushed; `c84b881` is immutable history in the source repository.

### A.3 nfc-tag-core extraction

**Do.** Create `~/Documents/Projects/AndroidStudioProjects/nfc-tag-core`, `git init`, and build it
per the target document's §4.1 layout. Copy the Gradle wrapper (9.7.1) and the relevant slice of
`gradle/libs.versions.toml` from ServiceTag so a composite build never resolves two Kotlin
compilers. Then, file by file, follow the **provenance table** at target §4.6: it names, for every
library file, the source file, the line range and the `git log --follow` starting point, and the
change made on extraction. Write `README.md` around that table — for a repository with no inherited
history, the table *is* the provenance.

| # | Commit | Verify |
|---|---|---|
| **1** | skeleton: `settings.gradle.kts`, two module build files, version catalog, wrapper, `.gitignore` | `./gradlew projects` lists `:nfc-tag-core` and `:nfc-tag-core-android` |
| **2** | pure-JVM module: `NdefRecordData`, `TagIdentity`, `TagContent`, `NdefEnvelope`, `VersionedUuidBody`, `ExistingContent`, `OverwriteReason`, `OverwritePolicy` + the JVM test suite from target §4.5 | `./gradlew :nfc-tag-core:test`; the module declares **no** runtime dependency **[P7]** |
| **3** | Android module: `NdefBridge` (+ the new `serialisedSize`), `NfcReaderModeSession` (doc comment carried across verbatim), `TagWriter`/`TagInspection`/`WriteResult` with the formatted-size capacity rule **[P8]**, `TagHandle`/`TagIo`/`RealTagIo`, `TagWriteSession`/`WriteState`/`TagWriteCallbacks` | `./gradlew :nfc-tag-core-android:testDebugUnitTest :nfc-tag-core-android:assembleDebug` |
| **4** | `tools/forbidden-scan.sh` + `tools/forbidden-scan.allow`, wired as a `check` dependency | the scan passes; every allow entry carries a reason **[P9]** |
| **5** | `README.md` with the provenance table, the invariant list, and the two documented *patterns* the library does not own: the `LifecycleResumeEffect { start(); onPauseOrDispose { stop() } }` reader-mode idiom and the mint→write→verify→complete-else-abandon provisioning protocol (arch §6.1, §6.2) | a reader who has never seen either app can tell where each file came from |
| **6** | `.github/workflows/ci.yml` per target §9 | green once the remote exists (§B) |
| **7** | emulator `androidTest` suite for the adapter (target §4.5, tier 2) | green on the emulator, **locally**; never in CI |

**Verify.** `./gradlew build` green from a clean clone; `tools/forbidden-scan.sh` exits 0; the
provenance table's every `git log --follow` starting point resolves in the ServiceTag repository.

**Rollback.** Delete the directory. Nothing else references it yet.

### A.4 Both apps consume the library

**Do.** In each app repository, after `GonzRon/nfc-tag-core` exists on the remote (§B.1 — a
submodule needs a URL, **[P13]**):

```bash
git submodule add -b main https://github.com/GonzRon/nfc-tag-core.git third_party/nfc-tag-core
git -C third_party/nfc-tag-core checkout v0.1.0
git add .gitmodules third_party/nfc-tag-core
```

Add the three NEW lines to `settings.gradle.kts` (the `require` guard, `includeBuild`, and the
dependency line in `app/build.gradle.kts`) exactly as target §6.1 gives them; add
`tools/check-submodule-pin.sh` and wire it into `check` and into CI; add `submodules: true` to the
checkout step. Then delete from each app everything the library now owns, replacing it with the
library's types — in ServiceTag that is `NdefBridge`, `NfcReaderModeSession`, `TagWriter`,
`TagIo`/`TagHandle` and the protocol half of `TagWriteController`, leaving the row minting and all
thirteen message strings behind in the app.

**Verify.**

1. `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug` in both apps.
2. `tools/check-submodule-pin.sh` passes in both: `git describe --exact-match --tags HEAD` inside
   the submodule resolves to the same tag in both apps, and the submodule working tree is clean.
3. **The negative tests**: temporarily `rm -rf third_party/nfc-tag-core/*` and confirm the
   configuration-time `require` fires with the fix command; `git -C third_party/nfc-tag-core checkout HEAD~1`
   and confirm the pin check fails. Restore both.
4. Clean-checkout proof per repository: `git clone --recurse-submodules <url> <tmp>` into a
   never-used directory and run that repo's CI task list; then once more **from a second
   workstation** (§19 of the brief).
5. Both apps are on the **same** library tag.

**Rollback.** `git submodule deinit -f third_party/nfc-tag-core && git rm -f third_party/nfc-tag-core`
and revert the `settings.gradle.kts`/`app/build.gradle.kts` commits. Each app returns to its own
copy of the NFC layer, which is still in its history.

---

## B. Remote transition

Order per the brief's §27, as relayed: rename, create, push, transfer, enable CI. **[P14]** — the
brief's own §27 text is not in this worktree, so the sub-ordering below is this design's reading and
needs ratification. The one place it departs from a literal "rename first" reading is B.1, and the
reason is mechanical: a submodule cannot be added without a URL, so the *new* library repository is
created before the rename. Creating a new repository mutates nothing that exists; the rename is
still the last irreversible act.

Before any step: confirm the active token can do it. The active `GonzRon` token holds
`gist, read:org, repo, workflow` — enough to rename, create and transfer — and **cannot delete a
repository**; `delete_repo` lives only on the second, inactive account (arch §4.3). **No step below
requires a delete, and none may be designed to.**

### B.1 Create `nfc-tag-core` and push

**Do.**

```bash
gh repo create GonzRon/nfc-tag-core --public \
  --description "Product-neutral NDEF tag mechanism: envelope codec, reader mode, safe writer" \
  --disable-wiki
cd ~/Documents/Projects/AndroidStudioProjects/nfc-tag-core
git remote add origin https://github.com/GonzRon/nfc-tag-core.git
git push -u origin main
git tag -a v0.1.0 -m "extracted from noteNFC/ServiceTag at ac523d7; provenance in README"
git push origin v0.1.0
```

**Verify.** `gh repo view GonzRon/nfc-tag-core --json name,visibility,defaultBranchRef`;
`gh api repos/GonzRon/nfc-tag-core/tags` lists `v0.1.0`; the Actions run for the push is green
(§B.5 enables it if it is not on by default).

**Rollback.** The token cannot delete. `gh repo edit GonzRon/nfc-tag-core --visibility private` and
rename it aside (`gh repo rename nfc-tag-core-abandoned`); the name is then free again. Record the
abandoned name in the ledger so nobody wonders later.

### B.2 Rename the existing repository to ServiceTag

**Do.**

```bash
# Pre-flight: the recovery refs must already be on the remote.
gh api repos/GonzRon/noteNFC/git/refs/tags/pre-split-checkpoint --jq .object.sha   # ac523d7
gh api repos/GonzRon/noteNFC/git/refs/heads/pre-split-master     --jq .object.sha   # ac523d7

gh repo rename ServiceTag --repo GonzRon/noteNFC
```

**Verify.**

```bash
gh repo view GonzRon/ServiceTag --json name,visibility,isFork,hasIssuesEnabled,defaultBranchRef
curl -sI https://github.com/GonzRon/noteNFC | grep -i '^location'   # GitHub redirects the old path
cd ~/Documents/Projects/AndroidStudioProjects/noteNFC
git remote set-url origin https://github.com/GonzRon/ServiceTag.git
git fetch --all --tags && git rev-parse origin/master pre-split-checkpoint
git -C ../noteNFC-split remote -v      # the worktree shares the repository's remote
```

The rename must preserve: visibility PUBLIC, not-a-fork, issues enabled, default branch `master`,
all 36 issues, the one git tag, the 2023 draft release, and the CI history (arch §4.1, §4.8). Check
each.

**Rollback.** `gh repo rename noteNFC --repo GonzRon/ServiceTag` — a rename is reversible in both
directions and GitHub redirects the old path either way. Reset the local remote URL. **The recovery
refs `pre-split-checkpoint` and `pre-split-master` stay in place through every step of §B and are
never deleted**; they are the anchor a full rollback (§I) uses.

**Note on the redirect.** GitHub redirects the old `noteNFC` path *until a new repository claims it*.
B.3 claims it deliberately, which is why B.3 comes after B.2 and not before: creating `noteNFC`
first would collide with the existing name.

### B.3 Create the new `noteNFC` and push the reconstruction

**Do.**

```bash
gh repo create GonzRon/noteNFC --public \
  --description "Share a note link, write it to an NFC tag, tap to open it. Nothing else." \
  --disable-wiki
cd ~/Documents/Projects/AndroidStudioProjects/noteNFC-narrow
git remote add origin https://github.com/GonzRon/noteNFC.git
git push -u origin master
```

**Verify.** `gh api repos/GonzRon/noteNFC/commits --jq 'length'` and
`git log --oneline origin/master | tail -1` → `5fb6aed`, proving the 2023 root travelled; the
Actions run is green; the repository carries **no** `docs/` directory, **no** `.apk`, and no owner
handle in the tree (`gh search code --repo GonzRon/noteNFC GonzRon` finds nothing outside history).

**Rollback.** Cannot delete; rename aside (`gh repo rename noteNFC-wip`), which also restores the
`noteNFC` → `ServiceTag` redirect. Re-push from the local reconstruction once fixed.

### B.4 Transfer issues #6 and #36, with backlinks

**Do.** Per R9, and in this order so no issue is ever unreachable:

```bash
# 1. Leave the backlink FIRST, while the issue still has its original URL.
gh issue comment 6  --repo GonzRon/ServiceTag \
  --body "Moving to the standalone noteNFC product: https://github.com/GonzRon/noteNFC — split recorded in docs/architecture/product-split-migration.md."
gh issue comment 36 --repo GonzRon/ServiceTag --body "<same>"

# 2. Transfer.
gh issue transfer 6  GonzRon/noteNFC
gh issue transfer 36 GonzRon/noteNFC

# 3. Backlink the other way, from the new home to the origin repository.
gh issue comment <NEW#> --repo GonzRon/noteNFC \
  --body "Transferred from GonzRon/ServiceTag (formerly GonzRon/noteNFC), where this product's history lives."
```

**Verify.** Both issues resolve in `GonzRon/noteNFC` with their bodies intact — in particular #36's
ownership statement, which is the primary source for the maintenance product being called ServiceTag
(arch §4.7) — and the old URLs redirect. `gh issue list --repo GonzRon/ServiceTag --state all | wc -l`
= 34 (36 minus 2). Issue transfer needs push/admin on both repositories, which `repo` provides; the
capability was assessed but never exercised (arch §4.3), so treat a failure here as expected-possible
and fall back to close-with-pointer plus a fresh issue in the destination carrying the verbatim body.

**Rollback.** `gh issue transfer <NEW#> GonzRon/ServiceTag` moves it back; the backlink comments stay
and become the audit trail. Transfer is not destructive: comment history and the redirect survive.

### B.5 Enable CI on the new repositories

**Do.** For `GonzRon/noteNFC` and `GonzRon/nfc-tag-core`: confirm Actions is enabled
(`gh api repos/<owner>/<repo>/actions/permissions`), then push a trivial commit or
`gh workflow run ci` and read the result. Add no secrets — none of the three workflows interpolates
`secrets.*`, and release signing never touches CI (arch §4.2).

**Verify.** Three repositories, three green runs, on `push` and on `pull_request`. Each run's
uploaded `test-results` artifact is present (`if: always()`).

**Rollback.** Disable Actions on the affected repository; no code change needed.

---

## C. Android package transition on the phone

Per R8 and the brief's §15, in exactly this order: **keep the modern app → export → install
ServiceTag alongside → restore → prove → migrate tags → only then uninstall the old package →
install narrow noteNFC → coexistence.** The order is not stylistic. Two properties depend on it:

- The old package is the **only** holder of the data until the restore is proved, and the only
  holder of the SAF grant (arch §7.4 — a persisted grant is scoped to the calling application, and
  "there is no way to carry the old grant across a rename").
- Every physical tag written before the split carries the type `com.loosecannon.notenfc:tag`, which
  post-split is **noteNFC's own type**. Migrating every tag *before* narrow noteNFC is installed is
  the structural mitigation for the one wire-level collision in this whole design (target §7,
  **[P12]**).

### C.1 Snapshot before

**Do.**

```bash
export ANDROID_SERIAL=<the phone>        # set once, never printed into a document
STAMP=$(date +%Y%m%d-%H%M%S)
OUT=~/Documents/Projects/AndroidStudioProjects/noteNFC-backups/transition-$STAMP
mkdir -p "$OUT"
```

1. In the modern app (still `com.loosecannon.notenfc` 2.4): **Settings → Backup → Export set**.
   It writes both archives or neither — a set whose halves disagree cannot exist (arch §7.2).
2. Pull both archives off the phone to `$OUT`.
3. Capture the app-data snapshot the same way Phase A did (database + prefs), since the installed
   build is debuggable.
4. Record the modern app's `versionName`/`versionCode` and the certificate SHA-256 of the installed
   APK, so the rollback in §I knows what it is restoring to.

**Verify.**

```bash
cd "$OUT"
unzip -p <data>.zip manifest.json | jq '{formatVersion, schemaVersion, appVersion, backupSetId, counts, artifactCount, artifactBytes, dataSha256}'
# formatVersion 5, schemaVersion 5, backupSetId non-empty (a blank one at format 5 is refused as
# corrupt, because it could never pair with an artifacts file -- arch §7.2)
unzip -p <data>.zip data.json | sha256sum          # must equal manifest.dataSha256
unzip -p <artifacts>.zip manifest.json | jq '{artifactFormatVersion, dataFormatVersion, backupSetId, entries: (.entries|length)}'
# same backupSetId as the data manifest; entries == artifactCount
```

Then verify every artifact entry's SHA-256 against its manifest row, and confirm the counts against
the eleven tables. The pre-split baseline (arch §7.5) is assets 5, nfcTags **0**, externalLinks 1,
measurementDefinitions 13, eventProfiles 15, profileFields 22, profileConsumables 14, assetEvents 26,
measurements 68, consumableUsages 4, attachments 8. A count that has moved since 2026-09-16 is fine —
the *new* export is the baseline from here on — but a count that has moved *down* is a stop.

**Rollback.** Nothing was changed. Re-export.

### C.2 Install ServiceTag alongside

**Do.** Build the ServiceTag debug APK from the converted tree and install it. **Do not uninstall
anything.** The two applicationIds are different, so the OS treats them as unrelated apps and both
sit on the launcher (arch §7.4).

**Verify.** Both packages are present; both launch; the modern app's data is untouched (open it and
see the five assets). The new install's database is empty — a fresh package gets a fresh database,
not a copied file (arch §7.6 last row).

**Rollback.** Uninstall the ServiceTag package. The old package and its data are untouched.

### C.3 Re-acquire the SAF grant — the same tree first

A grant cannot be inherited. ServiceTag must drive its own `OpenDocumentTree` →
`takePersistableUriPermission` (arch §7.4, §7.7 item 8).

**Do.** ServiceTag → **Settings → Attachment storage** → pick **the same folder the modern app
already uses**. The take is wrapped in `runCatching`; only a successful take updates the pref, and
the screen re-reads the store state so it shows what is actually true — some cloud-backed providers
refuse a lasting grant (arch §7.4).

**Discovery via stable locators — how "the same tree" is confirmed without guessing.** Every
attachment locator is **store-relative**: `assets/<asset-id>/<attachment-id>.<ext>` or
`events/<event-id>/<attachment-id>.<ext>`, with no drive letter, authority, tree-URI fragment or
package name anywhere in it, confirmed against the real set's 8 rows (arch §7.4). So the right tree
is simply the one that already contains those relative paths. Read the 8 expected locators out of
the exported `data.json`:

```bash
unzip -p "$OUT"/<data>.zip data.json | jq -r '.attachments[] | .storageLocator'
```

and confirm, in ServiceTag's store-health readout, that all 8 resolve **before any restore runs**.
8 of 8 means the same tree. Fewer means a different folder, and the restore becomes the empty-tree
case in C.7.

**Verify.** `StoreState` is `Ready(displayName, authority)`, not `NotConfigured` and not
`AccessLost`; the persisted-grant list for the ServiceTag package contains exactly one entry — the
chosen tree — because a successful take of a *different* folder releases the previous grant, and
grants otherwise accumulate (arch §7.4, spike S5).

**Rollback.** Revoke the grant in system settings, or pick again — picking is idempotent, and the
pref is only written on a successful take. Nothing in the tree has been written yet.

### C.4 Restore the data

**Do.** ServiceTag → **Settings → Backup → Import (replace)** → the pulled `<data>.zip`.

The importer decodes **outside** the transaction — "refuse before touching data" — then, inside one
write transaction, deletes in reference-clearing order and inserts in reference order: assets
parents-first, definitions ENTERED-then-DERIVED, profiles, links, tags, events, attachments last
(arch §7.2). `validateGraph()` re-checks every FK-shaped reference and every locator shape
*before* the transaction opens. A format-5 file with a blank `backupSetId` is refused as corrupt.

**Why this needs no code change.** The backup content carries **zero package or product identity**:
grepping the real pulled `manifest.json`, `data.json` and the artifacts `manifest.json` for
`notenfc`, `loosecannon`, `com.loosecannon`, `NoteNFC`, `noteNFC` returns **zero hits in any of the
three files**, and `appVersion` is a bare semver string. The import path never inspects a file name
or extension for meaning. Consequence, stated in the archaeology: *a renamed build with an
unmodified codec layer can import the two preserved real files with zero code changes*
(arch §7.3).

**Verify.** The import reports the same eleven counts as the manifest. `AppPrefs`'
`last_restored_backup_set_id` now holds the set id (it was **absent** before, because no import had
ever run against this data — arch §7.5).

**Rollback.** The import is all-or-nothing inside one transaction; a failure leaves the (empty)
ServiceTag database as it was. If it succeeds but proves wrong, uninstall the ServiceTag package —
the old app still holds everything.

### C.5 Restore the artifacts

**Do.** ServiceTag → **Settings → Backup → Restore files** → the pulled `<artifacts>.zip`.

`RestoreArtifacts.run(archive, expectedSetId)` throws `ArtifactsSetMismatch` **before touching any
entry** if the archive's `backupSetId` disagrees with the data restore's
`lastRestoredBackupSetId`, and is idempotent: bytes that already hash correctly are
`alreadyPresent`, never rewritten. Each entry is verified **twice** — the manifest's `sha256`
against the local row before anything is opened, and the store's own streaming digest after the
write (arch §7.2).

**Verify (same-tree case).** The expected result is **8 `alreadyPresent`, 0 written, 0 skipped** —
because the files are already in that folder and already hash correctly. That is the same-tree proof:
idempotence, not a copy. A single `written` means the tree was not the one in use; a `skipped` means
a hash mismatch and is a stop.

**Rollback.** Nothing was written in the same-tree case. In the empty-tree case (C.7) the restore
deletes any stale document at a locator first and deletes the partial document on failure, so a
failed entry leaves no debris (arch §7.4).

### C.6 The data proof — stable-id comparison

**The principle** (arch §7.4): every id in the backup format is a verbatim UUID string, every
relationship is by id, **no id is package-, device- or install-qualified**, so *an id that exists
pre-split must exist, unchanged, post-split*. That sentence is the whole proof.

**Do.** Export a fresh set from the **restored ServiceTag**, pull it, and compare its `data.json`
against the preserved one. The comparison is cheap because `BackupCodec.encode()` sorts every list
by `id`, and children by `sortOrder` within parent, so the same input always produces the same bytes
(arch §7.2) — a `data.json` that differs only in the fields listed in C.8 is a pass.

```bash
unzip -p "$OUT"/<preserved-data>.zip data.json > /tmp/before.json
unzip -p "$OUT"/<servicetag-data>.zip data.json > /tmp/after.json
diff <(jq -S . /tmp/before.json) <(jq -S . /tmp/after.json)   # expect: empty, or only C.8 fields
```

Then the per-table stable-id assertions. **Compare, for every one of the eleven tables, the id set
and every relationship field** (fields from arch §7.1, §7.4, §7.6):

| Table / DTO list | Ids to compare | Relationship fields to compare |
|---|---|---|
| `asset` / `assets[]` | `id` set, exactly | `parentAssetId` (nullable, self-referencing) — and rebuild the tree from ids and diff it against the pre-restore tree; the importer's `AssetTree.parentsFirst` re-validates no-cycle on the way in |
| `nfc_tag` / `nfcTags[]` | `id` set | `assetId` **xor** `linkId`, plus `payloadFormat`, `payloadKey`, `status`, `physicalUid`. **Empty in this dataset** (`nfcTags = 0`, arch §7.5) — the list is asserted empty, and the assertion stays for whenever tags exist |
| `external_link` / `externalLinks[]` | `id` set | `assetId` (nullable), `kind`, `uri`. **The one row that matters most here**: a single `JOPLIN` link with `assetId = NULL` — an unowned link that nothing else references, and therefore the row most likely to be quietly lost (arch §7.5) |
| `measurement_definition` / `measurementDefinitions[]` | `id` set | `assetId`, `kind`, and for `DERIVED` rows `formula`, `sourceAId`, `sourceBId` (self-referencing, RESTRICT) |
| `event_profile` / `eventProfiles[]` | `id` set | `assetId` |
| `profile_field` / `eventProfiles[].fields[]` | nested `id` set | `definitionId`, and the `(profileId, definitionId)` uniqueness |
| `profile_consumable` / `eventProfiles[].consumables[]` | nested `id` set | `profileId` (implicit in nesting), values |
| `asset_event` / `assetEvents[]` | `id` set | `assetId`, `profileId` (nullable), `(source, source_ref)` uniqueness, `occurredOn` |
| `measurement` / `assetEvents[].measurements[]` | nested `id` set | `definitionId` (RESTRICT), `eventId` (implicit in nesting), value |
| `consumable_usage` / `assetEvents[].consumables[]` | nested `id` set | `eventId` (implicit), amount |
| `attachment` / `attachments[]` | `id` set | owner = **exactly one of** `assetId` / `eventId` (XOR, enforced both in `toDomain()` and by `validateGraph()`), plus `sha256`, `sizeBytes`, `storageLocator`, `mode`, and the `(storageProvider, storageLocator)` uniqueness |

**The attachment byte proof — three independent hashes per row, eight rows.** For each of the 8
attachments, assert:

1. `AttachmentDto.sha256` in the restored export **==** `AttachmentDto.sha256` in the preserved
   export (the row survived).
2. **==** the artifacts manifest's per-entry `sha256` for that `attachmentId` (the archive agrees
   with the row).
3. **==** `sha256sum` of the **original file** at its locator — read back out of the tree after the
   restore, and independently out of the preserved artifacts archive entry
   (`artifacts/<attachment-id>.<ext>`, whose extension is taken from the *locator* so entry name and
   locator can never disagree — arch §7.2).

Also assert `sizeBytes` on all three sides, and that `artifactCount` equals the attachment-row count
(8 = 8). This is the same check `RestoreArtifacts` already performs twice at runtime; doing it a
third time from the workstation is what makes it *evidence* rather than a claim.

**Verify (the gate).** Eleven tables with identical id sets; every relationship field equal; 8×3
hashes equal; `5.json`'s `identityHash` unchanged and matching the new package's live
`room_master_table.identity_hash`, and `PRAGMA user_version` = 5 (arch §7.1). Record the whole
result as a table in the ServiceTag evidence file.

**Rollback.** If any assertion fails: do **not** uninstall anything. The old package still holds the
live data and the modern app still runs. Uninstall ServiceTag, fix, reinstall, restore again — the
restore is replace-mode and idempotent from the same archive.

### C.7 The empty-tree restore, proved on the emulator

The same-tree case proves idempotence; it does not prove the restore can actually *write* bytes. So
the writing path is proved separately, on the emulator, where a failure costs nothing.

**Do.** Install ServiceTag on the emulator; point its store at an empty folder; restore data, then
artifacts, from the same two archives.

**Verify.** **8 written, 0 alreadyPresent, 0 skipped**; every file lands at its store-relative
locator under the new root — proving locators are store-agnostic and package-agnostic by
construction (arch §7.4); all 8×3 hashes equal; the same eleven-table id comparison passes. Note
that `documentIn()` falls back from an exact name match to "the first child whose name starts with
`<id>.`", because some SAF providers append or normalise extensions — so a restored file whose
extension differs is a pass, not a failure, as long as its hash matches.

**Rollback.** Wipe the emulator. Nothing on the phone was touched.

### C.8 Fields that legitimately differ

A difference in any of these is **not** a migration failure. Anything *not* on this list must be
identical, because every remaining field is domain data (arch §7.6).

| Field | Why it differs |
|---|---|
| `manifest.backupSetId` | minted fresh on every export — a pairing token between one data archive and one artifacts archive, not a stable identity to preserve across exports |
| `manifest.createdAt` | the new export's timestamp |
| `manifest.appVersion` | `BuildConfig.VERSION_NAME`; it carries no package identity and ServiceTag's version was bumped |
| Export file names (`noteNFC-data-*` → `ServiceTag-data-*`) | cosmetic; the importer never reads a file name |
| The Room database **file** and its name (`notenfc.db` → `servicetag.db`) | the new package gets a fresh, empty database populated by the *data* restore, never a copied file |
| Schema export directory (`app/schemas/<old FQN>/` → `<new FQN>/`) | a build artefact derived from the `AppDatabase` FQN, not runtime state |
| `attachment_tree_uri` pref | **must** be re-chosen: a persisted SAF grant is scoped to the calling application and cannot cross a package boundary, even for the same signing key |
| `last_backup_at` pref | device-local, never in a backup |
| `last_restored_backup_set_id` pref | **absent** before (no import had ever run against this data), set after |
| `appearance_mode` pref | never explicitly set, so it falls back to its `SYSTEM` default and is never written |
| Thumbnail cache (`<cacheDir>/thumbs/…`) | purely derived and regenerable, in a directory that "may vanish at any time" |
| `shared_prefs/storage_spike.xml` | one `tree_uri` key outside the `AppPrefs` contract — residue from spike S5. **Device-local; the migration need not preserve it** (arch §7.5). Clearing it on the phone during the transition is optional (arch §8.2 Q10) |
| SAF `displayName` / `authority` in `StoreState` | derived from the newly granted tree |
| `external_link.lastOpenedAt` | changes if the link is launched during the proof; compare it before launching anything |
| `nfc_tag.lastScannedAt` / `writtenAt` / `physicalUid` | a read scan performs a write (arch §5.9), so any tag row touched during §D or §E moves. Not applicable to this dataset before §D, because `nfcTags = 0` |

### C.9 Uninstall the old package — only now

**Do.** Only after C.6 passes and its evidence table is written: uninstall `com.loosecannon.notenfc`
(the modern 2.4 build).

**Verify.** ServiceTag still shows all five assets, 26 journal events and 8 attachments with
thumbnails, and the attachment files still open in the system viewer — the SAF tree is owned by the
folder, not by the uninstalled app, and ServiceTag holds its own grant. The `noteNFCURLs` prefs map
goes with the uninstall; it is not part of ServiceTag's data and its fate is arch §8.2 Q7's
question, deferred to noteNFC's backlog.

**Rollback.** Reinstall the preserved signed 2.4 / vc6 APK from
`~/Documents/Projects/AndroidStudioProjects/noteNFC-releases/` and import the preserved set into it.
The old app's data is gone with the uninstall, which is why this step is last and why C.1's export
plus C.6's proof are its preconditions.

### C.10 Install narrow noteNFC

**Do.** Build and install the reconstruction's debug APK. **Precondition: §D is complete** — every
physical tag carrying `com.loosecannon.notenfc:tag` has been migrated, because noteNFC now claims
that type ambiently.

**Verify.** Both `com.loosecannon.servicetag` and `com.loosecannon.notenfc` are installed; each
launches; neither sees the other's data. Then §E.

**Rollback.** Uninstall noteNFC. ServiceTag is unaffected.

---

## D. Physical tag migration (R7)

### D.1 The Migrate-tag tool

Entered deliberately, never ambiently: **ServiceTag → Settings → Read / inspect tag → Migrate tag**.
There is no manifest filter for the pre-split type, so the tool's reader mode is the only path to
it — which works because reader mode overrides AARs and the intent dispatch system
(**[platform-doc]**, arch §8.1 Q4).

1. The screen states what it will do and what it cannot undo, and shows the availability sentence
   from `NfcReaderModeSession.available`/`.enabled`.
2. Reader mode starts on RESUMED and stops on PAUSE or dispose — "reader mode belongs to the resumed
   screen and to nothing else: leaving this screen hands NFC back to the system, which is what lets
   the background trampoline keep working" (arch §5.5).
3. **Tap.** The callback arrives on a binder thread; the tap is single-flight.
4. **Inspect** (`TagWriter.inspect`, off the main thread, fresh `Ndef.getNdefMessage()`): uid,
   records, `maxSize`, `writable`, `needsFormat`, `canLock`.
5. **Classify.** Only here is `com.loosecannon.notenfc:tag` recognised, and only as *the pre-split
   type*: the body is read with the same `version | flags | UUID` parse, yielding the pre-split tag
   id. Any other type is `Foreign` and the tool says so and offers nothing. A legacy `md5_short`
   record is named as noteNFC's and offered nothing **[P11]**.
6. **Confirm.** The tool shows the pre-split tag id and the binding it will preserve, and asks once.
   Nothing is written without that confirmation — and a confirmation is remembered as consent for
   *that content*, so a stale `Tag` handle costs one more tap and not another question (arch §5.6).
7. **Write** the new identity: `[com.loosecannon.servicetag:tag record, AAR com.loosecannon.servicetag]`,
   the external record first (invariant 2), capacity checked against the serialised message size.
8. **Verify** by structural read-back equality — record count, order, TNF, full type, full payload
   (invariant 3). On the `NdefFormatable` path the tag is formatted unlocked and both verification
   and any lock wait for the second tap (invariant 9).
9. **Preserve the binding.** The `nfc_tag` row keeps its own `id`, its `target` and its `status`;
   `payloadKey` is rewritten to the new payload's id if the tool mints a new one, or kept if it
   reuses the pre-split id. **[P15]** — reuse the pre-split UUID as the new `payloadKey`: the body is
   the same 18 bytes, the uniqueness constraint is on `(payload_format, payload_key)` and nothing
   else references the key, so reuse makes the migration a pure envelope rewrite and keeps the row
   comparable to its pre-split export.
10. **Abandon** on close without a verified write: no phantom row is left behind
    (`abandonIfUnwritten`, on a scope that outlives the screen).

**Population today: zero real bindings** (`nfc_tag = 0`, arch §7.5). The tool is proved on test tags,
and it exists for the tags the owner will write between now and deployment.

### D.2 Physical test tags: how many and which kinds

The owner has **no tag bound to real data**, so every tag below is expendable — except that locking
is permanent, which is why there are spares.

| # | Tag | Prepared how | Used for |
|---|---|---|---|
| **T1** | blank, unformatted NTAG213 (`NdefFormatable`, not yet `Ndef`) | out of the packet | the format path: `maxSize = -1`, format-unlocked, second-tap verify, the formatted-size capacity rule **[P8]** |
| **T2** | NTAG213 carrying the **pre-split** `com.loosecannon.notenfc:tag` + AAR `com.loosecannon.notenfc` | written by the preserved 2.4 APK before C.9, or by the reconstruction after C.10 | the Migrate-tag subject; the wire-level collision in target §7 row 3 |
| **T3** | NTAG213 carrying **legacy** `com.loosecannon.notenfc:md5_short`, payload 8 lower-case hex | **no encoder exists in either codebase** — the type is decode-only (arch §5.2). **[P16]**: write it with a generic third-party NFC writer as an external-type record, or add a debug-only fixture writer to the reconstruction. The generic writer is cheaper and touches no product code | noteNFC's legacy path; the "Legacy tag" offer; ServiceTag naming it and refusing it |
| **T4** | NTAG213 carrying **new ServiceTag** `com.loosecannon.servicetag:tag` + its AAR | written by ServiceTag after C.2 | ServiceTag's own happy path; noteNFC seeing it as `Foreign` |
| **T5** | NTAG213 carrying **new noteNFC** `com.loosecannon.notenfc:tag` + its AAR, bound to a link | written by narrow noteNFC after C.10 | noteNFC's happy path; ServiceTag seeing it as `Foreign` |
| **T6** | a commercial sticker with a URI/web-link record | any retail NFC sticker | "foreign tag → nothing unsafe", and the Android 16+ `ACTION_VIEW` / Android 17 notification behaviour (arch §8.1 Q5) |
| **T7** | an unrelated **external-type** tag (a different domain entirely) | generic writer | the `Foreign` branch with a type string that is neither product's — the 1B row that was **[unobserved]** for want of a foreign tag (arch §5.12) |
| **T8** | spare blank NTAG213 | out of the packet | the destructive rows: the permanent lock, and any tag a failed write leaves in an unknown state |

**Eight tags**, all NTAG213 or similar (the 92-byte message fits 144 with ~52 bytes of headroom —
arch §5.7), of which five must be *written* by software and two by a generic writer. T4 doubles as
the "AAR names an uninstalled app" case for arch §8.1 Q1(c) after ServiceTag is uninstalled on a
throwaway device or the emulator-adjacent check; on the owner's phone that case is observed with
T2 before C.10, when no app claims `com.loosecannon.notenfc`.

### D.3 The on-device observation matrix

Every row below is **to observe on-device** and comes from arch §8.1. Each is recorded as one row in
a new evidence file, `docs/architecture/product-split-evidence.md`, in **ServiceTag**, in the same
shape as the existing `docs/design/phase-*-evidence.md` files — which is what makes it comparable to
the 1B/1C rows this design leans on.

Row schema: `id | question | precondition (which apps installed, which build, which tag) | action |
expected (with its claim tag) | observed | verdict | date | build`.

| Row | Question | Precondition | Action | Expected | Claim today |
|---|---|---|---|---|---|
| **M1** | Q1(a) chooser vs AAR, distinct domains | both apps installed, both on the same library tag | tap **T4** | ServiceTag opens the asset; **no chooser** — noteNFC's filter is an *exact* path and does not match, and noteNFC has no tech catch-all, so it is not offered at all | **[unobserved]**, "correct by construction, no new code" (arch §6.5) |
| **M2** | Q1(a), other direction | both installed | tap **T5** | noteNFC opens the note; no chooser | **[unobserved]** |
| **M3** | Q1(b) shared filter | both installed | tap **T3** | **noteNFC only** — ServiceTag declares no `md5_short` filter (R3), so the chooser D13 anticipated for the old/new window must **not** appear | **[unobserved]**; the removal of the overlap is the design's claim |
| **M4** | Q1(c) AAR names an uninstalled package | ServiceTag installed, `com.loosecannon.notenfc` **not** installed (between C.9 and C.10) | tap **T2** | **[platform-doc]** the platform goes to Google Play for the AAR's package; neither id is published, so a Play page for a non-existent listing | **[unobserved]** |
| **M5** | Q2 stopped-state dispatch, per app | both installed, one force-stopped (`am force-stop`), `dumpsys` confirming `stopped=true` | tap that app's tag | **contradictory**: **[platform-doc]** says a stopped app gets no dispatch; **[device-observed]** 1C row 16 says a force-stopped *and* data-cleared package **was** dispatched. Re-observe **per app** | the contradiction is the finding (arch §5.10, §8.1 Q2) |
| **M6** | Q2, never-launched install | an app freshly installed and **never opened** | tap its tag | 1B observed **silence** for the never-launched case. If that reproduces, a freshly installed second product looks broken until its first launch — which needs a sentence on each app's first screen | **[device-observed]** partially; generalisation unknown |
| **M7** | Q3 Android 16+ per-app NFC allowlist | first NFC intent to each app | tap once per app | a first-scan notification **per app**, and two entries under Settings → Apps → Special app access → Launch via NFC. Neither app calls `NfcAdapter.isTagIntentAllowed()`, so a denial is currently **invisible inside the app** | **[platform-doc]** (arch §8.1 Q3) |
| **M8** | Q3, cross-effect | one app's NFC access **disallowed** | tap the *other* app's tag | the other app still works — the apps share the NFC service but not the allowlist entry | **[unobserved]** |
| **M9** | Q4 reader-mode intercept, A→B | ServiceTag's Migrate-tag screen open | tap **T5** | reader mode overrides dispatch; the tool sees a noteNFC tag and names it, offering nothing | **[platform-doc]** C6 |
| **M10** | Q4, B→A | noteNFC's write screen open | tap **T4** | `Confirm(FOREIGN, "…com.loosecannon.servicetag:tag")` — nothing written without one explicit confirmation, and the reason string already names the other product | `Foreign` is **[JVM-proven]**; the device path is **[unobserved]** |
| **M11** | Q5 foreign, blank | both installed | tap a blank **T1** | nothing unsafe; no app screen | **[unobserved]** — 1B row 11 was skipped for want of a foreign tag |
| **M12** | Q5, commercial sticker | both installed | tap **T6** | **not silence**: from Android 16 a web-link tag triggers `ACTION_VIEW`, and from Android 17 an "open link" notification | **[platform-doc]** C10 |
| **M13** | Q5, unrelated external type | both installed | tap **T7** | `Foreign` with that type string, no lookup, no row, no transaction in either app | **[JVM-proven]** only |
| **M14** | R7 migration, happy path | ServiceTag, Migrate-tag open | tap **T2**, confirm | rewritten as `com.loosecannon.servicetag:tag` + its AAR, structural read-back equal, binding preserved, row id unchanged | the mechanism is **[JVM-proven]**; the device path is new |
| **M15** | R7 migration, refused | ServiceTag, Migrate-tag open | tap **T3**, then **T6** | each is named and **no** action is offered **[P11]** | new |
| **M16** | capacity, format path | ServiceTag write screen | tap **T1** twice | first tap formats unlocked and says "lift it off and hold it again"; second tap verifies, and *then* locks if armed. A too-small unformatted tag reports `TooSmall`, not a generic failure **[P8]** | the sequence is **[device-observed]** in 1B/1C; the `TooSmall` case is new |

**How each observation is recorded.** The operator captures `adb logcat` and the relevant `dumpsys`
output from the workstation for the whole session, writes the observed column verbatim (including a
surprise), and marks the verdict PASS / FAIL / INCONCLUSIVE. **An INCONCLUSIVE row stays
INCONCLUSIVE** — 1B row 13 is the precedent (arch §5.10) — and the claim tag in the target document
is updated from **[unobserved]** to **[device-observed]** *only* for rows that passed. A row whose
observation contradicts a **[platform-doc]** expectation is recorded as a contradiction and cited in
both documents, exactly as arch §5.10 does for stopped-state dispatch.

**Rollback (this whole phase).** Physical tags: any tag can be rewritten, **except** a locked one —
which is why locking is exercised only on **T8**. No app data changes: the Migrate-tag tool rewrites
the envelope and preserves the row, and `abandonIfUnwritten` removes any row a cancelled write
minted. If the tool misbehaves, stop using it; the pre-split tags are still readable by the
reconstruction and, until C.9, by the preserved 2.4 build.

---

## E. Coexistence acceptance

Per the brief's §25 and D7's list: **note tag → noteNFC, asset tag → ServiceTag, link tag →
ServiceTag, legacy tag → noteNFC, foreign tag → nothing unsafe, no ambient scan enters write mode**.

Preconditions: both apps installed (C.2 and C.10 done), both on the same library tag, §D's matrix
complete, `adb logcat` capturing from the workstation throughout.

**The owner's manual actions are the taps and nothing else.** Every install, force-stop, state
inspection and log capture is driven from the workstation. The count is **11**: nine taps plus two
one-time system NFC-allowlist confirmations. The two dialogs may appear on the first tap of each app
and fold into checks 1 and 3, which gives a floor of **9**.

| # | Check | Owner action | Pass condition |
|---|---|---|---|
| **1** | asset tag → ServiceTag | tap **T4** (bound to an asset) | ServiceTag opens that asset. No chooser. No noteNFC window (M1) |
| **2** | link tag → ServiceTag | tap **T4** re-bound to a link, or a second ServiceTag tag | the link launches with **no app screen at all**, and `last_opened_at` is stamped; Done returns to the launched app (arch §5.11) |
| **3** | note tag → noteNFC | tap **T5** | noteNFC opens the note. No chooser (M2) |
| **4** | legacy tag → noteNFC | tap **T3** | noteNFC only; the note opens on a prefs hit, or the "Legacy tag" offer appears. **ServiceTag does not appear** (M3) |
| **5** | foreign tag → nothing unsafe | tap **T7** | neither app draws a screen; no database write in either (`Foreign` → `NotOurs` → no lookup, no transaction — arch §5.12). A notification from the platform for a *web-link* tag is a pass, not a failure (M12/M13) |
| **6** | blank tag → nothing unsafe | tap **T1** | nothing happens in either app (M11) |
| **7** | no ambient scan enters write mode | tap **T5** with both apps closed | noteNFC resolves and opens; **no write screen, no reader mode**. Structurally guaranteed: the ambient trampoline never calls `NdefBridge.nfcTag()`, so it cannot obtain a writable handle (arch §5.10), and reader mode is entered only from an intentional destination (arch §5.13) |
| **8** | sibling interception, A→B | with ServiceTag's Migrate-tag screen open, tap **T5** | the tool names it as noteNFC's and offers nothing (M9, **[P11]**) |
| **9** | sibling interception, B→A | with noteNFC's write screen open, tap **T4** | one explicit confirmation is required, the reason names ServiceTag, and **Keep it** leaves the tag exactly as it was (M10) |
| **10** | NFC allowlist, ServiceTag | confirm the first-scan system dialog | two allowlist entries exist, one per app (M7) |
| **11** | NFC allowlist, noteNFC | confirm the first-scan system dialog | as above; and a denial for one does not break the other (M8, driven from the workstation) |

**Verify (the gate).** Eleven checks with a recorded pass, plus — from the logs, no owner action —
zero unexpected activity starts, zero chooser dialogs on checks 1–4, and no database write in either
app on checks 5–7.

**Rollback.** Uninstall either app; neither holds the other's data or grant. A failure on checks 1–4
means a filter is wrong and is fixed in source, not on the phone. A failure on 5–7 is the serious
one: it means the type gate or the ambient/write separation is broken, which stops the split and
sends the library back to §A.3.

---

## F. Issue and backlog partition

From the classification in arch §4.7: **36 issues, 35 open / 1 closed, no labels on any of them.**
The classification was done by reading each title and, for the eleven ambiguous ones, the full body —
because most ServiceTag issues still say "noteNFC", which is the current app's name.

| Disposition | Issues | Action |
|---|---|---|
| **Moves to noteNFC** (2) | **#6** "[MVP] Generalize external note/deep-link support beyond Joplin"; **#36** "[FUTURE noteNFC] First-class deep-link support for Joplin, Obsidian, Logseq, Evernote, Notion, OneNote and Todoist" | `gh issue transfer` per §B.4, with a backlink comment in each direction. #36's body is the ownership statement that asked for exactly this |
| **Stays in ServiceTag — ServiceTag proper** (26) | #2–#5, #7–#18, #19, #21, #22, #24–#28, #33, #34 | nothing. Two of these look like noteNFC and are not: **#19** ("`notenfc://` deep-link contract") is entirely about `notenfc://asset|link|tag`, i.e. ServiceTag domain objects despite the legacy scheme name — and its scheme literal is retitled to `servicetag://` as part of the issue, not of the transfer; **#34** ("Todoist deep-link actions into noteNFC") says noteNFC in the title but its body is about landing on a maintenance operation |
| **Stays in ServiceTag — shared NFC, genuinely dual-purpose** (4) | **#1** "[EPIC] Evolve noteNFC into an NFC-first maintenance tracker"; **#30** "Bind, rebind, revoke, and unknown-tag flows"; **#31** "noteNFC tag payload format v1 and legacy md5_short resolver"; **#35** "Untrusted input policy: tag payloads, deep links, stored URIs" | stay, each with **a note** recording that the mechanism half now lives in `nfc-tag-core` and linking the library repository (R9). #31 additionally records that the `md5_short` half moved to noteNFC (R3) |
| **Stays in ServiceTag — generic build/test/design infra** (3) | **#20** "Phase 1C: Compose design system foundation (Apollo Service Binder)"; **#23** "Phase 0: clone-buildable repo, AGP 9 toolchain, `:core` module, CI"; **#32** "Testing pyramid and CI gates" | stay. #23 gets a note that noteNFC inherited its outcome by branching `c84b881`, and #32 a note that the gates now exist in three repositories |
| **Closed, with a pointer** (1) | **#29** "[MVP] Investigate the installed APK's signing certificate and upgrade path" — already closed; it is the historical record of the unavailable legacy key | stays closed, in ServiceTag, with a comment pointing at target §8 (why the key matters, and where each product's key now lives). It is not reopened and not transferred: it is why noteNFC has a *new* lowercase identity at all |
| **New, in noteNFC** | — | one issue per unresolved noteNFC question the archaeology surfaced but could not answer: Q7 (a one-time importer for the `noteNFCURLs` prefs map), Q8 (keep or replace the `contains("joplin")` gate), Q10 (whether a debug source set comes along). Each quotes the archaeology's framing verbatim so the trade-off is not re-derived |
| **New, in nfc-tag-core** | — | one issue for Q13's residue if **[P8]** does not fully close the `NdefFormatable` capacity gap, and one for Q14's manifest↔constant link if the assertion test in §A.1 task 6 turns out to be the wrong mechanism |

**Verify.** `gh issue list --repo GonzRon/ServiceTag --state all` = 34;
`gh issue list --repo GonzRon/noteNFC --state all` = 2 transferred + 3 new; every "stays with a
note" issue has the note. **Rollback.** Transfers reverse (§B.4); notes and new issues are additive
and can be closed.

---

## G. Releases and tags

| | Disposition |
|---|---|
| **The 2023 draft release** | **Stays in ServiceTag, as history. Not published, not deleted, not re-anchored.** It is: name `initial working`, `draft: true`, `prerelease: true`, `tag_name: ""` (**empty — never tagged**; GitHub minted only a synthetic placeholder for its `html_url`), `target_commitish: "master"`, `published_at: null`, no assets. The commit the repository stood at two minutes before it was created is `707ca3f` "adding a debug apk" — an **Evernote-era** commit, package `com.looseCannon.evernotenfc`, key `UUID.randomUUID().toString().substring(0, 8)`, record type `com.loosecannon.evernotenfc:uuid8_link`. It predates MD5 keying, the `md5_short` type, the project rename and the Joplin conversion by over a year (arch §2.10). **Because it has no tag and targets the branch ref rather than a SHA, it is unanchored — publishing it today would tag whatever master then is.** So it is left exactly as it is, and this paragraph is the record of why |
| **`pre-split-checkpoint`** | stays in ServiceTag, at `ac523d7`. A ServiceTag-state marker, not a narrow-product marker (arch §4.8) |
| **`pre-split-master`** (branch) | stays in ServiceTag, at `ac523d7`. Recorded here because the name reads like a tag and is not — `git tag` lists only `pre-split-checkpoint` (arch §4.1) |
| **ServiceTag's first tag** | `servicetag-v<versionName>-vc<versionCode>` on the commit that passes §E, annotated with: the split's completion date, the library tag both apps consume, and the certificate SHA-256 of the ServiceTag signing key. **[P17]** — the prefix is explicit because the repository's history contains a differently-named product, and a bare `v2.5` would be ambiguous across the rename |
| **noteNFC's first tag** | `notenfc-v<versionName>-vc<versionCode>` on the reconstruction commit that passes §E, annotated with the boundary commit `c84b881` it descends from and the library tag it consumes. **This is the first tag the narrow product has ever had** — there is no 2023 or 2024 tag, locally or on origin (arch §4.1) |
| **nfc-tag-core's first tag** | `v0.1.0`, created in §B.1, annotated with the source commit `ac523d7` and a pointer to the provenance table |
| **Releases on the new repositories** | none at creation. `includeBuild` means the library has nothing to attach; the two apps are unpublished and locally signed, so a GitHub release would carry an APK the owner does not want distributed |

**Rollback.** Tags are deletable locally and on the remote (`git push --delete origin <tag>`) and
carry no data. The draft release is never touched, so there is nothing to undo.

---

## H. Documentation plan

### H.1 Untouched — history, in ServiceTag

Everything under `docs/design/` and `docs/superpowers/` is a historical design-process record of the
ServiceTag line and is **neither edited nor reinterpreted**: `docs/design/README.md`, the numbered
design documents D1–D13, the eight `phase-{0,1a,1b,1c,2a,2b1,2b2,4a}-evidence.md` files, the `g1/`
visual-gate pair, the two `spikes/` reports, and the `issues/` package (arch §4.9). Rewriting a
phase-evidence file would destroy the evidence this whole split is built on — the 1B and 1C device
rows are cited a dozen times above. Where a historical document is now out of date, the correction
goes in a *new* document, with a pointer, exactly as D13 corrected D6.

`docs/architecture/product-split-archaeology.md` is likewise final: this package cites it, and a
reviewer is reading it.

### H.2 Changed — the canonical surfaces

| Surface | Change |
|---|---|
| **`README.md`** (ServiceTag) | currently describes the **merged** noteNFC+ServiceTag scope (arch §4.9) and opens `# noteNFC`. Rewritten to describe ServiceTag only: the title, the one-paragraph what-it-is, the "What it does today" list with the note-utility framing removed, the identity block (`applicationId`, NDEF type, deep-link scheme), the **Building** section gaining the `--recurse-submodules` requirement and the submodule/`includeBuild` mechanism, the **Signing** section pointing at `~/.config/servicetag/`, and the **"Cutover from the old package"** section replaced by a pointer to this runbook. A "Related projects" block links noteNFC and nfc-tag-core |
| **`README.md`** (noteNFC, new repo) | written fresh: what the narrow product is and is not, its true ancestry from 2023 with the boundary commit named, the note that the inherited ServiceTag design package was removed going forward and still exists in history, `--recurse-submodules`, signing from `~/.config/notenfc/`, and the legacy-tag promise (the `md5_short` filter is kept permanently) |
| **`README.md`** (nfc-tag-core, new repo) | the provenance table from target §4.6, the invariant list from target §4.3, the public API, the two documented *patterns* the library does not own (the reader-mode `LifecycleResumeEffect` idiom and the mint→write→verify→complete-else-abandon provisioning protocol), and the forbidden-knowledge rule with its scan |
| **`docs/design/README.md`** (ServiceTag) | one **new paragraph at the top**, no table rows edited: this package's scope is the ServiceTag line; the product split happened on <date>; the narrow product now lives at `GonzRon/noteNFC` and the shared NFC mechanism at `GonzRon/nfc-tag-core`; every document below is historical and its "noteNFC" means "this app before the rename". Its terminology note — which defines "noteNFC tag payload format v1" as record type `com.loosecannon.notenfc:tag` — gains a sentence saying the type is now `com.loosecannon.servicetag:tag` and that the old string is noteNFC's |
| **D3 (`03-target-architecture.md`)** | append a short **"After the product split"** section: the NFC mechanism moved to `nfc-tag-core`; D3 §9's rules are now library invariants (with the mapping); the `md5_short` filter and the `DISPATCH_NFC_MESSAGE` item at targetSdk 37 are restated against the new identity. The existing §9 text stays, because the 1B/1C evidence rows reference it by section number |
| **D7 (`07-implementation-sequence.md`)** | the **"Product separation — deferred convergence operation"** section is the one place in the historical corpus that describes this operation prospectively, including the three-artifact target shape and the eight "when the split runs" conditions. It gains a closing block: *executed on <date>; see `docs/architecture/product-split-{archaeology,target,migration}.md`*, and a line-by-line tick against its own eight conditions — archaeology before mutation ✓, durable checkpoint and rollback ✓, remote rename only after local proof ✓, both apps installable together with the five-way dispatch proof ✓ (§E), identity-preserving data migration through the canonical backup format ✓ (§C.6), explicit-user-intent tag rewrite ✓ (§D), distinct ServiceTag key with the original preserved ✓ (target §8), independent green CI for all three ✓ (target §9). Its Phase 3 / Phase 3R roadmap lines gain "begins after the split completes". **Nothing else in D7 is edited** |
| **D13 (`13-compatibility-policy.md`)** | one appended note: its anticipated chooser window ("Both apps match `md5_short`; Android shows a chooser until the old app is removed") **does not arise**, because R3 removes the type from ServiceTag entirely — with §E check 4 as the evidence |
| **This package** | `product-split-target.md` and `product-split-migration.md` gain, at completion: the observed values for every "to observe on-device" marker (or the recorded contradiction), the ratified status of every **[P*n*]** proposal, and the three first tags from §G. `product-split-evidence.md` is created in ServiceTag per §D.3 |
| **`.superpowers/split/ledger.md`** | one line per completed phase, as it has for A and B |

**Verify.** No file under `docs/design/` or `docs/superpowers/` has an edited line except the three
appended blocks named above (`git diff --stat` shows additions only). Every cross-reference in the
new READMEs resolves. A reader who opens ServiceTag's `README.md` cannot mistake it for noteNFC, and
a reader who opens `docs/design/` is told in the first paragraph what era they are in — which is the
brief's stated reason for this section: *"so no future session mistakes the maintenance product for
noteNFC"*.

**Rollback.** Documentation commits are independently revertible and touch no code.

---

## I. Rollback, end to end

The preserved artifacts make every phase reversible. Read this table from the row you are in,
upward: the earlier the failure, the cheaper it is.

| Failed in | What has changed | Recovery |
|---|---|---|
| **§A.1** ServiceTag identity conversion | commits on the `product-split` branch in a worktree | `git reset --hard ac523d7` on `product-split`, or revert the offending task commit. `master`, `origin`, the tag `pre-split-checkpoint` and the branch `pre-split-master` are untouched. Cost: the conversion work |
| **§A.2** noteNFC reconstruction | one local directory | delete `~/Documents/Projects/AndroidStudioProjects/noteNFC-narrow` and re-clone from `c84b881`, which is immutable history. Cost: nothing but time |
| **§A.3** library extraction | one local directory | delete it. Nothing references it yet. Cost: nothing |
| **§A.4** both consume | submodule + `settings.gradle.kts` commits in two local repositories | `git submodule deinit -f && git rm -f third_party/nfc-tag-core`, revert the two commits in each app. Each app's own NFC layer is still in its history and can simply be un-deleted. Cost: the extraction's integration |
| **§B.1** library repo created | a new remote repository exists | the token **cannot delete**. Make it private and rename it aside; record the abandoned name in the ledger. Cost: a burned repository name |
| **§B.2** rename | `GonzRon/noteNFC` is now `GonzRon/ServiceTag` | `gh repo rename noteNFC` — reversible in both directions, and GitHub redirects either way. Reset the local remote URL. **The recovery refs stay in place throughout and are the anchor for everything below.** Cost: nothing |
| **§B.3** new noteNFC pushed | a second new remote repository exists, and it has claimed the `noteNFC` path from the redirect | rename it aside (which restores the old redirect), fix locally, re-push. Cost: a name, briefly |
| **§B.4** issue transfer | two issues moved | `gh issue transfer` back. Comment history and redirects survive; the backlinks become the audit trail. Cost: nothing |
| **§C.1–C.8** phone restore and proof | **the old package is still installed and still holds the live data** — this is the whole point of the §15 ordering | uninstall the ServiceTag package and continue using the modern app. The SAF tree is untouched in the same-tree case (8 `alreadyPresent`, 0 written). Cost: nothing |
| **§C.9** old package uninstalled | the old app's data and its `noteNFCURLs` prefs map are **gone** | reinstall the preserved signed **2.4 / versionCode 6** APK from `~/Documents/Projects/AndroidStudioProjects/noteNFC-releases/` — built from `ac523d7`, signer DN `CN=noteNFC, O=GonzRon`, certificate SHA-256 in the checkpoint ledger — re-grant its SAF tree, and import the preserved set from `~/Documents/Projects/AndroidStudioProjects/noteNFC-backups/pre-split-2026-09-16/` (or the fresher `transition-<stamp>` set). What does **not** come back: the `noteNFCURLs` map, which no backup ever contained. **This is the only genuinely irreversible step in the runbook, which is why C.6's proof is its precondition** |
| **§C.10 / §D / §E** noteNFC installed, tags migrated, coexistence | tags rewritten; two apps installed | uninstall either app freely. Any tag can be rewritten **except a locked one** — locking is exercised only on the spare **T8**. A migrated tag can be rewritten back to the pre-split envelope by hand with a generic writer if a rollback ever needs it, because the body is unchanged **[P15]** |
| **The whole split** | everything above | `git checkout pre-split-checkpoint` (or `pre-split-master`) in a fresh clone of the renamed repository; rename the repository back to `noteNFC`; transfer the two issues back; rename the two new repositories aside; reinstall the 2.4 APK and import the preserved set. The pre-rewrite history bundle beside the project directories covers the one case none of this does — a corrupted or force-pushed history — because it predates the 2026-09-14 `git filter-repo` rewrite |

**One rule that makes all of it hold.** No recovery ref is ever deleted, and no step is designed to
require a repository delete — which is also what the active token's capabilities force
(arch §4.3).

---

## J. Reviews — the ten gates

The brief's §34 defines ten review boundaries across the A–N sequence. The ledger anchors the first
three (checkpoint, archaeology, architecture); the mapping of 4–10 onto the remaining phases is this
design's reading, **[P18]**, since the brief text is not in this worktree.

| Gate | Phase(s) | Reviewed against | Entry condition |
|---|---|---|---|
| **1** | **A — checkpoint** *(done)* | recovery refs pushed, signed rollback APK preserved with its certificate recorded, backup set preserved and hash-verified, phone untouched | `pre-split-checkpoint` and `pre-split-master` both at `ac523d7`, on origin |
| **2** | **B — archaeology** *(done)* | evidence-only, no proposals; every surprising claim re-verified; discrepancies recorded | `docs/architecture/product-split-archaeology.md` at `1dd6be9`, its §9 listing four resolved discrepancies |
| **3** | **C — target architecture** *(this package)* | R1–R9 honoured; every named module/package/file exists or is marked NEW; every unobserved platform claim marked; the forbidden-knowledge boundary stated and testable; proposals separated from rulings | `product-split-target.md` + `product-split-migration.md` committed on `product-split` |
| **4** | **D — ServiceTag identity** | §A.1 task list complete; the identity table in target §3 true of the **built APK**, not just the source; legacy `md5_short` gone in all five places; the manifest↔constant assertion test green; `5.json`'s `identityHash` **unchanged**; clean-checkout build green | §A.1 whole-phase Verify passes |
| **5** | **E — noteNFC reconstruction** | true ancestry (30 commits, zero merges, root `5fb6aed`); the four scrub obligations discharged; no tracked APK; the legacy vectors intact and green; the 2024 tech catch-all **not** re-inherited; clean-checkout build green | §A.2 whole-phase Verify passes |
| **6** | **F — nfc-tag-core** | the forbidden-dependency scan green with a reasoned allow file; every invariant in target §4.3 tested; the provenance table complete and each `git log --follow` starting point resolving; no product vocabulary in `main` **or** in fixtures | §A.3 Verify passes; `v0.1.0` tagged |
| **7** | **G — both consume** | both apps on the **same** library tag; both negative tests (missing submodule, unpinned submodule) demonstrated failing; clean `--recurse-submodules` clone building on a **second workstation**; each app's sibling-isolation test green in its own direction | §A.4 Verify passes |
| **8** | **H — data and attachment migration proof** | eleven tables with identical id sets and relationship fields; 8×3 attachment hashes equal; the same-tree restore reporting 8 `alreadyPresent`/0 written; the empty-tree restore reporting 8 written on the emulator; every difference accounted for by §C.8 and nothing else. **This is the gate that must pass before §C.9's irreversible uninstall** | §C.6 and §C.7 Verify pass |
| **9** | **I + J — tag migration and coexistence** | §D.3's sixteen observation rows recorded with verdicts (an INCONCLUSIVE stays INCONCLUSIVE); §E's eleven checks passed with the owner's actions held to 11 (floor 9); zero chooser dialogs on checks 1–4; zero database writes on checks 5–7 | §D and §E Verify pass |
| **10** | **K + L + M + N — remotes, CI, docs, handoff** | three repositories, three green CI runs; the two issues transferred with backlinks both ways and the notes added to the four shared-NFC issues; the three first tags created; documentation changed only where §H says and additively where it touches history; every **[P*n*]** proposal ratified or superseded; every "to observe on-device" marker resolved to an observation or an explicit deferral | §B, §F, §G, §H Verify pass |

**No gate is self-certified.** Each is reviewed against the artifact it names, by someone who did not
produce it — the pattern already used for gates 2 and 3.

---

## K. Proposals introduced by this runbook

Continuing the target document's numbering; P1–P12 are there.

| # | Proposal | Where |
|---|---|---|
| **P13** | `GonzRon/nfc-tag-core` is created **before** the rename, because a git submodule needs a URL and the clean-checkout/CI requirement cannot be met without one. Creating a new repository mutates nothing that exists; the rename remains the last irreversible remote act | §B.1 |
| **P14** | The sub-ordering within §B (create library → rename → create noteNFC → transfer → enable CI) is this design's reading of the brief's §27, whose text is not in this worktree | §B |
| **P15** | The Migrate-tag tool **reuses** the pre-split UUID as the new `payloadKey`, making the migration a pure envelope rewrite: the body is the same 18 bytes, the uniqueness constraint is `(payload_format, payload_key)`, and nothing else references the key — so the row stays comparable to its pre-split export and a hand rollback stays possible | §D.1 |
| **P16** | The legacy `md5_short` test tag (T3) is written with a generic third-party NFC writer rather than by adding an encoder to either codebase. No encoder for that type has ever existed in this estate and adding one would contradict "decode-only" | §D.2 |
| **P17** | First tags are product-prefixed (`servicetag-v…`, `notenfc-v…`) because ServiceTag's repository history contains a differently-named product and a bare `v2.5` would be ambiguous across the rename | §G |
| **P18** | The mapping of §34's gates 4–10 onto phases D–N. Gates 1–3 are anchored by the ledger; the rest is inferred from the A–N sequence | §J |
