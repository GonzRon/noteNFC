# Product-split target architecture — ServiceTag / noteNFC / nfc-tag-core

The shape the estate is being moved *to*. Written for an engineer who has never opened this
repository: every module, package, file and command named below either exists at the pre-split
checkpoint `ac523d7` (cited to the archaeology, which cites the code) or is marked **NEW**.

- **Evidence base:** `docs/architecture/product-split-archaeology.md` (cited as "arch §n"). Nothing
  here re-derives a fact that document already established.
- **Decided, not re-opened:** controller rulings **R1–R9** in `.superpowers/split/ledger.md`,
  plus the R4 clarification that the library's reader-mode session **never** sets
  `FLAG_READER_SKIP_NDEF_CHECK` (device-proven at `e2cf1d0`; arch §5.5, §9 D2).
- **Runbook:** `docs/architecture/product-split-migration.md` is the ordered how; this document is
  the what.
- **Claim tags** follow the archaeology: **[code]**, **[JVM-proven]**, **[device-observed]**,
  **[platform-doc]**, **[unobserved]**. Any platform behaviour not yet seen on hardware is marked
  **to observe on-device** and carries the archaeology's question number (arch §8.1).
- **Proposals.** Decisions this design had to make that R1–R9 do not settle are marked
  **[P*n* — proposal]** and collected in §11 for ratification. Nothing marked so should be treated
  as ratified.
- **Hygiene.** No home paths beyond `~`, no device serials, no phone model or codename, no phone
  folder names, no key material, no fingerprints reproduced.

---

## 1. The picture

```text
                      ┌───────────────────────────────┐
                      │        nfc-tag-core           │   one repo, two Gradle modules
                      │  nfc-tag-core       (pure JVM)│   NDEF envelope + bytes + rules
                      │  nfc-tag-core-android         │   android.nfc adapter, minSdk 26
                      └───────────────┬───────────────┘
                     git submodule pinned to a tag, consumed by includeBuild
              ┌───────────────────────┴───────────────────────┐
              │                                              │
   ┌──────────▼──────────┐                        ┌───────────▼─────────┐
   │      noteNFC        │                        │      ServiceTag     │
   │ com.loosecannon.    │                        │ com.loosecannon.    │
   │        notenfc      │                        │        servicetag   │
   │ share → write → tap │                        │ assets · journal ·  │
   │ prefs, no database  │                        │ profiles · schedules│
   │ no Compose, no Room │                        │ attachments · backup│
   └─────────────────────┘                        └─────────────────────┘
```

Both apps hold the library at the *same* pinned commit and neither depends on the other. The
library knows about NDEF records, tag hardware and a write protocol. It does not know what a tag
*means* to anybody.

**noteNFC is** the narrow share→write→tap utility that existed in 2024 (arch §2.4): receive a
shared note link, key it, remember the key→link pair in `SharedPreferences`, write one identifier
to a tag, and on a later tap open that note or web page. Reconstructed from `c84b881` with true
ancestry (R6), it gains only modern NFC safety from the library — reader mode instead of a mutable
`PendingIntent`, read-before-write consent, capacity check, read-back verification — plus a copied
safe `ACTION_VIEW` launch policy and honest wording for tags that belong to another app.
**noteNFC is not** an asset tracker: it has no database, no journal, no measurements, no profiles,
no schedules, no reminders, no attachments, no backup format, no Compose, no navigation framework
and no design system (arch §2.5). It keeps the only legacy obligation in the estate: reading the
2024 `md5_short` record (R3).

**ServiceTag is** the physical-asset service product this repository's master branch already is at
`ac523d7` (arch §3, §4): five-table-deep domain across eleven Room tables, an event journal with
typed measurements and consumable usage, measurement definitions and event profiles, the full
physical-asset model, attachments in a SAF-tree managed store, a two-archive backup set at data
format 5 / artifacts format 1, a single-activity Compose/Nav3 shell on the Apollo Service Binder
theme. **ServiceTag is not** a note utility: it drops the 2024 `md5_short` decoder, filter and
"Legacy" sheet entirely (R3 — it has zero legacy bindings to lose, arch §7.5), and it keeps no
ambient claim on any identifier it does not own.

**nfc-tag-core is** the mechanism both need and neither should own twice: an NDEF envelope codec
whose identity is a parameter, the single `android.nfc`↔bytes bridge, the reader-mode lifecycle,
the safe writer (read-first / capacity / read-back / lock-last / `NdefFormatable` fallback) and the
write protocol as a state machine. **nfc-tag-core is not** allowed to know the words in §4.4.

---

## 2. Repository identities

| | **ServiceTag** | **noteNFC** | **nfc-tag-core** |
|---|---|---|---|
| Remote | `https://github.com/GonzRon/ServiceTag.git` | `https://github.com/GonzRon/noteNFC.git` | `https://github.com/GonzRon/nfc-tag-core.git` |
| Default branch | `master` (unchanged) | `master` | `main` **[P1 — proposal]**: a new repo has no reason to inherit `master`; the two app repos keep `master` because their history does |
| How it comes into being | **Rename** of the current `GonzRon/noteNFC` repository (arch §4.1: single `origin`, public, not a fork, issues enabled). The rename is a settings PATCH the active token can do; it cannot delete a repository (arch §4.3), so no plan may require one | **New** repository, then pushed a branch taken from **`c84b881`** in a local clone of the pre-split history — true ancestry, no rewrite, no graft (R6; arch §2.7) | **New** repository seeded from files extracted out of `ac523d7`, with the provenance table in §4.6 copied into its `README.md` as the record of where each file came from |
| History it carries | Everything: 175 commits, 8 merges, the whole Evernote→Joplin→Phase-0→Phase-4A line (arch §2.2, §3, §9 D1) | `5fb6aed`…`c84b881` — 30 commits, **zero merge commits**, from the 2023 Evernote prototype through the MD5 switch, the Joplin conversion and all of Phase 0 (arch §2.7) | **None.** A fresh root commit. Ancestry is documented, not inherited — see §4.6 |
| What it must **not** carry | nothing to strip; it is the continuing line | **(R6)** the inherited ServiceTag design package (`docs/design/`, `docs/superpowers/` — 70 files, ~9.6k lines at `c84b881`, arch §2.7); the tracked release APK `app/release/app-release.apk`; the two docs carrying the owner's GitHub handle and personal issue URLs (`docs/design/issues/applied.md`, `docs/superpowers/plans/2026-09-14-phase-0-foundation.md`); the four certificate-digest evidence lines in `docs/design/phase-0-evidence.md` (arch §2.8). All four are deleted **going forward** in the reconstruction's first commit; they remain in history, which is why the repository's visibility decision (arch §8.2 Q6) belongs to the owner | any app vocabulary — §4.4 is a gate, not a guideline |
| Recovery refs | tag `pre-split-checkpoint` and branch `pre-split-master`, both at `ac523d7`, both already pushed (arch §4.1) — they stay, under the renamed repository | inherits none | none |
| Releases | keeps the 2023 draft release as history (arch §2.10, §4.8): untagged, unanchored, Evernote-era, no assets. Not published, not deleted | none at creation | none at creation |
| Issues | 33 of 36 stay (arch §4.7) | receives **#6** and **#36** by transfer (R9) | none at creation |

**Why the rename rather than a fresh ServiceTag repo.** The continuing line is the one with 145
commits of ServiceTag work, 70 design documents, 33 issues, a green CI history and the two recovery
refs. Moving *that* costs everything; moving the 30-commit narrow line costs nothing. This also
settles arch §8.2 Q12: the `pre-split-checkpoint` tag and the 2023 draft release travel with
ServiceTag, because they describe ServiceTag's state and the Evernote era respectively — neither is
a narrow-product anchor.

---

## 3. Android identities

Every row is a distinct decision because the current identity is not a handful of call sites: the
string `com.loosecannon.notenfc` occurs **2 028 times across 236 files** (arch §4.6, §9 D4).

| Identity | noteNFC | ServiceTag | Source of truth |
|---|---|---|---|
| `applicationId` | `com.loosecannon.notenfc` | `com.loosecannon.servicetag` | R1 / R2 |
| `namespace` | `com.loosecannon.notenfc` | `com.loosecannon.servicetag` | `app/build.gradle.kts` **[code]** |
| Kotlin package root | `com.looseCannon.noteNFC` at `c84b881`, **normalised to `com.loosecannon.notenfc`** in the reconstruction's rename commit **[P2 — proposal]** | `com.loosecannon.servicetag` | arch §2.10 records the mixed-case historical root and the asymmetry with `:core`'s already-lowercase root |
| App label (`app_name`) | `noteNFC` | `ServiceTag` | `res/values/strings.xml` **[code]**; ServiceTag's user-facing name is fixed by R2 |
| Theme | `Theme.NoteNfc` (as at `c84b881`) | `Theme.ServiceTag` **[P3 — proposal]** | `res/values/themes.xml` **[code]** |
| `Application` class | none (the narrow app has no `Application` subclass at `c84b881`) | `ServiceTagApp` **[P3 — proposal]**, which also resolves the two-classes-named-`NoteNfcApp` collision (arch §4.6) by leaving the nav-root composable as the only survivor of that name, renamed `ServiceTagRoot` | arch §4.6 |
| FileProvider authority | none (no FileProvider in the narrow app) | `com.loosecannon.servicetag.files` — **derived**, no literal to change: manifest uses `${applicationId}.files` and `AppGraph` uses `BuildConfig.APPLICATION_ID` (arch §4.6, §7.7 item 6) | **[code]** |
| Deep-link scheme | `notenfc` — **reserved, no filter declared at reconstruction** **[P4 — proposal]**: the narrow app had no `VIEW` filter and R6 forbids new surface; `notenfc://tag` is held for issues #6/#36 | `servicetag`, hosts `asset`, `link`, `tag` | R2; arch §5.4, §6.3 |
| NDEF external type — written | `com.loosecannon.notenfc:tag` | `com.loosecannon.servicetag:tag` | R1 / R2 |
| NDEF external type — decode-only | `com.loosecannon.notenfc:md5_short` (permanent; D6's still-live promise, arch §5.9) | **none** — dropped: decoder, `PayloadFormat` value, manifest filter and "Legacy" sheet (R3) | R3; arch §6.1 |
| AAR package | `com.loosecannon.notenfc` | `com.loosecannon.servicetag` — equal to the `applicationId` by construction, not by doc comment (§4.3 invariant 6) | R2; arch §5.3, §6.3 |
| Room database name | **N/A** — no Room in noteNFC (R6); persistence is `SharedPreferences("noteNFCURLs")` | `servicetag.db` (was `notenfc.db`, a literal in `AppGraph.DB_NAME`, not derived — arch §4.6, §7.7 item 4) | R2 |
| Room schema export dir | N/A | `app/schemas/com.loosecannon.servicetag.data.room.AppDatabase/`, holding `1.json`…`5.json` re-exported under the new FQN (arch §4.5, §7.7 item 5) | derived from the `AppDatabase` FQN |
| Export file-name prefixes | N/A | `ServiceTag-data-<stamp>.zip` / `ServiceTag-artifacts-<stamp>.zip` (was `noteNFC-*`, `SafBackupSetIO.BackupSetNames`). **The importer never reads a file name** (arch §7.3), so the preserved `noteNFC-*` files import unchanged | R2; arch §7.7 item 2 |
| `shared_prefs` file | `noteNFCURLs` (the 2024 map, unchanged — it is the product's whole persistence) | `servicetag` **[P5 — proposal]**, cosmetic only: prefs are package-scoped either way (arch §7.7 item 9) | **[code]** |
| Test instrumentation package | `com.loosecannon.notenfc.test` (AGP default; no `applicationIdSuffix`) | `com.loosecannon.servicetag.test` (same default) | arch §4.6 |
| `testInstrumentationRunner` | `androidx.test.runner.AndroidJUnitRunner` | same | **[code]** |
| `minSdk` / `targetSdk` / `compileSdk` | 26 / 36 / 37 | 26 / 36 / 37 | **[code]**, arch §4.5 |
| Signing key location pattern | `~/.config/notenfc/keystore.properties` → `storeFile`/`storePassword`/`keyAlias`/`keyPassword`, read at configuration time via `System.getProperty("user.home")` — **unchanged** | `~/.config/servicetag/keystore.properties`, same four keys, same mechanism, pointing at `~/.config/servicetag/servicetag-release.jks` | R1 / R2 / §8. **Values never appear in any repository, log or document** |

### 3.1 nfc-tag-core coordinates

Consumed by `includeBuild`, so no artifact is ever published — but a composite build substitutes on
*coordinates*, so they must exist and must be stable in case publication is ever wanted (§6.4).

| | Value |
|---|---|
| Gradle root project | `nfc-tag-core` |
| Modules | `:nfc-tag-core` (pure JVM), `:nfc-tag-core-android` (Android library) |
| Group | `com.loosecannon.nfc` **[P6 — proposal]** |
| Artifact ids | `nfc-tag-core`, `nfc-tag-core-android` |
| Kotlin package root | `com.loosecannon.nfc.tagcore`, `com.loosecannon.nfc.tagcore.android` **[P6 — proposal]** |
| Android library `namespace` | `com.loosecannon.nfc.tagcore.android` |
| Version | the git tag the submodule is pinned at, `v0.1.0` at extraction (§10.3) |

---

## 4. The nfc-tag-core boundary

### 4.1 Module layout

```text
nfc-tag-core/                     (repo root; Gradle root project "nfc-tag-core")
├── settings.gradle.kts           include(":nfc-tag-core", ":nfc-tag-core-android")
├── gradle/libs.versions.toml     Kotlin, AGP, JUnit 5, androidx.test — pinned to the same
│                                 versions the two apps use (arch §4.4) so a composite build
│                                 never resolves two Kotlin compilers
├── nfc-tag-core/                 PURE JVM. plugins: kotlin.jvm only. jvmToolchain(17).
│   │                             deps: NONE at runtime (not even coroutines). tests: JUnit 5.
│   └── src/{main,test}/kotlin/com/loosecannon/nfc/tagcore/
└── nfc-tag-core-android/         ANDROID LIBRARY. plugins: com.android.library + kotlin.android.
    │                             minSdk 26, compileSdk 37. NO Compose, NO Room, NO KSP,
    │                             NO serialization, NO lifecycle, NO Material.
    │                             deps: api(project(":nfc-tag-core")) + kotlinx-coroutines-core.
    └── src/{main,test,androidTest}/kotlin/com/loosecannon/nfc/tagcore/android/
```

The pure-JVM half is why this split is cheap rather than speculative: `:core` is **already** an
Android-free Kotlin JVM module with no Room and no Compose reach (arch §4.4 — "the single most
important structural fact for the split"). `nfc-tag-core` drops even its two dependencies, because
nothing in the envelope codec needs coroutines or JSON. **[P7 — proposal]**: keep
`kotlinx-coroutines-core` on the *Android* module only, where `TagWriteSession` needs a dispatcher.

### 4.2 Public API

Sketch, not final source. Each type names the file it was extracted from in §4.6.

```kotlin
// ---------- nfc-tag-core (pure JVM) ----------
package com.loosecannon.nfc.tagcore

/**
 * What identifies one product's tags on the wire. These three strings are the ONLY product
 * knowledge the library ever holds, and the caller supplies them.
 *
 * @param externalDomain NFC Forum external-type domain. Lower-case: `NdefRecord.createExternal`
 *   lower-cases both halves before joining them, so a mixed-case domain would not match the bytes
 *   actually on the tag (arch §2.9).
 * @param typeName external-type name, e.g. "tag".
 * @param aarPackage the Android applicationId to pin with an Application Record, or null for no
 *   AAR. Nullable because the 2024 records carried none (arch §2.9).
 */
data class TagIdentity(
    val externalDomain: String,
    val typeName: String,
    val aarPackage: String?,
) {
    val externalType: String = "$externalDomain:$typeName"

    init {
        require(externalType == externalType.lowercase()) { "external type must be lower-case: $externalType" }
        require(externalDomain.isNotBlank() && typeName.isNotBlank())
    }
}

/** Android-free view of one NDEF record; value equality over tnf + type bytes + payload bytes. */
data class NdefRecordData(val tnf: Int, val type: ByteArray, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean = /* contentEquals on both arrays */
    override fun hashCode(): Int = /* … */
}

/** What the envelope made of a message, before any product body parse. */
sealed interface TagContent {
    /** The first record is ours: same TNF, same external type. [body] is the raw payload. */
    data class Recognised(val body: ByteArray) : TagContent
    /** Not ours. [description] keeps the full offending type string so the caller can name it. */
    data class Foreign(val description: String) : TagContent
    /** No records at all. */
    data object Empty : TagContent
}

object NdefEnvelope {
    const val TNF_EXTERNAL_TYPE: Int = 0x04
    /** Platform constant, not identity. */
    const val AAR_TYPE: String = "android.com:pkg"

    /** `[externalRecord, aar?]` — in that order, always (§4.3 invariant 2). */
    fun encode(identity: TagIdentity, body: ByteArray): List<NdefRecordData>

    /**
     * First record of the message only, as the platform does. TNF gate, then type gate, then —
     * and only then — the body is handed back unparsed (§4.3 invariant 1).
     */
    fun decode(identity: TagIdentity, records: List<NdefRecordData>): TagContent

    /** Byte-identical to `NdefRecord.createApplicationRecord(packageName)`. */
    fun applicationRecord(packageName: String): NdefRecordData
}

/**
 * The generic `version | flags | 16-byte UUID` body. INCLUDED because both products consume it
 * identically: ServiceTag's v1 body and noteNFC's post-reconstruction body are the same 18 bytes
 * with the same version and flags semantics, differing only in the enclosing external type
 * (arch §5.2, §6.5). If ratification changes that — e.g. if noteNFC keeps writing an 8-hex body —
 * this object leaves the library and each app carries its own, per R4.
 */
object VersionedUuidBody {
    const val LENGTH: Int = 18

    sealed interface Parsed {
        data class Ok(val uuid: String) : Parsed
        /** Version byte above what the caller understands. Never parsed further, any length. */
        data class NewerVersion(val version: Int) : Parsed
        data class Malformed(val reason: String) : Parsed
    }

    fun encode(version: Int, flags: Int, canonicalUuid: String): ByteArray
    fun parse(expectedVersion: Int, expectedFlags: Int, body: ByteArray): Parsed

    /** Refuses a non-UUID and a UUID that is not the canonical lower-case form. */
    fun requireCanonicalUuid(value: String): java.util.UUID
}

/** What the CALLER's classifier made of what the tag already holds. No product words. */
sealed interface ExistingContent {
    data object Empty : ExistingContent
    /** Our external type, body understood. */
    data class Ours(val detail: String) : ExistingContent
    /** Our external type, version byte above this build's. */
    data class OursNewerVersion(val version: Int) : ExistingContent
    /** A type this product reads but never writes (a decode-only predecessor). */
    data class OursDecodeOnly(val detail: String) : ExistingContent
    data class Foreign(val description: String) : ExistingContent
    data class Unreadable(val reason: String) : ExistingContent
}

enum class OverwriteReason {
    EMPTY_TAG, SAME_TAG, OTHER_TAG_SAME_PRODUCT,
    SAME_PRODUCT_DECODE_ONLY, SAME_PRODUCT_NEWER_VERSION, FOREIGN, UNREADABLE,
}

sealed interface OverwriteDecision {
    data object Proceed : OverwriteDecision
    /** [reason] is a token; [detail] is raw evidence (a type string, an id). NOT a sentence. */
    data class Confirm(val reason: OverwriteReason, val detail: String) : OverwriteDecision
}

/**
 * Read-before-write: write without asking ONLY over an empty tag or over the very identity being
 * written (a retry). Everything else costs exactly one confirmation. The rule is identical for
 * both products; only the sentences differ, and the library builds none.
 */
object OverwritePolicy {
    fun decide(existing: ExistingContent, isSameIdentity: Boolean): OverwriteDecision
}

// ---------- nfc-tag-core-android (Android adapter) ----------
package com.loosecannon.nfc.tagcore.android

/** The only place `android.nfc` types meet the pure-bytes codec. */
object NdefBridge {
    fun NdefMessage?.toRecordData(): List<NdefRecordData>
    fun List<NdefRecordData>.toNdefMessage(): NdefMessage       // record id always ByteArray(0)
    fun ByteArray?.toHexOrNull(): String?
    /** First message of `EXTRA_NDEF_MESSAGES`, with the SDK-33 typed/untyped split. */
    fun Intent.ndefRecords(): List<NdefRecordData>?
    fun Intent.nfcTag(): Tag?
    /** Serialised size of the message the caller intends to write — the `needed` of a capacity check. */
    fun List<NdefRecordData>.serialisedSize(): Int
}

/**
 * Reader mode: callback-based, no PendingIntent, no activity relaunch. The platform's NDEF check
 * is deliberately left ON — it is what makes `Ndef` and `NdefFormatable` available on the
 * delivered `Tag`, so skipping it would leave nothing to write to (§4.3 invariant 5).
 *
 * @param onTag runs on a platform binder/background thread, never the main thread: blocking
 *   [TagWriter] calls are allowed straight from it, any UI update must be posted.
 */
class NfcReaderModeSession(activity: Activity, onTag: (Tag) -> Unit) {
    val available: Boolean      // an NFC adapter exists
    val enabled: Boolean        // …and it is switched on
    fun start()                 // call from onResume / LifecycleResumeEffect
    fun stop()                  // call from onPause or dispose
}

class TagInspection(
    val uid: String?,
    val existingRecords: List<NdefRecordData>,
    /** `Ndef.maxSize`, or -1 for a tag that still needs formatting: capacity is unknown until then. */
    val maxSize: Int,
    val writable: Boolean,
    val needsFormat: Boolean,
    val canLock: Boolean,
    /** Set when the tag's NDEF could not be parsed at all; the tag is still overwritable. */
    val unreadable: String? = null,
)

sealed interface WriteResult {
    /** [verified] is false only on the format path, where the same `Tag` cannot be re-read. */
    data class Written(val readBack: List<NdefRecordData>, val bytes: Int, val verified: Boolean, val locked: Boolean) : WriteResult
    data class TooSmall(val maxSize: Int, val needed: Int) : WriteResult
    data object ReadOnly : WriteResult
    data object Unsupported : WriteResult
    data class VerifyMismatch(val readBack: List<NdefRecordData>) : WriteResult
    data class Failed(val reason: String) : WriteResult
}

/**
 * Read-first, write, read-back. Every function BLOCKS on tag I/O: call from a worker thread.
 * Decisions are the caller's, made between [inspect] and [write] while the tag stays in the field.
 * The two halves report failure differently on purpose: [write] folds tag I/O into
 * [WriteResult.Failed]; [inspect] lets it propagate.
 */
object TagWriter {
    /** @throws java.io.IOException (incl. `TagLostException`). Null when neither `Ndef` nor `NdefFormatable`. */
    fun inspect(tag: Tag): TagInspection?
    fun write(tag: Tag, records: List<NdefRecordData>, lock: Boolean): WriteResult
    /** Permanent. Call only after a verified read-back. */
    fun lock(tag: Tag): Boolean
}

/** One tap's handle. The seam that keeps `android.nfc.Tag` — unconstructible in a JVM test — out of decision logic. */
interface TagHandle { val uid: String? }
class NfcTagHandle(val tag: Tag) : TagHandle

interface TagIo {
    fun inspect(tag: TagHandle): TagInspection?
    fun write(tag: TagHandle, records: List<NdefRecordData>, lock: Boolean): WriteResult
    fun lock(tag: TagHandle): Boolean
}
object RealTagIo : TagIo   // the only place a Tag comes back out of a handle

/** What the caller must supply once per write session. */
data class IntendedWrite(
    val records: List<NdefRecordData>,
    /** True when [ExistingContent] is the very identity these records carry (a retry). */
    val isSameIdentity: (ExistingContent) -> Boolean,
)

/** Everything the library refuses to decide: rows, consent presentation, wording. */
interface TagWriteCallbacks {
    /** Mint or reuse the caller's row and return the message. Called once; reused on every retry. */
    suspend fun intendedWrite(): IntendedWrite
    /** Classify what was read, in the caller's own vocabulary. */
    fun classify(records: List<NdefRecordData>, unreadable: String?): ExistingContent
    /** A verified write landed. The caller stamps its row with the physical UID. */
    suspend fun onWritten(uid: String?, locked: Boolean)
    /** The session ended with nothing verified. The caller deletes any row it minted. */
    suspend fun onAbandoned()
    /** Every user-facing sentence. The library builds none — it hands over tokens and evidence. */
    fun say(event: WriteEvent): String
}

/** The states a write screen draws. Sentences come from [TagWriteCallbacks.say]. */
sealed interface WriteState {
    data class Idle(val message: String) : WriteState
    data class Confirm(val reason: OverwriteReason, val detail: String, val message: String) : WriteState
    /** A formatted tag was written but could not be re-read on the same handle: tap it again. */
    data class Verifying(val message: String) : WriteState
    data class Written(val locked: Boolean) : WriteState
    data class Error(val message: String) : WriteState
}

/**
 * The write protocol, and nothing else: single-flight; read before write; one confirmation, whose
 * consent is remembered against the CONTENT it was given for (so a stale `Tag` handle costs a
 * second tap, not a second question); format → second-tap verify → lock last; abandon on close.
 */
class TagWriteSession(
    private val io: TagIo,
    private val callbacks: TagWriteCallbacks,
    private val scope: CoroutineScope,
    /** Outlives the screen: abandoning must finish even though the screen is going away. */
    private val abandonScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val state: StateFlow<WriteState>
    val lockArmed: StateFlow<Boolean>
    fun armLock(on: Boolean)
    /** Call from the reader-mode callback. Safe on a binder thread. */
    fun onTag(handle: TagHandle)
    fun confirmOverwrite()
    fun keepIt()
    fun abandonIfUnwritten()
}
```

### 4.3 Invariants

The library states and tests these; each consumer re-tests the ones that are about *it*.

1. **Type gate before body parse.** `decode` checks TNF, then the exact external type, and only
   then returns the body. A sibling product's tag must come back `Foreign` and never `Recognised`.
   **This is the one real hazard of the split**: both products' bodies are byte-identical
   (`01 00 ‖ 16 UUID bytes`), so any path that read a payload without checking the type first would
   parse a ServiceTag tag as a valid noteNFC tag with an unknown id, show "Unregistered tag" with a
   **Bind** button, and let noteNFC silently adopt a live asset tag (arch §6.3 invariant 1).
   `Recognised` therefore carries only the body, never the whole record, so there is no API shape in
   which a caller can reach a payload it did not type-check.
2. **AAR never first.** `encode` returns `[externalRecord, aar?]`. Put the AAR first and the tag
   stops matching the `NDEF_DISCOVERED` filter, because the platform reads the *first* record to
   decide the tag's type (**[platform-doc]**, arch §5.3; order is **[JVM-proven]** today by
   `NdefCodecV1Test.messageIsTagRecordThenApplicationRecord`).
3. **Structural read-back equality.** Verification compares `List<NdefRecordData>` — record count,
   order, each record's TNF, full type string and full payload bytes — not raw tag bytes. The
   record **id** field is deliberately outside the comparison (`toNdefMessage` always writes
   `ByteArray(0)`), and so are the TLV framing, terminator and chunking, because the comparison
   happens after the platform re-parsed the message (arch §5.8). The intent is "the tag holds the
   records I meant", not "the tag holds the bytes I sent".
4. **Encode strict, decode canonicalising.** `encode` refuses a non-canonical UUID; `parse` always
   yields the canonical lower-case form because it round-trips through `java.util.UUID`
   (arch §5.2 rule 6).
5. **Never `FLAG_READER_SKIP_NDEF_CHECK`.** The reader-mode flag set is
   `FLAG_READER_NFC_A or _B or _F or _V` and nothing else. With the skip flag set, `Ndef.get()`
   returns null and there is nothing to write to — device-proven negatively at `e2cf1d0`
   (arch §5.5, §9 D2, and the R4 clarification). The library still never trusts the platform's
   cached message: `inspect` does a fresh `Ndef.getNdefMessage()`.
6. **AAR package equals the consumer's `applicationId`.** Today this is a doc comment and nothing
   else (arch §4.6, §6.3). In the library it is the caller's `TagIdentity.aarPackage`, and each app
   pins it with a test asserting `aarPackage == BuildConfig.APPLICATION_ID` (§4.5).
7. **Capacity is checked on both write paths.** `needed` is the **serialised NDEF message** size —
   header, type and payload for every record, plus TLV framing — not the body size
   (`NdefBridge.serialisedSize`). On the `Ndef` path the check is
   `if (maxSize < needed) TooSmall(maxSize, needed)`, evaluated after `connect()` and after the
   `isWritable` check, so a read-only tag reports `ReadOnly` rather than `TooSmall` (arch §5.7).
   On the `NdefFormatable` path capacity is unknown before formatting (`inspect` reports
   `maxSize = -1`), which today makes a too-small unformatted tag surface as a generic `Failed`
   instead of `TooSmall` — the real gap at arch §5.7 and §8.2 Q13. **The formatted-size rule
   closes it** (R4 mandates the fix; the mechanism is **[P8 — proposal]**): the `NdefFormatable`
   path defers the comparison to the first moment `Ndef.maxSize` is readable, which is the second
   tap, and reports `TooSmall(maxSize, needed)` there rather than letting the tag arrive at
   verification; and before the first tap the library exposes `serialisedSize` so a consumer can
   state the requirement up front ("this needs N bytes; NTAG213 holds 144").
8. **Failure reporting is asymmetric on purpose.** `inspect` propagates tag I/O failure so the
   caller can say "hold it still and try again"; `write` folds every tag I/O failure into
   `Failed(reason)` so a half-written tag never looks like an exception. Every connection is closed
   in a `finally { runCatching { … } }` (arch §5.6, `bdcc475`).
9. **Lock last, never blind.** `makeReadOnly()` runs only after a verified read-back. On the format
   path the tag is formatted **unlocked** and both the verification and the lock are deferred to the
   second tap (arch §5.6 step 7, `e2cf1d0`).
10. **One confirmation, remembered against content.** A confirmed overwrite is consent for *that
    content*, honoured on the next tap of a tag carrying it, and cleared by a *different* payload.
    This exists because the captured `Tag` handle goes stale while a confirmation sheet is up — the
    NFC service re-discovers the tag and refuses the old handle with "Tag is out of date",
    **[device-observed]** on an Android 17 phone (arch §5.6, `0e1975f`).
11. **Single-flight.** A tap arriving while a write is in flight, or after the session finished, is
    dropped; a confirmation transfers ownership of the busy flag to the sheet. Any exception
    escaping the callback becomes `WriteState.Error`, never a crash on a binder thread.
12. **The library builds no sentence.** Every user-facing string comes from
    `TagWriteCallbacks.say`; the library's own return values carry tokens plus raw evidence. This is
    what lets each app say "this is a ServiceTag tag — overwriting it will detach it from its
    machine" where the other says "this tag belongs to another app" (arch §6.5).

### 4.4 The forbidden-dependency scan

A CI job in the library repository that fails the build when the library learns something it must
not know. The word list is the brief's forbidden-library-knowledge set (**[P9 — proposal]**: the
brief's own §22 list is not present in this worktree, so the list below is reconstructed from the
binding §4 enumeration relayed with this task and needs ratification against the brief text).

```bash
# nfc-tag-core/tools/forbidden-scan.sh  — NEW
set -euo pipefail
WORDS='joplin|obsidian|logseq|evernote|notion|onenote|todoist|note[ _-]?id|noteNFC|notenfc|ServiceTag|servicetag|asset|TagBinding|Room|room3|androidx\.room|Compose|compose|journal|measurement|profile|schedule|reminder|attachment|backup|restore|navigation|nav3|deep[ _-]?link|md5|prefs|SharedPreferences'
HITS=$(grep -RInE "$WORDS" \
        --include='*.kt' --include='*.kts' --include='*.xml' --include='*.toml' \
        nfc-tag-core/src nfc-tag-core-android/src settings.gradle.kts \
        | grep -vFf tools/forbidden-scan.allow || true)
if [ -n "$HITS" ]; then echo "$HITS"; echo "forbidden knowledge in the library"; exit 1; fi
```

**False positives are handled by an allow file, never by weakening the pattern.**
`tools/forbidden-scan.allow` holds one exact `path:line-fragment` per accepted hit, each with a
one-line reason in a trailing comment. The hits that will legitimately exist:

| Expected false positive | Why it is allowed |
|---|---|
| `com.loosecannon.nfc.tagcore` in every package line | the library's own name; `nfc` is not on the list, but the group string is matched by a future broadening |
| the word `asset` inside `androidTest` resource paths (`src/androidTest/assets/`) | a Gradle source-set convention, not the domain noun. **[P10 — proposal]**: rename the fixture directory rather than allow-list it, so the word never appears |
| `Room` inside `NdefRecordData`'s KDoc if a reviewer writes "room for one more record" | prose. Rewrite the prose; do not allow-list it |
| `deepLink` in a KDoc explaining why routes are *not* here | rewrite as "URI routes" |
| the `TagIdentity` KDoc example domain | must be `com.example.app`, never either real applicationId |

The scan is deliberately over-broad: a hit is a conversation, and the allow file is the record of
each conversation's outcome. A hit inside a *test fixture* is as much a failure as one in `main`,
because a fixture is how product vocabulary usually gets in.

### 4.5 Test plan

**`nfc-tag-core` (pure JVM, JUnit 5, runs on every push).** Extracted and generalised from the
three existing `:core` test classes (arch §4.4; provenance in §4.6).

| Group | Cases |
|---|---|
| Envelope round-trip | `encode(identity, body)` then `decode(identity, …)` returns `Recognised(body)` byte-for-byte; exact record layout (tnf `0x04`, type as US-ASCII, body verbatim) — from `NdefCodecV1Test.exactByteLayout`, `roundTrips` |
| Record order | `encode` with a non-null `aarPackage` yields exactly two records, ours first, AAR second, with the AAR's type `android.com:pkg` and the package as its ASCII payload — from `messageIsTagRecordThenApplicationRecord`. With a null `aarPackage`, exactly one record |
| AAR byte-identity | `applicationRecord(pkg)` equals what `NdefRecord.createApplicationRecord(pkg)` produces (asserted as a pinned byte vector in the JVM module; re-asserted against the real platform call in the Android module) |
| **Sibling isolation** | a record whose type is a *different* domain with the same `typeName` decodes as `Foreign`, and the `Foreign.description` contains the full offending type string — the generalisation of `NdefCodecTest.evernoteEraTypeIsForeign`, and the template each app copies (§4.3 invariant 1) |
| TNF gate | our exact external type under a non-external TNF is `Foreign`, not `Recognised` and not malformed — from `tagRecordUnderWrongTnfIsForeign` |
| First-record-only | extra records after the first are ignored — from `onlyFirstRecordMatters`; an AAR-only message is `Foreign` — from `applicationRecordAloneIsForeign`; an empty list is `Empty` — from `emptyMessageIsEmpty` |
| Body: version negotiation | version byte above the expected one → `NewerVersion(n)`, never parsed, at any body length (vectors `0x02` full-length, `0x7f` 3 bytes, `0xff` 1 byte); version `0x00` → `Malformed` — from `newerVersionIsReportedNotParsed`, `versionZeroIsMalformed` |
| Body: malformed | empty body, wrong length, non-zero flags → `Malformed` with a reason naming what was wrong — from `emptyPayloadIsMalformed`, `wrongLengthIsMalformed`, `nonZeroFlagsIsMalformed` |
| Body: UUID canonicality | encode refuses a non-UUID and a non-canonical (upper-case) UUID; decode always yields the canonical form — from `refusesNonCanonicalIdOnEncode`, `decodedIdIsCanonicalLowercase` |
| Limits | a two-record message (ours + AAR) with an 18-byte body serialises to **92 bytes** — 48 + 41 + 3 — and fits an NTAG213's 144, asserted as `serialisedSize() <= 144`, from `fitsAnNtag213`. Parameterised so a consumer can assert its own budget |
| `TagIdentity` | refuses a mixed-case external type; `externalType` is `"$domain:$name"` |
| `OverwritePolicy` | the full `ExistingContent` × `isSameIdentity` matrix → the seven `OverwriteReason` tokens; `Proceed` for `Empty` and for `Ours` with `isSameIdentity`, `Confirm` for everything else — generalised from `OverwritePolicyTest` |

**`nfc-tag-core-android`.** Two tiers, because the adapter has both testable and untestable halves.

| Tier | Where it runs | Cases |
|---|---|---|
| Local unit (`test/`) | JVM, every push | `TagWriteSession` against a fake `TagIo` and a fake `TagHandle`: single-flight (a second tap during a write is dropped); read-before-write ordering; `Confirm` parks and does not write; remembered consent honoured on the next tap with the same content and cleared by different content; the format path's `Verifying` → second-tap verify → lock-last sequence; `abandonIfUnwritten` fires `onAbandoned` exactly once when nothing was verified and never after a verified write; every `WriteResult` maps to exactly one `WriteState`. This is the tier that exists today only because `TagIo`/`TagHandle` keep `android.nfc.Tag` out of the decision logic (arch §6.2) |
| Instrumented (`androidTest/`) | **the emulator, locally** — not in CI | `NdefBridge` round-trips (`toNdefMessage` → `toRecordData` identity; an `Intent` carrying `EXTRA_NDEF_MESSAGES` on both the pre-33 and 33+ branches; `toHexOrNull`); `applicationRecord` against the real `NdefRecord.createApplicationRecord`; `NfcReaderModeSession.available == false` on an emulator with no NFC, and `start()`/`stop()` being safe no-ops there. Anything needing a real chip is a physical-tag row in the migration runbook §D, never an automated test |

The emulator suites stay local because the owner's rules keep instrumented tests off CI and off the
phone (arch §4.2: CI has no `androidTest` step, "correctly excluded — those need a device").

### 4.6 Provenance

What "documented provenance" means for a repository with no inherited history: every file says which
file it came from, and where that file's own history starts. Start every trace with
`git log --follow -- <path>` **in the ServiceTag repository** (the renamed continuation of this one),
because that is where the history lives. All SHAs below are from that history and are re-verified
first-appearance commits.

| NEW file in nfc-tag-core | Extracted from (at `ac523d7`) | `git log --follow` starting point | Change on extraction |
|---|---|---|---|
| `nfc-tag-core/…/NdefRecordData.kt` | `core/src/main/kotlin/com/loosecannon/notenfc/core/nfc/NdefCodec.kt` lines 8–12 | `76b751a` "add :core with the legacy key and ndef codec pinned by tests" | moved to its own file; unchanged otherwise. "The single most reusable type in the repo" (arch §6.1) |
| `nfc-tag-core/…/TagIdentity.kt` | **NEW type** parameterising `NdefCodec.DOMAIN` (`:37`), `V1_TYPE_NAME` (`:39`), `PACKAGE_NAME` (`:46`) | same three constants: `76b751a` (`DOMAIN`, normalised at `b9f7e51`), `f92a391` (`V1_TYPE_NAME`, `PACKAGE_NAME`) | the constants become a value type. `DOMAIN` and `PACKAGE_NAME` are the *same literal today but two different things* — an NFC Forum domain and an Android package name — and are therefore separate fields (arch §5.2, §6.3) |
| `nfc-tag-core/…/NdefEnvelope.kt` | `NdefCodec.decode` (`:56-65`), `encodeV1` (`:90`), `v1Record` (`:93-102`), `applicationRecord` (`:104-109`) | `76b751a` (`decode` skeleton, legacy branch), `f92a391` "tag payload format v1: codec, AAR, overwrite policy" (v1 + AAR) | identity becomes a parameter; the type switch collapses to "my type / not my type"; the body is returned unparsed. The legacy branch does **not** come along — it is noteNFC policy (R3, arch §6.1) |
| `nfc-tag-core/…/TagContent.kt` | `TagPayload.Foreign` / `.Empty` (`NdefCodec.kt:21-26`) | `76b751a` (`Foreign`, `Empty`), `f92a391` (`NewerVersion`) | `Recognised(body)` replaces the product-specific `V1`/`LegacyMd5` arms (arch §6.1); `NewerVersion` moves down into `VersionedUuidBody.Parsed` because it is a *body* fact, not an envelope fact |
| `nfc-tag-core/…/VersionedUuidBody.kt` | `NdefCodec.decodeV1` (`:67-80`), the `ByteBuffer` half of `v1Record`, `requireCanonicalUuid` (`:111-120`), constants `V1_VERSION`/`V1_FLAGS`/`V1_PAYLOAD_LENGTH` (`:41-43`) | `f92a391` | version and flags become parameters; `requireCanonicalUuid` takes a `String` instead of a `TagId`, so the `TagId` wrapper stays in each app (arch §6.1) |
| `nfc-tag-core/…/OverwritePolicy.kt` | `core/src/main/kotlin/com/loosecannon/notenfc/core/nfc/OverwritePolicy.kt` (whole file) | `f92a391` | signature changes from `decide(existing: TagPayload, intended: TagId)` to `decide(existing: ExistingContent, isSameIdentity: Boolean)`; the five hard-coded reason sentences — each of which says "noteNFC" — become `OverwriteReason` tokens plus a `detail` string (arch §6.1) |
| `nfc-tag-core-android/…/NdefBridge.kt` | `app/src/main/kotlin/com/loosecannon/notenfc/nfc/NdefBridge.kt` (whole file) | `dc1bb1c` "nfc adapter: ndef bridge, reader-mode session, tag writer with read-back; wire the use cases" | moved wholesale; imports only `android.*` and `NdefRecordData`, so nothing to strip. `Intent.nfcTag()` is currently dead code in the app (arch §6.2) and becomes live API here. `serialisedSize()` is **NEW**, lifting the `message.toByteArray().size` computation out of `TagWriter.write` so a caller can ask before a tap (§4.3 invariant 7) |
| `nfc-tag-core-android/…/NfcReaderModeSession.kt` | `app/src/main/kotlin/com/loosecannon/notenfc/nfc/NfcReaderModeSession.kt` (whole file, 39 lines) | `dc1bb1c`; **corrected at `e2cf1d0`** "reader mode: keep the platform ndef check; lock only after read-back" | verbatim. **The doc comment is half the value — carry it across** (arch §6.2), including the sentence that says why the platform NDEF check is left on and the threading contract on `onTag` |
| `nfc-tag-core-android/…/TagWriter.kt` (+ `TagInspection`, `WriteResult`) | `app/src/main/kotlin/com/loosecannon/notenfc/nfc/TagWriter.kt` (whole file, 126 lines) | `dc1bb1c`; throw contracts documented at `bdcc475`; lock-after-read-back and the unlocked format path at `e2cf1d0` | the one `NdefCodec.decode` call — used only to classify what was read — is removed, so the library carries no product type string; `TagInspection.existing: TagPayload` becomes `unreadable: String?` and the caller classifies via `TagWriteCallbacks.classify` (arch §6.2). Adds the formatted-size capacity rule (§4.3 invariant 7) |
| `nfc-tag-core-android/…/TagIo.kt` (+ `TagHandle`, `NfcTagHandle`, `RealTagIo`) | `app/src/main/kotlin/com/loosecannon/notenfc/ui/scan/TagWriteController.kt` lines 32–65 | `c808b49` "scan, tag result sheets, write flow with the 1b rules, share card, links" | moved out of a UI file, where it does not belong (arch §6.2) |
| `nfc-tag-core-android/…/TagWriteSession.kt` (+ `WriteState`, `WriteEvent`, `TagWriteCallbacks`, `IntendedWrite`) | `app/src/main/kotlin/com/loosecannon/notenfc/ui/scan/TagWriteController.kt` lines 90–299 | `c808b49`; the rules it encodes land at `18534bd` (the sequence), `0e1975f` (remembered consent), `485e110` (don't leave the lock switch armed on back) | **the highest-value extraction after `TagWriter`, and the hardest** (arch §6.2). `ProvisionTag`, `TagBinding`, `TagTarget`, `AppGraph` and all thirteen message strings leave; `TagWriteCallbacks` is the hole they leave behind. `WriteState.Written(tagId, locked)` loses `tagId` — the caller already knows it |
| `nfc-tag-core/src/test/…/NdefEnvelopeTest.kt` | `core/src/test/kotlin/com/loosecannon/notenfc/core/nfc/NdefCodecTest.kt` | `76b751a`; legacy-only cases trimmed at `26ec9d0` | the legacy-key cases stay behind in noteNFC; `evernoteEraTypeIsForeign` becomes the parameterised sibling-isolation case |
| `nfc-tag-core/src/test/…/VersionedUuidBodyTest.kt` | `core/src/test/kotlin/com/loosecannon/notenfc/core/nfc/NdefCodecV1Test.kt` | `f92a391` | version/flags/length become parameters |
| `nfc-tag-core/src/test/…/OverwritePolicyTest.kt` | `core/src/test/kotlin/com/loosecannon/notenfc/core/nfc/OverwritePolicyTest.kt` | `f92a391` | asserts tokens instead of sentences |
| `nfc-tag-core-android/src/test/…/TagWriteSessionTest.kt` | `app/src/test/kotlin/com/loosecannon/notenfc/ui/scan/TagWriteControllerTest.kt` | `c808b49` | fakes replace `ProvisionTag`; assertions move from sentences to states and callback calls |

Two things deliberately do **not** move, and the provenance table records the decision so nobody
looks for them: `LegacyKey.compute` (added `76b751a`, **deleted** `26ec9d0`; it survives as working
code only in the `c84b881` tree, which is exactly why noteNFC's legacy support comes from the
reconstruction and not from master — arch §6.4), and `TagRoute`/`DeepLinkRoute`/`LinkLaunchPolicy`
(policy, R4, arch §6.1 and §8.2 Q11).

---

## 5. What deliberately stays app-specific

Duplication here is a decision, not debt. The rule applied is the archaeology's: mechanism only if
a second real consumer would use the same behaviour unchanged given a parameter for identity
(arch §6).

| Stays in each app | Why not shared |
|---|---|
| **Resolution and binding** — `ResolveTag`/`Resolution`, `BindTag`, `ProvisionTag`, `TagBinding`, `PayloadFormat`, `TagStatus`, `TagTarget`, `requireTargetExists` | "The clearest boundary in the codebase: everything below is mechanism, this and above is policy" (arch §6.1). ServiceTag's resolver reaches four repositories, a `UnitOfWork` and a `Clock`, stamps `lastScannedAt` inside a transaction — *a read scan performs a write* — and has eight outcomes including `OpenAsset` and `Revoked` (arch §5.9). noteNFC's has no database at all: a prefs lookup with two outcomes. The *protocol* (mint → write → verify → complete, else abandon) is mechanism and is documented in the library's README; the rows are not |
| **Deep-link routes** — `TagRoute`, `DeepLinkRoute` | "The library must not own a scheme" (arch §6.1). `notenfc` and `servicetag` are identity; `asset` has no meaning in noteNFC. The only mechanism inside is the canonical-UUID regex, which the library exports once via `VersionedUuidBody.requireCanonicalUuid` — removing the *three-place* duplication the archaeology found (`TagRoute.kt:13`, `DeepLinkRoute.kt:18`, and inside `requireCanonicalUuid`) |
| **Share flow** — `ShareActivity`, `ShareFlow`, `ShareCardScreen` | noteNFC's version is the whole product; ServiceTag's is one way to create a link (arch §6.2). Both read `EXTRA_TEXT` as a `CharSequence` "because that is what the contract promises; the styling is dropped, not trusted" — a *pattern* worth copying, in two different UI toolkits |
| **Link-launch policy** — `LinkLaunchPolicy`, `LinkCheck`, `OpenLink`, `SaveLink`, `LinkLauncher` | "The allowlist is a **product decision**, not a mechanism. Duplicate; do not share" (arch §6.1). It is not even NFC. ServiceTag's allowlist is `{joplin, obsidian, logseq, http, https}` with the gate run twice — at save and at launch, "so a URI that arrives through a backup gets the same treatment as one typed in" (arch §5.11). noteNFC copies the *code* at reconstruction (R6: "a copied (not shared) safe `ACTION_VIEW` launch policy") and then diverges under issues #6/#36. Both keep the `ActivityNotFoundException` **and** `SecurityException` catch, because the 2024 app crashed without it (arch §2.5) |
| **The v1 payload's meaning** | The library hands back 18 bytes. That a ServiceTag tag's UUID is a row in `nfc_tag` whose `target` names an asset, and that a noteNFC tag's UUID is a key in a prefs map naming a link, is exactly the knowledge §4.4 forbids. The tag format knows nothing about assets or links — "that is the property the whole split depends on" (arch §5.2) |
| **Legacy `md5_short`** — the decoder, the 8-hex pattern, the `LEGACY_MD5` row value, the manifest filter, the "Legacy tag" sheet with its *Rewrite in format v1* / *Bind as-is* / *Cancel* actions | **noteNFC only** (R3). ServiceTag has zero legacy bindings (`nfc_tag = 0` live, arch §7.5) and dropping the type removes the only ambient-filter overlap between the two products — which is what §16 of the brief requires. noteNFC inherits the decoder from the `c84b881` tree, not from master, because `LegacyKey.compute` no longer exists in modern code (arch §6.4) |
| **The "Legacy" UI** | A legacy tag is "a migration opportunity, not damaged data" (arch §5.9). That sentence is a noteNFC sentence. ServiceTag never draws it |
| **Every user-facing sentence** — including the two deliberately different `describe(TagPayload)` functions and the two "Unregistered tag" states that differ only in whether a row exists | "Do not extract — the divergence is the feature" (arch §6.2, §5.12) |
| **The dispatch trampoline** — `NfcDispatchActivity` | The *shape* (translucent, UI-less, `singleTop`, `excludeFromRecents`, hostile-extras guard, exactly two accepted actions, hand off to the one renderer) is a pattern documented in the library README; the code reaches `AppGraph`, `MainActivity`, `OpenLink` and `Resolution` and is product-specific (arch §6.2) |

---

## 6. Dependency mechanism (R5)

A git submodule pinned to a **tagged** commit, consumed by Gradle `includeBuild`. No artifact is
published, because no publication infrastructure exists anywhere in this estate — no Maven
repository in any build file, no init script (R5).

### 6.1 `settings.gradle.kts`, exactly

**ServiceTag** (the current file gains the two blocks marked NEW; everything else is as it is today
**[code]**):

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// NEW — fail loudly before Gradle tries to configure a directory that is not there.
val lib = file("third_party/nfc-tag-core")
require(lib.resolve("settings.gradle.kts").isFile) {
    """
    nfc-tag-core is missing at third_party/nfc-tag-core.
    Clone with --recurse-submodules, or run: git submodule update --init --recursive
    """.trimIndent()
}
includeBuild(lib)   // NEW — composite build; substitution is automatic on matching coordinates

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}

rootProject.name = "ServiceTag"
include(":app", ":core")
```

**noteNFC** is the same three NEW lines, with `rootProject.name = "noteNFC"` and
`include(":app", ":core")` — the reconstruction keeps the `:app` + `:core` pair it already has at
`c84b881` **[code]**, because that is where the legacy key, codec and link policy live with their
pinned vectors (arch §2.3).

**Consumption**, in both apps' `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.loosecannon.nfc:nfc-tag-core-android:0.1.0")  // substituted by includeBuild
    // nfc-tag-core (pure JVM) arrives transitively as an `api` dependency of the Android module.
}
```

Gradle substitutes both coordinates with the included build's projects because the group and
artifact ids match what `nfc-tag-core`'s own build declares. The version string in the coordinate is
then **advisory**: what is actually built is whatever the submodule points at. That is why the
submodule pointer must be a tag and why §6.3's check exists.

### 6.2 How CI checks out

Both app workflows gain one line:

```yaml
      - uses: actions/checkout@v4
        with:
          submodules: true          # NEW  (recursive: true if the library ever gains its own)
```

`nfc-tag-core`'s own workflow needs nothing extra: it has no submodules. Because the library is a
composite build and not a published artifact, CI resolves it from the checkout — there is no
credential, no token and no repository URL in any build file, which keeps the "no `secrets.*`
interpolation anywhere" property the current CI already has (arch §4.2).

### 6.3 A version bump, and what fails loudly

A bump is three commits and is never a pointer nudge:

1. **In `nfc-tag-core`:** land the change, then `git tag -a v0.2.0 -m "…"` and push the tag.
2. **In each app:** `git -C third_party/nfc-tag-core fetch --tags && git -C third_party/nfc-tag-core checkout v0.2.0`,
   then commit the pointer with a message naming the tag and what changed for that app.
3. Both apps move independently, and a bump is complete only when **both** are green. A library
   change that only one app can absorb is a library design error.

| Failure | How it surfaces |
|---|---|
| Submodule directory absent (clone without `--recurse-submodules`) | the `require` in `settings.gradle.kts` fails **at configuration time**, before any task runs, with the exact command to fix it. Without it, `includeBuild` on a missing directory is a confusing Gradle error |
| Submodule present but the pointer is not a tagged commit | `tools/check-submodule-pin.sh` (**NEW**, run as a CI step and as a Gradle `check` dependency): `git -C third_party/nfc-tag-core describe --exact-match --tags HEAD` — a detached commit that is not exactly a tag fails the build (R5: pinned exact commit, no mutable HEAD) |
| Submodule dirty (local edits) | the same script: `git -C third_party/nfc-tag-core status --porcelain` must be empty. A dirty submodule means the app is building against code nobody else can reproduce |
| Submodule pointer moved without a commit in the app repo | the app's own `git status` shows the gitlink modified; CI checks out the recorded pointer and therefore builds the *old* library, so a forgotten pointer commit shows up as "my change did nothing" rather than as a silent divergence |
| The two apps on different library tags | intended and allowed *between* bumps; the acceptance gate for the coexistence phase requires both on the same tag (migration runbook §J gate 7) |

### 6.4 The JitPack alternative, in three lines

JitPack builds a tag of a public GitHub repository on demand and serves it as
`com.github.GonzRon:nfc-tag-core:v0.2.0` from `maven { url = uri("https://jitpack.io") }`, which
would replace the submodule with an ordinary version string. It is rejected for now because it adds
a network dependency and a third-party build service to an estate that currently resolves only
`google()` and `mavenCentral()`, and because `FAIL_ON_PROJECT_REPOS` would have to be relaxed.
It becomes the right answer the moment a third consumer exists or the library is wanted by someone
who cannot clone the submodule.

---

## 7. NFC semantic ownership after the split

Rows are tag kinds; columns are what each app does. "Nobody" means no component of either app is
offered the tap.

| Tag kind (wire type) | noteNFC manifest | ServiceTag manifest | noteNFC decoder returns | ServiceTag decoder returns | What the noteNFC user sees | What the ServiceTag user sees |
|---|---|---|---|---|---|---|
| **Legacy** `com.loosecannon.notenfc:md5_short`, payload 8 ASCII lower-case hex, no AAR (arch §2.9) | `NDEF_DISCOVERED`, `scheme=vnd.android.nfc`, `host=ext`, `path=/com.loosecannon.notenfc:md5_short` — **exact** path, kept permanently (R3; arch §5.9) | **no filter** (R3) | `Recognised` → the app's own 8-hex parse → a prefs lookup | `Foreign("tnf=4 type=com.loosecannon.notenfc:md5_short")` — only reachable inside reader mode | the note opens on a hit; on a miss the "Legacy tag" offer: *Rewrite* / *Bind as-is* / *Cancel* (arch §5.9) | nothing ambiently. Inside **Migrate tag** only: "this tag belongs to noteNFC", no action offered **[P11 — proposal]** |
| **noteNFC v1** `com.loosecannon.notenfc:tag` + AAR `com.loosecannon.notenfc`, body `01 00 ‖ 16 UUID bytes` | `NDEF_DISCOVERED`, exact `path=/com.loosecannon.notenfc:tag` | **no ambient filter.** Recognised **only inside the Migrate-tag tool**, in reader mode (R7) | `Recognised` → `VersionedUuidBody.Ok(uuid)` → prefs lookup | `Foreign(…)` ambiently by construction; inside Migrate tag, recognised as the pre-split type | the note opens, or "not in this phone's records" | nothing ambiently; inside Migrate tag, the rewrite offer (§D of the runbook) |
| **Pre-split ServiceTag asset/link tag** — *also* `com.loosecannon.notenfc:tag` with AAR `com.loosecannon.notenfc` | matches noteNFC's filter — **indistinguishable on the wire from the row above** | same as above | `Recognised` with a UUID noteNFC has never seen | `Foreign` ambiently; recognised inside Migrate tag | "This tag is not in this phone's records" — and it must **not** offer a silent rewrite **[P12 — proposal]**: noteNFC's wording names the possibility ("it may belong to another app on this phone") and any rewrite costs an explicit confirmation | the Migrate-tag tool claims it and rewrites it as `com.loosecannon.servicetag:tag` (R7) |
| **New ServiceTag** `com.loosecannon.servicetag:tag` + AAR `com.loosecannon.servicetag` | **no filter** | `NDEF_DISCOVERED`, exact `path=/com.loosecannon.servicetag:tag` | `Foreign("tnf=4 type=com.loosecannon.servicetag:tag")` — reachable only in reader mode | `Recognised` → v1 body → `nfc_tag` row → asset or link | inside reader mode only: on the write screen `Confirm(FOREIGN, …)` — nothing is written without one explicit confirmation, and the wording can name ServiceTag because `Foreign` carries the full type string; on an inspect screen, "this tag belongs to another app" (arch §6.5) | the asset opens, or a link launches with no UI at all (arch §5.11) |
| **Foreign / other** — a commercial URL sticker, an unrelated external type, a blank tag, unparseable NDEF | no matching filter; **no `TECH_DISCOVERED` catch-all** (deleted at `26ec9d0`; the reconstruction must not re-inherit the 2024 `nfc_tech_filter.xml`) | no matching filter; no catch-all (arch §5.1) | `Foreign` / `Empty` / unreadable → never a lookup, never a row, never a transaction (arch §5.12) | same | reader mode only: "Not a noteNFC tag / This tag holds something else", *Write a new tag over it* / *Cancel* | same shape with ServiceTag's words |

### 7.1 Who is launched when no filter matches

**The platform rule** **[platform-doc]**, as the archaeology records it (arch §5.3, §8.1 Q1, Q5):

1. `ACTION_NDEF_DISCOVERED` is tried first. With an AAR present, the platform tries the intent
   filter, and starts the AAR's package *"if the Activity that filters for the intent does not match
   the AAR, if multiple Activities can handle the intent, or if no Activity handles the intent."*
2. If more than one application can handle the intent, **the Activity Chooser is presented**.
3. If nothing matches `NDEF_DISCOVERED`, the platform tries `TECH_DISCOVERED`; neither product
   declares a tech filter, so that fails too.
4. If nothing filters for any of the intents, **the platform does nothing** — with two modern
   caveats: from Android 16 a web-link tag triggers `ACTION_VIEW` rather than
   `ACTION_NDEF_DISCOVERED`, and from Android 17 an "open link" notification appears. So the
   expected result for a commercial sticker is *a notification*, not silence.
5. If the AAR names a package that is not installed, the platform goes to Google Play for it.
   Neither `com.loosecannon.notenfc` nor `com.loosecannon.servicetag` is published there, so the
   expected outcome is a Play page for a listing that does not exist.
6. Reader mode **overrides** both AARs and the intent dispatch system, which is why each app's
   write/inspect screen can see the sibling's tag at all, and why ServiceTag's Migrate-tag tool can
   claim a type it declares no filter for.

**Every one of the six is to observe on-device** (arch §8.1 Q1 a/b/c, Q4, Q5), and the runbook's §D
observation matrix is where each is recorded. Two further platform behaviours are also to observe
on-device: **stopped-state dispatch**, where the documentation and this project's own device row
disagree — **[platform-doc]** says a force-stopped app gets no NFC dispatch, while
**[device-observed]** 1C row 16 recorded a dispatch to a force-stopped *and* data-cleared package,
with only the never-launched fresh install silent (arch §5.10, §8.1 Q2) — and the **Android 16+
per-app NFC allowlist**, where two installed apps mean two allowlist entries and neither app
currently calls `NfcAdapter.isTagIntentAllowed()`, so a denial is invisible inside the app
(arch §8.1 Q3).

### 7.2 The two rules that make this table safe

- **Never let both apps permanently claim the same ambient filter.** The only overlap that could
  exist is `md5_short`, and R3 removes it by dropping the type from ServiceTag entirely. The
  pre-split `com.loosecannon.notenfc:tag` type is claimed ambiently by noteNFC alone; ServiceTag
  reaches it only through reader mode inside an explicitly-entered tool (R7).
- **Tag mutation is explicit-intent only.** The ambient path *cannot* write, by construction:
  `NfcDispatchActivity` reads `EXTRA_NDEF_MESSAGES`, `EXTRA_TAG` and the data URI, and
  `NdefBridge.nfcTag()` — the only way to get a writable `Tag` out of the intent — is **never
  called** (arch §5.10). The Scan tab was retired precisely so that "reader mode is entered only
  from an intentional destination… so an ambient read can never drift into write mode"
  (arch §5.13, `838d6a3`/`53f9cac`). Both products keep that shape.

---

## 8. Signing

| | noteNFC | ServiceTag |
|---|---|---|
| Keystore | `~/.config/notenfc/notenfc-release.jks` — **unchanged, not rotated, not copied** | `~/.config/servicetag/servicetag-release.jks` — **new** |
| Properties | `~/.config/notenfc/keystore.properties` | `~/.config/servicetag/keystore.properties` |
| Keys in the properties file | `storeFile`, `storePassword`, `keyAlias`, `keyPassword` | the same four |
| Mode | 0600 on both files, and 0700 on the directory | the same |
| Read by | `app/build.gradle.kts` at configuration time, via `System.getProperty("user.home") + "/.config/<product>/keystore.properties"`; absent or empty → the release build type simply has no signing config **[code]** | the same mechanism with `servicetag` in the path |
| In the repository | nothing. No keystore, no properties file, no password, no alias | the same |
| In CI | nothing. Release signing is entirely local; CI builds `assembleDebug` only and interpolates no `secrets.*` (arch §4.2) | the same |
| Recorded | the certificate **SHA-256 fingerprint only**, in the checkpoint ledger, already captured for the 2.4/vc6 rollback artifact whose signer DN is `CN=noteNFC, O=GonzRon` (arch §4.5) | the new certificate's SHA-256 fingerprint, recorded once in the ServiceTag phase-evidence file. Fingerprints are public keys, not secrets; passwords and the keystore itself are never printed, committed, pasted or logged |

**Generating the ServiceTag key** — command shape, placeholders only, run by the owner in an
interactive shell so no secret reaches a transcript:

```bash
mkdir -p ~/.config/servicetag && chmod 700 ~/.config/servicetag

keytool -genkeypair -v \
  -keystore ~/.config/servicetag/servicetag-release.jks \
  -storetype PKCS12 \
  -alias <ALIAS> \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=ServiceTag, O=<ORG>"
# keytool prompts for the store password interactively. Do NOT pass -storepass or -keypass.

cat > ~/.config/servicetag/keystore.properties <<'PROPS'
storeFile=<ABSOLUTE PATH TO servicetag-release.jks>
storePassword=<STORE PASSWORD>
keyAlias=<ALIAS>
keyPassword=<KEY PASSWORD>
PROPS
chmod 600 ~/.config/servicetag/keystore.properties ~/.config/servicetag/servicetag-release.jks

# Record the fingerprint (public; safe to paste into the evidence file):
keytool -list -v -keystore ~/.config/servicetag/servicetag-release.jks -alias <ALIAS> | grep 'SHA256:'
```

**Backup.** Both `~/.config/notenfc/` and `~/.config/servicetag/` go, as an encrypted archive, to
storage that is not this workstation's disk and not any git repository — the same place the owner
keeps other irrecoverable secrets. The reason is written into the estate's own history: the 2024
release's signing key is **not available**, which is why an in-place upgrade of that install is
impossible and why Phase 1A had to mint a new identity at all (arch §2.10, closed issue #29). A lost
ServiceTag key means the same amputation for ServiceTag — no upgrade path for any installed build —
so the archive is verified restorable *before* the first signed ServiceTag release, not after.

---

## 9. CI per repository

All three workflows keep the shape the current one already has: `ubuntu-latest`,
`actions/checkout@v4`, `actions/setup-java@v4` with Temurin 17, `gradle/actions/setup-gradle@v4`,
`--console=plain`, and `actions/upload-artifact@v4` with `if: always()` for test results
(arch §4.2). Triggers stay `push` and `pull_request` on all branches. No `secrets.*` anywhere.

| Repository | Steps | Runs |
|---|---|---|
| **ServiceTag** | checkout **with `submodules: true`** → JDK 17 → `android-actions/setup-android@v3` (`platform-tools` only) → setup-gradle → `tools/check-submodule-pin.sh` → `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug` → upload `core/build/test-results` + `app/build/test-results` | the existing command, unchanged, plus the pin check |
| **noteNFC** | identical, with the same task list: the reconstruction inherits `:core:test`, `:app:testDebugUnitTest` and `:app:assembleDebug` from Phase 0 — the workflow, the tracked wrapper and the three JUnit 5 `:core` test classes all exist at `c84b881` and were **verified green** there (arch §2.3, §3) | unchanged from `c84b881` apart from `submodules: true` and the pin check |
| **nfc-tag-core** | checkout → JDK 17 → setup-android (`platform-tools`) → setup-gradle → `tools/forbidden-scan.sh` → `./gradlew :nfc-tag-core:test :nfc-tag-core-android:testDebugUnitTest :nfc-tag-core-android:assembleDebug` → upload both `build/test-results` trees | the forbidden-dependency scan is a **first-class gate**, placed before the build so a violation is the first thing a reader sees |

**No instrumented step in any of the three.** The emulator suites — the app device-proof tests and
the library's `androidTest` adapter tests — stay local, run against the emulator, per the owner's
rules and the existing CI's deliberate exclusion (arch §4.2). The physical phone runs no
instrumented suite at all: it holds the owner's real data, and an instrumented run would wipe it.

**Clean-checkout proof** is a per-repository acceptance, not a CI trick: for each of the three,
`git clone --recurse-submodules <url> <tmp> && cd <tmp> && ./gradlew <that repo's CI task list>`
from a directory that has never held the project, and — for the two apps — once from a second
workstation, so "it builds" never means "it builds where the caches are".

---

## 10. Roadmap

### 10.1 ServiceTag

Through **Phase 4A** and ready to begin **Phase 3**. Landed: Phase 0 (foundation), 1A (durable
identity), 1B (NFC identity), 1C (Compose shell), 2A (maintenance journal), 2B-1 (editors), 2B-2
(physical asset model), 4A (attachments) — arch §3. Next is **Phase 3 (scheduling)**: designed in
D5 and D7, not implemented. **Phase 3R** (automatic versioned backup) is designed and deliberately
off the critical path, after Phase 3 (`94d4286`, `0f3ed3a`). Phase 7 already carries the
`targetSdk 37` + `DISPATCH_NFC_MESSAGE` item, which is the one NFC obligation the split does not
discharge (arch §5.1). The split itself is the convergence operation D7 always said it would be —
"deferred until ServiceTag is functionally mature and before its first real deployment or permanent
tag rollout" — so Phase 3 begins *after* the split completes, not alongside it.

### 10.2 noteNFC

The reconstruction's roadmap starts with the two issues that were always noteNFC's:

- **#6** "[MVP] Generalize external note/deep-link support beyond Joplin" — *"Preserve noteNFC's
  original purpose while making external-link handling generic."* This is where the
  `contains("joplin")` gate finally becomes a scheme allowlist, and where arch §8.2 Q8 gets
  answered: keep the 2024 gate as the compatibility path (so re-sharing an old note reproduces the
  same MD5 key and the same tag still resolves) or replace it outright and accept that the key
  changes. **Unresolved here, deliberately** — it is a product decision inside noteNFC's own
  backlog, not a split decision.
- **#36** "[FUTURE noteNFC] First-class deep-link support for Joplin, Obsidian, Logseq, Evernote,
  Notion, OneNote and Todoist" — whose body is the explicit ownership statement that started this
  whole operation: *"This issue belongs to the future standalone noteNFC product, not ServiceTag…
  move/transfer this issue to the standalone noteNFC repository."* (arch §4.7).

Also inherited as open questions, not as work items: arch §8.2 Q7 (whether a one-time importer for
the on-device `noteNFCURLs` key→link map is wanted, and whether it reads the prefs XML from a device
backup or needs an export from the phone first — noting that the modern line has no importer and
that D6 §3's automatic prefs migration was explicitly dropped) and Q10 (whether a debug source set
comes along).

### 10.3 nfc-tag-core versioning

- **Semantic versioning on git tags**, `v<major>.<minor>.<patch>`, starting at **`v0.1.0`** — the
  extraction commit, tagged the moment both apps are green against it. `0.x` while the API is still
  moving; `1.0.0` when a third consumer or an external user appears.
- **The tag is the unit of consumption.** Each app's submodule pointer is always a commit that
  `git describe --exact-match --tags` resolves (§6.3). There is no mutable-branch consumption and no
  snapshot.
- **What each bump means.** *Patch*: no API change; a fix or a test. *Minor*: additive API, or a
  behaviour change that both apps want. *Major*: a removal or a signature change. A change that only
  one app wants is not a library change — it is a sign the boundary is in the wrong place, and the
  fix is to move policy back into that app.
- **Every bump updates the invariant list (§4.3) or explains why it does not**, because the
  invariants, not the type signatures, are what the two apps actually depend on.
- **No release artifacts.** `includeBuild` means there is nothing to attach to a tag; the tag *is*
  the release.

---

## 11. Decisions beyond R1–R9 — proposals awaiting ratification

| # | Proposal | Where |
|---|---|---|
| **P1** | `nfc-tag-core`'s default branch is `main`; the two app repos keep `master` because their history does | §2 |
| **P2** | The reconstruction normalises its Kotlin package root from the historical mixed-case `com.looseCannon.noteNFC` to `com.loosecannon.notenfc` in its rename commit, matching the `applicationId` R1 fixes and removing the `:app`/`:core` case asymmetry that exists at `c84b881` | §3 |
| **P3** | ServiceTag's theme is `Theme.ServiceTag`, its `Application` subclass is `ServiceTagApp`, and the nav-root composable currently also named `NoteNfcApp` becomes `ServiceTagRoot` — resolving the naming collision the archaeology flagged | §3 |
| **P4** | noteNFC declares **no** `notenfc://` `VIEW` filter at reconstruction; the scheme is reserved to it and stays unused until #6/#36 need it | §3 |
| **P5** | ServiceTag's `shared_prefs` file becomes `servicetag`; cosmetic, prefs are package-scoped either way | §3 |
| **P6** | Library coordinates `com.loosecannon.nfc:nfc-tag-core{,-android}`, Kotlin root `com.loosecannon.nfc.tagcore` | §3.1 |
| **P7** | The pure-JVM module takes **no** runtime dependency at all (not even coroutines); coroutines live on the Android module, where `TagWriteSession` needs a dispatcher | §4.1 |
| **P8** | The `NdefFormatable` capacity gap is closed by deferring the comparison to the second tap, where `Ndef.maxSize` first becomes readable, and by exposing `serialisedSize()` so a consumer can state the requirement before the first tap. R4 mandates *a* fix; this is the mechanism | §4.3 inv. 7 |
| **P9** | The forbidden-word list in §4.4 is reconstructed from the binding forbidden-knowledge enumeration, because the brief's own text is not in this worktree. It needs a word-for-word check against the brief before the scan becomes a gate | §4.4 |
| **P10** | Rename the library's `androidTest` fixture directory so the word "asset" never appears, rather than allow-listing a Gradle convention | §4.4 |
| **P11** | Inside ServiceTag's Migrate-tag tool, a legacy `md5_short` tag is named ("this tag belongs to noteNFC") and offered **no** action — ServiceTag never writes or rewrites a legacy record | §7 |
| **P12** | Because a pre-split ServiceTag tag is **byte-identical** to a post-split noteNFC tag, noteNFC's "not in this phone's records" wording must name the possibility that the tag belongs to another app, and any rewrite costs an explicit confirmation. The structural mitigation is the phone ordering (migrate every tag *before* narrow noteNFC is installed) — see the runbook §C/§D | §7 |

---

## 12. Definition of done for this document's subject matter

The target architecture is realised when, and only when: all three repositories build from a clean
`--recurse-submodules` clone on two different machines and are green in their own CI; both apps
consume the *same* pinned library tag; the forbidden-dependency scan passes with an allow file whose
every entry has a reason; every invariant in §4.3 has a test in the library and, where it is about a
consumer, a test in each consumer — including the sibling-isolation test in both directions; the
identity tables in §3 are true of the built APKs and not just of the source; the coexistence matrix
in §7 has been observed on the phone with every "to observe on-device" row recorded as evidence; and
the owner's real data has been proved intact by stable id in ServiceTag, per the migration runbook.
