# Product-split target architecture — ServiceTag / NoteTag / nfc-tag-core

The shape the estate is being moved *to*. Written for an engineer who has never opened this
repository: every module, package, file and command named below either exists at the pre-split
checkpoint `ac523d7` (cited to the archaeology, which cites the code) or is marked **NEW**.

- **Evidence base:** `docs/architecture/product-split-archaeology.md` (cited as "arch §n"), as
  corrected by the archaeology review of 2026-09-16 (cited as "review correction *n*" /
  "review addition *n*"). Where a review correction changes a fact, the correction is cited rather
  than the section, so nothing here depends on an edit that has not landed.
- **Binding:** the owner's brief at `.superpowers/split/brief.md` (cited as "§n"), and the owner's
  rulings **O1–O15** plus the archaeology-gate corrections **C1–C9** in
  `.superpowers/split/ledger.md`. O1–O15 supersede R1, R3, R5, R6, R7 and the legacy parts of R9;
  §16–§18, the `md5_short`/foreign-noteNFC rows of §23 and rows 1/4/5 of §25 are withdrawn.
- **Runbook:** `docs/architecture/product-split-migration.md` is the ordered how; this document is
  the what. `docs/architecture/product-split-evidence.md` is where results land (§29).
- **Claim tags** follow the archaeology: **[code]**, **[JVM-proven]**, **[device-observed]**,
  **[platform-doc]**, **[unobserved]**. Platform behaviour not yet seen on hardware is marked **to
  observe on-device**; per C9 and the gate verdict, device questions Q1–Q5 are **not architecture
  blockers** — they are final coexistence gates against the two finished APKs.
- **Proposals.** Decisions this design had to make beyond the brief and O1–O15 were tracked as
  **[P*n*]** proposals while drafting; as of `5d8ca77` every one is ratified and §11 is the closed
  register (none open).
- **Hygiene.** No home paths beyond `~`, no device serials, no phone model or codename, no phone
  folder names, no key material, no fingerprints reproduced.

**One naming rule, applied throughout.** *noteNFC* is a **historical name only**: the 2023–2024
Evernote/Joplin utility and the 2026 pre-split application that grew out of it. The narrow product
going forward is **NoteTag** (O1). No repository, applicationId, record type, scheme or label named
noteNFC exists after the split, and nothing reuses `com.loosecannon.notenfc` (O1, §15 step 8).

---

## 1. The picture

```text
                      ┌───────────────────────────────┐
                      │        nfc-tag-core           │  its own repo, its own root build
                      │   nfc-core    (pure Kotlin/JVM)│  envelope · framing · bytes
                      │   Kotlin stdlib only, no deps  │  (review addition 5)
                      │   nfc-android (NFC adapter)    │  android.nfc, minSdk 26
                      └───────────────┬───────────────┘
        pinned Git submodule at libs/nfc-tag-core, modules included as subprojects (O15)
              ┌───────────────────────┴───────────────────────┐
              │                                              │
   ┌──────────▼──────────┐                        ┌───────────▼─────────┐
   │      NoteTag        │                        │      ServiceTag     │
   │ com.loosecannon.    │                        │ com.loosecannon.    │
   │        notetag      │                        │        servicetag   │
   │ share → write → tap │                        │ assets · journal ·  │
   │ 3 tag kinds, one    │                        │ definitions ·       │
   │ external record     │                        │ profiles ·          │
   │ minimal local store │                        │ attachments · backup│
   └─────────────────────┘                        └─────────────────────┘
```

Both apps hold the library at the *same* pinned commit; neither depends on the other, and **the
library depends on neither** (O15). The library knows about NDEF records, tag hardware, capacity and
write safety. It does not know what a tag *means* to anybody (§4, O5).

**NoteTag is** the small utility that attaches a note or a useful link to a physical NFC tag (O1):
receive a shared note or URL, write one typed external record, and on a later tap open that note or
page. It descends from the 2024 product's *intent* — `c84b881` stays the documented semantic endpoint
for provenance, intent and the share→write→tap evidence — but the application itself is a
**substantial rewrite** on modern engineering and nfc-tag-core; historical technical debt is not
preserved for authenticity (O6). **NoteTag is not** an asset tracker: no journal, no measurements,
no profiles, no schedules, no reminders, no attachments, no backup format (arch §2.5). It carries
**no legacy compatibility at all** (O2): no `md5_short`, no old private lookup map, no `notenfc://`,
no migration wizard, no compatibility filters.

**ServiceTag is** the physical-asset service and maintenance product this repository's master branch
already is at `ac523d7` (arch §3, §4): eleven Room tables, an event journal with typed measurements
and consumable usage, measurement definitions and event profiles, the full physical-asset model,
attachments in a SAF-tree managed store, a two-archive backup set at data format 5 / artifacts
format 1, a single-activity Compose/Nav3 shell on the Apollo Service Binder theme. **Schedules,
reminders and scan-time context are FUTURE work — Phase 3, designed in D5/D7 and not implemented**
(C1, review correction 1). **ServiceTag is not** a note utility: it drops `md5_short` and
`notenfc://` entirely (O3), and the pre-split type `com.loosecannon.notenfc:tag` is historical.

**nfc-tag-core is** the mechanism both need and neither should own twice, extracted conservatively:
the external-record envelope with identity as a parameter, the single `android.nfc`↔bytes bridge, the
reader-mode lifecycle, capability and capacity inspection, the safe writer, structural read-back
verification, and a generic overwrite decision returning reason tokens. **nfc-tag-core is not**
allowed to know the words in §4.4, and nothing enters it on resemblance — only behaviour proven by
two real consumers (§4, C5, O5).

---

## 2. Repository identities

| | **ServiceTag** | **NoteTag** | **nfc-tag-core** |
|---|---|---|---|
| Remote | `https://github.com/GonzRon/ServiceTag.git` | `https://github.com/GonzRon/NoteTag.git` | `https://github.com/GonzRon/nfc-tag-core.git` |
| Default branch | `master` (unchanged) | `master` | `main` (ratified P1) — a new repo has no history to inherit a branch name from |
| Visibility | PUBLIC, unchanged | **PUBLIC** (controller ruling): the same history is already public in this repository, so branching it publishes nothing new. What it does publish is stated below | PUBLIC |
| How it comes into being | **Rename** of the current `GonzRon/noteNFC` (§9, O7): same repository, same history, same issues, same design record. The rename is a settings PATCH the active token can perform; it **cannot delete** a repository (arch §4.3), so no step may require one | **New** repository, then a branch taken from **`c84b881`** in a local clone of the pre-split history — exact ancestry, no rewrite, no graft, **no second history rewrite** (C6, arch §2.7) | **New** repository seeded from files extracted out of `ac523d7`, with the provenance table in §4.6 copied into its `README.md`. "Correctness of the neutral extraction matters more" than carrying history (§9) |
| History it carries | everything: 175 commits, 8 merges, the whole Evernote→Joplin→Phase-0→Phase-4A line (arch §2.2, §3, §9 D1) | `5fb6aed`…`c84b881` — 30 commits, **zero merge commits**, independently confirmed by the archaeology review | **none**; a fresh root commit. Ancestry is documented, not inherited — §4.6 |
| Deleted at the new branch tip | — | the inherited ServiceTag design package (`docs/design/`, `docs/superpowers/` — 70 files at `c84b881`) and the tracked release APK `app/release/app-release.apk`, whose blob is byte-identical at `3a3c69a` and `c84b881` and **is the 2024 shipped artifact** (review addition 8) | — |
| What that does **not** do | — | **it does not scrub any of it from history**, and no second rewrite will be done (C6). Still reachable in the public history: the ServiceTag design documents; the owner's GitHub handle and personal issue URLs in `docs/design/issues/applied.md` and one superpowers plan; the release-signer certificate digests in `docs/design/phase-0-evidence.md`; and the author name plus personal email in the commit metadata of **all 30 preserved commits** (review correction 10). None of it is a secret — a public handle and public-key fingerprints — and all of it is *already* public here (arch §2.8) | — |
| Recovery refs | tag `pre-split-checkpoint` and branch `pre-split-master`, both at `ac523d7`, both pushed. **Not deleted until all three repositories are green** (§27 step 12, §35) | inherits none | none |
| Releases | keeps the 2023 draft as history (§28; arch §2.10, §4.8): untagged, unanchored, Evernote-era, no assets. Not published, not deleted | none at creation; gets `notetag-v1.0` when it passes its gate (§28: "never claim a maintenance release was a narrow release") | `nfc-tag-core-v0.1.0` at extraction |
| Issues | 34 of 36 stay (§10, O8, C7) | receives **#6** and **#36**, retitled under the NoteTag name (§10, O8) | none at creation |

**Why the rename rather than a fresh ServiceTag repository.** §9 is explicit — *"Do not shallow-copy
ServiceTag into a new unrelated repo."* The continuing line holds 145 commits of ServiceTag work, 70
design documents, 34 issues, the CI history and both recovery refs. Moving *that* costs everything;
branching the 30-commit narrow line costs nothing. This also settles arch §8.2 Q12: the
`pre-split-checkpoint` tag and the 2023 draft release travel with ServiceTag, because they describe
ServiceTag's state and the Evernote era respectively — neither is a narrow-product anchor.

**CI provenance caveat.** `c84b881` itself never ran on a runner: the green runs recorded for Phase 0
belong to its **pre-rewrite twins** `ccdb9d3`/`12e2c09`, and the 2026-09-14 `git filter-repo` rewrite
changed every SHA (review correction 8). A repository branched at `c84b881` therefore **must re-run
CI from scratch** and may not inherit a green claim. The archaeology's CI-failure attribution is
likewise corrected: `19213dd` and `1b1bd3c` both failed, and master went green at `73fc463` (review
correction 9).

---

## 3. Android identities

Every row is a distinct decision: the string `com.loosecannon.notenfc` occurs **2 028 times across
236 files** (arch §4.6, §9 D4), so this is a deliberate inventory-and-migrate exercise, not a blind
search-and-replace (§11).

| Identity | NoteTag | ServiceTag | Source of truth |
|---|---|---|---|
| `applicationId` | `com.loosecannon.notetag` | `com.loosecannon.servicetag` | O1 / O3, §11 |
| `namespace` | `com.loosecannon.notetag` | `com.loosecannon.servicetag` | `app/build.gradle.kts` **[code]** |
| Kotlin package root | `com.loosecannon.notetag` | `com.loosecannon.servicetag` | O1 / O3. The historical mixed-case `com.looseCannon.noteNFC` root and the `:app`/`:core` case asymmetry at `c84b881` (arch §2.10) go with the rewrite (O6) |
| `rootProject.name` | `NoteTag` | `ServiceTag` (was `noteNFC`) | review correction 11 — an omission from the archaeology's identity inventory |
| App label | `NoteTag` | `ServiceTag` | O1 / O3 |
| **Launcher icon** | its own | **a new ServiceTag icon**, replacing the inherited `ic_launcher` mipmap set | **review addition 3: a distinct launcher icon AND label per product is a coexistence requirement**, not cosmetics — two apps that look identical on the launcher are a usability failure and make every device observation and every NFC-allowlist entry ambiguous |
| Theme | NoteTag's own window shell | `Theme.ServiceTag` (was `Theme.NoteNfc`) | ratified P3 |
| `Application` class | NoteTag's own, if it needs one | `ServiceTagApp`; the nav-root composable also named `NoteNfcApp` becomes `ServiceTagRoot`, resolving the two-classes-one-name collision (arch §4.6) | ratified P3 |
| Manifest `android:name` **FQN literals** | NoteTag's own | **five literals that do not follow `namespace` and must be edited by hand** (review correction 4): `com.loosecannon.notenfc.NoteNfcApp` (application), `…MainActivity`, `…ShareActivity`, `…nfc.NfcDispatchActivity`, plus `…debug.DebugBackupActivity` in the debug manifest | arch §5.1 **[code]** |
| FileProvider authority | only if NoteTag ever needs one (it has no attachments) | `com.loosecannon.servicetag.files` — **derived**, no literal to change: the manifest uses `${applicationId}.files` and `AppGraph` uses `BuildConfig.APPLICATION_ID` (arch §4.6, §7.7 item 6) | **[code]** |
| Persisted SAF tree grant | n/a — NoteTag has no attachment store | **ServiceTag must take its own.** A persisted grant is scoped to the *calling* application, so a different `applicationId` — a genuinely different app to the OS, even from the same source with the same signing key — holds none of another app's grants. **[platform-doc]**, not observed here (arch §7.4 records it as such, and `archaeology-data.md` notes it was written from public API documentation rather than a byte-for-byte fetch). It is the load-bearing reason for §15's ordering and for C.9's "uninstall last", so the runbook adds an **emulator observation** before C.9 rather than resting on the document | §13, arch §7.4 |
| Deep-link scheme | `notetag` — **reserved; no `VIEW` filter declared at reconstruction** (ratified P4). Held for #6/#36 | `servicetag`, hosts `asset`, `link`, `tag` | O1 / O3 |
| NDEF external type | `com.loosecannon.notetag:tag` | `com.loosecannon.servicetag:tag` | O1 / O3 |
| Decode-only types | **none** | **none** | O2 / O3. Neither product understands `md5_short`. A legacy decoder was permitted only if "essentially free and harmless" (O2); it is not — it would need a manifest filter, a payload branch and a UI state, which is architecture for a dead format (O11) |
| AAR | **none by default.** AAR support is an *optional builder* in nfc-tag-core that a consumer may append; NoteTag adds one only if the coexistence dispatch spike shows a concrete benefit (O13) | `com.loosecannon.servicetag` — **kept by default** pending the same spike, because an AAR is what today's build always writes and 1B row 5 **[device-observed]** the platform matching it to start the dispatch activity from a killed process (arch §5.3). O13 makes it contingent; the conservative default for an already-proven behaviour is to keep it | O13; ratified P21 |
| Room database name | **none** — NoteTag has a minimal local store, not Room (O14) | `servicetag.db` (was the literal `notenfc.db` in `AppGraph.DB_NAME`, not derived — arch §4.6, §7.7 item 4) | O14; ratified P5 |
| Room schema export dir | n/a | `app/schemas/com.loosecannon.servicetag.data.room.AppDatabase/`, `1.json`…`5.json` re-exported under the new FQN (arch §4.5, §7.7 item 5) | derived from the `AppDatabase` FQN |
| Export file-name prefixes | n/a | `ServiceTag-data-<stamp>.zip` / `ServiceTag-artifacts-<stamp>.zip`. **The importer never reads a file name** (arch §7.3), so the preserved `noteNFC-*` files import unchanged | ratified P5 |
| `shared_prefs` file | NoteTag's own | `servicetag` | ratified P5; package-scoped either way (arch §7.7 item 9) |
| Test package identity | `com.loosecannon.notetag.test` | `com.loosecannon.servicetag.test` (AGP default; no `applicationIdSuffix`) | arch §4.6 |
| `testInstrumentationRunner` | `androidx.test.runner.AndroidJUnitRunner` | same | **[code]** |
| `minSdk` / `targetSdk` / `compileSdk` | 26 / 36 / 37 | 26 / 36 / 37 | **[code]**, arch §4.5 |
| Version display | NoteTag's own | `BuildConfig.VERSION_NAME`, shown in `ui/settings/SettingsScreen.kt:241` as well as written into `BackupManifest.appVersion` at `AppGraph.kt:151` — the settings use was an omission from the archaeology inventory (review correction 11) | **[code]** |
| Notification channels | none yet | none yet — no `NotificationChannel` anywhere; issue #21's local reminders are Phase 3 (arch §4.6). Listed because §11 requires channels to be migrated deliberately *when they exist* | arch §4.6 |
| Signing key location | `~/.config/notenfc/keystore.properties` → the **existing noteNFC key, unchanged, never rotated or replaced** (§12). The key outlives the product name | `~/.config/servicetag/keystore.properties`, same four keys, same `user.home` mechanism | §12, §8 |

### 3.1 nfc-tag-core module names and layout (O15)

| | Value |
|---|---|
| Repository | `GonzRon/nfc-tag-core`, its **own root build**, so it builds and tests standalone |
| Directories in that repo | `nfc-core/` (pure Kotlin/JVM, **zero third-party, application or framework runtime dependencies — the Kotlin stdlib only**, which the `kotlin.jvm` plugin adds), `nfc-android/` (Android NFC adapter) |
| Submodule path inside each app | `libs/nfc-tag-core/` |
| Gradle project paths **as each app sees them** | `:nfc-core`, `:nfc-android` — the same paths in both apps, because the modules are included as ordinary subprojects |
| Plugins applied | `:nfc-core` → `kotlin.jvm`. `:nfc-android` → **`com.android.library` and nothing else**: AGP 9.4 carries Kotlin built in, so no `kotlin.android` plugin is applied and `jvmTarget` is set inside `android { kotlin { compilerOptions { … } } }`, exactly as `app/build.gradle.kts` already does **[code]**. The catalog alias `android-library` must be **added** to every consumer's catalog and to the library's own — no catalog has it today (§6.1) |
| Maven coordinates | **none.** Nothing is published: no Maven, no publication, no credentials, no composite build (O15). The project paths are the whole interface |
| Kotlin package roots | `com.loosecannon.nfc.tagcore`, `com.loosecannon.nfc.tagcore.android` — ratified P6 |
| Android library `namespace` | `com.loosecannon.nfc.tagcore.android` |
| Version | the git tag the submodule is pinned at; `nfc-tag-core-v0.1.0` at extraction (§10.3) |
| Direction of dependency | `nfc-android` → `nfc-core`, and nothing else. **No dependency from the library back into either app**, ever (O15) |

---

## 4. The nfc-tag-core boundary

**The rule, twice stated.** §4: *"Do not extract abstractions merely because two source files look
similar. Extract only behaviour proven by two real consumers."* C5: extract the unquestionable seam
now, build NoteTag against it, promote the rest only if both genuinely need it. §4.7 names exactly
what is deferred and why.

### 4.1 Module layout

```text
nfc-tag-core/                     (its own repository, its own root build — O15)
├── settings.gradle.kts           rootProject.name = "nfc-tag-core"
│                                 include(":nfc-core", ":nfc-android")
│                                 Read only when the library IS the root build. Gradle reads the
│                                 root settings file alone, so an app that includes these modules
│                                 as subprojects never sees this file.
├── build.gradle.kts              plugins declared `apply false`
├── gradle/libs.versions.toml      standalone catalog. The alias NAMES must match the two apps'
│                                 catalogs; three independent CI greens are what prove they do.
├── gradlew + gradle/wrapper/      so the library builds from a clean clone on its own
├── nfc-core/                      PURE KOTLIN/JVM. plugins: kotlin.jvm. jvmToolchain(17).
│   │                             *** NO third-party, application or framework runtime
│   │                             dependency: the Kotlin stdlib only (added by the plugin) *** —
│   │                             not coroutines, not serialization. It imports only
│   │                             java.nio.ByteBuffer and java.util.UUID (review addition 5).
│   │                             tests: JUnit 5 + kotlin.test only.
│   └── src/{main,test}/kotlin/com/loosecannon/nfc/tagcore/
├── nfc-android/                   ANDROID LIBRARY. plugin: `com.android.library` ONLY.
│   │                             AGP 9.4 carries Kotlin built in, so NO `kotlin.android` plugin is
│   │                             applied anywhere; Kotlin is configured inside the android block,
│   │                             exactly as `app/build.gradle.kts` already does:
│   │                               android { kotlin { compilerOptions {
│   │                                 jvmTarget.set(JvmTarget.JVM_17) } } }
│   │                             minSdk 26, compileSdk 37. NO Compose, NO Room, NO KSP, NO
│   │                             serialization, NO lifecycle, NO Material.
│   │                             deps: api(project(":nfc-core")) and nothing else while
│   │                             TagWriteSession stays deferred (§4.7) — the blocking adapter
│   │                             needs no dispatcher of its own.
│   └── src/{main,test,androidTest}/kotlin/com/loosecannon/nfc/tagcore/android/
└── tools/forbidden-scan.sh + forbidden-scan.allow      (§4.4)
```

The pure-JVM half is why this extraction is cheap rather than speculative: `:core` is **already** an
Android-free Kotlin JVM module with no Room or Compose reach (arch §4.4 — "the single most important
structural fact for the split"), and the NFC subset inside it is dependency-free.

### 4.2 Public API — what is extracted now

```kotlin
// ---------- nfc-core (pure Kotlin/JVM) ----------
package com.loosecannon.nfc.tagcore

/**
 * What identifies one product's tags on the wire. These strings are the ONLY product knowledge the
 * library holds, and the caller supplies them.
 *
 * @param externalDomain NFC Forum external-type domain. Lower-case: `NdefRecord.createExternal`
 *   lower-cases both halves before joining, so a mixed-case domain would not match the bytes
 *   actually on the tag (arch §2.9).
 * @param typeName external-type name, e.g. "tag".
 * @param aarPackage the applicationId to pin with an Application Record, or **null for no AAR**.
 *   Kept a separate parameter from [externalDomain] even when the two strings are equal, because
 *   they are different things: an NFC Forum domain and an Android package name (C9, arch §6.3).
 */
data class TagIdentity(
    val externalDomain: String,
    val typeName: String,
    val aarPackage: String? = null,
) {
    val externalType: String = "$externalDomain:$typeName"
    init { require(externalType == externalType.lowercase()); require(externalDomain.isNotBlank() && typeName.isNotBlank()) }
}

/** Android-free view of one NDEF record; value equality over tnf + type bytes + payload bytes. */
data class NdefRecordData(val tnf: Int, val type: ByteArray, val payload: ByteArray) { /* contentEquals */ }

/** What the envelope made of a message, before any product body parse. */
sealed interface TagContent {
    /** The first record is ours: same TNF, same external type. [body] is the raw payload. */
    data class Recognised(val body: ByteArray) : TagContent
    /** Not ours. [description] keeps the full offending type string so the caller can name it. */
    data class Foreign(val description: String) : TagContent
    data object Empty : TagContent
}

object NdefEnvelope {
    const val TNF_EXTERNAL_TYPE: Int = 0x04
    /** Platform constant, not identity. */
    const val AAR_TYPE: String = "android.com:pkg"

    /**
     * The message for one tag: the external record, plus an AAR appended **only** when
     * `identity.aarPackage != null`. One record is the default (O13).
     */
    fun encode(identity: TagIdentity, body: ByteArray): List<NdefRecordData>

    /**
     * First record of the message only, as the platform does. TNF gate, then exact-type gate, and
     * only then is the body handed back UNPARSED (§4.3 invariant 1).
     */
    fun decode(identity: TagIdentity, records: List<NdefRecordData>): TagContent

    /** The optional AAR builder a consumer may append itself. Byte-identical to the platform call. */
    fun applicationRecord(packageName: String): NdefRecordData
}

/**
 * Generic byte↔UUID conversion and canonical-form checking. Application-neutral; named by §4 as a
 * likely JVM-boundary helper and by §22 as something to prove. NOT a payload layout: the
 * `version|flags|UUID` *scheme* stays in the app that uses it (§4.7).
 */
object UuidBytes {
    const val LENGTH: Int = 16
    fun toBytes(uuid: java.util.UUID): ByteArray                  // big-endian msb ‖ lsb
    fun fromBytes(bytes: ByteArray, offset: Int = 0): java.util.UUID
    /** Refuses a non-UUID and a UUID that is not the canonical lower-case form. */
    fun requireCanonical(value: String): java.util.UUID
}

/** What the CALLER's classifier made of what the tag already holds. No product words. */
sealed interface ExistingContent {
    data object Empty : ExistingContent
    /** Our external type, body understood. */
    data class Ours(val detail: String) : ExistingContent
    /** Our external type, body this build will not parse (a version or kind above ours). */
    data class OursUnsupported(val detail: String) : ExistingContent
    data class Foreign(val description: String) : ExistingContent
    data class Unreadable(val reason: String) : ExistingContent
}

enum class OverwriteReason { EMPTY_TAG, SAME_TAG, OTHER_TAG_SAME_PRODUCT, SAME_PRODUCT_UNSUPPORTED, FOREIGN, UNREADABLE }

sealed interface OverwriteDecision {
    data object Proceed : OverwriteDecision
    /** [reason] is a token; [detail] is raw evidence (a type string, an id). NEVER a sentence. */
    data class Confirm(val reason: OverwriteReason, val detail: String) : OverwriteDecision
}

/**
 * Read-before-write: write without asking ONLY over an empty tag, or over the very identity being
 * written (a retry). Everything else costs exactly one confirmation. The rule is identical for both
 * products; only the sentences differ, and the library builds none.
 */
object OverwritePolicy { fun decide(existing: ExistingContent, isSameIdentity: Boolean): OverwriteDecision }

// ---------- nfc-android (Android NFC adapter) ----------
package com.loosecannon.nfc.tagcore.android

/** The only place `android.nfc` types meet the pure-bytes codec. */
object NdefBridge {
    fun NdefMessage?.toRecordData(): List<NdefRecordData>
    fun List<NdefRecordData>.toNdefMessage(): NdefMessage         // record id always ByteArray(0)
    fun ByteArray?.toHexOrNull(): String?
    /** First message of `EXTRA_NDEF_MESSAGES`, with the SDK-33 typed/untyped split. */
    fun Intent.ndefRecords(): List<NdefRecordData>?
    fun Intent.nfcTag(): Tag?
    /**
     * Size of the EXACT encoded NDEF message — `toNdefMessage().toByteArray().size` — which is the
     * `needed` of every capacity check, compared directly against `Ndef.getMaxSize()` (itself a
     * maximum *message* size). No Type-2 TLV header or terminator is added: that layer is
     * Android's and the tag's (§4.3 invariant 7).
     */
    fun List<NdefRecordData>.serialisedSize(): Int
}

/**
 * Reader mode: callback-based, no PendingIntent, no activity relaunch. **The platform's NDEF check
 * is deliberately left ON and `FLAG_READER_SKIP_NDEF_CHECK` is NEVER set** — with it, `Ndef.get()`
 * returns null and there is nothing to write to (device-proven negatively at `e2cf1d0`).
 *
 * @param onTag runs on a platform binder/background thread, never the main thread: blocking
 *   [TagWriter] calls are allowed straight from it, any UI update must be posted.
 */
class NfcReaderModeSession(activity: Activity, onTag: (Tag) -> Unit) {
    val available: Boolean; val enabled: Boolean
    fun start(); fun stop()
}

class TagInspection(
    val uid: String?,
    val existingRecords: List<NdefRecordData>,
    /** `Ndef.maxSize`, or -1 for a tag that still needs formatting: capacity unknown until then. */
    val maxSize: Int,
    val writable: Boolean,
    val needsFormat: Boolean,
    val canLock: Boolean,
    /** Set when the tag's NDEF could not be parsed; the tag is still overwritable. */
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
 * [write] folds tag I/O into [WriteResult.Failed]; [inspect] lets it propagate — on purpose.
 */
object TagWriter {
    /** @throws java.io.IOException (incl. `TagLostException`). Null when neither Ndef nor NdefFormatable. */
    fun inspect(tag: Tag): TagInspection?

    /**
     * The `Ndef` path only: capacity-check [records] against `Ndef.maxSize`, write, read back and
     * compare structurally, then lock if asked. A tag that still needs formatting is NOT written
     * here — see [format].
     */
    fun write(tag: Tag, records: List<NdefRecordData>, lock: Boolean): WriteResult

    /**
     * Formats an `NdefFormatable` tag and NOTHING else: calls `format(null)`, leaving the tag
     * **empty and unlocked**.
     *
     * The platform's `NdefFormatable.format(firstMessage)` formats *and* writes that message in one
     * operation, and there is no `Ndef` — and therefore no `maxSize` — until after it has run. So
     * passing the intended message here would let a too-large message fail inside the format call,
     * before any capacity check could exist. Instead the caller formats with no payload, and the
     * next tap delivers the tag as `Ndef`, where [write] can measure `maxSize` first (§4.3
     * invariant 7).
     */
    fun format(tag: Tag): WriteResult

    /** Permanent. Call only after a verified read-back; the returned value is the proof it took. */
    fun lock(tag: Tag): Boolean
}

/** The seam that keeps `android.nfc.Tag` — unconstructible in a JVM test — out of decision logic. */
interface TagHandle { val uid: String? }
class NfcTagHandle(val tag: Tag) : TagHandle
interface TagIo {
    fun inspect(tag: TagHandle): TagInspection?
    fun write(tag: TagHandle, records: List<NdefRecordData>, lock: Boolean): WriteResult
    fun lock(tag: TagHandle): Boolean
}
object RealTagIo : TagIo   // the only place a Tag comes back out of a handle
```

### 4.3 Invariants

Invariants 1–9 and 13 are the library's, and it tests them. Invariants 10–12 are **protocol**
invariants each consumer implements today; they become the library's only if `TagWriteSession` is
promoted (§4.7).

1. **Type gate before body parse — a hard invariant** (C8). `decode` checks TNF, then the exact
   external type, and only then returns the body. A sibling's tag must come back `Foreign` and never
   `Recognised`. This matters even with different payload schemes, because **both products' bodies
   may be valid UUID payloads**, so any path that read a payload without checking the type first
   could present a ServiceTag tag as a plausible NoteTag target. `Recognised` therefore carries only
   the body, never the whole record: there is no API shape in which a caller can reach a payload it
   did not type-check.
2. **The AAR, when present, is never first**, and is **absent by default** (O13). Put an AAR first
   and the tag stops matching the `NDEF_DISCOVERED` filter, because the platform reads the *first*
   record to decide the tag's type (**[platform-doc]**, arch §5.3; order **[JVM-proven]** today).
3. **Structural read-back equality.** Verification compares `List<NdefRecordData>` — record count,
   order, each record's TNF, full type string and full payload bytes — not raw tag bytes. The record
   **id** field is deliberately outside the comparison (`toNdefMessage` always writes `ByteArray(0)`),
   and so are the TLV framing, terminator and chunking, because the comparison happens after the
   platform re-parsed the message (arch §5.8). The intent is "the tag holds the records I meant".
4. **Never `FLAG_READER_SKIP_NDEF_CHECK`.** The flag set is `FLAG_READER_NFC_A or _B or _F or _V` and
   nothing else; device-proven negatively at `e2cf1d0` (arch §5.5, §9 D2). Stated in the library's
   own terms at the archaeology review's request. "Platform-sound suppression" is not an existing
   capability anywhere in this estate and is not in scope.
5. **Lower-case external type**, enforced in `TagIdentity`'s `init`.
6. **The AAR package, when used, equals the consumer's `applicationId`**, and the manifest filter
   path equals the identity's external type. Today both are doc comments and unlinked literals
   (arch §4.6, §6.3). §4.8 gives the one mechanism that binds them and the two tests that prove it
   (C9).
7. **Capacity is checked on both write paths, against the exact encoded NDEF message — and nothing
   else.** `Ndef.getMaxSize()` is *the maximum NDEF **message** size the tag can hold*, so the
   comparison is message size against message size:

   ```kotlin
   val needed = records.toNdefMessage().toByteArray().size
   if (needed > ndef.maxSize) return WriteResult.TooSmall(ndef.maxSize, needed)
   ```

   `needed` is the serialised message — every record's header, type and payload — and **never** a
   body length, **never** a character count (O13), and **never** the Type-2 TLV header or
   terminator. That framing layer belongs to Android and to the tag, not to this arithmetic: adding
   it would inflate `needed` by a few bytes and make NoteTag fall back to `LOCAL_REF` for URIs that
   would in fact have fitted. Note that the existing app's *design-time* budget test computes
   `sumOf { 3 + type.size + payload.size } + 3` (arch §5.7); that trailing `+ 3` is a TLV allowance,
   appropriate for a pessimistic compile-time assertion and **wrong for the runtime check**. The
   check runs after `connect()` and after the
   `isWritable` check, so a read-only tag reports `ReadOnly` rather than `TooSmall` (arch §5.7).

   **The `NdefFormatable` path needs a different shape, because `format` is not just a format.**
   `NdefFormatable.format(firstMessage)` **formats the tag and writes that message in one
   operation**, and `firstMessage` may be null. There is no `Ndef` instance and therefore no
   `maxSize` until after the format has happened — so handing the intended message to `format` means
   a too-large message fails *inside* the format call, before any capacity check could exist, and
   surfaces as a generic `Failed` (arch §5.7, §8.2 Q13). **The ratified P8 rule, corrected here, is
   therefore a two-step sequence and not a deferred comparison:**

   1. **`format(null)`** — format only, **unlocked**, **no payload**. Nothing of the intended message
      is offered to the tag, so nothing about it can fail yet.
   2. On the next tap the tag comes back as `Ndef`: **read `maxSize`** — this is the first moment a
      real capacity figure exists, and it is the number the runbook records in the evidence file —
      then capacity-check the intended message against it, write it, read back and compare
      structurally, and only then optionally lock.

   `serialisedSize()` is what lets a consumer state the requirement *before* either tap ("this needs
   N bytes"), but it is never a substitute for the measured `maxSize`: the check that governs is
   always step 2's comparison.
8. **Failure reporting is asymmetric on purpose.** `inspect` propagates tag I/O failure so the caller
   can say "hold it still and try again"; `write` folds every tag I/O failure into `Failed(reason)`
   so a half-written tag never looks like an exception. Every connection closes in
   `finally { runCatching { … } }` (arch §5.6, `bdcc475`).
9. **Lock last, never blind.** `makeReadOnly()` runs only after a verified read-back, and its
   **return value is the proof** that the lock took — nothing later is required to confirm it. On the
   format path the tag is formatted **unlocked and empty** by `format(null)`, and the capacity check,
   the write, the verification and the lock all happen on the second tap (invariant 7, arch §5.6,
   `e2cf1d0`).
10. *(protocol)* **One confirmation, remembered against content.** A confirmed overwrite is consent
    for *that content*, honoured on the next tap of a tag carrying it and cleared by a different
    payload — because the captured `Tag` handle goes stale while a sheet is up and the NFC service
    then refuses it with "Tag is out of date", **[device-observed]** on an Android 17 phone
    (arch §5.6, `0e1975f`).
11. *(protocol)* **Single-flight.** A tap arriving mid-write, or after the session finished, is
    dropped; a confirmation transfers ownership of the busy flag to the sheet. Any exception escaping
    the callback becomes an error state, never a crash on a binder thread.
12. *(protocol)* **Explicit write mode only.** Ambient dispatch cannot write, by construction: the
    trampoline reads `EXTRA_NDEF_MESSAGES`, `EXTRA_TAG` and the data URI, and `nfcTag()` — the only
    route to a writable handle — is never called on that path (arch §5.10). Reader mode is entered
    only from an intentional destination (arch §5.13). §25 requires both.
13. **The library builds no sentence; `nfc-core` has zero third-party, application or framework
    runtime dependencies — the Kotlin stdlib only, added by the `kotlin.jvm` plugin — and the library
    never depends on an app.** Return values carry tokens plus raw evidence; every user-facing string is the consumer's.
    This is what lets each app say "this is a ServiceTag tag — overwriting it will detach it from its
    machine" where the other says "this tag belongs to another app".

### 4.4 The forbidden-dependency scan

The word list is §22's, verbatim, plus §4's NoteTag/app-semantics clause:

> Joplin, noteNFC application semantics, ServiceTag, Asset, AssetEvent, Measurement, Profile,
> Schedule, Attachment, Room, Compose, Backup

— and §4 adds: note IDs, NoteTag semantics, TagBinding, journals, reminders, backup formats, app
navigation, app-specific deep links, application-specific record semantics. §22's own instruction
governs the exceptions: *"Inspect false positives from comments/history, do not blindly accept."*

```bash
# nfc-tag-core/tools/forbidden-scan.sh  — NEW; a CI gate, run BEFORE the build
set -euo pipefail
WORDS='[Jj]oplin|[Oo]bsidian|[Ll]ogseq|[Ee]vernote|[Nn]otion|OneNote|[Tt]odoist|note[ _-]?id|noteNFC|notenfc|NoteTag|notetag|ServiceTag|servicetag|Asset|AssetEvent|Measurement|Profile|Schedule|Reminder|Attachment|Room|room3|androidx\.room|Compose|compose|[Bb]ackup|[Jj]ournal|[Nn]avigation|deep[ _-]?link|md5|MD5'
HITS=$(grep -RInE "$WORDS" \
        --include='*.kt' --include='*.kts' --include='*.xml' --include='*.toml' --include='*.md' \
        nfc-core/src nfc-android/src settings.gradle.kts \
        | grep -vFf tools/forbidden-scan.allow || true)
if [ -n "$HITS" ]; then echo "$HITS"; echo "forbidden knowledge in the library"; exit 1; fi
```

**False positives go in an allow file, never into a weakened pattern.**
`tools/forbidden-scan.allow` holds one exact `path:fragment` per accepted hit with a one-line reason.
Expected entries, and how each was inspected rather than accepted:

| Expected hit | Verdict |
|---|---|
| `com.loosecannon.nfc.tagcore` in every package line | allowed: the library's own name, and `nfc` is not a forbidden word |
| `assets/` under `src/androidTest/` | **not allowed — renamed.** A Gradle source-set convention is not a reason to let the domain noun into the tree (ratified P10) |
| prose using "room for one more record", "profile of the message" | **not allowed — rewritten.** Prose is free to change |
| a KDoc explaining why routes or link policy are *not* here | **not allowed — rewritten** as "URI handling", so the phrase never appears |
| the `TagIdentity` KDoc example | must use `com.example.app`; never either real applicationId |
| `md5` anywhere | **not allowed.** There is no MD5 in this estate's modern code at all (arch §6.4) and none may enter the library |

A hit inside a test fixture is as much a failure as one in `main`, because a fixture is how product
vocabulary usually gets in.

**What the scan covers, and what it deliberately does not.** The paths passed to `grep` are
`nfc-core/src`, `nfc-android/src` and `settings.gradle.kts` — so the `--include='*.md'` flag reaches
only Markdown *inside those source trees*, and the repository's **root documents are outside the scan
on purpose**. `README.md` must be able to say "extracted from the noteNFC/ServiceTag tree", to carry
the provenance table naming `ServiceTag`, `NoteTag` and `TagBinding`, and to explain which app
vocabulary was left behind — and a scan that forbade those words in the very document whose job is to
name them would be self-defeating. The rule the scan enforces is about *code and its fixtures*: what
the library can compile against and test with. The rule for root documents is editorial and is
enforced at review: they may **describe** the consumers, and must never **depend** on them.

### 4.5 Test plan (§22)

**`nfc-core` — pure JVM, JUnit 5, every push.** Generalised from the three existing `:core` test
classes (provenance in §4.6).

| Group | Cases |
|---|---|
| Envelope round-trip | `encode(identity, body)` → `decode(identity, …)` returns `Recognised(body)` byte-for-byte; exact record layout (tnf `0x04`, type as US-ASCII, body verbatim) — from `NdefCodecV1Test.exactByteLayout`, `roundTrips`. Any *total* a test asserts is the new-identity figure: **51 B** for ServiceTag's external record, **95 B** for its record + AAR, **49 B** for a NoteTag `JOPLIN_NOTE` message (§4.9) |
| Record count and order | `aarPackage = null` → **exactly one record** (the default, O13); `aarPackage` set → exactly two, ours first, the AAR second with type `android.com:pkg` and the package as ASCII payload — from `messageIsTagRecordThenApplicationRecord` |
| AAR byte-identity | `applicationRecord(pkg)` matches a pinned byte vector in `nfc-core`, re-asserted against the real `NdefRecord.createApplicationRecord` in `nfc-android` |
| **Sibling isolation** | a record whose type is a *different* domain with the same `typeName` decodes as `Foreign`, and `Foreign.description` carries the full offending type string — the generalisation of `NdefCodecTest.evernoteEraTypeIsForeign`, and the template each app copies (invariant 1) |
| TNF gate | our exact type under a non-external TNF is `Foreign`, not `Recognised` — from `tagRecordUnderWrongTnfIsForeign` |
| First-record-only | extra records after the first are ignored (`onlyFirstRecordMatters`); an AAR-only message is `Foreign` (`applicationRecordAloneIsForeign`); an empty list is `Empty` (`emptyMessageIsEmpty`) |
| Malformed input | a truncated body, an empty body and a body under the wrong type all come back without an exception; the envelope never throws on hostile bytes |
| Generic payload limits | `serialisedSize()` for representative messages, asserted to be exactly `toNdefMessage().toByteArray().size` with **no TLV allowance added**, and compared against **`NTAG213_MAX_MESSAGE_BYTES`** — one named constant, **[unobserved]** at a provisional 137 B and re-pinned to the `Ndef.maxSize` measured from a physical NTAG213 on **Session 1 tap 2** and recorded in the evidence file (§4.9). Parameterised so each consumer asserts its own budget (O14). A second case pins the boundary: a message of exactly `maxSize` bytes is accepted and one of `maxSize + 1` is `TooSmall` (invariant 7) |
| `UuidBytes` | `toBytes`/`fromBytes` round-trip over random UUIDs and the all-zero / all-ones edges; big-endian layout pinned as bytes; `requireCanonical` refuses a non-UUID and an upper-case UUID, accepts the canonical form |
| `TagIdentity` | refuses a mixed-case external type; `externalType` is `"$domain:$name"`; `aarPackage` defaults to null |
| `OverwritePolicy` | the full `ExistingContent` × `isSameIdentity` matrix → the six `OverwriteReason` tokens; `Proceed` only for `Empty` and for `Ours` with `isSameIdentity` — generalised from `OverwritePolicyTest` |

**`nfc-android` — two tiers.**

| Tier | Where | Cases |
|---|---|---|
| Local unit (`test/`) | JVM, every push | `WriteResult` → outcome mapping; the capacity arithmetic (`serialisedSize` vs `maxSize` on the `Ndef` path; the `-1` formatable case routes to `format` and computes no verdict) against a fake `TagIo`; `TagInspection` construction from each inspect branch |
| Instrumented (`androidTest/`) | **the emulator, locally — never in CI** | `NdefBridge` round-trips (`toNdefMessage` → `toRecordData` identity; an `Intent` carrying `EXTRA_NDEF_MESSAGES` on both the pre-33 and 33+ branches; `toHexOrNull`); `applicationRecord` against the real platform call; `NfcReaderModeSession.available == false` on an emulator with no NFC, with `start()`/`stop()` safe no-ops there. Anything needing a real chip is a physical-tag row in the runbook, never an automated test |

The emulator suites stay local: CI has no `androidTest` step, correctly, because those need a device
(arch §4.2), and §15 keeps instrumented suites off the phone entirely.

**The operating rule for every proof in this design: vet everything possible on the emulator; the
phone is used only where RF hardware or the real install is required.** Concretely, capacity
selection, malformed and foreign classification, the `LOCAL_REF` missing-map path, the
crash-consistency failure injection are all JVM or emulator work (ServiceTag's standalone-link
resolution stays a physical row — §E checks 1 and 4 — because §25 says observe, don't infer);
only the physical taps in the runbook's §D and §E need the phone.

### 4.6 Provenance (§9: "document provenance")

For a repository with no inherited history, provenance is a table: every file says which file it came
from and where that file's history starts. Start every trace with `git log --follow -- <path>` **in
the ServiceTag repository** — the renamed continuation of this one, which is where the history lives.

| NEW file in nfc-tag-core | Extracted from (at `ac523d7`) | `git log --follow` start | Change on extraction |
|---|---|---|---|
| `nfc-core/…/NdefRecordData.kt` | `core/…/core/nfc/NdefCodec.kt:8-12` | `76b751a` "add :core with the legacy key and ndef codec pinned by tests" | own file; otherwise unchanged. "The single most reusable type in the repo" (arch §6.1) |
| `nfc-core/…/TagIdentity.kt` | **NEW type** parameterising `NdefCodec.DOMAIN` (`:37`), `V1_TYPE_NAME` (`:39`), `PACKAGE_NAME` (`:46`) | `76b751a` (`DOMAIN`, normalised at `b9f7e51`), `f92a391` (`V1_TYPE_NAME`, `PACKAGE_NAME`) | three constants become a value type; domain and AAR package stay separate fields even when equal (C9) |
| `nfc-core/…/NdefEnvelope.kt` | `NdefCodec.decode` (`:56-65`), `encodeV1` (`:90`), `v1Record` (`:93-102`), `applicationRecord` (`:104-109`) | `76b751a` (decode skeleton), `f92a391` "tag payload format v1: codec, AAR, overwrite policy" | identity becomes a parameter; the type switch collapses to "my type / not my type"; the body is returned unparsed; the AAR becomes optional, appended only when `aarPackage != null` (O13). The legacy branch does **not** come along (O2) |
| `nfc-core/…/TagContent.kt` | `TagPayload.Foreign` / `.Empty` (`NdefCodec.kt:21-26`) | `76b751a` | `Recognised(body)` replaces the product arms `V1`/`LegacyMd5`; `NewerVersion` does not come along — version negotiation is a *body* concern and each app owns its body (§4.7) |
| `nfc-core/…/TagContent.kt` — **`Malformed` does NOT come along** | `TagPayload.Malformed` (`NdefCodec.kt:25`), raised by `decodeV1`/`decodeLegacy` for a bad version byte, a wrong length or non-zero flags | `76b751a` (the type), `f92a391` (the v1 reasons) | **stays per app, by the same rule that keeps the layout out** (§4.7): "malformed" is a judgment about a *body*, and only the app that owns the body scheme can make it. The envelope's vocabulary is `Recognised` / `Foreign` / `Empty`; a recognised body that then fails to parse is the consumer's `Malformed`, in the consumer's words. The library does keep the *classification slot* — `ExistingContent.Unreadable` — so the overwrite decision can still be made about a tag whose body nobody could read |
| `nfc-core/…/UuidBytes.kt` | the `ByteBuffer`/`UUID` halves of `NdefCodec.decodeV1` and `v1Record`, plus `requireCanonicalUuid` (`:111-120`) | `f92a391` | takes a `String`/`ByteArray` instead of a `TagId`, so the `TagId` wrapper stays in the app. **The layout it used to live in stays behind** (§4.7) |
| `nfc-core/…/OverwritePolicy.kt` | `core/…/core/nfc/OverwritePolicy.kt` (whole file) | `f92a391` | `decide(existing: TagPayload, intended: TagId)` → `decide(existing: ExistingContent, isSameIdentity: Boolean)`; the five hard-coded sentences — each of which says "noteNFC" — become tokens plus a `detail` string |
| `nfc-android/…/NdefBridge.kt` | `app/…/nfc/NdefBridge.kt` (whole file) | `dc1bb1c` "nfc adapter: ndef bridge, reader-mode session, tag writer with read-back" | wholesale; imports only `android.*` and `NdefRecordData`, so nothing to strip. `Intent.nfcTag()` is dead code in the app today (arch §6.2) and becomes live API. `serialisedSize()` is **NEW**, lifting `message.toByteArray().size` out of `TagWriter.write` so a consumer can ask before a tap |
| `nfc-android/…/NfcReaderModeSession.kt` | `app/…/nfc/NfcReaderModeSession.kt` (whole file, 39 lines) | `dc1bb1c`; **corrected at `e2cf1d0`** | verbatim. **The doc comment is half the value — carry it across** (arch §6.2), including why the platform NDEF check stays on and the `onTag` threading contract |
| `nfc-android/…/TagWriter.kt` (+ `TagInspection`, `WriteResult`) | `app/…/nfc/TagWriter.kt` (whole file, 126 lines) | `dc1bb1c`; throw contracts at `bdcc475`; lock-after-read-back and the unlocked format path at `e2cf1d0` | the single `NdefCodec.decode` call — used only to classify what was read — is removed, so the library carries no product type string; `TagInspection.existing: TagPayload` becomes `unreadable: String?` and the caller classifies. Adds the formatted-size capacity rule (invariant 7) |
| `nfc-android/…/TagIo.kt` (+ `TagHandle`, `NfcTagHandle`, `RealTagIo`) | `app/…/ui/scan/TagWriteController.kt:32-65` | `c808b49` "scan, tag result sheets, write flow with the 1b rules, share card, links" | moved out of a UI file, where it does not belong (arch §6.2) |
| `nfc-core/src/test/…/NdefEnvelopeTest.kt` | `core/src/test/…/NdefCodecTest.kt` | `76b751a`; legacy cases trimmed at `26ec9d0` | the legacy-key cases do not come along (O2); `evernoteEraTypeIsForeign` becomes the parameterised sibling-isolation case |
| `nfc-core/src/test/…/UuidBytesTest.kt`, `…/EnvelopeLimitsTest.kt` | `core/src/test/…/NdefCodecV1Test.kt` | `f92a391` | the helper and limit cases are extracted; the version/flags cases stay with the app that owns the layout |
| `nfc-core/src/test/…/OverwritePolicyTest.kt` | `core/src/test/…/OverwritePolicyTest.kt` | `f92a391` | asserts tokens instead of sentences |

Three things deliberately do **not** move, recorded so nobody looks for them: `LegacyKey.compute`
(added `76b751a`, deleted `26ec9d0`; it survives as working code only in the `c84b881` tree — arch
§6.4, and under O2 nothing consumes it); `TagRoute`/`DeepLinkRoute`/`LinkLaunchPolicy` (policy — §4,
O5, arch §6.1); and `TagWriteSession` (deferred — §4.7).

### 4.7 What is deliberately NOT extracted yet (C5)

| Candidate | Why it waits | When it may be promoted |
|---|---|---|
| **The versioned payload layout** (`version|flags|UUID`, once proposed as `VersionedUuidPayload`) | **Out, not deferred.** The two products' bodies genuinely differ: ServiceTag's v1 body is `version|flags|16-byte UUID`; NoteTag's is `version|kind|flags|kind-body` with three kinds (§4.9, O13/O14). No single layout is used identically by two consumers, so the two-consumer rule forbids it. What *is* shared is the byte↔UUID helper, which is application-neutral and named by §4 and §22 — the distinction is between a **helper** and a **scheme** | not while the schemes differ. If a third product ever adopts one of the two layouts, that layout belongs to the app that already owns it |
| **`TagWriteSession`** — single-flight, read-before-write, consent ownership, remembered consent across a stale handle, format → measure → capacity-check → write → verify → lock-last, abandon-on-close | The protocol exists once, in `TagWriteController` (299 lines, `c808b49`), entangled with `ProvisionTag`, `TagBinding`, `AppGraph` and thirteen message strings (arch §6.2). It is *believed* general, but NoteTag has not been built yet, and "the highest-value extraction and the hardest" is exactly the kind that must not be designed against one consumer. C5 is explicit: extract the unquestionable seam, build NoteTag against it, promote the rest only if both need it | **A later step, not a day-one deliverable.** The criterion is concrete: after NoteTag ships its writer, diff its write flow against ServiceTag's. If both need single-flight, one-confirmation-remembered-against-content, and format → measure → write → verify → lock-last with identical *decisions* (not merely similar shapes), promote it as `nfc-tag-core-v0.2.0` and delete both copies. If NoteTag's writer turns out simpler — plausibly it has no row to provision and no stale-sheet problem — the protocol stays ServiceTag's and the library keeps only the pattern in its README |
| **Version/kind negotiation** (`NewerVersion`) | a body concern; each app decides what an unknown version or kind means to its user | with the layout, i.e. not at all |
| **Foreign/malformed *wording*** | §4: no wording in the library. The classification is shared (`Foreign`, `Unreadable`); the sentence is not | never |

Stating this is itself a deliverable: gate 4 (§34) reviews the library boundary, and a boundary that
claims more than two consumers have proven is the failure mode that review exists to catch.

### 4.8 The manifest ↔ constant binding — one mechanism (C9)

C9 requires ONE mechanism. This is it: **a Gradle-owned identity value that produces both the
manifest placeholder and the app-side `TagIdentity`.**

```kotlin
// app/build.gradle.kts — the single source of truth for this app's tag identity
val ndefExternalDomain = "com.loosecannon.servicetag"   // NFC Forum external-type domain
val ndefTypeName       = "tag"
val aarPackage: String? = "com.loosecannon.servicetag"  // null for NoteTag (O13)

android.defaultConfig {
    manifestPlaceholders["ndefTagPath"] = "/$ndefExternalDomain:$ndefTypeName"
    buildConfigField("String", "NDEF_EXTERNAL_DOMAIN", "\"$ndefExternalDomain\"")
    buildConfigField("String", "NDEF_TYPE_NAME", "\"$ndefTypeName\"")
    buildConfigField("String", "NDEF_AAR_PACKAGE", aarPackage?.let { "\"$it\"" } ?: "null")
}
```

```xml
<data android:scheme="vnd.android.nfc" android:host="ext" android:path="${ndefTagPath}" />
```

```kotlin
val tagIdentity = TagIdentity(
    externalDomain = BuildConfig.NDEF_EXTERNAL_DOMAIN,
    typeName       = BuildConfig.NDEF_TYPE_NAME,
    aarPackage     = BuildConfig.NDEF_AAR_PACKAGE,
)
```

`android:path` stays an **exact** match, never `pathPrefix` — the stricter and correct choice the
manifest already makes (arch §5.1). Two tests hold the binding:

1. **JVM unit test**: `TagIdentity` built from `BuildConfig` yields the expected lower-case external
   type, and `aarPackage` is `null` (NoteTag) or equal to `BuildConfig.APPLICATION_ID` (ServiceTag,
   while it keeps one) — invariant 6.
2. **Emulator instrumented test**: build `vnd.android.nfc://ext/<identity.externalType>`, ask
   `PackageManager.queryIntentActivities` for `ACTION_NDEF_DISCOVERED` on it, and assert exactly one
   match, in this package, on this app's dispatch activity. This tests the *merged manifest as
   installed*, which a string comparison cannot.

Separate parameters for domain and AAR package are kept even when the strings are equal, because they
are different things (C9).

### 4.9 NoteTag's canonical tag format (O13 + O14) — app-owned, library-framed

Recorded here because it determines the library boundary, not because the library knows it. **Version
and kind meanings belong to NoteTag; envelope, framing, capacity and write safety belong to
nfc-tag-core** (O13).

One record, `TNF_EXTERNAL_TYPE`, type `com.loosecannon.notetag:tag`, **no AAR by default**:

```text
byte 0   version = 0x01
byte 1   kind
byte 2   flags   = 0x00        (reserved; any non-zero value is malformed)
byte 3.. kind body
```

| kind | Name | Body | Read as |
|---|---|---|---|
| `0x01` | `JOPLIN_NOTE` | 16 raw bytes of the **32 lower-case hex** note id | the 16 bytes are re-rendered as **32 lower-case hex** and opened as `joplin://x-callback-url/openNote?id=<hex>` |
| `0x02` | `URI` | the full canonical URI as UTF-8, **no abbreviation byte** | launched through NoteTag's own safe-launch allowlist |
| `0x03` | `LOCAL_REF` | a 16-byte UUID | resolved through NoteTag's minimal local store to a target |
| `0x04`+ | reserved | — | a compact provider kind is allocated only when a stable compact identifier earns one; otherwise providers use `URI` or `LOCAL_REF` |

**The writer's decision is automatic, in this order** (O14): a compact representation is available →
write it; else the full `URI` **fits**, decided by encoding the exact NDEF message and comparing with
the tag's **measured `Ndef.maxSize`** → write `URI`; else `LOCAL_REF`, and store the target locally.

**`JOPLIN_NOTE` is lower-case hex on both sides, and the write side validates rather than assumes.**
The 16-byte body is only a legitimate compact representation when the source id really is 32
hexadecimal characters; a Joplin id that arrives upper-case, mixed-case, shortened, hyphenated or
otherwise non-conforming must not be silently truncated or mangled into 16 bytes. So the writer:

1. accepts a candidate id **only** if it matches `^[0-9a-fA-F]{32}$`;
2. **normalises it to lower case** before packing the 16 bytes;
3. and on any other shape **falls through to `URI`** — the explicit fallback, carrying the full
   `joplin://…` URI as UTF-8 — rather than refusing the write or guessing.

The read side renders the 16 bytes back as 32 lower-case hex, so a mixed-case input round-trips to a
lower-case output. A **mixed-case round-trip test** pins exactly that: encode from an upper- or
mixed-case id, decode, and assert the reconstructed id is lower case and equal to the normalised
input. A non-conforming id gets its own test asserting the `URI` fallback was chosen.

**NTAG213 is the minimum supported tag**, and every figure below is labelled by unit, because the two
units differ by exactly the framing G1 removed from the write comparison:

| Message | **NDEF message size — the unit the write check uses** | message + Type-2 framing — *physical-tag scale only* |
|---|---|---|
| NoteTag `JOPLIN_NOTE`, one external record, no AAR — `3 + 27 + 19` | **49 B** | 52 B |
| NoteTag `JOPLIN_NOTE` *if* an AAR were appended — `+ (3 + 15 + 23)` | **90 B** | 93 B |
| ServiceTag's record + AAR **under the new identity** — `(3 + 30 + 18) + (3 + 15 + 26)` = `51 + 44` | **95 B** | 98 B |

The ServiceTag figures are **not** the ones the archaeology quotes: those were computed against the
old identity, whose type string and AAR package were four characters shorter each. With
`com.loosecannon.servicetag:tag` (30 characters) and AAR package `com.loosecannon.servicetag` (26),
the external record is `3 + 30 + 18 = 51 B` and the AAR `3 + 15 + 26 = 44 B`, so the message is
**95 B**, not 89. Every exact-byte test description that names a total must use these numbers, and
the identity conversion is the commit that changes them.

The right-hand column is the figure the archaeology quotes (arch §5.7) and is useful for one purpose
only — sanity-checking against a datasheet's user-memory number. **It is never the write
comparison**: `needed` is the left-hand column, compared directly against `Ndef.getMaxSize()`
(invariant 7). The extracted limits test asserts the left-hand column against a single named
constant, **`NTAG213_MAX_MESSAGE_BYTES`**, seeded provisionally at **137 B — [unobserved], a
provisional seed and nothing more.** It is *not* a **[platform-doc]** figure: Android documents what
`getMaxSize()` *means*, never what an NTAG213 returns for it, and NXP's datasheet documents user
memory, not the platform's reported message capacity. The constant is **re-pinned to the value
actually measured from a physical NTAG213** on the runbook's Session 1 **tap 2** — the first moment an
`Ndef` instance exists at all (invariant 7) — and recorded in the evidence file, after which the
budget the tests defend is a number this project has observed rather than one it guessed.

For reference and clearly labelled as such: NTAG213's **144 B** of user memory, of which roughly
**139 B** remain for NDEF after NXP's lock-control TLV, are **datasheet** figures — vendor
documentation, tagged **[platform-doc]** in the vendor sense: not Android's, not observations — and
nothing in the design computes from them. NTAG215 and NTAG216 hold more by the same
logic and are never required. **There are no character-count promises anywhere in this design**:
capacity is always the measured tag against the exact encoded message, and a tag that cannot hold the
message is refused cleanly (O13, O14).

**Persistence.** NoteTag gets a **minimal local store** — `LOCAL_REF` targets plus convenience
metadata (label, kind, written-at, last-opened) — and it is **never required to resolve a
`JOPLIN_NOTE` or `URI` tag**: those are self-contained and portable. Only `LOCAL_REF` tags are
**device-bound**, and the writer must tell the user so at write time. A NoteTag export/import of the
local map is NoteTag roadmap, not split scope (O14). The store is a single **atomically-replaced JSON
file** behind a small interface, using kotlinx-serialization (already in the version catalog), so
"no Room unless it earns it" stays true and the store can be swapped later without touching the tag
format (ratified P19).

**The `LOCAL_REF` crash-consistency invariant** (a NoteTag invariant, not a library one — the library
never sees the mapping). Two rules, in this order of priority:

> **(a)** The mapping is durably stored **before** the physical tag is written; a `LOCAL_REF` whose
> mapping has not committed is never written at all.
> **(b)** Once a physical write has been **attempted**, the mapping is **retained** unless it is
> provable that **no bytes reached the tag**.

The sequence:

1. allocate the UUID;
2. **atomically persist** the `LOCAL_REF → target` mapping (temporary file, `fsync`, atomic rename
   over the store) and continue only once that has returned successfully;
3. write the tag and verify it by structural read-back;
4. **on success, retain the mapping. On an *ambiguous* failure — the tag lost mid-write, or lost
   between the write and the read-back, or any I/O error once the message has been handed to the
   chip — also retain it.** Remove it only where no write can have happened: the user cancelled
   before the write, or the attempt was rejected *pre-write* (a capacity refusal, a foreign-record
   refusal, a read-only tag, an unsupported tag).

**Why retention, not cleanup, is the safe default after an attempt.** A failed `write` does not mean
an unwritten tag: `writeNdefMessage` can physically succeed and the tag can then leave the field
before the read-back confirms it, which the platform reports as a failure. Delete the mapping on that
path and the result is a **live `LOCAL_REF` tag in the world that resolves to nothing on the only
phone that could ever resolve it** — silent, permanent, and indistinguishable to the user from a
broken app. Keep it and the worst case is an **orphan JSON entry**: a few bytes nobody sees, which
the next write of that UUID overwrites and which no user-visible behaviour depends on. The two
outcomes are not comparable in cost, so the rule follows the cheaper failure. Rule (a) still holds
the other end: persisting first is what makes "the tag might be live" a recoverable state rather than
a lost one.

The **failure-injection test** is the named deliverable, with three cases:

| Case | Expected |
|---|---|
| persist succeeds, then an **ambiguous** write failure (tag lost mid-write, or lost before the read-back completes) | **the mapping is RETAINED** |
| persist **fails** | **no tag write is attempted at all** |
| the user **cancels**, or the write is rejected **pre-write** (capacity, foreign refusal, read-only, unsupported) | the mapping **may be removed** |

It is a NoteTag test, not a library test — the library never sees the mapping — and it is named as a
deliverable of phase E in the runbook (§A.2 task 7).

**UI toolkit** (ratified P20): **Compose, one activity, two tiny screens** — share/write, and a
short list of tags this phone has written. R6's "no Compose" is superseded by O1–O15, O6 asks for a
substantial rewrite on modern engineering, and the version catalog the estate already pins supplies
Compose at a single known version (arch §4.4), so this adds no new toolchain decision.

**The deliberate trade of shipping without an AAR.** With one external record and no AAR, a phone
*without* NoteTag installed does nothing at all on a tap: the platform tries `NDEF_DISCOVERED` (no
match), then `TECH_DISCOVERED` (neither product declares one), then stops. With an AAR it would
instead open a Play page for a listing that does not exist. Neither is useful, which is why the AAR
is not paid for by default — and why **a dispatch spike is a coexistence-phase deliverable** (O13):
external-type-only dispatch reliability when the app *is* installed, and observed behaviour when it
is not. Note too that NoteTag's `URI` kind travels **inside** the external record, not as an NDEF URI
record, so the platform never sees a URI and never offers a browser — only NoteTag's filter matches
(compare **[platform-doc] PD10** in arch §8.1, where a genuine web-link tag triggers `ACTION_VIEW`
from Android 16
and an "open link" notification from Android 17).

---

## 5. What deliberately stays app-specific

Duplication here is a decision. The rule is §4's: extract only behaviour proven by two real
consumers, and never on resemblance (arch §6 applies the same test).

| Stays in each app | Why not shared |
|---|---|
| **The payload body and its meaning** — ServiceTag's `version|flags|UUID` v1 layout; NoteTag's `version|kind|flags|body` with three kinds | The library hands back bytes. That a ServiceTag body's UUID is a row in `nfc_tag` whose `target` names an asset, and that a NoteTag body may be a Joplin note id, a URI or a local reference, is exactly the knowledge §4 forbids. It is also a two-consumer *failure*: the schemes differ (§4.7) |
| **Resolution and binding** — `ResolveTag`/`Resolution`, `BindTag`, `ProvisionTag`, `TagBinding`, `PayloadFormat`, `TagStatus`, `TagTarget`, `requireTargetExists` | "The clearest boundary in the codebase: everything below is mechanism, this and above is policy" (arch §6.1). ServiceTag's resolver reaches four repositories, a `UnitOfWork` and a `Clock`, and stamps `lastScannedAt` inside a transaction — *a read scan performs a write* — with eight outcomes (arch §5.9). NoteTag's resolves a self-contained body, or does one local-store lookup |
| **Deep-link routes** — `TagRoute`, `DeepLinkRoute` | "The library must not own a scheme" (arch §6.1). `notetag` and `servicetag` are identity; `asset` has no meaning in NoteTag. The only mechanism inside was the canonical-UUID regex, duplicated in three places today (`TagRoute.kt:13`, `DeepLinkRoute.kt:18`, inside `requireCanonicalUuid`); the library now exports it once as `UuidBytes.requireCanonical` |
| **Share flow** — `ShareActivity`, `ShareFlow`, `ShareCardScreen` | NoteTag's is the whole product (§23); ServiceTag's is one way to create a link (arch §6.2). Both read `EXTRA_TEXT` as a `CharSequence` "because that is what the contract promises; the styling is dropped, not trusted" — a *pattern* worth copying, in two different codebases |
| **Link-launch policy** — `LinkLaunchPolicy`, `OpenLink`, `SaveLink`, `LinkLauncher` | "The allowlist is a **product decision**, not a mechanism. Duplicate; do not share" (arch §6.1); it is not even NFC. ServiceTag's allowlist is `{joplin, obsidian, logseq, http, https}` with the gate run twice — at save and at launch, "so a URI that arrives through a backup gets the same treatment as one typed in" (arch §5.11). NoteTag writes its own from first principles and diverges under #6/#36. Both keep the `ActivityNotFoundException` **and** `SecurityException` catch, because the 2024 app crashed without it (arch §2.5) |
| **The write protocol**, until two consumers prove it | §4.7. Each app keeps its own controller; the library keeps the pattern in its README |
| **Every user-facing sentence** — including the two deliberately different `describe()` functions and the two "Unregistered tag" states that differ only in whether a row exists | "Do not extract — the divergence is the feature" (arch §6.2, §5.12) |
| **The dispatch trampoline** — `NfcDispatchActivity` | The *shape* (translucent, UI-less, `singleTop`, `excludeFromRecents`, hostile-extras guard, exactly two accepted actions, hand off to one renderer) is a documented pattern; the code reaches `AppGraph`, `MainActivity`, `OpenLink` and `Resolution` (arch §6.2) |
| **ServiceTag's Apollo Service Binder theme and design system** | ServiceTag's alone; issue #20 is ServiceTag's, not shared (C7) |

---

## 6. Dependency mechanism (O15, §19)

**Decided by O15, not chosen here:** a separate `nfc-tag-core` Git repository, a **pinned Git
submodule** in each app at `libs/nfc-tag-core/`, and its Gradle modules **included directly as
subprojects**. Not `includeBuild`. No Maven, no publication, no credentials, no composite build. This
satisfies §19's preference order — no publication infrastructure exists in this estate (no Maven
repository in any build file, no init script), so the answer is the simple pinned source mechanism.

### 6.1 Why subprojects rather than a composite build

The reasoning is worth keeping, because it is what review correction 14 identified:

| | `includeBuild` | **subprojects (O15)** |
|---|---|---|
| Version catalog | **not shared** — an included build resolves `libs.*` from its *own* `gradle/libs.versions.toml`, so Kotlin, AGP and JUnit versions exist twice and can drift silently | **shared** — the library's modules resolve `libs.*` from the consuming app's catalog: one version of each per build, by construction |
| Settings plugins | **not shared** — the foojay toolchain-resolver convention is applied in `settings.gradle.kts`, so an included build needs its own copy or its `jvmToolchain(17)` cannot be provisioned | **shared** — the app's foojay convention covers the library's modules exactly as it covers `:core` today |
| Daemon JVM pin | **not shared** — `gradle/gradle-daemon-jvm.properties` pins `toolchainVendor=JETBRAINS`, `toolchainVersion=25` while every module targets 17 (review correction 13); a working arrangement that would have to be reproduced | **shared** — one daemon JVM, one resolver, one toolchain story |
| `gradle.properties` | not shared: caching and **configuration cache on** (review correction 14) would need reproducing | shared |
| Dependency wiring | coordinates plus automatic substitution — an indirection that can silently resolve a real artifact if substitution ever misses | `implementation(project(":nfc-android"))` — unambiguous, and impossible to satisfy from a repository |
| Standalone library build | natural | also fine: the library has its own root build, read only when it *is* the root (Gradle reads the root settings file alone) |
| Repository declarations | each build declares its own | **the library declares repositories ONLY in its own root `settings.gradle.kts`, never in a module script.** Both apps set `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` **[code]**, so a `repositories { … }` block inside `nfc-core/build.gradle.kts` or `nfc-android/build.gradle.kts` would fail the *app* build the moment the module is included as a subproject — while passing the library's own standalone build. That is the nastiest shape of failure: green where it is authored, red where it is consumed |

The one real cost of subprojects is that the library's module build files may use only catalog
**alias names that exist in all three catalogs**: `kotlin.jvm`, **`android-library`**, `junit.bom`,
`junit.jupiter`, `junit.platform.launcher`, `kotlin.test`, and the androidx.test set. Two of those
need attention before any of this compiles:

- **`android-library` does not exist in either app's catalog today** — the apps only ever needed
  `android-application` (`gradle/libs.versions.toml` **[code]**). Every consumer's catalog, and the
  library's own, must gain
  `android-library = { id = "com.android.library", version.ref = "agp" }`. That is the first task of
  §A.4 in the runbook, before the submodule is wired at all.
- **`kotlin-android` is deliberately absent and stays absent.** No catalog has one and none needs
  one: AGP 9.4 has Kotlin built in, `app/build.gradle.kts` already configures `jvmTarget` inside its
  `android { kotlin { compilerOptions { … } } }` block **[code]**, and `nfc-android` copies that
  exact shape. Adding a `kotlin.android` alias would be the wrong fix for a problem that does not
  exist.

**The library's standalone catalog pins the same `agp` and `kotlin` versions as the apps'** — AGP
9.4.0 and Kotlin 2.4.20 today (arch §4.4) — because a standalone build that compiles the same sources
against a different AGP proves nothing about the subproject build. One line makes drift loud, run as
part of the pin-assertion step (§6.3):

```bash
diff <(grep -E '^(agp|kotlin) =' gradle/libs.versions.toml) \
     <(grep -E '^(agp|kotlin) =' libs/nfc-tag-core/gradle/libs.versions.toml)
```

Three independent CI greens — the library standalone, plus each app building it as a subproject —
prove the alias set agrees. A missing alias fails at configuration time naming the alias, which is a
good failure.

### 6.2 The exact settings snippet

**ServiceTag** — the current file gains the block marked NEW; everything else is as it is today
**[code]**:

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}

rootProject.name = "ServiceTag"
include(":app", ":core")

// ---- NEW (O15): the pinned shared library, as ordinary subprojects of THIS build ----
require(file("libs/nfc-tag-core/nfc-core/build.gradle.kts").isFile) {
    """
    libs/nfc-tag-core is missing or uninitialised.
    Clone with --recurse-submodules, or run:  git submodule update --init --recursive
    """.trimIndent()
}
include(":nfc-core", ":nfc-android")
project(":nfc-core").projectDir    = file("libs/nfc-tag-core/nfc-core")
project(":nfc-android").projectDir = file("libs/nfc-tag-core/nfc-android")
```

**NoteTag** is the identical NEW block with `rootProject.name = "NoteTag"` and its own module list.

Consumption, in both apps' `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":nfc-android"))
    // :nfc-core arrives transitively as an `api` dependency of :nfc-android.
}
```

### 6.3 The CI snippet, and what CI/settings must change (review addition 4)

```yaml
      - uses: actions/checkout@v4
        with:
          submodules: recursive          # NEW -- today the checkout has none
          fetch-depth: 0                 # NEW -- the pin assertion needs tags; depth 1 has none

      - name: assert the shared library is initialised at the pinned commit
        run: |
          set -euo pipefail
          test -f libs/nfc-tag-core/nfc-core/build.gradle.kts \
            || { echo "libs/nfc-tag-core is not initialised"; exit 1; }
          pinned=$(git ls-tree HEAD libs/nfc-tag-core | awk '{print $3}')
          actual=$(git -C libs/nfc-tag-core rev-parse HEAD)
          [ "$pinned" = "$actual" ] \
            || { echo "submodule is at $actual but this commit pins $pinned"; exit 1; }
          # fetch-depth 0 already brought the tags; the explicit fetch keeps this step correct
          # even if someone later reverts the checkout to a shallow one.
          git -C libs/nfc-tag-core fetch --tags --force --quiet || true
          git -C libs/nfc-tag-core describe --exact-match --match 'nfc-tag-core-v*' --tags HEAD \
            || { echo "submodule is not at an exact nfc-tag-core-v* tag (mutable HEAD)"; exit 1; }
          diff <(grep -E '^(agp|kotlin) =' gradle/libs.versions.toml) \
               <(grep -E '^(agp|kotlin) =' libs/nfc-tag-core/gradle/libs.versions.toml) \
            || { echo "library catalog pins a different agp/kotlin than this app"; exit 1; }
          [ -z "$(git -C libs/nfc-tag-core status --porcelain)" ] \
            || { echo "submodule working tree is dirty"; exit 1; }
```

| Change | Where | Why |
|---|---|---|
| `submodules: recursive` on the checkout | both apps' `actions/checkout@v4` step — **today it has none** (review correction 14) | without it CI checks out an empty `libs/nfc-tag-core` and fails at configuration time with the `require` message |
| **`fetch-depth: 0`** on the same step | both apps | `actions/checkout` defaults to a depth-1 fetch with **no tags**, under which `git describe --exact-match --match 'nfc-tag-core-v*'` fails on a correctly-pinned submodule — a false red that would teach everyone to ignore the assertion. The step also runs `git -C libs/nfc-tag-core fetch --tags --force` so it survives a later reversion to a shallow checkout |
| the `android-library` catalog alias | both apps' and the library's `gradle/libs.versions.toml` | **absent today**; without it `nfc-android` cannot declare its plugin at all (§6.1, §A.4 task 1) |
| the assertion step above, also available as `tools/check-submodule-pin.sh` for local runs and as a `check` dependency | both apps | §19's "exact version/commit pinned" and "mutable HEAD not silently consumed" |
| clean-clone builds for **both** apps | §26 acceptance, run outside CI as well | `git clone --recurse-submodules <url> <tmp>` into a never-used directory, then that repo's CI task list; once more from a second workstation |
| nothing about the daemon JVM, the catalog or the configuration cache | — | that is the point of subprojects: those files exist once, at the app root, and now cover the library too |
| no credential, token or repository URL in any build file | all three | preserves the current property that CI interpolates no `secrets.*` (arch §4.2) and satisfies §26 |

### 6.4 A version bump, and what fails loudly

A bump is three commits, never a pointer nudge:

1. **In `nfc-tag-core`:** land the change; `git tag -a nfc-tag-core-v0.2.0 -m "…"`; push the tag.
2. **In each app:**
   `git -C libs/nfc-tag-core fetch --tags && git -C libs/nfc-tag-core checkout nfc-tag-core-v0.2.0`,
   then commit the gitlink in a commit **whose message names the library tag** and what changed for
   that app — e.g. `bump nfc-tag-core to nfc-tag-core-v0.2.0: TagWriteSession promoted`.
3. Both apps move independently, and the bump is complete only when **both** are green. A library
   change only one app can absorb is a library design error — the same rule that keeps
   `TagWriteSession` out until two consumers want it (§4.7).

| Failure | How it surfaces |
|---|---|
| submodule missing (clone without `--recurse-submodules`, or a fresh checkout) | the `require` in `settings.gradle.kts` fails **at configuration time**, before any task runs, printing the exact fix command; CI's assertion step fails even earlier |
| submodule present but at the wrong commit | the assertion step compares `git ls-tree HEAD libs/nfc-tag-core` against the submodule's `HEAD` and names both |
| pointer is not at an exact release tag | `git describe --exact-match --match 'nfc-tag-core-v*' --tags HEAD` fails: a commit that is not exactly an `nfc-tag-core-v*` tag — an untagged commit, or one carrying some unrelated tag — is a mutable-HEAD consumption (§19) |
| submodule working tree dirty | `git status --porcelain` in the submodule is non-empty: the app would be building against code nobody else can reproduce |
| pointer moved without a commit in the app repo | the app's `git status` shows the gitlink modified; CI checks out the *recorded* pointer, so the change appears to do nothing rather than diverging silently |
| catalog alias missing from a consumer | configuration-time failure naming the alias — the expected first encounter is `android-library`, which **no app catalog has today** (§6.1) |
| a `repositories { … }` block added to a library **module** script | the *app* build fails on `FAIL_ON_PROJECT_REPOS` while the library's standalone build stays green: authored-green, consumed-red. Repositories belong only in the library's root `settings.gradle.kts` (§6.1) |
| the library catalog pinning a different `agp`/`kotlin` than the app | the `diff` in the pin-assertion step fails, naming both files (§6.3) |
| the two apps on different library tags | allowed *between* bumps; the coexistence gate requires both on the same tag (§J gate 9 of the runbook) |

O15 excludes Maven, publication, credentials and composite builds, so no alternative mechanism —
JitPack included — is in scope for this split.

---

## 7. NFC semantic ownership after the split

Rows are tag kinds; columns are what each app does. There are **no legacy rows and no pre-split
rows**: nothing reuses `com.loosecannon.notenfc`, neither product understands `md5_short`, and no tag
written before the split is claimed ambiently by anything (O1, O2, O3).

| Tag kind | NoteTag manifest | ServiceTag manifest | NoteTag decoder returns | ServiceTag decoder returns | NoteTag user sees | ServiceTag user sees |
|---|---|---|---|---|---|---|
| **NoteTag `JOPLIN_NOTE`** — one external record `com.loosecannon.notetag:tag`, body `01 01 00 ‖ 16 note-id bytes`, no AAR | `NDEF_DISCOVERED`, `scheme=vnd.android.nfc`, `host=ext`, **exact** `path=/com.loosecannon.notetag:tag` | no filter | `Recognised` → kind `0x01` → the note id → safe `ACTION_VIEW` on `joplin://…` | `Foreign("tnf=4 type=com.loosecannon.notetag:tag")` — reachable only in reader mode | the note opens in Joplin; a missing handler is a message, never a crash | nothing ambiently; inside Read/inspect tag: "this tag belongs to another app" |
| **NoteTag `URI`** — same envelope, body `01 02 00 ‖ UTF-8 URI` | the same single filter | no filter | `Recognised` → kind `0x02` → allowlist gate → `ACTION_VIEW` | `Foreign` | the page or app opens; a refused scheme is named and not launched | as above |
| **NoteTag `LOCAL_REF`** — body `01 03 00 ‖ 16-byte UUID` | the same single filter | no filter | `Recognised` → kind `0x03` → local-store lookup | `Foreign` | the target opens on a hit; on a miss, "this tag was written on another phone" — and the *writer* said so at write time (O14) | as above |
| **ServiceTag asset tag** — `com.loosecannon.servicetag:tag`, body `01 00 ‖ 16 UUID bytes`, AAR `com.loosecannon.servicetag` (pending the spike) | no filter | `NDEF_DISCOVERED`, exact `path=/com.loosecannon.servicetag:tag` | `Foreign("tnf=4 type=com.loosecannon.servicetag:tag")` — reader mode only | `Recognised` → v1 body → `nfc_tag` row → the asset | reader mode only: on the writer, `Confirm(FOREIGN, …)` — nothing written without one explicit confirmation, and the reason names the other product; on inspect, "belongs to another app" | the asset opens |
| **ServiceTag standalone-link tag** — same wire shape, row target is a link | no filter | the same single filter | `Foreign` | as above | the link launches with **no app screen at all**, and `last_opened_at` is stamped (arch §5.11) |
| **Unrelated / foreign** — a commercial URL sticker, an unrelated external type, a blank tag, unparseable NDEF | no matching filter; **no `TECH_DISCOVERED` catch-all** | no matching filter; no catch-all (arch §5.1) | `Foreign` / `Empty` / unreadable → never a lookup, never a write, never a transaction (arch §5.12) | same | reader mode only: "not a NoteTag tag / this tag holds something else", *Write over it* / *Cancel* | the same shape, ServiceTag's words |

### 7.1 The eight legacy tags — inventory facts only

Eight physical `md5_short` tags exist on the owner's equipment, one of them targeting a standalone
note rather than an asset; the census is `docs/design/g1/00-source-data-inventory.md` (review
correction 3, addition 2). **The historical package is not installed. Its old private lookup map is
not part of any supported migration path and is intentionally abandoned.** (Evidence: an
attached-phone `pm list packages` listing showed only the lower-case current package; the exact
`pm path com.looseCannon.noteNFC` check was run with the phone attached on 2026-09-16 and returned
nothing; nothing here claims the old private data is proven gone, only that the package is absent.)

Their lifecycle is therefore not a migration at all: an old tag on a machine is currently useless;
when convenient, the owner opens that machine in ServiceTag and writes the canonical ServiceTag tag;
done. The normal writer's read-before-write step already presents a foreign or unreadable record as a
one-confirmation overwrite, so this needs **no wizard, no resolver, no compatibility mode, no
acceptance row and no migration code** (O2, O11). This paragraph is the whole treatment.

### 7.2 Who is launched when no filter matches

**The platform rule** **[platform-doc]**, cited by the archaeology's `PD*` labels (arch §5.3, §8.1
Q1/Q5 — the labels were `C*` until the internal review renamed them, to stop them colliding with the
gate corrections also called C1–C9):

1. **PD1/PD2** — `ACTION_NDEF_DISCOVERED` is tried first. With an AAR present, the platform tries the
   intent filter and starts the AAR's package *"if the Activity that filters for the intent does not match the AAR,
   if multiple Activities can handle the intent, or if no Activity handles the intent."*
2. **PD1** — if more than one application can handle the intent, the Activity Chooser is presented.
   **With two disjoint exact-path filters this should never arise** — which is exactly what §25's "no ordinary
   tag produces an unpredictable chooser from overlapping identity" requires proving.
3. **PD5** — if nothing matches `NDEF_DISCOVERED`, `TECH_DISCOVERED` is tried; neither product
   declares a tech filter, so that fails too.
4. **PD5/PD10** — if nothing filters for any intent, the platform does nothing, except that from
   Android 16 a genuine **web-link** tag triggers `ACTION_VIEW`, and from Android 17 an "open link"
   notification.
   A commercial sticker should therefore produce *a notification*, not silence.
5. **PD2** — if an AAR names an uninstalled package, the platform goes to Google Play; neither
   applicationId is published, so the outcome is a Play page for a listing that does not exist.
   **NoteTag ships with no AAR, so this does not apply to it** (O13).
6. **PD6** — reader mode **overrides** both AARs and the intent dispatch system, which is why each
   app's writer or inspect screen can see the sibling's tag at all — the mechanism behind §25's two
   foreign/protected rows.

**Every one of the six is to observe on-device**, together with **stopped-state dispatch**, where the
documentation and this project's own device row disagree (**[platform-doc] PD8** says a force-stopped
app gets no dispatch; **[device-observed]** 1C row 16 recorded a dispatch to a force-stopped *and*
data-cleared package, with only the never-launched install silent — arch §5.10, §8.1 Q2), and the
**Android 16+ per-app NFC allowlist** (**[platform-doc] PD9**), where two installed apps mean two
entries, a distinct icon and label per app matter for telling those entries apart (review addition 3),
and neither app calls `NfcAdapter.isTagIntentAllowed()`, so a denial is invisible in-app
(arch §8.1 Q3). Per the gate
verdict these are **final coexistence gates, not architecture blockers**.

### 7.3 The two rules that make this table safe

- **No overlapping ambient claim.** Each product declares exactly one `NDEF_DISCOVERED` filter, on an
  exact path, for its own external type; no tech catch-all, no compatibility filter, no claim on
  anything historical (O2). The 2024 `TECH_DISCOVERED` catch-all and `res/xml/nfc_tech_filter.xml`,
  deleted from master at `26ec9d0`, must not be re-inherited by the NoteTag rewrite.
- **Tag mutation is explicit-intent only** (invariant 12, §25). The ambient path cannot write, by
  construction; reader mode is entered only from an intentional destination.
- **Sibling-refusal wording is fixed and identical in both products** (ratified P11): where a product
  meets the other's tag it **names** it — "this tag belongs to another app", with the offending type
  string available from `Foreign.description` — and offers exactly two actions, **Write over it** and
  **Cancel**. No cross-product action of any kind is offered: no adopt, no bind, no migrate, no
  "keep". *Cancel* is the wording in both apps and in every table in this package.

---

## 8. Signing (§12)

| | NoteTag | ServiceTag |
|---|---|---|
| Keystore | `~/.config/notenfc/notenfc-release.jks` — **the existing noteNFC key, unchanged. Never deleted, replaced, rotated or overwritten** (§12). The key outlives the product name; the directory keeps its historical name deliberately, because renaming it would mutate the one thing §12 forbids mutating | `~/.config/servicetag/servicetag-release.jks` — **new** |
| Properties | `~/.config/notenfc/keystore.properties` | `~/.config/servicetag/keystore.properties` |
| Keys inside | `storeFile`, `storePassword`, `keyAlias`, `keyPassword` | the same four |
| Permissions | 0600 on the files, 0700 on the directory | the same |
| Read by | `app/build.gradle.kts` at configuration time via `System.getProperty("user.home") + "/.config/<dir>/keystore.properties"`; absent or empty → the release build type simply has no signing config **[code]** | the same mechanism |
| In the repository | nothing: no keystore, no properties file, no password, no alias. `.gitignore` covers `keystore.properties`, `*.jks`, `*.keystore` | the same |
| In CI | nothing. Release signing is entirely local; CI builds `assembleDebug` only and interpolates no `secrets.*` (§26, arch §4.2) | the same |
| Recorded | the certificate **SHA-256 fingerprint only**, in the evidence file. Hygiene item: the current `README.md:121-125` reproduces the signer DN **and** the full colon-separated SHA-256 in the repository (review correction 11) — §H's README rewrite reduces that to a pointer | the new certificate's SHA-256 fingerprint, recorded once. Fingerprints are public keys; passwords and keystores are never printed, committed, pasted or logged |
| Proof required | a signed release build, produced and verified (§12) | the same |

**Generating the ServiceTag key** — command shape, placeholders only, run interactively so no secret
reaches a transcript:

```bash
mkdir -p ~/.config/servicetag && chmod 700 ~/.config/servicetag

keytool -genkeypair -v \
  -keystore ~/.config/servicetag/servicetag-release.jks \
  -storetype PKCS12 -alias <ALIAS> \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=ServiceTag, O=<ORG>"
# keytool prompts for the password. Do NOT pass -storepass or -keypass.

cat > ~/.config/servicetag/keystore.properties <<'PROPS'
storeFile=<ABSOLUTE PATH TO servicetag-release.jks>
storePassword=<STORE PASSWORD>
keyAlias=<ALIAS>
keyPassword=<KEY PASSWORD>
PROPS
chmod 600 ~/.config/servicetag/keystore.properties ~/.config/servicetag/servicetag-release.jks

keytool -list -v -keystore ~/.config/servicetag/servicetag-release.jks -alias <ALIAS> | grep 'SHA256:'
```

**Backup and recovery** (§12). Both `~/.config/notenfc/` and `~/.config/servicetag/` go, as an
encrypted archive, to storage that is neither this workstation's disk nor any git repository. The
reason is in this estate's own history: the 2024 release's signing key is **not available**, which is
why an in-place upgrade of that install was impossible and why a new identity had to be minted at all
(arch §2.10, closed issue #29). The archive is verified restorable **before** the first signed
ServiceTag release, not after.

---

## 9. CI per repository (§26)

All three workflows keep the shape the current one has: `ubuntu-latest`, `actions/checkout@v4`,
`actions/setup-java@v4` with Temurin 17, `android-actions/setup-android@v3` (`platform-tools` only),
`gradle/actions/setup-gradle@v4`, `--console=plain`, and `actions/upload-artifact@v4` with
`if: always()` for test results (arch §4.2). Triggers stay `push` and `pull_request`. **No
`secrets.*` anywhere; no absolute home path, no developer-local Gradle state, no device id, no
private signing material in source** (§26).

| Repository | Steps |
|---|---|
| **ServiceTag** | checkout with **`submodules: recursive`** → JDK 17 → setup-android → setup-gradle → the submodule-pin assertion (§6.3) → `./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest :app:assembleDebug` → upload every `build/test-results` tree. Because the library's modules are subprojects of this build, one green also proves the catalog alias set agrees (§6.1) |
| **NoteTag** | the same shape, the same submodule steps, NoteTag's own module list. **CI must be run from scratch**: `c84b881` itself never ran on a runner, and its green Phase-0 evidence belongs to pre-rewrite twins whose SHAs no longer exist (review correction 8) |
| **nfc-tag-core** | checkout → JDK 17 → setup-android → setup-gradle → **`tools/forbidden-scan.sh`** → `./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :nfc-android:assembleDebug` → upload both `build/test-results` trees. The scan runs **before** the build, so a violation is the first thing a reader sees |

**No instrumented step in any of the three.** The emulator suites — the app device-proof tests and
the library's adapter tests — stay local (arch §4.2), and §15 keeps instrumented suites off the phone
entirely, because it holds the owner's real data.

**One caveat on what the device evidence proves.** The coexistence and dispatch observations are
collected from **debug builds** — that is what §15 installs and what the emulator runs — so they are
*debug-build evidence*. The signed-release requirement of §12 is discharged separately and only as a
**build-verified** claim: a release APK is produced, signed and its certificate fingerprint recorded,
but no device row is collected from a release build. Both facts are stated in the evidence file so
nobody later reads a debug observation as a release guarantee.

**Where CI sits in the order.** The canonical master order is **local D–G → remote K/L → phone H →
physical and coexistence I/J → docs and handoff M/N** (the runbook's front matter states it once and
every phase table obeys it). CI therefore becomes green *before* the phone is touched: the remote
work in K/L is what produces the first green run on a machine that is not this one, and the data
migration in H then proceeds against a ServiceTag build whose CI already passes. The one documented
departure from §27's literal step order is that `nfc-tag-core` is created and pushed early, because a
submodule needs a URL before phase G can be proved locally at all.

**Clean-checkout proof** is a per-repository acceptance, not a CI trick (§26): for each of the three,
`git clone --recurse-submodules <url> <tmp> && cd <tmp> && ./gradlew <that repo's CI task list>` from
a directory that has never held the project, and — for the two apps — once from a **second
workstation**, so "it builds" never means "it builds where the caches are".

---

## 10. Roadmap

### 10.1 ServiceTag (§31)

**Through Phase 4A, ready to begin Phase 3.** Landed: 0 (foundation), 1A (durable identity), 1B (NFC
identity), 1C (Compose shell), 2A (maintenance journal), 2B-1 (editors), 2B-2 (physical asset model),
4A (attachments) — arch §3. The order from here is **Phase 3 (schedules, reminders, scan-time
context, clock seam) → 3R → 4B → 5 → 6 → 7**. Never "ready for 2B" (§31). Schedules and reminders are
**future** work, not present capability (C1). Phase 7 carries the one NFC obligation the split does
not discharge: `targetSdk 37` + `DISPATCH_NFC_MESSAGE` (arch §5.1). Store-location migration is 4B
(§13). Phase 3 begins **after** the split completes — meaning after the last letter of the canonical
order, **local D–G → remote K/L → phone H → physical and coexistence I/J → docs and handoff M/N** —
and the split itself is scoped strictly to separation, shared extraction, identity migration and data
migration (§33).

### 10.2 NoteTag

The roadmap starts with the two transferred issues, retitled under the NoteTag name (§10, O8):

- **#6** "Generalize external note/deep-link support beyond Joplin" — *"Preserve the original purpose
  while making external-link handling generic."* Under O13/O14 this is now concrete: new providers
  use the `URI` kind unless a stable compact identifier earns its own kind value.
- **#36** "First-class deep-link support for Joplin, Obsidian, Logseq, Evernote, Notion, OneNote and
  Todoist" — whose body is the ownership statement that started this operation.

Also NoteTag's, and explicitly **not** split scope: an export/import of the local `LOCAL_REF` map
(O14), and whether `notetag://` ever gets a `VIEW` filter, which ratified P4 defers to that work.

### 10.3 nfc-tag-core versioning

- **Semantic versioning on git tags**, `nfc-tag-core-v<major>.<minor>.<patch>`, from
  **`nfc-tag-core-v0.1.0`** on the extraction commit. Two distinct moments, deliberately separated:
  the tag is **created** once the **standalone library is green** — it has to exist before either app
  can add a submodule pointing at it (runbook §B.1) — and it is **accepted as final** only once
  **both consuming apps are green against that exact tag** (runbook §A.4, §B.6). Between those two
  moments the tag is provisional: if integration forces a change, it is deleted and re-cut rather
  than consumed as-is, because a tag two apps have built against must never move.
  `0.x` while the API is still moving; `1.0.0` when a third consumer or an external user appears.
- **The tag is the unit of consumption.** Each app's submodule pointer is always a commit that
  `git describe --exact-match --match 'nfc-tag-core-v*' --tags` resolves (§6.3, §6.4). No
  mutable-branch consumption, no
  snapshot, no published artifact.
- **What a bump means.** *Patch*: no API change. *Minor*: additive API, or a behaviour change both
  apps want — `TagWriteSession`'s promotion, if it happens, is the expected `v0.2.0` (§4.7). *Major*:
  a removal or a signature change. A change only one app wants is not a library change; it is a sign
  the boundary is wrong, and the fix is to move policy back into that app.
- **Every bump updates the invariant list (§4.3) or says why it does not**, because the invariants,
  not the signatures, are what the apps depend on.
- **No release artifacts**: included as subprojects, there is nothing to attach. The tag *is* the
  release.

---

## 11. Proposal ledger — all ratified, none open

**There is no open proposal.** Gate 3 ratified the last five, and each now lives in the design text
rather than in a list:

| # | Ruling | Where it now lives |
|---|---|---|
| **P4** | approved — NoteTag declares no `notetag://` `VIEW` filter at reconstruction; the scheme is reserved for #6/#36 | §3 identity table; §10.2 |
| **P11** | approved, **normalised everywhere**: a sibling's tag is named and offers exactly **Write over it / Cancel**, with no cross-product action and no "keep" wording | §7.3, and every row of §7 and of the runbook's §D/§E |
| **P19** | approved, **with the G2 crash-consistency invariant**: the local store is a single atomically-replaced JSON file, and the `LOCAL_REF` mapping commits before the tag is written; once a write has been attempted the mapping is retained unless it is provable that no bytes reached the tag (H1) | §4.9 |
| **P20** | approved — Compose, one activity, two tiny screens | §4.9 |
| **P21** | approved — ServiceTag keeps its AAR by default pending the §D.3 dispatch spike; NoteTag ships without one per O13 | §3 identity table; §4.9 |

Ratified earlier, and likewise in the text: P1 and P6 (library branch and Kotlin package roots; O15
removed Maven coordinates entirely), P2, P3 and P5 (cosmetic identities), P7 (the Kotlin-stdlib-only
`nfc-core`), P8 (the formatted-size capacity rule, now corrected by G1), P10 (rename the fixture
directory rather than allow-list it), P13 (create the library remote first), P17 (product-prefixed
first tags).

Withdrawn as superseded: the contingent versioned-UUID body helper (O13/O14 and the two-consumer
rule); every `notenfc`-era reconstruction and Migrate-tag proposal (O1, O2, O7); the composite-build
choice (O15); and the three reconstructions of brief text now on disk — the §22 word list, the §27
order and the §34 gate list are quoted, not inferred.

---

## 12. Definition of done for this document's subject matter (§37)

NoteTag is the small NFC note/link utility; ServiceTag is the physical-asset service and maintenance
product; both have distinct canonical tag and Android identities — distinct applicationId, external
record type, scheme, label **and launcher icon** — and share one small product-neutral NFC
implementation, pinned identically by both. The owner's real ServiceTag data and attachments migrate
through the supported backup-set boundary with stable identities intact. Both applications coexist on
the same device, proven by observation rather than inferred from manifests. All three repositories are
independently green from a clean checkout. Every invariant in §4.3 has a test in the library and,
where it is about a consumer, in each consumer — including the sibling-isolation test in both
directions. The forbidden-dependency scan passes with an allow file whose every entry was inspected.
The identity tables in §3 are true of the built APKs, not just of the source.
