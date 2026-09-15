# Phase 1C — Compose Shell and First User-Facing Experience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Phase 1B interim screens with the real noteNFC application shell: the Apollo Service Binder theme, a single-activity Navigation 3 host with bottom navigation, asset and link management, the six NFC states as sheets, the share-sheet card, a backup screen with the "no backup yet" nudge, and a settings shell — and prove milestone M1 end to end on a phone (tag written → backup → restore → tag resolves, entirely through the UI).

**Architecture:** `:core` gains reactive read ports (`Flow`) and the general `DeepLinkRoute`; nothing else moves out of `:app`. `:app` gains `ui/theme` (D12 as corrected by G1), three signature composables (`IdentityPlate`, `StatusBadge`, `LedgerEntry`) plus small layout primitives, `MainActivity` hosting `NavDisplay` with `@Serializable` route keys and a `NavigationBar` (Dashboard · Assets · Scan), `ShareActivity` hosting the share card in the caller's task, ViewModels (`lifecycle-viewmodel-compose`) built from `AppGraph`, and screens that consume only the 1A/1B use cases. `NfcDispatchActivity` becomes a UI-less trampoline: link tags launch, everything else lands in `MainActivity` as a `TagResult` route. The 1B `TagWriter`/`NfcReaderModeSession`/`OverwritePolicy` logic is reused unchanged behind a `TagWriteController` state holder. Interim screens and layouts are deleted.

**Tech Stack:** Compose BOM 2026.08.00 (Compose 1.12, Material 3 1.4), `org.jetbrains.kotlin.plugin.compose` 2.4.20 (required with AGP 9.4's built-in Kotlin), activity-compose 1.13.0, Navigation 3 1.1.7 (`navigation3-runtime` + `navigation3-ui`), lifecycle 2.10.0 (`lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`), kotlinx-serialization plugin on `:app` for route keys, `material-icons-core` (BOM) plus a few app vector drawables, Compose UI test (`ui-test-junit4`, `ui-test-manifest`) with `androidx.test` runner/rules for the device-run smoke suite. Everything else unchanged (AGP 9.4.0, Room 3.0.3, coroutines 1.10.2, JUnit 5 in `:core`, JUnit 4 in `:app`).

**Spec:** `docs/design/07-implementation-sequence.md` §1C (as revised by D13 §2: no migration report), `docs/design/12-visual-design-apollo-service-binder.md` (with G1 corrections a–j applied), `docs/design/g1/01-g1-visual-gate-report.md` §1, §5, §7, `docs/design/03-target-architecture.md` §3, §4, §9, §10, §13, `docs/design/06-legacy-compatibility.md` §8, `docs/design/13-compatibility-policy.md` §3, `docs/design/09-security-privacy.md` (rows "Deep links", "Exported components").

## Global Constraints

- **Design authority is D12 as corrected by G1** (`docs/design/g1/01-g1-visual-gate-report.md` §5 "Locked"). Palettes §3/§4 verbatim (dark `onSurfaceVariant` `#B9C3C9`, light DUE container `#F3C89A`); semantic states §5 plus `NO TARGET SET`; shapes 2/4/8/12/16dp; badges 4dp; buttons/fields 6dp; plate 8dp; typography roles §6 with 22sp entry / 28–32sp hero measurement sizes. No Compose screen references a raw colour for operational state: only `MaterialTheme.colorScheme` roles and `NoteNfcTheme.semanticColors`.
- **Restraint (D12 non-negotiables):** no NASA logos, worm typography, spacecraft imagery, CRT effects, fake switches, warning stripes, HUD styling; cards rare; elevation only for sheets/dialogs/menus/FAB; no FAB on dashboard or asset detail (G1 correction c).
- **Every operational state = position + wording + glyph + colour.** Never colour alone.
- **Navigation (G1 correction i):** one `MainActivity` (`ComponentActivity`, `launchMode="singleTask"`, exported: LAUNCHER + `VIEW notenfc://asset|link|tag`), bottom `NavigationBar` with exactly Dashboard · Assets · Scan; asset detail, edit, link detail, backup, settings and sheets are pushed on top. `ShareActivity` (exported, `SEND text/plain`, `excludeFromRecents`, standard launch mode) hosts the share card **in the caller's task** so "Done" returns to Joplin (D6 §8). `NfcDispatchActivity` keeps its three intent filters and becomes UI-less. Exported components after this phase: exactly `MainActivity`, `ShareActivity`, `NfcDispatchActivity` (+ debug `DebugBackupActivity`).
- **Deep links (D3 §13, D9):** navigation only; `notenfc://asset/<uuid>`, `notenfc://link/<uuid>`, `notenfc://tag/<uuid>`; malformed or unknown ids → snackbar/toast and land on Dashboard; intent extras on deep links are ignored. Parsing lives in `:core` (`DeepLinkRoute`), tested on the JVM.
- **NFC behaviour is 1B's, unchanged:** link tags launch immediately with no sheet (R-7); read-before-write, `OverwritePolicy`, confirmation remembered for the next tap, read-back before `ProvisionTag.complete`, lock only after a verified read-back, reader mode only while the Scan or Write screen is resumed. `TagWriter`, `NfcReaderModeSession`, `NdefBridge`, `LinkLauncher` are reused, not rewritten.
- **Backup (D7 §1C exit 1, 3):** Export (SAF `CreateDocument`) and Import-replace (SAF `OpenDocument` + typed confirmation dialog) through the UI; `AppPrefs.lastBackupAt` is set only after a successful export; Dashboard shows the "No backup yet" nudge until then. Wipe stays debug-only.
- **Data scope:** only 1A/1B entities (asset, tag binding, external link). No events, measurements, schedules, attachments, reminders, Todoist, supplies. Dashboard lists assets under CURRENT with the quiet line "No schedule yet". Asset edit fields: name (required), category, description, notes. Delete is archive-first (`AssetStatus.ARCHIVED`, R-9); hard delete is not in 1C.
- **Layering:** `:core` must not import `android.*`/`androidx.*`; ViewModels depend on `AppGraph` members (use cases, repositories) only; composables depend on ViewModel state and callbacks only.
- **Tests:** `:core` JUnit 5 block-bodied; `:app` JVM tests with `inMemoryDb()` for DAO flows and ViewModel state via fakes; a theme contrast test on the JVM; Compose smoke tests in `app/src/androidTest` compiled in every task (`:app:compileDebugAndroidTestKotlin`) and **executed on the attached phone in Task 8** (`:app:connectedDebugAndroidTest`). CI stays `:core:test :app:testDebugUnitTest :app:assembleDebug`.
- **Cross-phase rules (D7):** `versionCode` → 2; Room schema unchanged (v1), so no migration test is added; the backup importer is untouched.
- **Privacy:** no personal data, phone identifiers or note links in code, tests, previews or docs. Preview/sample data uses the D12 examples (rack UPS, generator, hot tub).
- **Commits:** casual, terse, human (repo `CLAUDE.md`); **never** any `Co-Authored-By` or AI attribution line. Repo-local identity GonzRon. Do not push. Do not touch the sibling checkout of `master`.
- **Build/test commands** (worktree root): `./gradlew :core:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew :app:assembleDebug :app:compileDebugAndroidTestKotlin`, full gate `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:compileDebugAndroidTestKotlin`.

---

## File map

| Path | Responsibility |
|---|---|
| `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts` | Compose/Nav3/lifecycle/serialization/test deps; compose compiler plugin; `versionCode 2` |
| `app/src/main/kotlin/com/loosecannon/notenfc/ui/theme/{Color,SemanticColors,Type,Shape,Theme}.kt` | D12 §15 theme, two layers |
| `app/src/test/kotlin/com/loosecannon/notenfc/ui/theme/ContrastTest.kt` | every foreground/container pair ≥ 4.5:1 in both themes |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/ports/Repositories.kt` (+ Room repos, fakes) | `observeAll()` / `observeForAsset()` flows |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/links/DeepLinkRoute.kt` (+ test) | `notenfc://asset|link|tag` parsing |
| `app/src/main/kotlin/com/loosecannon/notenfc/prefs/AppPrefs.kt` (+ test) | `lastBackupAt`, `appearanceMode` |
| `app/src/main/kotlin/com/loosecannon/notenfc/ui/components/{IdentityPlate,StatusBadge,LedgerEntry,SectionHeader,LabelValue,ActionGrid,QuietLine}.kt` | signature composables + primitives, with previews |
| `app/src/main/kotlin/com/loosecannon/notenfc/ui/nav/{Route,NoteNfcApp,BottomBar}.kt` | route keys, `NavDisplay`, bottom navigation |
| `app/src/main/kotlin/com/loosecannon/notenfc/MainActivity.kt`, `ShareActivity.kt` | hosts |
| `app/src/main/kotlin/com/loosecannon/notenfc/nfc/NfcDispatchActivity.kt` | trampoline (trimmed) |
| `app/src/main/kotlin/com/loosecannon/notenfc/ui/asset/{AssetsScreen,AssetDetailScreen,AssetEditScreen,AssetViewModels}.kt` | assets |
| `app/src/main/kotlin/com/loosecannon/notenfc/ui/links/{LinksScreen,LinkDetailScreen,LinkViewModels}.kt` | links |
| `app/src/main/kotlin/com/loosecannon/notenfc/ui/scan/{ScanScreen,TagResultSheet,WriteTagScreen,TagWriteController,ScanViewModels}.kt` | NFC UX |
| `app/src/main/kotlin/com/loosecannon/notenfc/ui/share/ShareCardScreen.kt` | share card |
| `app/src/main/kotlin/com/loosecannon/notenfc/ui/dashboard/DashboardScreen.kt`, `ui/backup/BackupScreen.kt`, `ui/settings/SettingsScreen.kt` | home, backup, settings |
| `app/src/androidTest/kotlin/com/loosecannon/notenfc/…` | Compose smoke suite |
| `docs/design/phase-1c-evidence.md` | M1 evidence |
| Deleted: `app/src/main/kotlin/com/loosecannon/notenfc/ui/interim/*`, `app/src/main/res/layout/*`, interim strings | |

---

### Task 1: Compose toolchain and the Apollo Service Binder theme

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/ui/theme/Color.kt`, `SemanticColors.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`
- Test: `app/src/test/kotlin/com/loosecannon/notenfc/ui/theme/ContrastTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `NoteNfcTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content)`, `NoteNfcTheme.semanticColors: NoteNfcSemanticColors` (via `CompositionLocal`), `StatusColor(foreground, container)`, `NoteNfcSemanticColors` with fields `maintenanceOkay, dueSoon, due, overdue, seasonInactive, paused, measurementLow, measurementInRange, measurementHigh, measurementNoTarget, reminderHealthy, reminderFailure, syncProblem, destructiveAction`; `NoteNfcLightSemanticColors`, `NoteNfcDarkSemanticColors`; `LightColorScheme`, `DarkColorScheme`; `NoteNfcTypography`; `NoteNfcShapes`; `MonoText` text style (`FontFamily.Monospace`, tabular numerals); `Eyebrow` text style (11sp, Medium, 0.6sp tracking).

- [ ] **Step 1: Catalog and plugins.** Add to `[versions]`: `composeBom = "2026.08.00"`, `activityCompose = "1.13.0"`, `navigation3 = "1.1.7"`, `lifecycle = "2.10.0"`, `androidxTestRunner = "1.7.0"`, `androidxTestRules = "1.7.0"`, `androidxTestExtJunit = "1.3.0"`. Add to `[libraries]`:

```toml
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
navigation3-runtime = { group = "androidx.navigation3", name = "navigation3-runtime", version.ref = "navigation3" }
navigation3-ui = { group = "androidx.navigation3", name = "navigation3-ui", version.ref = "navigation3" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "androidxTestRunner" }
androidx-test-rules = { group = "androidx.test", name = "rules", version.ref = "androidxTestRules" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestExtJunit" }
```

and to `[plugins]`: `compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }`. Root `build.gradle.kts`: add `alias(libs.plugins.compose.compiler) apply false`. If any of the three `androidx.test` versions or `lifecycle 2.10.0` fails to resolve, use the newest stable that does and record the actual number in your report (these four are the only guesses; everything else is the S1-verified set).

- [ ] **Step 2: `app/build.gradle.kts`.** Apply `alias(libs.plugins.compose.compiler)` and `alias(libs.plugins.kotlin.serialization)`; `versionCode = 2`; `buildFeatures { compose = true; buildConfig = true }`; `defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }`; dependencies:

```kotlin
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.core)
    implementation(libs.activity.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
```

(`kotlinx-serialization-json` is already in the catalog from `:core`.)

- [ ] **Step 3: Write the failing contrast test.** `ContrastTest.kt` (JUnit 4, pure JVM — `androidx.compose.ui.graphics.Color` is a value class over a `ULong` and loads fine in unit tests):

```kotlin
package com.loosecannon.notenfc.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** WCAG 2.x relative-luminance contrast, so the D12 numbers are checked rather than trusted. */
class ContrastTest {
    private fun channel(c: Float): Double = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun contrast(a: Color, b: Color): Double {
        val l1 = luminance(a); val l2 = luminance(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }
    private fun assertAtLeast(min: Double, fg: Color, bg: Color, what: String) {
        val r = contrast(fg, bg)
        assertTrue("$what contrast %.2f:1 < $min:1".format(r), r >= min)
    }
    private fun checkScheme(name: String, s: ColorScheme) {
        assertAtLeast(4.5, s.onPrimary, s.primary, "$name primary")
        assertAtLeast(4.5, s.onSecondaryContainer, s.secondaryContainer, "$name secondaryContainer")
        assertAtLeast(4.5, s.onTertiaryContainer, s.tertiaryContainer, "$name tertiaryContainer")
        assertAtLeast(4.5, s.onErrorContainer, s.errorContainer, "$name errorContainer")
        assertAtLeast(4.5, s.onBackground, s.background, "$name background")
        assertAtLeast(4.5, s.onSurface, s.surface, "$name surface")
        assertAtLeast(4.5, s.onSurfaceVariant, s.surface, "$name onSurfaceVariant on surface")
        assertAtLeast(4.5, s.onSurface, s.surfaceContainerLow, "$name surfaceContainerLow")
        assertAtLeast(3.0, s.outline, s.surface, "$name outline (graphic)")
    }
    private fun checkSemantic(name: String, c: NoteNfcSemanticColors) {
        listOf(
            "ok" to c.maintenanceOkay, "dueSoon" to c.dueSoon, "due" to c.due, "overdue" to c.overdue,
            "seasonInactive" to c.seasonInactive, "paused" to c.paused, "low" to c.measurementLow,
            "inRange" to c.measurementInRange, "high" to c.measurementHigh, "noTarget" to c.measurementNoTarget,
            "reminderHealthy" to c.reminderHealthy, "reminderFailure" to c.reminderFailure,
            "sync" to c.syncProblem, "destructive" to c.destructiveAction,
        ).forEach { (label, sc) -> assertAtLeast(4.5, sc.foreground, sc.container, "$name semantic $label") }
    }

    @Test fun lightSchemePairsMeetAA() { checkScheme("light", LightColorScheme) }
    @Test fun darkSchemePairsMeetAA() { checkScheme("dark", DarkColorScheme) }
    @Test fun lightSemanticPairsMeetAA() { checkSemantic("light", NoteNfcLightSemanticColors) }
    @Test fun darkSemanticPairsMeetAA() { checkSemantic("dark", NoteNfcDarkSemanticColors) }
    @Test fun darkMetadataRecedesFromBodyText() {
        // G1 correction h: onSurfaceVariant must be visibly darker than onSurface in dark mode
        assertTrue(luminance(DarkColorScheme.onSurfaceVariant) < luminance(DarkColorScheme.onSurface) * 0.75)
    }
    @Test fun okIsNotGreen() {
        val ok = NoteNfcLightSemanticColors.maintenanceOkay.foreground
        assertTrue("OK foreground must be a cool blue, not green", ok.blue > ok.green && ok.green > ok.red)
    }
}
```

- [ ] **Step 4: Run to verify it fails.** Run: `./gradlew :app:testDebugUnitTest --tests '*ContrastTest*'` → compilation failure (theme symbols unresolved).

- [ ] **Step 5: Implement the theme.** `Color.kt` — every value from D12 §3/§4 with correction h:

```kotlin
package com.loosecannon.notenfc.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Foundational palette (D12 §2) — named so previews and tests can refer to them.
val Navy900 = Color(0xFF102F4A); val Navy700 = Color(0xFF1F4E78); val Navy600 = Color(0xFF2F648E); val Navy200 = Color(0xFFA9CBE5); val Navy100 = Color(0xFFD8E7F2)
val Steel900 = Color(0xFF25313A); val Steel700 = Color(0xFF52606A); val Steel300 = Color(0xFFB9C6CE); val Steel100 = Color(0xFFDDE3E7)
val Signal900 = Color(0xFF173A4A); val Signal700 = Color(0xFF336B87); val Signal300 = Color(0xFFA4C6D5); val Signal100 = Color(0xFFD7EAF1)
val Brick600 = Color(0xFFA43D36); val Brick300 = Color(0xFFF2B8B5); val Brick100 = Color(0xFFF8DAD6)
val Carbon = Color(0xFF10171D); val Ink = Color(0xFF1B1F22); val WarmIvory = Color(0xFFF7F5EF); val PaperWhite = Color(0xFFFCFAF5)

val LightColorScheme = lightColorScheme(
    primary = Navy700, onPrimary = Color.White, primaryContainer = Navy100, onPrimaryContainer = Navy900,
    secondary = Steel700, onSecondary = Color.White, secondaryContainer = Steel100, onSecondaryContainer = Steel900,
    tertiary = Signal700, onTertiary = Color.White, tertiaryContainer = Signal100, onTertiaryContainer = Signal900,
    error = Brick600, onError = Color.White, errorContainer = Brick100, onErrorContainer = Color(0xFF3B0807),
    background = WarmIvory, onBackground = Ink, surface = PaperWhite, onSurface = Ink,
    surfaceVariant = Color(0xFFE2E5E6), onSurfaceVariant = Color(0xFF444B50),
    surfaceDim = Color(0xFFD7D8D3), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F2EC), surfaceContainer = Color(0xFFECEBE5),
    surfaceContainerHigh = Color(0xFFE6E5DF), surfaceContainerHighest = Color(0xFFDFE0DB),
    outline = Color(0xFF737B80), outlineVariant = Color(0xFFC1C7CA),
    inverseSurface = Color(0xFF2E3438), inverseOnSurface = Color(0xFFF4F2EC), inversePrimary = Navy200,
    surfaceTint = Navy700, scrim = Color.Black,
)

val DarkColorScheme = darkColorScheme(
    primary = Navy200, onPrimary = Color(0xFF0A3452), primaryContainer = Color(0xFF214E70), onPrimaryContainer = Color(0xFFD7E9F6),
    secondary = Steel300, onSecondary = Color(0xFF243139), secondaryContainer = Color(0xFF394750), onSecondaryContainer = Color(0xFFDDE6EB),
    tertiary = Signal300, onTertiary = Signal900, tertiaryContainer = Color(0xFF2A5267), onTertiaryContainer = Signal100,
    error = Brick300, onError = Color(0xFF601410), errorContainer = Color(0xFF8C2E2A), onErrorContainer = Color(0xFFFFDAD7),
    background = Carbon, onBackground = Color(0xFFE5E7E8), surface = Color(0xFF151D23), onSurface = Color(0xFFE5E7E8),
    surfaceVariant = Color(0xFF3D474E), onSurfaceVariant = Color(0xFFB9C3C9), // G1 correction h
    surfaceDim = Carbon, surfaceBright = Color(0xFF353F46),
    surfaceContainerLowest = Color(0xFF0C1217), surfaceContainerLow = Color(0xFF151D23), surfaceContainer = Color(0xFF1A2229),
    surfaceContainerHigh = Color(0xFF202A32), surfaceContainerHighest = Color(0xFF27323A),
    outline = Color(0xFF8D979E), outlineVariant = Color(0xFF3F4A51),
    inverseSurface = Color(0xFFE5E7E8), inverseOnSurface = Color(0xFF2C3134), inversePrimary = Navy700,
    surfaceTint = Navy200, scrim = Color.Black,
)
```

`SemanticColors.kt` — D12 §5 with the two G1 additions/changes:

```kotlin
package com.loosecannon.notenfc.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable data class StatusColor(val foreground: Color, val container: Color)

/** Operational meaning lives here, never in Material roles and never in raw colours at call sites (D12 §5, §15). */
@Immutable
data class NoteNfcSemanticColors(
    val maintenanceOkay: StatusColor, val dueSoon: StatusColor, val due: StatusColor, val overdue: StatusColor,
    val seasonInactive: StatusColor, val paused: StatusColor,
    val measurementLow: StatusColor, val measurementInRange: StatusColor, val measurementHigh: StatusColor,
    val measurementNoTarget: StatusColor,
    val reminderHealthy: StatusColor, val reminderFailure: StatusColor,
    val syncProblem: StatusColor, val destructiveAction: StatusColor,
)

val NoteNfcLightSemanticColors = NoteNfcSemanticColors(
    maintenanceOkay = StatusColor(Color(0xFF245B78), Color(0xFFDCEBF3)),
    dueSoon = StatusColor(Color(0xFF7A4B0A), Color(0xFFF6E5C3)),
    due = StatusColor(Color(0xFF8C4700), Color(0xFFF3C89A)),            // G1 correction b
    overdue = StatusColor(Color(0xFF8C2E2A), Color(0xFFF8DAD6)),
    seasonInactive = StatusColor(Color(0xFF586269), Color(0xFFE6E8E8)),
    paused = StatusColor(Color(0xFF5B4D6F), Color(0xFFE8E3EF)),
    measurementLow = StatusColor(Color(0xFF4F5F9A), Color(0xFFE2E5F6)),
    measurementInRange = StatusColor(Color(0xFF245B78), Color(0xFFDCEBF3)),
    measurementHigh = StatusColor(Color(0xFF8C4700), Color(0xFFF7D3AD)),
    measurementNoTarget = StatusColor(Color(0xFF444B50), Color(0xFFE6E5DF)),  // G1 correction f
    reminderHealthy = StatusColor(Color(0xFF245B78), Color(0xFFDCEBF3)),
    reminderFailure = StatusColor(Color(0xFF8C2E2A), Color(0xFFF8DAD6)),
    syncProblem = StatusColor(Color(0xFF684682), Color(0xFFE9DFF2)),
    destructiveAction = StatusColor(Color(0xFFA43D36), Color(0xFFF8DAD6)),
)

val NoteNfcDarkSemanticColors = NoteNfcSemanticColors(
    maintenanceOkay = StatusColor(Color(0xFF91BED6), Color(0xFF17384B)),
    dueSoon = StatusColor(Color(0xFFE4B45F), Color(0xFF4A320D)),
    due = StatusColor(Color(0xFFF0A15D), Color(0xFF573015)),
    overdue = StatusColor(Color(0xFFF2B8B5), Color(0xFF4E1C1A)),
    seasonInactive = StatusColor(Color(0xFFB1B8BC), Color(0xFF2A3136)),
    paused = StatusColor(Color(0xFFC4B4D3), Color(0xFF342C3B)),
    measurementLow = StatusColor(Color(0xFFB5C1F0), Color(0xFF2A3152)),
    measurementInRange = StatusColor(Color(0xFF91BED6), Color(0xFF17384B)),
    measurementHigh = StatusColor(Color(0xFFF0A15D), Color(0xFF573015)),
    measurementNoTarget = StatusColor(Color(0xFFD9E0E4), Color(0xFF202A32)),
    reminderHealthy = StatusColor(Color(0xFF91BED6), Color(0xFF17384B)),
    reminderFailure = StatusColor(Color(0xFFF2B8B5), Color(0xFF4E1C1A)),
    syncProblem = StatusColor(Color(0xFFCFB3E5), Color(0xFF3B2C46)),
    destructiveAction = StatusColor(Color(0xFFF2B8B5), Color(0xFF4E1C1A)),
)

val LocalNoteNfcSemanticColors = staticCompositionLocalOf { NoteNfcLightSemanticColors }
```

`Type.kt`:

```kotlin
package com.loosecannon.notenfc.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFeatureSettings
import androidx.compose.ui.unit.sp

/** Roles from D12 §6. Character comes from hierarchy, spacing and mono numerals, not a display face. */
val NoteNfcTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp),              // asset name / screen title
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 26.sp),                 // plate model
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),                // manufacturer/model, event title
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),                 // metadata value
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),                  // timestamps
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),                 // buttons
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp), // section title (uppercase applied by SectionHeader)
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),   // metadata label / badge
)

/** Serials, tag ids, readings: monospace with tabular numerals (D12 §6 "Technical typeface"). */
val MonoText = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum")
val MeasurementEntryText = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 22.sp, fontFeatureSettings = "tnum")
val MeasurementHeroText = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 30.sp, fontFeatureSettings = "tnum")
```

(If `fontFeatureSettings` is rejected by the compiler in this Compose version, drop it; `FontFamily.Monospace` is already tabular.)

`Shape.kt`:

```kotlin
package com.loosecannon.notenfc.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val NoteNfcShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(4.dp), medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp), extraLarge = RoundedCornerShape(16.dp),
)
val BadgeShape = RoundedCornerShape(4.dp)
val ControlShape = RoundedCornerShape(6.dp)   // buttons and text fields (D12 §7)
val PlateShape = RoundedCornerShape(8.dp)
val SheetShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
```

`Theme.kt` — D12 §15 verbatim in spirit:

```kotlin
package com.loosecannon.notenfc.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

object NoteNfcTheme {
    val semanticColors: NoteNfcSemanticColors
        @Composable @ReadOnlyComposable get() = LocalNoteNfcSemanticColors.current
}

/**
 * Two layers (D12 §15): Material answers "how do generic components look", the semantic layer
 * answers "what does this operational state mean". Dynamic colour may replace the first, never
 * the second (D12 §13). The setting that turns it on is Phase 7; the parameter exists now so the
 * rule is enforced from the first screen.
 */
@Composable
fun NoteNfcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val materialColors = resolveNoteNfcColorScheme(darkTheme, dynamicColor)
    val semanticColors = if (darkTheme) NoteNfcDarkSemanticColors else NoteNfcLightSemanticColors
    CompositionLocalProvider(LocalNoteNfcSemanticColors provides semanticColors) {
        MaterialTheme(colorScheme = materialColors, typography = NoteNfcTypography, shapes = NoteNfcShapes, content = content)
    }
}

@Composable
private fun resolveNoteNfcColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme {
    if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    return if (darkTheme) DarkColorScheme else LightColorScheme
}
```

- [ ] **Step 6: Build and test.** Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest` → BUILD SUCCESSFUL, `:app` 33 + 6 = **39** tests. If a semantic pair fails the 4.5:1 check, adjust the *container* toward the theme background until it passes and report the changed value (the foreground colours are the ones D12 pins). Also confirm `./gradlew :core:test` is still 104.

- [ ] **Step 7: Commit.** `git add gradle build.gradle.kts app/build.gradle.kts app/src` → `git commit -m "compose toolchain + apollo service binder theme, contrast-tested"`

---

### Task 2: Reactive read ports, deep-link routes, app preferences

**Files:**
- Modify: `core/src/main/kotlin/com/loosecannon/notenfc/core/ports/Repositories.kt`, `app/src/main/kotlin/com/loosecannon/notenfc/data/room/dao/{AssetDao,NfcTagDao,ExternalLinkDao}.kt`, `app/src/main/kotlin/com/loosecannon/notenfc/data/room/RoomRepositories.kt`, `core/src/test/kotlin/com/loosecannon/notenfc/core/testing/InMemoryRepositories.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/links/DeepLinkRoute.kt`, `core/src/test/kotlin/com/loosecannon/notenfc/core/links/DeepLinkRouteTest.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/prefs/AppPrefs.kt`, `app/src/test/kotlin/com/loosecannon/notenfc/prefs/AppPrefsTest.kt` (uses a `Map`-backed fake of the tiny `KeyValueStore` interface)
- Test: `app/src/test/kotlin/com/loosecannon/notenfc/data/room/RepositoryFlowsTest.kt`

**Interfaces:**
- Consumes: 1A ports and Room DAOs; `TagRoute` (1B).
- Produces:
  - `AssetRepository.observeAll(): Flow<List<Asset>>` (active first, then archived, name NOCASE); `TagRepository.observeForAsset(assetId): Flow<List<TagBinding>>`, `TagRepository.observeForLink(linkId)`; `LinkRepository.observeAll(): Flow<List<ExternalLink>>`, `LinkRepository.observeForAsset(assetId)`.
  - `DeepLink = Asset(id: AssetId) | Link(id: LinkId) | Tag(payload: TagPayload) | Malformed(reason)`; `DeepLinkRoute.parse(scheme: String?, host: String?, pathSegments: List<String>): DeepLink?` (null = not ours; `TagRoute.parse` delegates for host `tag`).
  - `KeyValueStore` (`getLong/putLong/getString/putString`), `SharedPrefsStore(context)`, `AppPrefs(store)` with `lastBackupAt: Long?` (null until set), `appearanceMode: AppearanceMode` (`SYSTEM|LIGHT|DARK`, default SYSTEM), `markBackupExported(now)`.

- [ ] **Step 1: Failing tests.** `DeepLinkRouteTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.links

import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.TagId
import com.loosecannon.notenfc.core.nfc.TagPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DeepLinkRouteTest {
    private val id = "123e4567-e89b-12d3-a456-426614174000"
    @Test fun assetRoute() { assertEquals(DeepLink.Asset(AssetId(id)), DeepLinkRoute.parse("notenfc", "asset", listOf(id))) }
    @Test fun linkRoute() { assertEquals(DeepLink.Link(LinkId(id)), DeepLinkRoute.parse("notenfc", "link", listOf(id))) }
    @Test fun tagRouteDelegatesToTagRoute() { assertEquals(DeepLink.Tag(TagPayload.V1(TagId(id))), DeepLinkRoute.parse("notenfc", "tag", listOf(id))) }
    @Test fun otherSchemesAndHostsAreNotOurs() {
        assertNull(DeepLinkRoute.parse("https", "asset", listOf(id)))
        assertNull(DeepLinkRoute.parse("notenfc", "health", emptyList()))   // Phase 3 route, not yet
        assertNull(DeepLinkRoute.parse(null, null, emptyList()))
    }
    @Test fun badIdsAreMalformedNeverExceptions() {
        assertIs<DeepLink.Malformed>(DeepLinkRoute.parse("notenfc", "asset", emptyList()))
        assertIs<DeepLink.Malformed>(DeepLinkRoute.parse("notenfc", "asset", listOf("nope")))
        assertIs<DeepLink.Malformed>(DeepLinkRoute.parse("notenfc", "asset", listOf(id, "schedule", id)))  // 1C: schedules not routed yet
        assertIs<DeepLink.Malformed>(DeepLinkRoute.parse("notenfc", "link", listOf(id.uppercase())))
        assertIs<DeepLink.Malformed>(DeepLinkRoute.parse("notenfc", "tag", listOf("nope")))
    }
}
```

`RepositoryFlowsTest.kt` (JUnit 4, `runTest`, `inMemoryDb()`): `assetsObserveAllEmitsOnUpsertAndOrdersActiveFirstThenName` (insert "zeta" active, "Alpha" archived, "beta" active → first emission `[Alpha? no: beta, zeta, Alpha]`; upsert a fourth → second emission), `tagsObserveForAssetFiltersByAsset`, `linksObserveAllEmitsOnDelete`. Use `first()` after each write rather than turbine.

`AppPrefsTest.kt`: `lastBackupAtIsNullUntilMarked`, `markBackupExportedStoresTheInstant`, `appearanceDefaultsToSystemAndRoundTrips`, `unknownStoredModeFallsBackToSystem`.

- [ ] **Step 2: Run to verify they fail** (compilation).

- [ ] **Step 3: Implement.** Ports — add to `Repositories.kt` (import `kotlinx.coroutines.flow.Flow`):

```kotlin
interface AssetRepository { /* existing */ ; fun observeAll(): Flow<List<Asset>> }
interface TagRepository { /* existing */ ; fun observeForAsset(assetId: AssetId): Flow<List<TagBinding>>; fun observeForLink(linkId: LinkId): Flow<List<TagBinding>> }
interface LinkRepository { /* existing */ ; fun observeAll(): Flow<List<ExternalLink>>; fun observeForAsset(assetId: AssetId): Flow<List<ExternalLink>> }
```

DAOs (Room 3 supports `Flow` return types on `@Query`):

```kotlin
@Query("SELECT * FROM asset ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END, name COLLATE NOCASE") fun observeAll(): Flow<List<AssetEntity>>
@Query("SELECT * FROM nfc_tag WHERE asset_id = :assetId ORDER BY created_at") fun observeForAsset(assetId: String): Flow<List<NfcTagEntity>>
@Query("SELECT * FROM nfc_tag WHERE link_id = :linkId ORDER BY created_at") fun observeForLink(linkId: String): Flow<List<NfcTagEntity>>
@Query("SELECT * FROM external_link ORDER BY label COLLATE NOCASE") fun observeAll(): Flow<List<ExternalLinkEntity>>
@Query("SELECT * FROM external_link WHERE asset_id = :assetId ORDER BY label COLLATE NOCASE") fun observeForAsset(assetId: String): Flow<List<ExternalLinkEntity>>
```

(Use the real column names from the entities; check `NfcTagEntity`/`ExternalLinkEntity` before writing the SQL.) Room repositories map with the existing mappers via `.map { list -> list.map { it.toDomain() } }`. Fakes: keep the `rows` maps, add a `MutableStateFlow<Int>` "version" bumped on every mutation and derive the flows with `map { rows.values... }` — simple and correct for tests.

`DeepLinkRoute.kt`:

```kotlin
package com.loosecannon.notenfc.core.links

import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.nfc.TagPayload
import com.loosecannon.notenfc.core.nfc.TagRoute

/** The `notenfc://` contract (D3 §13): navigation only, validated by shape here and by existence in the UI. */
sealed interface DeepLink {
    data class Asset(val id: AssetId) : DeepLink
    data class Link(val id: LinkId) : DeepLink
    data class Tag(val payload: TagPayload) : DeepLink
    data class Malformed(val reason: String) : DeepLink
}

object DeepLinkRoute {
    const val SCHEME = "notenfc"
    private val canonicalUuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    fun parse(scheme: String?, host: String?, pathSegments: List<String>): DeepLink? {
        if (scheme != SCHEME) return null
        return when (host) {
            TagRoute.HOST -> TagRoute.parse(scheme, host, pathSegments)?.let { p ->
                if (p is TagPayload.Malformed) DeepLink.Malformed(p.reason) else DeepLink.Tag(p)
            }
            "asset" -> single(pathSegments)?.let { DeepLink.Asset(AssetId(it)) } ?: DeepLink.Malformed("notenfc://asset needs one tag id segment")
            "link" -> single(pathSegments)?.let { DeepLink.Link(LinkId(it)) } ?: DeepLink.Malformed("notenfc://link needs one id segment")
            else -> null
        }
    }

    private fun single(segments: List<String>): String? = segments.singleOrNull()?.takeIf { canonicalUuid.matches(it) }
}
```

`AppPrefs.kt`:

```kotlin
package com.loosecannon.notenfc.prefs

import android.content.Context

interface KeyValueStore {
    fun getLong(key: String): Long?; fun putLong(key: String, value: Long)
    fun getString(key: String): String?; fun putString(key: String, value: String)
}

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences("notenfc", Context.MODE_PRIVATE)
    override fun getLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    override fun putLong(key: String, value: Long) { prefs.edit().putLong(key, value).apply() }
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
}

enum class AppearanceMode { SYSTEM, LIGHT, DARK }

/** The few per-install facts the UI needs that are not domain data. Backed up? No — they are device-local by design. */
class AppPrefs(private val store: KeyValueStore) {
    val lastBackupAt: Long? get() = store.getLong(KEY_LAST_BACKUP)
    fun markBackupExported(now: Long) = store.putLong(KEY_LAST_BACKUP, now)
    var appearanceMode: AppearanceMode
        get() = store.getString(KEY_APPEARANCE)?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() } ?: AppearanceMode.SYSTEM
        set(value) = store.putString(KEY_APPEARANCE, value.name)
    private companion object { const val KEY_LAST_BACKUP = "last_backup_at"; const val KEY_APPEARANCE = "appearance_mode" }
}
```

Wire `val prefs = AppPrefs(SharedPrefsStore(context))` into `AppGraph`.

- [ ] **Step 4: Run all tests.** `./gradlew :core:test :app:testDebugUnitTest` → `:core` 104 + 5 = **109**, `:app` 39 + 3 + 4 = **46**.

- [ ] **Step 5: Commit.** `git commit -m "flows on the read ports, notenfc:// route parser, app prefs"`

---

### Task 3: Signature composables and layout primitives

**Files:**
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/ui/components/SectionHeader.kt`, `LabelValue.kt`, `StatusBadge.kt`, `IdentityPlate.kt`, `LedgerEntry.kt`, `ActionGrid.kt`, `QuietLine.kt`, `Previews.kt`
- Create: `app/src/main/res/drawable/ic_contactless.xml` (24dp Material "contactless" path), `ic_nfc_tag.xml` (Material "nfc")
- Test: `app/src/androidTest/kotlin/com/loosecannon/notenfc/ui/components/ComponentsSmokeTest.kt` (compiled now, run in Task 8)

**Interfaces:**
- Consumes: Task 1 theme.
- Produces:
  - `SectionHeader(title: String, trailing: (@Composable () -> Unit)? = null)` — 12sp SemiBold uppercase, 1dp `outlineVariant` rule, 18dp top / 6dp bottom.
  - `LabelValue(label: String, value: String, mono: Boolean = false, valueStyle: TextStyle? = null)` — eyebrow over value.
  - `StatusBadge(label: String, colors: StatusColor, icon: ImageVector? = null, modifier)` — 4dp rectangle, glyph 16dp, 11sp Bold uppercase; `contentDescription` = label.
  - `IdentityPlate(category: String, model: String, name: String?, cells: List<Pair<String, PlateValue>>, icon: ImageVector)` where `PlateValue(text: String, mono: Boolean)`; renders "—" for blank text.
  - `LedgerEntry(date: LocalDate-free: day: String, month: String, year: String, title: String, detail: String? = null, badge: (@Composable () -> Unit)? = null, meta: List<String> = emptyList())`.
  - `ActionGrid(actions: List<ActionSpec>)` with `ActionSpec(label, icon, outlined: Boolean, onClick)` — 2 per row, 44dp, `ControlShape`.
  - `QuietLine(text: String)` — `onSurfaceVariant`, 14sp, used for "No schedule yet", "No entries yet".
  - `StatusBlock(kind: StatusColor, headline: String, title: String, detail: String, icon: ImageVector, leftRule: Boolean)` — the asset-screen due/overdue block.

- [ ] **Step 1: Write the smoke test first** (`ComponentsSmokeTest.kt`, `createComposeRule()`):

```kotlin
@Test fun identityPlateShowsDashForBlankValues() {
    rule.setContent { NoteNfcTheme { IdentityPlate(category = "Battery / power", model = "Rack UPS", name = null,
        cells = listOf("Serial" to PlateValue("", mono = true), "NFC tag" to PlateValue("41c11b73 · v1", mono = true)), icon = Icons.Outlined.Info) } }
    rule.onNodeWithText("—").assertIsDisplayed()
    rule.onNodeWithText("41c11b73 · v1").assertIsDisplayed()
}
@Test fun statusBadgeExposesItsLabelToAccessibility() {
    rule.setContent { NoteNfcTheme { StatusBadge(label = "Overdue", colors = NoteNfcLightSemanticColors.overdue, icon = Icons.Outlined.Warning) } }
    rule.onNodeWithContentDescription("Overdue").assertExists()
}
```

- [ ] **Step 2: Implement** each component per D12 §7/§8 and G1 §1.1. `IdentityPlate`: `Surface(color = surfaceContainerLow, shape = PlateShape, border = BorderStroke(1.dp, outlineVariant))`, `Column(padding 14.dp)`: `Row` eyebrow + icon (tint `onSurfaceVariant`); model `titleLarge`; name `bodyLarge` in `onSurfaceVariant`; `HorizontalDivider(color = outlineVariant)` with 10dp above/below; cells in a 2-column grid (`Row`s of two `LabelValue`s, `Modifier.weight(1f)`). `StatusBadge`: `Row` in `Surface(color = colors.container, shape = BadgeShape)` padding 7×2dp, icon `Modifier.size(16.dp)`, text `labelSmall` Bold uppercase, `Modifier.semantics { contentDescription = label }` on the row and `clearAndSetSemantics` is **not** used (the badge text must stay readable). `LedgerEntry`: `Row` with a 64dp date column (`day month` SemiBold 12sp, `year` Regular below), content column; `HorizontalDivider` between entries is the caller's job (a `LedgerList` helper adds them). `ActionGrid`: `OutlinedButton`/`FilledTonalButton` with `ControlShape`, `Modifier.height(44.dp)`. Add `@Preview(showBackground = true)` + `@Preview(uiMode = UI_MODE_NIGHT_YES)` pairs in `Previews.kt` using the D12 sample data (rack UPS).

- [ ] **Step 3: Build.** `./gradlew :app:assembleDebug :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL; no Kotlin warnings in the new files.

- [ ] **Step 4: Commit.** `git commit -m "identity plate, status badge, ledger entry and the small layout primitives"`

---

### Task 4: Navigation shell, hosts, dispatch trampoline, interim removal

**Files:**
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/ui/nav/Route.kt`, `NoteNfcApp.kt`, `BottomBar.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/MainActivity.kt`, `ShareActivity.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/nfc/NfcDispatchActivity.kt` (trampoline), `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/kotlin/com/loosecannon/notenfc/di/AppGraph.kt` (`prefs`)
- Delete: `app/src/main/kotlin/com/loosecannon/notenfc/ui/interim/*`, `app/src/main/res/layout/*`
- Test: `app/src/androidTest/kotlin/com/loosecannon/notenfc/ui/nav/NavigationSmokeTest.kt` (compiled now)

**Interfaces:**
- Consumes: Tasks 1–3; `Resolution`, `ResolveTag`, `OpenLink`, `LinkLauncher`, `NdefBridge`, `TagRoute`, `DeepLinkRoute`.
- Produces:
  - `Route` (`@Serializable`, `NavKey`): `Dashboard`, `Assets`, `AssetDetail(id)`, `AssetEdit(id: String?)`, `Links`, `LinkDetail(id)`, `Scan`, `TagResult(format: String, key: String)`, `WriteTag(targetKind: String, targetId: String?, label: String?)`, `Backup`, `Settings`.
  - `NoteNfcApp(graph: AppGraph, initialRoutes: List<Route>, pendingDeepLink: Flow<Route?>)`: `NavDisplay` + `Scaffold` with `BottomBar` visible only on the three top-level routes; `onBack` pops; deep links push.
  - `MainActivity` (`ComponentActivity`): applies `NoteNfcTheme(darkTheme = mode)` from `AppPrefs.appearanceMode`; `enableEdgeToEdge()`; `onCreate`/`onNewIntent` translate the intent into a `Route` via `DeepLinkRoute` (`VIEW`) or the internal extras `EXTRA_TAG_FORMAT`/`EXTRA_TAG_KEY` (from the dispatch trampoline) and push it; malformed/unknown → `Dashboard` + snackbar "That link doesn't point at anything here."
  - `ShareActivity`: reads `EXTRA_TEXT` as `CharSequence`; hosts `NoteNfcTheme { ShareFlow(text, onFinished = { finish() }) }` (the composables arrive in Task 6; this task ships the host with a placeholder that shows the extracted URI and a Close button).
  - `NfcDispatchActivity`: no layout; `LaunchLink` → `LinkLauncher.open` + `finish()`; every other payload → `startActivity(Intent(this, MainActivity::class.java).putExtra(EXTRA_TAG_FORMAT, …).putExtra(EXTRA_TAG_KEY, …).addFlags(FLAG_ACTIVITY_NEW_TASK))` + `finish()`; `Foreign/Malformed/Empty/NewerVersion` payloads are forwarded with format `"NONE"` and the reason as key so the app can show the "not ours" sheet. Deep-link `VIEW notenfc://tag/…` moves to `MainActivity`'s filter; `NfcDispatchActivity` keeps only the two NDEF filters.

- [ ] **Step 1: Routes.**

```kotlin
package com.loosecannon.notenfc.ui.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Route : NavKey {
    @Serializable data object Dashboard : Route
    @Serializable data object Assets : Route
    @Serializable data class AssetDetail(val id: String) : Route
    @Serializable data class AssetEdit(val id: String?) : Route
    @Serializable data object Links : Route
    @Serializable data class LinkDetail(val id: String) : Route
    @Serializable data object Scan : Route
    @Serializable data class TagResult(val format: String, val key: String) : Route
    @Serializable data class WriteTag(val targetKind: String, val targetId: String?, val label: String?) : Route
    @Serializable data object Backup : Route
    @Serializable data object Settings : Route
}
val TopLevelRoutes: List<Route> = listOf(Route.Dashboard, Route.Assets, Route.Scan)
```

- [ ] **Step 2: `NoteNfcApp.kt`.** Use `rememberNavBackStack(Route.Dashboard)` (requires the `@Serializable` keys; if it fails to save/restore under 1.1.7, fall back to `rememberSaveable(saver = listSaver(...))` mapping routes to strings and note it). Structure:

```kotlin
@Composable
fun NoteNfcApp(graph: AppGraph, deepLinks: SharedFlow<Route>, snackbar: SharedFlow<String>) {
    val backStack = rememberNavBackStack(Route.Dashboard)
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { deepLinks.collect { route -> backStack.add(route) } }
    LaunchedEffect(Unit) { snackbar.collect { snackbarHost.showSnackbar(it) } }
    val current = backStack.lastOrNull()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = { if (current in TopLevelRoutes) BottomBar(current = current as Route, onSelect = { backStack.switchTopLevel(it) }) },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.padding(padding),
            entryProvider = entryProvider {
                entry<Route.Dashboard> { DashboardScreen(graph, onOpenAsset = { backStack.add(Route.AssetDetail(it)) }, onBackup = { backStack.add(Route.Backup) }, onSettings = { backStack.add(Route.Settings) }, onNewAsset = { backStack.add(Route.AssetEdit(null)) }) }
                entry<Route.Assets> { AssetsScreen(...) }
                entry<Route.AssetDetail> { key -> AssetDetailScreen(graph, key.id, ...) }
                entry<Route.AssetEdit> { key -> AssetEditScreen(graph, key.id, onDone = { backStack.removeLastOrNull() }) }
                entry<Route.Links> { LinksScreen(...) }
                entry<Route.LinkDetail> { key -> LinkDetailScreen(graph, key.id, ...) }
                entry<Route.Scan> { ScanScreen(graph, onResolved = { r -> ... }) }
                entry<Route.TagResult> { key -> TagResultSheet(graph, key.format, key.key, ...) }
                entry<Route.WriteTag> { key -> WriteTagScreen(graph, key, onDone = { backStack.removeLastOrNull() }) }
                entry<Route.Backup> { BackupScreen(graph, onBack = { backStack.removeLastOrNull() }) }
                entry<Route.Settings> { SettingsScreen(graph, onBack = { backStack.removeLastOrNull() }) }
            },
        )
    }
}
/** Top-level switch keeps one entry per destination at the root (no deep stacks of Dashboard/Assets/Scan). */
private fun MutableList<NavKey>.switchTopLevel(route: Route) { clear(); add(route) }
```

In this task the screen composables are **stubs** (a `Scaffold` with a `TopAppBar` titled after the route and a `QuietLine("Coming in Task 5–7")`); Tasks 5–7 replace them. `BottomBar`: `NavigationBar` with three `NavigationBarItem`s (icons `Icons.Outlined.Home`? use `Icons.Outlined.Home` for Dashboard, `Icons.Outlined.List` for Assets, the `ic_contactless` drawable for Scan), labels "Dashboard", "Assets", "Scan", indicator colour `secondaryContainer`.

- [ ] **Step 3: `MainActivity.kt`.**

```kotlin
class MainActivity : ComponentActivity() {
    private val graph: AppGraph get() = (application as NoteNfcApp).graph
    private val deepLinks = MutableSharedFlow<Route>(extraBufferCapacity = 4)
    private val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode = graph.prefs.appearanceMode
            val dark = when (mode) { AppearanceMode.SYSTEM -> isSystemInDarkTheme(); AppearanceMode.LIGHT -> false; AppearanceMode.DARK -> true }
            NoteNfcTheme(darkTheme = dark) { NoteNfcApp(graph, deepLinks, messages) }
        }
        if (savedInstanceState == null) routeFrom(intent)?.let { deepLinks.tryEmit(it) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); routeFrom(intent)?.let { deepLinks.tryEmit(it) } }

    /** Only the data URI (deep links) or the two trampoline extras are read; every other extra is ignored. */
    private fun routeFrom(intent: Intent): Route? {
        intent.getStringExtra(EXTRA_TAG_FORMAT)?.let { f -> return Route.TagResult(f, intent.getStringExtra(EXTRA_TAG_KEY).orEmpty()) }
        if (intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data
        return when (val d = DeepLinkRoute.parse(uri?.scheme, uri?.host, uri?.pathSegments.orEmpty())) {
            is DeepLink.Asset -> Route.AssetDetail(d.id.value)
            is DeepLink.Link -> Route.LinkDetail(d.id.value)
            is DeepLink.Tag -> (d.payload as? TagPayload.V1)?.let { Route.TagResult("V1", it.tagId.value) }
            is DeepLink.Malformed -> { messages.tryEmit("That link doesn't point at anything here."); null }
            null -> null
        }
    }
    companion object { const val EXTRA_TAG_FORMAT = "tag_format"; const val EXTRA_TAG_KEY = "tag_key" }
}
```

Existence of an asset/link id is checked by the screen (unknown → snackbar + pop). Note `setContent` reads `appearanceMode` once; the Settings screen restarts the activity (`recreate()`) after a change — acceptable in 1C.

- [ ] **Step 4: Manifest.** Replace the activities block with:

```xml
        <activity android:name="com.loosecannon.notenfc.MainActivity" android:exported="true" android:launchMode="singleTask"
            android:configChanges="orientation|screenSize|keyboardHidden" android:theme="@style/Theme.NoteNfc">
            <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" /><category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="notenfc" android:host="asset" /><data android:scheme="notenfc" android:host="link" /><data android:scheme="notenfc" android:host="tag" />
            </intent-filter>
        </activity>
        <activity android:name="com.loosecannon.notenfc.ShareActivity" android:exported="true" android:excludeFromRecents="true"
            android:configChanges="orientation|screenSize|keyboardHidden" android:theme="@style/Theme.NoteNfc">
            <intent-filter><action android:name="android.intent.action.SEND" /><category android:name="android.intent.category.DEFAULT" /><data android:mimeType="text/plain" /></intent-filter>
        </activity>
        <activity android:name="com.loosecannon.notenfc.nfc.NfcDispatchActivity" android:exported="true" android:theme="@android:style/Theme.Translucent.NoTitleBar" android:excludeFromRecents="true">
            (the two NDEF_DISCOVERED filters exactly as today; the VIEW filter removed)
        </activity>
```

Add `res/values/themes.xml` with `<style name="Theme.NoteNfc" parent="android:Theme.Material.NoActionBar"/>` (Compose paints everything; the window background is set by the theme's `background` in `NoteNfcApp` via `Scaffold`). Keep `<queries>`. Delete the interim activities, the three layouts, and their strings (`app_name` stays; add `dispatch_nothing` if still used).

- [ ] **Step 5: Trampoline.** Rewrite `NfcDispatchActivity` per the Interfaces block; keep `handle()`'s guarded `payloadOf` and the `LaunchLink` path with its try/catch; remove all View code. It must not call `setContentView`.

- [ ] **Step 6: Build + manifest check.** `./gradlew :app:assembleDebug :app:assembleRelease :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest` → green; `grep -c 'android:exported="true"' app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml` → 3; no `TECH_DISCOVERED`; `grep -rn "ui.interim" app/src` → nothing.

- [ ] **Step 7: Commit.** `git commit -m "single-activity nav3 shell, share host, dispatch trampoline; interim screens gone"`

---

### Task 5: Assets — list, detail, create/edit, archive

**Files:**
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/ui/asset/AssetsScreen.kt`, `AssetDetailScreen.kt`, `AssetEditScreen.kt`, `AssetViewModels.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/UpdateAsset.kt`, `ArchiveAsset.kt` (+ tests in `core/src/test/.../usecase/AssetUseCasesTest.kt`)
- Test: `app/src/test/kotlin/com/loosecannon/notenfc/ui/asset/AssetViewModelsTest.kt` (fakes from `:core` test sources are not visible to `:app`; add a tiny `app/src/test/.../testing/FakeGraph.kt` that builds the real Room in-memory repositories and the use cases — same shape as `AppGraph` but on `inMemoryDb()`)

**Interfaces:**
- Consumes: `CreateAsset`, `AssetRepository.observeAll`, `TagRepository.observeForAsset`, `LinkRepository.observeForAsset`, components, theme.
- Produces:
  - `UpdateAsset(assets, uow, clock).run(id, name, category, description, notes): Asset` (blank name refused); `ArchiveAsset(assets, uow, clock).run(id)` / `.unarchive(id)`.
  - `AssetsViewModel(graph)`: `state: StateFlow<AssetsState(items: List<Asset>, showArchived: Boolean)>`; `toggleArchived()`.
  - `AssetDetailViewModel(graph, id)`: `state: StateFlow<AssetDetailState?>` with `asset`, `tags: List<TagBinding>`, `links: List<ExternalLink>`; `archive()`, `unarchive()`; `missing: StateFlow<Boolean>`.
  - `AssetEditViewModel(graph, id?)`: fields + `save(): Result<AssetId>`.

- [ ] **Step 1: Tests first** — `AssetUseCasesTest` (update trims and refuses blank; archive flips status and bumps `updatedAt`; unarchive restores ACTIVE), `AssetViewModelsTest` (`runTest` + `UnconfinedTestDispatcher`: list emits after create; detail exposes tags bound to the asset only; edit `save` with blank name returns failure and writes nothing; `missing` is true for an unknown id).

- [ ] **Step 2: Screens** per G1 §1.1:
  - **AssetsScreen**: `TopAppBar("Assets")`, a filter chip row (`FilterChip("Show archived")`), `LazyColumn` of rows (name `titleSmall`, category `bodySmall` in `onSurfaceVariant`, archived rows show `StatusBadge("Archived", seasonInactive)`), hairline dividers, empty state `QuietLine("No assets yet")` + `Button("Add asset")`. A single `ExtendedFloatingActionButton("Add asset")`? **No FAB** on lists either — keep a `TopAppBar` action icon `Icons.Outlined.Add`.
  - **AssetDetailScreen**: `TopAppBar` with back and overflow (Edit, Archive/Unarchive); `IdentityPlate(category, model = asset.name, name = asset.description.takeIf { it.isNotBlank() }, cells = [SERIAL "—" (no field yet: show "—"), NFC TAG (first tag: 8 chars + " · v1"/" · legacy", or "—"), CREATED (date), LINKS (count)], icon by category keyword)`; no status block in 1C (no schedules) — instead `QuietLine("No schedule yet")` under the plate; `ActionGrid([Write tag (outlined, → Route.WriteTag("asset", id, name)), Edit (outlined), Links (tonal → Links filtered? in 1C: opens LinkDetail of the first link or Links list), Backup (tonal)])`; `SectionHeader("Tags")` listing `LedgerEntry(date = writtenAt or createdAt, title = "Tag written"/"Tag bound", detail = "${id.take(8)} · v1", badge = status if not ACTIVE)` or `QuietLine("No tag yet · Write tag to add one")`; `SectionHeader("Links")` with rows (label, kind) or `QuietLine`; `SectionHeader("Notes")` with the notes text or `QuietLine("No notes")`. Archived assets show `StatusBadge("Archived")` next to the plate eyebrow.
  - **AssetEditScreen**: four `OutlinedTextField`s (`ControlShape`), name with `isError` + supporting text when blank after an attempt; `TopAppBar` Save (text button) + bottom filled "Save asset"; on success `onDone(id)`.
  - Category icon mapping (D12 §12, broad only): keyword match on category → `Icons.Outlined.*` from `material-icons-core` (`Build` tool, `Home` other, `Info` fallback) plus `ic_nfc_tag`; no per-brand icons.

- [ ] **Step 3: Wire** into `NoteNfcApp` (replace stubs). Build + tests: `:core` 109 + 6 = **115**; `:app` 46 + 4 = **50**.

- [ ] **Step 4: Commit.** `git commit -m "assets: list, identity plate detail, edit, archive"`

---

### Task 6: Scan, tag result sheets, write tag, share card, links

**Files:**
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/ui/scan/ScanScreen.kt`, `TagResultSheet.kt`, `WriteTagScreen.kt`, `TagWriteController.kt`, `ScanViewModels.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/ui/share/ShareCardScreen.kt` (+ `ShareFlow` used by `ShareActivity`)
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/ui/links/LinksScreen.kt`, `LinkDetailScreen.kt`, `LinkViewModels.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/DeleteLink.kt` (+ test)
- Test: `app/src/test/kotlin/com/loosecannon/notenfc/ui/scan/TagWriteControllerTest.kt` (pure logic with a fake `TagIo` seam — see below)

**Interfaces:**
- Consumes: `NfcReaderModeSession`, `TagWriter`, `OverwritePolicy`, `NdefCodec`, `ProvisionTag`, `BindTag`, `ResolveTag`, `OpenLink`, `SaveLink`, `LinkLauncher`, `LinkLaunchPolicy`, components.
- Produces:
  - `TagIo` interface (`inspect(tag): TagInspection?`, `write(tag, records, lock): WriteResult`, `lock(tag): Boolean`) with `RealTagIo` delegating to `TagWriter`, so the controller's decision logic is JVM-testable with a fake.
  - `TagWriteController(graph, io: TagIo, target: TagTarget, label: String?)`: `state: StateFlow<WriteState>` where `WriteState = Idle(message) | Confirm(reason) | Written(tagId, locked) | Verifying | Error(message)`; `onTag(tag)`, `confirmOverwrite()`, `keepIt()`, `setLock(Boolean)`, `abandonIfUnwritten()` — a faithful port of `WriteTagActivity` (busy flag, `awaitingVerify`, `confirmedOverwrite` arming, lock after verified read-back).
  - `ScanScreen`: "READY TO SCAN" (`tertiaryContainer` card with the `ic_contactless` glyph in a 96dp halo that breathes 3.2 s unless reduced motion), reader mode via `LifecycleResumeEffect`; on tag → `ResolveTag` → `LaunchLink` launches; else `onShowResult(format, key)` pushes `Route.TagResult`.
  - `TagResultSheet(format, key)`: re-resolves and renders one of the six D12 §11 sheets (G1 §1.4 copy); actions: Bind (opens a target picker sheet: assets, links, "New asset…"), Rewrite in format v1 / Write a new tag over it (→ `Route.WriteTag`), Open asset (→ `Route.AssetDetail`), Cancel.
  - `WriteTagScreen(route)`: renders `WriteState`; the lock switch shows the irreversible warning dialog (cancel = unchecked); "Overwrite?" is a `ModalBottomSheet` with Overwrite (filled) / Keep it (outlined); success shows the OK-container "Read back byte-identical" line and a Done button.
  - `ShareFlow(text, onFinished)`: `LinkLaunchPolicy.extractUri` → card (kind label e.g. "Joplin note", the URI in mono, `NeedsConfirmation` sheet for unknown schemes) with actions **Write to a new tag** (filled, default), **Keep as link** (outlined), Cancel; both save via `SaveLink`; writing pushes `WriteTagScreen` inside `ShareActivity` and `onFinished` after Done (returns to Joplin).
  - `LinksScreen` (list, label + kind badge-less, mono host), `LinkDetailScreen` (label, kind, URI mono, actions Open (`OpenLink` → `LinkLauncher`), Write tag, Delete (confirmation dialog; `DeleteLink` refuses while a tag is bound to it and explains)).

- [ ] **Step 1: Tests first.** `TagWriteControllerTest` with a fake `TagIo` and the Room-backed `FakeGraph`: `emptyTagIsWrittenWithoutAsking`, `foreignContentAsksFirstAndKeepItWritesNothing`, `confirmedOverwriteIsHonouredOnTheNextTapWithoutAskingAgain`, `readBackMismatchDoesNotCompleteTheRow`, `formatPathLocksOnlyAfterSecondTapVerifies`, `abandonDeletesOnlyAnUnwrittenRow`. `DeleteLinkTest`: refuses when bound, deletes when free.

- [ ] **Step 2: Implement** the controller (transcribe the 1B activity's `handle/write/verify/finishWrite/confirm` logic into suspend functions on the controller; `TagIo` calls run on `Dispatchers.IO`), then the screens per G1 §1.4. Reader-mode lifecycle: `val activity = LocalActivity.current` (or `LocalContext.current as Activity`), `LifecycleResumeEffect(Unit) { session.start(); onPauseOrDispose { session.stop() } }`.

- [ ] **Step 3: Wire** `ScanScreen`, `TagResultSheet`, `WriteTagScreen`, `LinksScreen`, `LinkDetailScreen` into `NoteNfcApp`; `ShareActivity` uses `ShareFlow`. Build + tests: `:core` 115 + 2 = **117**; `:app` 50 + 6 = **56**; `:app:compileDebugAndroidTestKotlin` green.

- [ ] **Step 4: Commit.** `git commit -m "scan, tag result sheets, write flow with the 1b rules, share card, links"`

---

### Task 7: Dashboard, backup, settings

**Files:**
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/ui/dashboard/DashboardScreen.kt`, `ui/backup/BackupScreen.kt`, `ui/settings/SettingsScreen.kt`, matching ViewModels
- Test: `app/src/test/kotlin/com/loosecannon/notenfc/ui/dashboard/DashboardViewModelTest.kt`, `app/src/test/kotlin/com/loosecannon/notenfc/ui/backup/BackupViewModelTest.kt`

**Interfaces:**
- Consumes: `AppPrefs`, `ExportBackup`, `ImportBackupReplace`, `SafBackupIO`, `AssetRepository.observeAll`, components.
- Produces:
  - `DashboardViewModel`: `state(assets: List<Asset>, needsBackup: Boolean, lastBackupAt: Long?)`.
  - `DashboardScreen`: `TopAppBar("noteNFC", actions: Settings)`; if `needsBackup` a **card** (independent meaning, allowed): eyebrow "BACKUP", "No backup yet", "Tags survive a phone change only if you have one.", button "Export now" (→ Backup); `SectionHeader("Current")` with one quiet row per active asset (`Icons.Outlined.CheckCircle` tinted `onSurfaceVariant`, name, `QuietLine("No schedule yet")`, chevron → detail); empty: "Add your first asset" (filled) + "Scan a tag" (outlined). Sections ATTENTION/UPCOMING/OUT OF SEASON are absent until Phase 3 (empty sections are omitted by rule).
  - `BackupViewModel`: `export(uri): Result<Int>` (bytes), `importReplace(uri): Result<ImportReport>`, `lastBackupAt`; `export` calls `prefs.markBackupExported(clock.nowMillis())` only after `SafBackupIO.write` returns.
  - `BackupScreen`: last backup line (or "Never"), `Button("Export backup")` → `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"))` with the `notenfc-backup-<stamp>.zip` name; `OutlinedButton("Import (replace everything)")` → `OpenDocument(arrayOf("application/zip","application/octet-stream"))` then an `AlertDialog` requiring the user to type `REPLACE` (R-9 typed confirmation) before `importReplace`; result snackbars.
  - `SettingsScreen`: "Appearance → Mode" radio group (System/Light/Dark) persisted to `AppPrefs` then `activity.recreate()`; "Theme" row shows "Apollo Service Binder" and the explanation "Dynamic colour arrives in a later release" (D12 §14: information stays visible when the control is unavailable); "About" with version name and a link to the GitHub repo.

- [ ] **Step 1: Tests first** — Dashboard: `needsBackupIsTrueUntilPrefsSayOtherwise`, `assetsListedActiveOnly`; Backup: `exportMarksPrefsOnlyOnSuccess` (fake `BackupIO` that throws → prefs untouched), `importReplaceReturnsReport`.

- [ ] **Step 2: Implement**, wire into `NoteNfcApp`, build + tests: `:app` 56 + 4 = **60**.

- [ ] **Step 3: Commit.** `git commit -m "dashboard with the backup nudge, backup screen, settings shell"`

---

### Task 8: Device smoke suite, evidence, final gate

**Files:**
- Create: `app/src/androidTest/kotlin/com/loosecannon/notenfc/ui/AppSmokeTest.kt`
- Create: `docs/design/phase-1c-evidence.md`; modify `docs/design/README.md` (index row), `README.md` (root: "What it does today" gains the app shell; debug backup paragraph updated)

**Interfaces:** none.

- [ ] **Step 1: Smoke tests** (`createAndroidComposeRule<MainActivity>()`): `dashboardShowsTheBackupNudgeOnAFreshInstall` (clear prefs in `@Before`), `bottomBarReachesScanAndShowsReadyToScan`, `assetCanBeCreatedFromTheDashboardAndOpens` (type a name, save, assert the plate shows it), `backupScreenRenders`, `malformedDeepLinkLandsOnDashboard` (launch with `Intent(VIEW, "notenfc://asset/nope")` via `ActivityScenario`, assert "Dashboard" content and no crash), plus `ShareActivitySmokeTest`: launching `ShareActivity` with `EXTRA_TEXT = "Title\nhttps://example.invalid/x"` shows "Web link" and the mono URI.

- [ ] **Step 2: Run on the attached phone.** `./gradlew :app:connectedDebugAndroidTest` (the phone must be unlocked; NFC not required). Paste the summary line and per-test results into the evidence doc §3. If the runner cannot install on the device, report BLOCKED with the exact error.

- [ ] **Step 3: Evidence doc** with the same skeleton as `phase-1b-evidence.md`: §1 M1 exit criteria → evidence (all four pending the owner's device rows: write tag on the phone → export → wipe via the debug screen (stands in for "phone Y") → import → scan resolves; share → write → scan → Joplin; nudge disappears after first export; 1A/1B rows re-run: write with confirmation, dispatch with app closed); §2 commits; §3 tests (JVM per module + instrumented results); §4 device checklist rows; §5 status; §6 rulings; §7 deferred to Phase 2 (measurement sheet, ledger events, serial field on the plate, hero reading); §8 what 1C changed for later phases.

- [ ] **Step 4: Final gate.** `./gradlew clean :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:compileDebugAndroidTestKotlin`; record test totals and APK sizes; `grep -rn "ui.interim\|TECH_DISCOVERED\|Color.Red\|Color.Green\|Color(0xFF" app/src/main/kotlin --include=*.kt | grep -v "ui/theme/"` → nothing (no raw colours outside the theme); three exported activities in the release manifest.

- [ ] **Step 5: Commit.** `git commit -m "phase 1c evidence, device smoke suite, readme"`

---

## Self-review

- **Spec coverage.** D7 §1C source areas: theme (T1), Home/assets+links (T5, T6, T7), asset create/edit with the plate (T5), Scan/Write with the D12 §11 states (T6), Links (T6), unknown/legacy sheets (T6), Backup (T7), Settings shell (T7), `notenfc://asset|link` deep links (T2, T4), share-sheet card (T6), "no backup yet" nudge (T7); migration report dropped per D13. G1 §7 build list: theme, three signature composables, badge, nav shell, asset detail with 1A/1B data, asset list, dashboard skeleton, six NFC sheets, interim deletion — all present. Exit criteria M1 (1)–(4) mapped in T8.
- **Placeholders.** Screen bodies in T5–T7 are specified by layout and state contract rather than full Compose listings; every state, action, colour role and copy string is named, and the reviewer checks against G1 §1. No "TBD".
- **Type consistency.** `Route` names (T4) match the `entry<...>` list and the pushes in T5–T7; `TagWriteController` state names (T6) match the screen; `AppPrefs` API (T2) matches T7's calls; `StatusColor`/`NoteNfcSemanticColors` fields (T1) match the contrast test and `StatusBadge` (T3).
