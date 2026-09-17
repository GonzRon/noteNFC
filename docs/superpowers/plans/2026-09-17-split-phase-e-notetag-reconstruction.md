# Split Phase E — NoteTag reconstruction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reconstruct the narrow note/link utility as **NoteTag** — a new local repository with true ancestry from `c84b881`, identity `com.loosecannon.notetag`, the NoteTag v1 tag format (`version|kind|flags|body`, kinds `JOPLIN_NOTE` / `URI` / `LOCAL_REF`, no AAR), a minimal atomically-replaced JSON local store with the `LOCAL_REF` crash-consistency invariant, a copied safe `ACTION_VIEW` launch policy, sibling isolation in NoteTag's direction, a Compose single-activity UI (share → write; a list of tags this phone wrote), an ambient dispatch trampoline, a signed release with the existing noteNFC key, and the evidence — all local, all emulator-only.

**Architecture:** Eleven commits in §A.2's order (rows 1–9; row 10 — adopting `nfc-tag-core` — is Phase G and does not start here). `c84b881` supplies provenance and the share→write→tap intent, not code: the three 2024 activities, both XML layouts, the `TECH_DISCOVERED` catch-all and the three legacy `:core` files are deleted, not modernised (O6). NoteTag's NFC mechanism in this phase is an **interim verbatim copy** of ServiceTag's adapter (`NdefRecordData`, `TagIdentity`, the envelope half of the codec, `NdefBridge`, `NfcReaderModeSession`, `TagWriter`, the `TagIo` seam) so that Phase F extracts *demonstrated* commonality from two real consumers and Phase G deletes the copy in both apps. Everything NoteTag-specific — the body codec, the writer's compact → URI-if-it-fits → `LOCAL_REF` decision, the store, the wording, the resolver — is new, pure-JVM where possible, and tested without a device. Physical tags are never touched in this phase; the format/write/read-back/lock proof belongs to the physical session after Phase G.

**Tech Stack:** the estate's pins, unchanged — AGP 9.4.0, Kotlin 2.4.20 (built into AGP; **never apply `org.jetbrains.kotlin.android`**), Compose BOM 2026.08.00, kotlinx-serialization 1.9.0, kotlinx-coroutines 1.10.2, JUnit 5 in `:core`, JUnit 4 + Compose test in `:app`, minSdk 26, targetSdk 36, compileSdk 37, JDK 17 toolchain. **No Room, no Navigation 3, no DocumentFile, no KSP** in NoteTag (P19/P20: "no Room unless it earns it"; two screens do not earn a navigation framework).

**Spec:** `docs/architecture/product-split-migration.md` **§A.2** (the ten-row task table; rows 1–9 are this phase, row 10 is Phase G; the whole-phase verify and rollback) and `docs/architecture/product-split-target.md` **§3** (the NoteTag column of the identity table), **§4.3** (invariants 1–9, 12, 13), **§4.5** (the test tiers and the operating rule), **§4.9** (the format, the writer's decision, the `JOPLIN_NOTE` validation rule, the `LOCAL_REF` crash-consistency invariant and its three-case failure-injection test, the UI toolkit ruling, the no-AAR trade), **§10.2** (what is NoteTag roadmap, not split scope). The owner's brief §12 (signing), §23 (acceptance), §25 (coexistence). **On any conflict between this plan and those documents, the plan loses.**

## Global Constraints

- **Where the work happens.** A NEW local repository at `~/Documents/Projects/AndroidStudioProjects/NoteTag`, created by Task 1 exactly as §A.2's "Do." block says: `git clone --no-local noteNFC NoteTag`, `origin` removed, `master` reset to `c84b881`, every other ref deleted, reflog expired, gc'd. **It has no remote in this phase** (§B.4 creates `GonzRon/NoteTag` later). The ServiceTag worktree (`../ServiceTag-split`, branch `product-split`) is read-only for implementers except for Task 11's evidence commit. The main checkout `noteNFC` (`master` at `ac523d7`) is never modified.
- **Ancestry is real and stays real.** `git rev-list --merges --count c84b881` = 0, the root is `5fb6aed`, and 30 commits precede the reconstruction. Nothing is rebased, squashed, filtered or force-pushed; **no history rewrite of any kind** (C6). The first commit states what remains reachable in history (the ServiceTag design docs, the owner's public handle and issue URLs, the release-signer digests, the author name and email on all 30 preserved commits) and that none of it is a secret.
- **Identity, typed once.** `com.loosecannon.notetag` is `namespace`, `applicationId`, the NFC Forum external-type domain and the Kotlin root of `:app`; `com.loosecannon.notetag.core` is `:core`'s root. External type `com.loosecannon.notetag:tag`; manifest path `${ndefTagPath}` = `/com.loosecannon.notetag:tag`, an **exact** path; **`NDEF_AAR_PACKAGE` is `null` — no Application Record, ever, in this phase** (O13). The deep-link scheme `notetag` is **reserved and declares no `VIEW` filter** (P4): no `TagRoute`, no `DeepLinkRoute`, no `android:scheme="notetag"` anywhere. Label `NoteTag`; `rootProject.name = "NoteTag"`; `versionCode 3`, `versionName "2.0"` (past the historical 2 / 1.1).
- **Signing.** `~/.config/notenfc/keystore.properties` through the same `Properties`-from-`user.home` mechanism and the same four-key `hasSigningKeys` guard ServiceTag uses — **the existing noteNFC key, unchanged, never rotated, replaced or copied** (§12). The key exists on this workstation, so `:app:assembleRelease` produces a **signed** APK and Task 11 proves it. Keystore values are never printed, logged or committed; the certificate digest is **compared** with the one recorded in `docs/design/phase-1a-evidence.md` (in the ServiceTag worktree) and the result is reported as a word, never reproduced.
- **No AAR, no TECH_DISCOVERED, no tech filter, no legacy.** Exactly one `NDEF_DISCOVERED` filter in the merged manifest; `git grep -c nfc_tech_filter` = 0; `git grep -c TECH_DISCOVERED` = 0; `LegacyKey`, `LegacyLinkPolicy`, `md5_short`, `notenfc://` and the mixed-case `com.looseCannon.noteNFC` root are deleted, not deprecated. The 2024 prefs map is abandoned (greenfield NFC, O2/O3).
- **NDEF invariants (target §4.3), in NoteTag's own code and tests:** type gate before body parse (1); a sibling ServiceTag record decodes `Foreign` and is never interpreted as a note (§23); never `FLAG_READER_SKIP_NDEF_CHECK` (4); lower-case external type (5); capacity is the exact serialised NDEF message in bytes against the **measured** `maxSize`, with **no TLV allowance and no character count** (7, O13/O14); ambient dispatch cannot write, by construction — the trampoline never calls `nfcTag()` (12); every user-facing sentence is NoteTag's own (13).
- **`LOCAL_REF` crash consistency (target §4.9, G2/H1):** the mapping is atomically persisted **before** any write; a mapping that did not commit is never written; once a write has been **attempted** the mapping is **retained** on every ambiguous failure and removed only on cancellation or a pre-write rejection. The three-case failure-injection test is a named deliverable (Task 7).
- **Instrumented runs are emulator-only.** Every `connectedDebugAndroidTest`, `installDebug`, `am start` and `adb` call in this plan carries `ANDROID_SERIAL=emulator-5554` (or `adb -s emulator-5554`) on the same command line. **The phone is never a target in this phase** (it holds the owner's real ServiceTag data). No physical tag is written, formatted or read in this phase.
- **Pure `:core`.** `:core` imports nothing from `android.*`, `androidx.*`, Gradle or `BuildConfig`; identity reaches it as plain values. Kotlin stdlib, kotlinx-serialization-json and kotlinx-coroutines-core are its only runtime dependencies.
- **No personal data in tracked files.** No owner paths (write `~`), no device serials other than `emulator-5554`, no usernames, no real note ids, no keystore material, no fingerprints (pointers only). Commit messages are single casual subject lines: no body unless a task says so, no trailers, no attribution lines of any kind.
- **CI file kept, not re-run here.** `.github/workflows/ci.yml` stays as inherited (`:core:test :app:testDebugUnitTest :app:assembleDebug`); §A.2 row 3's "re-run from scratch" needs the remote and happens at §B.4. Every task's local gate runs that same line.
- **Per-task gate:** `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain` from the NoteTag root; plus `:app:compileDebugAndroidTestKotlin` from Task 9 on. Each task is one commit on `master` of the NoteTag repository. **Rollback:** revert the task commit; the phase's floor is the Task 1 commit; the repository's floor is "delete the directory and re-clone — `c84b881` is immutable history" (§A.2).
- **Interim copies are labelled.** Every file copied from ServiceTag carries a one-line header comment `// Interim copy of ServiceTag's <path> (dc1bb1c lineage); Phase G replaces it with nfc-tag-core.` so Phase G can `grep` its own deletion list.
- **Exactly two screens (P20).** `List` (the tags this phone has written, with an inline result card for whatever an ambient tap or a refusal has to say) and `Write` (share → hold a tag). There is no third navigation state and no message screen: the trampoline hands a sentence to `MainActivity`, which shows it on the list.
- **Retained is not written (owner correction, 2026-09-17).** A `LOCAL_REF` mapping persisted before a write and retained after an ambiguous failure stays **resolvable** (`get`) but is **not** a confirmed write: `TagEntry.writtenAt` is null until a verified `Written` result confirms it, and `list()` — the only thing the UI shows as "tags this phone wrote" — returns confirmed entries only. The model permits a later ambient resolution of such a tag to promote the mapping (the tag's existence is then proven); Phase E does not implement that promotion.
- **The `LOCAL_REF` warning is a binding UX rule (owner, 2026-09-17).** When the measured capacity forces the `LOCAL_REF` fallback, the writer stops **before writing** with "This tag needs this phone to open. Back up NoteTag to protect the link." and a Write / Cancel choice; the success state reads "Written · This phone only" with the line "Saved as a this-phone-only tag."; the list marks such entries "This phone only". Persistence and recovery (Room, versioned export/import, a SAF backup folder, scheduled backups) are roadmap after Phases F/G — target §10.2 — not this phase.
- **Gate 6 is not claimed by this plan.** §23 end to end and CI green from scratch need Phase G (row 10) and the remote (§B.4). The phase ends as "Phase E local reconstruction complete; Gate 6 pending its deferred prerequisites", recorded in the ledger in those words.

## The identity, and everything derived from it

| Thing | Value | Where it comes from |
|---|---|---|
| `rootProject.name` | `NoteTag` | `settings.gradle.kts` (Task 2) |
| `namespace` / `applicationId` | `com.loosecannon.notetag` | `val appId` in `app/build.gradle.kts` (Task 2) |
| `versionCode` / `versionName` | `3` / `"2.0"` | `app/build.gradle.kts` (Task 2) |
| keystore properties | `~/.config/notenfc/keystore.properties` | `app/build.gradle.kts` (Task 2); proven signed in Task 11 |
| Kotlin roots | `com.loosecannon.notetag` (`:app`), `com.loosecannon.notetag.core` (`:core`) | Task 2 |
| NDEF external domain / type name | `com.loosecannon.notetag` / `tag` | `tagExternalDomain`, `tagTypeName` → `BuildConfig.NDEF_EXTERNAL_DOMAIN`, `NDEF_TYPE_NAME` (Task 2) |
| AAR package | **none** | `tagAarPackage: String? = null` → `BuildConfig.NDEF_AAR_PACKAGE = null` (Task 2) |
| manifest `android:path` | `${ndefTagPath}` = `/com.loosecannon.notetag:tag` | `manifestPlaceholders` from the same vals (Task 2); bound by Task 10's test |
| deep-link scheme | `notetag` — reserved, **no filter** | nothing declares it (P4); Task 10's test asserts the absence |
| label / theme / app class | `NoteTag` / `Theme.NoteTag` / `NoteTagApp` | Task 2 |
| local store file | `filesDir/tags.json` | `NoteTagApp` (Task 9) |
| test APK id | `com.loosecannon.notetag.test` | AGP default |

## File structure

```
NoteTag/
  settings.gradle.kts  build.gradle.kts  gradle/libs.versions.toml  (Task 2)
  core/                                          pure JVM, JUnit 5
    src/main/kotlin/com/loosecannon/notetag/core/
      nfc/NdefRecordData.kt        interim copy (Task 4)
      nfc/TagIdentity.kt           interim copy (Task 4)
      nfc/NdefEnvelope.kt          envelope: type gate, one record, optional AAR (Task 4)
      nfc/NdefSize.kt              serialisedSize — the pure-JVM mirror of toByteArray().size (Task 5)
      tag/JoplinId.kt              32-hex validation, lower-casing, 16-byte packing, openNote URI (Task 4)
      tag/NoteTagContent.kt        the decoded kinds + Foreign/Malformed/NewerVersion/UnknownKind/Empty (Task 4)
      tag/NoteTagCodec.kt          version|kind|flags|body over the envelope (Task 4)
      links/LinkLaunchPolicy.kt    copied, not shared (Task 5)
      write/WritePlanner.kt        compact → URI-if-it-fits → LOCAL_REF, against an injected maxSize (Task 5)
      store/TagStore.kt, TagEntry.kt, JsonFileTagStore.kt   atomic-replace JSON store; writtenAt null = not yet confirmed (Task 6)
      resolve/ResolveTap.kt        records → Open(uri) | Message(text) (Task 8)
      nfc/OverwriteWording.kt      NoteTag's sentences for the read-before-write question (Task 7)
    src/test/kotlin/...            one test class per file above
  app/                                           Android, JUnit 4 + Compose test
    build.gradle.kts  proguard-rules.pro  src/main/AndroidManifest.xml  (Task 2)
    src/main/res/{values,mipmap-*,drawable}/     strings, themes, the icon pack (Tasks 2, 3)
    src/main/kotlin/com/loosecannon/notetag/
      NoteTagApp.kt                the graph: store file, identity, codec, TagIo (Task 9)
      MainActivity.kt              single activity; share receiver; two screens: List (with its result card) and Write (Task 9)
      nfc/NdefBridge.kt, NfcReaderModeSession.kt, TagWriter.kt, TagIo.kt   interim copies (Task 7)
      nfc/NfcDispatchActivity.kt   translucent trampoline (Task 10)
      links/LinkLauncher.kt        copied, both catches (Task 10)
      write/NoteTagWriteController.kt   single-flight, read-before-write, the LOCAL_REF sequence (Task 7)
      ui/WriteScreen.kt, TagListScreen.kt (with its inline result card), NoteTagTheme.kt   (Task 9)
    src/test/kotlin/...            controller, resolver wiring, identity binding
    src/androidTest/kotlin/...     NdefSize pin, smoke, ambient device proof (Tasks 9, 10)
  README.md                        rewritten twice: Task 1 (C6 statement) and Task 11 (the product)
  .github/workflows/ci.yml         inherited, unchanged
```

---

### Task 1 (§A.2 row 1): the reconstruction root

**Files:**
- Create: the repository `~/Documents/Projects/AndroidStudioProjects/NoteTag` (a `--no-local` clone of the main checkout)
- Delete (in its first commit): `docs/` (70 files), `app/release/app-release.apk`
- Modify: `README.md`

**Interfaces:**
- Produces: a repository whose `master` is `c84b881` + one commit, with a 45-file tree, no remote, no other refs.

- [ ] **Step 1: Clone and reset, exactly as §A.2 says**

```bash
cd ~/Documents/Projects/AndroidStudioProjects
test ! -e NoteTag || { echo "NoteTag already exists — stop"; exit 1; }
git clone --no-local noteNFC NoteTag
cd NoteTag
git remote remove origin
git checkout -B master c84b881
git for-each-ref --format='%(refname)' refs/heads refs/tags \
  | grep -v '^refs/heads/master$' | xargs -r -n1 git update-ref -d
git reflog expire --expire=now --all && git gc --prune=now
git for-each-ref | cat            # expect: exactly one line, refs/heads/master at c84b881
git rev-list --merges --count HEAD   # expect: 0
git rev-list --max-parents=0 HEAD    # expect: 5fb6aed…
git rev-list --count HEAD            # expect: 30
git ls-files | wc -l                 # expect: 116
```

- [ ] **Step 2: The first commit — delete the ServiceTag-only material and state what this does not do**

```bash
git rm -r -q docs
git rm -q app/release/app-release.apk
```

Replace `README.md` wholesale with:

```markdown
# NoteTag

The narrow note-and-link NFC utility, reconstructed from its own history. This repository's
`master` continues the original noteNFC line unchanged through `c84b881` (the last commit before
that product grew into ServiceTag) and starts the NoteTag rewrite from there.

## What this first commit does, and does not do

It deletes the ServiceTag design documents and the 2024 release APK from the tree, because they
belong to the other product. It does **not** scrub anything from history, and no history rewrite
will ever be performed here. Reachable in this repository's public history, deliberately: the
ServiceTag design documents; the owner's public GitHub handle and issue links; the release-signer
certificate digests; and the author name and email on every preserved commit. None of it is a
secret — a public handle and public-key fingerprints — and all of it is already public in the
repository this one was cloned from.

Everything else about NoteTag — identity, tag format, store, screens — is written fresh in the
commits that follow; see the migration runbook in the ServiceTag repository
(`docs/architecture/product-split-migration.md`, §A.2) for the sequence.
```

```bash
git add README.md
git commit -m "start the narrow product from its own history

deletes docs/ and the 2024 apk from the tree only. nothing is scrubbed from
history and no history rewrite will be performed: the servicetag design docs,
the owner's public handle and issue links, the signer digests and the author
name/email on all 30 preserved commits stay reachable. none of it is secret."
```

(This is the one commit in the phase with a body: C6 asks for the statement in the commit message itself.)

- [ ] **Step 3: Verify row 1**

```bash
git ls-files | wc -l                                     # expect: 45
git ls-files | grep -c '\.apk$'                          # expect: 0
git grep -lE 'GonzRon|github\.com/|SHA-?256:' | cat      # expect: only `gradlew` — the wrapper's upstream github.com/gradle/gradle comments, present at c84b881; nothing else
git log --oneline | tail -1                              # expect: 5fb6aed …
git rev-list --merges --count HEAD                       # expect: 0
git rev-list --count HEAD                                # expect: 31
```

---

### Task 2 (§A.2 rows 2, 4, 5-skeleton): the modern build, the identity, and an empty NoteTag

**Files:**
- Modify: `settings.gradle.kts` (`rootProject.name = "NoteTag"`), `gradle/libs.versions.toml`, `app/build.gradle.kts`, `core/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `.gitignore`
- Delete: `app/src/main/java/com/looseCannon/noteNFC/{LaunchNoteNFCLinkActivity,NFCHandlerActivity,mainActivity}.kt`, `app/src/main/res/layout/{activity_main,write_nfc_link}.xml`, `app/src/main/res/xml/nfc_tech_filter.xml`, `app/src/main/res/values/colors.xml`, `core/src/main/kotlin/com/loosecannon/notenfc/core/links/LegacyLinkPolicy.kt`, `core/src/main/kotlin/com/loosecannon/notenfc/core/nfc/{LegacyKey,NdefCodec}.kt`, `core/src/test/kotlin/com/loosecannon/notenfc/core/**` (all three test files)
- Create: `app/src/main/res/values/themes.xml`, `app/src/main/kotlin/com/loosecannon/notetag/NoteTagApp.kt`, `app/src/main/kotlin/com/loosecannon/notetag/MainActivity.kt`, `core/src/main/kotlin/com/loosecannon/notetag/core/Placeholder.kt` is **not** created — `:core` may be empty of sources until Task 4; `core/src/test/kotlin/com/loosecannon/notetag/core/BuildSanityTest.kt`

**Interfaces:**
- Produces: `BuildConfig.NDEF_EXTERNAL_DOMAIN`, `NDEF_TYPE_NAME`, `NDEF_AAR_PACKAGE` (`null`), `APPLICATION_ID`; the manifest placeholder `ndefTagPath`; `NoteTagApp : Application`; a launchable `MainActivity` that shows "NoteTag" and accepts `ACTION_SEND text/plain` (does nothing with it yet).

- [ ] **Step 1: The catalog**

Replace `gradle/libs.versions.toml` with the ServiceTag catalog **minus** Room, KSP, SQLite, DocumentFile and Navigation 3:

```toml
[versions]
agp = "9.4.0"
kotlin = "2.4.20"
coreKtx = "1.13.1"
junit = "5.11.4"
kotlinxSerialization = "1.9.0"
kotlinxCoroutines = "1.10.2"
junit4 = "4.13.2"
composeBom = "2026.08.00"
activityCompose = "1.13.0"
lifecycle = "2.10.0"
androidxTestRunner = "1.7.0"
androidxTestRules = "1.7.0"
androidxTestExtJunit = "1.3.0"
androidxEspresso = "3.7.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit-bom = { group = "org.junit", name = "junit-bom", version.ref = "junit" }
junit-jupiter = { group = "org.junit.jupiter", name = "junit-jupiter" }
junit-platform-launcher = { group = "org.junit.platform", name = "junit-platform-launcher" }
kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test", version.ref = "kotlin" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
junit4 = { group = "junit", name = "junit", version.ref = "junit4" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "androidxTestRunner" }
androidx-test-rules = { group = "androidx.test", name = "rules", version.ref = "androidxTestRules" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestExtJunit" }
androidx-test-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "androidxEspresso" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

Copy the exact group/name/version strings from `../ServiceTag-split/gradle/libs.versions.toml` where this block abbreviates (the values above are transcribed from it; verify each line against that file rather than trusting this block).

- [ ] **Step 2: `settings.gradle.kts`, `core/build.gradle.kts`, `.gitignore`**

`settings.gradle.kts`: change only `rootProject.name = "noteNFC"` → `rootProject.name = "NoteTag"`. Keep `FAIL_ON_PROJECT_REPOS`, foojay, `include(":app", ":core")`.

`core/build.gradle.kts`: keep the inherited file and add the serialization plugin and the two runtime deps:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.withType<Test>().configureEach { useJUnitPlatform() }
```

`.gitignore`: append the signing lines ServiceTag carries, with the NoteTag key's home named:

```
# signing material never belongs in the repo (it lives in ~/.config/notenfc)
keystore.properties
*.jks
*.keystore
```

- [ ] **Step 3: `app/build.gradle.kts` — the ServiceTag template with NoteTag's values**

```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val keystoreProps = Properties().apply {
    val f = file(System.getProperty("user.home") + "/.config/notenfc/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// A signing config needs all four values; a partial file must fail to SIGN, not to CONFIGURE.
val hasSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { keystoreProps.getProperty(it)?.isNotBlank() == true }

// This app's identity, typed once (C9, target §4.8).
val appId = "com.loosecannon.notetag"
val tagExternalDomain = appId          // NFC Forum external-type domain
val tagTypeName = "tag"
val tagAarPackage: String? = null      // NoteTag writes no Application Record (O13)

android {
    namespace = appId
    compileSdk = 37
    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["ndefTagPath"] = "/$tagExternalDomain:$tagTypeName"
        buildConfigField("String", "NDEF_EXTERNAL_DOMAIN", "\"$tagExternalDomain\"")
        buildConfigField("String", "NDEF_TYPE_NAME", "\"$tagTypeName\"")
        buildConfigField("String", "NDEF_AAR_PACKAGE", tagAarPackage?.let { "\"$it\"" } ?: "null")
    }
    signingConfigs {
        if (hasSigningKeys) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigningKeys) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.core)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
```

- [ ] **Step 4: Delete the 2024 app and the legacy core, then the manifest**

```bash
git rm -r -q app/src/main/java app/src/main/res/layout app/src/main/res/xml app/src/main/res/values/colors.xml
git rm -r -q core/src/main/kotlin/com/loosecannon/notenfc core/src/test/kotlin/com/loosecannon/notenfc
```

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.NFC" />
    <uses-feature android:name="android.hardware.nfc" android:required="true" />

    <application
        android:name="com.loosecannon.notetag.NoteTagApp"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.NoteTag">

        <activity
            android:name="com.loosecannon.notetag.MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        </activity>

        <!-- The one NFC-exported component. Exactly one filter, on the exact external type;
             the placeholder is produced from the same Gradle values as BuildConfig (C9). -->
        <activity
            android:name="com.loosecannon.notetag.nfc.NfcDispatchActivity"
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
    </application>
</manifest>
```

`NfcDispatchActivity` does not exist until Task 10; the manifest must still merge and the APK build, so **this task creates it as a stub** at `app/src/main/kotlin/com/loosecannon/notetag/nfc/NfcDispatchActivity.kt`:

```kotlin
package com.loosecannon.notetag.nfc

import android.app.Activity
import android.os.Bundle

/** Stub until Task 10; it must exist for the manifest to merge. Finishes immediately. */
class NfcDispatchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); finish() }
}
```

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">NoteTag</string>
</resources>
```

`app/src/main/res/values/themes.xml`:

```xml
<resources>
    <!-- Compose draws everything; the platform theme only needs to not draw an action bar. -->
    <style name="Theme.NoteTag" parent="android:Theme.Material.NoActionBar" />
</resources>
```

- [ ] **Step 5: The empty app**

`app/src/main/kotlin/com/loosecannon/notetag/NoteTagApp.kt`:

```kotlin
package com.loosecannon.notetag

import android.app.Application

/** The graph grows in Task 9; today it only names the application class the manifest points at. */
class NoteTagApp : Application()
```

`app/src/main/kotlin/com/loosecannon/notetag/MainActivity.kt`:

```kotlin
package com.loosecannon.notetag

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { Surface { Text(stringResource(R.string.app_name)) } }
        }
    }
}
```

`core/src/test/kotlin/com/loosecannon/notetag/core/BuildSanityTest.kt` (so `:core:test` runs at least one test until Task 4):

```kotlin
package com.loosecannon.notetag.core

import kotlin.test.Test
import kotlin.test.assertEquals

class BuildSanityTest {
    @Test fun theModuleBuildsAndTests() = assertEquals(4, 2 + 2)
}
```

- [ ] **Step 6: Gate and badging**

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain
AAPT2=~/Android/Sdk/build-tools/36.0.0/aapt2
"$AAPT2" dump badging app/build/outputs/apk/debug/app-debug.apk | grep -E "^package:|application-label:|launchable-activity:"
git grep -n 'nfc_tech_filter\|TECH_DISCOVERED\|looseCannon\|notenfc' -- app core | cat   # expect: exactly one line — the ~/.config/notenfc keystore path in app/build.gradle.kts
grep -c NDEF_DISCOVERED app/src/main/AndroidManifest.xml                                   # expect: 1
```

Expected: BUILD SUCCESSFUL; `package: name='com.loosecannon.notetag' versionCode='3' versionName='2.0'`; `application-label:'NoteTag'`; one launchable activity `com.loosecannon.notetag.MainActivity`. (The launcher icon is still the inherited placeholder until Task 3; that is expected.)

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "notetag: modern build, the identity typed once, and an empty compose app"
```

---

### Task 3 (§A.2 row 2, "its own launcher icon"): the NoteTag icon pack

**Files:**
- Delete: `app/src/main/res/mipmap-anydpi/ic_launcher{,_round}.xml`, the ten `.webp` rasters under `mipmap-{m,h,xh,xxh,xxxh}dpi/`
- Create/replace: `app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml`, `mipmap-anydpi-v26/ic_launcher{,_round}.xml`, the ten `.png` rasters

**Interfaces:**
- Consumes: the owner's pack, staged read-only at `~/Documents/Projects/AndroidStudioProjects/split-assets/NoteTag/app/src/main/res/` (amber tag + note mark on an ivory ground; a `<monochrome>` layer for themed icons). Do not touch the sibling `ServiceTag` pack.

- [ ] **Step 1: Swap the resources**

```bash
PACK=~/Documents/Projects/AndroidStudioProjects/split-assets/NoteTag/app/src/main/res
git rm -r -q app/src/main/res/mipmap-anydpi
git rm -q app/src/main/res/mipmap-*/ic_launcher*.webp
cp -r "$PACK"/drawable "$PACK"/mipmap-anydpi-v26 "$PACK"/mipmap-mdpi "$PACK"/mipmap-hdpi "$PACK"/mipmap-xhdpi "$PACK"/mipmap-xxhdpi "$PACK"/mipmap-xxxhdpi app/src/main/res/
for f in $(cd "$PACK" && find . -type f); do cmp -s "$PACK/$f" "app/src/main/res/$f" || echo "DIFFERS $f"; done   # expect: no output
```

- [ ] **Step 2: Build, badge, look**

```bash
./gradlew :app:assembleDebug --console=plain
"$AAPT2" dump badging app/build/outputs/apk/debug/app-debug.apk | grep -E "^application:|application-icon"
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -i ic_launcher | grep -c webp        # expect: 0
export ANDROID_SERIAL=emulator-5554
adb -s emulator-5554 get-state                                                               # expect: device
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug --console=plain
adb -s emulator-5554 shell am start -W -n com.loosecannon.notetag/.MainActivity
adb -s emulator-5554 shell input keyevent KEYCODE_HOME
adb -s emulator-5554 exec-out screencap -p > "$SCRATCH/notetag-launcher.png"   # look at it (app drawer if needed), then delete it
```

Expected: `icon='res/mipmap-anydpi-v26/ic_launcher.xml'`, six `application-icon-<density>` lines, and an amber-and-ivory NoteTag tile labelled **NoteTag** — not the inherited green robot.

- [ ] **Step 3: Commit**

```bash
git add -A app/src/main/res
git commit -m "the notetag launcher icon, round icon and all"
```

---

### Task 4 (§A.2 row 6, the format): the NoteTag v1 tag format in `:core`

**Files:**
- Create: `core/src/main/kotlin/com/loosecannon/notetag/core/nfc/NdefRecordData.kt`, `nfc/TagIdentity.kt`, `nfc/NdefEnvelope.kt`, `tag/JoplinId.kt`, `tag/NoteTagContent.kt`, `tag/NoteTagCodec.kt`
- Create: `core/src/test/kotlin/com/loosecannon/notetag/core/nfc/NdefEnvelopeTest.kt`, `tag/JoplinIdTest.kt`, `tag/NoteTagCodecTest.kt`
- Delete: `core/src/test/kotlin/com/loosecannon/notetag/core/BuildSanityTest.kt`

**Interfaces:**
- Produces: `TagIdentity(externalDomain, typeName, aarPackage = null)`; `NdefEnvelope(identity).decode(records): EnvelopeContent` (`Recognised(body)`, `Foreign(description)`, `Empty`) and `.encode(body): List<NdefRecordData>`; `NoteTagContent` (`JoplinNote(id)`, `Uri(uri)`, `LocalRef(uuid)`, `NewerVersion(version)`, `UnknownKind(kind)`, `Malformed(reason)`, `Foreign(description)`, `Empty`); `NoteTagCodec(identity).decode(records)` / `.encode(content: NoteTagContent.Writable)`; `JoplinId.normalise`, `toBytes`, `fromBytes`, `openNoteUri`, `idFromOpenNoteUri`.

- [ ] **Step 1: The two interim copies**

`nfc/NdefRecordData.kt` — copy lines 8–12 of `../ServiceTag-split/core/src/main/kotlin/com/loosecannon/servicetag/core/nfc/NdefCodec.kt` (the data class with array-aware `equals`/`hashCode`) into its own file under `package com.loosecannon.notetag.core.nfc`, with the interim-copy header comment.

`nfc/TagIdentity.kt` — copy `../ServiceTag-split/core/src/main/kotlin/com/loosecannon/servicetag/core/nfc/TagIdentity.kt` verbatim, package changed, header comment added. Both `require` guards stay.

- [ ] **Step 2: The envelope (the library's future `NdefEnvelope`, target §4.6)**

`nfc/NdefEnvelope.kt`:

```kotlin
package com.loosecannon.notetag.core.nfc

// Interim: the envelope half of ServiceTag's NdefCodec (dc1bb1c/f92a391 lineage), reshaped to the
// nfc-tag-core API target §4.6 names; Phase G replaces it with the library's NdefEnvelope.

sealed interface EnvelopeContent {
    /** The first record carried our exact external type; [body] is its payload, untouched. */
    data class Recognised(val body: ByteArray) : EnvelopeContent {
        override fun equals(other: Any?) = other is Recognised && body.contentEquals(other.body)
        override fun hashCode() = body.contentHashCode()
    }
    /** Something else is on the tag; [description] names the offending TNF/type for a message. */
    data class Foreign(val description: String) : EnvelopeContent
    data object Empty : EnvelopeContent
}

class NdefEnvelope(val identity: TagIdentity) {
    /** Type gate before anything else (invariant 1): TNF, then the exact type, then the body. */
    fun decode(records: List<NdefRecordData>): EnvelopeContent {
        val first = records.firstOrNull() ?: return EnvelopeContent.Empty
        val type = String(first.type, Charsets.US_ASCII)
        if (first.tnf != TNF_EXTERNAL_TYPE || type != identity.externalType) {
            return EnvelopeContent.Foreign("tnf=${first.tnf} type=$type")
        }
        return EnvelopeContent.Recognised(first.payload)
    }

    /** One external record carrying [body]; an AAR second only if the identity asks for one (never, in NoteTag). */
    fun encode(body: ByteArray): List<NdefRecordData> = listOfNotNull(
        NdefRecordData(TNF_EXTERNAL_TYPE, identity.externalType.toByteArray(Charsets.US_ASCII), body),
        identity.aarPackage?.let { NdefRecordData(TNF_EXTERNAL_TYPE, AAR_TYPE.toByteArray(Charsets.US_ASCII), it.toByteArray(Charsets.US_ASCII)) },
    )

    companion object {
        const val TNF_EXTERNAL_TYPE: Int = 0x04
        const val AAR_TYPE: String = "android.com:pkg"
    }
}
```

- [ ] **Step 3: `JoplinId` — validate, normalise, pack (target §4.9)**

`tag/JoplinId.kt`:

```kotlin
package com.loosecannon.notetag.core.tag

object JoplinId {
    /** A compact representation is legitimate only for exactly 32 hex characters (target §4.9). */
    val PATTERN = Regex("^[0-9a-fA-F]{32}$")
    private val OPEN_NOTE = Regex("""^joplin://x-callback-url/openNote\?id=([^&\s]+)$""")

    /** The 32 lower-case hex form, or null when the candidate is not a conforming id. */
    fun normalise(candidate: String): String? =
        if (PATTERN.matches(candidate)) candidate.lowercase() else null

    fun toBytes(id32lower: String): ByteArray {
        require(id32lower.length == 32 && id32lower == id32lower.lowercase() && PATTERN.matches(id32lower)) { "not a normalised id" }
        return ByteArray(16) { i -> id32lower.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    /** Re-renders 16 bytes as 32 lower-case hex, so a mixed-case input round-trips lower-case. */
    fun fromBytes(bytes: ByteArray): String {
        require(bytes.size == 16) { "a note id is 16 bytes, got ${bytes.size}" }
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun openNoteUri(id32lower: String): String = "joplin://x-callback-url/openNote?id=$id32lower"

    /** The id inside a Joplin openNote URI, un-normalised (call [normalise] to decide compactness). */
    fun idFromOpenNoteUri(uri: String): String? = OPEN_NOTE.find(uri)?.groupValues?.get(1)
}
```

- [ ] **Step 4: The content and the codec**

`tag/NoteTagContent.kt`:

```kotlin
package com.loosecannon.notetag.core.tag

import java.util.UUID

sealed interface NoteTagContent {
    /** The three kinds NoteTag writes. */
    sealed interface Writable : NoteTagContent
    /** [id] is 32 lower-case hex, always. */
    data class JoplinNote(val id: String) : Writable
    data class Uri(val uri: String) : Writable
    data class LocalRef(val uuid: UUID) : Writable

    data class NewerVersion(val version: Int) : NoteTagContent
    data class UnknownKind(val kind: Int) : NoteTagContent
    data class Malformed(val reason: String) : NoteTagContent
    data class Foreign(val description: String) : NoteTagContent
    data object Empty : NoteTagContent
}
```

`tag/NoteTagCodec.kt`:

```kotlin
package com.loosecannon.notetag.core.tag

import com.loosecannon.notetag.core.nfc.EnvelopeContent
import com.loosecannon.notetag.core.nfc.NdefEnvelope
import com.loosecannon.notetag.core.nfc.NdefRecordData
import com.loosecannon.notetag.core.nfc.TagIdentity
import java.nio.ByteBuffer
import java.util.UUID

/**
 * NoteTag v1: one external record, body `version | kind | flags | kind-body` (target §4.9).
 * Version and kind meanings are NoteTag's; the envelope, framing and capacity are the library's.
 */
class NoteTagCodec(identity: TagIdentity) {
    private val envelope = NdefEnvelope(identity)

    fun decode(records: List<NdefRecordData>): NoteTagContent = when (val e = envelope.decode(records)) {
        EnvelopeContent.Empty -> NoteTagContent.Empty
        is EnvelopeContent.Foreign -> NoteTagContent.Foreign(e.description)
        is EnvelopeContent.Recognised -> parse(e.body)
    }

    fun encode(content: NoteTagContent.Writable): List<NdefRecordData> = envelope.encode(body(content))

    fun body(content: NoteTagContent.Writable): ByteArray {
        val (kind, kindBody) = when (content) {
            is NoteTagContent.JoplinNote -> KIND_JOPLIN_NOTE to JoplinId.toBytes(content.id)
            is NoteTagContent.Uri -> KIND_URI to content.uri.toByteArray(Charsets.UTF_8)
            is NoteTagContent.LocalRef -> KIND_LOCAL_REF to ByteBuffer.allocate(16)
                .putLong(content.uuid.mostSignificantBits).putLong(content.uuid.leastSignificantBits).array()
        }
        return byteArrayOf(VERSION.toByte(), kind.toByte(), FLAGS.toByte()) + kindBody
    }

    private fun parse(body: ByteArray): NoteTagContent {
        if (body.isEmpty()) return NoteTagContent.Malformed("empty payload")
        val version = body[0].toInt() and 0xff
        if (version > VERSION) return NoteTagContent.NewerVersion(version)      // before any length check
        if (version == 0) return NoteTagContent.Malformed("version 0")
        if (body.size < HEADER) return NoteTagContent.Malformed("payload is ${body.size} bytes, header needs $HEADER")
        if (body[2].toInt() != FLAGS) return NoteTagContent.Malformed("flags 0x%02x are reserved".format(body[2].toInt() and 0xff))
        val kind = body[1].toInt() and 0xff
        val kindBody = body.copyOfRange(HEADER, body.size)
        return when (kind) {
            KIND_JOPLIN_NOTE -> if (kindBody.size == 16) NoteTagContent.JoplinNote(JoplinId.fromBytes(kindBody))
                else NoteTagContent.Malformed("note id is ${kindBody.size} bytes, expected 16")
            KIND_URI -> if (kindBody.isNotEmpty()) NoteTagContent.Uri(String(kindBody, Charsets.UTF_8))
                else NoteTagContent.Malformed("empty uri")
            KIND_LOCAL_REF -> if (kindBody.size == 16) {
                val b = ByteBuffer.wrap(kindBody); NoteTagContent.LocalRef(UUID(b.long, b.long))
            } else NoteTagContent.Malformed("local ref is ${kindBody.size} bytes, expected 16")
            else -> NoteTagContent.UnknownKind(kind)
        }
    }

    companion object {
        const val VERSION = 0x01
        const val FLAGS = 0x00
        const val HEADER = 3
        const val KIND_JOPLIN_NOTE = 0x01
        const val KIND_URI = 0x02
        const val KIND_LOCAL_REF = 0x03
    }
}
```

- [ ] **Step 5: The tests (write them first; they fail until the files above exist)**

`tag/JoplinIdTest.kt`:

```kotlin
package com.loosecannon.notetag.core.tag

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JoplinIdTest {
    private val mixed = "0123456789ABCDEFfedcba9876543210"
    @Test fun aConformingIdNormalisesToLowerCase() = assertEquals(mixed.lowercase(), JoplinId.normalise(mixed))
    @Test fun anythingElseIsNotCompact() {
        for (bad in listOf("", "0123456789abcdef", "0123456789abcdef-fedcba9876543210", "0123456789abcdeffedcba987654321g", "0123456789abcdeffedcba98765432100"))
            assertNull(JoplinId.normalise(bad), bad)
    }
    @Test fun sixteenBytesRoundTripLowerCase() {
        val bytes = JoplinId.toBytes(mixed.lowercase())
        assertEquals(16, bytes.size)
        assertContentEquals(byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0xfe.toByte(), 0xdc.toByte(), 0xba.toByte(), 0x98.toByte(), 0x76, 0x54, 0x32, 0x10), bytes)
        assertEquals(mixed.lowercase(), JoplinId.fromBytes(bytes))
    }
    @Test fun theOpenNoteUriIsParsedAndBuilt() {
        val id = mixed.lowercase()
        assertEquals(id, JoplinId.idFromOpenNoteUri(JoplinId.openNoteUri(id)))
        assertEquals(mixed, JoplinId.idFromOpenNoteUri("joplin://x-callback-url/openNote?id=$mixed"))
        assertNull(JoplinId.idFromOpenNoteUri("joplin://x-callback-url/openFolder?id=$id"))
        assertNull(JoplinId.idFromOpenNoteUri("https://example.org/openNote?id=$id"))
    }
}
```

`nfc/NdefEnvelopeTest.kt` — a neutral identity `TagIdentity("com.example.app", "tag")`: recognised body byte-for-byte; a different domain with the same name is `Foreign` and the description carries the full type; our type under TNF 0x01 is `Foreign`; an empty list is `Empty`; only the first record matters; `encode` yields exactly one record with `aarPackage = null` and two (ours first, AAR second, type `android.com:pkg`, payload the package as ASCII) with it set. Six tests, modelled on ServiceTag's `NdefCodecTest`/`NdefCodecV1Test` — read them for the shape, keep NoteTag's identity out of this file.

`tag/NoteTagCodecTest.kt`:

```kotlin
package com.loosecannon.notetag.core.tag

import com.loosecannon.notetag.core.nfc.NdefEnvelope
import com.loosecannon.notetag.core.nfc.NdefRecordData
import com.loosecannon.notetag.core.nfc.TagIdentity
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NoteTagCodecTest {
    // TODO(Phase F): this test names an application; it stays behind when nfc-core moves to nfc-tag-core.
    private val identity = TagIdentity("com.loosecannon.notetag", "tag")
    private val codec = NoteTagCodec(identity)
    private val id = "0123456789abcdeffedcba9876543210"

    @Test fun aJoplinNoteRoundTripsAsOneRecordOf49Bytes() {
        val records = codec.encode(NoteTagContent.JoplinNote(id))
        assertEquals(1, records.size)                                          // no AAR, ever (O13)
        assertEquals(NdefEnvelope.TNF_EXTERNAL_TYPE, records[0].tnf)
        assertContentEquals("com.loosecannon.notetag:tag".toByteArray(Charsets.US_ASCII), records[0].type)
        assertEquals(19, records[0].payload.size)                              // 3 header + 16 id
        assertContentEquals(byteArrayOf(0x01, 0x01, 0x00), records[0].payload.copyOfRange(0, 3))
        assertEquals(3 + 27 + 19, 3 + records[0].type.size + records[0].payload.size)   // 49 B message (target §4.9)
        assertEquals(NoteTagContent.JoplinNote(id), codec.decode(records))
    }
    @Test fun aMixedCaseIdRoundTripsLowerCase() {
        val mixed = "0123456789ABCDEFfedcba9876543210"
        val normalised = JoplinId.normalise(mixed)!!
        val back = codec.decode(codec.encode(NoteTagContent.JoplinNote(normalised)))
        assertEquals(NoteTagContent.JoplinNote(mixed.lowercase()), back)
        assertEquals(back, codec.decode(codec.encode(NoteTagContent.JoplinNote(mixed.lowercase()))))
    }
    @Test fun aUriRoundTripsVerbatimUtf8() {
        val uri = "https://example.org/notes/ünïcödé?x=1"
        val records = codec.encode(NoteTagContent.Uri(uri))
        assertEquals(1, records.size)
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x00) + uri.toByteArray(Charsets.UTF_8), records[0].payload)
        assertEquals(NoteTagContent.Uri(uri), codec.decode(records))
    }
    @Test fun aLocalRefRoundTripsItsUuid() {
        val uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val records = codec.encode(NoteTagContent.LocalRef(uuid))
        assertEquals(19, records[0].payload.size)
        assertEquals(0x03, records[0].payload[1].toInt())
        assertEquals(NoteTagContent.LocalRef(uuid), codec.decode(records))
    }
    @Test fun malformedBodiesAreNamedNotThrown() {
        fun ours(body: ByteArray) = listOf(NdefRecordData(0x04, "com.loosecannon.notetag:tag".toByteArray(Charsets.US_ASCII), body))
        assertIs<NoteTagContent.Malformed>(codec.decode(ours(byteArrayOf())))
        assertIs<NoteTagContent.Malformed>(codec.decode(ours(byteArrayOf(0x01))))
        assertIs<NoteTagContent.Malformed>(codec.decode(ours(byteArrayOf(0x00, 0x01, 0x00) + ByteArray(16))))
        assertIs<NoteTagContent.Malformed>(codec.decode(ours(byteArrayOf(0x01, 0x01, 0x01) + ByteArray(16))))   // reserved flags
        assertIs<NoteTagContent.Malformed>(codec.decode(ours(byteArrayOf(0x01, 0x01, 0x00) + ByteArray(15))))   // short id
        assertIs<NoteTagContent.Malformed>(codec.decode(ours(byteArrayOf(0x01, 0x02, 0x00))))                    // empty uri
        assertIs<NoteTagContent.Malformed>(codec.decode(ours(byteArrayOf(0x01, 0x03, 0x00) + ByteArray(17))))   // long ref
    }
    @Test fun anUnknownKindAndANewerVersionAreReportedNotParsed() {
        fun ours(body: ByteArray) = listOf(NdefRecordData(0x04, "com.loosecannon.notetag:tag".toByteArray(Charsets.US_ASCII), body))
        assertEquals(NoteTagContent.UnknownKind(0x04), codec.decode(ours(byteArrayOf(0x01, 0x04, 0x00, 0x7f))))
        assertEquals(NoteTagContent.NewerVersion(0x02), codec.decode(ours(byteArrayOf(0x02))))       // before any length check
    }
    @Test fun aServiceTagRecordIsForeignEvenWithAPlausibleBody() {   // §23, sibling isolation in NoteTag's direction
        val sibling = listOf(NdefRecordData(0x04, "com.loosecannon.servicetag:tag".toByteArray(Charsets.US_ASCII), byteArrayOf(0x01, 0x00) + ByteArray(16)))
        val foreign = assertIs<NoteTagContent.Foreign>(codec.decode(sibling))
        assertEquals("tnf=4 type=com.loosecannon.servicetag:tag", foreign.description)
    }
    @Test fun ourTypeUnderTheWrongTnfIsForeign() {
        val wrong = listOf(NdefRecordData(0x01, "com.loosecannon.notetag:tag".toByteArray(Charsets.US_ASCII), byteArrayOf(0x01, 0x01, 0x00) + ByteArray(16)))
        assertIs<NoteTagContent.Foreign>(codec.decode(wrong))
    }
    @Test fun anEmptyMessageIsEmpty() = assertEquals(NoteTagContent.Empty, codec.decode(emptyList()))
}
```

- [ ] **Step 6: Run red, then green, then commit**

```bash
./gradlew :core:test --console=plain          # red: unresolved references
# create the six main files
./gradlew :core:test --console=plain          # green
git rm -q core/src/test/kotlin/com/loosecannon/notetag/core/BuildSanityTest.kt
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain
git add -A core
git commit -m "notetag v1 tag format: version|kind|flags|body, three kinds, no aar, foreign is foreign"
```

Expected: every `NoteTagCodecTest`, `JoplinIdTest` and `NdefEnvelopeTest` case green; no test asserts a character count.

---

### Task 5 (§A.2 rows 6 and 8, the decision and the policy): serialised size, the copied launch policy, the write planner

**Files:**
- Create: `core/src/main/kotlin/com/loosecannon/notetag/core/nfc/NdefSize.kt`, `links/LinkLaunchPolicy.kt`, `write/WritePlanner.kt`
- Create: `core/src/test/kotlin/com/loosecannon/notetag/core/nfc/NdefSizeTest.kt`, `links/LinkLaunchPolicyTest.kt`, `write/WritePlannerTest.kt`

**Interfaces:**
- Produces: `NdefSize.serialisedSize(records): Int` (the pure-JVM mirror of `NdefMessage.toByteArray().size`, pinned against the real thing on the emulator in Task 10); `LinkLaunchPolicy` (`extractUri`, `check`, `ALLOWED_SCHEMES`, `BLOCKED_SCHEMES`, `LinkCheck.Accepted/NeedsConfirmation/Rejected`); `WritePlanner.plan(sharedText, maxSize, codec, newUuid): WritePlan` with `WritePlan.Compact(records, content)`, `FullUri(records, content)`, `DeviceBound(records, content, target)`, `Refused(reason)`.

- [ ] **Step 1: `NdefSize`**

```kotlin
package com.loosecannon.notetag.core.nfc

/**
 * The size of the serialised NDEF message — exactly what `NdefMessage.toByteArray().size` returns
 * on Android, computed here without Android so the writer can decide off-device (invariant 7).
 * Per record: 1 header byte + 1 type-length byte + 1 payload-length byte (short record, payload
 * < 256) or 4 (long record) + the type + the payload. No ID field (IL = 0), no TLV framing, no
 * terminator: the Type-2 framing belongs to Android and the tag, never to this arithmetic.
 */
object NdefSize {
    fun serialisedSize(records: List<NdefRecordData>): Int = records.sumOf { r ->
        1 + 1 + (if (r.payload.size < 256) 1 else 4) + r.type.size + r.payload.size
    }
}
```

`NdefSizeTest`: a 49-byte `JOPLIN_NOTE` message is 49; a `URI` record with a 255-byte payload is `3 + 27 + 255`; with 256 it is `6 + 27 + 256`; two records sum; the empty list is 0.

- [ ] **Step 2: `LinkLaunchPolicy` — copied, not shared (row 8)**

Copy `../ServiceTag-split/core/src/main/kotlin/com/loosecannon/servicetag/core/links/LinkLaunchPolicy.kt` (52 lines) into `links/LinkLaunchPolicy.kt`, package `com.loosecannon.notetag.core.links`, header comment `// Copied from ServiceTag's core/links/LinkLaunchPolicy.kt (8a94872 lineage) on purpose: the policy is a product decision, not a library mechanism (target §4.7).` Keep the allowlist (`joplin`, `obsidian`, `logseq`, `http`, `https`), the blocklist, the whitespace/control-character rejection and `extractUri` exactly; `LinkKind` comes with it. Copy `LinkLaunchPolicyTest.kt` likewise and add one test per blocked scheme if the copied file does not already have them (`javascript`, `file`, `content`, `intent`, `android-app`, `tel`, `sms`, `mailto` each → `Rejected`), plus `"joplin://x\\u0000y"` and `"https://a b"` → `Rejected`.

- [ ] **Step 3: The planner**

```kotlin
package com.loosecannon.notetag.core.write

import com.loosecannon.notetag.core.links.LinkCheck
import com.loosecannon.notetag.core.links.LinkLaunchPolicy
import com.loosecannon.notetag.core.nfc.NdefRecordData
import com.loosecannon.notetag.core.nfc.NdefSize
import com.loosecannon.notetag.core.tag.JoplinId
import com.loosecannon.notetag.core.tag.NoteTagCodec
import com.loosecannon.notetag.core.tag.NoteTagContent
import java.util.UUID

sealed interface WritePlan {
    val records: List<NdefRecordData>
    /** A stable compact identifier: self-contained, portable. */
    data class Compact(override val records: List<NdefRecordData>, val content: NoteTagContent.JoplinNote) : WritePlan
    /** The full URI fits the measured tag: self-contained, portable. */
    data class FullUri(override val records: List<NdefRecordData>, val content: NoteTagContent.Uri) : WritePlan
    /** Only this phone can resolve it; the mapping must be persisted before the write (target §4.9). */
    data class DeviceBound(override val records: List<NdefRecordData>, val content: NoteTagContent.LocalRef, val target: String) : WritePlan
    data class Refused(val reason: String) : WritePlan { override val records: List<NdefRecordData> get() = emptyList() }
}

/**
 * The writer's decision, in this order (O14): compact if a stable compact id exists → the full URI
 * if the exact encoded message fits [maxSize] → LOCAL_REF. [maxSize] is the tag's measured
 * `Ndef.maxSize`; the comparison is message bytes against message bytes, no TLV, no characters.
 */
object WritePlanner {
    fun plan(sharedText: String?, maxSize: Int, codec: NoteTagCodec, newUuid: () -> UUID = UUID::randomUUID): WritePlan {
        val uri = LinkLaunchPolicy.extractUri(sharedText) ?: return WritePlan.Refused("no link in the shared text")
        val check = LinkLaunchPolicy.check(uri)
        if (check is LinkCheck.Rejected) return WritePlan.Refused(check.reason)
        val joplin = JoplinId.idFromOpenNoteUri(uri)?.let(JoplinId::normalise)
        if (joplin != null) {
            val c = NoteTagContent.JoplinNote(joplin)
            return WritePlan.Compact(codec.encode(c), c)
        }
        val full = NoteTagContent.Uri(uri)
        val fullRecords = codec.encode(full)
        if (NdefSize.serialisedSize(fullRecords) <= maxSize) return WritePlan.FullUri(fullRecords, full)
        val ref = NoteTagContent.LocalRef(newUuid())
        return WritePlan.DeviceBound(codec.encode(ref), ref, uri)
    }
}
```

- [ ] **Step 4: `WritePlannerTest` — capacity from injected `maxSize` (§D.3), fallback only when it truly does not fit**

```kotlin
package com.loosecannon.notetag.core.write

import com.loosecannon.notetag.core.nfc.NdefSize
import com.loosecannon.notetag.core.nfc.TagIdentity
import com.loosecannon.notetag.core.tag.NoteTagCodec
import com.loosecannon.notetag.core.tag.NoteTagContent
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WritePlannerTest {
    private val codec = NoteTagCodec(TagIdentity("com.loosecannon.notetag", "tag"))
    private val fixedUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    private val uri = "https://example.org/some/rather/long/path/that/we/will/measure"
    private val needed = NdefSize.serialisedSize(codec.encode(NoteTagContent.Uri(uri)))

    @Test fun aConformingJoplinIdIsCompactWhateverTheTag() {
        val p = assertIs<WritePlan.Compact>(WritePlanner.plan("look: joplin://x-callback-url/openNote?id=0123456789ABCDEFfedcba9876543210", 0, codec))
        assertEquals("0123456789abcdeffedcba9876543210", p.content.id)
        assertEquals(49, NdefSize.serialisedSize(p.records))
    }
    @Test fun aNonConformingJoplinIdFallsThroughToTheFullUri() {
        val shared = "joplin://x-callback-url/openNote?id=0123456789abcdef"     // 16 chars: not compact
        val p = assertIs<WritePlan.FullUri>(WritePlanner.plan(shared, 1000, codec))
        assertEquals(shared, p.content.uri)
    }
    @Test fun theUriIsWrittenWhenItFitsExactly() {
        assertIs<WritePlan.FullUri>(WritePlanner.plan(uri, needed, codec) { fixedUuid })
        assertIs<WritePlan.FullUri>(WritePlanner.plan(uri, needed + 1, codec) { fixedUuid })
    }
    @Test fun oneByteShortMeansLocalRef() {
        val p = assertIs<WritePlan.DeviceBound>(WritePlanner.plan(uri, needed - 1, codec) { fixedUuid })
        assertEquals(uri, p.target)
        assertEquals(NoteTagContent.LocalRef(fixedUuid), p.content)
        assertEquals(49, NdefSize.serialisedSize(p.records))     // a LOCAL_REF is the same 19-byte body shape
    }
    @Test fun aRejectedSchemeIsRefusedBeforeAnyEncoding() {
        assertIs<WritePlan.Refused>(WritePlanner.plan("javascript:alert(1)", 1000, codec))
        assertIs<WritePlan.Refused>(WritePlanner.plan("no link here at all", 1000, codec))
    }
}
```

- [ ] **Step 5: Gate and commit**

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain
git add -A core
git commit -m "the writer's decision: compact, then the uri if it fits the measured tag, then local ref"
```

---

### Task 6 (§A.2 row 7, the store): the minimal local store, atomically replaced

**Files:**
- Create: `core/src/main/kotlin/com/loosecannon/notetag/core/store/TagEntry.kt`, `store/TagStore.kt`, `store/JsonFileTagStore.kt`
- Create: `core/src/test/kotlin/com/loosecannon/notetag/core/store/JsonFileTagStoreTest.kt`

**Interfaces:**
- Produces: `TagEntry(uuid: String, kind: String, label: String, target: String?, writtenAt: Long?, lastOpenedAt: Long?)` — **`writtenAt == null` means persisted but not confirmed written**; `interface TagStore { suspend fun put(entry); suspend fun get(uuid): TagEntry?; suspend fun list(): List<TagEntry>; suspend fun remove(uuid); suspend fun confirm(uuid, at); suspend fun touch(uuid, at) }` where `get` sees every entry and `list` returns **confirmed entries only**; `JsonFileTagStore(file, json = Json, replace: AtomicReplace = FsyncRename)` where `fun interface AtomicReplace { fun replace(target: File, bytes: ByteArray) }` is the injection point for "persist fails".

- [ ] **Step 1: The types and the store**

```kotlin
package com.loosecannon.notetag.core.store

import kotlinx.serialization.Serializable

/**
 * One tag this phone wrote, or tried to. Only [target] is load-bearing, and only for LOCAL_REF
 * entries. [writtenAt] is the write-history confirmation: null while a LOCAL_REF mapping has been
 * persisted (before the write) but no verified read-back has confirmed the tag holds it. Such an
 * entry is still resolvable through [TagStore.get] — a live tag may exist — but it is not shown as
 * a tag this phone wrote (target §4.9, owner correction 2026-09-17).
 */
@Serializable
data class TagEntry(
    val uuid: String,
    val kind: String,          // "JOPLIN_NOTE" | "URI" | "LOCAL_REF"
    val label: String,         // what the user saw: the uri, or the note id
    val target: String? = null,   // the LOCAL_REF target; null for self-contained kinds
    val writtenAt: Long? = null,  // null = persisted, not confirmed written
    val lastOpenedAt: Long? = null,
)

interface TagStore {
    suspend fun put(entry: TagEntry)
    /** Every entry, confirmed or not: resolution must see a retained mapping. */
    suspend fun get(uuid: String): TagEntry?
    /** Confirmed writes only (`writtenAt != null`), newest first: the write history the UI shows. */
    suspend fun list(): List<TagEntry>
    suspend fun remove(uuid: String)
    /** A verified read-back happened: the entry becomes part of the write history. */
    suspend fun confirm(uuid: String, at: Long)
    suspend fun touch(uuid: String, at: Long)
}
```

```kotlin
package com.loosecannon.notetag.core.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
private data class StoreFile(val version: Int = 1, val entries: List<TagEntry> = emptyList())

/** temp file → fsync → atomic rename over the store (target §4.9). */
fun interface AtomicReplace { fun replace(target: File, bytes: ByteArray) }

object FsyncRename : AtomicReplace {
    override fun replace(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out -> out.write(bytes); out.fd.sync() }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}

/**
 * A single JSON file, rewritten whole on every change. "No Room unless it earns it" (P19): a
 * handful of entries, one writer, and a swap-able interface if that ever changes.
 */
class JsonFileTagStore(
    private val file: File,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
    private val replace: AtomicReplace = FsyncRename,
) : TagStore {
    private val lock = Mutex()

    private fun read(): StoreFile = if (!file.exists()) StoreFile() else
        runCatching { json.decodeFromString<StoreFile>(file.readText()) }.getOrElse { throw StoreCorrupt(file.name, it) }

    private fun write(s: StoreFile) = replace.replace(file, json.encodeToString(StoreFile.serializer(), s).toByteArray())

    override suspend fun put(entry: TagEntry) = lock.withLock { withContext(Dispatchers.IO) {
        val s = read(); write(s.copy(entries = s.entries.filterNot { it.uuid == entry.uuid } + entry))
    } }
    override suspend fun get(uuid: String): TagEntry? = lock.withLock { withContext(Dispatchers.IO) { read().entries.firstOrNull { it.uuid == uuid } } }
    override suspend fun list(): List<TagEntry> = lock.withLock { withContext(Dispatchers.IO) {
        read().entries.filter { it.writtenAt != null }.sortedByDescending { it.writtenAt }
    } }
    override suspend fun confirm(uuid: String, at: Long) = lock.withLock { withContext(Dispatchers.IO) {
        val s = read(); write(s.copy(entries = s.entries.map { if (it.uuid == uuid) it.copy(writtenAt = at) else it }))
    } }
    override suspend fun remove(uuid: String) = lock.withLock { withContext(Dispatchers.IO) {
        val s = read(); if (s.entries.any { it.uuid == uuid }) write(s.copy(entries = s.entries.filterNot { it.uuid == uuid }))
    } }
    override suspend fun touch(uuid: String, at: Long) = lock.withLock { withContext(Dispatchers.IO) {
        val s = read(); write(s.copy(entries = s.entries.map { if (it.uuid == uuid) it.copy(lastOpenedAt = at) else it }))
    } }
}

class StoreCorrupt(name: String, cause: Throwable) : RuntimeException("the tag store $name could not be read", cause)
```

- [ ] **Step 2: `JsonFileTagStoreTest` (JUnit 5, `@TempDir`)**

Cases: a missing file lists nothing; put/get/list/remove round-trip; `put` of an existing uuid replaces; `touch` sets `lastOpenedAt`; **an entry put with `writtenAt = null` is returned by `get` but absent from `list`, and appears in `list` after `confirm`** (the retained-is-not-written rule); after every write no `.tmp` file remains and the JSON is valid; a corrupt file throws `StoreCorrupt` (never a bare parse exception) on read; a `replace` that throws leaves the previous file byte-identical (**the "persist fails" seam Task 7 relies on**); concurrent `put`s from two coroutines both land (the mutex).

- [ ] **Step 3: Gate and commit**

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain
git add -A core
git commit -m "a json file store, replaced atomically, behind a small interface"
```

---

### Task 7 (§A.2 rows 7 and 9, the write protocol): the interim NFC adapter, the `TagIo` seam, and the write controller with the `LOCAL_REF` sequence

**Files:**
- Create (interim copies, header-commented): `app/src/main/kotlin/com/loosecannon/notetag/nfc/NdefBridge.kt`, `nfc/NfcReaderModeSession.kt`, `nfc/TagWriter.kt`, `nfc/TagIo.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notetag/core/nfc/OverwriteWording.kt`, `app/src/main/kotlin/com/loosecannon/notetag/write/NoteTagWriteController.kt`
- Create: `core/src/test/kotlin/com/loosecannon/notetag/core/nfc/OverwriteWordingTest.kt`, `app/src/test/kotlin/com/loosecannon/notetag/write/NoteTagWriteControllerTest.kt`, `app/src/test/kotlin/com/loosecannon/notetag/write/FakeTagIo.kt`

**Interfaces:**
- Consumes: `WritePlanner`, `NoteTagCodec`, `TagStore`.
- Produces: `TagHandle`/`NfcTagHandle`/`TagIo`/`RealTagIo(codec)` (the seam; `inspect` returns `TagInspection?` with `maxSize`, `existing: NoteTagContent`, `writable`, `needsFormat`; `write(handle, records, lock=false): WriteResult`); `OverwriteWording.reason(existing: NoteTagContent, intended: Writable): String?` (null = proceed) and `OverwriteWording.DEVICE_BOUND = "This tag needs this phone to open. Back up NoteTag to protect the link."`; `NoteTagWriteController(tagIo, codec, store, sharedText, scope, clock, newUuid)` with `state: StateFlow<WriteState>` (`Waiting(preview)`, `Confirm(reasons: List<String>, action: String)` — `action` is "Write over it" when an overwrite reason is present, else "Write"; the device-bound sentence is one of the reasons whenever the plan is `DeviceBound`, `Writing`, `Written(entry, deviceBound: Boolean)` (the entry as confirmed), `Refused(reason)`, `Error(message)`), `onTag(handle)`, `confirm()`, `cancel()`, `abandon()`.

- [ ] **Step 1: The four interim copies**

Copy from `../ServiceTag-split/app/src/main/kotlin/com/loosecannon/servicetag/nfc/`: `NdefBridge.kt` (43 lines), `NfcReaderModeSession.kt` (39 lines, **doc comment intact**), `TagWriter.kt` (126 lines); and the `TagHandle`/`NfcTagHandle`/`TagIo`/`RealTagIo` block from `ui/scan/TagWriteController.kt:32-65` into `nfc/TagIo.kt`. Package → `com.loosecannon.notetag.nfc`; every `NdefCodec` reference becomes `NoteTagCodec` and every `TagPayload` becomes `NoteTagContent` (`TagInspection.existing: NoteTagContent`); `RealTagIo.write` always passes `lock = false` from the controller (NoteTag has no lock UI in this phase; `TagIo.lock` stays on the seam for the library's sake). Header comment on each: `// Interim copy of ServiceTag's app/src/main/kotlin/com/loosecannon/servicetag/nfc/<file> (dc1bb1c lineage); Phase G replaces it with nfc-tag-core.` **Do not "improve" them**: the `format(message)` shape on the formatable path is known to be superseded by the library's `format(null)` two-tap sequence (target §4.3 invariant 7); that change lands in Phase F/G, and no physical tag is written in this phase.

- [ ] **Step 2: NoteTag's own sentences (P11, invariant 13)**

```kotlin
package com.loosecannon.notetag.core.nfc

import com.loosecannon.notetag.core.tag.NoteTagContent

/** Read-before-write: what NoteTag says when the tag already holds something. Null means write without asking. */
object OverwriteWording {
    const val SIBLING_DOMAIN = "com.loosecannon.servicetag"

    fun reason(existing: NoteTagContent, intended: NoteTagContent.Writable): String? = when (existing) {
        NoteTagContent.Empty -> null
        is NoteTagContent.Foreign ->
            if (existing.description.contains("type=$SIBLING_DOMAIN:")) "This tag belongs to ServiceTag."
            else "This tag holds something else (${existing.description})."
        is NoteTagContent.JoplinNote, is NoteTagContent.Uri, is NoteTagContent.LocalRef ->
            if (existing == intended) null else "This NoteTag tag already points somewhere else."
        is NoteTagContent.NewerVersion -> "This tag was written by a newer NoteTag (format ${existing.version})."
        is NoteTagContent.UnknownKind -> "This NoteTag tag holds a kind this version does not know (${existing.kind})."
        is NoteTagContent.Malformed -> "This tag holds unreadable NoteTag content (${existing.reason})."
    }
}
```

`OverwriteWordingTest`: empty → null; same content → null (a retry); a different NoteTag tag → the "points somewhere else" sentence; a ServiceTag record → the sentence **names ServiceTag** and nothing else about it (row 9 / P11); another foreign type → "holds something else"; newer / unknown kind / malformed each → their sentence.

- [ ] **Step 3: The controller — single-flight, read-before-write, and the `LOCAL_REF` sequence**

```kotlin
package com.loosecannon.notetag.write

import com.loosecannon.notetag.core.nfc.OverwriteWording
import com.loosecannon.notetag.core.store.TagEntry
import com.loosecannon.notetag.core.store.TagStore
import com.loosecannon.notetag.core.tag.NoteTagCodec
import com.loosecannon.notetag.core.tag.NoteTagContent
import com.loosecannon.notetag.core.write.WritePlan
import com.loosecannon.notetag.core.write.WritePlanner
import com.loosecannon.notetag.nfc.TagHandle
import com.loosecannon.notetag.nfc.TagIo
import com.loosecannon.notetag.nfc.WriteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
    private val clock: () -> Long = System::currentTimeMillis,
    private val newUuid: () -> UUID = UUID::randomUUID,
) {
    private val _state = MutableStateFlow<WriteState>(WriteState.Waiting("Hold a tag to the phone."))
    val state: StateFlow<WriteState> = _state
    private val busy = AtomicBoolean(false)
    private var pending: Pending? = null      // a confirmation awaits the next tap of the same tag content
    private var done = false

    /** Consent is for THIS existing content and THIS plan kind (invariant 10). */
    private class Pending(val plan: WritePlan, val existing: NoteTagContent, val consented: Boolean)

    /** Runs on the reader-mode binder thread; every state change is a flow emission. */
    fun onTag(tag: TagHandle) {
        if (done || !busy.compareAndSet(false, true)) return          // single-flight (invariant 11)
        scope.launch {
            try { handle(tag) }
            catch (t: Throwable) { _state.value = WriteState.Error(t.message ?: "the tag could not be read") }
            finally { busy.set(false) }
        }
    }

    private suspend fun handle(tag: TagHandle) {
        val inspection = tagIo.inspect(tag) ?: run { _state.value = WriteState.Error("This tag type is not supported."); return }
        if (!inspection.writable) { _state.value = WriteState.Error("This tag is read-only."); return }
        val maxSize = if (inspection.needsFormat) UNMEASURED else inspection.maxSize
        val plan = WritePlanner.plan(sharedText, maxSize, codec, newUuid)
        if (plan is WritePlan.Refused) { _state.value = WriteState.Refused(plan.reason); return }
        val prior = pending
        pending = null
        val reasons = listOfNotNull(
            OverwriteWording.reason(inspection.existing, contentOf(plan)),
            if (plan is WritePlan.DeviceBound) OverwriteWording.DEVICE_BOUND else null,   // the warning BEFORE the write
        )
        val sameQuestion = prior?.consented == true && prior.existing == inspection.existing && prior.plan::class == plan::class
        if (reasons.isNotEmpty() && !sameQuestion) {
            pending = Pending(plan, inspection.existing, consented = false)
            _state.value = WriteState.Confirm(reasons, action = if (reasons.first() != OverwriteWording.DEVICE_BOUND) "Write over it" else "Write")
            return
        }
        write(tag, plan)
    }

    /** The user pressed Write / Write over it: remember it for the next tap of the same tag (the handle went stale under the sheet). */
    fun confirm() {
        pending = pending?.let { Pending(it.plan, it.existing, consented = true) }
        _state.value = WriteState.Waiting("Hold the same tag to the phone again to write it.")
    }
    fun cancel() { val p = pending; pending = null; p?.let { scope.launch { forget(it.plan) } }; _state.value = WriteState.Waiting("Cancelled. Hold a tag to the phone to try again.") }

    /**
     * The LOCAL_REF sequence (target §4.9): persist first, UNCONFIRMED (writtenAt = null); confirm
     * only on a verified Written; retain — still unconfirmed, still resolvable — on any ambiguous
     * failure, so a tag that may exist resolves and a tag we cannot vouch for is not shown as written.
     */
    private suspend fun write(tag: TagHandle, plan: WritePlan) {
        _state.value = WriteState.Writing
        val entry = entryFor(plan)                                       // writtenAt == null for every plan
        if (plan is WritePlan.DeviceBound) {
            try { store.put(entry) }                                     // (a) durably stored BEFORE the write
            catch (t: Throwable) { _state.value = WriteState.Error("Could not save the link on this phone; nothing was written to the tag."); return }
        }
        when (val r = tagIo.write(tag, plan.records, lock = false)) {
            is WriteResult.Written -> {
                val at = clock()
                if (plan is WritePlan.DeviceBound) runCatching { store.confirm(entry.uuid, at) }   // the read-back is the proof
                else runCatching { store.put(entry.copy(writtenAt = at)) }                          // convenience only: never load-bearing
                done = true
                _state.value = WriteState.Written(entry.copy(writtenAt = at), deviceBound = plan is WritePlan.DeviceBound)
            }
            // pre-write rejections: no bytes can have reached the tag, so the mapping may go
            is WriteResult.TooSmall -> { forget(plan); _state.value = WriteState.Error("This tag is too small: it holds ${r.maxSize} bytes and this needs ${r.needed}.") }
            WriteResult.ReadOnly -> { forget(plan); _state.value = WriteState.Error("This tag is read-only.") }
            WriteResult.Unsupported -> { forget(plan); _state.value = WriteState.Error("This tag type is not supported.") }
            // ambiguous: the write may have landed. RETAIN (rule b).
            is WriteResult.VerifyMismatch -> _state.value = WriteState.Error("The tag did not read back what was written. Try again with the same tag.")
            is WriteResult.Failed -> _state.value = WriteState.Error("Writing failed (${r.reason}). If the tag was touched, it may already hold the link; try again with the same tag.")
        }
    }

    /** Leaving the screen before any write: nothing can have reached a tag, so a pending LOCAL_REF mapping may go. */
    fun abandon(): Job? = pending?.plan?.let { p -> scope.launch { forget(p) } }

    private suspend fun forget(plan: WritePlan) { if (plan is WritePlan.DeviceBound) runCatching { store.remove(plan.content.uuid.toString()) } }

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

    private companion object {
        /** A formatable tag has no measured size until the second tap; plan as if unlimited so the URI is attempted (the write itself reports TooSmall). */
        const val UNMEASURED = Int.MAX_VALUE
    }
}
```

Note the two design points the reviewer must check: for a `DeviceBound` plan the `LOCAL_REF` uuid is the entry's uuid **and** the tag body, and the mapping is stored before `tagIo.write` with `writtenAt = null`. A `Written` result confirms it (`store.confirm`) and never removes it; `VerifyMismatch` and `Failed` never remove it **and never confirm it** — retained, resolvable, hidden from the list; `TooSmall`/`ReadOnly`/`Unsupported` and `abandon()`/`cancel()` remove it.

- [ ] **Step 4: `FakeTagIo` and the controller tests (JUnit 4, `runTest`)**

`FakeTagIo(inspection: TagInspection?, result: WriteResult)` records `writeAttempts` and the records written. A `FailingStore(delegate)` whose `put` throws.

`NoteTagWriteControllerTest`, cases (each names its §A.2 row):
1. *(row 7, failure injection 1)* `DeviceBound` plan (uri longer than `maxSize`), store ok, `tagIo.write` → `Failed("tag left the field")`: state `Error`, `writeAttempts == 1`, **`store.get(uuid)` is not null (RETAINED) and `store.list()` does not contain it (NOT CONFIRMED)**.
2. *(row 7, 2)* `DeviceBound`, `FailingStore`: state `Error` naming "nothing was written", **`writeAttempts == 0`**.
3. *(row 7, 3a)* `DeviceBound`, `tagIo.write` → `TooSmall`: mapping removed (`store.get == null`), `writeAttempts == 1` (the rejection is the writer's pre-write check).
4. *(row 7, 3b)* `DeviceBound`, then `abandon()` before any write (no tap): mapping absent; with a pending confirmation → `cancel()` removes it.
5. *(row 7)* `DeviceBound`, `VerifyMismatch` → RETAINED and not confirmed (`get` non-null, absent from `list`).
6. *(row 7)* `DeviceBound`, `Written` → RETAINED **and confirmed**: `store.list()` contains it with a non-null `writtenAt`, state `Written(deviceBound = true)`.
6b. *(row 7)* `FullUri`, `Written` → a confirmed convenience entry appears in `list`; `FullUri`, `Failed` → nothing in the store at all (portable kinds never need it).
7. *(row 6)* `Compact` plan with `maxSize = 0` still writes 49 bytes (the compact form never needs capacity checked by the planner; the writer's own `TooSmall` covers a genuinely tiny tag).
8. *(row 9 / P11)* existing = a ServiceTag record → state `Confirm(listOf("This tag belongs to ServiceTag."), "Write over it")`, `writeAttempts == 0`; `confirm()` then a second `onTag` with the same existing content → written; a second tap with **different** existing content → `Confirm` again, not written (invariant 10).
8b. *(binding UX)* a `DeviceBound` plan on an empty tag → `Confirm(listOf(OverwriteWording.DEVICE_BOUND), "Write")`, **nothing persisted and nothing written yet**; `confirm()` + the same tag → persisted, written, confirmed, state `Written(deviceBound = true)`; `cancel()` instead → nothing in the store.
8c. *(binding UX)* a `DeviceBound` plan on a ServiceTag tag → one `Confirm` with **both** sentences, overwrite first, action "Write over it"; one consent covers both.
9. *(invariant 11)* two `onTag` calls before the first completes → one inspect.
10. *(row 6)* a `Refused` plan → state `Refused`, no inspect-then-write beyond the inspect.

- [ ] **Step 5: Gate and commit**

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain
git add -A app core
git commit -m "write controller: read first, ask in notetag's words, persist before a local ref, retain on doubt"
```

---

### Task 8 (§A.2 rows 7–9, the read side): `ResolveTap`

**Files:**
- Create: `core/src/main/kotlin/com/loosecannon/notetag/core/resolve/ResolveTap.kt`, `core/src/test/kotlin/com/loosecannon/notetag/core/resolve/ResolveTapTest.kt`

**Interfaces:**
- Produces: `sealed interface TapOutcome { Open(uri: String, entryUuid: String?); Message(text: String) }`; `class ResolveTap(codec, store) { suspend fun resolve(records): TapOutcome }`.

- [ ] **Step 1: The use case**

```kotlin
package com.loosecannon.notetag.core.resolve

import com.loosecannon.notetag.core.links.LinkCheck
import com.loosecannon.notetag.core.links.LinkLaunchPolicy
import com.loosecannon.notetag.core.nfc.NdefRecordData
import com.loosecannon.notetag.core.nfc.OverwriteWording
import com.loosecannon.notetag.core.store.TagStore
import com.loosecannon.notetag.core.tag.JoplinId
import com.loosecannon.notetag.core.tag.NoteTagCodec
import com.loosecannon.notetag.core.tag.NoteTagContent

sealed interface TapOutcome {
    /** Launch [uri] through the safe launcher; [entryUuid] is touched in the store if present. */
    data class Open(val uri: String, val entryUuid: String? = null) : TapOutcome
    data class Message(val text: String) : TapOutcome
}

/**
 * What an ambient tap means. JOPLIN_NOTE and URI never need the store; only LOCAL_REF does (target
 * §4.9). A LOCAL_REF hit on an entry with `writtenAt == null` proves the tag exists; the model permits
 * promoting it with [TagStore.confirm] — deliberately not done in Phase E (owner correction 2026-09-17).
 */
class ResolveTap(private val codec: NoteTagCodec, private val store: TagStore) {
    suspend fun resolve(records: List<NdefRecordData>): TapOutcome = when (val c = codec.decode(records)) {
        is NoteTagContent.JoplinNote -> TapOutcome.Open(JoplinId.openNoteUri(c.id))
        is NoteTagContent.Uri -> when (val check = LinkLaunchPolicy.check(c.uri)) {
            is LinkCheck.Accepted -> TapOutcome.Open(check.uri)
            is LinkCheck.NeedsConfirmation -> TapOutcome.Message("This tag holds a ${check.scheme} link, which NoteTag does not open by itself: ${check.uri}")
            is LinkCheck.Rejected -> TapOutcome.Message("This tag holds a link NoteTag will not open (${check.reason}).")
        }
        is NoteTagContent.LocalRef -> {
            val entry = runCatching { store.get(c.uuid.toString()) }.getOrNull()
            val target = entry?.target
            if (target == null) TapOutcome.Message("This tag was written on another phone, so this phone cannot open it.")
            else when (val check = LinkLaunchPolicy.check(target)) {
                is LinkCheck.Accepted -> TapOutcome.Open(check.uri, entry.uuid)
                else -> TapOutcome.Message("This tag points at a link NoteTag will not open.")
            }
        }
        is NoteTagContent.Foreign ->
            if (c.description.contains("type=${OverwriteWording.SIBLING_DOMAIN}:")) TapOutcome.Message("This tag belongs to ServiceTag, not NoteTag.")
            else TapOutcome.Message("Not a NoteTag tag.")
        is NoteTagContent.Malformed -> TapOutcome.Message("This NoteTag tag is unreadable (${c.reason}).")
        is NoteTagContent.NewerVersion -> TapOutcome.Message("This tag needs a newer NoteTag (format ${c.version}).")
        is NoteTagContent.UnknownKind -> TapOutcome.Message("This tag holds a kind this NoteTag does not know (${c.kind}).")
        NoteTagContent.Empty -> TapOutcome.Message("This tag is empty.")
    }
}
```

- [ ] **Step 2: `ResolveTapTest`** — with a `JsonFileTagStore` in `@TempDir` and one with the file **deleted**: a `JOPLIN_NOTE` opens `joplin://x-callback-url/openNote?id=<lower-case id>` **with no store file present** (row 7); a `URI` opens with no store; a `LOCAL_REF` hit opens its target and carries the uuid — **including a hit on a retained, unconfirmed entry** (`writtenAt == null`), which must resolve exactly like a confirmed one; a `LOCAL_REF` miss is the "another phone" message, not an exception (row 7); a ServiceTag record is the message naming ServiceTag, never `Open` (row 9, §23); a `URI` whose scheme is blocked is a message, never `Open` (§23 "malformed tag does not launch unsafe content"); malformed / newer / unknown kind / empty each a message.

- [ ] **Step 3: Gate and commit**

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain
git add -A core
git commit -m "resolve a tap: open what is safe, say why when it is not, never need the store for a portable tag"
```

---

### Task 9 (§A.2 row 5, the screens): the graph, the share receiver, the write screen, the list

**Files:**
- Modify: `app/src/main/kotlin/com/loosecannon/notetag/NoteTagApp.kt`, `MainActivity.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notetag/ui/NoteTagTheme.kt`, `ui/WriteScreen.kt`, `ui/TagListScreen.kt`, `ui/MainViewModel.kt`
- Create: `app/src/test/kotlin/com/loosecannon/notetag/ui/MainViewModelTest.kt`, `app/src/androidTest/kotlin/com/loosecannon/notetag/ui/AppSmokeTest.kt`

**Interfaces:**
- Produces: `NoteTagApp.graph: AppGraph` (`identity` from `BuildConfig`, `codec`, `store = JsonFileTagStore(File(filesDir, "tags.json"))`, `tagIo = RealTagIo(codec)`, `resolveTap`, `newWriteController(sharedText, scope)`); `MainActivity` handles `ACTION_SEND text/plain` (cold and `onNewIntent`) and the hand-off extra `EXTRA_MESSAGE` from the trampoline; **exactly two screens (P20)** driven by `MainViewModel.screen: StateFlow<Screen>` — `List(message: String? = null)` and `Write(sharedText)`. A hand-off sentence is a transient result card on the list, not a screen.

- [ ] **Step 1: The graph and the activity**

`NoteTagApp`:

```kotlin
class NoteTagApp : Application() {
    lateinit var graph: AppGraph; private set
    override fun onCreate() { super.onCreate(); graph = AppGraph(this) }
}

class AppGraph(app: Application) {
    val identity = TagIdentity(BuildConfig.NDEF_EXTERNAL_DOMAIN, BuildConfig.NDEF_TYPE_NAME, BuildConfig.NDEF_AAR_PACKAGE)
    val codec = NoteTagCodec(identity)
    val store: TagStore = JsonFileTagStore(File(app.filesDir, "tags.json"))
    val tagIo: TagIo = RealTagIo(codec)
    val resolveTap = ResolveTap(codec, store)
    fun newWriteController(sharedText: String?, scope: CoroutineScope) = NoteTagWriteController(tagIo, codec, store, sharedText, scope)
}
```

`MainActivity`: `singleTop`; on create and on new intent, derive the screen: `ACTION_SEND` with `EXTRA_TEXT` (plain or spanned — `getCharSequenceExtra`, then `toString()`) → `Screen.Write(text)`; an intent carrying `EXTRA_MESSAGE` (from the trampoline) → `Screen.List(message = text)`; otherwise `Screen.List()`. Wrap intent reading in `try/catch` (hostile extras, 54f9aea). `setContent { NoteTagTheme { when (screen) { … } } }`.

- [ ] **Step 2: The screens**

`WriteScreen(controller: NoteTagWriteController, onDone: () -> Unit)`: shows the shared link, then the controller state: `Waiting` → the message plus a neutral "If the link is too long for the tag, it will be saved on this phone instead."; `Confirm(reasons, action)` → every sentence in `reasons`, each on its own line (so the device-bound warning **"This tag needs this phone to open. Back up NoteTag to protect the link."** appears before any write, exactly as the binding rule says), with exactly two buttons **`action`** / **Cancel** (P11); `Writing` → "Writing…"; `Written(deviceBound = false)` → "Written." and Done; `Written(deviceBound = true)` → **"Written · This phone only"** with the line **"Saved as a this-phone-only tag."** and Done; `Refused`/`Error` → the sentence and Done. Reader mode: `LifecycleResumeEffect { session.start(); onPauseOrDispose { session.stop() } }` with `NfcReaderModeSession(activity) { controller.onTag(NfcTagHandle(it)) }`; `DisposableEffect` on leave → `controller.abandon()`. If `!session.available` show "This phone has no NFC." and if `!session.enabled` "Turn NFC on to write a tag."

`TagListScreen(entries, message, onDismissMessage)`: when `message` is non-null, an inline **result card** at the top carrying the sentence and a Dismiss action (this is where every ambient-tap and refusal sentence lands); below it the store's **confirmed** writes (`list()`), newest first: label, kind word, "this phone only" for `LOCAL_REF`, written-at as a date. Empty state: "Share a Joplin note or a link to NoteTag to write your first tag." There is no message screen.

`MainViewModel(graph)`: `screen`, `entries` (reloaded on `List`), `show(screen)`, `dismissMessage()`; unit-tested with a fake store.

- [ ] **Step 3: Tests**

`MainViewModelTest`: share text → `Write`; a message extra → `List(message)`, and `dismissMessage()` → `List(null)`; returning to `List` reloads entries from the store; **an unconfirmed entry in the store does not appear in `entries`**.

`WriteScreenDeviceBoundTest` (emulator, Compose rule, no NFC needed): a `NoteTagWriteController` over a five-line `FakeTagIo` (duplicated in `androidTest`, not shared with `test`) whose inspection reports an empty writable tag with `maxSize` one byte below the URI's serialised size; `controller.onTag(FakeHandle)` → the screen shows the device-bound sentence and the **Write** button and nothing has been written; press Write, `onTag` again → "Written · This phone only" and "Saved as a this-phone-only tag."; a second run with a `maxSize` that fits → no warning, plain "Written.".

`AppSmokeTest` (emulator, Compose test rule, fresh install in `@Before` via `clearInstall()` copied from ServiceTag's `AppSmokeTest.kt:45-77` pattern): launching shows the empty list sentence; an intent carrying `EXTRA_MESSAGE` shows the sentence on the list's result card and Dismiss clears it; an `ACTION_SEND` intent with `joplin://x-callback-url/openNote?id=<32 hex>` shows the write screen with the link and "Hold a tag to the phone." (the emulator has no NFC: the "no NFC" line is acceptable and asserted as *either* the hold sentence or the no-NFC sentence); a `Confirm` state is not reachable without a tag and is covered by the JVM tests.

- [ ] **Step 4: Gate and commit**

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --console=plain
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --console=plain
git add -A app
git commit -m "notetag screens: share it, hold a tag, see what this phone has written (two screens, p20)"
```

---

### Task 10 (§A.2 rows 5, 8, 9, and the C9 binding): ambient dispatch, the safe launcher, and the identity tests

**Files:**
- Replace: `app/src/main/kotlin/com/loosecannon/notetag/nfc/NfcDispatchActivity.kt` (the Task 2 stub)
- Create: `app/src/main/kotlin/com/loosecannon/notetag/links/LinkLauncher.kt` (copied), `app/src/test/kotlin/com/loosecannon/notetag/nfc/TagIdentityBindingTest.kt`, `app/src/androidTest/kotlin/com/loosecannon/notetag/nfc/TagIdentityDispatchTest.kt`, `app/src/androidTest/kotlin/com/loosecannon/notetag/nfc/NdefSizeDeviceTest.kt`, `app/src/androidTest/kotlin/com/loosecannon/notetag/nfc/AmbientDispatchDeviceProofTest.kt`

- [ ] **Step 1: `LinkLauncher` — copied, both catches (row 8)**

Copy `../ServiceTag-split/app/src/main/kotlin/com/loosecannon/servicetag/links/LinkLauncher.kt` (25 lines): `ActivityNotFoundException` **and** `SecurityException` → a toast "No app can open this link" and `false`; header comment naming the copy.

- [ ] **Step 2: The trampoline (invariant 12: never `nfcTag()`)**

```kotlin
package com.loosecannon.notetag.nfc

class NfcDispatchActivity : Activity() {
    private val scope = MainScope()
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); handle(intent) }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent) }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun handle(intent: Intent?) {
        val records = try { if (intent?.action == NfcAdapter.ACTION_NDEF_DISCOVERED) intent.ndefRecords() else null }
                      catch (t: Throwable) { null }              // hostile extras: nothing to resolve
        if (records == null) { finishWith("Nothing to resolve."); return }
        val graph = (application as NoteTagApp).graph
        scope.launch {
            when (val outcome = runCatching { graph.resolveTap.resolve(records) }.getOrElse { TapOutcome.Message("This tag could not be read.") }) {
                is TapOutcome.Open -> {
                    val opened = LinkLauncher.open(this@NfcDispatchActivity, outcome.uri)
                    if (opened) { outcome.entryUuid?.let { runCatching { graph.store.touch(it, System.currentTimeMillis()) } }; finish() }
                    else finishWith("No app can open this link: ${outcome.uri}")
                }
                is TapOutcome.Message -> finishWith(outcome.text)
            }
        }
    }

    private fun finishWith(message: String) {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(MainActivity.EXTRA_MESSAGE, message))
        finish()
    }
}
```

`EXTRA_MESSAGE = "com.loosecannon.notetag.MESSAGE"` on `MainActivity`'s companion. **`Intent.nfcTag()` is never called anywhere in this file** — assert it with `git grep -c 'nfcTag()' -- app/src/main/kotlin/com/loosecannon/notetag/nfc/NfcDispatchActivity.kt` = 0 in the report.

- [ ] **Step 3: The C9 binding tests (target §4.8, in NoteTag's terms)**

`TagIdentityBindingTest` (JVM): reads `app/src/main/AndroidManifest.xml` and `app/build.gradle.kts` as text (the same file-relative idiom as ServiceTag's `TagIdentityBindingTest.kt:34`): the manifest contains `android:path="${ndefTagPath}"` and **zero** occurrences of the literal `com.loosecannon.notetag:tag`; exactly one `NDEF_DISCOVERED`; **no `android:scheme="notetag"`** (P4); `BuildConfig.NDEF_AAR_PACKAGE == null` and `BuildConfig.NDEF_EXTERNAL_DOMAIN == BuildConfig.APPLICATION_ID`; the build script contains `tagAarPackage: String? = null`.

`TagIdentityDispatchTest` (emulator): `queryIntentActivities` for `NDEF_DISCOVERED` + `vnd.android.nfc://ext/com.loosecannon.notetag:tag` resolves exactly one activity, ours, `NfcDispatchActivity`; the retired `…notenfc:md5_short` and the sibling `…servicetag:tag` resolve to **nothing in this package**; `ACTION_VIEW notetag://anything` resolves to nothing in this package (P4).

`NdefSizeDeviceTest` (emulator): for the 49-byte `JOPLIN_NOTE`, a 255-byte-payload `URI`, a 300-byte-payload `URI` and a two-record message, `NdefSize.serialisedSize(records) == records.toNdefMessage().toByteArray().size` — **this is the pin that makes the pure-JVM planner's arithmetic the platform's** (invariant 7).

- [ ] **Step 4: The ambient device proof (through the real filter, like ServiceTag's `NfcIdentityDeviceProofTest`)**

`AmbientDispatchDeviceProofTest`: fresh install; each case builds an **implicit** `NDEF_DISCOVERED` intent with `EXTRA_NDEF_MESSAGES` and the data URI `vnd.android.nfc://ext/${BuildConfig.NDEF_EXTERNAL_DOMAIN}:${BuildConfig.NDEF_TYPE_NAME}` (never `setClassName`), `FLAG_ACTIVITY_NEW_TASK`, `startActivity`, then asserts the sentence on the list screen's result card with a bounded `awaitText`:
1. a `JOPLIN_NOTE` record → the emulator has no Joplin, so the outcome is the launcher's "No app can open this link: joplin://x-callback-url/openNote?id=<id>" message — **no crash, and the id in the message is lower-case**;
2. a ServiceTag record (`com.loosecannon.servicetag:tag`, a valid-looking 18-byte body) → "This tag belongs to ServiceTag, not NoteTag." (§23);
3. a `LOCAL_REF` whose uuid is not in the store → "This tag was written on another phone…";
4. a `URI` record with `javascript:alert(1)` → the "will not open" message, and **no** `ACTION_VIEW` was started (assert via an `Instrumentation.ActivityMonitor` on `ACTION_VIEW` seeing zero hits);
5. our type with a 1-byte body → "unreadable".

- [ ] **Step 5: Gate and commit**

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --console=plain
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --console=plain
git grep -c 'nfcTag()' -- app/src/main/kotlin/com/loosecannon/notetag/nfc/NfcDispatchActivity.kt | cat   # expect: no output
git add -A app
git commit -m "ambient dispatch: one filter, read only, open what is safe, say why otherwise"
```

---

### Task 11 (§A.2 row 4 and the whole-phase verify): the signed release, the README, the final commit, the verification, the evidence

**Files:**
- Modify: `README.md` (NoteTag repo) — committed in **Step 3**, before any proof
- Modify: `docs/architecture/product-split-evidence.md` (**in `../ServiceTag-split`, branch `product-split`**) — a separate commit there, **last**

**Order matters (owner correction, 2026-09-17):** the final NoteTag commit exists **before** the whole-phase run, the clean clone and the evidence, so that every number and SHA the evidence records is the tree the clone proved.

- [ ] **Step 1: The signed release (§12, row 4) — a comparison that prints one word**

```bash
./gradlew :app:assembleRelease --console=plain
ls app/build/outputs/apk/release/            # expect: app-release.apk (signed), NOT app-release-unsigned.apk
APKSIGNER=~/Android/Sdk/build-tools/36.0.0/apksigner
T="$(mktemp -d -p "$SCRATCH")"
# Neither digest is ever printed, echoed, pasted or passed as an argument: both go to files, get
# normalised (lower-case, no colons, no spaces) and are compared byte-for-byte.
"$APKSIGNER" verify --print-certs app/build/outputs/apk/release/app-release.apk 2>/dev/null \
  | grep -i 'SHA-256 digest' | head -1 | sed 's/.*: *//' | tr -d ': \n' | tr 'A-F' 'a-f' > "$T/built"
grep -m1 'SHA-256' ../ServiceTag-split/docs/design/phase-1a-evidence.md \
  | sed 's/.*SHA-256:[[:space:]]*//' | tr -d ': \n' | tr 'A-F' 'a-f' > "$T/recorded"
if ! test -s "$T/built" || ! test -s "$T/recorded"; then
    rm -rf "$T"
    echo BLOCKED          # an empty digest on either side is a failure, never a silent pass
    exit 1
fi
cmp -s "$T/built" "$T/recorded" && echo matches || {
    rm -rf "$T"
    echo differs
    exit 1
}
rm -rf "$T"
```

Only `matches`, `differs` or `BLOCKED` can escape this block. Write **only** that word in the report and the evidence. `differs` or `BLOCKED` stops the task: the wrong key or the wrong record was used. If `grep -m1 'SHA-256'` in the evidence file lands on a line that is not the certificate line, adjust the `grep` to the line that is — by line content, never by pasting the value.

- [ ] **Step 2: The README, for the product**

Replace the Task 1 README with a product README (keep the history section verbatim): what NoteTag is (share a Joplin note or a link, hold a tag, tap it later — it opens); the identity block (`com.loosecannon.notetag`, external type `com.loosecannon.notetag:tag`, no AAR, scheme `notetag` reserved and undeclared); the three kinds and the writer's automatic decision in two sentences; "tags written as a local reference only work on this phone"; what it does not do (no chooser, no ServiceTag tags, no export/import of the local map yet — roadmap #6/#36 under their NoteTag titles); Building (`./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug`; Phase G adds `--recurse-submodules`); Signing: `~/.config/notenfc/keystore.properties` with the four keys, the existing key, unsigned build when absent, the fingerprint recorded in the ServiceTag repository's `docs/design/phase-1a-evidence.md` and not reproduced here; History (the Task 1 paragraph); Related projects: ServiceTag, nfc-tag-core (the latter "not yet created").

- [ ] **Step 3: The final NoteTag commit — before any proof**

```bash
git add README.md
git commit -m "notetag readme: what it is, what it writes, and where its history came from"
git status --short | wc -l        # expect: 0 — the tree below is exactly what the clone will see
FINAL=$(git rev-parse --short HEAD)
```

- [ ] **Step 4: The whole-phase verification, at that HEAD**

```bash
git grep -niIE 'notenfc|noteNFC|NoteNfc|md5_short|TECH_DISCOVERED|nfc_tech_filter|looseCannon' -- . ':!README.md' | cat
#   expect: exactly the keystore path lines in app/build.gradle.kts and .gitignore (~/.config/notenfc), nothing else
git grep -n 'notenfc' -- README.md | cat          # expect: the keystore path and the history paragraph only
git grep -c 'android:scheme="notetag"' | cat      # expect: no output (P4)
grep -c NDEF_DISCOVERED app/src/main/AndroidManifest.xml     # expect: 1
git ls-files | wc -l; git rev-list --merges --count HEAD; git log --oneline | tail -1; git rev-list --count HEAD
./gradlew clean :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:compileDebugAndroidTestKotlin --console=plain
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --console=plain
"$AAPT2" dump badging app/build/outputs/apk/debug/app-debug.apk | grep -E "^package:|application-label:|launchable-activity:"
```

- [ ] **Step 5: The clean clone, from that same HEAD**

```bash
CLEAN="$(mktemp -d -p "$SCRATCH")"
git clone --no-local . "$CLEAN/NoteTag"
test "$(git -C "$CLEAN/NoteTag" rev-parse --short HEAD)" = "$FINAL" && echo "clone is at $FINAL"
(cd "$CLEAN/NoteTag" && ANDROID_HOME=~/Android/Sdk ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain)
rm -rf "$CLEAN"
```

- [ ] **Step 6: Collect the numbers**

From Steps 3–5: `$FINAL`; the commit count and range `c84b881..$FINAL`; merges 0; root `5fb6aed`; the tree count; unit and connected counts per class; the badging lines; "signed: matches"; the clone result. Nothing is added to the NoteTag repository after Step 3 — if anything needs to change, fix it, commit, and **repeat Steps 4–6 at the new HEAD**.

- [ ] **Step 7: The evidence section (in the ServiceTag worktree)**

Append a `## Phase E — NoteTag reconstruction` section to `../ServiceTag-split/docs/architecture/product-split-evidence.md` following the Phase D section's shape: the repository and its commit range (`c84b881..$FINAL`, `<n>` commits, merges 0, root `5fb6aed`) — the same `$FINAL` the clone proved; the tree count after Task 1; the identity off the built APK; one filter, no AAR, no `notetag` scheme; the format's measured message sizes (49 B `JOPLIN_NOTE`, the `NdefSize` pin green on the emulator); the store, the retained-is-not-written rule and the three-case failure injection (test names); sibling isolation both ways now (cite ServiceTag's `NdefEnvelopeIsolationTest` and NoteTag's `NoteTagCodecTest.aServiceTagRecordIsForeignEvenWithAPlausibleBody`); the suites (counts, `emulator-5554`); the clean clone at `$FINAL`; "release signed with the existing key — fingerprint matches the record in `docs/design/phase-1a-evidence.md`, compared without printing, not reproduced"; the caveat (debug and emulator evidence; no physical tag; the interim adapter's `format(message)` shape is superseded by the library in Phase F/G); the not-attempted list (row 10, CI on a runner, the physical session); and the closing line **"Phase E local reconstruction complete; Gate 6 pending its deferred prerequisites (row 10 / Phase G, §B.4 CI, the physical session)."**

- [ ] **Step 8: Commit the evidence, on `product-split`**

```bash
cd ../ServiceTag-split
git add docs/architecture/product-split-evidence.md
git commit -m "evidence: phase e, notetag reconstructed on the emulator"
```

---

## Spec coverage (self-review)

| §A.2 row / requirement | Task |
|---|---|
| 1 the deletion, the C6 statement, the 45-file tree, real ancestry | 1 (verify repeated in 11) |
| 2 identity, root name, label, own icon, mixed-case root gone | 2, 3 |
| 3 CI from scratch on the new remote | **not this phase** (§B.4); the same Gradle line gates every task locally |
| 4 signing with the existing key, signed release proof, versions past 2 / 1.1 | 2 (mechanism), 11 (proof) |
| 5 share receiver, writer screen, ambient trampoline, minimal store; 2024 activities deleted; one filter; no tech filter | 2 (skeleton + deletions), 9, 10, 6 |
| 6 the format, no AAR, the decision, JOPLIN_NOTE validation, mixed-case round-trip, URI fallback, capacity at maxSize/−1/+1, no TLV, no character count | 4, 5, (10 pins `NdefSize`) |
| 7 the store, never required for portable kinds, device-bound warning **before the write and on success** (binding UX rule), crash-consistency (a)/(b), three failure-injection cases | 6, 7 (controller + tests 8b/8c), 8 (store-deleted resolution), 9 (the sentences, `WriteScreenDeviceBoundTest`) |
| 8 copied safe launch policy, both exceptions caught, tests per rejected scheme, missing handler is a message | 5 (policy), 10 (launcher, device case 1) |
| 9 a ServiceTag tag is not a note; writer offers only Write over it / Cancel and names the other app | 4 (codec), 7 (wording + controller), 8, 10 (device case 2) |
| 10 adopt nfc-tag-core | **Phase G** |
| target §4.8 C9 binding | 2 (mechanism), 10 (tests) |
| §23 acceptance (local half) | 10 (device proof), 11 (clean clone; CI line) — the physical half is the later session |
| P4 no `notetag://` filter | 2, 10 |
| P19/P20 JSON store, Compose **exactly two** screens (List with its result card, Write) | 6, 9 |
| Gate 6 | **not claimed**: the phase ends "local reconstruction complete; Gate 6 pending its deferred prerequisites" (11, Step 7) |

**Placeholder scan:** none. **Type consistency:** `NoteTagContent.Writable` (Tasks 4, 5, 7), `WritePlan` (5, 7), `TagStore`/`TagEntry` with nullable `writtenAt` and `confirm` (6, 7, 8, 9), `TapOutcome` (8, 10), `TagIo`/`TagInspection`/`WriteResult` (7 copies, 7 controller), `Screen.List(message)`/`EXTRA_MESSAGE` (9, 10) — one definition each.
