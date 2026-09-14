# Phase 0 — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the noteNFC repository clone-buildable and CI-tested on the modern toolchain, characterise the legacy NFC behaviour with tests behind a pure-Kotlin `:core` module, restructure the GitHub issues per the approved design, run the S1 toolchain spike, and record the Phase 0 exit-criteria evidence — with **no user-visible behaviour change**.

**Architecture:** Two Gradle modules: `:core` (pure Kotlin/JVM, no Android imports: `LegacyKey`, `NdefCodec`, `LegacyLinkPolicy`) and `:app` (the existing three activities, rewired to call `:core`). Toolchain: AGP 9.4.0 with built-in Kotlin, Gradle 9.7.1 wrapper, version catalog, JDK 17 bytecode targets. Issue restructuring is authored as reviewable files under `docs/design/issues/` before it is applied to GitHub.

**Tech Stack:** Kotlin 2.4.x (AGP built-in for `:app`; `org.jetbrains.kotlin.jvm` 2.4.20 for `:core`), AGP 9.4.0, Gradle 9.7.1, JUnit 5 (`junit-bom` 5.11.4) + `kotlin-test`, GitHub Actions, `gh` CLI.

**Spec:** `docs/design/07-implementation-sequence.md` (Phase 0 section) argued from `docs/design/03-target-architecture.md` §15, `docs/design/01-current-state-archaeology.md`, `docs/design/06-legacy-compatibility.md` §5/§10, `docs/design/02-requirements-reconciliation.md` §5, `docs/design/08-risk-register.md` §2 (S1) and §3 (R-1).

## Global Constraints

- `applicationId` and `namespace` stay exactly `com.looseCannon.noteNFC` (D1 §9).
- The manifest `NDEF_DISCOVERED` filter `vnd.android.nfc://ext/com.loosecannon.notenfc:md5_short` stays (D1 §9). No behaviour change for a valid legacy tag or a Joplin share.
- The legacy key function is preserved verbatim: `MD5(UTF-8 bytes of text)` → lowercase hex → first 8 characters (D1 §2, D6 §5).
- Tags written in Phase 0 still use the legacy external record `com.loosecannon.notenfc:md5_short` with the 8-char ASCII key as payload (payload format v1 arrives in Phase 1B).
- `minSdk = 26`, `targetSdk = 36`, `compileSdk = 37` (D3 §15; the local SDK has platform 37 installed, not 36). `versionCode = 2`, `versionName = "1.1"`.
- Java/Kotlin bytecode target 17 (`JavaVersion.VERSION_17`, `JvmTarget.JVM_17`). The Gradle daemon JVM stays JetBrains JDK 25 via `gradle/gradle-daemon-jvm.properties`.
- `:core` must not import `android.*` or `androidx.*`; its tests are plain JVM tests.
- No Robolectric in Phase 0 (ruling: JDK 25 daemon vs Robolectric's supported JDK range is an unverified risk; the pure extraction in Task 5 gives equivalent characterisation).
- Commit messages: casual, terse, human (per repo `CLAUDE.md`). **Never** add `Co-Authored-By`, `Generated-by`, or any AI attribution line. Author is the configured git user.
- Do not push. Do not touch the `master` branch or the sibling `noteNFC` working tree.
- Do not edit GitHub before Task 2; Task 2 is the only task that mutates GitHub, and only as its files specify.
- The three tracked binaries: remove `app/build/outputs/apk/debug/app-debug.apk` from git (build output). Keep `app/release/app-release.apk` tracked (it is the archaeological artifact referenced by D1 §6).
- New Kotlin package root for new code: `com.loosecannon.notenfc.core` (in `:core`). The existing `com.looseCannon.noteNFC` package of the three activities is **not** renamed in Phase 0.

---

## File map

| Path | Responsibility |
|---|---|
| `.gitignore`, `app/.gitignore` | ignore build output only; stop ignoring wrapper, settings, `gradle/`, `gradle.properties`, test dirs |
| `settings.gradle.kts` | plugin/dependency repos, `include(":app", ":core")` |
| `gradle.properties` | JVM args, AndroidX flags |
| `gradle/libs.versions.toml` | single source of versions |
| `build.gradle.kts` | root: plugin declarations `apply false` |
| `app/build.gradle.kts` | Android app config, depends on `:core` |
| `app/src/main/AndroidManifest.xml` | remove `package` attr; explicit `android:exported` on every activity |
| `core/build.gradle.kts` | `kotlin("jvm")`, JUnit 5 |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/nfc/LegacyKey.kt` | the MD5[0:8] function |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/nfc/NdefCodec.kt` | Android-free NDEF record model, legacy decode/encode |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/links/LegacyLinkPolicy.kt` | the `contains("joplin")` gate, extracted |
| `core/src/test/kotlin/...` | tests for the three files above |
| `app/src/main/java/com/looseCannon/noteNFC/*.kt` | rewired to `:core`, behaviour identical |
| `.github/workflows/ci.yml` | build + unit tests on push/PR |
| `docs/design/issues/*.md` | authored issue bodies + mapping (Task 1), applied numbers (Task 2) |
| `docs/design/spikes/S1-toolchain.md` | S1 spike report (Task 7) |
| `docs/design/phase-0-evidence.md` | exit-criteria evidence incl. R-1 (Task 8) |

---

### Task 1: Author the GitHub issue restructuring as files

**Files:**
- Create: `docs/design/issues/README.md` (mapping table)
- Create: `docs/design/issues/rewrite-01.md` … one file per existing issue that is rewritten (`rewrite-NN.md`)
- Create: `docs/design/issues/new-<slug>.md` one file per new issue
- Read only: `docs/design/issues/original/issue_1.md` … `issue_16.md` (verbatim originals), `docs/design/02-requirements-reconciliation.md` (§1 matrix, §2 graph, §5 actions), `docs/design/07-implementation-sequence.md`, `docs/design/README.md` ("GitHub issue actions"), `docs/design/04-domain-data-model.md`, `docs/design/03-target-architecture.md`

**Interfaces:**
- Produces: the exact set of files Task 2 applies. Each file has a YAML front-matter block Task 2 parses:

```yaml
---
action: rewrite | create
number: 4            # rewrite only: the existing issue number
title: "[MVP] ..."   # the final title (existing issues may be retitled)
milestone: "Phase 3 — Scheduling + local reminders"
labels: []           # leave empty; no labels are introduced in Phase 0
---
```

followed by the full Markdown body.

- [ ] **Step 1: Read the sources.** Read every file in `docs/design/issues/original/` and D2 §1, §2, §5. The action plan is D2 §5 exactly; do not invent scope beyond what D2/D7/README name.

- [ ] **Step 2: Write `docs/design/issues/README.md`** with (a) the milestone list below verbatim, (b) a mapping table with columns `Original issue | Action | Result file | Phase / milestone | Notes`, (c) a dependency list "X depends on Y" derived from D2 §2, and (d) a "Traceability" paragraph stating that every rewritten/new body links back to its origin issue number and to the design document sections it implements, and that original texts are preserved verbatim under `original/` and in GitHub's edit history.

Milestones (create exactly these titles in Task 2):

```
Phase 0 — Foundation
Phase 1 — Tag survival (M1)
Phase 2 — Journal + profiles
Phase 3 — Scheduling + local reminders
Phase 4 — Attachments
Phase 5 — Todoist
Phase 6 — Supplies
Phase 7 — Extension points
```

- [ ] **Step 3: Write the rewrite files.** One per existing issue #1–#16. Every body starts with:

```markdown
> Revised 2026-09-14 per the approved design package (`docs/design/`, review 1). Original text is preserved in this issue's edit history and in `docs/design/issues/original/issue_N.md`. Design references: <D-doc §sections>.
```

Required content per issue (from D2 §5 and README "GitHub issue actions"):
  - #1: epic. Keep the product principles and workflows; replace the scope list with the reconciled MVP set (#2, #3, #4, #5, #6, #8, #13, #14 + the new foundational issues by slug, to be replaced by numbers in Task 2) and state that Todoist (#9/#10/#12 projection) and supplies (#15) are NEXT. Title `[EPIC] Evolve noteNFC into an NFC-first maintenance tracker`. Milestone: none.
  - #2: narrowed to "Room + Asset entity (Phase 1A)". Remove the "write the asset id to the tag" wording; state tag identity is a separate `nfc_tag` row (D4 §3). Link to new issues `tag-payload-v1-legacy-resolver` and `tag-bind-rebind-ux`. Milestone Phase 1.
  - #3: keep; add the "queryable time series" storage requirement (moved from #13); state quick actions are instances of #13 profiles; define the `source` enum (`MANUAL, SCHEDULE_QUICK_COMPLETE, TODOIST_SYNC, IMPORT, TELEMETRY`). Milestone Phase 2.
  - #4: narrowed to "scheduling engine + state model"; becomes the single owner of the four operations (complete early / snooze / postpone occurrence / edit recurrence) and of `completion_mode` (QUICK | FORM) merged from #10/#11; FIXED = skip-forward, COMPLETION = from completion date (D5 §2, §7). Link to new issues `local-reminder-provider` and `reminder-provider-interface`. Milestone Phase 3.
  - #5: keep; note the meter model dependency. Milestone Phase 3 (a minimal list ships in Phase 1C — say so).
  - #6: keep; define "validate" as scheme allowlist + handler-present check via `<queries>` + confirmation for unknown schemes; standalone links are first-class tag targets. Milestone Phase 1.
  - #7: convert the ideas list into scope: (a) attachment metadata + LOCAL + SAF-tree providers, (b) referenced (non-copied) documents, (c) structured spec fields, (d) backup inclusion; the shared part entity belongs to #15. Title prefix stays `[NEXT]`. Milestone Phase 4.
  - #8: retier to MVP-1; add format versioning, Replace vs Merge, automatic snapshots, attachment bundle, exclusion of secrets. Title prefix `[MVP]`. Milestone Phase 1.
  - #9: title prefix `[NEXT]`; narrowed to the Todoist projection with capability-based representation (`NATIVE_RECURRING` for completion-relative, time-only, year-round schedules; `MANAGED_OCCURRENCE` otherwise; canonical due verification after every completion; noteNFC canonical). Remove the OAuth section (moved to `todoist-authentication`), the deep-link section (moved to `deeplink-contract`), the provider-radio UX (moved to `provider-selection-ux`), the `ReminderProvider` interface (moved to `reminder-provider-interface`), and the duplicated four-operation semantics (owned by #4). Milestone Phase 5.
  - #10: keep for the sync engine; add activity-log completion detection for native tasks and reconciliation of Todoist's post-completion date; remove simple/rich completion text (owned by #4). Milestone Phase 5.
  - #11: narrowed to local notification quick actions (Done / Snooze / Open) for Phase 3; link to new `reminder-fatigue-controls` and `todoist-link-actions`; remove duplicated semantics (link #4). Title prefix `[MVP]`. Milestone Phase 3.
  - #12: narrowed to the Todoist projection policy for usage-based schedules (NEXT, Phase 5); the meter model moves to new `meter-model`. Milestone Phase 5.
  - #13: keep; remove the time-series requirement (moved to #3); state stock decrement is behind #15. Milestone Phase 2.
  - #14: narrowed to seasonal windows (window on the asset; `FOLLOW_ASSET | IGNORE`; re-entry `AT_START(+offset)` and `RESUME_CLAMPED`); reminder health moves to new `reminder-health`. Milestone Phase 3.
  - #15: keep; owns the shared part/supply entity; specify the stock ledger (COUNT/DELTA). Milestone Phase 6.
  - #16: keep as FUTURE; record the two invariants the core model keeps (measurement provenance `source`; meter readings are ordinary measurements). Milestone Phase 7.

- [ ] **Step 4: Write the new-issue files** with these slugs and titles (body: goal, scope, acceptance criteria, design references, "Split from #N" or "Identified in D2 §4 item N" line):

| Slug | Title | Milestone |
|---|---|---|
| `phase0-repo-hygiene` | `[MVP] Phase 0: clone-buildable repo, AGP 9 toolchain, :core module, CI` | Phase 0 |
| `signing-key-investigation` | `[MVP] Investigate the installed APK's signing certificate and upgrade path` | Phase 0 |
| `testing-and-ci-strategy` | `[MVP] Testing pyramid and CI gates` | Phase 0 |
| `tag-payload-v1-legacy-resolver` | `[MVP] noteNFC tag payload format v1 and legacy md5_short resolver` | Phase 1 |
| `tag-bind-rebind-ux` | `[MVP] Bind, rebind, revoke, and unknown-tag flows` | Phase 1 |
| `deeplink-contract` | `[MVP] notenfc:// deep-link contract (navigation-only, validated)` | Phase 1 |
| `untrusted-input-policy` | `[MVP] Untrusted input policy: tag payloads, deep links, stored URIs` | Phase 1 |
| `date-semantics` | `[MVP] Calendar-date semantics for due and occurred dates` | Phase 2 |
| `meter-model` | `[MVP] Meter model: meter definitions, readings, baselines, reset` | Phase 2 |
| `asset-templates` | `[MVP] Asset templates that seed definitions, profiles, and default schedules` | Phase 2 |
| `reminder-provider-interface` | `[MVP] ReminderProvider port and reconcile contract` | Phase 3 |
| `local-reminder-provider` | `[MVP] Local reminder provider: daily digest alarm, WorkManager backstop, boot receivers, channels` | Phase 3 |
| `platform-permissions-scheduling` | `[MVP] Platform permissions and scheduling constraints (POST_NOTIFICATIONS, no exact alarms, BOOT/TIME/TIMEZONE)` | Phase 3 |
| `reminder-health` | `[MVP] Reminder health and integrity checks with idempotent repair` | Phase 3 |
| `provider-selection-ux` | `[NEXT] Per-schedule reminder provider selection (single-choice UI over a multi-provider model)` | Phase 5 |
| `todoist-authentication` | `[NEXT] Todoist authentication: personal API token first, OAuth later` | Phase 5 |
| `todoist-link-actions` | `[NEXT] Todoist deep-link actions into noteNFC` | Phase 5 |
| `reminder-fatigue-controls` | `[NEXT] Reminder-fatigue controls per schedule` | Phase 7 |

- [ ] **Step 5: Self-check.** Every rewrite file references its design sections; every new file has a "Split from #N" or "Identified in D2 §4" line; no file introduces scope absent from D2/D7/README. Count: 16 rewrite files + 18 new files + README.

- [ ] **Step 6: Commit**

```bash
git add docs/design/issues
git commit -m "draft the issue restructuring as files before touching github"
```

Also commit the design package itself if it is not yet committed on this branch (it was copied into the worktree untracked):

```bash
git add docs/design docs/superpowers
git commit -m "design package for the maintenance-tracker evolution (review 1 applied)"
```

Make the design-package commit **first** (before the issues commit) so history reads naturally.

---

### Task 2: Apply the issue restructuring to GitHub

**Files:**
- Read: every file under `docs/design/issues/` (from Task 1)
- Create: `docs/design/issues/applied.md` (what was done, with numbers and URLs)
- Modify: `docs/design/issues/README.md` (fill the new issue numbers into the mapping table)

**Interfaces:**
- Consumes: the front-matter contract from Task 1.
- Produces: GitHub state; `applied.md`.

- [ ] **Step 1: Verify access.** Run `gh auth status` and `gh repo view GonzRon/noteNFC --json nameWithOwner`. Run `gh issue list --repo GonzRon/noteNFC --state all --limit 100 --json number,title` and confirm exactly issues 1–16 exist and all are open. If anything else exists, stop and report BLOCKED.

- [ ] **Step 2: Create the eight milestones** exactly as titled in `docs/design/issues/README.md`:

```bash
for t in "Phase 0 — Foundation" "Phase 1 — Tag survival (M1)" "Phase 2 — Journal + profiles" "Phase 3 — Scheduling + local reminders" "Phase 4 — Attachments" "Phase 5 — Todoist" "Phase 6 — Supplies" "Phase 7 — Extension points"; do
  gh api repos/GonzRon/noteNFC/milestones -f title="$t" -f state=open >/dev/null && echo "created: $t"
done
```

- [ ] **Step 3: Create the new issues first** (so their numbers can be substituted into the rewrite bodies). For each `new-*.md`: strip the front-matter, create with `gh issue create --repo GonzRon/noteNFC --title "<title>" --body-file <tmp> --milestone "<milestone>"`, record `slug → number, url`.

- [ ] **Step 4: Substitute slugs with numbers.** In every rewrite body and every new body, replace occurrences of a slug written as `` `slug` `` or `#slug` with `#<number>`. Then `gh issue edit <number> --body-file <tmp>` for each new issue whose body referenced a slug.

- [ ] **Step 5: Rewrite the existing issues.** For each `rewrite-NN.md`: `gh issue edit NN --repo GonzRon/noteNFC --title "<title>" --body-file <tmp> --milestone "<milestone>"` (omit `--milestone` when the file says none).

- [ ] **Step 6: Verify.** `gh issue list --repo GonzRon/noteNFC --state all --limit 100 --json number,title,milestone` must show 34 issues, every milestone populated as the README mapping says, and no issue closed. Spot-read three issues back with `gh issue view`.

- [ ] **Step 7: Write `docs/design/issues/applied.md`**: table `number | action | title | milestone | url`, a "Differences from the approved plan" section (must say "none" or list them precisely), and the date. Update the numbers in `README.md`'s mapping table.

- [ ] **Step 8: Commit**

```bash
git add docs/design/issues
git commit -m "apply the issue restructuring on github, record numbers"
```

---

### Task 3: Repository hygiene and toolchain

**Files:**
- Modify: `.gitignore`, `app/.gitignore`
- Delete from git: `app/build/outputs/apk/debug/app-debug.apk` (`git rm --cached`, then the file is ignored)
- Create: `gradle.properties`, `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Add to git: `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `gradle/gradle-daemon-jvm.properties`, `settings.gradle.kts` (they exist on disk but were gitignored)

**Interfaces:**
- Produces: a building `:app`; the version catalog aliases used by Tasks 4–6: `libs.versions.agp`, `libs.versions.kotlin`, `libs.plugins.android.application`, `libs.plugins.kotlin.jvm`, `libs.androidx.core.ktx`, `libs.junit.bom`, `libs.junit.jupiter`, `libs.junit.platform.launcher`, `libs.kotlin.test`.

- [ ] **Step 1: Replace `.gitignore`** with exactly:

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

and `app/.gitignore` with exactly:

```gitignore
/build
```

- [ ] **Step 2: Remove the tracked build output**

```bash
git rm --cached app/build/outputs/apk/debug/app-debug.apk
```

Keep `app/release/app-release.apk` tracked.

- [ ] **Step 3: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 4: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "9.4.0"
kotlin = "2.4.20"
coreKtx = "1.13.1"
junit = "5.11.4"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit-bom = { group = "org.junit", name = "junit-bom", version.ref = "junit" }
junit-jupiter = { group = "org.junit.jupiter", name = "junit-jupiter" }
junit-platform-launcher = { group = "org.junit.platform", name = "junit-platform-launcher" }
kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test", version.ref = "kotlin" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

- [ ] **Step 5: Replace `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "noteNFC"
include(":app")
```

(`:core` is added in Task 4.)

- [ ] **Step 6: Replace root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
```

- [ ] **Step 7: Replace `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.looseCannon.noteNFC"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.looseCannon.noteNFC"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
```

The unused appcompat, material, and constraintlayout dependencies are dropped (D1 §1: no AppCompat activity, no ConstraintLayout, unreferenced theme). The `androidTest` dependencies and `testInstrumentationRunner` are dropped in Phase 0 (no instrumented tests exist; Phase 1C reintroduces them).

If AGP 9.4's built-in Kotlin rejects the `kotlin { compilerOptions { … } }` block inside `android { … }`, use the top-level `kotlin { compilerOptions { jvmTarget.set(...) } }` form instead and note it in the report; do not apply `org.jetbrains.kotlin.android`.

- [ ] **Step 8: Edit `app/src/main/AndroidManifest.xml`**: remove `package="com.looseCannon.noteNFC"` from the `<manifest>` element; add `android:exported="false"` to `NFCHandlerActivity`; delete the commented-out `GetUIDActivity` block. Leave every intent filter as is, including `TECH_DISCOVERED` (its removal is Phase 1B).

- [ ] **Step 9: Build**

Run: `./gradlew :app:assembleDebug --console=plain -q`
Expected: BUILD SUCCESSFUL (first run downloads AGP 9.4 and dependencies; allow up to 15 minutes). Then run `git status --short` and confirm nothing under `app/build/` is listed and `.gradle/`, `.kotlin/` are ignored.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "make the repo buildable from a clone: track wrapper/settings, version catalog, agp 9.4, jdk 17 targets, drop tracked debug apk"
```

Confirm with `git show --stat HEAD` that `gradlew`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/gradle-daemon-jvm.properties`, `settings.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml` are in the commit and `app-debug.apk` is deleted.

---

### Task 4: The `:core` module with the legacy codec

**Files:**
- Modify: `settings.gradle.kts` (add `include(":core")`)
- Create: `core/build.gradle.kts`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/nfc/LegacyKey.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/nfc/NdefCodec.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/links/LegacyLinkPolicy.kt`
- Test: `core/src/test/kotlin/com/loosecannon/notenfc/core/nfc/LegacyKeyTest.kt`
- Test: `core/src/test/kotlin/com/loosecannon/notenfc/core/nfc/NdefCodecTest.kt`
- Test: `core/src/test/kotlin/com/loosecannon/notenfc/core/links/LegacyLinkPolicyTest.kt`

**Interfaces:**
- Produces (used verbatim by Task 5):

```kotlin
package com.loosecannon.notenfc.core.nfc
object LegacyKey { fun compute(text: String): String }               // MD5(UTF-8) hex, first 8 chars
data class NdefRecordData(val tnf: Int, val type: ByteArray, val payload: ByteArray)
sealed interface TagPayload {
    data class LegacyMd5(val key: String) : TagPayload
    data class Foreign(val description: String) : TagPayload
    data class Malformed(val reason: String) : TagPayload
    data object Empty : TagPayload
}
object NdefCodec {
    const val TNF_EXTERNAL_TYPE: Int = 0x04
    const val LEGACY_DOMAIN: String = "com.loosecannon.notenfc"
    const val LEGACY_TYPE_NAME: String = "md5_short"
    const val LEGACY_TYPE: String = "com.loosecannon.notenfc:md5_short"
    fun decode(records: List<NdefRecordData>): TagPayload
    fun encodeLegacy(key: String): NdefRecordData   // tnf=0x04, type=LEGACY_TYPE bytes, payload=key ASCII
}
package com.loosecannon.notenfc.core.links
object LegacyLinkPolicy { fun accept(sharedText: String?): String? }  // returns text iff it contains "joplin"
```

- [ ] **Step 1: Add the module.** In `settings.gradle.kts` change `include(":app")` to `include(":app", ":core")`. Create `core/build.gradle.kts`:

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

If toolchain auto-provisioning of JDK 17 is needed, Gradle's foojay resolver must be declared in `settings.gradle.kts`: add `plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }` at the top of `settings.gradle.kts`. If the AGP built-in Kotlin refuses to coexist with `org.jetbrains.kotlin.jvm` 2.4.20, try the KGP version AGP 9.4.0 bundles (read it from the build error or `./gradlew :app:dependencies --configuration kotlinCompilerClasspath`) and record the choice in the report.

- [ ] **Step 2: Write the failing tests.** `LegacyKeyTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.nfc

import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyKeyTest {
    // Vectors computed with `printf '%s' "<text>" | md5sum | cut -c1-8`; they pin the legacy behaviour verbatim.
    @Test fun emptyString() = assertEquals("d41d8cd9", LegacyKey.compute(""))
    @Test fun abc() = assertEquals("90015098", LegacyKey.compute("abc"))
    @Test fun joplinExternalLink() =
        assertEquals("63b37acf", LegacyKey.compute("joplin://x-callback-url/openNote?id=0123456789abcdef0123456789abcdef"))
    @Test fun rawSharedTextWithTitleIsHashedAsAWhole() =
        assertEquals("8fbfdd64", LegacyKey.compute("Note title joplin://x-callback-url/openNote?id=0123456789abcdef0123456789abcdef"))
    @Test fun utf8BytesNotPlatformDefault() = assertEquals("31f448a3", LegacyKey.compute("Ünïcödé joplin"))
    @Test fun alwaysEightLowercaseHexChars() {
        val k = LegacyKey.compute("anything at all")
        assertEquals(8, k.length)
        assert(k.all { it in '0'..'9' || it in 'a'..'f' }) { "not lowercase hex: $k" }
    }
}
```

`NdefCodecTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.nfc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertContentEquals

class NdefCodecTest {
    private fun legacy(payload: String, type: String = NdefCodec.LEGACY_TYPE) =
        NdefRecordData(NdefCodec.TNF_EXTERNAL_TYPE, type.toByteArray(Charsets.US_ASCII), payload.toByteArray(Charsets.UTF_8))

    @Test fun decodesValidLegacyKey() {
        val p = NdefCodec.decode(listOf(legacy("63b37acf")))
        assertEquals(TagPayload.LegacyMd5("63b37acf"), p)
    }
    @Test fun onlyFirstRecordMatters() {
        val p = NdefCodec.decode(listOf(legacy("63b37acf"), legacy("deadbeef")))
        assertEquals(TagPayload.LegacyMd5("63b37acf"), p)
    }
    @Test fun emptyMessageIsEmpty() = assertEquals(TagPayload.Empty, NdefCodec.decode(emptyList()))
    @Test fun uppercaseKeyIsMalformed() = assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(legacy("63B37ACF"))))
    @Test fun wrongLengthIsMalformed() = assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(legacy("63b37ac"))))
    @Test fun nonHexIsMalformed() = assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(legacy("63b37acz"))))
    @Test fun evernoteEraTypeIsForeign() {
        val p = NdefCodec.decode(listOf(legacy("63b37acf", type = "com.loosecannon.evernotenfc:md5_short")))
        assertIs<TagPayload.Foreign>(p)
    }
    @Test fun uriRecordIsForeign() {
        val uri = NdefRecordData(tnf = 0x01, type = byteArrayOf('U'.code.toByte()), payload = byteArrayOf(0x01) + "example.com".toByteArray())
        assertIs<TagPayload.Foreign>(NdefCodec.decode(listOf(uri)))
    }
    @Test fun encodeLegacyRoundTrips() {
        val rec = NdefCodec.encodeLegacy("63b37acf")
        assertEquals(NdefCodec.TNF_EXTERNAL_TYPE, rec.tnf)
        assertContentEquals(NdefCodec.LEGACY_TYPE.toByteArray(Charsets.US_ASCII), rec.type)
        assertContentEquals("63b37acf".toByteArray(Charsets.US_ASCII), rec.payload)
        assertEquals(TagPayload.LegacyMd5("63b37acf"), NdefCodec.decode(listOf(rec)))
    }
}
```

`LegacyLinkPolicyTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LegacyLinkPolicyTest {
    @Test fun joplinLinkPassesThroughUnchanged() {
        val t = "joplin://x-callback-url/openNote?id=0123456789abcdef0123456789abcdef"
        assertEquals(t, LegacyLinkPolicy.accept(t))
    }
    @Test fun anyTextContainingJoplinPassesVerbatim() {
        val t = "My Note https://example.com not joplin"
        assertEquals(t, LegacyLinkPolicy.accept(t))     // characterises the old substring gate, warts and all
    }
    @Test fun textWithoutJoplinIsRejected() = assertNull(LegacyLinkPolicy.accept("https://example.com"))
    @Test fun nullIsRejected() = assertNull(LegacyLinkPolicy.accept(null))
    @Test fun caseSensitiveLikeTheOriginal() = assertNull(LegacyLinkPolicy.accept("JOPLIN link"))
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :core:test --console=plain -q`
Expected: compilation failure (`LegacyKey`, `NdefCodec`, `LegacyLinkPolicy` unresolved).

- [ ] **Step 4: Implement.** `LegacyKey.kt`:

```kotlin
package com.loosecannon.notenfc.core.nfc

import java.security.MessageDigest

/**
 * The identifier the 2023–2024 app wrote to tags: MD5 of the shared text's UTF-8 bytes,
 * lowercase hex, first 8 characters. Preserved verbatim from `mainActivity.kt` so that
 * re-sharing the same note reproduces the same key (D6 §5). Never change this.
 */
object LegacyKey {
    fun compute(text: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(text.toByteArray(Charsets.UTF_8))
        return digest.fold("") { str, it -> str + "%02x".format(it) }.substring(0, 8)
    }
}
```

`NdefCodec.kt`:

```kotlin
package com.loosecannon.notenfc.core.nfc

/** Android-free view of one NDEF record (mirrors android.nfc.NdefRecord's tnf/type/payload). */
data class NdefRecordData(val tnf: Int, val type: ByteArray, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is NdefRecordData && tnf == other.tnf && type.contentEquals(other.type) && payload.contentEquals(other.payload)
    override fun hashCode(): Int = 31 * (31 * tnf + type.contentHashCode()) + payload.contentHashCode()
}

sealed interface TagPayload {
    data class LegacyMd5(val key: String) : TagPayload
    data class Foreign(val description: String) : TagPayload
    data class Malformed(val reason: String) : TagPayload
    data object Empty : TagPayload
}

object NdefCodec {
    const val TNF_EXTERNAL_TYPE: Int = 0x04
    const val LEGACY_DOMAIN: String = "com.loosecannon.notenfc"
    const val LEGACY_TYPE_NAME: String = "md5_short"
    const val LEGACY_TYPE: String = "$LEGACY_DOMAIN:$LEGACY_TYPE_NAME"

    private val legacyKeyPattern = Regex("^[0-9a-f]{8}$")

    /** Android dispatches on the first record of the first message; so do we. */
    fun decode(records: List<NdefRecordData>): TagPayload {
        val first = records.firstOrNull() ?: return TagPayload.Empty
        val type = String(first.type, Charsets.US_ASCII)
        if (first.tnf != TNF_EXTERNAL_TYPE || type != LEGACY_TYPE) {
            return TagPayload.Foreign("tnf=${first.tnf} type=$type")
        }
        val key = String(first.payload, Charsets.UTF_8)
        if (!legacyKeyPattern.matches(key)) {
            return TagPayload.Malformed("legacy payload is not 8 lowercase hex chars: '$key'")
        }
        return TagPayload.LegacyMd5(key)
    }

    fun encodeLegacy(key: String): NdefRecordData {
        require(legacyKeyPattern.matches(key)) { "not a legacy key: '$key'" }
        return NdefRecordData(
            tnf = TNF_EXTERNAL_TYPE,
            type = LEGACY_TYPE.toByteArray(Charsets.US_ASCII),
            payload = key.toByteArray(Charsets.US_ASCII),
        )
    }
}
```

`LegacyLinkPolicy.kt`:

```kotlin
package com.loosecannon.notenfc.core.links

/**
 * The 2024 share gate, preserved verbatim: any shared text containing "joplin" (case-sensitive)
 * is accepted as the link, unchanged. Phase 1 replaces this with LinkLaunchPolicy (D3 §10).
 */
object LegacyLinkPolicy {
    fun accept(sharedText: String?): String? =
        if (sharedText != null && sharedText.contains("joplin")) sharedText else null
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :core:test --console=plain -q`
Expected: BUILD SUCCESSFUL; 20 tests passed (6 + 9 + 5). Confirm with `find core/build/test-results -name '*.xml' | xargs grep -h -o 'tests="[0-9]*"'`.

- [ ] **Step 6: Guard the no-Android rule.** Run `grep -rn "import android" core/src` — must print nothing.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts core
git commit -m "add :core with the legacy key and ndef codec pinned by tests"
```

---

### Task 5: Rewire the activities to `:core` (behaviour preserved)

**Files:**
- Modify: `app/build.gradle.kts` (add `implementation(project(":core"))`)
- Modify: `app/src/main/java/com/looseCannon/noteNFC/mainActivity.kt`
- Modify: `app/src/main/java/com/looseCannon/noteNFC/NFCHandlerActivity.kt`
- Modify: `app/src/main/java/com/looseCannon/noteNFC/LaunchNoteNFCLinkActivity.kt`

**Interfaces:**
- Consumes: `LegacyKey.compute`, `LegacyLinkPolicy.accept`, `NdefCodec.decode`, `NdefCodec.encodeLegacy`, `NdefRecordData`, `TagPayload` from Task 4.

- [ ] **Step 1: Add the dependency** in `app/build.gradle.kts` `dependencies { implementation(project(":core")) ... }`.

- [ ] **Step 2: `mainActivity.kt`** — replace the private `getShortHash` and `transformLink` with calls to `:core`; everything else identical:

```kotlin
package com.looseCannon.noteNFC

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.loosecannon.notenfc.core.links.LegacyLinkPolicy
import com.loosecannon.notenfc.core.nfc.LegacyKey

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val sharedPreferences = getSharedPreferences("noteNFCURLs", Context.MODE_PRIVATE)

        val noteLink = LegacyLinkPolicy.accept(intent?.getStringExtra(Intent.EXTRA_TEXT))
        if (noteLink == null) {
            Toast.makeText(this, "No link received", Toast.LENGTH_LONG).show()
            return
        }

        val uniqueId = LegacyKey.compute(noteLink)
        sharedPreferences.edit().putString(uniqueId, noteLink).apply()

        val nfcIntent = Intent(this, NFCHandlerActivity::class.java)
        nfcIntent.putExtra("uniqueId", uniqueId)
        startActivity(nfcIntent)
        finish()
    }
}
```

- [ ] **Step 3: `NFCHandlerActivity.kt`** — in `writeLinkToTag`, replace the three lines that build `payload`, `domain`, `type` and call `NdefRecord.createExternal(domain, type, payload)` with:

```kotlin
val legacy = NdefCodec.encodeLegacy(currentUniqueId ?: return false)
val ndefRecord = NdefRecord.createExternal(NdefCodec.LEGACY_DOMAIN, NdefCodec.LEGACY_TYPE_NAME, legacy.payload)
```

(`createExternal` lower-cases domain and type itself; the on-tag type stays `com.loosecannon.notenfc:md5_short`, identical to before.) Add `import com.loosecannon.notenfc.core.nfc.NdefCodec`. Leave everything else in the file untouched, including the logging and the foreground-dispatch setup.

- [ ] **Step 4: `LaunchNoteNFCLinkActivity.kt`** — replace `val customData = String(messages[0].records[0].payload)` and the lookup with the codec:

```kotlin
val records = messages.firstOrNull()?.records.orEmpty().map { NdefRecordData(it.tnf.toInt(), it.type, it.payload) }
val noteGuid = when (val payload = NdefCodec.decode(records)) {
    is TagPayload.LegacyMd5 -> lookupNoteUrl(payload.key)
    else -> null
}
```

with imports `com.loosecannon.notenfc.core.nfc.NdefCodec`, `NdefRecordData`, `TagPayload`. Keep the existing `ACTION_VIEW` launch and toast exactly as they are (their hardening is Phase 1B).

- [ ] **Step 5: Build and run all unit tests**

Run: `./gradlew :core:test :app:assembleDebug --console=plain -q`
Expected: BUILD SUCCESSFUL. `grep -rn "MessageDigest\|contains(\"joplin\")" app/src` must print nothing (the logic now lives only in `:core`).

- [ ] **Step 6: Commit**

```bash
git add app
git commit -m "route the activities through :core, no behaviour change"
```

---

### Task 6: CI workflow

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Write the workflow**

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
      - uses: gradle/actions/setup-gradle@v4
      - name: unit tests and debug build
        run: ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results
          path: |
            core/build/test-results
            app/build/test-results
```

Note: `gradle/gradle-daemon-jvm.properties` carries foojay download URLs, so Gradle provisions the JDK 25 daemon itself on the runner; `setup-java` 17 only bootstraps the wrapper launcher.

- [ ] **Step 2: Validate locally that the exact command works**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain -q`
Expected: BUILD SUCCESSFUL (`:app:testDebugUnitTest` may report no tests; that is fine).

- [ ] **Step 3: Commit**

```bash
git add .github
git commit -m "ci: unit tests and debug build on push"
```

---

### Task 7: Spike S1 — Room 3.0.x + Compose + Navigation 3 on this toolchain

**Files:**
- Create (outside the repo, throwaway): `<scratchpad>/s1-spike/` — a minimal Gradle project with one Android app module
- Create (in the repo): `docs/design/spikes/S1-toolchain.md`

**Interfaces:**
- Produces: the report Phase 1A relies on: which exact versions configure, compile, and run a DAO test on the JVM; which fallbacks were needed.

- [ ] **Step 1: Scaffold the spike project** by copying `gradlew`, `gradlew.bat`, `gradle/` (wrapper + daemon JVM properties), `gradle.properties`, and `local.properties` from this worktree, then a `settings.gradle.kts` like Task 3's with `include(":app")`, and an `app/build.gradle.kts` that adds to Task 3's:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    id("com.google.devtools.ksp") version "<KSP2 version matching Kotlin 2.4.20, e.g. 2.4.20-1.0.x — look it up on Maven Central>"
    id("androidx.room3") version "3.0.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
}
android { /* as Task 3, namespace com.loosecannon.s1spike */ buildFeatures { compose = true } }
room3 { schemaDirectory("$projectDir/schemas") }
dependencies {
    implementation("androidx.room3:room3-runtime:3.0.3")
    ksp("androidx.room3:room3-compiler:3.0.3")
    implementation("androidx.sqlite:sqlite-bundled:2.7.1")
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.navigation3:navigation3-runtime:1.1.7")
    implementation("androidx.navigation3:navigation3-ui:1.1.7")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room3:room3-testing:3.0.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
```

Adjust versions if resolution fails; record every adjustment. If AGP 9.4's built-in Kotlin conflicts with `org.jetbrains.kotlin.plugin.compose`, try omitting it (AGP 9 may wire the Compose compiler itself) and record the outcome.

- [ ] **Step 2: Write the smallest schema and DAO**: an `@Entity data class Note(@PrimaryKey val id: String, val title: String)`, a `@Dao interface NoteDao { @Insert suspend fun insert(n: Note); @Query("SELECT * FROM Note WHERE id = :id") suspend fun byId(id: String): Note? }`, an `@Database(entities = [Note::class], version = 1, exportSchema = true) abstract class SpikeDb : RoomDatabase()`, and a one-screen Compose `Activity` using `NavDisplay` from Navigation 3 with a single entry.

- [ ] **Step 3: Write a JVM unit test** under `app/src/test` that opens the database with `BundledSQLiteDriver` in memory (`Room.inMemoryDatabaseBuilder(...)` is Android-context bound — use `Room.databaseBuilder<SpikeDb>(name = ":memory:").setDriver(BundledSQLiteDriver()).build()` or the Room 3 KMP-style builder as documented), inserts a note, reads it back, and asserts equality. It must run with `./gradlew :app:testDebugUnitTest` on the JVM, without Robolectric.

- [ ] **Step 4: Run** `./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain` in the spike directory. Also confirm `app/schemas/**/1.json` was exported.

- [ ] **Step 5: Write `docs/design/spikes/S1-toolchain.md`** with: the final `libs`/plugin versions that worked; each failure encountered and the adjustment; whether the DAO test ran on the JVM; whether schema export worked; whether the Compose compiler needed the explicit plugin; build wall time; a one-line verdict "Room 3.0.x confirmed as the Phase 1A starting line" or "fallback to Room 2.8.5 because <concrete deficiency>". If the verdict is fallback, also state what was tried.

- [ ] **Step 6: Commit the report only**

```bash
git add docs/design/spikes/S1-toolchain.md
git commit -m "spike s1: room 3 / compose / nav3 on agp 9.4, report"
```

---

### Task 8: Phase 0 exit-criteria evidence and the R-1 investigation

**Files:**
- Create: `docs/design/phase-0-evidence.md`

- [ ] **Step 1: Clone-build proof.** From a temporary directory outside the repo:

```bash
rm -rf <scratchpad>/clone-check
git clone --branch phase-0-foundation ~/Documents/Projects/AndroidStudioProjects/noteNFC-phase0 <scratchpad>/clone-check
cd <scratchpad>/clone-check
printf 'sdk.dir=~/Android/Sdk\n' > local.properties
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain -q && echo CLONE_BUILD_OK
git status --short   # must be empty except local.properties, which is ignored
```

Record the command, its result, and the test count.

- [ ] **Step 2: Legacy decode equivalence proof.** The shipped 1.0 APK's `LaunchNoteNFCLinkActivity` read `String(messages[0].records[0].payload)` and looked it up. Show, in the evidence file, that `NdefCodec.decode` yields the same key for the same bytes by citing the `NdefCodecTest.decodesValidLegacyKey` and `encodeLegacyRoundTrips` tests and the `LegacyKeyTest` vectors, and note the one deliberate difference: a payload that is not 8 lowercase hex chars is now reported as `Malformed` (the old app would have looked up a string that could not have existed as a key, then shown "not found") — same user-visible outcome.

- [ ] **Step 3: R-1 investigation (non-destructive).** Record:
  - `adb devices -l` output (no device was attached during planning; re-run and record).
  - The read-only keystore search result: run `find ~ /root -xdev \( -name '*.jks' -o -name '*.keystore' -o -name 'keystore.properties' \) -not -path '*/.gradle/caches/*' -not -path '*/build/*' -not -path '*/node_modules/*' 2>/dev/null` and list matches; state whether any is an Android app-signing keystore for noteNFC (expected: none; `/root/.android/debug.keystore` is this sandbox's own debug key).
  - Certificate comparison: `apksigner verify --print-certs app/release/app-release.apk` (use `~/Android/Sdk/build-tools/36.0.0/apksigner` or `34.0.0`), and `keytool -list -v -keystore /root/.android/debug.keystore -storepass android` SHA-256; state that neither matches the shipped release cert `e18854af…9a69` and that the shipped debug APK's cert (`apksigner verify --print-certs` on it, obtained from git history: `git show master:app/build/outputs/apk/debug/app-debug.apk > /tmp/legacy-debug.apk`) does not match this sandbox's debug key either.
  - Conclusion line: "Situation B (reinstall) must be assumed; situation A remains possible only if the user locates the `fillMateAndroid` keystore or the original debug keystore on another machine."

- [ ] **Step 4: Working-tree preservation check.** Record `git -C ~/Documents/Projects/AndroidStudioProjects/noteNFC status --short` showing the sibling working tree still has its two modified Gradle files and untracked `docs/`, untouched.

- [ ] **Step 5: Write `docs/design/phase-0-evidence.md`** with sections: Exit criteria (table: criterion from D7 Phase 0 | evidence | status), R-1 investigation, S1 verdict (one line + link to the spike report), Deviations from D7 (the release APK stays tracked; no Robolectric test — pure extraction used instead; the manual on-device legacy-tag check is pending the user's phone), and "Ready for Phase 1A: yes/no".

- [ ] **Step 6: Commit**

```bash
git add docs/design/phase-0-evidence.md
git commit -m "phase 0 evidence: clone build, legacy codec equivalence, signing key findings"
```
