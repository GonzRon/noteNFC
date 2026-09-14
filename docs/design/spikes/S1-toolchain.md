# Spike S1 — Room 3.0.x + Compose + Navigation 3 on the noteNFC toolchain

Date: 2026-09-14
Risk register: `08-risk-register.md` §2 row S1 (and risks 4, 10, 12)
Design authority: `03-target-architecture.md` §5 (Persistence: Room 3.0)
Spike project: throwaway, outside the repo (scratchpad `s1-spike/`), one `:app` module, not committed.

## Verdict

**Room 3.0.3 confirmed as the Phase 1A starting line.**

Everything the spike was asked to prove passed on the first serious attempt. The five problems hit
were build-configuration and API-naming issues, not Room defects, and every one has a concrete,
permanent fix recorded below. Room 2.8.5 was never exercised because there was no deficiency to
fall back from.

Evidence, from a clean run with the build cache, configuration cache and Gradle daemon all cold:

```
$ ./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain \
      --no-build-cache --no-configuration-cache
BUILD SUCCESSFUL in 10s
45 actionable tasks: 45 executed
```

- `app/build/outputs/apk/debug/app-debug.apk` — 16.0 MB, assembled.
- 3 JVM unit tests, 0 failures, 0 errors, **no Robolectric, no emulator, no `android.content.Context`**.
- `app/schemas/com.loosecannon.s1spike.SpikeDb/1.json` exported by the Room Gradle plugin's
  `copyRoomSchemas` task.

## The configuration that works

### `gradle/libs.versions.toml` (copy-pasteable fragment)

```toml
[versions]
agp = "9.4.0"
kotlin = "2.4.20"
ksp = "2.3.12"
room3 = "3.0.3"
sqlite = "2.7.1"
composeBom = "2026.08.00"
activityCompose = "1.13.0"
navigation3 = "1.1.7"
coroutines = "1.10.2"
junit4 = "4.13.2"

[libraries]
room3-runtime          = { group = "androidx.room3",       name = "room3-runtime",       version.ref = "room3" }
room3-compiler         = { group = "androidx.room3",       name = "room3-compiler",      version.ref = "room3" }
room3-testing          = { group = "androidx.room3",       name = "room3-testing",       version.ref = "room3" }
sqlite-bundled         = { group = "androidx.sqlite",      name = "sqlite-bundled",      version.ref = "sqlite" }
sqlite-bundled-jvm     = { group = "androidx.sqlite",      name = "sqlite-bundled-jvm",  version.ref = "sqlite" }
compose-bom            = { group = "androidx.compose",     name = "compose-bom",         version.ref = "composeBom" }
compose-material3      = { group = "androidx.compose.material3", name = "material3" }
compose-ui             = { group = "androidx.compose.ui",  name = "ui" }
activity-compose       = { group = "androidx.activity",    name = "activity-compose",    version.ref = "activityCompose" }
navigation3-runtime    = { group = "androidx.navigation3", name = "navigation3-runtime", version.ref = "navigation3" }
navigation3-ui         = { group = "androidx.navigation3", name = "navigation3-ui",      version.ref = "navigation3" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
junit4                 = { group = "junit",                name = "junit",               version.ref = "junit4" }

[plugins]
android-application = { id = "com.android.application",             version.ref = "agp" }
ksp                 = { id = "com.google.devtools.ksp",             version.ref = "ksp" }
room3               = { id = "androidx.room3",                      version.ref = "room3" }
compose-compiler    = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

### Plugin blocks

Root `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room3) apply false
    alias(libs.plugins.compose.compiler) apply false
}
```

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
    alias(libs.plugins.compose.compiler)   // REQUIRED — AGP 9.4 does not bundle it
}

android {
    namespace = "…"
    compileSdk = 37
    defaultConfig { minSdk = 26; targetSdk = 36 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

room3 {                                    // extension is `room3`, not `room`
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.room3.runtime)
    ksp(libs.room3.compiler)
    implementation(libs.sqlite.bundled)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)

    testImplementation(libs.junit4)
    testImplementation(libs.sqlite.bundled.jvm)   // REQUIRED for JVM unit tests — see failure 5
    testImplementation(libs.room3.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

`settings.gradle.kts` must keep `google()` in **`pluginManagement.repositories`**: the
`androidx.room3` plugin marker is published to Google Maven only — it is not on the Gradle Plugin
Portal. The existing Task 3 `settings.gradle.kts` already lists `google()` first, so no change is
needed.

Unchanged from the repo scaffolding and all fine as-is: Gradle 9.7.1 wrapper, foojay resolver
1.0.0, `gradle-daemon-jvm.properties` (JetBrains JDK 25), `gradle.properties`
(`org.gradle.configuration-cache=true` — the configuration cache stores and reuses entries
correctly across AGP 9.4 + KSP 2.3.12 + the Room 3 plugin), `local.properties`.

## Failures encountered and the adjustment for each

### 1. AGP 9.4's built-in Kotlin does **not** bundle the Compose compiler

```
A problem occurred configuring project ':app'.
> Starting in Kotlin 2.0, the Compose Compiler Gradle plugin is required
  when compose is enabled. See https://d.android.com/r/studio-ui/compose-compiler
```

Configuration failed in 6 s with only `buildFeatures { compose = true }` and no Compose plugin.
**Adjustment:** apply `org.jetbrains.kotlin.plugin.compose` version `2.4.20` (pinned to the same
version as AGP's built-in Kotlin). The brief's "AGP 9 may wire the Compose compiler itself"
hypothesis is **false** for 9.4.0. Note the plugin id is versioned by the *Kotlin* version, so it
must be bumped in lockstep with `kotlin` in the catalog even though no
`org.jetbrains.kotlin.android` plugin is applied anywhere.

### 2. KSP no longer uses the `<kotlin>-<ksp>` version scheme

The brief expected something like `2.4.20-1.0.x`. No such version exists. KSP left the
Kotlin-coupled scheme after `2.2.21-2.0.5` and now publishes **independent** versions `2.3.0` …
`2.3.12` (latest on Maven Central at the time of the spike). These are KSP2-only and are not
pinned to a single Kotlin release.

**Adjustment:** use `com.google.devtools.ksp` **2.3.12**. This line is the right one for this
toolchain for two specific reasons in its own release notes:

- 2.3.10 — "Fix R-class resolution in KSP when **AGP 9 built-in Kotlin** is enabled" (#2857)
- 2.3.10 — "Sanitize ':' in internal-name module suffix so KSP works with **Kotlin 2.4.0** default
  module names" (#2964)

Anything older than 2.3.10 is expected to break on exactly this AGP-9-built-in-Kotlin setup.
2.3.11 additionally supports `org.gradle.isolated-projects`.

**Action for the catalog:** carry a `ksp` version separate from `kotlin`. Do not assume a Kotlin
bump requires a KSP bump or vice versa; check the KSP release notes for the Kotlin version it
targets.

### 3. Navigation 3 — `entry` is not a top-level import

```
e: MainActivity.kt:12:37 Unresolved reference 'entry'.
```

**Adjustment:** delete `import androidx.navigation3.runtime.entry`. In navigation3-runtime 1.1.7,
`entry` is a member of `EntryProviderScope`, so it resolves implicitly inside the `entryProvider {}`
lambda. Only `entryProvider` (and `NavKey`) need importing from `androidx.navigation3.runtime`;
`NavDisplay` comes from `androidx.navigation3.ui`.

The working screen, for reference:

```kotlin
data object Home : NavKey

@Composable
fun SpikeApp() {
    val backStack = remember { mutableStateListOf<NavKey>(Home) }
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },     // onBack is () -> Unit
        entryProvider = entryProvider { entry<Home> { Text("…") } },
    )
}
```

Nav 3 pulled in `androidx.navigationevent.compose` transitively; nothing had to be declared for it.
`androidx.activity:activity-compose` is **not** managed by the Compose BOM and had to be given an
explicit version (1.13.0).

### 4. Room 3 rejects `":memory:"` — there is a context-free `inMemoryDatabaseBuilder`

The brief's suggested JVM builder call fails at runtime:

```
java.lang.IllegalArgumentException: Cannot build a database with the special name ':memory:'.
If you are trying to create an in memory database, use Room.inMemoryDatabaseBuilder().
```

`Room.inMemoryDatabaseBuilder` is **not** Android-context-bound in Room 3. `androidx.room3.Room`
exposes four overloads; two of them take no `Context` at all:

```
<T> RoomDatabase.Builder<T> inMemoryDatabaseBuilder(Function0<T> factory)      // no Context
<T> RoomDatabase.Builder<T> databaseBuilder(String name, Function0<T> factory) // no Context
```

**Adjustment:** `Room.inMemoryDatabaseBuilder<SpikeDb>()` (reified, factory defaulted). The
brief's premise that only the context-bound form existed was wrong.

### 5. `sqlite-bundled`'s Android variant has no desktop natives

With the fix above the test got as far as opening the connection and then:

```
java.lang.UnsatisfiedLinkError: no sqliteJni in java.library.path: …
  at androidx.sqlite.driver.bundled.NativeLibraryLoader.loadLibrary(NativeLibraryLoader.android.kt:44)
  at androidx.sqlite.driver.bundled.BundledSQLiteDriver.open(BundledSQLiteDriver.jvmAndAndroid.kt:69)
```

**Root cause.** `androidx.sqlite:sqlite-bundled` is a KMP publication. In an Android module, Gradle
variant-aware resolution picks the **android** variant for the unit-test classpath too; that AAR
ships `jni/<abi>/libsqliteJni.so` built for Android/bionic and an android `NativeLibraryLoader`
that calls `System.loadLibrary`, which finds nothing on a glibc JVM.

**Adjustment — this is the single most important line in this report:**

```kotlin
testImplementation("androidx.sqlite:sqlite-bundled-jvm:2.7.1")
```

The `-jvm` publication carries the desktop natives as jar resources
(`natives/linux_x64/libsqliteJni.so`, plus linux_arm64, osx_arm64, windows_x64) and a JVM
`NativeLibraryLoader` that extracts and loads them. With it on the unit-test classpath the test
passes and the log confirms the JVM loader is the one that ran:

```
WARNING: java.lang.System::loadLibrary has been called by
  androidx.sqlite.driver.bundled.NativeLibraryLoader in an unnamed module
  (file:…/androidx.sqlite/sqlite-bundled-jvm/2.7.1/…/sqlite-bundled-jvm-2.7.1.jar)
```

Caveat worth knowing: both publications contain a class named
`androidx.sqlite.driver.bundled.NativeLibraryLoader`, so the unit-test runtime classpath has a
duplicate and the `-jvm` one has to win. It does here (AGP orders `testImplementation` ahead of
`implementation`), and the failure mode if it ever stops winning is loud and immediate
(`UnsatisfiedLinkError` at the first `build()`), never a silent wrong result. No mitigation beyond
keeping a DAO smoke test in the suite is needed.

## What ran on the JVM

`app/src/test/.../NoteDaoTest.kt` — three tests, all green with
`./gradlew :app:testDebugUnitTest`, no Robolectric on the classpath:

| Test | What it proves |
|---|---|
| `NoteDaoTest.insertAndReadBack` | `@Insert` + `@Query` suspend DAO round-trip against the real bundled SQLite engine, in memory, on the JVM. |
| `TransactionTest.daoTransactionAnnotationCommits` | `@Transaction`-annotated suspend DAO method commits both rows (risk 10 — the 3.0.2 `@Transaction` deadlock does not reproduce on 3.0.3). |
| `TransactionTest.writeTransactionRollsBackOnThrow` | `withWriteTransaction {}` rolls back cleanly when the block throws. |

The database opens as:

```kotlin
val db = Room.inMemoryDatabaseBuilder<SpikeDb>()
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
```

### API-name change that affects the architecture doc

`03-target-architecture.md` §5 says "one use case = one `withTransaction {}`". In Room 3 that
extension is split and renamed:

- `RoomDatabase.withWriteTransaction { … }`
- `RoomDatabase.withReadTransaction { … }`

(also `useWriterConnection` / `useReaderConnection` for the low-level `Transactor`). The design's
intent is unchanged — write it as `withWriteTransaction {}` in Phase 1A.

## Schema export

Works, via the Room Gradle plugin's `copyRoomSchemas` task and `room3 { schemaDirectory(...) }`.
The exported file is `app/schemas/<database FQCN>/<version>.json`:

```
app/schemas/com.loosecannon.s1spike.SpikeDb/1.json
```

with `formatVersion: 1`, `identityHash`, `createSql`, `fields`, `primaryKey` and the
`room_master_table` setup queries — i.e. the same shape Room 2 exports, so the migration-test
workflow the design assumes carries over unchanged.

## Compose compiler

Needed the explicit `org.jetbrains.kotlin.plugin.compose` plugin (failure 1). With it, a
`ComponentActivity` + `setContent { MaterialTheme { … } }` + `NavDisplay` screen compiles and packages
without any further configuration — no `composeOptions`, no `kotlinCompilerExtensionVersion`.

## Build wall time

Machine: ICESWORD, Fedora, OpenJDK 25.0.4.1 launching the wrapper; Gradle daemon toolchain
JetBrains JDK 25 per `gradle-daemon-jvm.properties`.

| Scenario | Wall time |
|---|---|
| First run: configure + resolve + download AGP 9.4, Kotlin 2.4.20, KSP 2.3.12, Room 3.0.3, Compose BOM 2026.08.00, Nav3 1.1.7 (≈ 400 MB into `~/.gradle/caches`) + `assembleDebug` | **58 s** |
| Cold rebuild, dependencies cached, `--no-build-cache --no-configuration-cache`, daemon stopped, `build/` and `.gradle/` deleted: `assembleDebug` + `testDebugUnitTest` | **11 s** |
| Warm incremental (config cache reused, one source file changed): `testDebugUnitTest` | **~2 s** |
| Whole spike end to end, 9 Gradle invocations including all 5 failures and fixes | **≈ 7 min** |

Toolchain churn (risk 4) is not expensive here. Nothing needed the 15-minute allowance.

## Secondary observations for Phase 1A

- **Configuration cache is clean.** Every run reported `Configuration cache entry stored` /
  `reused` with AGP 9.4 + KSP 2.3.12 + the Room 3 plugin. Keep
  `org.gradle.configuration-cache=true`.
- **JDK 25 native-access warning.** The bundled driver's `System.loadLibrary` triggers
  `WARNING: A restricted method in java.lang.System has been called … Restricted methods will be
  blocked in a future release unless native access is enabled`. Harmless today; add
  `testOptions.unitTests.all { it.jvmArgs("--enable-native-access=ALL-UNNAMED") }` in Phase 1A so
  a future JDK bump does not turn this into a hard failure.
- **Debug APK is 16.0 MB** with the bundled SQLite `.so` for all ABIs. Phase 1A should decide on
  ABI splits or `ndk { abiFilters }` for release; not a blocker.
- **`stripDebugDebugSymbols` warns** it cannot strip `libsqliteJni.so` and
  `libandroidx.graphics.path.so`. Cosmetic.
- **Newer versions exist** and were deliberately *not* taken, to keep the spike honest against the
  design: Compose BOM 2026.09.00, Navigation 3 1.2.0-rc01, activity-compose 1.14.0-alpha02,
  kotlinx-coroutines-test 1.11.0, Room 3.1.0-alpha01, sqlite 2.8.0-alpha01, AGP 9.5.0-alpha05.
  Phase 1A may take the stable ones; nothing in this spike depends on staying at these exact
  patch levels except `ksp >= 2.3.10` (failure 2).

## Fallback line: not exercised, not needed

Room 2.8.5 (`androidx.room:room-*` + the `androidx.room` plugin) was **not** tried, because Room 3
produced no deficiency to fall back from. Of the five problems, exactly zero were Room 3 defects:
one was an AGP/Compose plugin requirement, one a KSP versioning-scheme change, one a Navigation 3
import, one a corrected reading of Room 3's own builder API (the error message named the right
call), and one a KMP variant-resolution issue in `androidx.sqlite` that Room 2.8 would hit
identically the moment it used the bundled driver.

Risk register updates this spike supports: risk 4 (toolchain churn) and risk 10 (Room 3.0
early-adopter issues) can both drop to **Low** likelihood; risk 12's Navigation 3 concern is
unchanged — the API worked, but the `entry` import surprise shows the docs and the 1.1.x surface
still move.
