# Split Phase D — ServiceTag identity conversion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn this repository's app into **ServiceTag** — new `applicationId`, namespace, Kotlin package root, NDEF external type, deep-link scheme, label, icon, database, prefs, export prefixes and signing location — with one Gradle-owned identity value feeding both the manifest and the code, and with the legacy `md5_short` format deleted rather than carried.

**Architecture:** Fifteen small, independently revertible commits in §A.1's order, then two verification commits. The identity conversion is deliberately *not* one search-and-replace: `com.loosecannon.notenfc` occurs 2 028 times across 236 files, and the NFC type constants, the manifest filter path and the deep-link literals must each change in their own task so the repository-wide zero-hit grep is a **whole-phase** gate and not a per-task one (G3). The new seam is `TagIdentity` — a plain data class introduced in `:core` now and moved to `nfc-tag-core` in Phase F — built from three `buildConfigField`s that come from the same Gradle value as the manifest's `${ndefTagPath}` placeholder (C9, target §4.8).

**Tech Stack:** unchanged — AGP 9.4.0, Kotlin 2.4.20, Compose BOM 2026.08.00, Room 3.0.3, Navigation 3 1.1.7, kotlinx-serialization 1.9.0, minSdk 26, targetSdk 36, compileSdk 37. **No new dependency in this phase.**

**Spec:** `docs/architecture/product-split-migration.md` **§A.1** (the fifteen-row task table, its per-row verification, and the whole-phase verification) and `docs/architecture/product-split-target.md` **§3** (the identity table), **§4.8** (the one manifest↔constant mechanism and its two tests), **§8** (signing), **§9** (CI) and **§11** (the ratified proposals). The identity-carrier inventory every task is measured against is `docs/architecture/product-split-archaeology.md` **§4.6**. Rulings: `~/Documents/Projects/AndroidStudioProjects/noteNFC/.superpowers/split/ledger.md` (O1–O15, C9, G1, G3, G4, H2, H8, the "vet everything possible on the emulator" rule and the naming rule). Read the spec sections before Task 1; every task argues from them.

## Global Constraints

- **Instrumented runs are emulator-only.** Every `connectedDebugAndroidTest`, `adb install`, `am start` and `logcat` in this plan runs with `export ANDROID_SERIAL=emulator-5554` exported first. **The phone is never a target in this phase** — it holds the owner's real data and an instrumented run wipes it. A step that cannot see an `emulator-*` serial stops; it does not fall back to whatever else is attached.
- **No library extraction starts here.** Phase F owns `nfc-tag-core`. `TagIdentity` is introduced as a plain data class in `:core` (`com.loosecannon.servicetag.core.nfc`) with the exact shape target §4.2 gives it, so Phase F is a *move*, not a redesign. No new module, no submodule, no `settings.gradle.kts` `include`, no catalog alias.
- **No legacy compatibility is kept** (O2/O3/O11). `md5_short`, `LEGACY_MD5`, the legacy decoder, its manifest filter, its sheet and its enum member are deleted, not deprecated. This is a **deliberate reversal of D6's "kept permanently" promise** and Task 6's commit says so. The live database has `nfc_tag = 0` rows (arch §7.5), so there is nothing in the owner's data to lose.
- **No `notenfc://` anywhere** (O3). The scheme is gone, not aliased.
- **Naming rule.** "noteNFC" survives in exactly three roles: the historical product name in `docs/` (history — never rewritten, §30/§H), the retired identity in prose *about* the split, and the path `~/.config/notenfc/` which keeps its name because §12 forbids touching that key. Everything else becomes ServiceTag. New working files use the new name.
- **`:app` must compile after every task.** The one exception is stated in its own task: after Task 3 the debug APK builds and both JVM suites pass, but the manifest's five FQN `android:name` literals still point at the retired package, so the APK must not be installed or launched until **Task 4** lands, which restores it. No other task leaves the tree in that state.
- **No personal data in tracked files.** No owner paths, device serials, usernames, note ids or keystore material. The owner's home is written `~`. Fingerprints are public keys and may be *pointed at*; passwords and keystores are never printed, committed or logged.
- **Commit messages are casual and terse, lower-case-ish, with no trailers and no attribution lines of any kind.** No `Co-Authored-By`, no `Generated-with`, no session URLs.
- **Signing must keep working without a key.** `~/.config/servicetag/keystore.properties` does not exist yet (it is generated in a later phase). The release build type simply has no signing config when the file is absent — exactly today's behaviour, and `:app:assembleRelease` must still succeed.
- **CI is unchanged in this phase.** `.github/workflows/ci.yml` runs `:core:test :app:testDebugUnitTest :app:assembleDebug`; no module path or task name changes, so no workflow edit is due until Phase G adds the submodule steps (target §9). Do not touch the workflow.
- **Per-task gate:** `./gradlew :core:test :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --console=plain`, plus `:app:assembleDebug` for any task that touches a manifest, a resource or `build.gradle.kts`.
- **Rollback (§I).** Every task is one commit on `product-split`. Revert the task commit, or `git reset --hard f8606a0` — the docs-only tip of `product-split`, which is this phase's floor. §I's `ac523d7` is the *repository* checkpoint (`master`, `pre-split-master`, `pre-split-checkpoint`); resetting there would also drop the design package, so use it only for abandoning the whole branch. `master`, `origin` and both recovery refs are untouched by every task in this plan.

## The one identity value, and everything derived from it

| Thing | Value | Where it comes from |
|---|---|---|
| `rootProject.name` | `ServiceTag` | `settings.gradle.kts` literal (Task 1) |
| `namespace` / `applicationId` | `com.loosecannon.servicetag` | `app/build.gradle.kts` (Task 2) |
| `versionCode` / `versionName` | `7` / `"2.5"` | `app/build.gradle.kts` (Task 2) |
| keystore properties | `~/.config/servicetag/keystore.properties` | `app/build.gradle.kts` (Task 2) |
| Kotlin package root | `com.loosecannon.servicetag` | path + `package`/`import` (Task 3) |
| NDEF external domain | `com.loosecannon.servicetag` | `tagExternalDomain` in `app/build.gradle.kts` → `BuildConfig.NDEF_EXTERNAL_DOMAIN` (Task 5) |
| NDEF type name | `tag` | `tagTypeName` → `BuildConfig.NDEF_TYPE_NAME` (Task 5) |
| AAR package | `com.loosecannon.servicetag` | `tagAarPackage` → `BuildConfig.NDEF_AAR_PACKAGE` (Task 5) |
| manifest `android:path` | `${ndefTagPath}` = `/com.loosecannon.servicetag:tag` | `manifestPlaceholders["ndefTagPath"]`, same two vals (Task 5) |
| FileProvider authority | `com.loosecannon.servicetag.files` | already derived: `${applicationId}` / `BuildConfig.APPLICATION_ID` — **no literal to change** |
| test APK id | `com.loosecannon.servicetag.test` | AGP default from `applicationId`; declared nowhere |
| deep-link scheme | `servicetag` | `TagRoute.SCHEME`, `DeepLinkRoute.SCHEME`, manifest (Task 7) |
| label / theme / app class / nav root | `ServiceTag` / `Theme.ServiceTag` / `ServiceTagApp` / `ServiceTagRoot` | Task 8 |
| database / prefs / export prefixes | `servicetag.db` / `servicetag` / `ServiceTag-{data,artifacts}-<stamp>.zip` | Task 10 |
| Room schema export dir | `app/schemas/com.loosecannon.servicetag.data.room.AppDatabase/` | derived from the `AppDatabase` FQN (Task 11) |

## File structure

`:core` — **new** `core/nfc/TagIdentity.kt` (the data class, Phase-F shaped); `core/nfc/NdefCodec.kt` becomes a class parameterised by it and loses every legacy member; `core/model/TagBinding.kt` (`PayloadFormat` loses `LEGACY_MD5`); `core/usecase/ResolveTag.kt` (loses `UnknownLegacy`); `core/nfc/OverwritePolicy.kt` (loses the legacy branch, gains ServiceTag wording); `core/nfc/TagRoute.kt` and `core/links/DeepLinkRoute.kt` (scheme); **new** `core/src/test/.../nfc/NdefEnvelopeIsolationTest.kt`; the whole tree moves from `.../notenfc/core/...` to `.../servicetag/core/...`.

`:app` — `build.gradle.kts` (identity, version, keystore path, the placeholder and three `buildConfigField`s); `settings.gradle.kts`; both manifests; `di/AppGraph.kt` (`tagIdentity`, `ndefCodec`, `DB_NAME`); `prefs/AppPrefs.kt`; `backup/SafBackupSetIO.kt` (`BackupSetNames`); `nfc/{TagWriter,NfcDispatchActivity}.kt`; `ui/scan/{TagWriteController,ScanViewModels,TagResultSheet}.kt`; **new** `ui/scan/TagResultWire.kt`; `MainActivity.kt`; `NoteNfcApp.kt` → `ServiceTagApp.kt`; `ui/nav/NoteNfcApp.kt` → `ui/nav/ServiceTagRoot.kt`; `ui/components/NoteNfcIcons.kt` → `ServiceTagIcons.kt`; `ui/theme/*`; `res/values/{strings,themes}.xml`; the icon set from the owner's pack — `res/drawable/ic_launcher_{background,foreground,monochrome}.xml`, `res/mipmap-anydpi-v26/ic_launcher{,_round}.xml`, `res/mipmap-*dpi/ic_launcher{,_round}.png`, replacing `res/mipmap-anydpi/*.xml` and the inherited `*.webp`; `app/schemas/`; **new** tests `app/src/test/.../nfc/TagIdentityBindingTest.kt`, `app/src/test/.../ui/scan/TagResultWireTest.kt`, `app/src/androidTest/.../nfc/TagIdentityDispatchTest.kt`, `app/src/androidTest/.../ui/NfcIdentityDeviceProofTest.kt`; the existing nine androidTest files and the JVM suites follow the package, the scheme and the prefixes.

`docs/` — `README.md` (Task 15, the hygiene slice only) and `docs/architecture/product-split-evidence.md` (Task 17). Everything under `docs/design/` is history and is **not** edited in this phase.

---

### Task 1 (§A.1 row 1): `rootProject.name`

**Files:**
- Modify: `settings.gradle.kts:20`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: the Gradle root project is named `ServiceTag`. Nothing in code reads it; it is what `./gradlew projects` prints and what the IDE shows.

- [ ] **Step 1: Read the current name**

Run: `git grep -n 'rootProject.name' settings.gradle.kts`
Expected: `settings.gradle.kts:20:rootProject.name = "noteNFC"`

- [ ] **Step 2: Change it**

`settings.gradle.kts` line 20:

```kotlin
rootProject.name = "ServiceTag"
```

Nothing else in that file changes: `pluginManagement`, the foojay plugin, `FAIL_ON_PROJECT_REPOS` and `include(":app", ":core")` all stay exactly as they are.

- [ ] **Step 3: Verify**

Run: `./gradlew projects --console=plain`
Expected: the tree prints `Root project 'ServiceTag'` with `Project ':app'` and `Project ':core'` under it.

Run: `git grep -c 'noteNFC' -- settings.gradle.kts`
Expected: no output (grep exits 1 — zero matches).

- [ ] **Step 4: Commit**

```bash
git add settings.gradle.kts
git commit -m "root project name is ServiceTag"
```

---

### Task 2 (§A.1 row 2): `namespace`, `applicationId`, the keystore path and the version ratchet

> **Pre-flight ruling (controller, 2026-09-16), applied at execution:** changing `namespace` moves the
> generated `R`/`BuildConfig` classes, and four Kotlin files import them by their full name
> (`ui/components/NoteNfcIcons.kt`, `di/AppGraph.kt`, `ui/settings/SettingsScreen.kt`,
> `debug/DebugBackupActivity.kt`). This task therefore also rewrites exactly those four import lines to
> `com.loosecannon.servicetag.{R,BuildConfig}` — nothing else in those files — so `:app:assembleDebug`
> is green after the task as §A.1 row 2 requires. They stay correct after Task 3 moves the package roots,
> because `R`/`BuildConfig` follow the Gradle namespace, not the Kotlin package.


**Files:**
- Modify: `app/build.gradle.kts:12,17,21,24,25`

**Interfaces:**
- Consumes: Task 1.
- Produces: `BuildConfig.APPLICATION_ID == "com.loosecannon.servicetag"`, `BuildConfig.VERSION_NAME == "2.5"`, the FileProvider authority `com.loosecannon.servicetag.files` (derived, nothing to edit), and the test APK id `com.loosecannon.servicetag.test` (AGP default). Task 5 adds the three `buildConfigField`s to the same `defaultConfig` block.

**Note on what does *not* change here.** The Kotlin sources are still in package `com.loosecannon.notenfc` (Task 3 moves them) and the manifest's `android:name` literals are absolute, so the build and the launch both still work after this task. `namespace` and the Kotlin package root are independent inputs to AGP; they are allowed to disagree for exactly one task.

- [ ] **Step 1: Edit the four values and the keystore path**

`app/build.gradle.kts`, line 12 — the properties file:

```kotlin
    val f = file(System.getProperty("user.home") + "/.config/servicetag/keystore.properties")
```

Line 17 and lines 21–25 — the identity and the version ratchet:

```kotlin
android {
    namespace = "com.loosecannon.servicetag"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.loosecannon.servicetag"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "2.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
```

The `signingConfigs`/`buildTypes` mechanism is untouched: the same four properties, the same `if (keystoreProps.isNotEmpty())` guards. `~/.config/servicetag/keystore.properties` does not exist yet, so `keystoreProps` is empty and the release build type gets no signing config — the behaviour today, and the reason `assembleRelease` still works.

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew :app:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL. `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 3: Read the identity back out of the APK, not out of the source**

```bash
AAPT2="$HOME/Android/Sdk/build-tools/35.0.0/aapt2"   # any build-tools >= 34 will do
"$AAPT2" dump badging app/build/outputs/apk/debug/app-debug.apk | grep -E "^package:|application-label:"
```

Expected:

```
package: name='com.loosecannon.servicetag' versionCode='7' versionName='2.5' ...
application-label:'noteNFC'
```

The label is still the old one — `app_name` is Task 8. The **package, versionCode and versionName** are what this task proves.

- [ ] **Step 4: Prove the release build still configures without a key**

Run: `./gradlew :app:assembleRelease --console=plain`
Expected: BUILD SUCCESSFUL, an **unsigned** release APK. If this fails, the keystore guard was broken — revert and re-read lines 29–38 and 45–51.

- [ ] **Step 5: Run the gate**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --console=plain`
Expected: PASS, unchanged counts.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts
git commit -m "servicetag applicationId, namespace, keystore path, version 7/2.5"
```

---

### Task 3 (§A.1 row 3): move the Kotlin package roots

**Files:**
- Move: `app/src/{main,debug,test,androidTest}/kotlin/com/loosecannon/notenfc/` → `.../servicetag/`
- Move: `core/src/{main,test}/kotlin/com/loosecannon/notenfc/` → `.../servicetag/`
- Modify: every one of the 233 `.kt` files (`package`, `import`, and KDoc FQN links)

**Interfaces:**
- Consumes: Task 2.
- Produces: every type in the app and the library half now lives under `com.loosecannon.servicetag`. Tasks 4–15 quote paths under the **new** root and nothing else.

**Scope, and why (G3).** This task rewrites **package declarations, imports, KDoc FQN links and path roots only**. Three files keep a `com.loosecannon.notenfc` string on purpose, because it is *identity on the wire* or a *resource path* and belongs to a later task:

| File | The line that stays | Whose task |
|---|---|---|
| `core/.../core/nfc/NdefCodec.kt` | `DOMAIN`, `PACKAGE_NAME` | Task 5 |
| `core/src/test/.../nfc/NdefCodecV1Test.kt` | the two on-wire byte assertions | Task 5 |
| `app/src/test/.../data/room/MigrationTestSupport.kt` | `schemas/com.loosecannon.notenfc.data.room.AppDatabase/$version.json` | Task 11 |

`NdefCodecTest.kt` is deliberately **not** in that list: its legacy fixture names the type through `NdefCodec.LEGACY_TYPE` and its foreign-type case reads `com.loosecannon.evernotenfc:md5_short`, neither of which the pattern below can match. It takes the blanket pass like every other file.

Both manifests also keep theirs (Tasks 4, 5, 6) and the six `notenfc://` `androidTest` files keep theirs (Task 7). **The repository-wide zero-hit grep does not belong here** — it is Task 16.

**The one thing this task leaves broken, and for one task only.** After this commit the manifest's five `android:name` literals still name `com.loosecannon.notenfc.*` classes that no longer exist. `:app:assembleDebug` succeeds (AGP does not resolve those literals at assemble time) and both JVM suites pass, but **do not install or launch the APK until Task 4**, which is the task that restores it.

- [ ] **Step 1: Move the six package roots with `git mv`**

```bash
for d in app/src/main app/src/debug app/src/test app/src/androidTest core/src/main core/src/test; do
  git mv "$d/kotlin/com/loosecannon/notenfc" "$d/kotlin/com/loosecannon/servicetag"
done
git status --short | head -5      # renames, not deletes+adds
```

Expected: `git ls-files app core | grep -c 'com/loosecannon/notenfc/'` prints `0`.

- [ ] **Step 2: Rewrite the declarations**

```bash
KEEP='core/src/main/kotlin/com/loosecannon/servicetag/core/nfc/NdefCodec.kt|core/src/test/kotlin/com/loosecannon/servicetag/core/nfc/NdefCodecV1Test.kt|app/src/test/kotlin/com/loosecannon/servicetag/data/room/MigrationTestSupport.kt'

# 1. everywhere else: every FQN reference, declarations and KDoc links alike
git ls-files -- 'app/*.kt' 'core/*.kt' | grep -vE "^($KEEP)$" \
  | xargs perl -pi -e 's/\bcom\.loosecannon\.notenfc\b/com.loosecannon.servicetag/g'

# 2. the three exceptions: package and import lines ONLY, so their on-wire and
#    resource-path strings survive for tasks 5 and 11
git ls-files -- 'app/*.kt' 'core/*.kt' | grep -E "^($KEEP)$" \
  | xargs perl -pi -e 's/^(\s*(?:package|import)\s+)com\.loosecannon\.notenfc/${1}com.loosecannon.servicetag/'
```

`\b` after `notenfc` keeps `notenfc://` untouched (it is never preceded by `com.loosecannon.`), which is what leaves Task 7 something to do.

- [ ] **Step 3: The scoped check — and only the scoped check (G3)**

```bash
git grep -nE '^\s*(package|import)\s+com\.loosecannon\.notenfc' -- app core   # expect: no output
git ls-files app core | grep -c 'com/loosecannon/notenfc/'                     # expect: 0
git grep -nIE 'com\.loosecannon\.notenfc' -- app core                          # expect: exactly the 12 lines below
```

The third command's expected output, line for line:

```
app/src/debug/AndroidManifest.xml:10            (FQN android:name  -> task 4)
app/src/main/AndroidManifest.xml:36             (FQN android:name  -> task 4)
app/src/main/AndroidManifest.xml:42             (FQN android:name  -> task 4)
app/src/main/AndroidManifest.xml:64             (FQN android:name  -> task 4)
app/src/main/AndroidManifest.xml:79             (FQN android:name  -> task 4)
app/src/main/AndroidManifest.xml:87             (:tag filter path  -> task 5)
app/src/main/AndroidManifest.xml:92             (md5_short filter  -> task 6)
app/src/test/.../data/room/MigrationTestSupport.kt:96   (schema dir -> task 11)
core/.../core/nfc/NdefCodec.kt:37               (DOMAIN            -> task 5)
core/.../core/nfc/NdefCodec.kt:46               (PACKAGE_NAME      -> task 5)
core/src/test/.../nfc/NdefCodecV1Test.kt:22     (on-wire bytes     -> task 5)
core/src/test/.../nfc/NdefCodecV1Test.kt:33     (on-wire bytes     -> task 5)
```

Any thirteenth line means the rewrite over- or under-reached: fix it before committing.

- [ ] **Step 4: Build and run both suites**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --console=plain`
Expected: PASS, the same test counts as before the move. The migration ladder still finds its fixtures, because `MigrationTestSupport` still points at the old schema directory and that directory has not moved.

- [ ] **Step 5: Commit**

```bash
git add -A app core
git commit -m "move the kotlin package roots to servicetag"
```

---

### Task 4 (§A.1 row 4): the five fully-qualified `android:name` literals

**Files:**
- Modify: `app/src/main/AndroidManifest.xml:36,42,64,79`
- Modify: `app/src/debug/AndroidManifest.xml:10`

**Interfaces:**
- Consumes: Task 3 (the classes are already at the new FQNs).
- Produces: a launchable debug APK again. Nothing else depends on this task.

**Why this is its own task.** None of the five is derived from `namespace` — the manifest spells every component out in full rather than using the `.MainActivity` shorthand (arch §4.6, review correction 4). A rename must edit all five by hand, and this is the hand.

- [ ] **Step 1: Edit the four in the main manifest**

```xml
    <application
        android:name="com.loosecannon.servicetag.NoteNfcApp"
```

```xml
        <activity
            android:name="com.loosecannon.servicetag.MainActivity"
```

```xml
        <activity
            android:name="com.loosecannon.servicetag.ShareActivity"
```

```xml
        <activity
            android:name="com.loosecannon.servicetag.nfc.NfcDispatchActivity"
```

The class *simple* names are still the old ones (`NoteNfcApp` is Task 8); only the package part changes here.

- [ ] **Step 2: Edit the one in the debug manifest**

```xml
        <activity
            android:name="com.loosecannon.servicetag.debug.DebugBackupActivity"
            android:exported="true"
            android:label="noteNFC Backup (debug)">
```

The label is Task 8.

- [ ] **Step 3: Check the merged manifest's component names**

```bash
./gradlew :app:processDebugMainManifest --console=plain
MERGED="$(find app/build/intermediates -name AndroidManifest.xml -path '*ebug*' -newermt '-10 minutes' | head -1)"
echo "$MERGED"
grep -oE 'android:name="com\.loosecannon\.[a-z]+[^"]*"' "$MERGED" | sort -u
```

Expected: every `com.loosecannon.*` component name reads `com.loosecannon.servicetag.…`, and none reads `com.loosecannon.notenfc.…`.

**Correction to §A.1 row 4's verify cell.** It says "the merged manifest contains no `notenfc` substring". That cannot be true yet: the two `NDEF_DISCOVERED` filter paths still carry the old external type until Tasks 5 and 6, and the `notenfc` deep-link scheme until Task 7. The assertion that belongs to *this* task is the one above, scoped to `android:name`. The no-substring assertion is Task 16's.

- [ ] **Step 4: Install on the emulator and launch it**

```bash
export ANDROID_SERIAL=emulator-5554
adb devices                        # exactly one emulator-* line, and no physical device
./gradlew :app:installDebug --console=plain
adb shell am start -W -n com.loosecannon.servicetag/com.loosecannon.servicetag.MainActivity
```

Expected: `Status: ok` and `LaunchState: COLD`, no `ActivityNotFoundException`, no crash dialog. **If a phone is attached, pin every adb and gradle command to `ANDROID_SERIAL=emulator-5554` / `-s emulator-5554` and never address the phone's serial.**

- [ ] **Step 5: Run the gate**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/debug/AndroidManifest.xml
git commit -m "the five manifest FQN names follow the new package"
```

---

### Task 5 (§A.1 row 5, target §4.8): one Gradle-owned identity value, `TagIdentity`, and the two tests that bind it

**Files:**
- Modify: `app/build.gradle.kts` (the identity vals, one `manifestPlaceholders` entry, three `buildConfigField`s)
- Modify: `app/src/main/AndroidManifest.xml:87` (the `:tag` filter path becomes the placeholder)
- Create: `core/src/main/kotlin/com/loosecannon/servicetag/core/nfc/TagIdentity.kt`
- Modify: `core/src/main/kotlin/com/loosecannon/servicetag/core/nfc/NdefCodec.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/di/AppGraph.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/nfc/TagWriter.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/nfc/NfcDispatchActivity.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/ui/scan/{TagWriteController,ScanViewModels}.kt`
- Modify: `app/src/test/kotlin/com/loosecannon/servicetag/testing/FakeGraph.kt`
- Modify: `app/src/test/kotlin/com/loosecannon/servicetag/ui/scan/TagWriteControllerTest.kt`, `app/src/test/kotlin/com/loosecannon/servicetag/nfc/TagUseCasesRoomTest.kt`
- Modify: `core/src/test/kotlin/com/loosecannon/servicetag/core/nfc/{NdefCodecTest,NdefCodecV1Test}.kt`
- Create: `app/src/test/kotlin/com/loosecannon/servicetag/nfc/TagIdentityBindingTest.kt`
- Create: `app/src/androidTest/kotlin/com/loosecannon/servicetag/nfc/TagIdentityDispatchTest.kt`

**Interfaces:**
- Consumes: Tasks 2–4.
- Produces, verbatim — Tasks 6, 12, 13, 16 and 17 name these and do not redefine them:

```kotlin
// core/.../core/nfc/TagIdentity.kt
package com.loosecannon.servicetag.core.nfc

/**
 * What identifies this product's tags on the wire. These strings are the only product knowledge
 * the codec holds, and the caller supplies them.
 *
 * Phase F moves this type verbatim into `nfc-tag-core`'s `com.loosecannon.nfc.tagcore` (target
 * §4.2); nothing here may grow a ServiceTag-specific member in the meantime.
 *
 * @param externalDomain NFC Forum external-type domain. Lower-case: `NdefRecord.createExternal`
 *   lower-cases both halves before joining, so a mixed-case domain would not match the bytes
 *   actually on the tag (arch §2.9).
 * @param typeName external-type name, e.g. "tag".
 * @param aarPackage the applicationId to pin with an Application Record, or **null for no AAR**.
 *   A separate parameter from [externalDomain] even when the two strings are equal, because they
 *   are different things: an NFC Forum domain and an Android package name (C9, arch §6.3).
 *   Example: `TagIdentity("com.example.app", "tag", "com.example.app")`.
 */
data class TagIdentity(
    val externalDomain: String,
    val typeName: String,
    val aarPackage: String? = null,
) {
    val externalType: String = "$externalDomain:$typeName"

    init {
        require(externalDomain.isNotBlank() && typeName.isNotBlank()) {
            "an external type needs a domain and a name: '$externalDomain':'$typeName'"
        }
        require(externalType == externalType.lowercase()) {
            "an external type is lower-case on the wire: '$externalType'"
        }
    }
}
```

```kotlin
// core/.../core/nfc/NdefCodec.kt — the identity-carrying half becomes instance state
class NdefCodec(val identity: TagIdentity) {
    fun decode(records: List<NdefRecordData>): TagPayload
    fun encodeV1(tagId: TagId): List<NdefRecordData>      // the :tag record, then the AAR if any
    fun v1Record(tagId: TagId): NdefRecordData
    fun applicationRecord(): NdefRecordData?              // null when identity.aarPackage == null

    companion object {
        const val TNF_EXTERNAL_TYPE: Int = 0x04
        const val AAR_TYPE: String = "android.com:pkg"
        const val V1_VERSION: Int = 0x01
        const val V1_FLAGS: Int = 0x00
        const val V1_PAYLOAD_LENGTH: Int = 18
        fun requireCanonicalUuid(tagId: TagId): java.util.UUID
    }
}
```

```kotlin
// app/.../di/AppGraph.kt — the app's own identity, built from the Gradle value and nothing else
val tagIdentity: TagIdentity = TagIdentity(
    externalDomain = BuildConfig.NDEF_EXTERNAL_DOMAIN,
    typeName = BuildConfig.NDEF_TYPE_NAME,
    aarPackage = BuildConfig.NDEF_AAR_PACKAGE,
)
val ndefCodec: NdefCodec = NdefCodec(tagIdentity)
```

```kotlin
// app/.../nfc/TagWriter.kt and ui/scan/TagWriteController.kt — the codec arrives, it is never a global
fun TagWriter.inspect(tag: Tag, codec: NdefCodec): TagInspection?
class RealTagIo(private val codec: NdefCodec) : TagIo
class TagWriteController(..., private val codec: NdefCodec, ...)
```

- [ ] **Step 1: Write the failing JVM binding test**

`app/src/test/kotlin/com/loosecannon/servicetag/nfc/TagIdentityBindingTest.kt`:

```kotlin
package com.loosecannon.servicetag.nfc

import com.loosecannon.servicetag.BuildConfig
import com.loosecannon.servicetag.core.nfc.TagIdentity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The C9 binding, JVM half (target §4.8 test 1). One Gradle value produces the manifest
 * placeholder and these three BuildConfig fields; this test is what fails if anyone puts a second
 * copy of the identity anywhere. The *installed* manifest is proved by `TagIdentityDispatchTest`
 * on the emulator, which a string comparison cannot do.
 *
 * Gradle runs JVM unit tests with the module directory as the working directory; an IDE run
 * configuration may use the repository root, so both are tried (the idiom `MigrationTestSupport`
 * already uses).
 */
class TagIdentityBindingTest {

    private val identity = TagIdentity(
        externalDomain = BuildConfig.NDEF_EXTERNAL_DOMAIN,
        typeName = BuildConfig.NDEF_TYPE_NAME,
        aarPackage = BuildConfig.NDEF_AAR_PACKAGE,
    )

    private fun moduleFile(relative: String): File =
        listOf(File(relative), File("app/$relative")).firstOrNull { it.isFile }
            ?: error("cannot find $relative from ${File(".").absolutePath}")

    private val manifest: String by lazy { moduleFile("src/main/AndroidManifest.xml").readText() }
    private val buildScript: String by lazy { moduleFile("build.gradle.kts").readText() }

    @Test fun theIdentityIsTheOneWeMeant() {
        assertEquals("com.loosecannon.servicetag:tag", identity.externalType)
        assertEquals(identity.externalType.lowercase(), identity.externalType)
    }

    /** Invariant 6: the AAR pins this app, not a string that merely looks like it. */
    @Test fun theAarPackageIsThisApplicationId() {
        assertEquals(BuildConfig.APPLICATION_ID, identity.aarPackage)
    }

    /** The manifest must carry no identity literal at all — only the placeholder. */
    @Test fun theManifestFilterPathIsThePlaceholder() {
        // Plain strings with escapes, not raw strings: a raw string that ends in a quote runs
        // straight into its own terminator and is a trap for the next reader.
        assertTrue(
            "the NDEF filter path must be \${ndefTagPath}, never a literal",
            manifest.contains("android:path=\"\${ndefTagPath}\""),
        )
        assertEquals(
            "no identity literal may survive in the manifest", 0,
            Regex(Regex.escape(identity.externalType)).findAll(manifest).count(),
        )
    }

    /** Exactly one NDEF_DISCOVERED filter, and exactly one place that defines the placeholder. */
    @Test fun oneFilterAndOneDefinition() {
        assertEquals(
            "one NDEF_DISCOVERED filter", 1,
            Regex("android.nfc.action.NDEF_DISCOVERED").findAll(manifest).count(),
        )
        assertEquals(
            "one definition of ndefTagPath", 1,
            Regex("""manifestPlaceholders\["ndefTagPath"]""").findAll(buildScript).count(),
        )
        assertTrue(
            "the placeholder is built from the identity vals, not from a literal",
            buildScript.contains("manifestPlaceholders[\"ndefTagPath\"] = \"/\$tagExternalDomain:\$tagTypeName\""),
        )
    }
}
```

`oneFilterAndOneDefinition`'s first assertion is **expected to fail until Task 6** deletes the `md5_short` filter: there are two filters today. Write it now, watch it fail with "one NDEF_DISCOVERED filter expected:<1> but was:<2>", and mark it `@Ignore("two filters until task 6 drops md5_short")` for this task only — Task 6's Step 5 removes the annotation and is the task whose green run proves it. Every other assertion here must pass at the end of this task.

- [ ] **Step 2: Write the failing instrumented binding test**

`app/src/androidTest/kotlin/com/loosecannon/servicetag/nfc/TagIdentityDispatchTest.kt`:

```kotlin
package com.loosecannon.servicetag.nfc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The C9 binding, device half (target §4.8 test 2). It asks the platform the question the
 * platform will be asked by a real tag: who resolves `vnd.android.nfc://ext/<externalType>` for
 * `ACTION_NDEF_DISCOVERED`? That reads the MERGED manifest as installed, so it is the only test
 * that can catch a placeholder that resolved to the wrong string.
 *
 * Emulator only (no NFC hardware needed — this is a PackageManager query, not a scan).
 */
@RunWith(AndroidJUnit4::class)
class TagIdentityDispatchTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val graph get() = (context.applicationContext as com.loosecannon.servicetag.NoteNfcApp).graph

    @Test fun theAarPackageIsThisApplicationId() {
        assertEquals(context.packageName, graph.tagIdentity.aarPackage)
    }

    @Test fun ourExternalTypeResolvesToOurDispatchActivity() {
        val intent = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
            .setData(Uri.parse("vnd.android.nfc://ext/${graph.tagIdentity.externalType}"))

        val matches = context.packageManager.queryIntentActivities(intent, 0)

        assertEquals("exactly one activity may claim our external type", 1, matches.size)
        val info = matches.single().activityInfo
        assertEquals(context.packageName, info.packageName)
        assertEquals(NfcDispatchActivity::class.java.name, info.name)
    }

    /** The retired identity belongs to nobody now (O3). */
    @Test fun theRetiredExternalTypeResolvesToNothingOfOurs() {
        val intent = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
            .setData(Uri.parse("vnd.android.nfc://ext/com.loosecannon.notenfc:tag"))

        val ours = context.packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName == context.packageName }

        assertEquals("nothing of ours may still claim the retired type", 0, ours.size)
    }
}
```

`com.loosecannon.servicetag.NoteNfcApp` is spelled out because the Application class is still called that until Task 8; Task 8's blanket rename fixes this reference with every other one.

- [ ] **Step 3: Run both new tests and watch them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*TagIdentityBindingTest' --console=plain`
Expected: FAIL — compilation error, `BuildConfig.NDEF_EXTERNAL_DOMAIN` and `TagIdentity` unresolved.

Run: `./gradlew :app:compileDebugAndroidTestKotlin --console=plain`
Expected: FAIL — `graph.tagIdentity` unresolved.

- [ ] **Step 4: Add the Gradle-owned identity value**

`app/build.gradle.kts` — immediately above the `android { }` block, beside the keystore reader:

```kotlin
// The single source of truth for this app's tag identity (C9, target §4.8). It produces the
// manifest filter path AND the BuildConfig fields the app builds its TagIdentity from, so the
// two cannot drift. android:path stays an EXACT match, never pathPrefix.
val tagExternalDomain = "com.loosecannon.servicetag"   // NFC Forum external-type domain
val tagTypeName = "tag"
val tagAarPackage: String? = "com.loosecannon.servicetag"  // null would mean "no AAR" (O13/P21)
```

and inside `defaultConfig`, after `testInstrumentationRunner`:

```kotlin
        manifestPlaceholders["ndefTagPath"] = "/$tagExternalDomain:$tagTypeName"
        buildConfigField("String", "NDEF_EXTERNAL_DOMAIN", "\"$tagExternalDomain\"")
        buildConfigField("String", "NDEF_TYPE_NAME", "\"$tagTypeName\"")
        buildConfigField("String", "NDEF_AAR_PACKAGE", tagAarPackage?.let { "\"$it\"" } ?: "null")
```

`buildFeatures { buildConfig = true }` is already set, so nothing else is needed.

- [ ] **Step 5: Point the manifest filter at the placeholder**

`app/src/main/AndroidManifest.xml` line 87:

```xml
                <data android:scheme="vnd.android.nfc" android:host="ext" android:path="${ndefTagPath}" />
```

Line 92 — the `md5_short` filter — is left alone; Task 6 deletes it whole.

- [ ] **Step 6: Create `TagIdentity` and reshape `NdefCodec`**

Create `core/src/main/kotlin/com/loosecannon/servicetag/core/nfc/TagIdentity.kt` with exactly the code in the **Produces** block.

Rewrite `core/src/main/kotlin/com/loosecannon/servicetag/core/nfc/NdefCodec.kt` as:

```kotlin
package com.loosecannon.servicetag.core.nfc

import com.loosecannon.servicetag.core.model.TagId
import java.nio.ByteBuffer
import java.util.UUID

/** Android-free view of one NDEF record (mirrors android.nfc.NdefRecord's tnf/type/payload). */
data class NdefRecordData(val tnf: Int, val type: ByteArray, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is NdefRecordData && tnf == other.tnf && type.contentEquals(other.type) && payload.contentEquals(other.payload)
    override fun hashCode(): Int = 31 * (31 * tnf + type.contentHashCode()) + payload.contentHashCode()
}

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
 * Pure-bytes codec for tag payload format v1. Android `NdefRecord` objects are built only in the
 * `:app` NFC adapter (D3 §9). The product's wire identity arrives as [identity] and is never a
 * constant in here — Phase F moves this class into `nfc-tag-core`, which may not know either
 * application's name (O5, target §4.2).
 */
class NdefCodec(val identity: TagIdentity) {

    /** Android dispatches on the first record of the first message; so do we. */
    fun decode(records: List<NdefRecordData>): TagPayload {
        val first = records.firstOrNull() ?: return TagPayload.Empty
        val type = String(first.type, Charsets.US_ASCII)
        if (first.tnf != TNF_EXTERNAL_TYPE) return TagPayload.Foreign("tnf=${first.tnf} type=$type")
        // The type gate comes before any body parse, and a sibling product's record must come
        // back Foreign even though its body would also parse as a UUID (C8, invariant 1).
        if (type != identity.externalType) return TagPayload.Foreign("tnf=${first.tnf} type=$type")
        return decodeV1(first.payload)
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
        val bb = ByteBuffer.wrap(payload, 2, 16)
        val uuid = UUID(bb.long, bb.long)
        return TagPayload.V1(TagId(uuid.toString()))
    }

    /** The whole message for a v1 tag: the `:tag` record, then the AAR when this identity has one. */
    fun encodeV1(tagId: TagId): List<NdefRecordData> = listOfNotNull(v1Record(tagId), applicationRecord())

    fun v1Record(tagId: TagId): NdefRecordData {
        val uuid = requireCanonicalUuid(tagId)
        val payload = ByteBuffer.allocate(V1_PAYLOAD_LENGTH)
            .put(V1_VERSION.toByte())
            .put(V1_FLAGS.toByte())
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
        return NdefRecordData(TNF_EXTERNAL_TYPE, identity.externalType.toByteArray(Charsets.US_ASCII), payload)
    }

    /**
     * Byte-identical to `NdefRecord.createApplicationRecord(identity.aarPackage)`, or null when
     * this identity carries no AAR. An AAR is never first: put it first and the tag stops matching
     * the NDEF_DISCOVERED filter (invariant 2).
     */
    fun applicationRecord(): NdefRecordData? = identity.aarPackage?.let { pkg ->
        NdefRecordData(
            tnf = TNF_EXTERNAL_TYPE,
            type = AAR_TYPE.toByteArray(Charsets.US_ASCII),
            payload = pkg.toByteArray(Charsets.US_ASCII),
        )
    }

    companion object {
        const val TNF_EXTERNAL_TYPE: Int = 0x04

        /** Platform constant, not identity. */
        const val AAR_TYPE: String = "android.com:pkg"

        const val V1_VERSION: Int = 0x01
        const val V1_FLAGS: Int = 0x00
        const val V1_PAYLOAD_LENGTH: Int = 18

        /** A tag id must be the canonical lower-case UUID string so that `nfc_tag.id == payload_key` (D4 §3). */
        fun requireCanonicalUuid(tagId: TagId): UUID {
            val uuid = try {
                UUID.fromString(tagId.value)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("tag id is not a UUID: '${tagId.value}'", e)
            }
            require(uuid.toString() == tagId.value) { "tag id must be the canonical lowercase UUID form: '${tagId.value}'" }
            return uuid
        }
    }
}
```

The legacy members (`LEGACY_TYPE_NAME`, `LEGACY_TYPE`, `legacyKeyPattern`, `decodeLegacy`, `TagPayload.LegacyMd5`) are **gone in this rewrite**, which is what makes Task 6 a small task: everything downstream of them is what Task 6 cleans up. Between this task and Task 6 the tree does not compile if you stop here — so Tasks 5 and 6 are written as one sitting and each is committed only when its own gate is green. If you must stop, stop after Task 6.

**Correction to the task split, stated once.** §A.1 splits "parameterise the codec" (row 5) from "drop the legacy format" (row 6), but `TagPayload.LegacyMd5` is a member of the same sealed interface the codec's `when` is exhaustive over, so the compiler makes them one edit. This plan keeps both task numbers and both commits — row 5's commit contains the codec rewrite and every call-site change, row 6's contains the enum, the resolution, the UI, the filter and the fixtures — and Step 11 below is where row 5's gate is allowed to be red in `:app` until row 6 lands. Row 5's own verification (the two binding tests) is unaffected.

- [ ] **Step 7: Wire the codec through the app**

`app/src/main/kotlin/com/loosecannon/servicetag/di/AppGraph.kt` — add the two members from the **Produces** block, beside the other graph members (`BuildConfig` is already imported for `APPLICATION_ID` and `VERSION_NAME`), and add `import com.loosecannon.servicetag.core.nfc.NdefCodec` / `TagIdentity`.

`app/src/main/kotlin/com/loosecannon/servicetag/nfc/TagWriter.kt` — `inspect` takes the codec:

```kotlin
    fun inspect(tag: Tag, codec: NdefCodec): TagInspection? {
```

and line 60 becomes `TagInspection(uid, codec.decode(records), records, ndef.maxSize, ndef.isWritable, needsFormat = false, canLock = ndef.canMakeReadOnly())`. `write` and `lock` are identity-free and do not change — `write`'s capacity arithmetic is already `message.toByteArray().size` against `ndef.maxSize`, which is G1-correct, and stays exactly as it is.

`app/src/main/kotlin/com/loosecannon/servicetag/ui/scan/TagWriteController.kt`:

```kotlin
/** [TagWriter] behind the seam; the only place an `android.nfc.Tag` comes back out of a handle. */
class RealTagIo(private val codec: NdefCodec) : TagIo {
    override fun inspect(tag: TagHandle): TagInspection? = TagWriter.inspect(tag.nfc(), codec)
    override fun write(tag: TagHandle, records: List<NdefRecordData>, lock: Boolean): WriteResult =
        TagWriter.write(tag.nfc(), records, lock)
    override fun lock(tag: TagHandle): Boolean = TagWriter.lock(tag.nfc())

    private fun TagHandle.nfc(): Tag = (this as? NfcTagHandle)?.tag
        ?: error("RealTagIo only accepts a handle delivered by reader mode")
}
```

The controller gains the codec as a constructor property, after `io`:

```kotlin
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
```

and its one use, `val intended = NdefCodec.encodeV1(row.id)`, becomes `val intended = codec.encodeV1(row.id)`.

`ScanViewModels.kt` — the two `RealTagIo` references become instances: line 90 `this(graph.resolveTag, graph.openLink, RealTagIo(graph.ndefCodec))`, and line 273 `{ scope -> TagWriteController(graph, RealTagIo(graph.ndefCodec), target, label, scope) }`.

`NfcDispatchActivity.kt` line 78 becomes `NfcAdapter.ACTION_NDEF_DISCOVERED -> graph.ndefCodec.decode(intent.ndefRecords().orEmpty())`.

`BindTag.kt` needs **no change**: `NdefCodec.requireCanonicalUuid` still resolves, through the companion.

- [ ] **Step 8: Give the JVM fixtures a codec too**

`app/src/test/kotlin/com/loosecannon/servicetag/testing/FakeGraph.kt` — `FakeGraph` is "`AppGraph` without a `Context`", so it gets the same two members, built the same way:

```kotlin
    val tagIdentity: TagIdentity = TagIdentity(
        externalDomain = BuildConfig.NDEF_EXTERNAL_DOMAIN,
        typeName = BuildConfig.NDEF_TYPE_NAME,
        aarPackage = BuildConfig.NDEF_AAR_PACKAGE,
    )
    val ndefCodec: NdefCodec = NdefCodec(tagIdentity)
```

(`BuildConfig` is generated for the unit-test classpath too, so the fixture reads the same values the app does.)

`TagWriteControllerTest.kt` — the `controller()` helper passes `codec = graph.ndefCodec`, and its two `NdefCodec.encodeV1(...)` calls become `graph.ndefCodec.encodeV1(...)`.

`TagUseCasesRoomTest.kt:44` — `val onTag = graph.ndefCodec.decode(graph.ndefCodec.encodeV1(row.id))`.

- [ ] **Step 9: Update the codec's own tests to the new identity and the new arithmetic**

`core/src/test/kotlin/com/loosecannon/servicetag/core/nfc/NdefCodecV1Test.kt` — the class gets a codec, the byte assertions get the new type, and the capacity test is corrected to G1:

```kotlin
package com.loosecannon.servicetag.core.nfc

import com.loosecannon.servicetag.core.model.TagId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `Ndef.getMaxSize()` is the maximum NDEF **message** size a tag can hold, so every capacity
 * comparison in this estate is message size against message size — never the Type-2 TLV header
 * or terminator, which belong to Android and to the tag (G1, invariant 7).
 *
 * The number is a provisional seed only: `[unobserved]`, to be re-pinned from the measured
 * `Ndef.maxSize` of a physical NTAG213 in §D Session 1 (H8). Nothing branches on it — this is a
 * limits test, and its job is to fail loudly if the record ever grows past a small tag.
 */
private const val NTAG213_MAX_MESSAGE_BYTES = 137

@OptIn(ExperimentalStdlibApi::class)
class NdefCodecV1Test {
    private val identity = TagIdentity("com.loosecannon.servicetag", "tag", "com.loosecannon.servicetag")
    private val codec = NdefCodec(identity)
    private val id = TagId("123e4567-e89b-12d3-a456-426614174000")
    private val idBytes = "123e4567e89b12d3a456426614174000".hexToByteArray()

    private fun v1(payload: ByteArray) =
        NdefRecordData(NdefCodec.TNF_EXTERNAL_TYPE, identity.externalType.toByteArray(Charsets.US_ASCII), payload)

    /** The exact bytes a ServiceTag tag carries: 3 + 30 + 18 = 51 B for the record (H2). */
    @Test fun exactByteLayout() {
        val rec = codec.v1Record(id)
        assertEquals(0x04, rec.tnf)
        assertContentEquals("com.loosecannon.servicetag:tag".toByteArray(Charsets.US_ASCII), rec.type)
        assertEquals(18, rec.payload.size)
        assertContentEquals(byteArrayOf(0x01, 0x00) + idBytes, rec.payload)
        // kotlin.test puts the message LAST, unlike JUnit's Assert -- `:core` is kotlin.test.
        assertEquals(51, 3 + rec.type.size + rec.payload.size, "the :tag record is 51 bytes")
    }

    @Test fun messageIsTagRecordThenApplicationRecord() {
        val msg = codec.encodeV1(id)
        assertEquals(2, msg.size)
        assertEquals(codec.v1Record(id), msg[0])
        assertEquals(assertNotNull(codec.applicationRecord()), msg[1])
    }

    /** 51 B for the record plus 3 + 15 + 26 = 44 B for the AAR: a 95 B message (H2). */
    @Test fun theWholeMessageIs95Bytes() {
        val onTag = codec.encodeV1(id).sumOf { 3 + it.type.size + it.payload.size }
        assertEquals(95, onTag)
    }

    @Test fun fitsAnNtag213() {
        val onTag = codec.encodeV1(id).sumOf { 3 + it.type.size + it.payload.size }
        assertTrue(onTag <= NTAG213_MAX_MESSAGE_BYTES, "the message needs $onTag bytes; the seed is $NTAG213_MAX_MESSAGE_BYTES")
    }

    @Test fun wrongLengthIsMalformed() {
        assertIs<TagPayload.Malformed>(codec.decode(listOf(v1(byteArrayOf(0x01, 0x00) + idBytes.copyOf(15)))))
        assertIs<TagPayload.Malformed>(codec.decode(listOf(v1(byteArrayOf(0x01, 0x00) + idBytes + 0x00))))
        assertIs<TagPayload.Malformed>(codec.decode(listOf(v1(byteArrayOf(0x01)))))
    }

    @Test fun nonZeroFlagsIsMalformed() {
        assertIs<TagPayload.Malformed>(codec.decode(listOf(v1(byteArrayOf(0x01, 0x01) + idBytes))))
        assertIs<TagPayload.Malformed>(codec.decode(listOf(v1(byteArrayOf(0x01, 0x80.toByte()) + idBytes))))
    }

    @Test fun emptyPayloadIsMalformed() {
        assertIs<TagPayload.Malformed>(codec.decode(listOf(v1(ByteArray(0)))))
    }

    @Test fun applicationRecordAloneIsForeign() {
        assertIs<TagPayload.Foreign>(codec.decode(listOf(assertNotNull(codec.applicationRecord()))))
    }

    @Test fun tagRecordUnderWrongTnfIsForeign() {
        val rec = NdefRecordData(0x02, identity.externalType.toByteArray(Charsets.US_ASCII), byteArrayOf(0x01, 0x00) + idBytes)
        assertIs<TagPayload.Foreign>(codec.decode(listOf(rec)))
    }

    /** An identity with no AAR writes one record and nothing else (O13's default). */
    @Test fun anIdentityWithoutAnAarWritesOneRecord() {
        val lone = NdefCodec(TagIdentity("com.example.app", "tag"))
        assertEquals(1, lone.encodeV1(id).size)
        assertEquals(null, lone.applicationRecord())
    }
}
```

`legacyRecordStillDecodes` is deleted with it — the legacy type no longer exists.

`core/src/test/kotlin/com/loosecannon/servicetag/core/nfc/NdefCodecTest.kt` — every test in this file is about the legacy record. Replace the whole file with the three claims that are *not* legacy-specific, re-stated against a neutral identity so the parameterisation itself is under test:

```kotlin
package com.loosecannon.servicetag.core.nfc

import com.loosecannon.servicetag.core.model.TagId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The envelope's own rules, on an identity that belongs to no real product. */
class NdefCodecTest {
    private val identity = TagIdentity("com.example.app", "tag", "com.example.app")
    private val codec = NdefCodec(identity)

    private fun external(type: String, payload: String) =
        NdefRecordData(NdefCodec.TNF_EXTERNAL_TYPE, type.toByteArray(Charsets.US_ASCII), payload.toByteArray(Charsets.UTF_8))

    @Test fun emptyMessageIsEmpty() = assertEquals(TagPayload.Empty, codec.decode(emptyList()))

    @Test fun onlyFirstRecordMatters() {
        val ours = codec.v1Record(TagId("123e4567-e89b-12d3-a456-426614174000"))
        val foreign = external("com.example.other:tag", "whatever")
        assertIs<TagPayload.V1>(codec.decode(listOf(ours, foreign)))
        assertIs<TagPayload.Foreign>(codec.decode(listOf(foreign, ours)))
    }

    @Test fun anotherDomainIsForeign() {
        assertIs<TagPayload.Foreign>(codec.decode(listOf(external("com.example.other:tag", "63b37acf"))))
    }

    @Test fun anotherTypeNameInOurDomainIsForeign() {
        assertIs<TagPayload.Foreign>(codec.decode(listOf(external("com.example.app:md5_short", "63b37acf"))))
    }

    @Test fun uriRecordIsForeign() {
        val uri = NdefRecordData(tnf = 0x01, type = byteArrayOf('U'.code.toByte()), payload = byteArrayOf(0x01) + "example.com".toByteArray())
        assertIs<TagPayload.Foreign>(codec.decode(listOf(uri)))
    }
}
```

- [ ] **Step 10: Run `:core`**

Run: `./gradlew :core:test --console=plain`
Expected: PASS. `:core` no longer mentions the legacy format anywhere except `PayloadFormat.LEGACY_MD5` and `Resolution.UnknownLegacy`, which still compile because nothing constructs a `LegacyMd5` payload any more — those are Task 6.

If `:core` fails to compile because `ResolveTag.kt` or `OverwritePolicy.kt` still branch on `TagPayload.LegacyMd5`, delete those two branches **now** (they are `is TagPayload.LegacyMd5 -> …` one-liners, one in each file) and leave the enum member and `Resolution.UnknownLegacy` for Task 6. That is the minimum edit that makes row 5 compile, and Task 6 removes the rest.

- [ ] **Step 11: Run `:app`, expecting the legacy UI to be the only failure**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: FAIL to compile, in exactly these places — `ScanViewModels.kt` (`PayloadFormat.LEGACY_MD5.name`, `TagPayload.LegacyMd5`, `Resolution.UnknownLegacy`), `TagResultSheet.kt` (the `Legacy` sheet), `TagWriteController.kt:292`, `DebugBackupActivity.kt`, and the fixtures that name `LEGACY_MD5`. Nothing else. Any other failure is a mistake in Steps 6–9: fix it before moving on.

- [ ] **Step 12: Commit row 5**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml core/src/main core/src/test app/src/main app/src/test
git commit -m "one gradle-owned tag identity, and the two tests that bind it"
```

---

### Task 6 (§A.1 row 6): drop `md5_short` entirely

**Files:**
- Modify: `core/src/main/kotlin/com/loosecannon/servicetag/core/model/TagBinding.kt`
- Modify: `core/src/main/kotlin/com/loosecannon/servicetag/core/usecase/ResolveTag.kt`
- Modify: `core/src/main/kotlin/com/loosecannon/servicetag/core/nfc/OverwritePolicy.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/ui/scan/{ScanViewModels,TagResultSheet,TagWriteController}.kt`
- Modify: `app/src/main/AndroidManifest.xml` (delete lines 89–93)
- Modify: `app/src/debug/kotlin/com/loosecannon/servicetag/debug/DebugBackupActivity.kt`
- Modify: `app/src/test/kotlin/com/loosecannon/servicetag/nfc/TagIdentityBindingTest.kt` (drop the `@Ignore`)
- Modify the fixtures that name the retired enum: `core/src/test/.../usecase/{ResolveTagTest,TagBindingUseCasesTest,BackupUseCasesTest}.kt`, `core/src/test/.../backup/BackupCodecTest.kt`, `core/src/test/.../nfc/OverwritePolicyTest.kt`, `app/src/test/.../data/room/{NfcTagDaoTest,RoomRepositoriesTest}.kt`, `app/src/test/.../backup/RestoreProofTest.kt`, `app/src/test/.../nfc/TagUseCasesRoomTest.kt`

**Interfaces:**
- Consumes: Task 5's codec (already legacy-free).
- Produces: `enum class PayloadFormat { V1 }`; `Resolution` without `UnknownLegacy`; `TagResult` without `Legacy`; exactly **one** `NDEF_DISCOVERED` filter in the manifest.

**Record this as what it is.** D6 promised the `md5_short` filter was "kept permanently". O2/O3 withdraw that promise, and this task is the withdrawal. The justification is in O11 and target §3: a decode-only legacy type would need a manifest filter, a payload branch and a UI state — architecture for a dead format. The eight physical legacy tags in the field are inventory facts (§D.5); their lifecycle is an ordinary ServiceTag write, with no migration code. The live database carries `nfc_tag = 0` rows (arch §7.5), so no row of the owner's data can name the retired format.

**The one consequence to state out loud.** `BackupCodec` validates `payloadFormat` with `enumOrCorrupt<PayloadFormat>`, so a backup archive that contained a `"LEGACY_MD5"` tag row would now be refused as corrupt. The owner's preserved set lists **zero** `nfcTags`, so no such archive exists; and O2/O11 forbid adding a compatibility branch for one that does not. This is the ruling, not an oversight.

- [ ] **Step 1: Shrink the enum and the resolution**

`core/.../core/model/TagBinding.kt` line 3:

```kotlin
enum class PayloadFormat { V1 }
```

`core/.../core/usecase/ResolveTag.kt` — delete `data class UnknownLegacy(val key: String) : Resolution` from the sealed interface, and the `is TagPayload.LegacyMd5 -> …` arm from `run` if Task 5's Step 10 did not already remove it. `run` becomes:

```kotlin
    suspend fun run(payload: TagPayload): Resolution = when (payload) {
        is TagPayload.V1 -> known(PayloadFormat.V1, payload.tagId.value) ?: Resolution.UnknownV1(payload.tagId)
        is TagPayload.NewerVersion -> Resolution.NeedsNewerApp(payload.version)
        is TagPayload.Foreign, is TagPayload.Malformed, TagPayload.Empty -> Resolution.NotOurs(payload)
    }
```

`core/.../core/nfc/OverwritePolicy.kt` — the legacy arm is gone and the two product words become ServiceTag's:

```kotlin
    fun decide(existing: TagPayload, intended: TagId): OverwriteDecision = when (existing) {
        TagPayload.Empty -> OverwriteDecision.Proceed
        is TagPayload.V1 ->
            if (existing.tagId == intended) OverwriteDecision.Proceed
            else OverwriteDecision.Confirm("a different ServiceTag tag (${existing.tagId.value})")
        is TagPayload.NewerVersion -> OverwriteDecision.Confirm("a ServiceTag tag written by a newer app (format ${existing.version})")
        is TagPayload.Foreign -> OverwriteDecision.Confirm("foreign NDEF content (${existing.description})")
        is TagPayload.Malformed -> OverwriteDecision.Confirm("unreadable NDEF content (${existing.reason})")
    }
```

- [ ] **Step 2: Delete the second manifest filter**

`app/src/main/AndroidManifest.xml` — remove lines 89–93 whole, so `NfcDispatchActivity` declares exactly one filter:

```xml
        <activity
            android:name="com.loosecannon.servicetag.nfc.NfcDispatchActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:excludeFromRecents="true"
            android:theme="@android:style/Theme.Translucent.NoTitleBar">
            <intent-filter>
                <action android:name="android.nfc.action.NDEF_DISCOVERED" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:scheme="vnd.android.nfc" android:host="ext" android:path="${ndefTagPath}" />
            </intent-filter>
        </activity>
```

- [ ] **Step 3: Delete the legacy UI**

`ui/scan/ScanViewModels.kt`:
- `asTagResult()` loses `is Resolution.UnknownLegacy -> …`.
- `TagResult` loses `data class Legacy(val key: String) : TagResult`.
- `resolve()`'s payload `when` loses the `PayloadFormat.LEGACY_MD5.name -> TagPayload.LegacyMd5(key)` arm, and its resolution `when` loses `is Resolution.UnknownLegacy -> TagResult.Legacy(...)`.

`ui/scan/TagResultSheet.kt` — delete the whole `is TagResult.Legacy -> NfcSheet(...)` arm (the "Legacy tag" eyebrow, the "Rewrite in format v1"/"Bind as-is" actions and the `PayloadFormat.LEGACY_MD5.name` identity line go with it). If `NoteNfcIcons.History` is then unused in this file, drop the import.

`ui/scan/TagWriteController.kt:292` — delete `is TagPayload.LegacyMd5 -> "legacy tag ${p.key}"`.

`app/src/debug/.../DebugBackupActivity.kt` — the first seeded tag becomes `format = PayloadFormat.V1`. Its `payloadKey` is already `ids.newId()`, a canonical UUID, so `BindTag`'s V1 rule is satisfied.

- [ ] **Step 4: Rewrite the fixtures**

Every remaining mention is a fixture. Rules, applied file by file:

- `PayloadFormat.LEGACY_MD5` → `PayloadFormat.V1`, and where the row's `payloadKey` was an 8-hex string (`"63b37acf"`, `"deadbeef"`, `"cafebabe"`, `"0123456789abcdef"`) it becomes a canonical UUID, because a V1 key must be one. Use fixed literals, never a generator: `"11111111-1111-4111-8111-111111111111"`, `"22222222-2222-4222-8222-222222222222"`, and so on — a test that reads the same on every run.
- `TagPayload.LegacyMd5("…")` → `TagPayload.V1(TagId("<one of those UUIDs>"))`.
- `Resolution.UnknownLegacy("…")` → `Resolution.UnknownV1(TagId("…"))`.
- The string `"LEGACY_MD5"` in DAO and codec JSON fixtures → `"V1"`. `BackupCodecTest:308`'s corrupt-enum case (`replace("\"LEGACY_MD5\"", "\"LEGACY_SHA9\"")`) becomes `replace("\"V1\"", "\"V9\"")` — it tests that an unknown enum name is refused, and that claim survives the deletion.
- `BackupCodecTest:1005`'s fuzz pick `rng.pick(listOf("LEGACY_MD5", "V1"))` → `"V1"`.
- Tests whose *only* purpose was legacy preservation are deleted, not adapted (O11): `ResolveTagTest.unknownV1AndLegacyAreDistinct`, `ResolveTagTest`'s "a LEGACY row whose payload key happens to equal a v1 id" case, `TagBindingUseCasesTest.bindingAnUnknownLegacyTagGetsAFreshRowId`, `OverwritePolicyTest.legacyConfirms` and its `Confirm` message assertion, and `TagUseCasesRoomTest.bindingAnUnknownLegacyTagThenRescanningFindsIt`.
- `RestoreProofTest`'s legacy row keeps its *place* in the proof (it is there to cover "bound to an asset" and the `(format, key)` lookup) and becomes a V1 row with a UUID key; its KDoc loses the words "legacy MD5".

Find them all with:

```bash
git grep -niE 'legacy|md5' -- app core
```

Expected when this step is done: no output.

- [ ] **Step 5: Un-ignore the filter-count assertion**

`app/src/test/.../nfc/TagIdentityBindingTest.kt` — delete the `@Ignore("two filters until task 6 drops md5_short")` from `oneFilterAndOneDefinition`. There is one filter now, and this is the task that proves it.

- [ ] **Step 6: Run everything**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --console=plain`
Expected: PASS. The suites are smaller by the deleted legacy tests and by nothing else.

```bash
git grep -niE 'md5|legacy' -- app core            # expect: no output
grep -c 'NDEF_DISCOVERED' app/src/main/AndroidManifest.xml   # expect: 1
```

- [ ] **Step 7: Commit**

```bash
git add -A app core
git commit -m "drop md5_short: no decoder, no filter, no sheet, no enum member

a deliberate reversal of D6's 'kept permanently' promise, per O2/O3. the live
database has no tag rows at all, so there is nothing to lose here."
```

---

### Task 7 (§A.1 row 7): `servicetag://` replaces `notenfc://`

**Files:**
- Modify: `core/src/main/kotlin/com/loosecannon/servicetag/core/nfc/TagRoute.kt:10` (and its KDoc)
- Modify: `core/src/main/kotlin/com/loosecannon/servicetag/core/links/DeepLinkRoute.kt:17` (and its KDoc and two `Malformed` messages)
- Modify: `app/src/main/AndroidManifest.xml:56-58`
- Modify: the six `androidTest` files that hard-code the scheme — `ui/AppSmokeTest.kt`, `ui/AssetModelDeviceProofTest.kt`, `ui/AttachmentsDeviceProofTest.kt`, `ui/EditorsDeviceProofTest.kt`, `ui/JournalDeviceProofTest.kt`, `ui/JournalSmokeTest.kt`

**Interfaces:**
- Consumes: Task 3.
- Produces: `TagRoute.SCHEME == "servicetag"`, `DeepLinkRoute.SCHEME == "servicetag"`. `servicetag://asset/<uuid>`, `servicetag://link/<uuid>` and `servicetag://tag/<uuid>` are the three deep links; `notenfc://` resolves to nothing (O3).

- [ ] **Step 1: Rewrite the two constants and every mention of the old scheme**

```bash
git ls-files -- 'core/*.kt' 'app/*.kt' | xargs perl -pi -e 's/\bnotenfc:\/\//servicetag:\/\//g'
perl -pi -e 's/const val SCHEME: String = "notenfc"/const val SCHEME: String = "servicetag"/' \
  core/src/main/kotlin/com/loosecannon/servicetag/core/nfc/TagRoute.kt
perl -pi -e 's/const val SCHEME = "notenfc"/const val SCHEME = "servicetag"/' \
  core/src/main/kotlin/com/loosecannon/servicetag/core/links/DeepLinkRoute.kt
```

The first command catches the KDoc contracts (`` `notenfc://tag/<uuid>` ``, "The `notenfc://` contract"), the two `Malformed` reason strings in `DeepLinkRoute` and `TagRoute`, and all eleven `androidTest` occurrences in one pass.

- [ ] **Step 2: Rewrite the manifest's three `<data>` elements**

`app/src/main/AndroidManifest.xml` lines 51–58:

```xml
            <!-- servicetag://asset|link|tag: navigation only, validated by shape then by existence. -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="servicetag" android:host="asset" />
                <data android:scheme="servicetag" android:host="link" />
                <data android:scheme="servicetag" android:host="tag" />
            </intent-filter>
```

The `<queries>` block at lines 7–33 is **not** touched: `joplin`, `obsidian`, `logseq`, `http`, `https` and `content` are outbound link targets the allowlist checks for a handler, not this app's identity (§A.1 row 14 asks for exactly this review, and this is it).

- [ ] **Step 3: Verify**

```bash
git grep -c 'notenfc://' -- app core              # expect: no output
git grep -n 'servicetag://' -- app core | wc -l   # expect: 16 -- 5 in :core (TagRoute 2, DeepLinkRoute 3), 11 in androidTest
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain
```

Expected: PASS — `DeepLinkRouteTest` and `TagRoute`'s route tests are green against the new scheme (they build their URIs from `SCHEME`, so only the two files that hard-coded it needed the edit).

- [ ] **Step 4: Prove it on the emulator**

```bash
export ANDROID_SERIAL=emulator-5554
./gradlew :app:installDebug --console=plain
adb shell am start -W -a android.intent.action.VIEW -d 'servicetag://asset/nope'
adb shell am start -W -a android.intent.action.VIEW -d 'notenfc://asset/nope'
```

Expected: the first prints `Status: ok` into `com.loosecannon.servicetag/.MainActivity`; the second fails to resolve (`Error: Activity not started, unable to resolve Intent`), which is O3 holding.

- [ ] **Step 5: Commit**

```bash
git add -A app core
git commit -m "servicetag:// replaces notenfc://"
```

---

### Task 8 (§A.1 row 8): the label, the theme, `ServiceTagApp`, `ServiceTagRoot` and the user-facing words

**Files:**
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`
- Move: `app/src/main/kotlin/com/loosecannon/servicetag/NoteNfcApp.kt` → `ServiceTagApp.kt`
- Move: `app/src/main/kotlin/com/loosecannon/servicetag/ui/nav/NoteNfcApp.kt` → `ui/nav/ServiceTagRoot.kt`
- Move: `app/src/main/kotlin/com/loosecannon/servicetag/ui/components/NoteNfcIcons.kt` → `ServiceTagIcons.kt`
- Modify: both manifests, and every `.kt` file that names a `NoteNfc*` symbol or the old product word

**Interfaces:**
- Consumes: Tasks 3–7.
- Produces: `class ServiceTagApp : Application()`; `@Composable fun ServiceTagRoot(graph, deepLinks, snackbars)`; `ServiceTagTheme`, `ServiceTagIcons`, `ServiceTagSemanticColors`, `ServiceTagLightSemanticColors`, `ServiceTagDarkSemanticColors`, `ServiceTagTypography`, `ServiceTagShapes`, `ServiceTagColorScheme`, `ServiceTagThemePreview`; `@style/Theme.ServiceTag`; `app_name` = `ServiceTag`. Tasks 16 and 17 assert the label off the built APK and on the emulator.

**The collision, and the order that resolves it.** Two classes are called `NoteNfcApp`: the `Application` subclass and the nav-root composable (arch §4.6). A blanket rename would give both the same new name, so the composable is renamed **first**, to `ServiceTagRoot`, and only then does the blanket pass run (ratified P3).

- [ ] **Step 1: Rename the nav composable, alone**

```bash
git mv app/src/main/kotlin/com/loosecannon/servicetag/ui/nav/NoteNfcApp.kt \
       app/src/main/kotlin/com/loosecannon/servicetag/ui/nav/ServiceTagRoot.kt
perl -pi -e 's/\bNoteNfcApp\b/ServiceTagRoot/g' \
  app/src/main/kotlin/com/loosecannon/servicetag/ui/nav/ServiceTagRoot.kt
perl -pi -e 's/^import com\.loosecannon\.servicetag\.ui\.nav\.NoteNfcApp$/import com.loosecannon.servicetag.ui.nav.ServiceTagRoot/;
             s/\{ NoteNfcApp\(graph, deepLinks, messages\) \}/{ ServiceTagRoot(graph, deepLinks, messages) }/' \
  app/src/main/kotlin/com/loosecannon/servicetag/MainActivity.kt
git grep -n 'NoteNfcApp' -- app | cat
```

Expected: the only remaining `NoteNfcApp` mentions are the `Application` class, its four `(application as NoteNfcApp)` casts (`MainActivity`, `ShareActivity`, `NfcDispatchActivity`, `DebugBackupActivity`), the `app` helper in `AppSmokeTest`, the `TagIdentityDispatchTest` cast from Task 5, and the main manifest's `android:name`.

- [ ] **Step 2: Rename the two files that keep their identity in their name, then every symbol**

```bash
git mv app/src/main/kotlin/com/loosecannon/servicetag/NoteNfcApp.kt \
       app/src/main/kotlin/com/loosecannon/servicetag/ServiceTagApp.kt
git mv app/src/main/kotlin/com/loosecannon/servicetag/ui/components/NoteNfcIcons.kt \
       app/src/main/kotlin/com/loosecannon/servicetag/ui/components/ServiceTagIcons.kt

# every NoteNfc* symbol, ~170 occurrences across 40-odd files
git ls-files -- 'app/*.kt' 'core/*.kt' | xargs perl -pi -e 's/NoteNfc/ServiceTag/g'   # unanchored: LocalNoteNfcSemanticColors and resolveNoteNfcColorScheme carry the word mid-identifier

# the product word in user-facing strings, KDoc and test assertions -- but NOT the export
# file-name prefixes, which are task 10 and would break the backup tests if they moved now
git ls-files -- 'app/*.kt' 'core/*.kt' | xargs perl -pi -e 's/noteNFC(?!-(?:data|artifacts|\(|<))/ServiceTag/g'
```

The negative lookahead is load-bearing: `BackupSetNames` still writes `noteNFC-data-<stamp>.zip` until Task 10, and its three test fixtures must keep agreeing with it until then.

- [ ] **Step 3: The two resources and the two manifests**

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">ServiceTag</string>
</resources>
```

`app/src/main/res/values/themes.xml`:

```xml
<resources>
    <!--
      The window shell only. Compose paints everything inside it, so this theme exists to give the
      activity a no-action-bar window that does not drag AppCompat onto the classpath.
    -->
    <style name="Theme.ServiceTag" parent="android:Theme.Material.NoActionBar" />
</resources>
```

`app/src/main/AndroidManifest.xml` — the application class and the two theme references:

```xml
        android:name="com.loosecannon.servicetag.ServiceTagApp"
```

```xml
            android:theme="@style/Theme.ServiceTag">
```

(twice — `MainActivity` at line 46 and `ShareActivity` at line 68. `NfcDispatchActivity`'s translucent platform theme is unchanged.)

`app/src/debug/AndroidManifest.xml` — the label:

```xml
            android:label="ServiceTag Backup (debug)">
```

- [ ] **Step 4: Run everything, including the build**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:compileDebugAndroidTestKotlin --console=plain`
Expected: PASS, both APKs built. If Kotlin complains about an unresolved `Theme.NoteNfc`, a `@style` reference was missed — `git grep -n 'Theme.NoteNfc' -- app` must be empty.

- [ ] **Step 5: Read the label back out of the APK**

```bash
AAPT2="$HOME/Android/Sdk/build-tools/35.0.0/aapt2"
"$AAPT2" dump badging app/build/outputs/apk/debug/app-debug.apk | grep -E "^package:|application-label:|launchable-activity:"
```

Expected: `package: name='com.loosecannon.servicetag' versionCode='7' versionName='2.5'`, `application-label:'ServiceTag'`, and two launchable activities (`MainActivity` and the debug backup one, labelled `ServiceTag Backup (debug)`).

- [ ] **Step 6: Commit**

```bash
git add -A app core
git commit -m "ServiceTag label, theme, app class and nav root"
```

---

### Task 9 (§A.1 row 9): the ServiceTag launcher icon, from the owner's pack

**Files:**
- Copy in from the staged pack: `app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher{,_round}.xml`, `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher{,_round}.png`
- Delete: `app/src/main/res/mipmap-anydpi/ic_launcher.xml`, `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml`
- Delete: the ten inherited `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher{,_round}.webp`
- Modify: `app/src/main/AndroidManifest.xml` (add `android:roundIcon`)

**Interfaces:**
- Consumes: Task 8.
- Produces: `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` resolve to the finished ServiceTag icon — adaptive on API 26+, themed through a real `<monochrome>` layer on 33+, PNG rasters below that — and `<application>` declares both.

**Why this is not cosmetics** (review addition 3, target §3). Two apps that look identical on the launcher make every device observation in §D/§E and every NFC-allowlist entry ambiguous. A distinct icon *and* a distinct label per product is a coexistence requirement.

**The pack.** The owner supplied a finished icon pack, staged **read-only** at `~/Documents/Projects/AndroidStudioProjects/split-assets/ServiceTag/`. NoteTag's pack sits beside it and belongs to **Phase E** — do not touch it here. The pack's own `README.md` records the identity: a warm ivory `#F7F5EF` ground from the Apollo Service Binder palette, a high-contrast text-free mark in `#1F4E78`, separate adaptive foreground/background layers, a dedicated monochrome layer for Android 13+ themed icons, and legacy rasters for pre-adaptive launchers.

**What is copied, and what is not.** Only `app/src/main/res/` goes into the module. `source/icon_source.svg`, `source/play_store_512.png` and `preview/icon_1024.png` stay in the staging area and are **not** tracked in this repository: an app module holds shipped resources, not design masters. **No placeholder is produced by this task, and none is acceptable** — if the pack is missing, stop and ask the owner rather than drawing one.

- [ ] **Step 1: Copy the pack's resources over the module**

```bash
PACK="$HOME/Documents/Projects/AndroidStudioProjects/split-assets/ServiceTag"
test -f "$PACK/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" \
  || { echo "the ServiceTag icon pack is not staged where it should be"; exit 1; }
cp -R "$PACK/app/src/main/res/." app/src/main/res/
```

The trailing `/.` matters: it copies the *contents* of the pack's `res/` into the module's `res/`, rather than nesting a second `res/` inside it. The pack is read-only staging and nothing writes back to it.

```bash
find app/src/main/res -name 'ic_launcher*' | sort
```

Expected, 27 paths: the pack's 3 `drawable/ic_launcher_*.xml`, its 2 `mipmap-anydpi-v26/*.xml` and its 10 `mipmap-*dpi/*.png`, **plus** the 2 old `mipmap-anydpi/*.xml` and the 10 old `mipmap-*dpi/*.webp` that Step 2 removes. The pack's `ic_launcher_background.xml` and `ic_launcher_foreground.xml` have already overwritten the inherited ones in place (same names), and `ic_launcher_monochrome.xml` is new.

- [ ] **Step 2: Remove the two icon sets the pack replaces**

```bash
git rm app/src/main/res/mipmap-anydpi/ic_launcher.xml \
       app/src/main/res/mipmap-anydpi/ic_launcher_round.xml
git rm app/src/main/res/mipmap-mdpi/ic_launcher.webp   app/src/main/res/mipmap-mdpi/ic_launcher_round.webp \
       app/src/main/res/mipmap-hdpi/ic_launcher.webp   app/src/main/res/mipmap-hdpi/ic_launcher_round.webp \
       app/src/main/res/mipmap-xhdpi/ic_launcher.webp  app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp \
       app/src/main/res/mipmap-xxhdpi/ic_launcher.webp app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp \
       app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp
rmdir app/src/main/res/mipmap-anydpi 2>/dev/null || true
```

**Both deletions are required, and the second is the one that would otherwise break the build.** `mipmap-anydpi/ic_launcher.xml` and `mipmap-anydpi-v26/ic_launcher.xml` are *different* configuration qualifiers, so they would merely coexist confusingly — on `minSdk` 26 the `-v26` copy always wins — but `mipmap-hdpi/ic_launcher.webp` and `mipmap-hdpi/ic_launcher.png` are **the same resource in the same configuration**, which AAPT2 refuses as a duplicate. Removing the webp set is also what stops the retired product's art from staying reachable, which is the whole point of row 9.

- [ ] **Step 3: Declare the round icon**

`app/src/main/AndroidManifest.xml`, the `<application>` element:

```xml
    <application
        android:name="com.loosecannon.servicetag.ServiceTagApp"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name">
```

`android:roundIcon` is new: the manifest previously set only `android:icon`, so a circular-mask launcher masked the square icon instead of using the round asset the pack ships.

- [ ] **Step 4: Build, and prove no retired raster survives in the APK**

```bash
./gradlew :app:assembleDebug --console=plain
AAPT2="$HOME/Android/Sdk/build-tools/35.0.0/aapt2"
"$AAPT2" dump badging app/build/outputs/apk/debug/app-debug.apk | grep -E "^application:|application-icon"
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -i ic_launcher
```

Expected: BUILD SUCCESSFUL — **a duplicate-resource failure here means Step 2's webp removal was skipped**; the `application:` line reads `label='ServiceTag' icon='res/mipmap-anydpi-v26/ic_launcher.xml'` (or the same resource under AGP's own path spelling), with `application-icon-<density>` lines for the PNGs; and the zip listing shows `ic_launcher*` entries ending in `.png` and `.xml` and **not one `.webp`**.

- [ ] **Step 5: Install fresh on the emulator and look at it**

```bash
export ANDROID_SERIAL=emulator-5554
adb devices                       # exactly one emulator-* line, and no physical device
adb uninstall com.loosecannon.servicetag || true
./gradlew :app:installDebug --console=plain
adb shell dumpsys package com.loosecannon.servicetag | grep -iE 'versionName|icon=|labelRes'
adb shell monkey -p com.loosecannon.servicetag -c android.intent.category.LAUNCHER 1
adb exec-out screencap -p > "$HOME/servicetag-launcher.png"   # look at it, then delete it
```

Expected: `dumpsys` reports `versionName=2.5` and a non-zero `icon=` resource id; the launcher tile is the ivory-and-navy ServiceTag mark — not the retired green robot, and not a placeholder — with **ServiceTag** as the label under it. Delete the screenshot afterwards: it is a look-at-it step, written outside the repository, and nothing commits it.

- [ ] **Step 6: Run the gate**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A app/src/main/res app/src/main/AndroidManifest.xml
git status --short app/src/main/res
git commit -m "the real servicetag launcher icon, round icon and all"
```

`git status` before the commit must read: twelve deletions (two `mipmap-anydpi` XML, ten webp), twelve additions (ten PNG, two `mipmap-anydpi-v26` XML), one new `drawable/ic_launcher_monochrome.xml` and two modified drawables — and **no `.webp` left anywhere** under `app/src/main/res`.

---

### Task 10 (§A.1 row 10): `servicetag.db`, the `servicetag` prefs file, and the `ServiceTag-*` export prefixes

**Files:**
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/di/AppGraph.kt` (`DB_NAME`)
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/prefs/AppPrefs.kt:13`
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/backup/SafBackupSetIO.kt:60-67`
- Modify: `app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/AppSmokeTest.kt` (`PREFS_NAME`, the data-archive lookup)
- Modify: `app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/AttachmentsDeviceProofTest.kt` (six prefix literals)
- Modify: `app/src/test/kotlin/com/loosecannon/servicetag/ui/backup/BackupViewModelTest.kt` (seven prefix literals, plus one new test)

**Interfaces:**
- Consumes: Task 8.
- Produces: `DB_NAME = "servicetag.db"`; `getSharedPreferences("servicetag", …)`; `BackupSetNames.data(stamp) == "ServiceTag-data-$stamp.zip"` and `.artifacts(stamp) == "ServiceTag-artifacts-$stamp.zip"`.

**Why none of this can affect a restore.** A fresh package has no old database file to find, and **the importer never reads a file name** (arch §7.3, §7.7 item 4): `BackupIO` hands the codec bytes. The preserved `noteNFC-*` archives therefore import into ServiceTag unchanged, and Step 4 is the test that keeps it that way.

- [ ] **Step 1: Write the failing test for the new prefixes and the old files**

`app/src/test/kotlin/com/loosecannon/servicetag/ui/backup/BackupViewModelTest.kt` — change the shape regex at line 206 to

```kotlin
    private val stamped = Regex("""^ServiceTag-(data|artifacts)-\d{8}-\d{6}\.zip$""")
```

and add one test beside the existing restore cases:

```kotlin
    /**
     * The retired product's file names still import. The name is not part of the format: the
     * importer is handed bytes (`BackupIO.read()`), and `BackupSetNames` is only ever consulted
     * on the export side (arch §7.3). This is the regression guard for the prefix change, so the
     * owner's preserved `noteNFC-*` set stays importable after the rename.
     */
    @Test fun aPreservedRetiredPrefixArchiveStillImports() = runTest {
        val vm = viewModel()
        val sink = RecordingSink()
        vm.exportSet(sink).getOrThrow()
        val bytes = sink.files.entries.single { it.key.startsWith("ServiceTag-data-") }.value
        val assetsBefore = graph.assets.all().size

        // the very same bytes, as they sit in the owner's folder under the retired name
        val preserved = mapOf("noteNFC-data-20260915-101010.zip" to bytes)
        val report = vm.restoreData(MemoryIO(preserved.getValue("noteNFC-data-20260915-101010.zip"))).getOrThrow()

        // a wipe-and-load of our own export puts back exactly what was there, and the set id is
        // the one we exported: the file's name reached nothing at all.
        assertEquals(assetsBefore, graph.assets.all().size)
        assertNotNull(report.lastRestoredBackupSetId)
        assertEquals(report.lastRestoredBackupSetId, vm.state.value.lastRestoredBackupSetId)
    }
```

Every value this test compares comes from its own run, so there is no fixture count to keep in step with the seed: that is deliberate — the claim is that the **name** is irrelevant, not that the archive has a particular size. `graph.assets.all()` is `AssetRepository.all()`, which the file already reaches through `FakeGraph`; add `import org.junit.Assert.assertNotNull` if the file does not already have it.

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*BackupViewModelTest' --console=plain`
Expected: FAIL — the exported names still start with `noteNFC-`, so `stamped` does not match and `single { it.key.startsWith("ServiceTag-data-") }` throws `NoSuchElementException`.

- [ ] **Step 3: Rename the three values**

`app/src/main/kotlin/com/loosecannon/servicetag/di/AppGraph.kt`, in the private companion:

```kotlin
        const val DB_NAME = "servicetag.db"
```

`app/src/main/kotlin/com/loosecannon/servicetag/prefs/AppPrefs.kt:13`:

```kotlin
    private val prefs = context.applicationContext.getSharedPreferences("servicetag", Context.MODE_PRIVATE)
```

`app/src/main/kotlin/com/loosecannon/servicetag/backup/SafBackupSetIO.kt`:

```kotlin
/** `ServiceTag-data-<stamp>.zip` and `ServiceTag-artifacts-<stamp>.zip`, stamp = local `yyyyMMdd-HHmmss`. */
object BackupSetNames {
    fun stamp(at: Long): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(at))

    fun data(stamp: String): String = "ServiceTag-data-$stamp.zip"

    fun artifacts(stamp: String): String = "ServiceTag-artifacts-$stamp.zip"
}
```

- [ ] **Step 4: Move the fixtures with it**

```bash
perl -pi -e 's/\bnoteNFC-(data|artifacts)/ServiceTag-$1/g' \
  app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/AppSmokeTest.kt \
  app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/AttachmentsDeviceProofTest.kt \
  app/src/test/kotlin/com/loosecannon/servicetag/ui/backup/BackupViewModelTest.kt
perl -pi -e 's/^private const val PREFS_NAME = "notenfc"$/private const val PREFS_NAME = "servicetag"/' \
  app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/AppSmokeTest.kt
```

Then put back the **one** deliberate old name the new test needs: `aPreservedRetiredPrefixArchiveStillImports`'s two `"noteNFC-data-20260915-101010.zip"` literals, which the blanket pass will have rewritten. They are the test's whole point.

```bash
git grep -n 'noteNFC-' -- app | cat
```

Expected: exactly the two literals inside that one test, and nothing else.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --console=plain`
Expected: PASS, including the new test.

- [ ] **Step 6: Commit**

```bash
git add -A app
git commit -m "servicetag.db, servicetag prefs, ServiceTag-* exports"
```

---

### Task 11 (§A.1 row 11): the Room schema export directory follows the package

**Files:**
- Move: `app/schemas/com.loosecannon.notenfc.data.room.AppDatabase/` → `app/schemas/com.loosecannon.servicetag.data.room.AppDatabase/` (`1.json`…`5.json`)
- Modify: `app/src/test/kotlin/com/loosecannon/servicetag/data/room/MigrationTestSupport.kt:96`

**Interfaces:**
- Consumes: Tasks 2–3 (the `AppDatabase` FQN is already `com.loosecannon.servicetag.data.room.AppDatabase`).
- Produces: the five exported schemas under the new directory, with `5.json`'s `identityHash` **unchanged**. The eleven migration tests read them from there.

**The ruling on history.** `git mv` the five JSON files rather than deleting the directory and re-exporting from scratch: the migration-ladder tests use `1.json`…`4.json` as *fixtures* for hops that Room can no longer generate, so deleting them would delete test inputs, and `git mv` keeps `git log --follow` working on each one. Then re-export, so the tree is what the build actually produces, and compare.

**The gate that stops the phase.** `5.json`'s `identityHash` is a structural hash of the schema and is independent of the package (arch §7.1). It must be **byte-identical** afterwards: `157988f1aada363f37590a6735a3be36`. If it changes, **stop** — the schema drifted, and the phone's database will not open against it.

- [ ] **Step 1: Record the hash before touching anything**

```bash
jq -r '.database.identityHash, .database.version' \
  app/schemas/com.loosecannon.notenfc.data.room.AppDatabase/5.json
```

Expected:

```
157988f1aada363f37590a6735a3be36
5
```

- [ ] **Step 2: Move the directory and point the tests at it**

```bash
git mv app/schemas/com.loosecannon.notenfc.data.room.AppDatabase \
       app/schemas/com.loosecannon.servicetag.data.room.AppDatabase
perl -pi -e 's/schemas\/com\.loosecannon\.notenfc\.data\.room\.AppDatabase/schemas\/com.loosecannon.servicetag.data.room.AppDatabase/' \
  app/src/test/kotlin/com/loosecannon/servicetag/data/room/MigrationTestSupport.kt
git grep -n 'com\.loosecannon\.notenfc' -- app/src/test app/schemas | cat
```

Expected: no output — this was the last `com.loosecannon.notenfc` string in the Kotlin sources.

- [ ] **Step 3: Re-export and compare**

```bash
./gradlew :app:kspDebugKotlin --console=plain
git status --short app/schemas
jq -r '.database.identityHash' app/schemas/com.loosecannon.servicetag.data.room.AppDatabase/5.json
```

Expected: `git status` shows the five renames and **no content modification** to `5.json`; the hash still prints `157988f1aada363f37590a6735a3be36`. If `5.json` shows as modified, `git diff` it: a formatting-only difference is acceptable and is committed; any change under `.database.entities` or to `identityHash` is the stop condition above.

- [ ] **Step 4: Run every migration hop**

Run: `./gradlew :app:testDebugUnitTest --tests '*Migration*' --console=plain`
Expected: PASS — `Migration1To2`, `1To3`, `1To4`, `1To5`, `2To3`, `3To4`, `4To5` and the full ladder, all finding their fixtures at the new path.

Run: `./gradlew :core:test :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A app/schemas app/src/test
git commit -m "re-export the room schemas under the new package (identityHash unchanged)"
```

---

### Task 12 (§A.1 row 12): a sibling's tag decodes as foreign

**Files:**
- Create: `core/src/test/kotlin/com/loosecannon/servicetag/core/nfc/NdefEnvelopeIsolationTest.kt`

**Interfaces:**
- Consumes: Task 5's `TagIdentity` and `NdefCodec`, Task 6's `TagPayload` (no `LegacyMd5`).
- Produces: nothing other code uses. This is the test that stops ServiceTag adopting a NoteTag tag.

**Why it needs its own test even though the payloads differ.** Both products' bodies may be valid payloads for the other's parser, so any path that read a payload before checking the type could present a NoteTag tag as a plausible ServiceTag target (C8, target §4.3 invariant 1). The type gate is the whole defence, and `Recognised`-style APIs that hand back only the body are why it cannot be bypassed. The mirror-image test in NoteTag's direction is Phase E's.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.loosecannon.servicetag.core.nfc

import com.loosecannon.servicetag.core.model.TagId
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Sibling isolation, ServiceTag's direction (target §4.3 invariant 1, C8). A NoteTag record must
 * come back `Foreign` even when its bytes would parse: the type gate runs before any body parse,
 * and a product never reads a payload it did not type-check.
 *
 * NoteTag's own v1 envelope is `version|kind|flags|body` on `com.loosecannon.notetag:tag` (O13,
 * O14). This test writes those bytes by hand — it must not import anything of NoteTag's, which
 * does not exist in this repository and never will.
 */
class NdefEnvelopeIsolationTest {
    private val ours = TagIdentity("com.loosecannon.servicetag", "tag", "com.loosecannon.servicetag")
    private val codec = NdefCodec(ours)

    private fun external(type: String, payload: ByteArray) =
        NdefRecordData(NdefCodec.TNF_EXTERNAL_TYPE, type.toByteArray(Charsets.US_ASCII), payload)

    /** A NoteTag JOPLIN_NOTE record: version 1, kind 0x01, flags 0, then 16 raw id bytes. */
    @OptIn(ExperimentalStdlibApi::class)
    private val noteTagJoplinRecord = external(
        "com.loosecannon.notetag:tag",
        byteArrayOf(0x01, 0x01, 0x00) + "123e4567e89b12d3a456426614174000".hexToByteArray(),
    )

    @Test fun aNoteTagRecordIsForeign() {
        val decoded = codec.decode(listOf(noteTagJoplinRecord))
        assertIs<TagPayload.Foreign>(decoded)
        // kotlin.test puts the message LAST, unlike JUnit's Assert -- `:core` is kotlin.test.
        assertTrue(
            decoded.description.contains("com.loosecannon.notetag:tag"),
            "the refusal names the type it saw, so the UI can say what the tag is",
        )
    }

    /**
     * The dangerous case: a sibling record whose body is byte-for-byte a valid ServiceTag v1
     * payload. Only the type gate can tell these apart, and it must.
     */
    @Test fun aSiblingRecordCarryingOurOwnPayloadIsStillForeign() {
        val ourPayload = codec.v1Record(TagId("123e4567-e89b-12d3-a456-426614174000")).payload
        assertIs<TagPayload.Foreign>(codec.decode(listOf(external("com.loosecannon.notetag:tag", ourPayload))))
    }

    /** And the reverse framing check: our type with a sibling's body is ours, and malformed. */
    @Test fun ourTypeWithASiblingBodyIsOursAndMalformed() {
        val noteTagBody = byteArrayOf(0x01, 0x01, 0x00) + ByteArray(16)
        assertIs<TagPayload.Malformed>(codec.decode(listOf(external(ours.externalType, noteTagBody))))
    }

    /** A NoteTag AAR in second place changes nothing: the platform reads the first record. */
    @Test fun aSiblingAarDoesNotMakeATagOurs() {
        val siblingAar = external("android.com:pkg", "com.loosecannon.notetag".toByteArray(Charsets.US_ASCII))
        assertIs<TagPayload.Foreign>(codec.decode(listOf(noteTagJoplinRecord, siblingAar)))
    }
}
```

- [ ] **Step 2: Run it**

Run: `./gradlew :core:test --tests '*NdefEnvelopeIsolationTest' --console=plain`
Expected: PASS on the first run — Task 5's codec already gates on the exact type, and this is the test that pins that behaviour so no later refactor can loosen it. If any case fails, the type gate is wrong and `NdefCodec.decode` is what to fix.

`ourTypeWithASiblingBodyIsOursAndMalformed` is the one to read twice: a 19-byte body under *our* type is ours, and a payload-length failure, not `Foreign`. That asymmetry is the invariant.

- [ ] **Step 3: Run the gate**

Run: `./gradlew :core:test :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add core/src/test/kotlin/com/loosecannon/servicetag/core/nfc/NdefEnvelopeIsolationTest.kt
git commit -m "a notetag record decodes as foreign, even carrying our own payload"
```

---

### Task 13 (§A.1 row 13): the trampoline's wire vocabulary is the enum

**Files:**
- Create: `app/src/main/kotlin/com/loosecannon/servicetag/ui/scan/TagResultWire.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/MainActivity.kt:84`
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/ui/scan/ScanViewModels.kt` (`FORMAT_NONE` moves; `resolve()` uses the new parser)
- Modify: `app/src/main/kotlin/com/loosecannon/servicetag/nfc/NfcDispatchActivity.kt` (import)
- Create: `app/src/test/kotlin/com/loosecannon/servicetag/ui/scan/TagResultWireTest.kt`

**Interfaces:**
- Consumes: Task 6's `PayloadFormat { V1 }`.
- Produces:

```kotlin
// app/.../ui/scan/TagResultWire.kt
object TagResultWire {
    const val FORMAT_NONE: String = "NONE"
    fun formatOf(payload: TagPayload): String          // PayloadFormat.V1.name, or FORMAT_NONE
    fun payloadOf(format: String, key: String): TagPayload?   // null = not ours, do not resolve
}
```

`FORMAT_NONE` keeps its name and its value so `NfcDispatchActivity`'s call sites read the same; it moves file so the three wire words have one owner.

**What is actually wrong today.** `MainActivity.kt:84` hard-codes `Route.TagResult("V1", …)` instead of `PayloadFormat.V1.name`, and the matching parse lives in `TagResultViewModel`. With `"LEGACY_MD5"` deleted in Task 6 the vocabulary is down to two words, and this task gives both one owner so the trampoline↔renderer contract stops being loose literals (arch §4.6, §6.3).

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/com/loosecannon/servicetag/ui/scan/TagResultWireTest.kt`:

```kotlin
package com.loosecannon.servicetag.ui.scan

import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.nfc.TagPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The (format, key) pair is what survives process death between the NFC trampoline and the sheet,
 * so it is a wire format with exactly two words in it. One owner, both directions, one test.
 */
class TagResultWireTest {

    private val id = TagId("123e4567-e89b-12d3-a456-426614174000")

    @Test fun aV1PayloadRoundTripsThroughTheEnumName() {
        val format = TagResultWire.formatOf(TagPayload.V1(id))
        assertEquals(PayloadFormat.V1.name, format)
        assertEquals(TagPayload.V1(id), TagResultWire.payloadOf(format, id.value))
    }

    @Test fun everythingElseIsTheNotOursWord() {
        assertEquals(TagResultWire.FORMAT_NONE, TagResultWire.formatOf(TagPayload.Empty))
        assertEquals(TagResultWire.FORMAT_NONE, TagResultWire.formatOf(TagPayload.Foreign("tnf=1 type=U")))
        assertEquals(TagResultWire.FORMAT_NONE, TagResultWire.formatOf(TagPayload.Malformed("short")))
        assertEquals(TagResultWire.FORMAT_NONE, TagResultWire.formatOf(TagPayload.NewerVersion(2)))
    }

    /**
     * The short circuit: an unknown word never becomes a payload, so nothing downstream of it can
     * reach `ResolveTag`. The sheet shows the key as prose instead (arch §5.12).
     */
    @Test fun anUnknownFormatIsNotAPayload() {
        assertNull(TagResultWire.payloadOf(TagResultWire.FORMAT_NONE, "empty tag"))
        assertNull(TagResultWire.payloadOf("LEGACY_MD5", "63b37acf"))
        assertNull(TagResultWire.payloadOf("", ""))
        assertNull(TagResultWire.payloadOf("v1", id.value))   // the enum name, exactly, or nothing
    }

    /** A V1 word with a key that is not a tag id is still not a payload. */
    @Test fun aV1WordNeedsACanonicalId() {
        assertNull(TagResultWire.payloadOf(PayloadFormat.V1.name, "not-a-uuid"))
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*TagResultWireTest' --console=plain`
Expected: FAIL — compilation error, `TagResultWire` unresolved.

- [ ] **Step 3: Write the owner**

`app/src/main/kotlin/com/loosecannon/servicetag/ui/scan/TagResultWire.kt`:

```kotlin
package com.loosecannon.servicetag.ui.scan

import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.nfc.NdefCodec
import com.loosecannon.servicetag.core.nfc.TagPayload

/**
 * The two words the (format, key) extras can carry, and the only place either is written or read.
 *
 * The pair — not a `Resolution` object — is what crosses from `NfcDispatchActivity` to
 * `MainActivity`, because it survives process death and a backup import while an in-memory
 * resolution does not.
 */
object TagResultWire {

    /** Not a payload format: "this tag is not ours, and the key is why". */
    const val FORMAT_NONE: String = "NONE"

    /** The word for a payload the sheet can resolve; [FORMAT_NONE] for everything else. */
    fun formatOf(payload: TagPayload): String = when (payload) {
        is TagPayload.V1 -> PayloadFormat.V1.name
        is TagPayload.NewerVersion, is TagPayload.Foreign, is TagPayload.Malformed, TagPayload.Empty -> FORMAT_NONE
    }

    /**
     * The payload those extras named, or null when there is nothing to resolve — an unknown word,
     * or a key that is not a tag id. A null short-circuits to the not-ours sheet and `ResolveTag`
     * is never called (arch §5.12).
     */
    fun payloadOf(format: String, key: String): TagPayload? = when (format) {
        PayloadFormat.V1.name -> runCatching { NdefCodec.requireCanonicalUuid(TagId(key)) }
            .getOrNull()?.let { TagPayload.V1(TagId(key)) }
        else -> null
    }
}
```

- [ ] **Step 4: Point the three call sites at it**

`MainActivity.kt` — line 84 inside `routeFrom`:

```kotlin
            is DeepLink.Tag -> when (val payload = link.payload) {
                is TagPayload.V1 -> Route.TagResult(TagResultWire.formatOf(payload), payload.tagId.value)
                else -> malformed()
            }
```

and add `import com.loosecannon.servicetag.ui.scan.TagResultWire`.

`ScanViewModels.kt`:
- delete `const val FORMAT_NONE: String = "NONE"` and its KDoc from the top of the file;
- `asTagResult()`'s two `FORMAT_NONE` uses become `TagResultWire.FORMAT_NONE`;
- `TagResultViewModel.resolve()`'s payload block becomes

```kotlin
        val payload = TagResultWire.payloadOf(format, key)
        if (payload == null) {
            // The key is the prose reason the tag could not be used, not an id.
            _state.value = TagResult.NotOurs(key)
            return
        }
```

`NfcDispatchActivity.kt` — `import com.loosecannon.servicetag.ui.scan.FORMAT_NONE` becomes `import com.loosecannon.servicetag.ui.scan.TagResultWire`, and its three `FORMAT_NONE` uses become `TagResultWire.FORMAT_NONE`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --console=plain`
Expected: PASS, with five new tests.

```bash
git grep -n '"V1"\|"NONE"\|"LEGACY_MD5"' -- app/src/main app/src/debug | cat
```

Expected: exactly one hit — `FORMAT_NONE`'s own definition in `TagResultWire.kt`. Every other wire word now comes from `PayloadFormat`.

- [ ] **Step 6: Commit**

```bash
git add -A app
git commit -m "the trampoline wire vocabulary is the enum, with one owner"
```

---

### Task 14 (§A.1 row 14): the last identity carriers, and three deliberate non-changes

**Files:**
- Modify: `.gitignore` (the comment naming the signing directory)
- Verify (no edit): `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`'s `<queries>` block, `ui/settings/SettingsScreen.kt:241`
- Verify (already edited by Task 8): `ui/settings/SettingsScreen.kt:64` `PROJECT_URL`
- Verify: the three files of `app/src/debug`

**Interfaces:**
- Consumes: Tasks 2–13.
- Produces: nothing new. This task's product is the *record* that the remaining inventory rows were looked at, plus the one real edit.

- [ ] **Step 1: The one edit — `.gitignore`'s signing comment**

```
# signing material never belongs in the repo (it lives in ~/.config/servicetag)
keystore.properties
*.jks
*.keystore
```

The three patterns are unchanged; only the comment moves to the new directory, matching Task 2's `keystoreProps` path. (The *retired* key keeps its own directory name, `~/.config/notenfc/`, because §12 forbids touching that keystore — that is the naming rule's third role, and it lives in NoteTag's README, not here.)

- [ ] **Step 2: `proguard-rules.pro` — nothing to change, and say so**

```bash
git grep -niE 'notenfc|loosecannon|keep class' -- app/proguard-rules.pro | cat
```

Expected: no output. The file is the unmodified AGP template with every rule commented out: no `-keep` for a package, no class name, no identity of any kind. §A.1 row 14's "proguard-rules.pro package references" describes a file that has none. Record it as reviewed, unchanged.

- [ ] **Step 3: The `<queries>` block — reviewed, left alone**

```bash
sed -n '6,33p' app/src/main/AndroidManifest.xml
```

Six intents: `joplin`, `obsidian`, `logseq`, `http`, `https`, and `content` with `*/*`. Every one is an **outbound** link target the allowlist asks the platform about (D3 §10, API 30+ package visibility), not this app's identity. Nothing here changes under any product name. Record it as reviewed, unchanged — this is the review §A.1 row 14 asks for.

- [ ] **Step 4: The version row and the project link in Settings**

```bash
git grep -n 'BuildConfig.VERSION_NAME\|PROJECT_URL' -- app/src/main | cat
```

Expected:

```
app/src/main/kotlin/com/loosecannon/servicetag/di/AppGraph.kt: ... BuildConfig.VERSION_NAME ...
app/src/main/kotlin/com/loosecannon/servicetag/ui/settings/SettingsScreen.kt:64: private const val PROJECT_URL = "https://github.com/GonzRon/ServiceTag"
app/src/main/kotlin/com/loosecannon/servicetag/ui/settings/SettingsScreen.kt:241: value = BuildConfig.VERSION_NAME,
```

Three facts to record. The Settings → About "Version" row and `BackupManifest.appVersion` both read `BuildConfig.VERSION_NAME`, which Task 2 already moved to `"2.5"`: **derived, no edit due**. There are **no notification channels** anywhere — `git grep -n 'NotificationChannel\|CHANNEL_ID' -- app core` is empty, and issue #21's local reminders are Phase 3 — so §11's "migrate channels deliberately" rule has nothing to act on; it is recorded here so the *next* phase that adds one knows the rule exists (target §3). And `PROJECT_URL` — an identity carrier that is **not** in §A.1's inventory or arch §4.6, found while doing this task — was rewritten by Task 8's blanket prose pass to `https://github.com/GonzRon/ServiceTag`. That name does not exist on the remote until §B.2 renames the repository, and this code is not pushed until §B.3a, which is after it; GitHub then redirects either way. Correct as written, and noted in the evidence file (Task 17).

- [ ] **Step 5: The debug source set — all three files**

```bash
git ls-files app/src/debug
git grep -niE 'notenfc|NoteNfc' -- app/src/debug | cat
```

Expected: three files — `AndroidManifest.xml`, `res/layout/activity_debug_backup.xml`, `kotlin/com/loosecannon/servicetag/debug/DebugBackupActivity.kt` — and **no output** from the grep. The manifest's FQN was Task 4, its label Task 8, the activity's package Task 3, its `NoteNfcApp` cast Task 8 and its legacy seed row Task 6. The layout carries no identity at all (it never did; §A.1 row 14's "three files, not one" is about the *count*, and this is the count).

- [ ] **Step 6: Run the gate**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add .gitignore
git commit -m "the signing dir comment follows the key; proguard and queries need nothing"
```

---

### Task 15 (§A.1 row 15): the README stops reproducing the signer

**Files:**
- Modify: `README.md` — line 1, 44, 68, 89, 105, 113–125, 128–138

**Interfaces:**
- Consumes: Tasks 2–14.
- Produces: a `README.md` with no `noteNFC`, no `notenfc`, no `md5_short` and no certificate fingerprint. Task 16's whole-phase grep covers `README.md`, so this is what makes it pass.

**Scope, against §H.** §H.2 owns the README's *full* rewrite — the title, the product framing, "Related projects", `--recurse-submodules`, the submodule mechanism — and that happens in the docs phase, after the library exists. This task is the **hygiene slice** §A.1 row 15 names, plus exactly the lines Task 16's grep would otherwise trip over. Both facts are true at once: the fingerprint must leave the repository now (review correction 11), and the phase's own gate must be reachable.

**What replaces the fingerprint.** A pointer. The certificate SHA-256 of the retired key is already recorded in `docs/design/phase-1a-evidence.md`, which is history and is not edited. Fingerprints are public keys, so this is hygiene — a repository should not reproduce its signer's DN and full digest in its front page — not a leak response.

- [ ] **Step 1: Rewrite the Signing section**

`README.md`, replacing lines 113–125 (the section body and the fenced fingerprint):

```markdown
## Signing

Release builds pick up `~/.config/servicetag/keystore.properties` if it exists; when it is
absent the release build is simply unsigned and everything else still works. The file is
plain `storeFile` / `storePassword` / `keyAlias` / `keyPassword` and points at a keystore
outside the repository. Neither file is ever in the repo (`.gitignore` covers
`keystore.properties`, `*.jks`, `*.keystore`).

The release certificate's SHA-256 fingerprint is recorded once, in
`docs/architecture/product-split-evidence.md`. It is not reproduced here: a fingerprint is a
public key, but a repository's front page is not where a signer's identity belongs.

Back the keystore up somewhere outside the repo. Lose it and the app can never be updated
in place again — a new key means a new install for every user.
```

- [ ] **Step 2: Replace the cutover section with a pointer**

Replacing lines 127–138 (`## Cutover from the old package` and its paragraph):

```markdown
## Where this app came from

This repository was a combined note-utility and maintenance product before the 2026 product
split; the maintenance product kept the history and became ServiceTag, and the note utility was
reconstructed as its own project. What moved, what stayed, what the identities are now and how
the data migrated are all in `docs/architecture/product-split-migration.md`. Everything under
`docs/design/` predates the split and is history.
```

- [ ] **Step 3: The four remaining product words**

- line 1: `# noteNFC` → `# ServiceTag`
- line 44: "…to noteNFC and you get a card…" → "…to ServiceTag and you get a card…"
- line 68: the sentence about `md5_short` records being recognised as legacy tags is **deleted** (O2/O3: no product understands that type any more). If that leaves a one-line paragraph, remove the paragraph.
- line 89: `git clone <this repo> && cd noteNFC` → `git clone <this repo> && cd ServiceTag`
- line 105: **noteNFC Backup (debug)** → **ServiceTag Backup (debug)**

- [ ] **Step 4: Verify**

```bash
git grep -niE 'notenfc|md5_short|CN=|SHA-256:' -- README.md | cat
```

Expected: no output.

```bash
git grep -c 'CN=noteNFC' | cat
```

Expected: only `docs/` files — the archaeology, the migration runbook, the seven phase-evidence files and one Phase-1A plan. Those are history (§30, §H.1) and are left exactly as they are.

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "readme points at the fingerprint instead of printing it"
```

---

### Task 16: whole-phase verification

**Files:**
- No source file changes. If a check fails, fix it in the task that owns it and re-run this one.

**Interfaces:**
- Consumes: Tasks 1–15.
- Produces: the evidence Task 17 and gate 5 quote — the greps, the badging line, four green Gradle tasks, a green instrumented suite and a fresh-install smoke.

**Why the repository-wide grep only runs here (G3).** In task order this assertion is expected to fail after Tasks 3 and 4 and to pass only from Task 7 onward: the NFC type constants (Tasks 5, 6), the manifest filter path (Task 6) and the deep-link literals (Task 7) each legitimately still carry the old string until their own task runs. Treating it as a per-task gate would either block Task 3 or invite someone to edit constants out of order.

- [ ] **Step 1: The repository-wide zero-hit grep**

```bash
git grep -lIE 'com\.loosecannon\.notenfc|notenfc://|md5_short|noteNFC|NoteNfc' \
  -- app core gradle settings.gradle.kts README.md
```

Expected: **no output**, except the one line this plan permits — `app/src/test/kotlin/com/loosecannon/servicetag/ui/backup/BackupViewModelTest.kt`, whose retired-prefix import test names `noteNFC-data-20260915-101010.zip` on purpose (Task 10). Confirm that is the only reason:

```bash
git grep -nIE 'noteNFC' -- app core gradle settings.gradle.kts README.md
```

Expected: exactly the two literals inside `aPreservedRetiredPrefixArchiveStillImports`.

Hits under `docs/` are **history** and are left alone (§30, §H).

**The `-I` flag, and why it is not a loophole.** `app/release/app-release.apk` is tracked, and it is the 2024 shipped artifact whose blob is byte-identical at `3a3c69a` and `c84b881` (review addition 8). It contains the retired package name inside its binary, it is history, and ServiceTag keeps it — only NoteTag's reconstruction deletes it (§A.2). `-I` (skip binary files) is what lets a text assertion be about text. Prove that the APK is the only binary hit:

```bash
git grep -lE 'noteNFC' -- app core | cat        # expect: app/release/app-release.apk and nothing else
```

- [ ] **Step 2: The identity is true of the built APK, not just of the source**

```bash
./gradlew clean :core:test :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --console=plain
AAPT2="$HOME/Android/Sdk/build-tools/35.0.0/aapt2"
"$AAPT2" dump badging app/build/outputs/apk/debug/app-debug.apk \
  | grep -E "^package:|application-label:|launchable-activity:|uses-permission"
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -ci 'ic_launcher.*\.webp'   # expect: 0
```

Expected: four green Gradle tasks, `0` retired rasters in the APK, and

```
package: name='com.loosecannon.servicetag' versionCode='7' versionName='2.5' ...
launchable-activity: name='com.loosecannon.servicetag.MainActivity'  label='' icon=''
launchable-activity: name='com.loosecannon.servicetag.debug.DebugBackupActivity'  label='ServiceTag Backup (debug)' ...
application-label:'ServiceTag'
uses-permission: name='android.permission.NFC'
```

- [ ] **Step 3: The merged manifest carries one filter, on the new type**

```bash
MERGED="$(find app/build/intermediates -name AndroidManifest.xml -path '*ebug*' | head -1)"
grep -oE 'android:(path|scheme)="[^"]*"' "$MERGED" | sort -u
grep -c 'NDEF_DISCOVERED' "$MERGED"
grep -c 'notenfc' "$MERGED"
```

Expected: `android:path="/com.loosecannon.servicetag:tag"`, `android:scheme="servicetag"`, `android:scheme="vnd.android.nfc"` and the six `<queries>` schemes; `1`; and `0` — **now** the "no `notenfc` substring in the merged manifest" claim of §A.1 row 4 is true, and this is where it belongs.

- [ ] **Step 4: The whole instrumented suite, on the emulator and nowhere else**

```bash
export ANDROID_SERIAL=emulator-5554
adb devices
```

Expected: exactly one `emulator-5554  device` line **and no physical device**. If a phone is listed, pin every adb and gradle command to `ANDROID_SERIAL=emulator-5554` / `-s emulator-5554` and never address the phone's serial.

Run: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --console=plain`
Expected: PASS — `AppSmokeTest`, `DeepLinkSmokeTest`, `NavigationSmokeTest`, `JournalSmokeTest`, `ComponentsSmokeTest`, `EditorsDeviceProofTest`, `JournalDeviceProofTest`, `AssetModelDeviceProofTest`, `AttachmentsDeviceProofTest`, `SafTreeAttachmentStoreContractTest`, and the new `TagIdentityDispatchTest`. The deep-link tests now drive `servicetag://`, and the label assertions now read `ServiceTag`.

- [ ] **Step 5: A fresh install, looked at**

```bash
export ANDROID_SERIAL=emulator-5554
adb uninstall com.loosecannon.servicetag || true
adb uninstall com.loosecannon.notenfc || true          # nothing of the retired package survives here
./gradlew :app:installDebug --console=plain
adb shell monkey -p com.loosecannon.servicetag -c android.intent.category.LAUNCHER 1
adb shell pm list packages | grep loosecannon
adb shell run-as com.loosecannon.servicetag ls databases
adb shell run-as com.loosecannon.servicetag ls shared_prefs
```

Expected: `package:com.loosecannon.servicetag` and nothing else from `loosecannon`; `databases` lists `servicetag.db` (plus its `-wal`/`-shm`); `shared_prefs` lists `servicetag.xml` once the app has written a preference. The launcher tile is the new icon and the label reads ServiceTag.

The fresh install holds **no persisted SAF tree grant**, so Settings → Attachment storage reads as unconfigured and attaching is refused with a path to Settings. That is correct and expected: a persisted grant is scoped to the calling application, so a new `applicationId` holds none of the old package's grants — which is the load-bearing reason for §15's ordering, is observed on the emulator at §C.8a, and is acquired on the phone at §C.3. Nothing about it is Phase D's business.

- [ ] **Step 6: The clean-checkout build (§26)**

```bash
CLEAN="$(mktemp -d)"
git clone --no-local --branch product-split . "$CLEAN/ServiceTag"
cd "$CLEAN/ServiceTag" && ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain
```

Expected: BUILD SUCCESSFUL from a directory that has never held the project. The clone-from-URL and second-workstation proofs belong to §B.6; this is the local one.

- [ ] **Step 7: Commit the verification record**

Nothing to add — this task changes no file. Record the run in the ledger line Task 17 writes, and go straight to Task 17. If you want a marker in the history, make it an empty commit rather than inventing a file:

```bash
git commit --allow-empty -m "phase d verification: no notenfc left in app or core"
```

---

### Task 17 (§A.1.1): the §21 regression pass on the emulator, and the evidence

**Files:**
- Create: `app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/NfcIdentityDeviceProofTest.kt`
- Create: `docs/architecture/product-split-evidence.md`
- (Controller's split ledger is NOT touched by the implementer: report the one-line Phase D summary in your report file and the controller records it.)

**Interfaces:**
- Consumes: Tasks 1–16.
- Produces: gate 5's entry condition — §A.1's fifteen tasks complete, the §21 regression pass green, the identity table true of the built APK, `5.json`'s `identityHash` unchanged, the clean-checkout build green.

**What a §21 pass can and cannot be in this phase.** The owner's rule is to vet everything possible on the emulator and use the phone only when absolutely necessary; the emulator has no NFC radio. So the NFC rows of §21 are exercised as **synthetic `NDEF_DISCOVERED` intents** through the real dispatch activity, real codec and real database — which is exactly the reassignment G4 made ("ServiceTag standalone-link resolution → emulator regression / synthetic NFC intent, same type + dispatch activity"). The rows that need a radio and a physical tag — format, measured `Ndef.maxSize`, capacity refusal, read-back, lock-last — are **§D Session 1's four taps and §E Session 2's eight**, and they are not attempted here. The phone is not touched in this phase at all.

§21's list, and where each row is discharged:

| §21 row | Discharged by |
|---|---|
| the core maintenance product (assets, journal, measurements, profiles, templates) | `:core:test` + `:app:testDebugUnitTest` + `AssetModelDeviceProofTest`, `JournalDeviceProofTest`, `EditorsDeviceProofTest` |
| NFC asset binding, ambient resolution, standalone links | `NfcIdentityDeviceProofTest` (below) + `TagUseCasesRoomTest` |
| foreign / malformed safety | `NfcIdentityDeviceProofTest` + `NdefEnvelopeIsolationTest` (Task 12) + `NdefCodecTest` |
| intentional write, capacity, read-back | `TagWriteControllerTest` off-device with the capacity seed; the physical rows are §D Session 1 |
| attachments | `AttachmentsDeviceProofTest`, `SafTreeAttachmentStoreContractTest` |
| backup: all-or-nothing export, data-only restore, artifacts restore, set mismatch, missing/hash drift | `BackupViewModelTest`, `RestoreProofTest`, `BackupUseCasesTest`, `AttachmentsDeviceProofTest` |
| UX: Apollo theme, Dashboard, Assets, two-tab navigation, Read/inspect tag, ambient NFC as the normal read path | `NavigationSmokeTest`, `AppSmokeTest`, `ComponentsSmokeTest`, `ContrastTest`, and the ambient row by `NfcIdentityDeviceProofTest` |

- [ ] **Step 1: Write the ambient-dispatch device proof**

`app/src/androidTest/kotlin/com/loosecannon/servicetag/ui/NfcIdentityDeviceProofTest.kt`:

```kotlin
package com.loosecannon.servicetag.ui

import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.model.TagTarget
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Ambient NFC as the normal read path (§21), on an emulator with no NFC radio: the intent the
 * platform would deliver is built in-process and handed to the real trampoline, which runs the
 * real codec against the real database. What this cannot prove — formatting, the measured
 * `Ndef.maxSize`, a capacity refusal, a read-back, a lock — belongs to §D Session 1 on a physical
 * NTAG213 and is not attempted here (G4).
 *
 * `NfcDispatchActivity` has no UI and finishes as soon as it has handed off, so there is no
 * scenario to track: the intent is started on the context and the assertions are made against
 * `MainActivity`'s tree through an empty Compose rule, the idiom `DeepLinkSmokeTest` uses.
 */
class NfcIdentityDeviceProofTest {

    @get:Rule val rule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun freshInstall() = clearInstall()

    private fun tap(records: Array<NdefRecord>) {
        val intent = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
            .setClassName(context, "com.loosecannon.servicetag.nfc.NfcDispatchActivity")
            .putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf(NdefMessage(records)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun ourRecords(tagId: String): Array<NdefRecord> =
        app.graph.ndefCodec.encodeV1(TagId(tagId))
            .map { NdefRecord(it.tnf.toShort(), it.type, ByteArray(0), it.payload) }
            .toTypedArray()

    /** A ServiceTag tag bound to an asset opens that asset, with no chooser and no sheet. */
    @Test fun ourTagOpensTheAssetItIsBoundTo() {
        val tagId = "11111111-1111-4111-8111-111111111111"
        runBlocking {
            val asset = app.graph.createAsset.run(name = "Hot tub")
            app.graph.bindTag.run(PayloadFormat.V1, tagId, TagTarget.AssetTarget(asset.id))
        }

        tap(ourRecords(tagId))

        rule.awaitText("Hot tub")
        rule.onNodeWithText("Hot tub").assertIsDisplayed()
    }

    /** A ServiceTag tag this install has no row for is named, not treated as damage. */
    @Test fun ourTagWithNoRowSaysSo() {
        tap(ourRecords("22222222-2222-4222-8222-222222222222"))

        rule.awaitText("This ServiceTag tag is not in this phone's records.")
    }

    /** A sibling's tag is foreign on the device, exactly as `NdefEnvelopeIsolationTest` says in JVM. */
    @Test fun aNoteTagRecordIsNotOurs() {
        val sibling = NdefRecord(
            0x04.toShort(),
            "com.loosecannon.notetag:tag".toByteArray(Charsets.US_ASCII),
            ByteArray(0),
            byteArrayOf(0x01, 0x01, 0x00) + ByteArray(16),
        )

        tap(arrayOf(sibling))

        rule.awaitText("Not a ServiceTag tag")
    }

    /** Our type, a body that cannot be parsed: ours, and refused as unreadable. */
    @Test fun ourTypeWithAShortBodyIsUnreadable() {
        val short = NdefRecord(
            0x04.toShort(),
            app.graph.tagIdentity.externalType.toByteArray(Charsets.US_ASCII),
            ByteArray(0),
            byteArrayOf(0x01, 0x00, 0x01),
        )

        tap(arrayOf(short))

        rule.awaitText("Not a ServiceTag tag")
    }
}
```

The sheet wordings above are `TagResultSheet`'s after Task 8's prose pass; if a sentence has been reworded since, read the composable and assert what it says — do not reword the app to match the test.

- [ ] **Step 2: Run it on the emulator**

```bash
export ANDROID_SERIAL=emulator-5554
adb devices     # one emulator, no phone
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --tests '*NfcIdentityDeviceProofTest' --console=plain
```

Expected: PASS, four tests. A failure in `ourTagOpensTheAssetItIsBoundTo` is worth reading carefully: it is the whole ambient path — filter → trampoline → codec → `ResolveTag` → `MainActivity` — and it is the row §21 calls "ambient NFC as the normal read path".

- [ ] **Step 3: Run the whole regression, both halves**

```bash
./gradlew clean :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --console=plain
export ANDROID_SERIAL=emulator-5554
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --console=plain
```

Expected: everything green, both APKs built, the instrumented suite green including the two new NFC suites. Write down the test counts — the evidence file quotes them.

- [ ] **Step 4: Write the evidence file**

Create `docs/architecture/product-split-evidence.md` with the Phase D section only (Phases E–N append to it later):

```markdown
# Product-split evidence

Commits, builds, test runs and device observations, one section per phase. Fingerprints only —
no secrets, no device ids, no owner paths.

## Phase D — ServiceTag identity conversion (§A.1)

**Commits.** Fifteen task commits plus two verification commits on `product-split`, in §A.1's
order. First: <sha> "root project name is ServiceTag". Last: <sha> "phase d regression pass on
the emulator".

**Identity, read off the built debug APK** (`aapt2 dump badging`):
`package: name='com.loosecannon.servicetag' versionCode='7' versionName='2.5'`,
`application-label:'ServiceTag'`.

**Merged manifest.** One `NDEF_DISCOVERED` filter; `android:path="/com.loosecannon.servicetag:tag"`
resolved from the single Gradle-owned value; `android:scheme="servicetag"`; zero occurrences of the
retired package name.

**The C9 binding.** `TagIdentityBindingTest` (JVM, <n> assertions) and `TagIdentityDispatchTest`
(emulator) both green: the identity comes from one Gradle value, the manifest carries only the
placeholder, and `queryIntentActivities` for `vnd.android.nfc://ext/com.loosecannon.servicetag:tag`
resolves to exactly one activity — this package's `NfcDispatchActivity`. The retired external type
resolves to nothing of ours.

**Record size (H2).** The `:tag` record is 51 B (3 + 30 + 18) and the AAR 44 B (3 + 15 + 26): a
**95 B** NDEF message. Capacity is compared message-size to message-size against `Ndef.maxSize`,
with no TLV framing (G1). The NTAG213 limits-test constant is `137` — **`[unobserved]` provisional
seed**, to be re-pinned from the measured `Ndef.maxSize` in §D Session 1 (H8).

**Legacy format.** `md5_short` is gone: no constant, no decoder, no filter, no sheet, no enum
member, no test. Recorded as a **deliberate reversal of D6's "kept permanently" promise** under
O2/O3. The live database carries `nfc_tag = 0` rows, so no row of the owner's data named it.

**Room.** Schemas re-exported under `app/schemas/com.loosecannon.servicetag.data.room.AppDatabase/`
by `git mv` plus a re-export; `5.json`'s `identityHash` is `157988f1aada363f37590a6735a3be36`,
**unchanged**. Every migration hop and the full 1→5 run green.

**Launcher icon.** The owner's finished pack, staged at
`~/Documents/Projects/AndroidStudioProjects/split-assets/ServiceTag/` and copied into the module:
ivory `#F7F5EF` ground and a text-free `#1F4E78` mark (the Apollo Service Binder palette), adaptive
foreground/background under `mipmap-anydpi-v26/`, a dedicated `<monochrome>` layer for Android 13+
themed icons, and PNG rasters for pre-adaptive launchers. The inherited `mipmap-anydpi/` XML pair
and all ten `*.webp` rasters are deleted — the APK contains no `.webp` under `ic_launcher*` — and
`<application>` now declares `android:roundIcon` as well as `android:icon`. No placeholder: the
coexistence requirement is met with shipped artwork. Design masters (`source/`, `preview/`) stay in
the staging area and are not tracked here.

**Inventory addition found in this phase.** `ui/settings/SettingsScreen.kt:64`
`PROJECT_URL = "https://github.com/GonzRon/ServiceTag"` — an identity carrier absent from arch
§4.6 and from §A.1. It points at the post-rename name, which resolves from §B.2 onward (this code
is pushed at §B.3a, after it).

**Suites.** `:core:test` <n> tests; `:app:testDebugUnitTest` <n>; `:app:connectedDebugAndroidTest`
<n>, on the emulator (`ANDROID_SERIAL=emulator-5554`). **No instrumented run touched the phone in
this phase.** Both APKs build; the release APK is unsigned, because
`~/.config/servicetag/keystore.properties` does not exist yet — the key is generated in a later
phase and §12's signed-release proof is discharged there.

**Clean checkout.** `git clone --no-local` into a directory that never held the project, then
`:core:test :app:testDebugUnitTest :app:assembleDebug` — green. The URL clone and the
second-workstation build are §B.6.

**Caveat, stated once.** Everything above is **debug-build evidence**. §12's signed-release
requirement is discharged separately and only as a build-verified claim; no device row in this
file comes from a release build.

**Not attempted in this phase, and why.** Formatting, the measured `Ndef.maxSize`, capacity
refusal, read-back and lock-last need a radio and a physical NTAG213: §D Session 1's four owner
taps. Coexistence needs two installed products: §E Session 2's eight. The emulator has no NFC
radio, so the ambient path is proved with synthetic `NDEF_DISCOVERED` intents through the real
dispatch activity, codec and database (G4's reassignment).
```

Replace every `<sha>` and `<n>` with the real value from your own run before committing — an
unfilled placeholder in an evidence file is a defect, not a formatting detail.

- [ ] **Step 5: Add the ledger line**

Do not write to the controller's split ledger. Put the one-line Phase D summary (commit range, gate results, the measured facts) at the top of your report file; the controller appends it to the ledger.

- [ ] **Step 6: Commit**

```bash
git add docs/architecture/product-split-evidence.md app/src/androidTest
git commit -m "phase d regression pass on the emulator, and the evidence"
```

---

## Spec coverage — §A.1's fifteen rows and target §4.8

| §A.1 row | Task | Row's own verification, where it lives |
|---|---|---|
| 1 `rootProject.name` | Task 1 | `./gradlew projects` prints `ServiceTag` |
| 2 namespace / applicationId / keystore / 7 / "2.5" | Task 2 | `assembleDebug` + `aapt2 dump badging` |
| 3 Kotlin package roots (233 files) | Task 3 | the **scoped** check only (G3); the 12 expected leftovers are listed |
| 4 the five FQN `android:name` literals | Task 4 | merged-manifest `android:name` scan + a launch on the emulator |
| 5 the one Gradle-owned identity value | Task 5 | both binding tests (JVM + emulator), target §4.8 |
| 6 drop `md5_short` | Task 6 | `git grep -i md5` empty; exactly one `NDEF_DISCOVERED` filter |
| 7 `servicetag://` | Task 7 | `git grep -c 'notenfc://'` = 0; route tests; two `am start` probes |
| 8 label / theme / app class / nav root / debug label | Task 8 | `assembleDebug` + `assembleRelease`; the label read off the APK |
| 9 a new launcher icon | Task 9 | the owner's pack copied in; no `.webp` left in the APK; `dumpsys` + the tile looked at on the emulator |
| 10 `servicetag.db` / prefs / export prefixes | Task 10 | JVM suites, plus the retired-prefix import test |
| 11 Room schema export directory | Task 11 | every hop green; `identityHash` `157988f1…` unchanged |
| 12 sibling isolation | Task 12 | `NdefEnvelopeIsolationTest`, five cases |
| 13 the string-typed wire vocabulary | Task 13 | `TagResultWireTest`; one literal left, its own definition |
| 14 remaining carriers + three non-changes | Task 14 | greps recorded; `.gitignore` the only edit |
| 15 README hygiene | Task 15 | `git grep 'CN=noteNFC'` clean outside `docs/` |
| whole-phase verification | Task 16 | the repository-wide grep, badging, four Gradle tasks, the instrumented suite, a fresh install, a clean checkout |
| §A.1.1 §21 regression (gate 5 entry) | Task 17 | the §21 row table, the ambient device proof, the evidence file |

Target §4.8's two tests are Task 5's `TagIdentityBindingTest` (JVM) and `TagIdentityDispatchTest`
(emulator). Target §3's identity table is asserted against the **built APK** in Task 16 Step 2 and
against the **installed** app in Task 16 Step 5 — not against the source.
