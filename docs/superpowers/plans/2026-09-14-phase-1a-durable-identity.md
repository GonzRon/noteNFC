# Phase 1A — Durable Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give noteNFC its clean production identity (`com.loosecannon.notenfc`), a Room 3 schema v1 holding assets, NFC tag bindings, and external links with stable UUID identity, a repository layer over pure domain models, and a Replace-mode backup/export/import — and prove that the app can forget its entire installation and reconstruct the same identity graph from a backup.

**Architecture:** `:core` (pure Kotlin) gains the domain models, repository ports, the backup codec (ZIP of `manifest.json` + `data.json`, kotlinx-serialization), and the two use cases (`ExportBackup`, `ImportBackupReplace`). `:app` gains the Room 3 database (schema v1, exported), entity↔model mappers, Room repository adapters, a hand-wired `AppGraph`, SAF-backed `BackupIO`, and a **debug-only** plain-Views activity to drive export/import on a device. Production opens the database with `AndroidSQLiteDriver`; JVM tests open it with `BundledSQLiteDriver` (no Robolectric).

**Tech Stack:** AGP 9.4.0 (built-in Kotlin 2.4.20), KSP `com.google.devtools.ksp` 2.3.12, Room 3.0.3 (`androidx.room3` plugin + `room3-runtime/compiler/testing`), `androidx.sqlite` 2.7.1 (`sqlite-bundled-jvm` for tests), kotlinx-serialization-json (plugin `org.jetbrains.kotlin.plugin.serialization` 2.4.20 applied in `:core` only), kotlinx-coroutines (+ `-test`), JUnit 5 in `:core`, JUnit 4 + coroutines-test in `:app` unit tests (the S1 spike's working recipe — see `docs/design/spikes/S1-toolchain.md` and the spike sources under `<scratchpad>/s1-spike/app/src/{main,test}`).

**Spec:** `docs/design/07-implementation-sequence.md` Phase 1A (as revised by `docs/design/13-compatibility-policy.md`), `docs/design/04-domain-data-model.md` §3–§4 and §15, `docs/design/03-target-architecture.md` §1, §3, §5, §12, §14, `docs/design/10-testing-strategy.md`.

## Global Constraints

- `applicationId` = `namespace` = **`com.loosecannon.notenfc`**; Kotlin package root `com.loosecannon.notenfc`; `versionCode = 1`, `versionName = "2.0"` (D13 §4).
- The three legacy activities keep working (share → write → scan) under the new package until Phase 1B deletes them; they are moved, not rewritten. Manifest intent filters unchanged.
- Primary keys are UUID v4 strings generated in `:core` via an injected `IdGenerator`; the database never generates ids (D4 conventions).
- Instants are epoch-millis `Long` columns with `_at` suffix; enums stored as their Kotlin names (D4 conventions).
- `:core` must not import `android.*`/`androidx.*`. `:core` tests are JUnit 5 on the JVM. `:app` unit tests use the bundled SQLite driver and **no Robolectric**.
- Room: `exportSchema = true` via the Room Gradle plugin `room3 { schemaDirectory("$projectDir/schemas") }`; the exported `1.json` is committed. Foreign keys as in D4 §3–§4: `nfc_tag.asset_id → asset ON DELETE SET NULL`, `nfc_tag.link_id → external_link ON DELETE SET NULL`, `external_link.asset_id → asset ON DELETE CASCADE`; indices as listed; `UNIQUE(payload_format, payload_key)` on `nfc_tag`.
- Backup format: ZIP with `manifest.json` and `data.json`; `manifest.formatVersion = 1`; a newer `formatVersion` is refused; a `data.json` SHA-256 mismatch is refused; IDs are preserved verbatim; no secrets (there are none yet). Replace import runs in one write transaction: delete everything, insert everything, or nothing.
- Room 3 API facts from S1: `Room.inMemoryDatabaseBuilder<T>()` for JVM tests (not `databaseBuilder(":memory:")`); transactions are `withWriteTransaction {}` / `withReadTransaction {}`; `setDriver(...)`, `setQueryCoroutineContext(...)`; JDK 25 unit-test JVMs warn on native access — add `jvmArgs("--enable-native-access=ALL-UNNAMED")` to `:app` unit tests.
- No NFC writing UX, no maintenance/event model, no Compose/Apollo UI in this phase. The only new screen is a debug-build-only plain-Views activity.
- Commit messages: casual, terse, human (repo `CLAUDE.md`); **never** `Co-Authored-By` or any AI attribution. Repo-local git identity is GonzRon. Do not push. Do not touch the sibling checkout `~/Documents/Projects/AndroidStudioProjects/noteNFC`.
- Cutover note (record in the evidence doc): the old `com.looseCannon.noteNFC` app must be uninstalled before real-device testing of the new package, because both match the `md5_short` NFC dispatch.

---

## File map

| Path | Responsibility |
|---|---|
| `app/build.gradle.kts` | new id/versions, KSP + Room 3 plugins, deps, optional release signing from `~/.config/notenfc/keystore.properties`, test jvmArgs |
| `gradle/libs.versions.toml` | new versions/aliases (Task 1 adds them all) |
| `settings.gradle.kts`, `build.gradle.kts` | plugin declarations |
| `app/src/main/AndroidManifest.xml` | fully-qualified activity names under the new package |
| `app/src/main/kotlin/com/loosecannon/notenfc/legacy/*.kt` | the three moved legacy activities (package `com.loosecannon.notenfc.legacy`) |
| `app/src/main/kotlin/com/loosecannon/notenfc/NoteNfcApp.kt` | `Application`; owns `AppGraph` |
| `app/src/main/kotlin/com/loosecannon/notenfc/di/AppGraph.kt` | hand-wired graph |
| `app/src/main/kotlin/com/loosecannon/notenfc/data/room/AppDatabase.kt` | `@Database(version = 1)` |
| `app/src/main/kotlin/com/loosecannon/notenfc/data/room/entities/*.kt` | `AssetEntity`, `NfcTagEntity`, `ExternalLinkEntity` |
| `app/src/main/kotlin/com/loosecannon/notenfc/data/room/dao/*.kt` | `AssetDao`, `NfcTagDao`, `ExternalLinkDao` |
| `app/src/main/kotlin/com/loosecannon/notenfc/data/room/Mappers.kt` | entity ↔ domain |
| `app/src/main/kotlin/com/loosecannon/notenfc/data/room/RoomRepositories.kt` | `RoomAssetRepository`, `RoomTagRepository`, `RoomLinkRepository`, `RoomUnitOfWork` |
| `app/src/main/kotlin/com/loosecannon/notenfc/backup/SafBackupIO.kt` | `BackupIO` over `ContentResolver` |
| `app/src/debug/AndroidManifest.xml`, `app/src/debug/kotlin/com/loosecannon/notenfc/debug/DebugBackupActivity.kt`, `app/src/debug/res/layout/activity_debug_backup.xml` | debug-only export/import/seed screen |
| `app/schemas/com.loosecannon.notenfc.data.room.AppDatabase/1.json` | exported schema (committed) |
| `app/src/test/kotlin/com/loosecannon/notenfc/data/room/*Test.kt` | JVM DAO/repository tests |
| `app/src/test/kotlin/com/loosecannon/notenfc/backup/RestoreProofTest.kt` | the Phase 1A proof |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/model/*.kt` | `Ids.kt`, `Asset.kt`, `TagBinding.kt`, `ExternalLink.kt` |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/ports/*.kt` | `Repositories.kt`, `UnitOfWork.kt`, `Clock.kt`, `IdGenerator.kt`, `BackupIO.kt` |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/backup/*.kt` | `BackupFormat.kt` (DTOs, manifest), `BackupCodec.kt`, `BackupErrors.kt` |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/*.kt` | `ExportBackup.kt`, `ImportBackupReplace.kt` |
| `core/src/test/kotlin/com/loosecannon/notenfc/core/backup/*Test.kt`, `.../usecase/*Test.kt` | codec and use-case tests with in-memory fakes |
| `docs/design/phase-1a-evidence.md` | exit-criteria evidence |
| `README.md` | build/signing/cutover notes |

---

### Task 1: Package identity, toolchain additions, signing

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts` (only if a plugin repo is missing), `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `.gitignore`, `README.md`
- Move: `app/src/main/java/com/looseCannon/noteNFC/{mainActivity.kt,NFCHandlerActivity.kt,LaunchNoteNFCLinkActivity.kt}` → `app/src/main/kotlin/com/loosecannon/notenfc/legacy/`
- Create outside the repo: `~/.config/notenfc/notenfc-release.jks`, `~/.config/notenfc/keystore.properties`

**Interfaces:**
- Produces: version-catalog aliases used by Tasks 2–4: `libs.plugins.ksp`, `libs.plugins.room3`, `libs.plugins.kotlin.serialization`, `libs.room3.runtime`, `libs.room3.compiler`, `libs.room3.testing`, `libs.sqlite.bundled`, `libs.sqlite.bundled.jvm`, `libs.kotlinx.serialization.json`, `libs.kotlinx.coroutines.core`, `libs.kotlinx.coroutines.android`, `libs.kotlinx.coroutines.test`, `libs.junit4`; a building `:app` under the new id.

- [ ] **Step 1: Version catalog.** Add to `gradle/libs.versions.toml` (keep existing entries):

```toml
[versions]
ksp = "2.3.12"
room3 = "3.0.3"
sqlite = "2.7.1"
kotlinxSerialization = "1.9.0"
kotlinxCoroutines = "1.10.2"
junit4 = "4.13.2"

[libraries]
room3-runtime = { group = "androidx.room3", name = "room3-runtime", version.ref = "room3" }
room3-compiler = { group = "androidx.room3", name = "room3-compiler", version.ref = "room3" }
room3-testing = { group = "androidx.room3", name = "room3-testing", version.ref = "room3" }
sqlite-bundled = { group = "androidx.sqlite", name = "sqlite-bundled", version.ref = "sqlite" }
sqlite-bundled-jvm = { group = "androidx.sqlite", name = "sqlite-bundled-jvm", version.ref = "sqlite" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
junit4 = { group = "junit", name = "junit", version.ref = "junit4" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room3 = { id = "androidx.room3", version.ref = "room3" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

If `kotlinx-serialization-json` 1.9.0 or coroutines 1.10.2 do not resolve, use the latest stable on Maven Central and record it in the report. Root `build.gradle.kts` adds `alias(libs.plugins.ksp) apply false`, `alias(libs.plugins.room3) apply false`, `alias(libs.plugins.kotlin.serialization) apply false`.

- [ ] **Step 2: Move the legacy activities.**

```bash
mkdir -p app/src/main/kotlin/com/loosecannon/notenfc/legacy
git mv app/src/main/java/com/looseCannon/noteNFC/mainActivity.kt app/src/main/kotlin/com/loosecannon/notenfc/legacy/MainActivity.kt
git mv app/src/main/java/com/looseCannon/noteNFC/NFCHandlerActivity.kt app/src/main/kotlin/com/loosecannon/notenfc/legacy/NFCHandlerActivity.kt
git mv app/src/main/java/com/looseCannon/noteNFC/LaunchNoteNFCLinkActivity.kt app/src/main/kotlin/com/loosecannon/notenfc/legacy/LaunchNoteNFCLinkActivity.kt
```

In each file change `package com.looseCannon.noteNFC` to `package com.loosecannon.notenfc.legacy`; `R` now resolves from `com.loosecannon.notenfc.R` — add `import com.loosecannon.notenfc.R` where `R.layout.*` is used. No other code changes. Delete the now-empty `app/src/main/java` tree.

- [ ] **Step 3: Manifest.** In `app/src/main/AndroidManifest.xml` set the activity names to `com.loosecannon.notenfc.legacy.MainActivity`, `com.loosecannon.notenfc.legacy.NFCHandlerActivity`, `com.loosecannon.notenfc.legacy.LaunchNoteNFCLinkActivity`; add `android:name="com.loosecannon.notenfc.NoteNfcApp"` on `<application>` (the class arrives in Task 2 — so in this task add it as an empty `class NoteNfcApp : android.app.Application()` at `app/src/main/kotlin/com/loosecannon/notenfc/NoteNfcApp.kt`). Everything else unchanged.

- [ ] **Step 4: `app/build.gradle.kts`** — replace with:

```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

val keystoreProps = Properties().apply {
    val f = file(System.getProperty("user.home") + "/.config/notenfc/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.loosecannon.notenfc"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.loosecannon.notenfc"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "2.0"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
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
    testOptions {
        unitTests.all { it.jvmArgs("--enable-native-access=ALL-UNNAMED") }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.room3.runtime)
    ksp(libs.room3.compiler)
    implementation(libs.sqlite.bundled)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room3.testing)
    testImplementation(libs.sqlite.bundled.jvm)
}
```

`libs.sqlite.bundled` is added now (needed by the Room 3 driver API even when production uses `AndroidSQLiteDriver`; keep it — the S1 spike showed it works; ABI filtering is a Phase 7 concern).

- [ ] **Step 5: Signing keystore (outside the repo).**

```bash
mkdir -p ~/.config/notenfc && chmod 700 ~/.config/notenfc
PW=$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 28)
keytool -genkeypair -v -keystore ~/.config/notenfc/notenfc-release.jks -alias notenfc -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=noteNFC, O=GonzRon" -storepass "$PW" -keypass "$PW"
printf 'storeFile=%s/.config/notenfc/notenfc-release.jks\nstorePassword=%s\nkeyAlias=notenfc\nkeyPassword=%s\n' "$HOME" "$PW" "$PW" > ~/.config/notenfc/keystore.properties
chmod 600 ~/.config/notenfc/keystore.properties ~/.config/notenfc/notenfc-release.jks
keytool -list -v -keystore ~/.config/notenfc/notenfc-release.jks -storepass "$PW" | grep -E 'Alias|SHA256'
```

Use `keytool` from `~/.gradle/jdks/*/bin/keytool` or the system JDK. Record the SHA-256 fingerprint in the README. Never print the password into the report or commit it. `.gitignore`: add `keystore.properties` and `*.jks` (belt and braces).

- [ ] **Step 6: README.** Add a short "Building" section: clone, `./gradlew :app:assembleDebug`; "Signing": release builds pick up `~/.config/notenfc/keystore.properties` if present (fingerprint listed; keep the keystore backed up outside the repo); "Cutover": uninstall the old `com.looseCannon.noteNFC` app before testing the new package on a device (both respond to legacy NFC tags).

- [ ] **Step 7: Build and verify.**

```bash
./gradlew :core:test :app:assembleDebug :app:assembleRelease --console=plain -q
~/Android/Sdk/build-tools/36.0.0/aapt dump badging app/build/outputs/apk/debug/app-debug.apk | grep -E "^package:"
~/Android/Sdk/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk | grep -E 'Signer #1 certificate (DN|SHA-256)'
git status --short
```

Expected: package `com.loosecannon.notenfc` versionCode `1` versionName `2.0`; release APK signed by `CN=noteNFC, O=GonzRon`; clean tree apart from intended changes. If `assembleRelease` fails only because of signing, report it — do not weaken the config.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "new identity: com.loosecannon.notenfc, v2.0, room3/ksp/serialization plugins, release signing from ~/.config"
```

---

### Task 2: Domain models, ports, Room 3 schema v1, repositories, AppGraph

**Files:**
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/model/Ids.kt`, `Asset.kt`, `TagBinding.kt`, `ExternalLink.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/ports/Repositories.kt`, `UnitOfWork.kt`, `Clock.kt`, `IdGenerator.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/data/room/AppDatabase.kt`, `entities/AssetEntity.kt`, `entities/NfcTagEntity.kt`, `entities/ExternalLinkEntity.kt`, `dao/AssetDao.kt`, `dao/NfcTagDao.kt`, `dao/ExternalLinkDao.kt`, `Mappers.kt`, `RoomRepositories.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/di/AppGraph.kt`; modify `NoteNfcApp.kt`
- Create: `app/schemas/com.loosecannon.notenfc.data.room.AppDatabase/1.json` (generated by the build; commit it)
- Test: `app/src/test/kotlin/com/loosecannon/notenfc/data/room/TestDb.kt`, `AssetDaoTest.kt`, `NfcTagDaoTest.kt`, `ExternalLinkDaoTest.kt`, `RoomRepositoriesTest.kt`

**Interfaces:**
- Produces (used verbatim by Tasks 3–4):

```kotlin
// core/model/Ids.kt
package com.loosecannon.notenfc.core.model
@JvmInline value class AssetId(val value: String)
@JvmInline value class TagId(val value: String)
@JvmInline value class LinkId(val value: String)

// core/model/Asset.kt
enum class AssetStatus { ACTIVE, ARCHIVED, RETIRED }
data class Asset(
    val id: AssetId, val name: String, val description: String = "", val category: String = "",
    val notes: String = "", val status: AssetStatus = AssetStatus.ACTIVE,
    val createdAt: Long, val updatedAt: Long,
)

// core/model/TagBinding.kt
enum class PayloadFormat { LEGACY_MD5, V1 }
enum class TagStatus { ACTIVE, UNBOUND, LOST, RETIRED }
sealed interface TagTarget {
    data class AssetTarget(val assetId: AssetId) : TagTarget
    data class LinkTarget(val linkId: LinkId) : TagTarget
    data object None : TagTarget
}
data class TagBinding(
    val id: TagId, val payloadFormat: PayloadFormat, val payloadKey: String,
    val target: TagTarget = TagTarget.None, val status: TagStatus = TagStatus.ACTIVE,
    val label: String? = null, val physicalUid: String? = null,
    val writtenAt: Long? = null, val lastScannedAt: Long? = null,
    val createdAt: Long, val updatedAt: Long,
)

// core/model/ExternalLink.kt
enum class LinkKind { JOPLIN, OBSIDIAN, LOGSEQ, WEB, OTHER }
data class ExternalLink(
    val id: LinkId, val assetId: AssetId? = null, val kind: LinkKind, val label: String, val uri: String,
    val createdAt: Long, val lastOpenedAt: Long? = null, val updatedAt: Long,
)

// core/ports/Repositories.kt
interface AssetRepository {
    suspend fun upsert(asset: Asset)
    suspend fun get(id: AssetId): Asset?
    suspend fun all(): List<Asset>
    suspend fun delete(id: AssetId)
    suspend fun deleteAll()
}
interface TagRepository {
    suspend fun upsert(tag: TagBinding)
    suspend fun get(id: TagId): TagBinding?
    suspend fun findByPayload(format: PayloadFormat, key: String): TagBinding?
    suspend fun forAsset(assetId: AssetId): List<TagBinding>
    suspend fun forLink(linkId: LinkId): List<TagBinding>
    suspend fun all(): List<TagBinding>
    suspend fun delete(id: TagId)
    suspend fun deleteAll()
}
interface LinkRepository {
    suspend fun upsert(link: ExternalLink)
    suspend fun get(id: LinkId): ExternalLink?
    suspend fun forAsset(assetId: AssetId): List<ExternalLink>
    suspend fun standalone(): List<ExternalLink>
    suspend fun all(): List<ExternalLink>
    suspend fun delete(id: LinkId)
    suspend fun deleteAll()
}
// core/ports/UnitOfWork.kt
interface UnitOfWork { suspend fun <T> write(block: suspend () -> T): T }
// core/ports/Clock.kt
fun interface Clock { fun nowMillis(): Long }
// core/ports/IdGenerator.kt
fun interface IdGenerator { fun newId(): String }
object UuidGenerator : IdGenerator { override fun newId(): String = java.util.UUID.randomUUID().toString() }
```

- [ ] **Step 1: Write the `:core` model and port files** exactly as above (one file each; `Ids.kt` may also hold nothing else). `:core` needs `implementation(libs.kotlinx.coroutines.core)` for `suspend` in interfaces — add it to `core/build.gradle.kts`.

- [ ] **Step 2: Room entities.** Table names and columns follow D4 §3–§4 (this phase's subset):

```kotlin
// entities/AssetEntity.kt
@Entity(tableName = "asset", indices = [Index("status"), Index("name")])
data class AssetEntity(
    @PrimaryKey val id: String,
    val name: String, val description: String, val category: String, val notes: String,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

// entities/ExternalLinkEntity.kt
@Entity(
    tableName = "external_link",
    foreignKeys = [ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["asset_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("asset_id")],
)
data class ExternalLinkEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "asset_id") val assetId: String?,
    val kind: String, val label: String, val uri: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

// entities/NfcTagEntity.kt
@Entity(
    tableName = "nfc_tag",
    foreignKeys = [
        ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["asset_id"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = ExternalLinkEntity::class, parentColumns = ["id"], childColumns = ["link_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["payload_format", "payload_key"], unique = true), Index("asset_id"), Index("link_id")],
)
data class NfcTagEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "payload_format") val payloadFormat: String,
    @ColumnInfo(name = "payload_key") val payloadKey: String,
    @ColumnInfo(name = "asset_id") val assetId: String?,
    @ColumnInfo(name = "link_id") val linkId: String?,
    val status: String, val label: String?,
    @ColumnInfo(name = "physical_uid") val physicalUid: String?,
    @ColumnInfo(name = "written_at") val writtenAt: Long?,
    @ColumnInfo(name = "last_scanned_at") val lastScannedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
```

The "at most one of asset_id/link_id" rule is enforced in `RoomTagRepository.upsert` (throw `IllegalArgumentException`), since Room has no CHECK annotation.

- [ ] **Step 3: DAOs** (suspend functions; `@Upsert` where available in Room 3, otherwise `@Insert(onConflict = REPLACE)`):

```kotlin
@Dao interface AssetDao {
    @Upsert suspend fun upsert(e: AssetEntity)
    @Query("SELECT * FROM asset WHERE id = :id") suspend fun byId(id: String): AssetEntity?
    @Query("SELECT * FROM asset ORDER BY name COLLATE NOCASE") suspend fun all(): List<AssetEntity>
    @Query("DELETE FROM asset WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM asset") suspend fun deleteAll()
}
@Dao interface NfcTagDao {
    @Upsert suspend fun upsert(e: NfcTagEntity)
    @Query("SELECT * FROM nfc_tag WHERE id = :id") suspend fun byId(id: String): NfcTagEntity?
    @Query("SELECT * FROM nfc_tag WHERE payload_format = :format AND payload_key = :key") suspend fun byPayload(format: String, key: String): NfcTagEntity?
    @Query("SELECT * FROM nfc_tag WHERE asset_id = :assetId") suspend fun forAsset(assetId: String): List<NfcTagEntity>
    @Query("SELECT * FROM nfc_tag WHERE link_id = :linkId") suspend fun forLink(linkId: String): List<NfcTagEntity>
    @Query("SELECT * FROM nfc_tag ORDER BY created_at") suspend fun all(): List<NfcTagEntity>
    @Query("DELETE FROM nfc_tag WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM nfc_tag") suspend fun deleteAll()
}
@Dao interface ExternalLinkDao {
    @Upsert suspend fun upsert(e: ExternalLinkEntity)
    @Query("SELECT * FROM external_link WHERE id = :id") suspend fun byId(id: String): ExternalLinkEntity?
    @Query("SELECT * FROM external_link WHERE asset_id = :assetId ORDER BY label COLLATE NOCASE") suspend fun forAsset(assetId: String): List<ExternalLinkEntity>
    @Query("SELECT * FROM external_link WHERE asset_id IS NULL ORDER BY label COLLATE NOCASE") suspend fun standalone(): List<ExternalLinkEntity>
    @Query("SELECT * FROM external_link ORDER BY created_at") suspend fun all(): List<ExternalLinkEntity>
    @Query("DELETE FROM external_link WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM external_link") suspend fun deleteAll()
}
@Database(entities = [AssetEntity::class, NfcTagEntity::class, ExternalLinkEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun nfcTagDao(): NfcTagDao
    abstract fun externalLinkDao(): ExternalLinkDao
}
```

- [ ] **Step 4: Mappers and repositories.** `Mappers.kt`: `AssetEntity.toDomain()`, `Asset.toEntity()`, same for tags (`TagTarget` ↔ the nullable pair) and links; enum names via `valueOf`. `RoomRepositories.kt`: the three repositories delegating to DAOs, and

```kotlin
class RoomUnitOfWork(private val db: AppDatabase) : UnitOfWork {
    override suspend fun <T> write(block: suspend () -> T): T = db.withWriteTransaction { block() }
}
```

- [ ] **Step 5: AppGraph.**

```kotlin
class AppGraph(context: Context) {
    val db: AppDatabase = Room.databaseBuilder<AppDatabase>(context, context.getDatabasePath("notenfc.db").absolutePath)
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    val clock: Clock = Clock { System.currentTimeMillis() }
    val ids: IdGenerator = UuidGenerator
    val uow: UnitOfWork = RoomUnitOfWork(db)
    val assets: AssetRepository = RoomAssetRepository(db.assetDao())
    val tags: TagRepository = RoomTagRepository(db.nfcTagDao())
    val links: LinkRepository = RoomLinkRepository(db.externalLinkDao())
}
class NoteNfcApp : Application() { lateinit var graph: AppGraph; override fun onCreate() { super.onCreate(); graph = AppGraph(this) } }
```

Use the exact Room 3 builder/driver API the S1 spike used (`Data.kt` in the spike shows the production shape; `NoteDaoTest.kt` the test shape); adjust names if the compiler disagrees and record it.

- [ ] **Step 6: Tests (write before Step 4's bodies compile, then make them pass).** `TestDb.kt`:

```kotlin
fun inMemoryDb(): AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>()
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.Default)
    .build()
```

`AssetDaoTest`: upsert→byId round-trip; `all()` sorted case-insensitively; delete. `NfcTagDaoTest`: unique `(payload_format, payload_key)` — second upsert with a *different id* but same pair fails with an SQLite constraint exception; `byPayload`; deleting the asset sets `asset_id` to NULL (`forAsset` empty, row still present); deleting a link sets `link_id` NULL. `ExternalLinkDaoTest`: `standalone()` vs `forAsset()`; deleting the asset cascades its links. `RoomRepositoriesTest`: domain round-trips through mappers (`TagTarget.AssetTarget` ↔ columns, enums), `upsert` of a tag with both targets throws, `RoomUnitOfWork.write` rolls back on exception (insert then throw → row absent). Each test opens a fresh `inMemoryDb()` and closes it.

Run: `./gradlew :app:testDebugUnitTest --console=plain -q` → all pass; confirm `app/schemas/com.loosecannon.notenfc.data.room.AppDatabase/1.json` exists and contains `nfc_tag` with the unique index.

- [ ] **Step 7: Commit**

```bash
git add core app/src app/schemas
git commit -m "room 3 schema v1: asset, nfc_tag, external_link + domain models, repos, app graph"
```

---

### Task 3: Backup format, codec, and the two use cases (`:core`)

**Files:**
- Modify: `core/build.gradle.kts` (apply `alias(libs.plugins.kotlin.serialization)`; add `implementation(libs.kotlinx.serialization.json)`)
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/backup/BackupFormat.kt`, `BackupCodec.kt`, `BackupErrors.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/ports/BackupIO.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/ExportBackup.kt`, `ImportBackupReplace.kt`
- Test: `core/src/test/kotlin/com/loosecannon/notenfc/core/backup/BackupCodecTest.kt`, `core/src/test/kotlin/com/loosecannon/notenfc/core/usecase/BackupUseCasesTest.kt`, `core/src/test/kotlin/com/loosecannon/notenfc/core/testing/InMemoryRepositories.kt`

**Interfaces:**

```kotlin
// backup/BackupFormat.kt
@Serializable data class BackupManifest(
    val formatVersion: Int, val appVersion: String, val schemaVersion: Int,
    val createdAt: Long, val counts: Map<String, Int>, val dataSha256: String,
)
@Serializable data class AssetDto(val id: String, val name: String, val description: String, val category: String, val notes: String, val status: String, val createdAt: Long, val updatedAt: Long)
@Serializable data class NfcTagDto(val id: String, val payloadFormat: String, val payloadKey: String, val assetId: String?, val linkId: String?, val status: String, val label: String?, val physicalUid: String?, val writtenAt: Long?, val lastScannedAt: Long?, val createdAt: Long, val updatedAt: Long)
@Serializable data class ExternalLinkDto(val id: String, val assetId: String?, val kind: String, val label: String, val uri: String, val createdAt: Long, val lastOpenedAt: Long?, val updatedAt: Long)
@Serializable data class BackupData(val assets: List<AssetDto>, val nfcTags: List<NfcTagDto>, val externalLinks: List<ExternalLinkDto>)
data class Backup(val manifest: BackupManifest, val data: BackupData)
// mapping helpers: Asset.toDto()/AssetDto.toDomain() etc. (throw BackupMalformed on unknown enum names)

// backup/BackupErrors.kt
sealed class BackupException(message: String) : Exception(message)
class BackupNewerFormat(val found: Int, val supported: Int) : BackupException("backup format $found is newer than supported $supported")
class BackupCorrupt(reason: String) : BackupException(reason)      // missing entries, hash mismatch, unparsable JSON

// backup/BackupCodec.kt
object BackupCodec {
    const val FORMAT_VERSION = 1
    const val MANIFEST_ENTRY = "manifest.json"
    const val DATA_ENTRY = "data.json"
    fun encode(data: BackupData, appVersion: String, schemaVersion: Int, createdAt: Long): ByteArray   // ZIP: manifest.json, data.json (pretty JSON, sorted by id), manifest.dataSha256 = sha256(data.json bytes) hex
    fun decode(bytes: ByteArray): Backup                                                              // throws BackupNewerFormat / BackupCorrupt
}

// ports/BackupIO.kt
interface BackupIO { suspend fun write(bytes: ByteArray); suspend fun read(): ByteArray }   // one destination/source per instance (a SAF URI in :app)

// usecase/ExportBackup.kt
class ExportBackup(private val assets: AssetRepository, private val tags: TagRepository, private val links: LinkRepository, private val clock: Clock, private val appVersion: String, private val schemaVersion: Int) {
    suspend fun run(): ByteArray
}
// usecase/ImportBackupReplace.kt
data class ImportReport(val assets: Int, val tags: Int, val links: Int, val formatVersion: Int)
class ImportBackupReplace(private val assets: AssetRepository, private val tags: TagRepository, private val links: LinkRepository, private val uow: UnitOfWork) {
    suspend fun run(bytes: ByteArray): ImportReport      // decode outside the transaction; inside uow.write: deleteAll (tags, links, assets in that order), then insert assets, links, tags (that order, for FKs)
}
```

- [ ] **Step 1: Failing tests first.** `InMemoryRepositories.kt` implements the three repository ports with `LinkedHashMap`s and a `FakeUnitOfWork` that snapshots the maps before `block()` and restores them if it throws (so rollback is testable). `BackupCodecTest`: round-trip identity for a 3-asset / 4-tag / 3-link fixture (including a standalone link, an UNBOUND tag, a LEGACY_MD5 tag, nulls); IDs verbatim; the ZIP contains exactly `manifest.json` and `data.json`; manifest counts match; tampering one byte of `data.json` inside the ZIP → `BackupCorrupt`; manifest with `formatVersion = 2` → `BackupNewerFormat(2, 1)`; missing `data.json` → `BackupCorrupt`; unknown enum name in a DTO → `BackupCorrupt`; `decode(encode(x)) == x` as a property over a small generated set (plain loop, 50 random fixtures via `kotlin.random.Random(42)`). `BackupUseCasesTest`: export from populated fakes then import into empty fakes → equal contents and IDs; import into *non-empty* fakes replaces everything (pre-existing rows gone); import whose insert throws (a fake repository rigged to throw on the third tag) leaves the previous contents intact (rollback via `FakeUnitOfWork`); import of a newer format changes nothing.

- [ ] **Step 2: Run** `./gradlew :core:test --console=plain -q` → compilation failure (expected RED).

- [ ] **Step 3: Implement** the files exactly per the Interfaces block. JSON via `Json { prettyPrint = true; encodeDefaults = true }`; SHA-256 via `java.security.MessageDigest`; ZIP via `java.util.zip.ZipOutputStream`/`ZipInputStream`; sort lists by `id` before encoding so output is deterministic.

- [ ] **Step 4: Run** `./gradlew :core:test --console=plain -q` → all green (expect ≥ 15 new tests; record the count from the XML). `grep -rn "import android" core/src` → nothing.

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "backup format v1: zip of manifest+data, codec, export and replace-import use cases"
```

---

### Task 4: SAF backup IO, debug backup screen, the restore proof, evidence

**Files:**
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/backup/SafBackupIO.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/di/AppGraph.kt` (add `exportBackup`, `importBackupReplace`, `appVersion` from `BuildConfig`; enable `buildFeatures { buildConfig = true }` in `app/build.gradle.kts`)
- Create: `app/src/debug/AndroidManifest.xml`, `app/src/debug/kotlin/com/loosecannon/notenfc/debug/DebugBackupActivity.kt`, `app/src/debug/res/layout/activity_debug_backup.xml`
- Test: `app/src/test/kotlin/com/loosecannon/notenfc/backup/RestoreProofTest.kt`
- Create: `docs/design/phase-1a-evidence.md`; modify `README.md` (debug screen note)

- [ ] **Step 1: `SafBackupIO`.**

```kotlin
class SafBackupIO(private val resolver: ContentResolver, private val uri: Uri) : BackupIO {
    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("cannot open $uri for writing")
    }
    override suspend fun read(): ByteArray = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("cannot open $uri for reading")
    }
}
```

- [ ] **Step 2: Debug screen (debug build only).** `app/src/debug/AndroidManifest.xml` declares `com.loosecannon.notenfc.debug.DebugBackupActivity` with `android:exported="true"`, `android:label="noteNFC Backup (debug)"`, and a `MAIN`/`LAUNCHER` intent filter. The activity (plain `android.app.Activity`, `LinearLayout` with four buttons and a `TextView`): **Seed sample** (inserts 2 assets, 3 tags — one bound to an asset, one bound to a standalone link, one UNBOUND — and 2 links via the repositories inside `uow.write`), **Export** (`ACTION_CREATE_DOCUMENT`, MIME `application/zip`, suggested name `notenfc-backup-<yyyyMMdd-HHmm>.zip`, then `ExportBackup.run()` → `SafBackupIO.write`), **Import (replace)** (`ACTION_OPEN_DOCUMENT` → `SafBackupIO.read` → `ImportBackupReplace.run` → show the `ImportReport`), **Wipe** (`uow.write { deleteAll ×3 }`), and a **Counts** line refreshed after every action (`assets/tags/links`). Use `lifecycleScope`-free plain coroutines: `MainScope()` cancelled in `onDestroy`. Errors → `Toast` + counts line.

- [ ] **Step 3: The proof test** (`RestoreProofTest`, JVM, bundled driver):

```kotlin
@Test fun forgetEverythingAndRebuildTheSameGraph() = runTest {
    val db1 = inMemoryDb(); val g1 = graphOver(db1)             // repos + uow + export/import over db1
    // create: 2 assets, 1 standalone link, 1 asset link, 3 tags (asset-bound, link-bound, unbound, one LEGACY_MD5)
    val bytes = g1.export.run()
    val before = snapshot(g1)                                     // sorted lists of domain objects
    db1.close()
    val db2 = inMemoryDb(); val g2 = graphOver(db2)               // "reinstall-equivalent": brand-new empty database
    val report = g2.import.run(bytes)
    val after = snapshot(g2)
    assertEquals(before, after)                                   // same ids, same targets, same links
    assertEquals(ImportReport(2, 3, 2, 1), report)
    // relationships survive through the FK columns, not just ids:
    assertEquals(1, g2.tags.forAsset(before.assets[0].id).size)
    assertEquals(1, g2.tags.forLink(before.links.first { it.assetId == null }.id).size)
    db2.close()
}
@Test fun replaceImportIsAtomic() = runTest { /* import corrupt bytes into a populated db → BackupCorrupt thrown, contents unchanged; import a valid backup over different data → only the backup's rows remain */ }
```

`graphOver(db)` builds `RoomAssetRepository` etc. plus `ExportBackup(..., appVersion = "test", schemaVersion = 1)` and `ImportBackupReplace(...)`.

- [ ] **Step 4: Run everything.** `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain -q` → green. Install on a device is optional (no device may be attached); if one is, `adb install -r app/build/outputs/apk/debug/app-debug.apk`, run the debug screen: Seed → Export → Wipe → Import → counts return; record it.

- [ ] **Step 5: Evidence.** `docs/design/phase-1a-evidence.md`: exit-criteria table (package identity with `aapt` output; schema v1 exported + committed; repositories + DAO tests count; codec/use-case test count; the restore proof test name and result; release signing fingerprint; cutover note: uninstall `com.looseCannon.noteNFC` before device testing; on-device run recorded or marked pending), deviations from the plan, "Ready for Phase 1B: yes/no". Add a "Debug backup screen" line to the README.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "saf backup io, debug backup screen, restore proof test, phase 1a evidence"
```
