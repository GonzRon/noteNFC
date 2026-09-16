# Product-split migration runbook — ServiceTag / NoteTag / nfc-tag-core

The ordered *how*. Companion to `docs/architecture/product-split-target.md` (the *what*) and
`docs/architecture/product-split-archaeology.md` (the evidence, cited as "arch §n"), with the
archaeology review's corrections cited as "review correction *n*" / "review addition *n*".
Binding: the brief at `.superpowers/split/brief.md` (cited as "§n") and the owner's rulings
**O1–O15** plus gate corrections **C1–C9** in `.superpowers/split/ledger.md`.

**How to read this.** Every step has **Do**, **Verify**, and **Rollback**. No step is complete until
its Verify passes; no step starts until the previous one's Verify has passed. §35's rule applies to
every consequential mutation: an exact rollback point, a backup set, the current signed APK, the old
signing key, the pre-split checkpoint, no deletion of old refs, no deletion of the attachment tree,
no silent tag rewrites, and **no uninstall of the old modern package until ServiceTag's migration is
independently proven**.

**Sequence (§5).** A freeze/checkpoint · B archaeology · C target architecture · **D local ServiceTag
identity conversion** · **E narrow-product reconstruction** · **F nfc-tag-core extraction** · **G
both apps consume** · **H data + attachment migration proof** · **I physical-tag proof (now:
coexistence of final products only)** · **J coexistence proof** · **K remote repository changes** ·
**L independent CI** · **M canonical documentation** · **N final review/handoff**. *Local proof comes
before consequential remote mutations.* This runbook's §A covers D–G, §B covers K–L, §C covers H,
§D covers I, §E covers J, §F–§H cover M, §I covers rollback, §J maps the §34 gates.

**Standing constraints.**

- **The operating rule: vet everything possible on the emulator; the phone is used only where RF
  hardware or the real install is required.** Every proof that can be a JVM test is a JVM test;
  every proof that needs Android but not a radio runs on the emulator; the phone is reserved for the
  physical taps in §D and §E and for the real-install migration in §C.
- The attached phone holds the owner's **real data**. **No instrumented suite runs on it** (§15) —
  an instrumented run wipes app data. Automated suites go to the emulator; the phone gets only §C,
  §D and §E, and only once a fresh export exists.
- `adb` is always pinned by an `ANDROID_SERIAL` the operator exports once. Serials, phone model and
  codename, and phone folder names appear nowhere in this document.
- Naming: **noteNFC is a historical name only** (O1). Nothing named noteNFC exists after the split
  and nothing reuses `com.loosecannon.notenfc`.
- **[P*n*]** markers continue the target document's numbering; §K lists what is still open.

**Preserved artifacts this runbook depends on** (Phase A, done):

| Artifact | Location | What it is |
|---|---|---|
| Rollback APK | `~/Documents/Projects/AndroidStudioProjects/noteNFC-releases/` | the signed 2.4 / versionCode 6 build of `ac523d7`, signer DN `CN=noteNFC, O=GonzRon`, certificate SHA-256 in the checkpoint ledger (arch §4.5) |
| Pre-split backup set | `~/Documents/Projects/AndroidStudioProjects/noteNFC-backups/pre-split-2026-09-16/` | a data archive at format 5 (9 419 B) and an artifacts archive at artifacts format 1 (13 651 544 B) sharing one `backupSetId`, 8 entries hash-verified, plus a database + prefs snapshot (arch §7, §7.5) |
| Pre-rewrite bundle | alongside the project directories | the history bundle taken before the 2026-09-14 `git filter-repo` rewrite (arch §4.1) |
| Recovery refs | on `origin` **and** locally | tag `pre-split-checkpoint` and branch `pre-split-master`, both at `ac523d7`. **Not deleted until all three repositories are green** (§27 step 12) |

---

## A. Local transition (sequence D → E → F → G)

Three local repositories are built and proven before anything remote moves. The library is extracted
*after* ServiceTag's identity conversion, so the extraction happens once against final code and the
provenance table in target §4.6 stays true.

### A.0 Pre-flight

**Do.**

```bash
cd ~/Documents/Projects/AndroidStudioProjects/noteNFC
git fetch --all --tags
git rev-parse pre-split-checkpoint^{commit} refs/heads/pre-split-master origin/master
git -C ../noteNFC-split rev-parse HEAD        # the product-split worktree
```

**Verify.** All three refs resolve to `ac523d7`; the `product-split` worktree holds the docs package;
`git status` clean in both checkouts. **Rollback.** None needed — read-only.

### A.1 ServiceTag identity conversion (sequence D)

An SDD-style task list: one commit per task, each with its own verification, each independently
revertible. The scale is the reason for the list — `com.loosecannon.notenfc` occurs **2 028 times
across 236 files** (arch §4.6, §9 D4) — and §11 requires a deliberate inventory, not a blind
search-and-replace.

| # | Task | Files | Verify |
|---|---|---|---|
| **1** | `rootProject.name = "ServiceTag"` (an omission from the archaeology inventory — review correction 11) | `settings.gradle.kts:20` | `./gradlew projects` shows the new root name |
| **2** | `namespace` and `applicationId` → `com.loosecannon.servicetag`; keystore path → `~/.config/servicetag/keystore.properties`; `versionCode`/`versionName` bumped | `app/build.gradle.kts` | `./gradlew :app:assembleDebug`; `aapt2 dump badging` shows the new package |
| **3** | Move the Kotlin package roots: `app/src/{main,debug,test,androidTest}/kotlin/com/loosecannon/notenfc/…` → `…/servicetag/…` and `core/src/{main,test}/kotlin/com/loosecannon/notenfc/core/…` → `…/servicetag/core/…`; rewrite every `package`/`import` | 233 `.kt` files + both manifests + `app/build.gradle.kts` | `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug`, **plus a scoped check only**: no `package` or `import` declaration and no source path root still says `com.loosecannon.notenfc` — `git grep -nE '^\s*(package\|import)\s+com\.loosecannon\.notenfc' -- app core` empty, and `git ls-files app core \| grep -c 'com/loosecannon/notenfc/'` = 0. **The repository-wide zero-hit grep does NOT belong here**: tasks 5–7 still legitimately hold `com.loosecannon.notenfc` in the NDEF type constants, the manifest filter path and the deep-link literals until they run, so that assertion is the whole-phase verification below |
| **4** | The **five FQN `android:name` literals that do not follow `namespace`** (review correction 4): `com.loosecannon.notenfc.NoteNfcApp` (application), `…MainActivity`, `…ShareActivity`, `…nfc.NfcDispatchActivity` in `app/src/main/AndroidManifest.xml`, and `…debug.DebugBackupActivity` in `app/src/debug/AndroidManifest.xml` | both manifests | the merged manifest contains no `notenfc` substring; the app launches |
| **5** | The **one Gradle-owned identity value** (C9, target §4.8): `ndefExternalDomain`, `ndefTypeName`, `aarPackage` in `app/build.gradle.kts`, feeding `manifestPlaceholders["ndefTagPath"]` and three `buildConfigField`s; the manifest's `android:path` becomes `${ndefTagPath}` (still an **exact** path, never `pathPrefix`); the app builds its `TagIdentity` from `BuildConfig` | `app/build.gradle.kts`, `AndroidManifest.xml:84-88`, the NFC wiring | **both** binding tests green: the JVM test on `TagIdentity`-from-`BuildConfig`, and the emulator test that `queryIntentActivities` on `vnd.android.nfc://ext/<externalType>` resolves to exactly this app's dispatch activity |
| **6** | **Drop legacy `md5_short` entirely** (O2/O3): the `LEGACY_TYPE`/`LEGACY_TYPE_NAME` constants, `legacyKeyPattern`, the `decodeLegacy` branch, `TagPayload.LegacyMd5`, `PayloadFormat.LEGACY_MD5`, `Resolution.UnknownLegacy`, the `Legacy` sheet, the `"LEGACY_MD5"` trampoline wire value, the second manifest filter, and the matching tests. Record it as **a deliberate reversal of D6's "kept permanently" promise** | `core/…/core/nfc/NdefCodec.kt:50-53,82-88`, `core/…/core/model/TagBinding.kt`, `core/…/core/usecase/ResolveTag.kt`, `app/…/ui/scan/{ScanViewModels,TagResultSheet}.kt`, `AndroidManifest.xml:89-93`, `core/src/test/…/NdefCodecTest.kt` | green suites; `git grep -i md5` over `app core` empty; the manifest declares exactly **one** `NDEF_DISCOVERED` filter |
| **7** | Deep-link scheme literals → `servicetag`; **`notenfc://` gone entirely** (O3) | `core/…/core/nfc/TagRoute.kt:10`, `core/…/core/links/DeepLinkRoute.kt:17`, `AndroidManifest.xml:56-58`, and the six `androidTest` files that hard-code `notenfc://` (arch §4.6) | `git grep -c 'notenfc://'` = 0; route tests green |
| **8** | `app_name` → `ServiceTag`; theme → `Theme.ServiceTag`; the `Application` subclass → `ServiceTagApp`; the nav-root composable → `ServiceTagRoot` (resolving the two-classes-one-name collision, arch §4.6); the debug manifest label | `res/values/{strings,themes}.xml`, both `NoteNfcApp.kt` files, both manifests | `./gradlew :app:assembleDebug :app:assembleRelease`; the label read back from the built APK |
| **9** | **A new ServiceTag launcher icon** replacing the inherited `ic_launcher` mipmap set — **a coexistence requirement, not cosmetics** (review addition 3): two apps that look identical on the launcher make every device observation and every NFC-allowlist entry ambiguous | `app/src/main/res/mipmap-*/`, the manifest's `android:icon` | the two apps are visually distinguishable on the launcher and in Settings → Apps |
| **10** | `DB_NAME` → `servicetag.db`; `AppPrefs` file name → `servicetag`; export prefixes → `ServiceTag-data-<stamp>.zip` / `ServiceTag-artifacts-<stamp>.zip`, and the three test fixtures that hard-code the old prefix | `app/…/di/AppGraph.kt:201`, `app/…/prefs/AppPrefs.kt:13`, `app/…/backup/SafBackupSetIO.kt:60-67`, `AppSmokeTest`, `AttachmentsDeviceProofTest`, `BackupViewModelTest` (arch §7.7 item 2) | JVM suites green. **A fresh install has no old database file to find, and the importer never reads a file name** (arch §7.3, §7.7 item 4) — none of this can affect restore correctness |
| **11** | Re-export the Room schema under the new `AppDatabase` FQN: `app/schemas/com.loosecannon.servicetag.data.room.AppDatabase/1.json`…`5.json`; delete the old directory; fix the migration-ladder tests' resource paths | `app/schemas/…` and the chain tests (arch §7.7 item 5) | every hop plus the full 1→5 run green; **`5.json`'s `identityHash` unchanged** — it is a structural hash independent of package (arch §7.1). **If it changes, stop**: the schema drifted and the phone's database will not open |
| **12** | Sibling isolation in ServiceTag's direction: a record typed `com.loosecannon.notetag:tag` decodes as `Foreign`, never as a recognised payload (target §4.3 invariant 1, C8) | `core/src/test/…/NdefEnvelopeIsolationTest.kt` (new) | green. This is the test that stops ServiceTag adopting a NoteTag tag |
| **13** | Remaining identity carriers from review correction 11: `BuildConfig.VERSION_NAME` displayed in `ui/settings/SettingsScreen.kt:241`; the **three** files of `app/src/debug` (manifest, `DebugBackupActivity.kt`, `res/layout/activity_debug_backup.xml`), not one; `.gitignore` and `proguard-rules.pro` package references; the `<queries>` block reviewed and left unchanged (those schemes are link targets, not identity) | as listed | `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug` |
| **14** | **README hygiene**: `README.md:121-125` reproduces the signer DN **and** the full colon-separated certificate SHA-256 in the repository (review correction 11). Reduce to a pointer; the fingerprint lives in the evidence file | `README.md` (the full rewrite is §H) | `git grep -c 'CN=noteNFC'` = 0 outside history |

**A.1.1 ServiceTag regression proof (§21) — the entry condition for gate 5.** ServiceTag must be
*functionally equivalent to pre-split except the deliberate identity changes*. The proof runs the
existing suites plus a device pass on the emulator over §21's list: the core maintenance product; NFC
and link behaviour (asset binding, ambient resolution, standalone links, intentional write, capacity,
read-back, foreign/malformed safety); attachments; backup (all-or-nothing export, data-only restore,
artifacts restore, set mismatch, missing/hash drift); and UX (Apollo theme, Dashboard, Assets,
two-tab navigation, Read/inspect tag, ambient NFC as the normal read path).

**Verify (whole phase).**

```bash
cd ~/Documents/Projects/AndroidStudioProjects/noteNFC-split
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain

# The repository-wide assertion, run ONLY after the last identity task (7) has landed:
git grep -lE 'com\.loosecannon\.notenfc|notenfc://|md5_short|noteNFC|NoteNfc' \
  -- app core gradle settings.gradle.kts README.md
# expected: no output. Hits under docs/ are HISTORY and are left alone (§30, §H).
```

Run in task order this assertion is expected to fail after tasks 3 and 4 and to pass only from task
7 onward: the NFC type constants (tasks 4 and 6), the manifest filter path (task 6) and the deep-link
literals (task 7) each legitimately still carry the old string until their own task runs. Treating it
as a per-task gate would either block task 3 or invite someone to edit constants out of order.

Then a clean-checkout build from a directory that has never held the project (§26).

**Rollback.** Every task is one commit on `product-split`; revert the task, or `git reset --hard` to
`ac523d7`. `master`, `origin` and both recovery refs are untouched.

### A.2 NoteTag reconstruction (sequence E)

**Do.**

```bash
cd ~/Documents/Projects/AndroidStudioProjects
git clone --no-local noteNFC NoteTag
cd NoteTag
git remote remove origin                     # it gets its own remote in §B
git checkout -B master c84b881               # master IS the narrow semantic endpoint (O6)
git for-each-ref --format='%(refname)' refs/heads refs/tags \
  | grep -v '^refs/heads/master$' | xargs -r -n1 git update-ref -d
git reflog expire --expire=now --all && git gc --prune=now
```

Then the **first commit** (C6): delete the ServiceTag-only material at the new branch tip.

```bash
git rm -r docs                               # 70 inherited ServiceTag design files
git rm app/release/app-release.apk           # the 2024 shipped artifact; identical blob at 3a3c69a
git commit -m "start the narrow product from its own history"
```

**State plainly, in that commit message and in the README, what this does not do** (C6): it does
**not** scrub any of it from history, and **no second history rewrite will be performed**. What stays
reachable in the public history: the ServiceTag design documents; the owner's GitHub handle and
personal issue URLs in `docs/design/issues/applied.md` and one superpowers plan; the release-signer
certificate digests in `docs/design/phase-0-evidence.md`; and the author name plus personal email in
the commit metadata of **all 30 preserved commits** (review correction 10). None of it is a secret —
a public handle and public-key fingerprints — and all of it is already public in the repository this
branch came from. The repository is public by ruling, for exactly that reason.

Then the rewrite. **O6 is explicit: NoteTag is a substantial rewrite on modern engineering and
nfc-tag-core; historical technical debt is not preserved for authenticity.** `c84b881` supplies
provenance, intent, lessons and the share→write→tap evidence — not code to keep alive.

| # | Commit | Verify |
|---|---|---|
| **1** | the deletion above | `git grep -lE 'GonzRon|github\.com/|SHA-?256:'` empty at the tip; `git ls-files | grep -c '\.apk$'` = 0; the tree is 46 files (116 minus the 70 under `docs/`) |
| **2** | identity: `applicationId`/`namespace`/Kotlin root → `com.loosecannon.notetag`; `rootProject.name = "NoteTag"`; label `NoteTag`; its own launcher icon (review addition 3). The historical mixed-case `com.looseCannon.noteNFC` root goes with the rewrite | `./gradlew :app:assembleDebug`; `aapt2 dump badging` shows the new package and label |
| **3** | **re-run CI from scratch.** `c84b881` itself never ran on a runner — the green Phase-0 evidence belongs to pre-rewrite twins `ccdb9d3`/`12e2c09` whose SHAs no longer exist (review correction 8). No green claim is inherited | a green run on the new repository's own workflow (after §B.4) |
| **4** | signing: `~/.config/notenfc/keystore.properties` via the same `Properties`-from-`user.home` mechanism — **the existing noteNFC key, unchanged, never rotated** (§12); `versionCode`/`versionName` past the historical 2 / `1.1` | `./gradlew :app:assembleRelease` produces a **signed** release build (§12 requires the proof); the certificate SHA-256 matches the existing key's, recorded not reproduced |
| **5** | the NoteTag application: share receiver, writer screen, ambient dispatch activity, minimal local store. **Delete the three 2024 activities** rather than modernise them (O6). Declare exactly **one** `NDEF_DISCOVERED` filter on the exact path `/com.loosecannon.notetag:tag`; **do not re-inherit the 2024 `TECH_DISCOVERED` catch-all or `res/xml/nfc_tech_filter.xml`** | `git grep -c nfc_tech_filter` = 0; exactly one NFC filter in the merged manifest |
| **6** | the **NoteTag v1 tag format** (O13/O14, target §4.9): one external record, `version|kind|flags|body`; kinds `0x01 JOPLIN_NOTE`, `0x02 URI`, `0x03 LOCAL_REF`; `0x04`+ reserved; **no AAR**; the automatic writer decision compact → URI-if-it-fits → LOCAL_REF, with "fits" decided by the exact encoded message against the **measured** `Ndef.maxSize` | JVM tests per kind: encode/decode round-trip, malformed bodies, an unknown kind, an unknown version; **capacity selection driven by injected `maxSize` values** (§D.3) with the exact encoded message at `maxSize`, `maxSize - 1` and `maxSize + 1`, asserting compact → URI-if-it-fits → `LOCAL_REF` and that the fallback fires **only** when the message genuinely does not fit — `needed` is `toNdefMessage().toByteArray().size` with **no TLV allowance** (target §4.3 invariant 7); **no test asserts a character count** |
| **7** | the **minimal local store** (O14, ratified P19): a single atomically-replaced JSON file behind a small interface; `LOCAL_REF` targets plus convenience metadata; **never required to resolve a `JOPLIN_NOTE` or `URI` tag**; the writer tells the user when a tag will only work on this phone. **Plus the `LOCAL_REF` crash-consistency invariant (G2)**: the mapping is durably stored **before** the physical tag is written, and a `LOCAL_REF` whose mapping has not committed is never successfully written. Sequence: allocate the UUID → **atomically persist** the mapping (temporary file, `fsync`, atomic rename) → write and verify the tag → success retains the mapping; failure, cancellation or a lost tag → best-effort removal of the orphan mapping. Persist-then-write can only leave a few invisible bytes of orphan JSON; write-then-persist can leave **a live tag that resolves to nothing on the phone that wrote it**, which is the one outcome a device-bound kind must never produce | a test that `JOPLIN_NOTE` and `URI` tags resolve with the store deleted; a test that a `LOCAL_REF` miss produces a message, not a crash; and the **named failure-injection deliverable**, two cases: *persist succeeds, tag write fails → the mapping is removed*, and *persist fails → no tag write is attempted at all* |
| **8** | a **copied, not shared** safe `ACTION_VIEW` launch policy: scheme allowlist, whitespace and control-character rejection, `ActivityNotFoundException` **and** `SecurityException` caught. The 2024 app passed stored text straight to `startActivity` with no `try/catch` and crashed on a missing Joplin (arch §2.5, §5.13) | a test per rejected scheme; a test that a missing handler is a message, never a crash |
| **9** | malformed/foreign wording: a ServiceTag tag is **not** interpreted as a note (§23); sibling isolation in NoteTag's direction | a test that `com.loosecannon.servicetag:tag` decodes as `Foreign`; the writer offers only **Write over it** / **Cancel** and names the other app (ratified P11) |
| **10** | adopt `nfc-tag-core` for reader mode, read-before-write, capacity, verified read-back. **Requires §A.3 and §A.4**, so this lands last | the §23 acceptance list, end to end |

**NoteTag acceptance (§23) — the entry condition for gate 6.** Share a supported Joplin note or link
→ NoteTag receives it → intentional write → nfc-tag-core safe write → read-back verified → ambient
tap → NoteTag resolves → safe `ACTION_VIEW` → the correct note or link opens. Plus: a malformed tag
does not launch unsafe content; a ServiceTag tag is not interpreted as a note; a clean checkout
builds; CI passes; the dependency on a pinned nfc-tag-core is real (§6.3's assertion step).

**Verify (whole phase).** `git rev-list --merges --count c84b881` = 0 and `git log --oneline | tail -1`
is `5fb6aed` — the 2023 root — proving the ancestry is real and ungrafted (arch §2.7). Clean-clone
build green. **Rollback.** Delete the directory and re-clone; `c84b881` is immutable history.

### A.3 nfc-tag-core extraction (sequence F)

**Do.** Create `~/Documents/Projects/AndroidStudioProjects/nfc-tag-core`, `git init`, and build the
layout at target §4.1: `nfc-core/` (pure Kotlin/JVM, with **zero third-party, application or
framework runtime dependencies — the Kotlin stdlib only**, added by the `kotlin.jvm` plugin),
`nfc-android/`
(the Android NFC adapter), its **own root build** so it builds and tests standalone (O15), its own
wrapper and its own catalog whose alias *names* match the two apps'. Then, file by file, follow the
**provenance table** at target §4.6: it names every library file's source file, line range,
`git log --follow` starting point, and the change made on extraction. Write `README.md` around that
table — for a repository with no inherited history, the table *is* the provenance (§9).

| # | Commit | Verify |
|---|---|---|
| **1** | skeleton: root `settings.gradle.kts` (`include(":nfc-core", ":nfc-android")`), root `build.gradle.kts`, catalog, wrapper, `.gitignore` | `./gradlew projects` lists both modules |
| **2** | `nfc-core`: `NdefRecordData`, `TagIdentity`, `TagContent`, `NdefEnvelope` (optional AAR builder), `UuidBytes`, `ExistingContent`, `OverwriteReason`, `OverwritePolicy` + the JVM suite at target §4.5 | `./gradlew :nfc-core:test`; `./gradlew :nfc-core:dependencies` shows **only the Kotlin stdlib** on the runtime classpath — no third-party, application or framework dependency |
| **3** | `nfc-android`: `NdefBridge` (+ the new `serialisedSize`), `NfcReaderModeSession` (doc comment carried across verbatim, including why the platform NDEF check stays on), `TagWriter`/`TagInspection`/`WriteResult` with the message-size capacity rule on both paths — `serialisedSize()` is exactly `toNdefMessage().toByteArray().size`, with **no TLV allowance** (target §4.3 invariant 7) — `TagHandle`/`TagIo`/`RealTagIo` | `./gradlew :nfc-android:testDebugUnitTest :nfc-android:assembleDebug`; **no dependency back into either app** (O15) |
| **4** | `tools/forbidden-scan.sh` + `forbidden-scan.allow`, wired as a `check` dependency and as the first CI step | the scan passes; every allow entry carries a reason, inspected not accepted (§22) |
| **5** | `README.md`: the provenance table, the invariant list, the public API, and the two *patterns* the library does not own — the reader-mode `LifecycleResumeEffect { start(); onPauseOrDispose { stop() } }` idiom, and the mint→write→verify→complete-else-abandon provisioning protocol (arch §6.1, §6.2). Plus an explicit note that `TagWriteSession` is **deferred** and on what criterion it would be promoted (target §4.7) | a reader who has never seen either app can tell where each file came from and what is deliberately absent |
| **6** | `.github/workflows/ci.yml` per target §9 | green once the remote exists (§B.1) |
| **7** | the emulator `androidTest` suite for the adapter | green on the emulator, **locally**; never in CI |

**Verify.** `./gradlew build` green from a clean clone; `tools/forbidden-scan.sh` exits 0; every
`git log --follow` starting point in the provenance table resolves in the ServiceTag repository.
**Rollback.** Delete the directory; nothing references it yet.

### A.4 Both apps consume the library (sequence G, O15)

**Do.** In each app repository, once `GonzRon/nfc-tag-core` exists on the remote (§B.1):

```bash
git submodule add https://github.com/GonzRon/nfc-tag-core.git libs/nfc-tag-core
git -C libs/nfc-tag-core checkout nfc-tag-core-v0.1.0
git add .gitmodules libs/nfc-tag-core
```

Add the NEW block to `settings.gradle.kts` exactly as target §6.2 gives it (the `require` guard,
`include(":nfc-core", ":nfc-android")`, the two relocated `projectDir`s), the
`implementation(project(":nfc-android"))` line in `app/build.gradle.kts`, the CI checkout's
`submodules: recursive`, and the submodule-pin assertion step (target §6.3) — whose tag check is
`git describe --exact-match --match 'nfc-tag-core-v*' --tags HEAD`, an **exact product tag** rather
than any tag that happens to point at that commit — also kept as `tools/check-submodule-pin.sh` for
local runs. No branch is tracked: the submodule is deliberately detached at a tag, which makes
§19's "mutable HEAD not silently consumed" true by construction rather than by policy. Then delete from each app everything the library now
owns and replace it with the library's types — in ServiceTag that is `NdefBridge`,
`NfcReaderModeSession`, `TagWriter`, `TagIo`/`TagHandle`, the envelope half of `NdefCodec` and
`OverwritePolicy`, leaving the body codec, the row minting, the write protocol and every message
string behind in the app (target §4.7).

**Verify.**

1. `./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug`
   in ServiceTag, and the equivalent in NoteTag.
2. The pin assertion passes in both, and **both apps are on the same library tag**.
3. **The four negative tests, demonstrated failing and then restored**: (a) `rm -rf libs/nfc-tag-core/*`
   → the `require` fires at configuration time with the fix command; (b)
   `git -C libs/nfc-tag-core checkout HEAD~1` → the pin assertion reports "not at an exact
   `nfc-tag-core-v*` tag";
   (c) `touch libs/nfc-tag-core/nfc-core/src/main/kotlin/x` → "submodule working tree is dirty";
   (d) remove a catalog alias the library uses → a configuration-time failure naming the alias.
4. Clean-checkout proof per repository: `git clone --recurse-submodules <url> <tmp>` into a never-used
   directory, then that repo's CI task list; then once more **from a second workstation** (§19, §26).

**Rollback.** `git submodule deinit -f libs/nfc-tag-core && git rm -f libs/nfc-tag-core`, revert the
settings and dependency commits. Each app's own NFC layer is still in its history and can be
un-deleted.

---

## B. Remote transition (sequence K → L)

§27's order, *only after local proofs*: 1 checkpoint pushed · 2 rename `GonzRon/noteNFC` →
`GonzRon/ServiceTag` · 3 update local origin · 4 verify redirects/CI/webhooks · 5 create
`GonzRon/nfc-tag-core` · 6 push library history · 7 create `GonzRon/NoteTag` · 8 push the
reconstructed lineage · 9 configure CI independently · 10 move/recreate the explicit narrow-product
issues · 11 verify all three from clean clones · 12 **do not delete recovery refs until everything is
green.**

**One documented deviation, accepted by the controller.** Steps 5–6 (create and push the library) run
**before** step 2 (the rename). A git submodule needs a URL, so §A.4 — a *local* proof, which §27's
own preamble requires to come first — cannot complete until the library repository exists. §27 itself
provides for this: *"If hosting capabilities differ, make the most conservative reversible adjustment
and document it."* Creating a new repository mutates nothing that exists and is reversible by rename;
the rename of the live repository stays the last irreversible act. Order actually run: **5–6 → 2 → 3
→ 4 → 7 → 8 → 9 → 10 → 11 → 12.**

Capability note: the active `GonzRon` token holds `gist, read:org, repo, workflow` — enough to rename,
create and transfer — and **cannot delete a repository**; `delete_repo` lives only on the second,
inactive account (arch §4.3). **No step below requires a delete, and none may be designed to.**

### B.1 (§27 steps 5–6) Create and push `nfc-tag-core`

**Do.**

```bash
gh repo create GonzRon/nfc-tag-core --public \
  --description "Product-neutral NFC tag mechanism: external-record envelope, reader mode, safe writer" \
  --disable-wiki
cd ~/Documents/Projects/AndroidStudioProjects/nfc-tag-core
git remote add origin https://github.com/GonzRon/nfc-tag-core.git
git push -u origin main
git tag -a nfc-tag-core-v0.1.0 -m "extracted from ac523d7; provenance in README"
git push origin nfc-tag-core-v0.1.0
```

**Verify.** `gh repo view GonzRon/nfc-tag-core --json name,visibility,defaultBranchRef`;
`gh api repos/GonzRon/nfc-tag-core/tags` lists the tag; the Actions run is green (§B.6 enables it if
it is not on by default). **Rollback.** The token cannot delete: make it private and rename it aside
(`gh repo rename nfc-tag-core-abandoned`), which frees the name; record the abandoned name in the
ledger.

### B.2 (§27 step 2) Rename the existing repository to ServiceTag

**Do.**

```bash
# Pre-flight (§27 step 1): the recovery refs must already be on the remote.
gh api repos/GonzRon/noteNFC/git/refs/tags/pre-split-checkpoint --jq .object.sha   # ac523d7
gh api repos/GonzRon/noteNFC/git/refs/heads/pre-split-master     --jq .object.sha   # ac523d7

gh repo rename ServiceTag --repo GonzRon/noteNFC
```

**Verify.** `gh repo view GonzRon/ServiceTag --json name,visibility,isFork,hasIssuesEnabled,defaultBranchRef`.
The rename must preserve: visibility PUBLIC, not-a-fork, issues enabled, default branch `master`, all
36 issues, the one git tag, the 2023 draft release, and the CI history (arch §4.1, §4.8). Check each.
**Rollback.** `gh repo rename noteNFC --repo GonzRon/ServiceTag` — reversible in both directions, with
GitHub redirecting either way. The recovery refs stay in place throughout and are never deleted.

### B.3 (§27 steps 3–4) Update local origins and verify redirects, CI and webhooks

**Do.**

```bash
cd ~/Documents/Projects/AndroidStudioProjects/noteNFC
git remote set-url origin https://github.com/GonzRon/ServiceTag.git
git fetch --all --tags && git rev-parse origin/master pre-split-checkpoint
git -C ../noteNFC-split remote -v          # the worktree shares the repository's remote
curl -sI https://github.com/GonzRon/noteNFC | grep -i '^location'   # the old path redirects
gh api repos/GonzRon/ServiceTag/hooks --jq '.[].config.url'         # webhooks, if any
gh run list --repo GonzRon/ServiceTag --limit 3
```

**Verify.** The local remote resolves; the old path redirects; CI still triggers on a push; any
webhook still points somewhere valid. **Note**: the redirect from `noteNFC` lasts only until a new
repository claims that path — **nothing will**, because the narrow product is now `NoteTag` (O1), so
the redirect is permanent. **Rollback.** Rename back and reset the remote URLs.

### B.4 (§27 steps 7–8) Create `GonzRon/NoteTag` and push the reconstruction

**Do.**

```bash
gh repo create GonzRon/NoteTag --public \
  --description "Attach a note or a useful link to a physical NFC tag." --disable-wiki
cd ~/Documents/Projects/AndroidStudioProjects/NoteTag
git remote add origin https://github.com/GonzRon/NoteTag.git
git push -u origin master
```

**Verify.** `git log --oneline origin/master | tail -1` → `5fb6aed`, proving the 2023 root travelled;
the repository carries **no** `docs/` directory and **no** `.apk`; the first CI run is green (it is
the first run this lineage has ever had — review correction 8). **Rollback.** Cannot delete; rename
aside (`gh repo rename NoteTag-wip`) and re-push once fixed.

### B.5 (§27 step 10) Move the explicit narrow-product issues

**Do.** §10 and O8: only the explicit future-narrow-product work moves, by source semantics, never by
keyword. Transfer if the tooling supports it, else recreate with backlinks and mark the originals
moved.

```bash
# 1. Backlink FIRST, while the issue still has its original URL.
gh issue comment 6  --repo GonzRon/ServiceTag \
  --body "Moving to the standalone NoteTag product: https://github.com/GonzRon/NoteTag — see docs/architecture/product-split-migration.md."
gh issue comment 36 --repo GonzRon/ServiceTag --body "<same>"

# 2. Transfer.
gh issue transfer 6  GonzRon/NoteTag
gh issue transfer 36 GonzRon/NoteTag

# 3. Retitle under the NoteTag name (O8) and backlink the other way.
gh issue edit <NEW#> --repo GonzRon/NoteTag --title "Generalize external note/deep-link support beyond Joplin"
gh issue comment <NEW#> --repo GonzRon/NoteTag \
  --body "Transferred from GonzRon/ServiceTag (formerly GonzRon/noteNFC), where this product's history lives."
```

**Verify.** Both issues resolve in `GonzRon/NoteTag` with their bodies intact — in particular #36's
ownership statement — and the old URLs redirect; `gh issue list --repo GonzRon/ServiceTag --state all`
= 34. If transfer fails (the capability was assessed but never exercised — arch §4.3), fall back to
§10's alternative: recreate in NoteTag with the verbatim body, and close the original **marked moved**
with a pointer. **Rollback.** `gh issue transfer <NEW#> GonzRon/ServiceTag`; the backlinks stay and
become the audit trail.

### B.6 (§27 steps 9, 11–12) CI, clean clones, and the recovery refs

**Do.** Confirm Actions is enabled on both new repositories
(`gh api repos/<owner>/<repo>/actions/permissions`) and that each has a green run. Then verify all
three **from clean clones** (§26, §27 step 11): for each,
`git clone --recurse-submodules <url> <tmp> && cd <tmp> && ./gradlew <that repo's CI task list>` in a
never-used directory, plus one run from a second workstation for the two apps.

**Verify.** Three repositories, three green CI runs, three clean-clone builds. **No secrets, no
absolute home path, no developer-local Gradle state, no device id, no private signing material in any
source tree** (§26). **Only then**, and not before, may anyone consider the recovery refs
`pre-split-checkpoint` and `pre-split-master` retired — and this runbook does not retire them (§27
step 12, §35). **Rollback.** Disable Actions on the affected repository; no code change.

---

## C. Phone transition and the data migration proof (sequence H, §13–§15, O10)

§15's order, unchanged: **1** keep modern 2.4 installed · **2** export and verify the backup set ·
**3** install ServiceTag (new package) alongside · **4** restore into ServiceTag · **5** prove
ServiceTag's data and attachments · **6** (tag proof — now final-products only, §D/§E) · **7** only
after ServiceTag is independently proven, uninstall the modern old-package app · **8** install NoteTag
(new package; nothing reuses `com.loosecannon.notenfc`) · **9** coexistence tests.

The order is not stylistic. Until step 5 passes, the old package is the **only** holder of the data
and the **only** holder of the SAF grant — a persisted grant is scoped to the calling application and
cannot cross a package boundary, even for the same signing key (arch §7.4).

**Two prohibitions that hold throughout** (§13, §35): **never delete, relocate or rewrite the live
attachment folder**, and no instrumented suite runs on the phone.

### C.1 (§15 steps 1–2) Snapshot before

**Do.**

```bash
export ANDROID_SERIAL=<the phone>      # set once, never printed into a document
STAMP=$(date +%Y%m%d-%H%M%S)
OUT=~/Documents/Projects/AndroidStudioProjects/noteNFC-backups/transition-$STAMP
mkdir -p "$OUT"
```

1. In the modern app: **Settings → Backup → Export set**. It writes both archives or neither — a set
   whose halves disagree cannot exist (arch §7.2).
2. Pull both archives to `$OUT`.
3. Capture the database + prefs snapshot the same way Phase A did (the installed build is debuggable).
4. Record the installed app's `versionName`/`versionCode` and its certificate SHA-256, so §I knows
   what it is restoring to.

**Verify.**

```bash
cd "$OUT"
unzip -p <data>.zip manifest.json | jq '{formatVersion, schemaVersion, appVersion, backupSetId, counts, artifactCount, artifactBytes, dataSha256}'
# formatVersion 5, schemaVersion 5, backupSetId non-empty (a blank one at format 5 is refused as
# corrupt, because it could never pair with an artifacts file -- arch §7.2)
unzip -p <data>.zip data.json | sha256sum                     # must equal manifest.dataSha256
unzip -p <artifacts>.zip manifest.json | jq '{artifactFormatVersion, dataFormatVersion, backupSetId, entries: (.entries|length)}'
# same backupSetId as the data manifest; entries == artifactCount
```

Verify every artifact entry's SHA-256 against its manifest row. The pre-split baseline (arch §7.5, and
O10's list) is assets **5**, nfcTags 0, externalLinks 1, measurementDefinitions 13, eventProfiles 15,
profileFields 22, profileConsumables 14, assetEvents **26**, measurements **68**, consumableUsages 4,
attachments **8**. A count that has moved *up* since 2026-09-16 is fine — this export is the baseline
from here. A count that has moved *down* is a stop.

**Rollback.** Nothing changed; re-export.

### C.2 (§15 step 3) Install ServiceTag alongside

**Do.** Build the ServiceTag debug APK from the converted tree and install it. **Uninstall nothing.**
The applicationIds differ, so the OS treats them as unrelated apps and both sit on the launcher — with
**distinct icons and labels**, which is why task A.1/9 exists (review addition 3).

**Verify.** Both packages present; both launch; the modern app's data untouched (open it and see the
five assets); ServiceTag's database empty — a fresh package gets a fresh database, never a copied file
(arch §7.6). **Rollback.** Uninstall the ServiceTag package; the old package is untouched.

### C.3 (§15 step 4a) Acquire ServiceTag's own SAF grant — the same tree first

A grant cannot be inherited (arch §7.4, §7.7 item 8): ServiceTag must drive its own
`OpenDocumentTree` → `takePersistableUriPermission`.

**Do.** ServiceTag → **Settings → Attachment storage** → pick **the same folder the modern app already
uses**. The take is wrapped in `runCatching`; only a successful take updates the pref, and the screen
re-reads the store state so it shows what is actually true — some cloud-backed providers refuse a
lasting grant (arch §7.4).

**Same-tree discovery via stable locators** — the optimisation §13 allows, not a substitute for proof.
Every attachment locator is **store-relative**: `assets/<asset-id>/<attachment-id>.<ext>` or
`events/<event-id>/<attachment-id>.<ext>`, with no drive letter, authority, tree-URI fragment or
package name anywhere in it, confirmed against the real set's 8 rows (arch §7.4). The right tree is
simply the one that already contains those relative paths:

```bash
unzip -p "$OUT"/<data>.zip data.json | jq -r '.attachments[] | .storageLocator'
```

Confirm in ServiceTag's store-health readout that all 8 resolve **before any restore runs**. 8 of 8
means the same tree.

**If the grant is refused** (a cloud-backed provider that will not persist one), do not fight it: this
becomes the empty-tree case, which §13 requires to be proved independently anyway (C.7). **Never
delete, relocate or rewrite the live folder to make a grant work** (§13).

**Verify.** `StoreState` is `Ready(displayName, authority)`, not `NotConfigured`, not `AccessLost`;
the ServiceTag package holds exactly one persisted grant — a successful take of a *different* folder
releases the previous one, and grants otherwise accumulate (arch §7.4, spike S5). **Rollback.** Revoke
in system settings or pick again; picking is idempotent and the pref is written only on success.
Nothing in the tree has been written.

### C.4 (§15 step 4b) Restore the data

**Do.** ServiceTag → **Settings → Backup → Import (replace)** → the pulled `<data>.zip`.

The importer decodes **outside** the transaction — "refuse before touching data" — then, inside one
write transaction, deletes in reference-clearing order and inserts in reference order: assets
parents-first, definitions ENTERED-then-DERIVED, profiles, links, tags, events, attachments last
(arch §7.2). `validateGraph()` re-checks every FK-shaped reference and every locator shape *before*
the transaction opens; a format-5 file with a blank `backupSetId` is refused as corrupt.

**Why this needs no code change.** The backup content carries **zero package or product identity**:
grepping the real pulled `manifest.json`, `data.json` and the artifacts `manifest.json` for `notenfc`,
`loosecannon`, `com.loosecannon`, `NoteNFC`, `noteNFC` returns **zero hits in any of the three files**,
`appVersion` is a bare semver string, and the import path never inspects a file name or extension for
meaning (arch §7.3). A renamed build with an unmodified codec layer imports the preserved files
unchanged.

**Verify.** The import reports the same eleven counts as the manifest; `AppPrefs`'
`last_restored_backup_set_id` now holds the set id (it was **absent** before — no import had ever run
against this data, arch §7.5). **Rollback.** All-or-nothing inside one transaction; a failure leaves
ServiceTag's empty database as it was. If it succeeds but proves wrong, uninstall ServiceTag — the old
app still holds everything.

### C.5 (§15 step 4c) Restore the artifacts

**Do.** ServiceTag → **Settings → Backup → Restore files** → the pulled `<artifacts>.zip`.

`RestoreArtifacts.run(archive, expectedSetId)` throws `ArtifactsSetMismatch` **before touching any
entry** if the archive's `backupSetId` disagrees with the data restore's
`lastRestoredBackupSetId`, and is idempotent: bytes that already hash correctly are `alreadyPresent`,
never rewritten. Each entry is verified **twice** — the manifest's `sha256` against the local row
before anything is opened, and the store's own streaming digest after the write (arch §7.2).

**Verify (same-tree case).** **8 `alreadyPresent`, 0 written, 0 skipped** — the files are already
there and already hash correctly. That is the same-tree proof: idempotence, not a copy. A single
`written` means the tree was not the one in use; a `skipped` means a hash mismatch and is a stop.
**Rollback.** Nothing is written in the same-tree case. In the empty-tree case the restore deletes any
stale document at a locator first and deletes the partial document on failure, so a failed entry
leaves no debris (arch §7.4).

### C.6 (§15 step 5, §24) The data proof — stable-id comparison

**The principle** (arch §7.4): every id in the backup format is a verbatim UUID string, every
relationship is by id, **no id is package-, device- or install-qualified**, so *an id that exists
pre-split must exist, unchanged, post-split*. That sentence is the proof.

**Do.** Export a fresh set from the **restored ServiceTag**, pull it, and compare its `data.json`
against the preserved one. `BackupCodec.encode()` sorts every list by `id`, and children by
`sortOrder` within parent, so the same input always produces the same bytes (arch §7.2) — a
`data.json` differing only in the fields listed in C.8 is a pass.

```bash
unzip -p "$OUT"/<preserved-data>.zip data.json > /tmp/before.json
unzip -p "$OUT"/<servicetag-data>.zip data.json > /tmp/after.json
diff <(jq -S . /tmp/before.json) <(jq -S . /tmp/after.json)   # expect empty, or only C.8 fields
```

Then the per-table assertions. **Compare, for every one of the eleven tables, the id set and every
relationship field** (arch §7.1, §7.4, §7.6, with the event rows corrected per review correction 17):

| Table / DTO list | Ids | Relationship and identity fields to compare |
|---|---|---|
| `asset` / `assets[]` | `id` set, exactly | `parentAssetId` (nullable, self-referencing) — and rebuild the tree from ids and diff it against the pre-restore tree; the importer's `AssetTree.parentsFirst` re-validates no-cycle on the way in. Plus every metadata field O10/§13 names: manufacturer, model, serial, purchase and in-service dates, price + currency, vendor, location, warranty, **retirement**, **season window**, status, category |
| `nfc_tag` / `nfcTags[]` | `id` set | `assetId` **xor** `linkId`, `payloadFormat`, `payloadKey`, `status`, `physicalUid`. **Empty in this dataset** (`nfcTags = 0`) — asserted empty, and the assertion stays for whenever tags exist |
| `external_link` / `externalLinks[]` | `id` set | `assetId` (nullable), `kind`, `uri`. **The row most at risk**: a single `JOPLIN` link with `assetId = NULL` — unowned, referenced by nothing, therefore the one most easily lost (arch §7.5) |
| `measurement_definition` / `measurementDefinitions[]` | `id` set | `assetId`, `kind`, and for `DERIVED` rows `formula`, `sourceAId`, `sourceBId` (self-referencing, RESTRICT) |
| `event_profile` / `eventProfiles[]` | `id` set | `assetId` |
| `profile_field` / `eventProfiles[].fields[]` | nested `id` set | `definitionId`, and the `(profileId, definitionId)` uniqueness |
| `profile_consumable` / `eventProfiles[].consumables[]` | nested `id` set | parent `profileId` (implicit in nesting), values |
| `asset_event` / `assetEvents[]` | `id` set | **per-field equality, not just ids** (review correction 17): `assetId`, `profileId` (nullable), **`occurredOn`**, **`tzId`**, **`createdAt`**, and the **`(source, source_ref)`** pair with its uniqueness constraint. A timestamp or zone that shifts is a migration failure, not a cosmetic difference |
| `measurement` / `assetEvents[].measurements[]` | nested `id` set | `definitionId` (RESTRICT), parent `eventId` (implicit), value and unit |
| `consumable_usage` / `assetEvents[].consumables[]` | nested `id` set | parent `eventId` (implicit), amount and unit |
| `attachment` / `attachments[]` | `id` set | owner = **exactly one of** `assetId` / `eventId` (XOR, enforced in `toDomain()` and by `validateGraph()`), plus `sha256`, `sizeBytes`, `storageLocator`, `mode`, and the `(storageProvider, storageLocator)` uniqueness |

**The attachment byte proof — three independent hashes per row, eight rows** (§24). For each
attachment, assert:

1. `AttachmentDto.sha256` in the restored export **==** the same field in the preserved export;
2. **==** the artifacts manifest's per-entry `sha256` for that `attachmentId`;
3. **==** `sha256sum` of the **original file** — read back at its locator after the restore, and
   independently out of the preserved artifacts archive entry `artifacts/<attachment-id>.<ext>`, whose
   extension is taken from the *locator* so entry name and locator can never disagree (arch §7.2).

Also assert `sizeBytes` on all three sides and `artifactCount` == the attachment-row count (8 = 8).
This is the same check `RestoreArtifacts` performs twice at runtime; doing it a third time from the
workstation is what makes it *evidence* rather than a claim.

**Verify (the gate).** Eleven tables with identical id sets; every relationship and per-field
comparison equal; 8×3 hashes equal; `5.json`'s `identityHash` unchanged and matching the new package's
live `room_master_table.identity_hash`; `PRAGMA user_version` = 5 (arch §7.1). Record the whole result
as a table in `docs/architecture/product-split-evidence.md` (§29).

**Rollback.** On any failure: **do not uninstall anything.** The old package still holds the live data.
Uninstall ServiceTag, fix, reinstall, restore again — replace-mode restore is idempotent from the same
archive.

### C.7 The empty-tree restore, proved independently (§13)

§13 is explicit: same-tree discovery is *an optimisation, not the only proof*; the set **must
independently prove portability to an EMPTY attachment tree**. The same-tree case proves idempotence;
it does not prove the restore can write bytes.

**Do.** Install ServiceTag on the emulator, point its store at an empty folder, and restore data then
artifacts from the same two archives. (If C.3's grant was refused on the phone, this is also the
phone's path — into a new, empty folder, never the live one.)

**Verify.** **8 written, 0 alreadyPresent, 0 skipped**; every file lands at its store-relative locator
under the new root, proving locators are store- and package-agnostic by construction (arch §7.4); all
8×3 hashes equal; the same eleven-table comparison passes. Note that `documentIn()` falls back from an
exact name match to "the first child whose name starts with `<id>.`", because some SAF providers
append or normalise extensions — a restored file whose extension differs is a pass, provided its hash
matches. **Rollback.** Wipe the emulator; the phone was not touched.

### C.8 Fields that legitimately differ (§24)

A difference in any of these is **not** a migration failure. Anything *not* listed must be identical,
because every remaining field is domain data (arch §7.6).

| Field | Why it differs |
|---|---|
| `manifest.backupSetId` | minted fresh per export — a pairing token between one data archive and one artifacts archive, not a stable identity |
| `manifest.createdAt` | the new export's timestamp |
| `manifest.appVersion` | `BuildConfig.VERSION_NAME`; carries no package identity, and ServiceTag's version was bumped |
| Export file names (`noteNFC-data-*` → `ServiceTag-data-*`) | cosmetic; the importer never reads a file name |
| The Room database **file** and its name (`notenfc.db` → `servicetag.db`) | a fresh package gets a fresh, empty database populated by the *data* restore, never a copied file (§13: no private-database copy migration) |
| Schema export directory (`<old FQN>/` → `<new FQN>/`) | a build artefact derived from the `AppDatabase` FQN, not runtime state |
| `attachment_tree_uri` pref | **must** be re-chosen: a persisted SAF grant is scoped to the calling application and cannot cross a package boundary, even for the same signing key |
| `last_backup_at` pref | device-local, never in a backup |
| `last_restored_backup_set_id` pref | absent before (no import had ever run), set after |
| `appearance_mode` pref | never explicitly set, so it falls back to its `SYSTEM` default and is never written |
| Thumbnail cache (`<cacheDir>/thumbs/…`) | purely derived and regenerable, in a directory that "may vanish at any time" |
| `shared_prefs/storage_spike.xml` | one `tree_uri` key outside the `AppPrefs` contract — residue from spike S5; device-local, not preserved (arch §7.5). Clearing it is optional |
| SAF `displayName` / `authority` in `StoreState` | derived from the newly granted tree |
| `external_link.lastOpenedAt` | changes if the link is launched during the proof; compare it before launching anything |
| `nfc_tag.lastScannedAt` / `writtenAt` / `physicalUid` | a read scan performs a write (arch §5.9), so any tag row touched during §D or §E moves. Not applicable before §D: `nfcTags = 0` |
| Launcher icon, label, theme, package-derived authorities | the deliberate identity changes of §11 |

### C.9 (§15 step 7) Uninstall the old package — only now

**Do.** Only after C.6 passes and its evidence table is written: uninstall `com.loosecannon.notenfc`.

**Verify.** ServiceTag still shows all five assets, 26 journal events and 8 attachments with
thumbnails, and the attachment files still open in the system viewer — the SAF tree belongs to the
folder, not to the uninstalled app, and ServiceTag holds its own grant. **Rollback.** Reinstall the
preserved signed 2.4 / vc6 APK from `~/Documents/Projects/AndroidStudioProjects/noteNFC-releases/` and
import the preserved set into it. **This is the only genuinely irreversible step in the runbook**,
which is why C.6's proof is its precondition (§35).

### C.10 (§15 step 8) Install NoteTag

**Do.** Build and install NoteTag's debug APK. Nothing reuses `com.loosecannon.notenfc`, so there is
no collision and no ordering hazard (O1) — unlike a design that kept the old identity, this step has
no precondition beyond C.9.

**Verify.** Both `com.loosecannon.servicetag` and `com.loosecannon.notetag` are installed with
**distinct icons and labels**; each launches; neither sees the other's data. **Rollback.** Uninstall
NoteTag; ServiceTag is unaffected.

---

## D. Physical tags — Session 1: writer mechanics (sequence I)

Tag *migration* is withdrawn (O2; §34 gate 8 folded into gate 9; §5's "physical-tag proof — now:
coexistence of final products only"). What remains on the phone is the irreducible minimum: the
mechanics that need a real radio and a real chip. **Everything that can be proved without RF has
been moved off the phone** — see §D.3.

### D.1 Physical tags required — three, plus two optional

| # | Tag | Prepared how | Used for |
|---|---|---|---|
| **T1** | blank, **unformatted** NTAG213 (`NdefFormatable`, not yet `Ndef`) | out of the packet | the format path **and** lock-last, in one tag: tap 1 formats and writes unlocked, tap 2 verifies the read-back and then locks. It ends its life read-only, which is why it is this tag and not a shared one |
| **T4** | **NoteTag `JOPLIN_NOTE`** canonical tag | written in Session 1 | the canonical write proof, then Session 2's ambient tap and ServiceTag's sibling-refusal read |
| **T2** | **ServiceTag asset** canonical tag | written in Session 1 | the canonical write proof, then Session 2's ambient tap and NoteTag's sibling-refusal read |
| *(opt)* | a commercial URL sticker | any retail sticker | **optional observation, not a gate**: the Android 16+ `ACTION_VIEW` / Android 17 "open link" notification behaviour for a genuine web-link tag |
| *(opt)* | a tag with an unrelated external type | a generic third-party NFC writer | **optional observation, not a gate**: the `Foreign` branch with a type string that is neither product's |

All NTAG213 or larger: NTAG213 is the minimum supported tag (O14), and both products' messages fit it
with room to spare — ServiceTag's ~92 B *with* its AAR, NoteTag's `JOPLIN_NOTE` ~52 B without one,
both as **NDEF message sizes** compared directly against what `Ndef.getMaxSize()` reports (target
§4.3 invariant 7).

### D.2 Session 1 — four owner taps

Each row is one deliberate tap in an explicitly-opened writer screen (§25: "intentional writes only
via explicit writer UI"). Installs, force-stops, `logcat` and `dumpsys` are workstation-driven.

| # | Owner action | Pass condition |
|---|---|---|
| **1** | **T1**, first tap, NoteTag's writer with the lock armed | the `NdefFormatable` path: the tag is formatted and written **unlocked**, and the screen says to lift it off and hold it again. `maxSize` was `-1` before the format, so no capacity verdict was possible yet (target §4.3 invariant 7) |
| **2** | **T1**, second tap | the read-back is compared structurally — record count, order, TNF, full type, full payload — and **only then** is the lock applied. The tag is thereafter permanently read-only, and a third tap of it in any writer reports `ReadOnly`, not `TooSmall` and not a generic failure. **This single tag exercises the whole format → verify → lock-last sequence**, which is why there is no separate lock tag |
| **3** | **T4**, one tap, NoteTag's writer | a canonical `JOPLIN_NOTE` tag: **one external record, no AAR** (O13); read-back verified; `needed` was the exact encoded message size |
| **4** | **T2**, one tap, ServiceTag's writer | a canonical ServiceTag asset tag: read-back verified; the `nfc_tag` row's `writtenAt` and `physicalUid` stamped |

**Four owner taps.** Nothing else in this session requires the owner.

### D.3 What moved off the phone, and where it went

Each of these was previously a physical row and is now cheaper, faster and more repeatable elsewhere.
The operating rule drove every reassignment: *vet everything possible on the emulator; the phone only
for RF hardware or the real install.*

| Former physical row | Now proved | How |
|---|---|---|
| URI-vs-`LOCAL_REF` **capacity selection** | **JVM tests with fake capacities** | drive the writer's decision with injected `maxSize` values around the boundary: a URI whose exact encoded message is `maxSize`, `maxSize - 1` and `maxSize + 1`. Asserts the order compact → URI-if-it-fits → `LOCAL_REF`, and that the fallback happens **only** when the message genuinely does not fit — the failure mode G1's arithmetic correction exists to prevent |
| `LOCAL_REF` **missing-map** behaviour | **JVM / app test** | resolve a `LOCAL_REF` body with the local store empty or the entry removed: a clear message, no crash, no silent nothing |
| `LOCAL_REF` **crash consistency** | **JVM failure-injection test** (§A.2 task 7, G2) | persist-succeeds-write-fails → the orphan mapping is removed; persist-fails → **no tag write is attempted** |
| ServiceTag **standalone-link resolution** | **emulator regression with a synthetic NFC intent** | deliver `ACTION_NDEF_DISCOVERED` to the same dispatch activity with the same external type as the asset tag, carrying a link-target payload; assert the link launches with no app screen and `last_opened_at` is stamped. The wire shape is identical to the asset tag, so a physical link tag would prove nothing the asset tag has not already proved on-device |
| **Commercial URL sticker** | **optional observation** | logged if the owner happens to have one to hand; not a gate |
| **App-not-installed tap** | **optional observation** | the AAR-less dispatch question; logged opportunistically, not a gate |
| Separate **lock** test (the old spare tag) | **folded into T1**, taps 1–2 | one tag now carries format, verify and lock |
| Separate **foreign-tag** write test | **folded into the sibling reads**, Session 2 checks 3–4 | a sibling's tag is a foreign record as far as the writer's overwrite decision is concerned, so the same confirmation path is exercised with a tag that also proves the coexistence row |
| The **AAR dispatch spike** (O13) | **folded into Session 2's ambient taps** | the cold ambient tap of each product's tag *is* the spike: it answers whether external-type-only dispatch reaches an installed app. Warm, from-recents and screen-off states are **optional observations logged from the workstation** via `logcat`/`dumpsys` while the owner is already tapping — never separate owner actions |

### D.4 How each observation is recorded

One row per observation in `docs/architecture/product-split-evidence.md` (§29), in the same shape as
the existing `docs/design/phase-*-evidence.md` files, so it is comparable to the 1B/1C rows this
design leans on. Schema: `id | question | precondition (which apps installed, which build, which tag)
| action | expected (with its claim tag) | observed | verdict | date | build`.

The operator captures `adb logcat` and the relevant `dumpsys` output for the whole session, writes the
observed column verbatim including surprises, and marks PASS / FAIL / INCONCLUSIVE. **An INCONCLUSIVE
row stays INCONCLUSIVE** — 1B row 13 is the precedent (arch §5.10). A claim tag in the target document
moves from **[unobserved]** to **[device-observed]** only for rows that passed, and an observation
that contradicts a **[platform-doc]** expectation is recorded as a contradiction in both documents,
exactly as arch §5.10 does for stopped-state dispatch. §29 forbids secrets and device ids in the
evidence file; certificate fingerprints only.

**Rollback (this session).** Any tag can be rewritten **except T1 once locked**, which is the point of
locking it and the reason no other tag is locked. No app data changes beyond the rows the writers
create, and a cancelled write leaves no row behind (`abandonIfUnwritten`). The eight legacy field tags
are untouched — §D.5.

### D.5 The eight legacy tags — inventory facts only

Eight physical `md5_short` tags exist on the owner's equipment, one of them targeting a standalone
note rather than an asset; the census is `docs/design/g1/00-source-data-inventory.md` (review
correction 3, addition 2). **The historical package is not installed. Its old private lookup map is
not part of any supported migration path and is intentionally abandoned.** (Evidence: an
attached-phone `pm list packages` listing showed only the lower-case current package; the exact
`pm path` check is scheduled for the next time the phone is attached, so nothing here claims the old
data is proven gone.)

Their lifecycle is not a migration: an old tag on a machine is currently useless; when convenient, the
owner opens that machine in ServiceTag and writes the canonical ServiceTag tag; done. The normal
writer's read-before-write step already presents a foreign or unreadable record as a one-confirmation
overwrite, offering **Write over it / Cancel**. **No wizard, no resolver, no compatibility mode, no
acceptance row, no migration code** (O2, O11).

---

## E. Session 2 — coexistence acceptance (sequence J, §25 as revised by O9)

Preconditions: both apps installed (C.2 and C.10), both on the **same** library tag, Session 1's three
tags written, `adb logcat` capturing from the workstation throughout.

| # | Check (§25 row) | Owner action | Pass condition |
|---|---|---|---|
| **1** | NoteTag canonical tag → NoteTag; **and the AAR dispatch spike** | **cold** ambient tap of **T4** (NoteTag not running, tapped from the lock screen or home screen) | NoteTag resolves and the correct note opens in Joplin through a safe `ACTION_VIEW`. **No chooser.** No ServiceTag window. This tap *is* the spike: it answers whether an AAR-less external-type-only tag reliably reaches an installed app (O13). Warm, from-recents and screen-off repeats are optional workstation-logged observations, not owner actions |
| **2** | ServiceTag asset tag → ServiceTag | ambient tap of **T2** | ServiceTag opens that asset. No chooser. No NoteTag window. Same spike question for ServiceTag, which still carries its AAR (ratified P21) — so this tap is also what the spike compares against |
| **3** | NoteTag ReaderMode sees a ServiceTag tag → foreign/protected | with **NoteTag's writer** open, read **T2**, then **Cancel** | the tag is **named** as another app's — the reason carries the full type string from `Foreign.description` — and exactly two actions are offered, **Write over it** and **Cancel**. **Cancel** leaves the tag byte-identical; no NoteTag record is created; no cross-product action of any kind is offered (ratified P11). This also discharges the old separate foreign-record write test |
| **4** | ServiceTag ReaderMode sees a NoteTag tag → foreign/protected | with **ServiceTag's Read / inspect tag** open, read **T4** | named as another app's tag; **Write over it / Cancel** only; no row created, no lookup performed, no transaction opened (arch §5.12) |
| **5** | ambient read never enters write mode | *no extra tap* — observed on checks 1 and 2 | no writer UI, no reader mode, no `Tag` handle obtained on the ambient path. Structurally guaranteed: the trampoline never calls `nfcTag()` (arch §5.10) |
| **6** | intentional writes only via explicit writer UI | *no extra tap* — established by Session 1 | every write in §D.2 came from a writer screen the owner opened |
| **7** | no unpredictable chooser from overlapping identity | *no extra tap* — observed on checks 1–4 | zero chooser dialogs in the logs across all four |
| **8** | unrelated/foreign tag → no unsafe action | *no owner action required* — the sibling reads in checks 3–4 already exercise the foreign classification path, and the commercial-sticker and unrelated-external-type taps are **optional observations** (§D.1, §D.3) | in checks 3–4: no database write in either app, no lookup, no transaction. If an optional sticker tap is run, a platform notification for a genuine web-link tag is a pass, not a failure (**[platform-doc]**: `ACTION_VIEW` from Android 16, an "open link" notification from Android 17) |

**Four owner scans**, plus **at most two first-use NFC permission confirmations** (one per app, on
whichever tap Android first shows them) and **at most one retap** if Android consumes an initial scan
while a permission dialog is up.

### E.1 The owner-action count

| Session | Deliberate taps | Incidental |
|---|---|---|
| **1 — writer mechanics** (§D.2) | **4** (T1 ×2, T4, T2) | — |
| **2 — coexistence** (§E) | **4** (T4 ambient, T2 ambient, T2 in NoteTag's writer, T4 in ServiceTag's inspector) | up to 2 first-use NFC permission confirmations; up to 1 retap |

**Total: 8 deliberate tag taps across two sessions, and at most 11 owner interventions worst case —
8 if the permission dialogs do not appear.** Everything else is workstation-driven: installs,
uninstalls, force-stops, `dumpsys`, `logcat`, exports, and every optional observation.

**Verify (the gate).** Eight checks recorded across §E, of which four cost a tap; from the logs, with
no owner action: zero unexpected activity starts, zero chooser dialogs on checks 1–4, and no database
write in either app on checks 3–4. §25's closing instruction governs: **observe actual Android
behaviour; do not infer from manifests.**

**Rollback.** Uninstall either app; neither holds the other's data or grant. A failure on checks 1–2
means a filter is wrong and is fixed in source, not on the phone. A failure on 3–5 is the serious
one: the type gate or the ambient/write separation is broken, which stops the split and sends the
library back to §A.3.

---

## F. Issue and backlog partition (§10, O8, C7)

From the classification in arch §4.7 — **36 issues, 35 open / 1 closed, no labels on any of them** —
done by reading each title and, for the eleven ambiguous ones, the full body, because most ServiceTag
issues still say "noteNFC". §10's rule: **use source semantics, not keywords.**

| Disposition | Issues | Action |
|---|---|---|
| **Moves to NoteTag** (2) | **#6** "Generalize external note/deep-link support beyond Joplin"; **#36** "First-class deep-link support for Joplin, Obsidian, Logseq, Evernote, Notion, OneNote and Todoist" — explicit future-narrow-product work (§10, O8) | transfer per §B.5, **retitled under the NoteTag name**, with backlinks both ways. If transfer is unavailable, recreate with the verbatim body and close the original marked moved |
| **Stays in ServiceTag — ServiceTag proper** (27) | #2–#5, #7–#18, **#19**, **#20**, #21, #22, #24–#28, #33, #34 | nothing moves. **#20** (Apollo Service Binder design system) is **ServiceTag's, not shared** (C7). **#19** ("`notenfc://` deep-link contract") is entirely about `notenfc://asset|link|tag`, i.e. ServiceTag domain objects despite the legacy scheme name — its scheme literal is retitled to `servicetag://` inside the issue, not by transfer. **#34** ("Todoist deep-link actions") says noteNFC in the title but its body is about landing on a maintenance operation. §10 forbids moving Todoist/asset-link/maintenance/schedule/attachment work merely because it mentions links |
| **Stays in ServiceTag — genuinely dual-purpose NFC** (4) | **#1** "[EPIC] Evolve noteNFC into an NFC-first maintenance tracker"; **#30** "Bind, rebind, revoke, and unknown-tag flows"; **#31** "tag payload format v1 and legacy md5_short resolver"; **#35** "Untrusted input policy: tag payloads, deep links, stored URIs" | stay, each with **a note**. **"Shared" does not mean "move to nfc-tag-core"** (C7): a mixed policy/mechanism issue is split or stays in ServiceTag as history. #31 additionally records that the `md5_short` half is **withdrawn, not moved** (O2), and that this reverses D6's "kept permanently" promise |
| **Stays in ServiceTag — build/test infra** (2) | **#23** "Phase 0: clone-buildable repo, AGP 9 toolchain, `:core` module, CI"; **#32** "Testing pyramid and CI gates" | stay. #23 gets a note that NoteTag inherited its *outcome* by branching `c84b881` but **must re-run CI from scratch** (review correction 8); #32 a note that the gates now exist in three repositories |
| **Closed, with a pointer** (1) | **#29** "[MVP] Investigate the installed APK's signing certificate and upgrade path" | stays closed, in ServiceTag, with a comment pointing at target §8. Not reopened, not transferred: it is why a new identity had to be minted at all |
| **New, in NoteTag** | — | one issue for the `LOCAL_REF` export/import of the local map (O14, explicitly not split scope). The `notetag://` `VIEW` filter is **not** a separate issue: ratified P4 reserves the scheme and defers any filter to #6/#36, where it belongs. The UI toolkit is **not** an issue either: ratified P20 settles it (Compose, one activity, two tiny screens) |
| **New, in nfc-tag-core** | — | one issue for `TagWriteSession`'s promotion criterion (target §4.7), so the deferral is tracked rather than forgotten; one for any residue of the `NdefFormatable` capacity gap if ratified P8 — as corrected by G1's message-size arithmetic — does not fully close it |

**Verify.** `gh issue list --repo GonzRon/ServiceTag --state all` = 34;
`gh issue list --repo GonzRon/NoteTag --state all` = 2 transferred + the new ones; every
"stays with a note" issue has its note. **Rollback.** Transfers reverse (§B.5); notes and new issues
are additive.

---

## G. Releases and tags (§28)

§28: inspect, do not mechanically copy or delete; ServiceTag keeps the complete modern history;
old-name tags may remain as history; NoteTag gets historically meaningful narrow-product tags **where
cleanly reconstructable**; never claim a maintenance release was a narrow release; document ambiguous
ownership.

| | Disposition |
|---|---|
| **The 2023 draft release** | **Stays in ServiceTag as history. Not published, not deleted, not re-anchored.** It is: name `initial working`, `draft: true`, `prerelease: true`, `tag_name: ""` (**empty — never tagged**; GitHub minted only a synthetic placeholder for its `html_url`), `target_commitish: "master"`, `published_at: null`, no assets. The commit the repository stood at two minutes before it was created is `707ca3f` "adding a debug apk" — an **Evernote-era** commit (package `com.looseCannon.evernotenfc`, key `UUID.randomUUID()…substring(0, 8)`, record type `com.loosecannon.evernotenfc:uuid8_link`), predating MD5 keying, the `md5_short` type, the project rename and the Joplin conversion by over a year (arch §2.10). **Because it has no tag and targets the branch ref rather than a SHA, it is unanchored — publishing it today would tag whatever master then is.** This paragraph is the documented ambiguous ownership §28 asks for: it is neither a ServiceTag release nor a NoteTag release, and it is left exactly as it is |
| **`pre-split-checkpoint`** | stays in ServiceTag at `ac523d7`; a ServiceTag-state marker, not a narrow-product marker (arch §4.8). Not deleted (§27 step 12) |
| **`pre-split-master`** (branch) | stays in ServiceTag at `ac523d7`. Recorded because the name reads like a tag and is not — `git tag` lists only `pre-split-checkpoint` (arch §4.1) |
| **ServiceTag's first tag** | **`servicetag-v2.5`** on the commit that passes gate 9, annotated with the split's completion date, the library tag both apps consume, and the certificate SHA-256 of the ServiceTag key. Product-prefixed because the repository's history contains a differently-named product and a bare `v2.5` would be ambiguous across the rename (ratified P17) |
| **NoteTag's first tag** | **`notetag-v1.0`** on the commit that passes gate 9, annotated with the boundary commit `c84b881` it descends from and the library tag it consumes. **This is the first tag this lineage has ever had** — there is no 2023 or 2024 tag, locally or on origin (arch §4.1). No earlier narrow-product tag is "cleanly reconstructable", so none is invented |
| **nfc-tag-core's first tag** | **`nfc-tag-core-v0.1.0`**, created in §B.1, annotated with the source commit `ac523d7` and a pointer to the provenance table |
| **Releases on the new repositories** | none at creation. Nothing is published (O15), and the two apps are locally signed, so a GitHub release would carry an APK the owner does not want distributed |

**Rollback.** Tags are deletable locally and on the remote and carry no data. The draft release is
never touched.

---

## H. Documentation plan (§29, §30)

### H.1 Untouched — history

§30: **never rewrite the Phase 0–4A documents as if they said ServiceTag.** Everything under
`docs/design/` and `docs/superpowers/` stays: `docs/design/README.md`, D1–D13, the eight
`phase-*-evidence.md` files, the `g1/` pair (including the eight-tag census this design cites), the
two `spikes/` reports and the `issues/` package (arch §4.9; the new-issue drafts are **19**, not 17 —
review correction 12). Rewriting a phase-evidence file would destroy the evidence this split is built
on: the 1B and 1C device rows are cited a dozen times across these two documents. Where a historical
document is now out of date, the correction goes in a *new* document with a pointer, exactly as D13
corrected D6.

`docs/architecture/product-split-archaeology.md` is likewise final once its C1/C2/C6/C7 and review
corrections land.

### H.2 Changed — the canonical surfaces (§29)

| Surface | Change |
|---|---|
| **`README.md`** (ServiceTag) | currently opens `# noteNFC` and describes the **merged** scope (arch §4.9). Rewritten for ServiceTag only: title, what it is, "What it does today" with the note-utility framing removed **and schedules/reminders stated as Phase 3 future work** (C1), the identity block (`applicationId`, NDEF type, `servicetag://`), **Building** gaining `--recurse-submodules` and the submodule/subproject mechanism (O15), **Signing** pointing at `~/.config/servicetag/` and **replacing the reproduced signer DN and full SHA-256 at lines 121-125 with a pointer** (review correction 11), and the "Cutover from the old package" section replaced by a pointer to this runbook. A "Related projects" block links NoteTag and nfc-tag-core |
| **`README.md`** (NoteTag, new) | written fresh: what the product is (attach a note or a useful link to a physical NFC tag) and is not; the v1 tag format and its three kinds; that `LOCAL_REF` tags are device-bound; its true ancestry from 2023 with `c84b881` named as the semantic endpoint; that the inherited ServiceTag design package was deleted at the branch tip and **is still in history, deliberately, with no second rewrite** (C6); `--recurse-submodules`; signing from `~/.config/notenfc/` with the note that the directory keeps its historical name because the key must not be touched (§12) |
| **`README.md`** (nfc-tag-core, new) | the provenance table (target §4.6), the invariant list (§4.3), the public API, the forbidden-knowledge rule and its scan, the two patterns the library does **not** own, and the explicit statement that `TagWriteSession` is deferred with its promotion criterion (§4.7) |
| **`docs/design/README.md`** (ServiceTag) | one **new paragraph at the top**, no table rows edited: this package is the ServiceTag line; the split happened on <date>; the narrow product now lives at `GonzRon/NoteTag` and the shared mechanism at `GonzRon/nfc-tag-core`; every document below is historical and its "noteNFC" means "this app before the rename". Its terminology note — which defines "noteNFC tag payload format v1" as record type `com.loosecannon.notenfc:tag` — gains a sentence saying the type is now `com.loosecannon.servicetag:tag` and that the old string belongs to nobody |
| **D3 (`03-target-architecture.md`)** | append a short **"After the product split"** section: the NFC mechanism moved to `nfc-tag-core`; D3 §9's rules are now library invariants, with the mapping; the `md5_short` filter is **withdrawn** (a deliberate reversal of D6, recorded as such); `DISPATCH_NFC_MESSAGE` at targetSdk 37 restated against the new identity. The existing §9 text stays, because the 1B/1C evidence rows reference it by section number |
| **D6 (`06-legacy-compatibility.md`)** | append one note: its still-live promise that the `md5_short` filter is "kept permanently" is **withdrawn by O2/O3**; the eight field tags are inventory facts whose lifecycle is a normal ServiceTag write (§D.5). The original text is not edited |
| **D7 (`07-implementation-sequence.md`)** | the "Product separation — deferred convergence operation" section is the one place in the historical corpus describing this operation prospectively. It gains a closing block — *executed on <date>; see `docs/architecture/product-split-{archaeology,target,migration,evidence}.md`* — and a tick against its own eight conditions: archaeology before mutation ✓, durable checkpoint and rollback ✓, remote rename only after local proof ✓, both apps installable together with the dispatch proof ✓ (§E), identity-preserving data migration through the canonical backup format ✓ (§C.6), explicit-user-intent tag writes ✓ (§D), distinct ServiceTag key with the original preserved ✓ (target §8), independent green CI for all three ✓ (target §9). Its roadmap lines gain "Phase 3 begins after the split completes" and the §31 order. **Nothing else in D7 is edited** |
| **D13 (`13-compatibility-policy.md`)** | one appended note: its anticipated chooser window ("Both apps match `md5_short`; Android shows a chooser until the old app is removed") **does not arise**, because no product declares that type at all |
| **This package** | at completion, both documents gain the observed values for every "to observe on-device" marker (or the recorded contradiction), the ratified status of every **[P*n*]**, and the three first tags from §G |
| **`docs/architecture/product-split-evidence.md`** | **created** (§29): commits, CI results, builds, clean-clone proof, the id comparison, the attachment hashes, the device results from §D and §E, and certificate fingerprints only — **no secrets, no device ids** |
| **`.superpowers/split/ledger.md`** | one line per completed phase, as for A and B |

**Verify.** `git diff --stat` over `docs/design/` and `docs/superpowers/` shows **additions only**, and
only in the five files named. Every cross-reference in the new READMEs resolves. A reader who opens
ServiceTag's `README.md` cannot mistake it for the note utility, and a reader who opens `docs/design/`
is told in the first paragraph what era they are in — §30's stated purpose, so that future maintenance
work points to ServiceTag.

---

## I. Rollback, end to end (§35)

Read from the row you are in, upward: the earlier the failure, the cheaper it is.

| Failed in | What has changed | Recovery |
|---|---|---|
| **§A.1** ServiceTag conversion | commits on `product-split` in a worktree | `git reset --hard ac523d7`, or revert the task commit. `master`, `origin` and both recovery refs untouched |
| **§A.2** NoteTag reconstruction | one local directory | delete it and re-clone from `c84b881`, which is immutable history |
| **§A.3** library extraction | one local directory | delete it; nothing references it yet |
| **§A.4** both consume | submodule + settings commits in two local repositories | `git submodule deinit -f libs/nfc-tag-core && git rm -f libs/nfc-tag-core`, revert the commits. Each app's own NFC layer is still in its history |
| **§B.1** library repo created | a new remote repository exists | the token **cannot delete**: make it private, rename it aside, record the abandoned name |
| **§B.2** rename | `GonzRon/noteNFC` is now `GonzRon/ServiceTag` | `gh repo rename noteNFC` — reversible both ways, with redirects either way. Reset local remotes. **The recovery refs stay throughout and are the anchor for everything below** |
| **§B.4** NoteTag pushed | a second new remote repository exists | rename it aside, fix locally, re-push |
| **§B.5** issue transfer | two issues moved | `gh issue transfer` back; comment history and redirects survive, and the backlinks become the audit trail |
| **§C.1–C.8** phone restore and proof | **the old package is still installed and still holds the live data** — the whole point of §15's ordering | uninstall the ServiceTag package and keep using the modern app. The SAF tree is untouched in the same-tree case (8 `alreadyPresent`, 0 written), and §13 forbids ever deleting, relocating or rewriting it |
| **§C.9** old package uninstalled | the old app's data is gone | reinstall the preserved signed **2.4 / versionCode 6** APK from `~/Documents/Projects/AndroidStudioProjects/noteNFC-releases/` — built from `ac523d7`, signer DN `CN=noteNFC, O=GonzRon`, certificate SHA-256 in the checkpoint ledger — re-grant its SAF tree, and import the preserved set. **The only genuinely irreversible step in the runbook**, which is why C.6's proof is its precondition (§35) |
| **§C.10 / §D / §E** NoteTag installed, tags written, coexistence | tags written; two apps installed | uninstall either app freely. Any tag can be rewritten **except T1 once locked** — T1 is the only tag ever locked, which is precisely why the format → verify → lock-last sequence is exercised on it and on nothing else (§D.2). **T4** and **T2** stay rewritable. The eight legacy field tags were never touched |
| **The whole split** | everything above | `git checkout pre-split-checkpoint` in a fresh clone of the renamed repository; rename the repository back to `noteNFC`; transfer the two issues back; rename the two new repositories aside; reinstall the 2.4 APK and import the preserved set. The pre-rewrite history bundle beside the project directories covers the one case none of this does — a corrupted or force-pushed history — because it predates the 2026-09-14 rewrite |

**Two rules that make all of it hold.** No recovery ref is ever deleted (§27 step 12, §35), and no
step is designed to require a repository delete — which the active token's capabilities enforce
anyway (arch §4.3).

---

## J. Reviews — the ten gates of §34

§34's list, quoted rather than inferred. Gate 8 (physical-tag migration) is **withdrawn and folded
into gate 9** (O2, §5). §34's standing rule: *important compatibility findings are fixed before
proceeding; minor cleanup is recorded and deferred.*

| Gate | §34 | Phase(s) | Entry condition |
|---|---|---|---|
| **1** | 4A final | — *(done)* | Phase 4A closed, CI green, the checkpoint taken |
| **2** | archaeology | B *(done — PASS with corrections)* | the archaeology document, its §9 discrepancy log, and the 19 corrections + 8 additions from the independent review |
| **3** | **split architecture before mutation** | C *(this package)* | target + migration documents revised under O1–O15 and C1–C9; every named module/package/file exists or is marked NEW; every unobserved platform claim marked; proposals separated from rulings. **Mutation stays on HOLD until this passes** |
| **4** | **nfc-tag-core boundary** | F, G | the forbidden-dependency scan green with a reasoned allow file; every library invariant tested; the provenance table complete with resolving `git log --follow` starting points; **what is deferred and why stated explicitly** (target §4.7) — a boundary claiming more than two consumers have proven is what this gate exists to catch |
| **5** | **ServiceTag identity / regression** | D | §A.1's fourteen tasks complete; §A.1.1's §21 regression pass green; the identity table true of the **built APK**; `5.json`'s `identityHash` unchanged; clean-checkout build green |
| **6** | **NoteTag narrow scope** | E | §23's acceptance list end to end; the v1 format's per-kind tests; the local store never required to resolve `JOPLIN_NOTE`/`URI`; true ancestry (30 commits, zero merges, root `5fb6aed`); CI green **from scratch** (review correction 8); no `docs/`, no tracked APK, no legacy decoder, no tech catch-all |
| **7** | **data / artifact migration** | H | §C.6 and §C.7 pass: eleven tables with identical id sets and per-field equality (events including `tzId`, `occurredOn`, `createdAt`, `(source, source_ref)`); 8×3 attachment hashes; the empty-tree restore proved independently; every difference accounted for by §C.8 and nothing else. **This gate precedes §C.9's irreversible uninstall** |
| **8** | *withdrawn* | — | folded into gate 9 (O2; §5's "physical-tag proof — now: coexistence of final products only") |
| **9** | **final coexistence / device** | I + J | Session 1's four writer taps recorded (§D.2, including format → verify → lock-last on one tag); Session 2's eight checks passed (§E), of which four cost a tap, with the **AAR dispatch spike folded into checks 1–2**; **owner interventions held to 8 deliberate taps and at most 11 in total** (§E.1); zero chooser dialogs on checks 1–4; no database write on checks 3–4; sibling refusals offering **Write over it / Cancel** and nothing else; every reassigned proof (§D.3) green off-device; both apps on the same library tag; every observation written to the evidence file with a verdict |
| **10** | **final three-repository convergence** | K + L + M + N | three repositories, three green CI runs, three clean-clone builds (one per app from a second workstation); issues moved with backlinks and the notes added; the three first tags created; documentation changed only where §H says and additively where it touches history; every **[P*n*]** ratified or superseded; every "to observe on-device" marker resolved or explicitly deferred; and the §36 handoff assembled — checkpoint SHA, the historical split SHA and why, three repo names/URLs/canonical commits, what the original repository became, what moved into nfc-tag-core, what stayed app-specific, the dependency/version mechanism, both applicationIds and namespaces, NFC record and deep-link ownership, AAR behaviour, signing fingerprints only, the migration backup format, the ID-preservation and attachment-hash proofs, the SAF grant procedure, the coexistence proof, CI status ×3, issue movements, releases/tags, rollback, and remaining debt. **Next operation after handoff: ServiceTag Phase 3** |

**No gate is self-certified**; each is reviewed against the artifact it names, by someone who did not
produce it — the pattern already used for gates 1–3.

---

## K. Proposal ledger — all ratified, none open

Gate 3 ratified the last five proposals, and each now lives in the design text rather than in a list:
**P4** (no `notetag://` `VIEW` filter at reconstruction), **P11** (a sibling's tag is named and offers
exactly **Write over it / Cancel**, normalised throughout both documents — no "keep" wording and no
cross-product action anywhere), **P19** (the local store as a single atomically-replaced JSON file)
**together with the G2 crash-consistency invariant and its failure-injection deliverable** (§A.2 task
7), **P20** (Compose, one activity, two tiny screens) and **P21** (ServiceTag keeps its AAR pending
the spike now folded into §E checks 1–2).

Everything this runbook once proposed has been accepted or superseded: the composite-build choice by
**O15**, the Migrate-tag tool and all legacy handling by **O2/O11**, the reconstructions of §22's word
list, §27's order and §34's gates by the brief now being on disk, and the remote-ordering deviation by
the controller's acceptance recorded in §B.
