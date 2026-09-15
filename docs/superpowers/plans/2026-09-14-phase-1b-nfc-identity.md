# Phase 1B — NFC Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put the durable tag identity from Phase 1A onto physical tags: noteNFC tag payload format v1 (a random tag id + an Android Application Record), a resolver that turns any scanned tag into one of a closed set of outcomes, reader-mode scanning and a writer that reads first, asks before overwriting, checks capacity, writes off the main thread, reads back and compares, and a manifest that dispatches only our two NDEF record types — with the 2024 legacy generation deleted.

**Architecture:** `:core` (pure Kotlin) gains the v1 codec (`TagPayload.V1`/`NewerVersion`, `NdefCodec.encodeV1`), `OverwritePolicy`, `LinkLaunchPolicy`, `TagRoute` (the `notenfc://tag/<uuid>` parser), and the use cases `ResolveTag`, `BindTag`, `ProvisionTag`, `CreateAsset`, `SaveLink`, `OpenLink`, all tested with the in-memory fakes. `:app` gains a thin NFC adapter (`NdefBridge`, `NfcReaderModeSession`, `TagWriter` over `android.nfc`), an exported `NfcDispatchActivity` (both `NDEF_DISCOVERED` filters plus the `notenfc://tag` route; no `TECH_DISCOVERED`), and three **interim plain-Views screens** in package `ui.interim` (`TagToolsActivity` launcher, `WriteTagActivity`, `ShareLinkActivity`) that exist so the phase can be proven on a real device; Phase 1C replaces them with Compose. `LegacyKey`, `LegacyLinkPolicy`, `NdefCodec.encodeLegacy` and the three legacy activities are deleted (D13 §2); legacy `md5_short` decode stays as best-effort recognition.

**Tech Stack:** unchanged from Phase 1A — AGP 9.4.0 (built-in Kotlin 2.4.20), Room 3.0.3, kotlinx-coroutines 1.10.2, JUnit 5 + kotlin-test in `:core`, JUnit 4 + coroutines-test + `sqlite-bundled-jvm` in `:app` (no Robolectric). **No new dependencies.** Android NFC APIs: `NfcAdapter.enableReaderMode`, `android.nfc.tech.Ndef` / `NdefFormatable`, `NdefMessage` / `NdefRecord`.

**Spec:** `docs/design/07-implementation-sequence.md` §1B (as revised by `docs/design/13-compatibility-policy.md` §2–§3), `docs/design/03-target-architecture.md` §9–§10 and §13, `docs/design/04-domain-data-model.md` §3, `docs/design/09-security-privacy.md` (rows "NFC payload", "NFC dispatch", "Tag writing", "Deep links", "Outbound URI launching", "Exported components"), `docs/design/10-testing-strategy.md`, `docs/design/issues/new-tag-payload-v1-legacy-resolver.md` (#31), `docs/design/issues/new-deeplink-contract.md`, `docs/design/issues/rewrite-06.md`.

## Global Constraints

- **Payload format v1 (D4 §3):** NDEF record 0 = `TNF_EXTERNAL_TYPE` (0x04), type `com.loosecannon.notenfc:tag`, payload exactly **18 bytes**: byte 0 version `0x01`, byte 1 flags `0x00` (must be zero), bytes 2..17 the tag id as an RFC 4122 UUID in network byte order (most-significant 8 bytes then least-significant 8 bytes). Record 1 = Android Application Record (`TNF_EXTERNAL_TYPE`, type `android.com:pkg`, payload the package name **`com.loosecannon.notenfc`** — D13 §4; the D4 §3 text that still says `com.looseCannon.noteNFC` is corrected in Task 8). Version byte > `0x01` → `NewerVersion(n)`, never parsed further. Version `0x00`, wrong length, or non-zero flags → `Malformed`. "v1" is the payload version byte, never an application generation.
- **Tag identity ≠ asset identity (D4 §3, R-2):** for a `V1` tag the `nfc_tag.id` **equals** the id written on the tag (`payload_key == id`); for `LEGACY_MD5` rows the id is a fresh UUID and `payload_key` is the 8-hex key. Tag ids are canonical lowercase UUID strings (`UUID.toString()` form); the codec refuses anything else.
- **Legacy `md5_short` (D13 §2–§3):** decode stays; the `NDEF_DISCOVERED` filter for `vnd.android.nfc://ext/com.loosecannon.notenfc:md5_short` stays; no lookup of old data is ever attempted; an unknown legacy tag offers **bind as-is** or **rewrite in v1**. `LegacyKey`, `LegacyLinkPolicy`, `NdefCodec.encodeLegacy`, their tests, and the three legacy activities are **deleted**. No re-link, no collision handling, no prefs migration.
- **Dispatch (D3 §9, D9):** `NfcDispatchActivity` is the only NFC-exported component; filters = `NDEF_DISCOVERED` for `:tag`, `NDEF_DISCOVERED` for `:md5_short`, and `VIEW` for `notenfc://tag/…` (`BROWSABLE`). `TECH_DISCOVERED` and `res/xml/nfc_tech_filter.xml` are removed. The activity reads **only** `EXTRA_NDEF_MESSAGES` / `EXTRA_TAG` (for the hardware uid) or the intent `data` URI; every other extra is ignored. `targetSdk` stays 36, so the Android 17 `DISPATCH_NFC_MESSAGE` permission is **not** added (Phase 7).
- **Writer (D3 §9, D9 "Tag writing"):** reader mode only on the Write screen and the tools screen; read before write; `OverwritePolicy` decides whether a confirmation is required (anything except an empty tag or the same v1 id); check `Ndef.maxSize` against the encoded message; write off the main thread; read back and compare the record list for byte equality; optional lock (`makeReadOnly`) behind an explicit irreversible warning; `:tag` record first, AAR second.
- **Links (D3 §10, D9 "Outbound URI launching"):** `LinkLaunchPolicy` runs at save time **and** launch time. Allowlist `joplin`, `obsidian`, `logseq`, `http`, `https`; hard block `javascript`, `file`, `content`, `intent`, `android-app`, `tel`, `sms`, `mailto`; any other scheme needs one explicit confirmation and is stored as `LinkKind.OTHER`. Share text: only the first URI token is stored, never the raw text. Manifest declares `<queries>` for the five allowed schemes; `ActivityNotFoundException` is caught regardless. A link tag launches immediately with no interstitial (R-7).
- **Deep link (`new-deeplink-contract`):** `notenfc://tag/<uuid>` resolves exactly as a scan would; navigation only; malformed or unknown ids show a message and never crash; extras ignored.
- **Layering:** `:core` must not import `android.*`/`androidx.*`; all business rules (codec, policies, resolution, binding) live there and are JVM-tested; `:app` holds only adapters and interim screens. Use cases take repositories + `UnitOfWork` + `Clock` + `IdGenerator` through constructors and are wired in `AppGraph`.
- **Interim UI:** plain `android.app.Activity` + `android.widget` (like `DebugBackupActivity`), no AppCompat, no Compose, no Material, no new dependencies, no Apollo styling (D12 is Phase 1C+). Screens live in `com.loosecannon.notenfc.ui.interim` and say so in their KDoc.
- **Tests:** `:core` JUnit 5 with `kotlin.test` assertions, block-bodied `@Test fun x() { … }` (an expression-bodied test returning a non-Unit value is silently skipped). `:app` JUnit 4 + `runTest` + `inMemoryDb()`; Android NFC classes cannot be exercised on the JVM (mockable android.jar), so `NdefBridge`/`TagWriter`/activities are compile-checked and device-proven, not unit-tested.
- **Commits:** casual, terse, human (repo `CLAUDE.md`); **never** `Co-Authored-By` or any AI attribution. Repo-local git identity is GonzRon. Do not push. Do not touch the sibling checkout `~/Documents/Projects/AndroidStudioProjects/noteNFC`.
- **Build/test commands** (run from the worktree root): `./gradlew :core:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew :app:assembleDebug`, full gate `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`.

---

## File map

| Path | Responsibility |
|---|---|
| `core/src/main/kotlin/com/loosecannon/notenfc/core/nfc/NdefCodec.kt` | `NdefRecordData`, `TagPayload` (+`V1`, `NewerVersion`), v1 encode/decode, legacy decode, AAR record |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/nfc/OverwritePolicy.kt` | proceed-or-confirm before writing over existing content |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/nfc/TagRoute.kt` | `notenfc://tag/<uuid>` → `TagPayload` |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/links/LinkLaunchPolicy.kt` | scheme allow/block lists, URI extraction from share text, kind detection |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/ResolveTag.kt` | `Resolution` + lookup/last-scanned bookkeeping |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/BindTag.kt` | bind a scanned tag (known or unknown) to an asset/link |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/ProvisionTag.kt` | begin/complete/abandon a v1 row around a physical write |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/CreateAsset.kt` | minimal asset creation for the bind flow |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/SaveLink.kt`, `OpenLink.kt` | link save/launch through `LinkLaunchPolicy` |
| `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/TagTargets.kt` | `UnknownTarget` + existence check shared by Bind/Provision |
| `app/src/main/kotlin/com/loosecannon/notenfc/nfc/NdefBridge.kt` | `NdefMessage` ⇄ `List<NdefRecordData>`, intent extras, uid hex |
| `app/src/main/kotlin/com/loosecannon/notenfc/nfc/NfcReaderModeSession.kt` | `enableReaderMode` lifecycle wrapper |
| `app/src/main/kotlin/com/loosecannon/notenfc/nfc/TagWriter.kt` | inspect / write / read-back / lock over `Ndef` + `NdefFormatable` |
| `app/src/main/kotlin/com/loosecannon/notenfc/nfc/NfcDispatchActivity.kt` | exported dispatch + interim resolution screen |
| `app/src/main/kotlin/com/loosecannon/notenfc/links/LinkLauncher.kt` | `ACTION_VIEW` with `ActivityNotFoundException` handling |
| `app/src/main/kotlin/com/loosecannon/notenfc/ui/interim/{TargetPicker,WriteTagActivity,TagToolsActivity,ShareLinkActivity}.kt` | interim screens |
| `app/src/main/kotlin/com/loosecannon/notenfc/di/AppGraph.kt` | wires the six use cases + `appScope` |
| `app/src/main/AndroidManifest.xml` | new activities, `<queries>`, NDEF + deep-link filters; legacy entries gone |
| `app/src/main/res/layout/{activity_nfc_dispatch,activity_write_tag,activity_tag_tools}.xml`, `values/strings.xml` | interim layouts/strings |
| `app/src/test/kotlin/com/loosecannon/notenfc/nfc/TagUseCasesRoomTest.kt` | resolver/bind/provision against real Room |
| `docs/design/phase-1b-evidence.md` | what shipped, test counts, device checklist vs D7 §1B exit criteria |

---

### Task 1: Retire the legacy generation

**Files:**
- Delete: `app/src/main/kotlin/com/loosecannon/notenfc/legacy/MainActivity.kt`, `NFCHandlerActivity.kt`, `LaunchNoteNFCLinkActivity.kt`
- Delete: `app/src/main/res/layout/activity_main.xml`, `app/src/main/res/layout/write_nfc_link.xml`, `app/src/main/res/xml/nfc_tech_filter.xml`
- Delete: `core/src/main/kotlin/com/loosecannon/notenfc/core/nfc/LegacyKey.kt`, `core/src/main/kotlin/com/loosecannon/notenfc/core/links/LegacyLinkPolicy.kt`
- Delete: `core/src/test/kotlin/com/loosecannon/notenfc/core/nfc/LegacyKeyTest.kt`, `core/src/test/kotlin/com/loosecannon/notenfc/core/links/LegacyLinkPolicyTest.kt`
- Modify: `app/src/main/AndroidManifest.xml` (remove the three `<activity>` elements; keep `<uses-permission>`, `<uses-feature>`, `<application android:name=… icon label>`)
- Modify: `core/src/main/kotlin/com/loosecannon/notenfc/core/nfc/NdefCodec.kt` (remove `encodeLegacy`; rename `LEGACY_DOMAIN` → `DOMAIN`)
- Modify: `core/src/test/kotlin/com/loosecannon/notenfc/core/nfc/NdefCodecTest.kt` (drop `encodeLegacyRoundTrips`; keep every decode test)
- Modify: `app/src/main/res/values/strings.xml` (only `app_name` remains)

**Interfaces:**
- Consumes: nothing new.
- Produces: `NdefCodec.DOMAIN = "com.loosecannon.notenfc"`, `NdefCodec.LEGACY_TYPE_NAME = "md5_short"`, `NdefCodec.LEGACY_TYPE`, `NdefCodec.TNF_EXTERNAL_TYPE = 0x04`, `NdefCodec.decode(List<NdefRecordData>): TagPayload` (unchanged behaviour for legacy/foreign/malformed/empty). The app temporarily has **no launcher activity** in the release manifest (the debug source set still contributes `DebugBackupActivity`); Task 7 adds the new launcher.

- [ ] **Step 1: Delete the files.**

```bash
git rm app/src/main/kotlin/com/loosecannon/notenfc/legacy/MainActivity.kt \
       app/src/main/kotlin/com/loosecannon/notenfc/legacy/NFCHandlerActivity.kt \
       app/src/main/kotlin/com/loosecannon/notenfc/legacy/LaunchNoteNFCLinkActivity.kt \
       app/src/main/res/layout/activity_main.xml app/src/main/res/layout/write_nfc_link.xml \
       app/src/main/res/xml/nfc_tech_filter.xml \
       core/src/main/kotlin/com/loosecannon/notenfc/core/nfc/LegacyKey.kt \
       core/src/main/kotlin/com/loosecannon/notenfc/core/links/LegacyLinkPolicy.kt \
       core/src/test/kotlin/com/loosecannon/notenfc/core/nfc/LegacyKeyTest.kt \
       core/src/test/kotlin/com/loosecannon/notenfc/core/links/LegacyLinkPolicyTest.kt
```

- [ ] **Step 2: Reduce the manifest** to exactly:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.NFC" />
    <uses-feature android:name="android.hardware.nfc" android:required="true" />

    <application
        android:name="com.loosecannon.notenfc.NoteNfcApp"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name">
    </application>
</manifest>
```

- [ ] **Step 3: Trim `NdefCodec.kt`.** Replace the whole file with:

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

    /** The NFC Forum external-type domain for every noteNFC record; lower-case on the wire. */
    const val DOMAIN: String = "com.loosecannon.notenfc"

    /** The 2024 record type. Read-only, best-effort recognition (D13 §2); never written again. */
    const val LEGACY_TYPE_NAME: String = "md5_short"
    const val LEGACY_TYPE: String = "$DOMAIN:$LEGACY_TYPE_NAME"

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
}
```

- [ ] **Step 4: Trim `NdefCodecTest.kt`.** Remove the `encodeLegacyRoundTrips` test and the now-unused `assertContentEquals` import. Everything else stays verbatim (the `legacy(...)` helper already builds records without `encodeLegacy`).

- [ ] **Step 5: Trim `strings.xml`** to:

```xml
<resources>
    <string name="app_name">noteNFC</string>
</resources>
```

- [ ] **Step 6: Build and test.**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL; `:core` runs 51 − 6 (LegacyKeyTest) − 5 (LegacyLinkPolicyTest) − 1 (encodeLegacyRoundTrips) = **39** tests, `:app` still 30. Also confirm nothing else references the deleted symbols: `grep -rn "LegacyKey\|LegacyLinkPolicy\|encodeLegacy\|LEGACY_DOMAIN" app/src core/src` prints nothing.

- [ ] **Step 7: Commit.**

```bash
git add -A app/src core/src
git commit -m "drop the 2024 legacy activities, LegacyKey and the tech-discovered catch-all"
```

---

### Task 2: Payload format v1 codec and overwrite policy

**Files:**
- Modify: `core/src/main/kotlin/com/loosecannon/notenfc/core/nfc/NdefCodec.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/nfc/OverwritePolicy.kt`
- Create: `core/src/test/kotlin/com/loosecannon/notenfc/core/nfc/NdefCodecV1Test.kt`
- Create: `core/src/test/kotlin/com/loosecannon/notenfc/core/nfc/OverwritePolicyTest.kt`

**Interfaces:**
- Consumes: `TagId` (`core.model.Ids`), `NdefRecordData`, `TagPayload`.
- Produces:
  - `TagPayload.V1(val tagId: TagId)`, `TagPayload.NewerVersion(val version: Int)`
  - `NdefCodec.V1_TYPE_NAME = "tag"`, `V1_TYPE = "com.loosecannon.notenfc:tag"`, `V1_VERSION = 0x01`, `V1_FLAGS = 0x00`, `V1_PAYLOAD_LENGTH = 18`, `PACKAGE_NAME = "com.loosecannon.notenfc"`, `AAR_TYPE = "android.com:pkg"`
  - `NdefCodec.encodeV1(tagId: TagId): List<NdefRecordData>` (2 records: `:tag`, AAR), `NdefCodec.v1Record(tagId): NdefRecordData`, `NdefCodec.applicationRecord(): NdefRecordData`, `NdefCodec.requireCanonicalUuid(tagId): java.util.UUID` (throws `IllegalArgumentException`)
  - `OverwriteDecision.Proceed | OverwriteDecision.Confirm(val reason: String)`, `OverwritePolicy.decide(existing: TagPayload, intended: TagId): OverwriteDecision`

- [ ] **Step 1: Write the failing tests.** `NdefCodecV1Test.kt`:

```kotlin
package com.loosecannon.notenfc.core.nfc

import com.loosecannon.notenfc.core.model.TagId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalStdlibApi::class)
class NdefCodecV1Test {
    private val id = TagId("123e4567-e89b-12d3-a456-426614174000")
    private val idBytes = "123e4567e89b12d3a456426614174000".hexToByteArray()

    private fun v1(payload: ByteArray) =
        NdefRecordData(NdefCodec.TNF_EXTERNAL_TYPE, NdefCodec.V1_TYPE.toByteArray(Charsets.US_ASCII), payload)

    @Test fun exactByteLayout() {
        val rec = NdefCodec.v1Record(id)
        assertEquals(0x04, rec.tnf)
        assertContentEquals("com.loosecannon.notenfc:tag".toByteArray(Charsets.US_ASCII), rec.type)
        assertEquals(18, rec.payload.size)
        assertContentEquals(byteArrayOf(0x01, 0x00) + idBytes, rec.payload)
    }

    @Test fun messageIsTagRecordThenApplicationRecord() {
        val msg = NdefCodec.encodeV1(id)
        assertEquals(2, msg.size)
        assertEquals(NdefCodec.v1Record(id), msg[0])
        assertEquals(0x04, msg[1].tnf)
        assertContentEquals("android.com:pkg".toByteArray(Charsets.US_ASCII), msg[1].type)
        assertContentEquals("com.loosecannon.notenfc".toByteArray(Charsets.US_ASCII), msg[1].payload)
    }

    @Test fun roundTrips() {
        assertEquals(TagPayload.V1(id), NdefCodec.decode(NdefCodec.encodeV1(id)))
    }

    @Test fun decodedIdIsCanonicalLowercase() {
        val p = NdefCodec.decode(listOf(v1(byteArrayOf(0x01, 0x00) + idBytes)))
        assertEquals(TagPayload.V1(TagId("123e4567-e89b-12d3-a456-426614174000")), p)
    }

    @Test fun refusesNonCanonicalIdOnEncode() {
        assertFailsWith<IllegalArgumentException> { NdefCodec.v1Record(TagId("123E4567-E89B-12D3-A456-426614174000")) }
        assertFailsWith<IllegalArgumentException> { NdefCodec.v1Record(TagId("not-a-uuid")) }
        assertFailsWith<IllegalArgumentException> { NdefCodec.v1Record(TagId("")) }
    }

    @Test fun newerVersionIsReportedNotParsed() {
        assertEquals(TagPayload.NewerVersion(2), NdefCodec.decode(listOf(v1(byteArrayOf(0x02, 0x00) + idBytes))))
        // a future format may have any length; the version byte alone decides
        assertEquals(TagPayload.NewerVersion(0x7f), NdefCodec.decode(listOf(v1(byteArrayOf(0x7f, 0x01, 0x02)))))
        assertEquals(TagPayload.NewerVersion(0xff), NdefCodec.decode(listOf(v1(byteArrayOf(0xff.toByte())))))
    }

    @Test fun versionZeroIsMalformed() {
        assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(v1(byteArrayOf(0x00, 0x00) + idBytes))))
    }

    @Test fun wrongLengthIsMalformed() {
        assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(v1(byteArrayOf(0x01, 0x00) + idBytes.copyOf(15)))))
        assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(v1(byteArrayOf(0x01, 0x00) + idBytes + 0x00))))
        assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(v1(byteArrayOf(0x01)))))
    }

    @Test fun nonZeroFlagsIsMalformed() {
        assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(v1(byteArrayOf(0x01, 0x01) + idBytes))))
        assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(v1(byteArrayOf(0x01, 0x80.toByte()) + idBytes))))
    }

    @Test fun emptyPayloadIsMalformed() {
        assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(v1(ByteArray(0)))))
    }

    @Test fun applicationRecordAloneIsForeign() {
        assertIs<TagPayload.Foreign>(NdefCodec.decode(listOf(NdefCodec.applicationRecord())))
    }

    @Test fun tagRecordUnderWrongTnfIsForeign() {
        val rec = NdefRecordData(0x02, NdefCodec.V1_TYPE.toByteArray(Charsets.US_ASCII), byteArrayOf(0x01, 0x00) + idBytes)
        assertIs<TagPayload.Foreign>(NdefCodec.decode(listOf(rec)))
    }

    @Test fun legacyRecordStillDecodes() {
        val rec = NdefRecordData(NdefCodec.TNF_EXTERNAL_TYPE, NdefCodec.LEGACY_TYPE.toByteArray(Charsets.US_ASCII), "63b37acf".toByteArray())
        assertEquals(TagPayload.LegacyMd5("63b37acf"), NdefCodec.decode(listOf(rec)))
    }

    @Test fun fitsAnNtag213() {
        // short-record header = flags + type length + payload length (3 bytes); plus NDEF TLV (2) + terminator (1)
        val onTag = NdefCodec.encodeV1(id).sumOf { 3 + it.type.size + it.payload.size } + 3
        assertTrue(onTag <= 144, "v1 message needs $onTag bytes; NTAG213 holds 144")
    }
}
```

`OverwritePolicyTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.nfc

import com.loosecannon.notenfc.core.model.TagId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OverwritePolicyTest {
    private val mine = TagId("123e4567-e89b-12d3-a456-426614174000")
    private val other = TagId("00000000-0000-4000-8000-000000000001")

    @Test fun emptyTagProceeds() { assertEquals(OverwriteDecision.Proceed, OverwritePolicy.decide(TagPayload.Empty, mine)) }
    @Test fun sameV1IdProceeds() { assertEquals(OverwriteDecision.Proceed, OverwritePolicy.decide(TagPayload.V1(mine), mine)) }
    @Test fun differentV1IdConfirms() { assertIs<OverwriteDecision.Confirm>(OverwritePolicy.decide(TagPayload.V1(other), mine)) }
    @Test fun legacyConfirms() { assertIs<OverwriteDecision.Confirm>(OverwritePolicy.decide(TagPayload.LegacyMd5("63b37acf"), mine)) }
    @Test fun foreignConfirms() { assertIs<OverwriteDecision.Confirm>(OverwritePolicy.decide(TagPayload.Foreign("tnf=1 type=U"), mine)) }
    @Test fun malformedConfirms() { assertIs<OverwriteDecision.Confirm>(OverwritePolicy.decide(TagPayload.Malformed("x"), mine)) }
    @Test fun newerVersionConfirms() { assertIs<OverwriteDecision.Confirm>(OverwritePolicy.decide(TagPayload.NewerVersion(3), mine)) }
    @Test fun reasonNamesWhatIsThere() {
        val c = OverwritePolicy.decide(TagPayload.LegacyMd5("63b37acf"), mine) as OverwriteDecision.Confirm
        assertTrue("63b37acf" in c.reason)
    }
}
```

- [ ] **Step 2: Run to verify they fail.**

Run: `./gradlew :core:test --tests '*NdefCodecV1Test*' --tests '*OverwritePolicyTest*'`
Expected: compilation failure (`TagPayload.V1`, `NdefCodec.v1Record`, `OverwritePolicy` unresolved).

- [ ] **Step 3: Implement.** Replace `NdefCodec.kt` with:

```kotlin
package com.loosecannon.notenfc.core.nfc

import com.loosecannon.notenfc.core.model.TagId
import java.nio.ByteBuffer
import java.util.UUID

/** Android-free view of one NDEF record (mirrors android.nfc.NdefRecord's tnf/type/payload). */
data class NdefRecordData(val tnf: Int, val type: ByteArray, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is NdefRecordData && tnf == other.tnf && type.contentEquals(other.type) && payload.contentEquals(other.payload)
    override fun hashCode(): Int = 31 * (31 * tnf + type.contentHashCode()) + payload.contentHashCode()
}

sealed interface TagPayload {
    /** noteNFC tag payload format v1: the tag carries a random tag id and nothing else (D4 §3). */
    data class V1(val tagId: TagId) : TagPayload

    /** The 2024 `md5_short` record; recognised best-effort, never written (D13). */
    data class LegacyMd5(val key: String) : TagPayload

    /** A `:tag` record whose version byte is above what this build understands. Never parsed. */
    data class NewerVersion(val version: Int) : TagPayload

    data class Foreign(val description: String) : TagPayload
    data class Malformed(val reason: String) : TagPayload
    data object Empty : TagPayload
}

/**
 * Pure-bytes codec for noteNFC tag payload format v1 and the legacy record. Android `NdefRecord`
 * objects are built only in the `:app` NFC adapter (D3 §9).
 */
object NdefCodec {
    const val TNF_EXTERNAL_TYPE: Int = 0x04

    /** The NFC Forum external-type domain for every noteNFC record; lower-case on the wire. */
    const val DOMAIN: String = "com.loosecannon.notenfc"

    const val V1_TYPE_NAME: String = "tag"
    const val V1_TYPE: String = "$DOMAIN:$V1_TYPE_NAME"
    const val V1_VERSION: Int = 0x01
    const val V1_FLAGS: Int = 0x00
    const val V1_PAYLOAD_LENGTH: Int = 18

    /** The package an Android Application Record pins; must equal the app's applicationId (D13 §4). */
    const val PACKAGE_NAME: String = "com.loosecannon.notenfc"
    const val AAR_TYPE: String = "android.com:pkg"

    /** The 2024 record type. Read-only, best-effort recognition (D13 §2); never written again. */
    const val LEGACY_TYPE_NAME: String = "md5_short"
    const val LEGACY_TYPE: String = "$DOMAIN:$LEGACY_TYPE_NAME"

    private val legacyKeyPattern = Regex("^[0-9a-f]{8}$")

    /** Android dispatches on the first record of the first message; so do we. */
    fun decode(records: List<NdefRecordData>): TagPayload {
        val first = records.firstOrNull() ?: return TagPayload.Empty
        val type = String(first.type, Charsets.US_ASCII)
        if (first.tnf != TNF_EXTERNAL_TYPE) return TagPayload.Foreign("tnf=${first.tnf} type=$type")
        return when (type) {
            V1_TYPE -> decodeV1(first.payload)
            LEGACY_TYPE -> decodeLegacy(first.payload)
            else -> TagPayload.Foreign("tnf=${first.tnf} type=$type")
        }
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

    private fun decodeLegacy(payload: ByteArray): TagPayload {
        val key = String(payload, Charsets.UTF_8)
        if (!legacyKeyPattern.matches(key)) {
            return TagPayload.Malformed("legacy payload is not 8 lowercase hex chars: '$key'")
        }
        return TagPayload.LegacyMd5(key)
    }

    /** The whole message for a v1 tag: the `:tag` record first, the AAR second (D4 §3). */
    fun encodeV1(tagId: TagId): List<NdefRecordData> = listOf(v1Record(tagId), applicationRecord())

    fun v1Record(tagId: TagId): NdefRecordData {
        val uuid = requireCanonicalUuid(tagId)
        val payload = ByteBuffer.allocate(V1_PAYLOAD_LENGTH)
            .put(V1_VERSION.toByte())
            .put(V1_FLAGS.toByte())
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
        return NdefRecordData(TNF_EXTERNAL_TYPE, V1_TYPE.toByteArray(Charsets.US_ASCII), payload)
    }

    /** Byte-identical to `NdefRecord.createApplicationRecord(PACKAGE_NAME)`. */
    fun applicationRecord(): NdefRecordData = NdefRecordData(
        tnf = TNF_EXTERNAL_TYPE,
        type = AAR_TYPE.toByteArray(Charsets.US_ASCII),
        payload = PACKAGE_NAME.toByteArray(Charsets.US_ASCII),
    )

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
```

`OverwritePolicy.kt`:

```kotlin
package com.loosecannon.notenfc.core.nfc

import com.loosecannon.notenfc.core.model.TagId

sealed interface OverwriteDecision {
    data object Proceed : OverwriteDecision

    /** The tag already holds something worth a question; [reason] is shown verbatim to the user. */
    data class Confirm(val reason: String) : OverwriteDecision
}

/**
 * Read-before-write rule (D3 §9): only an empty tag, or a tag that already carries the very id we
 * are about to write (a retry), is written without asking.
 */
object OverwritePolicy {
    fun decide(existing: TagPayload, intended: TagId): OverwriteDecision = when (existing) {
        TagPayload.Empty -> OverwriteDecision.Proceed
        is TagPayload.V1 ->
            if (existing.tagId == intended) OverwriteDecision.Proceed
            else OverwriteDecision.Confirm("a different noteNFC tag (${existing.tagId.value})")
        is TagPayload.LegacyMd5 -> OverwriteDecision.Confirm("a legacy noteNFC tag (${existing.key})")
        is TagPayload.NewerVersion -> OverwriteDecision.Confirm("a noteNFC tag written by a newer app (format ${existing.version})")
        is TagPayload.Foreign -> OverwriteDecision.Confirm("foreign NDEF content (${existing.description})")
        is TagPayload.Malformed -> OverwriteDecision.Confirm("unreadable NDEF content (${existing.reason})")
    }
}
```

- [ ] **Step 4: Run the tests.**

Run: `./gradlew :core:test`
Expected: PASS; 39 + 15 + 8 = **62** tests.

- [ ] **Step 5: Commit.**

```bash
git add core/src
git commit -m "tag payload format v1: codec, AAR, overwrite policy"
```

---

### Task 3: Link policy, `TagRoute`, and the link use cases

**Files:**
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/links/LinkLaunchPolicy.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/nfc/TagRoute.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/SaveLink.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/OpenLink.kt`
- Test: `core/src/test/kotlin/com/loosecannon/notenfc/core/links/LinkLaunchPolicyTest.kt`, `core/src/test/kotlin/com/loosecannon/notenfc/core/nfc/TagRouteTest.kt`, `core/src/test/kotlin/com/loosecannon/notenfc/core/usecase/LinkUseCasesTest.kt`

**Interfaces:**
- Consumes: `LinkKind`, `ExternalLink`, `LinkId`, `LinkRepository`, `UnitOfWork`, `IdGenerator`, `Clock`, `TagPayload`, `TagId`; test fakes `InMemoryLinkRepository`, `FakeUnitOfWork` (`core/src/test/.../testing/InMemoryRepositories.kt`).
- Produces:
  - `LinkCheck.Accepted(kind, uri) | NeedsConfirmation(scheme, uri) | Rejected(reason)`
  - `LinkLaunchPolicy.ALLOWED_SCHEMES`, `BLOCKED_SCHEMES`, `extractUri(sharedText: String?): String?`, `schemeOf(uri): String?`, `kindOf(scheme): LinkKind`, `check(uri): LinkCheck`
  - `TagRoute.SCHEME = "notenfc"`, `TagRoute.HOST = "tag"`, `TagRoute.parse(scheme: String?, host: String?, pathSegments: List<String>): TagPayload?` (null = not this route)
  - `SaveLink(links, uow, ids, clock).run(uri: String, label: String?, confirmedOther: Boolean = false): ExternalLink`; throws `LinkRefused(reason)` / `LinkNeedsConfirmation(scheme)`
  - `OpenLink(links, uow, clock).run(id: LinkId): OpenLink.Outcome` where `Outcome = Launch(uri, link) | Refused(link, reason) | Missing(id)`

- [ ] **Step 1: Write the failing tests.** `LinkLaunchPolicyTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.links

import com.loosecannon.notenfc.core.model.LinkKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LinkLaunchPolicyTest {
    private val joplin = "joplin://x-callback-url/openNote?id=0123456789abcdef0123456789abcdef"

    @Test fun extractsTheFirstUriFromShareText() {
        assertEquals(joplin, LinkLaunchPolicy.extractUri("Pool chemistry log\n$joplin"))
        assertEquals(joplin, LinkLaunchPolicy.extractUri("$joplin (shared from Joplin)"))
        assertEquals("https://a.example/x", LinkLaunchPolicy.extractUri("see https://a.example/x and https://b.example/y"))
    }
    @Test fun trailingPunctuationIsNotPartOfTheUri() {
        assertEquals("https://a.example/x", LinkLaunchPolicy.extractUri("Look at https://a.example/x."))
        assertEquals("https://a.example/x", LinkLaunchPolicy.extractUri("(https://a.example/x)"))
    }
    @Test fun noUriMeansNull() {
        assertNull(LinkLaunchPolicy.extractUri("just some words"))
        assertNull(LinkLaunchPolicy.extractUri(null))
        assertNull(LinkLaunchPolicy.extractUri(""))
    }
    @Test fun allowedSchemesAreAcceptedWithTheirKind() {
        assertEquals(LinkCheck.Accepted(LinkKind.JOPLIN, joplin), LinkLaunchPolicy.check(joplin))
        assertEquals(LinkKind.OBSIDIAN, (LinkLaunchPolicy.check("obsidian://open?vault=v&file=f") as LinkCheck.Accepted).kind)
        assertEquals(LinkKind.LOGSEQ, (LinkLaunchPolicy.check("logseq://graph/g?page=p") as LinkCheck.Accepted).kind)
        assertEquals(LinkKind.WEB, (LinkLaunchPolicy.check("http://a.example/") as LinkCheck.Accepted).kind)
        assertEquals(LinkKind.WEB, (LinkLaunchPolicy.check("https://a.example/") as LinkCheck.Accepted).kind)
    }
    @Test fun schemeMatchingIsCaseInsensitive() {
        assertEquals(LinkKind.WEB, (LinkLaunchPolicy.check("HTTPS://a.example/") as LinkCheck.Accepted).kind)
        assertIs<LinkCheck.Rejected>(LinkLaunchPolicy.check("JavaScript:alert(1)"))
    }
    @Test fun everyBlockedSchemeIsRejected() {
        for (s in listOf("javascript", "file", "content", "intent", "android-app", "tel", "sms", "mailto")) {
            assertIs<LinkCheck.Rejected>(LinkLaunchPolicy.check("$s:whatever"), "scheme $s")
            assertIs<LinkCheck.Rejected>(LinkLaunchPolicy.check("$s://whatever"), "scheme $s")
        }
    }
    @Test fun unknownSchemeNeedsConfirmation() {
        assertEquals(LinkCheck.NeedsConfirmation("bear", "bear://x-callback-url/open-note?id=1"), LinkLaunchPolicy.check("bear://x-callback-url/open-note?id=1"))
    }
    @Test fun surroundingWhitespaceIsTrimmedButInnerWhitespaceRejects() {
        assertEquals(LinkCheck.Accepted(LinkKind.WEB, "https://a.example/"), LinkLaunchPolicy.check("  https://a.example/ \n"))
        assertIs<LinkCheck.Rejected>(LinkLaunchPolicy.check("https://a.example/ b"))
        assertIs<LinkCheck.Rejected>(LinkLaunchPolicy.check("https://a.example/\u0000"))
    }
    @Test fun noSchemeRejects() {
        assertIs<LinkCheck.Rejected>(LinkLaunchPolicy.check("a.example/path"))
        assertIs<LinkCheck.Rejected>(LinkLaunchPolicy.check(""))
        assertIs<LinkCheck.Rejected>(LinkLaunchPolicy.check("1http://a.example/"))
    }
}
```

`TagRouteTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.nfc

import com.loosecannon.notenfc.core.model.TagId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TagRouteTest {
    @Test fun tagRouteBecomesAV1Payload() {
        val p = TagRoute.parse("notenfc", "tag", listOf("123e4567-e89b-12d3-a456-426614174000"))
        assertEquals(TagPayload.V1(TagId("123e4567-e89b-12d3-a456-426614174000")), p)
    }
    @Test fun otherSchemesAndHostsAreNotThisRoute() {
        assertNull(TagRoute.parse("https", "tag", listOf("123e4567-e89b-12d3-a456-426614174000")))
        assertNull(TagRoute.parse("notenfc", "asset", listOf("123e4567-e89b-12d3-a456-426614174000")))
        assertNull(TagRoute.parse(null, null, emptyList()))
    }
    @Test fun badIdsAreMalformedNotCrashes() {
        assertIs<TagPayload.Malformed>(TagRoute.parse("notenfc", "tag", emptyList()))
        assertIs<TagPayload.Malformed>(TagRoute.parse("notenfc", "tag", listOf("nope")))
        assertIs<TagPayload.Malformed>(TagRoute.parse("notenfc", "tag", listOf("123E4567-E89B-12D3-A456-426614174000")))
        assertIs<TagPayload.Malformed>(TagRoute.parse("notenfc", "tag", listOf("123e4567-e89b-12d3-a456-426614174000", "extra")))
    }
}
```

`LinkUseCasesTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.LinkKind
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.testing.FakeUnitOfWork
import com.loosecannon.notenfc.core.testing.InMemoryLinkRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LinkUseCasesTest {
    private val links = InMemoryLinkRepository()
    private val uow = FakeUnitOfWork(links)
    private var seq = 0
    private val ids = IdGenerator { "00000000-0000-4000-8000-%012d".format(++seq) }
    private val clock = Clock { 5_000L }
    private val save = SaveLink(links, uow, ids, clock)
    private val open = OpenLink(links, uow, clock)
    private val joplin = "joplin://x-callback-url/openNote?id=0123456789abcdef0123456789abcdef"

    @Test fun savesAnAllowedLinkWithItsKind() = runTest {
        val link = save.run("  $joplin ", label = "Pool log")
        assertEquals(LinkKind.JOPLIN, link.kind)
        assertEquals(joplin, link.uri)
        assertEquals("Pool log", link.label)
        assertEquals(null, link.assetId)
        assertEquals(5_000L, link.createdAt)
        assertEquals(link, links.rows[link.id.value])
        assertEquals(1, uow.commits)
    }
    @Test fun blankLabelFallsBackToTheUri() = runTest {
        assertEquals(joplin, save.run(joplin, label = "  ").label)
        assertEquals(joplin, save.run(joplin, label = null).label)
    }
    @Test fun blockedUriIsRefusedAndNothingIsStored() = runTest {
        assertFailsWith<LinkRefused> { save.run("intent://scan/#Intent;scheme=zxing;end", label = "x") }
        assertTrue(links.rows.isEmpty())
        assertEquals(0, uow.commits)
    }
    @Test fun unknownSchemeNeedsConfirmationThenSavesAsOther() = runTest {
        assertFailsWith<LinkNeedsConfirmation> { save.run("bear://x-callback-url/open-note?id=1", label = "b") }
        assertTrue(links.rows.isEmpty())
        val link = save.run("bear://x-callback-url/open-note?id=1", label = "b", confirmedOther = true)
        assertEquals(LinkKind.OTHER, link.kind)
    }
    @Test fun openingRecordsLastOpenedAtAndReturnsTheCheckedUri() = runTest {
        val link = save.run(joplin, label = "Pool log")
        val out = open.run(link.id)
        assertEquals(OpenLink.Outcome.Launch(joplin, link), out)
        assertEquals(5_000L, links.rows[link.id.value]!!.lastOpenedAt)
    }
    @Test fun aConfirmedOtherLinkLaunches() = runTest {
        val link = save.run("bear://x-callback-url/open-note?id=1", label = "b", confirmedOther = true)
        assertIs<OpenLink.Outcome.Launch>(open.run(link.id))
    }
    @Test fun aStoredBlockedUriIsRefusedAtLaunchTime() = runTest {
        // e.g. restored from a hand-edited backup: the policy runs again at launch (D3 §10)
        val bad = ExternalLink(LinkId("l1"), null, LinkKind.WEB, "evil", "javascript:alert(1)", 1L, null, 1L)
        links.rows["l1"] = bad
        val out = open.run(LinkId("l1"))
        assertIs<OpenLink.Outcome.Refused>(out)
        assertEquals(null, links.rows["l1"]!!.lastOpenedAt)
    }
    @Test fun anUnconfirmedUnknownSchemeIsRefusedAtLaunchTime() = runTest {
        val odd = ExternalLink(LinkId("l2"), null, LinkKind.WEB, "odd", "bear://x", 1L, null, 1L)
        links.rows["l2"] = odd
        assertIs<OpenLink.Outcome.Refused>(open.run(LinkId("l2")))
    }
    @Test fun missingLinkIsReported() = runTest {
        assertEquals(OpenLink.Outcome.Missing(LinkId("nope")), open.run(LinkId("nope")))
    }
}
```

- [ ] **Step 2: Run to verify they fail.**

Run: `./gradlew :core:test --tests '*LinkLaunchPolicyTest*' --tests '*TagRouteTest*' --tests '*LinkUseCasesTest*'`
Expected: compilation failure (unresolved `LinkLaunchPolicy`, `TagRoute`, `SaveLink`, `OpenLink`).

- [ ] **Step 3: Implement.** `LinkLaunchPolicy.kt`:

```kotlin
package com.loosecannon.notenfc.core.links

import com.loosecannon.notenfc.core.model.LinkKind

sealed interface LinkCheck {
    data class Accepted(val kind: LinkKind, val uri: String) : LinkCheck

    /** A scheme outside the allowlist: launchable only after one explicit user confirmation. */
    data class NeedsConfirmation(val scheme: String, val uri: String) : LinkCheck

    data class Rejected(val reason: String) : LinkCheck
}

/**
 * Outbound-link gate (D3 §10; security doc "Outbound URI launching"). Runs at save time and again
 * at launch time, so a URI that arrives through a backup gets the same treatment as one typed in.
 */
object LinkLaunchPolicy {
    val ALLOWED_SCHEMES: Set<String> = setOf("joplin", "obsidian", "logseq", "http", "https")
    val BLOCKED_SCHEMES: Set<String> = setOf("javascript", "file", "content", "intent", "android-app", "tel", "sms", "mailto")

    private val uriToken = Regex("""[A-Za-z][A-Za-z0-9+.\-]*://[^\s<>"']+""")
    private val schemePrefix = Regex("""^([A-Za-z][A-Za-z0-9+.\-]*):""")
    private const val TRAILING_PUNCTUATION = ".,;:)]}>'\""

    /** The first `scheme://…` token in shared text; the surrounding title/prose is never stored. */
    fun extractUri(sharedText: String?): String? {
        val text = sharedText ?: return null
        return uriToken.find(text)?.value?.trimEnd { it in TRAILING_PUNCTUATION }?.takeIf { it.isNotEmpty() }
    }

    fun schemeOf(uri: String): String? = schemePrefix.find(uri)?.groupValues?.get(1)?.lowercase()

    fun kindOf(scheme: String): LinkKind = when (scheme.lowercase()) {
        "joplin" -> LinkKind.JOPLIN
        "obsidian" -> LinkKind.OBSIDIAN
        "logseq" -> LinkKind.LOGSEQ
        "http", "https" -> LinkKind.WEB
        else -> LinkKind.OTHER
    }

    fun check(uri: String): LinkCheck {
        val candidate = uri.trim()
        if (candidate.any { it.isWhitespace() || it.isISOControl() }) {
            return LinkCheck.Rejected("URI contains whitespace or control characters")
        }
        val scheme = schemeOf(candidate) ?: return LinkCheck.Rejected("not a URI: no scheme")
        if (scheme in BLOCKED_SCHEMES) return LinkCheck.Rejected("scheme '$scheme' is never launched")
        return if (scheme in ALLOWED_SCHEMES) LinkCheck.Accepted(kindOf(scheme), candidate)
        else LinkCheck.NeedsConfirmation(scheme, candidate)
    }
}
```

`TagRoute.kt`:

```kotlin
package com.loosecannon.notenfc.core.nfc

import com.loosecannon.notenfc.core.model.TagId

/**
 * `notenfc://tag/<uuid>` — resolves exactly as if the tag had been scanned (deep-link contract).
 * Navigation only; the id is validated by shape here and by existence in `ResolveTag`.
 */
object TagRoute {
    const val SCHEME: String = "notenfc"
    const val HOST: String = "tag"

    private val canonicalUuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    /** Null when the URI is not this route at all; `Malformed` when it is but the id is unusable. */
    fun parse(scheme: String?, host: String?, pathSegments: List<String>): TagPayload? {
        if (scheme != SCHEME || host != HOST) return null
        val id = pathSegments.singleOrNull()
            ?: return TagPayload.Malformed("notenfc://tag needs exactly one path segment, got ${pathSegments.size}")
        return if (canonicalUuid.matches(id)) TagPayload.V1(TagId(id)) else TagPayload.Malformed("not a tag id: '$id'")
    }
}
```

`SaveLink.kt`:

```kotlin
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.links.LinkCheck
import com.loosecannon.notenfc.core.links.LinkLaunchPolicy
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.LinkKind
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

class LinkRefused(reason: String) : IllegalArgumentException(reason)

class LinkNeedsConfirmation(val scheme: String) : IllegalArgumentException("scheme '$scheme' needs explicit confirmation")

/** Creates a standalone link (asset attachment is a Phase 1C/2 concern). The policy gate runs here. */
class SaveLink(
    private val links: LinkRepository,
    private val uow: UnitOfWork,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun run(uri: String, label: String?, confirmedOther: Boolean = false): ExternalLink {
        val (kind, cleanUri) = when (val c = LinkLaunchPolicy.check(uri)) {
            is LinkCheck.Accepted -> c.kind to c.uri
            is LinkCheck.NeedsConfirmation -> if (confirmedOther) LinkKind.OTHER to c.uri else throw LinkNeedsConfirmation(c.scheme)
            is LinkCheck.Rejected -> throw LinkRefused(c.reason)
        }
        val now = clock.nowMillis()
        val link = ExternalLink(
            id = LinkId(ids.newId()),
            assetId = null,
            kind = kind,
            label = label?.trim()?.takeIf { it.isNotEmpty() } ?: cleanUri,
            uri = cleanUri,
            createdAt = now,
            lastOpenedAt = null,
            updatedAt = now,
        )
        uow.write { links.upsert(link) }
        return link
    }
}
```

`OpenLink.kt`:

```kotlin
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.links.LinkCheck
import com.loosecannon.notenfc.core.links.LinkLaunchPolicy
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.LinkKind
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/** Launch-time half of the link policy: re-checks the stored URI and records the open. */
class OpenLink(
    private val links: LinkRepository,
    private val uow: UnitOfWork,
    private val clock: Clock,
) {
    sealed interface Outcome {
        data class Launch(val uri: String, val link: ExternalLink) : Outcome
        data class Refused(val link: ExternalLink, val reason: String) : Outcome
        data class Missing(val id: LinkId) : Outcome
    }

    suspend fun run(id: LinkId): Outcome = uow.write {
        val link = links.get(id) ?: return@write Outcome.Missing(id)
        val uri = when (val c = LinkLaunchPolicy.check(link.uri)) {
            is LinkCheck.Accepted -> c.uri
            is LinkCheck.NeedsConfirmation ->
                if (link.kind == LinkKind.OTHER) c.uri
                else return@write Outcome.Refused(link, "scheme '${c.scheme}' was never confirmed")
            is LinkCheck.Rejected -> return@write Outcome.Refused(link, c.reason)
        }
        links.upsert(link.copy(lastOpenedAt = clock.nowMillis()))
        Outcome.Launch(uri, link)
    }
}
```

- [ ] **Step 4: Run the tests.**

Run: `./gradlew :core:test`
Expected: PASS; 62 + 9 + 3 + 9 = **83** tests.

- [ ] **Step 5: Commit.**

```bash
git add core/src
git commit -m "link launch policy, notenfc://tag route, save/open link use cases"
```

---

### Task 4: `ResolveTag`, `BindTag`, `ProvisionTag`, `CreateAsset`

**Files:**
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/TagTargets.kt`, `ResolveTag.kt`, `BindTag.kt`, `ProvisionTag.kt`, `CreateAsset.kt`
- Test: `core/src/test/kotlin/com/loosecannon/notenfc/core/usecase/ResolveTagTest.kt`, `core/src/test/kotlin/com/loosecannon/notenfc/core/usecase/TagBindingUseCasesTest.kt`

**Interfaces:**
- Consumes: `TagPayload` (Task 2), models/ports from 1A, fakes `InMemoryAssetRepository`, `InMemoryTagRepository`, `InMemoryLinkRepository`, `FakeUnitOfWork`.
- Produces:
  - `Resolution = OpenAsset(tag, asset) | LaunchLink(tag, link) | Unbound(tag) | Revoked(tag) | UnknownV1(tagId) | UnknownLegacy(key) | NeedsNewerApp(version) | NotOurs(payload)`
  - `ResolveTag(tags, assets, links, uow, clock).run(payload: TagPayload): Resolution` — a known row gets `lastScannedAt = now` (`updatedAt` untouched)
  - `UnknownTarget(target)` exception; `BindTag(tags, assets, links, uow, ids, clock).run(format: PayloadFormat, key: String, target: TagTarget, label: String? = null): TagBinding` (target must not be `None`)
  - `ProvisionTag(tags, assets, links, uow, ids, clock)`: `begin(target: TagTarget, label: String?): TagBinding` (new `V1` row, `id == payloadKey`, `UNBOUND` when target is `None` else `ACTIVE`, `writtenAt = null`), `complete(id: TagId, physicalUid: String?): TagBinding` (sets `writtenAt`, `physicalUid`), `abandon(id: TagId)` (deletes the row only if `writtenAt == null`)
  - `CreateAsset(assets, uow, ids, clock).run(name: String, category: String = ""): Asset` (blank name → `IllegalArgumentException`)

- [ ] **Step 1: Write the failing tests.** `ResolveTagTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.LinkKind
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagId
import com.loosecannon.notenfc.core.model.TagStatus
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.nfc.TagPayload
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.testing.FakeUnitOfWork
import com.loosecannon.notenfc.core.testing.InMemoryAssetRepository
import com.loosecannon.notenfc.core.testing.InMemoryLinkRepository
import com.loosecannon.notenfc.core.testing.InMemoryTagRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ResolveTagTest {
    private val assets = InMemoryAssetRepository()
    private val tags = InMemoryTagRepository()
    private val links = InMemoryLinkRepository()
    private val uow = FakeUnitOfWork(assets, tags, links)
    private val clock = Clock { 9_000L }
    private val resolve = ResolveTag(tags, assets, links, uow, clock)

    private val v1Id = TagId("123e4567-e89b-12d3-a456-426614174000")
    private val asset = Asset(AssetId("a1"), "Hot tub", createdAt = 1L, updatedAt = 1L)
    private val link = ExternalLink(LinkId("l1"), null, LinkKind.JOPLIN, "log", "joplin://x-callback-url/openNote?id=0123456789abcdef0123456789abcdef", 1L, null, 1L)

    private fun row(id: String, format: PayloadFormat, key: String, target: TagTarget, status: TagStatus = TagStatus.ACTIVE) =
        TagBinding(TagId(id), format, key, target, status, createdAt = 1L, updatedAt = 1L)

    @Test fun boundToAnAssetOpensIt() = runTest {
        assets.rows["a1"] = asset
        tags.rows[v1Id.value] = row(v1Id.value, PayloadFormat.V1, v1Id.value, TagTarget.AssetTarget(AssetId("a1")))
        val r = resolve.run(TagPayload.V1(v1Id))
        assertIs<Resolution.OpenAsset>(r)
        assertEquals(asset, r.asset)
        assertEquals(9_000L, r.tag.lastScannedAt)
        assertEquals(9_000L, tags.rows[v1Id.value]!!.lastScannedAt)
        assertEquals(1L, tags.rows[v1Id.value]!!.updatedAt)
    }
    @Test fun boundToALinkLaunchesIt() = runTest {
        links.rows["l1"] = link
        tags.rows["t1"] = row("t1", PayloadFormat.LEGACY_MD5, "63b37acf", TagTarget.LinkTarget(LinkId("l1")))
        val r = resolve.run(TagPayload.LegacyMd5("63b37acf"))
        assertIs<Resolution.LaunchLink>(r)
        assertEquals(link, r.link)
    }
    @Test fun lostAndRetiredAreRevokedEvenWhenStillTargeted() = runTest {
        assets.rows["a1"] = asset
        tags.rows[v1Id.value] = row(v1Id.value, PayloadFormat.V1, v1Id.value, TagTarget.AssetTarget(AssetId("a1")), TagStatus.LOST)
        assertIs<Resolution.Revoked>(resolve.run(TagPayload.V1(v1Id)))
        tags.rows[v1Id.value] = row(v1Id.value, PayloadFormat.V1, v1Id.value, TagTarget.AssetTarget(AssetId("a1")), TagStatus.RETIRED)
        assertIs<Resolution.Revoked>(resolve.run(TagPayload.V1(v1Id)))
    }
    @Test fun unboundRowIsUnbound() = runTest {
        tags.rows[v1Id.value] = row(v1Id.value, PayloadFormat.V1, v1Id.value, TagTarget.None, TagStatus.UNBOUND)
        assertIs<Resolution.Unbound>(resolve.run(TagPayload.V1(v1Id)))
    }
    @Test fun activeRowWhoseTargetVanishedIsUnbound() = runTest {
        // ON DELETE SET NULL leaves an ACTIVE row with no target; a dangling id is treated the same
        tags.rows[v1Id.value] = row(v1Id.value, PayloadFormat.V1, v1Id.value, TagTarget.None)
        assertIs<Resolution.Unbound>(resolve.run(TagPayload.V1(v1Id)))
        tags.rows[v1Id.value] = row(v1Id.value, PayloadFormat.V1, v1Id.value, TagTarget.AssetTarget(AssetId("gone")))
        assertIs<Resolution.Unbound>(resolve.run(TagPayload.V1(v1Id)))
    }
    @Test fun unknownV1AndLegacyAreDistinct() = runTest {
        assertEquals(Resolution.UnknownV1(v1Id), resolve.run(TagPayload.V1(v1Id)))
        assertEquals(Resolution.UnknownLegacy("63b37acf"), resolve.run(TagPayload.LegacyMd5("63b37acf")))
        assertEquals(0, uow.commits)
    }
    @Test fun lookupIsByFormatAndKeyNotById() = runTest {
        // a LEGACY row whose payload key happens to equal a v1 id string must not resolve a v1 scan
        tags.rows["t9"] = row("t9", PayloadFormat.LEGACY_MD5, v1Id.value, TagTarget.None)
        assertEquals(Resolution.UnknownV1(v1Id), resolve.run(TagPayload.V1(v1Id)))
    }
    @Test fun newerVersionAndForeignContentNeverTouchTheStore() = runTest {
        assertEquals(Resolution.NeedsNewerApp(3), resolve.run(TagPayload.NewerVersion(3)))
        assertEquals(Resolution.NotOurs(TagPayload.Empty), resolve.run(TagPayload.Empty))
        assertEquals(Resolution.NotOurs(TagPayload.Foreign("x")), resolve.run(TagPayload.Foreign("x")))
        assertEquals(Resolution.NotOurs(TagPayload.Malformed("y")), resolve.run(TagPayload.Malformed("y")))
        assertEquals(0, uow.commits)
    }
}
```

`TagBindingUseCasesTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.LinkKind
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagId
import com.loosecannon.notenfc.core.model.TagStatus
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.nfc.NdefCodec
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.testing.FakeUnitOfWork
import com.loosecannon.notenfc.core.testing.InMemoryAssetRepository
import com.loosecannon.notenfc.core.testing.InMemoryLinkRepository
import com.loosecannon.notenfc.core.testing.InMemoryTagRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagBindingUseCasesTest {
    private val assets = InMemoryAssetRepository()
    private val tags = InMemoryTagRepository()
    private val links = InMemoryLinkRepository()
    private val uow = FakeUnitOfWork(assets, tags, links)
    private var seq = 0
    private val ids = IdGenerator { "00000000-0000-4000-8000-%012d".format(++seq) }
    private val clock = Clock { 7_000L }
    private val bind = BindTag(tags, assets, links, uow, ids, clock)
    private val provision = ProvisionTag(tags, assets, links, uow, ids, clock)
    private val create = CreateAsset(assets, uow, ids, clock)

    private val scanned = TagId("123e4567-e89b-12d3-a456-426614174000")
    private val a1 = TagTarget.AssetTarget(AssetId("a1"))

    private fun seedAsset() { assets.rows["a1"] = Asset(AssetId("a1"), "Hot tub", createdAt = 1L, updatedAt = 1L) }

    // --- BindTag ---------------------------------------------------------------------------

    @Test fun bindingAnUnknownV1TagCreatesARowWhoseIdIsTheTagId() = runTest {
        seedAsset()
        val row = bind.run(PayloadFormat.V1, scanned.value, a1, label = "lid")
        assertEquals(scanned, row.id)
        assertEquals(scanned.value, row.payloadKey)
        assertEquals(PayloadFormat.V1, row.payloadFormat)
        assertEquals(a1, row.target)
        assertEquals(TagStatus.ACTIVE, row.status)
        assertEquals("lid", row.label)
        assertEquals(7_000L, row.createdAt)
        assertEquals(row, tags.rows[scanned.value])
    }
    @Test fun bindingAnUnknownLegacyTagGetsAFreshRowId() = runTest {
        seedAsset()
        val row = bind.run(PayloadFormat.LEGACY_MD5, "63b37acf", a1)
        assertNotEquals("63b37acf", row.id.value)
        assertEquals("63b37acf", row.payloadKey)
        assertEquals(PayloadFormat.LEGACY_MD5, row.payloadFormat)
    }
    @Test fun bindingAKnownRowRetargetsItAndReactivates() = runTest {
        seedAsset()
        links.rows["l1"] = ExternalLink(LinkId("l1"), null, LinkKind.WEB, "m", "https://a.example/", 1L, null, 1L)
        tags.rows[scanned.value] = TagBinding(scanned, PayloadFormat.V1, scanned.value, TagTarget.None, TagStatus.UNBOUND, label = "spare", createdAt = 1L, updatedAt = 1L)
        val row = bind.run(PayloadFormat.V1, scanned.value, TagTarget.LinkTarget(LinkId("l1")))
        assertEquals(TagTarget.LinkTarget(LinkId("l1")), row.target)
        assertEquals(TagStatus.ACTIVE, row.status)
        assertEquals("spare", row.label)          // label kept when none is given
        assertEquals(1L, row.createdAt)
        assertEquals(7_000L, row.updatedAt)
        assertEquals(1, tags.rows.size)
    }
    @Test fun bindingToAMissingTargetFailsAndWritesNothing() = runTest {
        assertFailsWith<UnknownTarget> { bind.run(PayloadFormat.V1, scanned.value, a1) }
        assertFailsWith<UnknownTarget> { bind.run(PayloadFormat.V1, scanned.value, TagTarget.LinkTarget(LinkId("nope"))) }
        assertTrue(tags.rows.isEmpty())
        assertEquals(0, uow.commits)
    }
    @Test fun bindingToNoTargetIsRefused() = runTest {
        assertFailsWith<IllegalArgumentException> { bind.run(PayloadFormat.V1, scanned.value, TagTarget.None) }
    }
    @Test fun bindingRefusesANonCanonicalV1Key() = runTest {
        seedAsset()
        assertFailsWith<IllegalArgumentException> { bind.run(PayloadFormat.V1, "NOT-A-UUID", a1) }
        assertTrue(tags.rows.isEmpty())
    }

    // --- ProvisionTag ----------------------------------------------------------------------

    @Test fun beginCreatesAnEncodableUnwrittenRow() = runTest {
        seedAsset()
        val row = provision.begin(a1, label = "panel")
        assertEquals(row.id.value, row.payloadKey)
        assertEquals(PayloadFormat.V1, row.payloadFormat)
        assertEquals(TagStatus.ACTIVE, row.status)
        assertNull(row.writtenAt)
        assertEquals(2, NdefCodec.encodeV1(row.id).size)   // the id is a canonical UUID
        assertEquals(row, tags.rows[row.id.value])
    }
    @Test fun beginWithNoTargetIsASpare() = runTest {
        val row = provision.begin(TagTarget.None, label = null)
        assertEquals(TagStatus.UNBOUND, row.status)
        assertEquals(TagTarget.None, row.target)
    }
    @Test fun beginRefusesAMissingTarget() = runTest {
        assertFailsWith<UnknownTarget> { provision.begin(a1, null) }
        assertTrue(tags.rows.isEmpty())
    }
    @Test fun completeStampsWrittenAtAndUid() = runTest {
        val row = provision.begin(TagTarget.None, null)
        val done = provision.complete(row.id, physicalUid = "04a1b2c3d4e5f6")
        assertEquals(7_000L, done.writtenAt)
        assertEquals("04a1b2c3d4e5f6", done.physicalUid)
        assertEquals(done, tags.rows[row.id.value])
    }
    @Test fun completeOnAnUnknownRowFails() = runTest {
        assertFailsWith<IllegalStateException> { provision.complete(TagId("nope"), null) }
    }
    @Test fun abandonDeletesOnlyAnUnwrittenRow() = runTest {
        val unwritten = provision.begin(TagTarget.None, null)
        val written = provision.complete(provision.begin(TagTarget.None, null).id, null)
        provision.abandon(unwritten.id)
        provision.abandon(written.id)
        provision.abandon(TagId("nope"))
        assertNull(tags.rows[unwritten.id.value])
        assertEquals(written, tags.rows[written.id.value])
    }

    // --- CreateAsset -----------------------------------------------------------------------

    @Test fun createAssetTrimsAndStores() = runTest {
        val asset = create.run("  Pool pump ", category = "Yard")
        assertEquals("Pool pump", asset.name)
        assertEquals("Yard", asset.category)
        assertEquals(7_000L, asset.createdAt)
        assertEquals(asset, assets.rows[asset.id.value])
    }
    @Test fun createAssetRefusesABlankName() = runTest {
        assertFailsWith<IllegalArgumentException> { create.run("   ") }
        assertTrue(assets.rows.isEmpty())
    }
}
```

- [ ] **Step 2: Run to verify they fail.**

Run: `./gradlew :core:test --tests '*ResolveTagTest*' --tests '*TagBindingUseCasesTest*'`
Expected: compilation failure.

- [ ] **Step 3: Implement.** `TagTargets.kt`:

```kotlin
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.LinkRepository

class UnknownTarget(target: TagTarget) : IllegalArgumentException("target does not exist: $target")

/** A binding may only point at a row that exists; `None` is always fine. */
internal suspend fun requireTargetExists(target: TagTarget, assets: AssetRepository, links: LinkRepository) {
    when (target) {
        is TagTarget.AssetTarget -> assets.get(target.assetId) ?: throw UnknownTarget(target)
        is TagTarget.LinkTarget -> links.get(target.linkId) ?: throw UnknownTarget(target)
        TagTarget.None -> Unit
    }
}
```

`ResolveTag.kt`:

```kotlin
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagId
import com.loosecannon.notenfc.core.model.TagStatus
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.nfc.TagPayload
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.TagRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/** Every way a scan can end (D3 §9). The UI switches on this and nothing else. */
sealed interface Resolution {
    data class OpenAsset(val tag: TagBinding, val asset: Asset) : Resolution
    data class LaunchLink(val tag: TagBinding, val link: ExternalLink) : Resolution
    data class Unbound(val tag: TagBinding) : Resolution
    data class Revoked(val tag: TagBinding) : Resolution
    data class UnknownV1(val tagId: TagId) : Resolution
    data class UnknownLegacy(val key: String) : Resolution
    data class NeedsNewerApp(val version: Int) : Resolution
    data class NotOurs(val payload: TagPayload) : Resolution
}

class ResolveTag(
    private val tags: TagRepository,
    private val assets: AssetRepository,
    private val links: LinkRepository,
    private val uow: UnitOfWork,
    private val clock: Clock,
) {
    suspend fun run(payload: TagPayload): Resolution = when (payload) {
        is TagPayload.V1 -> known(PayloadFormat.V1, payload.tagId.value) ?: Resolution.UnknownV1(payload.tagId)
        is TagPayload.LegacyMd5 -> known(PayloadFormat.LEGACY_MD5, payload.key) ?: Resolution.UnknownLegacy(payload.key)
        is TagPayload.NewerVersion -> Resolution.NeedsNewerApp(payload.version)
        is TagPayload.Foreign, is TagPayload.Malformed, TagPayload.Empty -> Resolution.NotOurs(payload)
    }

    /** Lookup is by (format, key) — never by row id (D4 §3). A hit records the scan. */
    private suspend fun known(format: PayloadFormat, key: String): Resolution? {
        val row = tags.findByPayload(format, key) ?: return null
        return uow.write {
            val tag = row.copy(lastScannedAt = clock.nowMillis())
            tags.upsert(tag)
            when {
                tag.status == TagStatus.LOST || tag.status == TagStatus.RETIRED -> Resolution.Revoked(tag)
                tag.status == TagStatus.UNBOUND -> Resolution.Unbound(tag)
                else -> when (val t = tag.target) {
                    is TagTarget.AssetTarget -> assets.get(t.assetId)?.let { Resolution.OpenAsset(tag, it) } ?: Resolution.Unbound(tag)
                    is TagTarget.LinkTarget -> links.get(t.linkId)?.let { Resolution.LaunchLink(tag, it) } ?: Resolution.Unbound(tag)
                    TagTarget.None -> Resolution.Unbound(tag)
                }
            }
        }
    }
}
```

`BindTag.kt`:

```kotlin
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagId
import com.loosecannon.notenfc.core.model.TagStatus
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.nfc.NdefCodec
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.TagRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * Binds the tag that carries ([format], [key]) to [target]. Works for a tag this phone has never
 * seen (a v1 tag from before a wipe, or a legacy tag bound as-is, D13 §3) and for a known row,
 * which is retargeted and re-activated in place — its history and label survive.
 */
class BindTag(
    private val tags: TagRepository,
    private val assets: AssetRepository,
    private val links: LinkRepository,
    private val uow: UnitOfWork,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun run(format: PayloadFormat, key: String, target: TagTarget, label: String? = null): TagBinding {
        require(target != TagTarget.None) { "bind needs an asset or a link" }
        if (format == PayloadFormat.V1) NdefCodec.requireCanonicalUuid(TagId(key))
        return uow.write {
            requireTargetExists(target, assets, links)
            val now = clock.nowMillis()
            val existing = tags.findByPayload(format, key)
            val bound = existing?.copy(target = target, status = TagStatus.ACTIVE, label = label ?: existing.label, updatedAt = now)
                ?: TagBinding(
                    id = TagId(if (format == PayloadFormat.V1) key else ids.newId()),
                    payloadFormat = format,
                    payloadKey = key,
                    target = target,
                    status = TagStatus.ACTIVE,
                    label = label,
                    createdAt = now,
                    updatedAt = now,
                )
            tags.upsert(bound)
            bound
        }
    }
}
```

`ProvisionTag.kt`:

```kotlin
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagId
import com.loosecannon.notenfc.core.model.TagStatus
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.TagRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * The row side of writing a new v1 tag: [begin] mints the identity before the write, [complete]
 * records the successful read-back, [abandon] cleans up a row whose tag was never written.
 */
class ProvisionTag(
    private val tags: TagRepository,
    private val assets: AssetRepository,
    private val links: LinkRepository,
    private val uow: UnitOfWork,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun begin(target: TagTarget, label: String?): TagBinding = uow.write {
        requireTargetExists(target, assets, links)
        val now = clock.nowMillis()
        val id = ids.newId()
        val row = TagBinding(
            id = TagId(id),
            payloadFormat = PayloadFormat.V1,
            payloadKey = id,
            target = target,
            status = if (target == TagTarget.None) TagStatus.UNBOUND else TagStatus.ACTIVE,
            label = label?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = now,
            updatedAt = now,
        )
        tags.upsert(row)
        row
    }

    suspend fun complete(id: TagId, physicalUid: String?): TagBinding = uow.write {
        val row = tags.get(id) ?: throw IllegalStateException("no provisioned tag ${id.value}")
        val now = clock.nowMillis()
        val done = row.copy(physicalUid = physicalUid ?: row.physicalUid, writtenAt = now, updatedAt = now)
        tags.upsert(done)
        done
    }

    suspend fun abandon(id: TagId) {
        uow.write {
            val row = tags.get(id)
            if (row != null && row.writtenAt == null) tags.delete(id)
        }
    }
}
```

`CreateAsset.kt`:

```kotlin
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.UnitOfWork

/** The minimum needed to bind a tag to something new; the full asset form is Phase 2. */
class CreateAsset(
    private val assets: AssetRepository,
    private val uow: UnitOfWork,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun run(name: String, category: String = ""): Asset {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "an asset needs a name" }
        val now = clock.nowMillis()
        val asset = Asset(id = AssetId(ids.newId()), name = clean, category = category.trim(), createdAt = now, updatedAt = now)
        uow.write { assets.upsert(asset) }
        return asset
    }
}
```

- [ ] **Step 4: Run the tests.**

Run: `./gradlew :core:test`
Expected: PASS; 83 + 8 + 14 = **105** tests.

- [ ] **Step 5: Commit.**

```bash
git add core/src
git commit -m "resolve/bind/provision tag use cases + create asset"
```

---

### Task 5: `:app` NFC adapter, `AppGraph` wiring, Room-backed use-case test

**Files:**
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/nfc/NdefBridge.kt`, `NfcReaderModeSession.kt`, `TagWriter.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/di/AppGraph.kt`
- Test: `app/src/test/kotlin/com/loosecannon/notenfc/nfc/TagUseCasesRoomTest.kt`

**Interfaces:**
- Consumes: `NdefRecordData`, `TagPayload`, `NdefCodec.decode` (Task 2); use cases (Tasks 3–4); `inMemoryDb()` (`app/src/test/.../data/room/TestDb.kt`), `RoomAssetRepository`, `RoomTagRepository`, `RoomLinkRepository`, `RoomUnitOfWork`.
- Produces:
  - `NdefMessage?.toRecordData(): List<NdefRecordData>`, `List<NdefRecordData>.toNdefMessage(): NdefMessage`, `ByteArray?.toHexOrNull(): String?`, `Intent.ndefRecords(): List<NdefRecordData>?` (null when the extra is absent), `Intent.nfcTag(): Tag?`
  - `NfcReaderModeSession(activity, onTag: (Tag) -> Unit)` with `available: Boolean`, `enabled: Boolean`, `start()`, `stop()`
  - `TagInspection(uid, existing: TagPayload, existingRecords, maxSize: Int /* -1 unknown */, writable, needsFormat, canLock)`; `TagWriter.inspect(tag): TagInspection?` (null = neither `Ndef` nor `NdefFormatable`); `WriteResult = Written(readBack, bytes, verified, locked) | TooSmall(maxSize, needed) | ReadOnly | Unsupported | VerifyMismatch(readBack) | Failed(reason)`; `TagWriter.write(tag, records, lock): WriteResult` (blocking; call off the main thread)
  - `AppGraph.appScope: CoroutineScope`, `resolveTag`, `bindTag`, `provisionTag`, `createAsset`, `saveLink`, `openLink`

- [ ] **Step 1: Write the failing Room-backed test.** `TagUseCasesRoomTest.kt`:

```kotlin
package com.loosecannon.notenfc.nfc

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagStatus
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.nfc.NdefCodec
import com.loosecannon.notenfc.core.nfc.TagPayload
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.UuidGenerator
import com.loosecannon.notenfc.core.usecase.BindTag
import com.loosecannon.notenfc.core.usecase.ProvisionTag
import com.loosecannon.notenfc.core.usecase.ResolveTag
import com.loosecannon.notenfc.core.usecase.Resolution
import com.loosecannon.notenfc.data.room.RoomAssetRepository
import com.loosecannon.notenfc.data.room.RoomLinkRepository
import com.loosecannon.notenfc.data.room.RoomTagRepository
import com.loosecannon.notenfc.data.room.RoomUnitOfWork
import com.loosecannon.notenfc.data.room.inMemoryDb
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Phase 1B use cases against the real schema: unique (format,key) lookup, FK targets, cleanup. */
class TagUseCasesRoomTest {

    @Test
    fun provisionWriteScanResolvesThroughRoom() = runTest {
        val db = inMemoryDb()
        try {
            val assets = RoomAssetRepository(db.assetDao())
            val tags = RoomTagRepository(db.nfcTagDao())
            val links = RoomLinkRepository(db.externalLinkDao())
            val uow = RoomUnitOfWork(db)
            val clock = Clock { 42L }
            val provision = ProvisionTag(tags, assets, links, uow, UuidGenerator, clock)
            val resolve = ResolveTag(tags, assets, links, uow, clock)

            uow.write { assets.upsert(Asset(AssetId("a1"), "Hot tub", createdAt = 1L, updatedAt = 1L)) }
            val row = provision.begin(TagTarget.AssetTarget(AssetId("a1")), "lid")
            val onTag = NdefCodec.decode(NdefCodec.encodeV1(row.id))   // what the phone will read back
            provision.complete(row.id, "04aabbcc")

            val r = resolve.run(onTag)
            assertTrue(r.toString(), r is Resolution.OpenAsset)
            r as Resolution.OpenAsset
            assertEquals("Hot tub", r.asset.name)
            assertEquals(42L, r.tag.lastScannedAt)
            assertEquals("04aabbcc", r.tag.physicalUid)
        } finally {
            db.close()
        }
    }

    @Test
    fun abandonRemovesOnlyTheUnwrittenRow() = runTest {
        val db = inMemoryDb()
        try {
            val assets = RoomAssetRepository(db.assetDao())
            val tags = RoomTagRepository(db.nfcTagDao())
            val links = RoomLinkRepository(db.externalLinkDao())
            val uow = RoomUnitOfWork(db)
            val provision = ProvisionTag(tags, assets, links, uow, UuidGenerator, Clock { 1L })
            val spare = provision.begin(TagTarget.None, null)
            val written = provision.complete(provision.begin(TagTarget.None, null).id, null)
            provision.abandon(spare.id)
            provision.abandon(written.id)
            assertNull(tags.get(spare.id))
            assertEquals(written, tags.get(written.id))
        } finally {
            db.close()
        }
    }

    @Test
    fun bindingAnUnknownLegacyTagThenRescanningFindsIt() = runTest {
        val db = inMemoryDb()
        try {
            val assets = RoomAssetRepository(db.assetDao())
            val tags = RoomTagRepository(db.nfcTagDao())
            val links = RoomLinkRepository(db.externalLinkDao())
            val uow = RoomUnitOfWork(db)
            val clock = Clock { 3L }
            val bind = BindTag(tags, assets, links, uow, UuidGenerator, clock)
            val resolve = ResolveTag(tags, assets, links, uow, clock)
            uow.write { assets.upsert(Asset(AssetId("a1"), "Hot tub", createdAt = 1L, updatedAt = 1L)) }

            assertEquals(Resolution.UnknownLegacy("63b37acf"), resolve.run(TagPayload.LegacyMd5("63b37acf")))
            val row = bind.run(PayloadFormat.LEGACY_MD5, "63b37acf", TagTarget.AssetTarget(AssetId("a1")))
            assertEquals(TagStatus.ACTIVE, row.status)
            val r = resolve.run(TagPayload.LegacyMd5("63b37acf"))
            assertTrue(r.toString(), r is Resolution.OpenAsset)
            // binding again retargets the same row: still exactly one row for this payload
            bind.run(PayloadFormat.LEGACY_MD5, "63b37acf", TagTarget.AssetTarget(AssetId("a1")), label = "old sticker")
            assertEquals(1, tags.all().size)
            assertEquals("old sticker", tags.all().single().label)
        } finally {
            db.close()
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails.**

Run: `./gradlew :app:testDebugUnitTest --tests '*TagUseCasesRoomTest*'`
Expected: PASS already is acceptable here only if compilation succeeds — it should, because the test depends only on `:core` and 1A code. If it passes, fine: it is the Room-backed characterisation of Task 4; proceed. (It is placed in this task so the adapter commit carries it.)

- [ ] **Step 3: Implement `NdefBridge.kt`.**

```kotlin
package com.loosecannon.notenfc.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import com.loosecannon.notenfc.core.nfc.NdefRecordData

/** The only place Android NDEF types meet the pure-bytes codec (D3 §9). */

fun NdefMessage?.toRecordData(): List<NdefRecordData> =
    this?.records.orEmpty().map { NdefRecordData(it.tnf.toInt(), it.type, it.payload) }

fun List<NdefRecordData>.toNdefMessage(): NdefMessage {
    require(isNotEmpty()) { "an NDEF message needs at least one record" }
    return NdefMessage(map { NdefRecord(it.tnf.toShort(), it.type, ByteArray(0), it.payload) }.toTypedArray())
}

fun ByteArray?.toHexOrNull(): String? =
    this?.takeIf { it.isNotEmpty() }?.joinToString("") { "%02x".format(it) }

/** Records of the first message in `EXTRA_NDEF_MESSAGES`; null when the extra is absent. */
fun Intent.ndefRecords(): List<NdefRecordData>? {
    val raw = (
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }
    ) ?: return null
    return (raw.firstOrNull() as? NdefMessage).toRecordData()
}

fun Intent.nfcTag(): Tag? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(NfcAdapter.EXTRA_TAG)
    }
```

- [ ] **Step 4: Implement `NfcReaderModeSession.kt`.**

```kotlin
package com.loosecannon.notenfc.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag

/**
 * Reader mode for in-app scanning and writing (D3 §9): callback-based, no PendingIntent, no
 * activity relaunch. `FLAG_READER_SKIP_NDEF_CHECK` keeps the platform from reading the tag for us
 * so the writer sees exactly what is there. Start in `onResume`, stop in `onPause`.
 */
class NfcReaderModeSession(private val activity: Activity, private val onTag: (Tag) -> Unit) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    val available: Boolean get() = adapter != null
    val enabled: Boolean get() = adapter?.isEnabled == true

    fun start() {
        adapter?.enableReaderMode(activity, { tag -> onTag(tag) }, FLAGS, null)
    }

    fun stop() {
        adapter?.disableReaderMode(activity)
    }

    private companion object {
        const val FLAGS = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
    }
}
```

- [ ] **Step 5: Implement `TagWriter.kt`.**

```kotlin
package com.loosecannon.notenfc.nfc

import android.nfc.FormatException
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.loosecannon.notenfc.core.nfc.NdefCodec
import com.loosecannon.notenfc.core.nfc.NdefRecordData
import com.loosecannon.notenfc.core.nfc.TagPayload
import java.io.IOException

class TagInspection(
    val uid: String?,
    val existing: TagPayload,
    val existingRecords: List<NdefRecordData>,
    /** `Ndef.maxSize`, or -1 for a tag that still needs formatting (capacity unknown until then). */
    val maxSize: Int,
    val writable: Boolean,
    val needsFormat: Boolean,
    val canLock: Boolean,
)

sealed interface WriteResult {
    /** [verified] is false only on the format path, where the same `Tag` object cannot be re-read. */
    data class Written(val readBack: List<NdefRecordData>, val bytes: Int, val verified: Boolean, val locked: Boolean) : WriteResult
    data class TooSmall(val maxSize: Int, val needed: Int) : WriteResult
    data object ReadOnly : WriteResult
    data object Unsupported : WriteResult
    data class VerifyMismatch(val readBack: List<NdefRecordData>) : WriteResult
    data class Failed(val reason: String) : WriteResult
}

/**
 * Read-first, write, read-back (D3 §9). Every function blocks on tag I/O: call from a worker
 * thread, never the main thread. Decisions (overwrite? which target?) are made by the caller
 * between [inspect] and [write], while the tag stays in the field.
 */
object TagWriter {

    fun inspect(tag: Tag): TagInspection? {
        val uid = tag.id.toHexOrNull()
        Ndef.get(tag)?.let { ndef ->
            return try {
                ndef.connect()
                val records = try {
                    ndef.ndefMessage.toRecordData()
                } catch (e: FormatException) {
                    return TagInspection(uid, TagPayload.Malformed("NDEF on tag could not be parsed"), emptyList(), ndef.maxSize, ndef.isWritable, false, ndef.canMakeReadOnly())
                }
                TagInspection(uid, NdefCodec.decode(records), records, ndef.maxSize, ndef.isWritable, needsFormat = false, canLock = ndef.canMakeReadOnly())
            } finally {
                runCatching { ndef.close() }
            }
        }
        NdefFormatable.get(tag) ?: return null
        return TagInspection(uid, TagPayload.Empty, emptyList(), maxSize = -1, writable = true, needsFormat = true, canLock = true)
    }

    fun write(tag: Tag, records: List<NdefRecordData>, lock: Boolean): WriteResult {
        val message = records.toNdefMessage()
        val needed = message.toByteArray().size
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                if (!ndef.isWritable) return WriteResult.ReadOnly
                if (ndef.maxSize < needed) return WriteResult.TooSmall(ndef.maxSize, needed)
                ndef.writeNdefMessage(message)
                val back = ndef.ndefMessage.toRecordData()
                if (back != records) return WriteResult.VerifyMismatch(back)
                var locked = false
                if (lock && ndef.canMakeReadOnly()) locked = ndef.makeReadOnly()
                WriteResult.Written(back, needed, verified = true, locked = locked)
            } catch (e: TagLostException) {
                WriteResult.Failed("tag left the field")
            } catch (e: IOException) {
                WriteResult.Failed(e.message ?: "I/O error")
            } catch (e: FormatException) {
                WriteResult.Failed("tag rejected the message: ${e.message}")
            } finally {
                runCatching { ndef.close() }
            }
        }
        val formatable = NdefFormatable.get(tag) ?: return WriteResult.Unsupported
        return try {
            formatable.connect()
            if (lock) formatable.formatReadOnly(message) else formatable.format(message)
            // The Tag object was discovered as NdefFormatable only; Ndef.get(tag) stays null
            // until the tag is rediscovered, so verification is the next tap's job.
            WriteResult.Written(emptyList(), needed, verified = false, locked = lock)
        } catch (e: TagLostException) {
            WriteResult.Failed("tag left the field")
        } catch (e: IOException) {
            WriteResult.Failed(e.message ?: "I/O error while formatting")
        } catch (e: FormatException) {
            WriteResult.Failed("tag could not be formatted: ${e.message}")
        } finally {
            runCatching { formatable.close() }
        }
    }
}
```

- [ ] **Step 6: Wire `AppGraph`.** Add these imports and members (keep everything already there):

```kotlin
import com.loosecannon.notenfc.core.usecase.BindTag
import com.loosecannon.notenfc.core.usecase.CreateAsset
import com.loosecannon.notenfc.core.usecase.OpenLink
import com.loosecannon.notenfc.core.usecase.ProvisionTag
import com.loosecannon.notenfc.core.usecase.ResolveTag
import com.loosecannon.notenfc.core.usecase.SaveLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
```

```kotlin
    /** Process-wide scope for work that must outlive a finishing activity (e.g. abandoning a row). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Phase 1B — NFC identity
    val resolveTag: ResolveTag = ResolveTag(tags, assets, links, uow, clock)
    val bindTag: BindTag = BindTag(tags, assets, links, uow, ids, clock)
    val provisionTag: ProvisionTag = ProvisionTag(tags, assets, links, uow, ids, clock)
    val createAsset: CreateAsset = CreateAsset(assets, uow, ids, clock)
    val saveLink: SaveLink = SaveLink(links, uow, ids, clock)
    val openLink: OpenLink = OpenLink(links, uow, clock)
```

Update the class KDoc to "No DI framework in Phase 1 (D3 §5)".

- [ ] **Step 7: Build and test.**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `:app` 30 + 3 = **33** tests.

- [ ] **Step 8: Commit.**

```bash
git add app/src
git commit -m "nfc adapter: ndef bridge, reader-mode session, tag writer with read-back; wire the use cases"
```

---

### Task 6: `TargetPicker`, `WriteTagActivity`, `LinkLauncher`

**Files:**
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/ui/interim/TargetPicker.kt`, `WriteTagActivity.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/links/LinkLauncher.kt`
- Create: `app/src/main/res/layout/activity_write_tag.xml`
- Modify: `app/src/main/AndroidManifest.xml` (register `WriteTagActivity`, not exported), `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `NfcReaderModeSession`, `TagWriter`, `TagInspection`, `WriteResult`, `toHexOrNull` (Task 5); `NdefCodec.encodeV1`, `OverwritePolicy`, `OverwriteDecision` (Task 2); `AppGraph.provisionTag/createAsset/assets/links/appScope`; `TagTarget`, `TagBinding`.
- Produces:
  - `TargetPicker.show(activity, graph, scope, allowNone: Boolean, onPicked: (TagTarget) -> Unit)`
  - `WriteTagActivity.start(activity: Activity, target: TagTarget, label: String? = null)`; extras `EXTRA_TARGET_KIND` ∈ {`asset`,`link`,`none`}, `EXTRA_TARGET_ID`, `EXTRA_LABEL`
  - `LinkLauncher.open(activity: Activity, uri: String): Boolean` (false + toast when no handler)

- [ ] **Step 1: `LinkLauncher.kt`.**

```kotlin
package com.loosecannon.notenfc.links

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Fires `ACTION_VIEW` for a URI that `OpenLink` has already checked; never crashes on a missing handler. */
object LinkLauncher {
    fun open(activity: Activity, uri: String): Boolean = try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(activity, "No app can open this link:\n$uri", Toast.LENGTH_LONG).show()
        false
    }
}
```

- [ ] **Step 2: `TargetPicker.kt`.**

```kotlin
package com.loosecannon.notenfc.ui.interim

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import android.widget.Toast
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.di.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Interim (Phase 1B) chooser: every asset, every link, "New asset…", optionally "no target".
 * Replaced by the Compose bind flow in Phase 1C; kept deliberately plain.
 */
object TargetPicker {
    fun show(activity: Activity, graph: AppGraph, scope: CoroutineScope, allowNone: Boolean, onPicked: (TagTarget) -> Unit) {
        scope.launch {
            val assets = graph.assets.all().sortedBy { it.name.lowercase() }
            val links = graph.links.all().sortedBy { it.label.lowercase() }
            val labels = ArrayList<String>()
            val actions = ArrayList<() -> Unit>()
            assets.forEach { a -> labels += "Asset: ${a.name}"; actions += { onPicked(TagTarget.AssetTarget(a.id)) } }
            links.forEach { l -> labels += "Link: ${l.label}"; actions += { onPicked(TagTarget.LinkTarget(l.id)) } }
            labels += "New asset…"; actions += { promptNewAsset(activity, graph, scope, onPicked) }
            if (allowNone) { labels += "No target yet (spare tag)"; actions += { onPicked(TagTarget.None) } }
            if (activity.isFinishing || activity.isDestroyed) return@launch
            AlertDialog.Builder(activity)
                .setTitle("Choose what this tag opens")
                .setItems(labels.toTypedArray()) { _, i -> actions[i]() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun promptNewAsset(activity: Activity, graph: AppGraph, scope: CoroutineScope, onPicked: (TagTarget) -> Unit) {
        val input = EditText(activity).apply { hint = "Asset name" }
        AlertDialog.Builder(activity)
            .setTitle("New asset")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString()
                scope.launch {
                    try {
                        val asset = graph.createAsset.run(name)
                        onPicked(TagTarget.AssetTarget(asset.id))
                    } catch (e: IllegalArgumentException) {
                        Toast.makeText(activity, "Give the asset a name.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
```

- [ ] **Step 3: `activity_write_tag.xml`.**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="24dp">
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/write_title"
            android:textSize="20sp"
            android:textStyle="bold" />
        <TextView
            android:id="@+id/write_status"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:textSize="16sp" />
        <CheckBox
            android:id="@+id/write_lock"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/write_lock" />
        <Button
            android:id="@+id/write_done"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/write_done" />
    </LinearLayout>
</ScrollView>
```

Add to `strings.xml`:

```xml
    <string name="write_title">Write a noteNFC tag</string>
    <string name="write_lock">Lock the tag after writing (permanent — it can never be rewritten)</string>
    <string name="write_done">Done</string>
```

- [ ] **Step 4: `WriteTagActivity.kt`.**

```kotlin
package com.loosecannon.notenfc.ui.interim

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.nfc.Tag
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import com.loosecannon.notenfc.NoteNfcApp
import com.loosecannon.notenfc.R
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.nfc.NdefCodec
import com.loosecannon.notenfc.core.nfc.NdefRecordData
import com.loosecannon.notenfc.core.nfc.OverwriteDecision
import com.loosecannon.notenfc.core.nfc.OverwritePolicy
import com.loosecannon.notenfc.core.nfc.TagPayload
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.nfc.NfcReaderModeSession
import com.loosecannon.notenfc.nfc.TagInspection
import com.loosecannon.notenfc.nfc.TagWriter
import com.loosecannon.notenfc.nfc.WriteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Interim (Phase 1B) writer screen — plain Views, replaced by Compose in Phase 1C. The workflow is
 * the one D3 §9 specifies: read first, confirm before overwriting anything but an empty tag or the
 * same id, check capacity, write off the main thread, read back and compare, optional lock.
 *
 * A row is provisioned on the first tap and reused for every retry on this screen; if the screen
 * closes before a verified write, the row is abandoned (deleted) so no phantom tag remains.
 */
class WriteTagActivity : Activity() {

    private val scope = MainScope()
    private val graph: AppGraph get() = (application as NoteNfcApp).graph
    private lateinit var status: TextView
    private lateinit var lock: CheckBox
    private lateinit var session: NfcReaderModeSession
    private lateinit var target: TagTarget
    private var label: String? = null

    @Volatile private var pending: TagBinding? = null
    @Volatile private var awaitingVerify = false
    @Volatile private var done = false
    @Volatile private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_write_tag)
        status = findViewById(R.id.write_status)
        lock = findViewById(R.id.write_lock)
        findViewById<Button>(R.id.write_done).setOnClickListener { finish() }

        val t = targetFrom(intent)
        if (t == null) {
            Toast.makeText(this, "Nothing to write: no target given.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        target = t
        label = intent.getStringExtra(EXTRA_LABEL)
        session = NfcReaderModeSession(this) { tag -> onTag(tag) }

        lock.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                AlertDialog.Builder(this)
                    .setTitle("Lock permanently?")
                    .setMessage("A locked tag can never be rewritten or reused. Only lock tags that are installed for good.")
                    .setPositiveButton("Lock after writing", null)
                    .setNegativeButton("Don't lock") { _, _ -> lock.isChecked = false }
                    .show()
            }
        }

        status.text = when {
            !session.available -> "This phone has no NFC hardware."
            !session.enabled -> "NFC is turned off. Enable it in system settings, then come back."
            else -> "Hold a blank or reusable tag to the back of the phone.\n\nTarget: ${describe(target)}"
        }
    }

    override fun onResume() { super.onResume(); if (::session.isInitialized) session.start() }
    override fun onPause() { if (::session.isInitialized) session.stop(); super.onPause() }

    override fun onDestroy() {
        scope.cancel()
        val row = pending
        if (!done && row != null) graph.appScope.launch { graph.provisionTag.abandon(row.id) }
        super.onDestroy()
    }

    // --- reader-mode callback (binder thread) -----------------------------------------------

    private fun onTag(tag: Tag) {
        if (busy || done) return
        busy = true
        scope.launch(Dispatchers.IO) {
            var dialogOwnsBusy = false
            try {
                dialogOwnsBusy = handle(tag)
            } catch (e: Exception) {
                say("Failed: ${e.javaClass.simpleName}: ${e.message}\nHold the tag still and try again.")
            } finally {
                if (!dialogOwnsBusy) busy = false
            }
        }
    }

    /** Returns true when a confirmation dialog now owns the `busy` flag. */
    private suspend fun handle(tag: Tag): Boolean {
        val row = pending ?: graph.provisionTag.begin(target, label).also { pending = it }
        val intended = NdefCodec.encodeV1(row.id)
        val inspection = TagWriter.inspect(tag)
        if (inspection == null) {
            say("This tag does not support NDEF. Use an NTAG213/215/216 or similar.")
            return false
        }
        if (awaitingVerify) {
            verify(inspection, intended, row)
            return false
        }
        if (!inspection.writable) {
            say("This tag is read-only (locked). Nothing written.")
            return false
        }
        return when (val d = OverwritePolicy.decide(inspection.existing, row.id)) {
            OverwriteDecision.Proceed -> { write(tag, intended, row); false }
            is OverwriteDecision.Confirm -> { confirm(d.reason, tag, intended, row); true }
        }
    }

    private suspend fun write(tag: Tag, intended: List<NdefRecordData>, row: TagBinding) {
        val wantLock = withContext(Dispatchers.Main) { lock.isChecked }
        when (val r = TagWriter.write(tag, intended, lock = wantLock)) {
            is WriteResult.Written -> if (r.verified) {
                finishWrite(row, tag.id.toHexOrNullSafe(), r.locked)
            } else {
                awaitingVerify = true
                say("Formatted and written (${r.bytes} bytes). Lift the tag off, then hold it again to verify the read-back.")
            }
            is WriteResult.TooSmall -> say("Tag too small: it holds ${r.maxSize} bytes, the message needs ${r.needed}.")
            WriteResult.ReadOnly -> say("This tag is read-only (locked). Nothing written.")
            WriteResult.Unsupported -> say("This tag does not support NDEF.")
            is WriteResult.VerifyMismatch -> say("Read-back differs from what was written. Nothing recorded — try again.")
            is WriteResult.Failed -> say("Write failed: ${r.reason}\nHold the tag still and try again.")
        }
    }

    private suspend fun verify(inspection: TagInspection, intended: List<NdefRecordData>, row: TagBinding) {
        if (inspection.existingRecords == intended) {
            finishWrite(row, inspection.uid, locked = !inspection.writable)
        } else {
            awaitingVerify = false
            say("Read-back differs: the tag holds ${describe(inspection.existing)}. Try writing again.")
        }
    }

    private suspend fun finishWrite(row: TagBinding, uid: String?, locked: Boolean) {
        val completed = graph.provisionTag.complete(row.id, uid)
        done = true
        say(
            "Written and read back byte-identical.\n\n" +
                "Tag id: ${completed.id.value}\nTarget: ${describe(target)}\nLocked: ${if (locked) "yes" else "no"}\n\n" +
                "Close the app and scan the tag to test dispatch.",
        )
    }

    private suspend fun confirm(reason: String, tag: Tag, intended: List<NdefRecordData>, row: TagBinding) {
        withContext(Dispatchers.Main) {
            if (isFinishing || isDestroyed) { busy = false; return@withContext }
            AlertDialog.Builder(this@WriteTagActivity)
                .setTitle("Overwrite this tag?")
                .setMessage("The tag already holds $reason.\n\nKeep it on the phone and choose Overwrite to replace it.")
                .setPositiveButton("Overwrite") { _, _ ->
                    scope.launch(Dispatchers.IO) {
                        try { write(tag, intended, row) } catch (e: Exception) { say("Write failed: ${e.message}. Try again.") } finally { busy = false }
                    }
                }
                .setNegativeButton("Keep it") { _, _ ->
                    busy = false
                    scope.launch { say("Not written. The tag was left as it was.\n\nTarget: ${describe(target)}") }
                }
                .setOnCancelListener { busy = false }
                .show()
        }
    }

    private suspend fun say(text: String) = withContext(Dispatchers.Main) { status.text = text }

    private fun describe(t: TagTarget): String = when (t) {
        is TagTarget.AssetTarget -> "asset ${t.assetId.value}" + (label?.let { " ($it)" } ?: "")
        is TagTarget.LinkTarget -> "link ${t.linkId.value}" + (label?.let { " ($it)" } ?: "")
        TagTarget.None -> "none yet (spare tag; bind it on first scan)"
    }

    private fun describe(p: TagPayload): String = when (p) {
        is TagPayload.V1 -> "noteNFC tag ${p.tagId.value}"
        is TagPayload.LegacyMd5 -> "legacy tag ${p.key}"
        is TagPayload.NewerVersion -> "a newer noteNFC format (${p.version})"
        is TagPayload.Foreign -> "foreign content (${p.description})"
        is TagPayload.Malformed -> "unreadable content (${p.reason})"
        TagPayload.Empty -> "nothing"
    }

    private fun ByteArray?.toHexOrNullSafe(): String? = this?.takeIf { it.isNotEmpty() }?.joinToString("") { "%02x".format(it) }

    companion object {
        const val EXTRA_TARGET_KIND = "target_kind"
        const val EXTRA_TARGET_ID = "target_id"
        const val EXTRA_LABEL = "label"

        fun start(activity: Activity, target: TagTarget, label: String? = null) {
            val intent = Intent(activity, WriteTagActivity::class.java).putExtra(EXTRA_LABEL, label)
            when (target) {
                is TagTarget.AssetTarget -> intent.putExtra(EXTRA_TARGET_KIND, "asset").putExtra(EXTRA_TARGET_ID, target.assetId.value)
                is TagTarget.LinkTarget -> intent.putExtra(EXTRA_TARGET_KIND, "link").putExtra(EXTRA_TARGET_ID, target.linkId.value)
                TagTarget.None -> intent.putExtra(EXTRA_TARGET_KIND, "none")
            }
            activity.startActivity(intent)
        }

        fun targetFrom(intent: Intent): TagTarget? {
            val id = intent.getStringExtra(EXTRA_TARGET_ID)
            return when (intent.getStringExtra(EXTRA_TARGET_KIND)) {
                "asset" -> id?.let { TagTarget.AssetTarget(AssetId(it)) }
                "link" -> id?.let { TagTarget.LinkTarget(LinkId(it)) }
                "none" -> TagTarget.None
                else -> null
            }
        }
    }
}
```

(`toHexOrNullSafe` duplicates the bridge helper only to keep the import list obvious; the implementer may instead `import com.loosecannon.notenfc.nfc.toHexOrNull` and delete the private copy — either is acceptable.)

- [ ] **Step 5: Register in the manifest** inside `<application>`:

```xml
        <activity
            android:name="com.loosecannon.notenfc.ui.interim.WriteTagActivity"
            android:exported="false"
            android:label="@string/write_title" />
```

- [ ] **Step 6: Build.**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, no warnings about unresolved resources.

- [ ] **Step 7: Commit.**

```bash
git add app/src
git commit -m "interim write-tag screen: read first, confirm overwrite, write, read back, optional lock"
```

---

### Task 7: `NfcDispatchActivity`, `TagToolsActivity`, `ShareLinkActivity`, manifest filters and `<queries>`

**Files:**
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/nfc/NfcDispatchActivity.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/ui/interim/TagToolsActivity.kt`, `ShareLinkActivity.kt`
- Create: `app/src/main/res/layout/activity_nfc_dispatch.xml`, `app/src/main/res/layout/activity_tag_tools.xml`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `NdefCodec.decode`, `TagRoute.parse`, `TagPayload`, `Resolution`, `AppGraph.resolveTag/bindTag/openLink/saveLink`, `Intent.ndefRecords()`, `Intent.nfcTag()`, `toNdefMessage()`, `TagWriter.inspect`, `NfcReaderModeSession`, `TargetPicker`, `WriteTagActivity.start`, `LinkLauncher.open`, `LinkLaunchPolicy.extractUri/check`, `LinkNeedsConfirmation`, `LinkRefused`.
- Produces: the exported surface of the app — `NfcDispatchActivity` (NDEF `:tag`, NDEF `:md5_short`, `notenfc://tag/*`), `TagToolsActivity` (launcher), `ShareLinkActivity` (`SEND text/plain`).

- [ ] **Step 1: Layouts and strings.** `activity_nfc_dispatch.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="24dp">
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/dispatch_title"
            android:textSize="20sp"
            android:textStyle="bold" />
        <TextView
            android:id="@+id/dispatch_status"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:textSize="16sp" />
        <LinearLayout
            android:id="@+id/dispatch_actions"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:orientation="vertical" />
        <Button
            android:id="@+id/dispatch_close"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/dispatch_close" />
    </LinearLayout>
</ScrollView>
```

`activity_tag_tools.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="24dp">
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/tools_title"
            android:textSize="20sp"
            android:textStyle="bold" />
        <TextView
            android:id="@+id/tools_counts"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp" />
        <TextView
            android:id="@+id/tools_status"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="@string/tools_hint"
            android:textSize="16sp" />
        <Button
            android:id="@+id/tools_write"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/tools_write" />
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/tools_footer"
            android:textSize="12sp" />
    </LinearLayout>
</ScrollView>
```

Add to `strings.xml`:

```xml
    <string name="dispatch_title">noteNFC tag</string>
    <string name="dispatch_close">Close</string>
    <string name="tools_title">noteNFC</string>
    <string name="tools_hint">Hold a tag to the phone to scan it, or write a new one.</string>
    <string name="tools_write">Write a new tag…</string>
    <string name="tools_footer">Interim Phase 1B screen. Share a note link from Joplin/Obsidian/Logseq to noteNFC to save it and write it to a tag. The debug build also has a Backup screen.</string>
```

- [ ] **Step 2: `NfcDispatchActivity.kt`.**

```kotlin
package com.loosecannon.notenfc.nfc

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.loosecannon.notenfc.NoteNfcApp
import com.loosecannon.notenfc.R
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.nfc.NdefCodec
import com.loosecannon.notenfc.core.nfc.TagPayload
import com.loosecannon.notenfc.core.nfc.TagRoute
import com.loosecannon.notenfc.core.usecase.OpenLink
import com.loosecannon.notenfc.core.usecase.Resolution
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.links.LinkLauncher
import com.loosecannon.notenfc.ui.interim.TargetPicker
import com.loosecannon.notenfc.ui.interim.WriteTagActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The one NFC-exported component (D3 §9, security doc "NFC dispatch"). Background scans arrive
 * here through the two `NDEF_DISCOVERED` filters; `notenfc://tag/<uuid>` arrives through `VIEW`.
 * Only `EXTRA_NDEF_MESSAGES`, `EXTRA_TAG` and the data URI are read — every other extra is ignored.
 *
 * A link tag launches immediately and this activity finishes (R-7). Everything else renders on
 * the interim Phase 1B result screen below; Phase 1C replaces the rendering with Compose routes.
 */
class NfcDispatchActivity : Activity() {

    private val scope = MainScope()
    private val graph: AppGraph get() = (application as NoteNfcApp).graph
    private lateinit var status: TextView
    private lateinit var actions: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_dispatch)
        status = findViewById(R.id.dispatch_status)
        actions = findViewById(R.id.dispatch_actions)
        findViewById<Button>(R.id.dispatch_close).setOnClickListener { finish() }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun handle(intent: Intent) {
        val payload = payloadOf(intent)
        if (payload == null) {
            Toast.makeText(this, "Nothing to resolve.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        actions.removeAllViews()
        status.text = "Resolving…"
        scope.launch {
            val resolution = try {
                graph.resolveTag.run(payload)
            } catch (e: Exception) {
                status.text = "Could not resolve this tag: ${e.javaClass.simpleName}: ${e.message}"
                return@launch
            }
            render(resolution)
        }
    }

    private fun payloadOf(intent: Intent): TagPayload? = when (intent.action) {
        NfcAdapter.ACTION_NDEF_DISCOVERED -> NdefCodec.decode(intent.ndefRecords().orEmpty())
        Intent.ACTION_VIEW -> tagRoute(intent.data)
        else -> null
    }

    private fun tagRoute(uri: Uri?): TagPayload? =
        TagRoute.parse(uri?.scheme, uri?.host, uri?.pathSegments.orEmpty())

    private fun render(r: Resolution) {
        when (r) {
            is Resolution.LaunchLink -> launch(r.link)
            is Resolution.OpenAsset -> status.text =
                "Asset: ${r.asset.name}" + (r.asset.category.takeIf { it.isNotEmpty() }?.let { "\nCategory: $it" } ?: "") +
                    "\n\nTag ${r.tag.id.value}" + (r.tag.label?.let { " ($it)" } ?: "") +
                    "\n\n(The asset screen arrives in Phase 1C.)"
            is Resolution.Unbound -> {
                status.text = "Tag ${r.tag.id.value} is not bound to anything yet."
                addBindAction(r.tag.payloadFormat, r.tag.payloadKey)
            }
            is Resolution.Revoked -> status.text =
                "This tag was marked ${r.tag.status.name.lowercase()}.\n\n(Re-activation arrives with the Phase 1C bind/rebind screens.)"
            is Resolution.UnknownV1 -> {
                status.text = "Unknown noteNFC tag ${r.tagId.value}.\n\nThis phone has no record of it: restore a backup, or bind it now."
                addBindAction(PayloadFormat.V1, r.tagId.value)
            }
            is Resolution.UnknownLegacy -> {
                status.text = "Legacy noteNFC tag (${r.key}).\n\nBind it as-is, or rewrite it in payload format v1."
                addBindAction(PayloadFormat.LEGACY_MD5, r.key)
                addWriteAction("Rewrite as a new v1 tag for…")
            }
            is Resolution.NeedsNewerApp -> status.text =
                "This tag was written by a newer noteNFC (payload format ${r.version}). Update the app to use it."
            is Resolution.NotOurs -> {
                status.text = when (val p = r.payload) {
                    TagPayload.Empty -> "Empty tag."
                    is TagPayload.Foreign -> "Not a noteNFC tag: ${p.description}"
                    is TagPayload.Malformed -> "Unreadable noteNFC record: ${p.reason}"
                    else -> "Not a noteNFC tag."
                }
                addWriteAction("Write a new v1 tag over it for…")
            }
        }
    }

    private fun launch(link: ExternalLink) {
        scope.launch {
            when (val out = graph.openLink.run(link.id)) {
                is OpenLink.Outcome.Launch -> { LinkLauncher.open(this@NfcDispatchActivity, out.uri); finish() }
                is OpenLink.Outcome.Refused -> status.text = "Link refused: ${out.reason}\n\n${link.uri}"
                is OpenLink.Outcome.Missing -> status.text = "The link this tag pointed at no longer exists."
            }
        }
    }

    private fun addBindAction(format: PayloadFormat, key: String) = addAction("Bind to an asset or link…") {
        TargetPicker.show(this, graph, scope, allowNone = false) { target ->
            scope.launch {
                try {
                    val bound = graph.bindTag.run(format, key, target)
                    actions.removeAllViews()
                    status.text = "Bound tag ${bound.id.value} to ${describe(target)}.\n\nScan it again to see it resolve."
                } catch (e: Exception) {
                    status.text = "Could not bind: ${e.message}"
                }
            }
        }
    }

    private fun addWriteAction(label: String) = addAction(label) {
        TargetPicker.show(this, graph, scope, allowNone = true) { target ->
            WriteTagActivity.start(this, target)
            finish()
        }
    }

    private fun addAction(label: String, onClick: () -> Unit) {
        actions.addView(Button(this).apply { text = label; setOnClickListener { onClick() } })
    }

    private fun describe(t: TagTarget): String = when (t) {
        is TagTarget.AssetTarget -> "asset ${t.assetId.value}"
        is TagTarget.LinkTarget -> "link ${t.linkId.value}"
        TagTarget.None -> "nothing"
    }
}
```

- [ ] **Step 3: `TagToolsActivity.kt`.**

```kotlin
package com.loosecannon.notenfc.ui.interim

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Parcelable
import android.widget.Button
import android.widget.TextView
import com.loosecannon.notenfc.NoteNfcApp
import com.loosecannon.notenfc.R
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.nfc.NfcDispatchActivity
import com.loosecannon.notenfc.nfc.NfcReaderModeSession
import com.loosecannon.notenfc.nfc.TagWriter
import com.loosecannon.notenfc.nfc.toNdefMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Interim (Phase 1B) launcher screen: counts, in-app scan (reader mode → the same dispatch path a
 * background scan takes), and "write a new tag". Replaced by the Compose shell in Phase 1C.
 */
class TagToolsActivity : Activity() {

    private val scope = MainScope()
    private val graph: AppGraph get() = (application as NoteNfcApp).graph
    private lateinit var counts: TextView
    private lateinit var status: TextView
    private lateinit var session: NfcReaderModeSession
    @Volatile private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tag_tools)
        counts = findViewById(R.id.tools_counts)
        status = findViewById(R.id.tools_status)
        session = NfcReaderModeSession(this) { tag -> onTag(tag) }
        findViewById<Button>(R.id.tools_write).setOnClickListener {
            TargetPicker.show(this, graph, scope, allowNone = true) { target -> WriteTagActivity.start(this, target) }
        }
        if (!session.available) status.text = "This phone has no NFC hardware."
    }

    override fun onResume() {
        super.onResume()
        session.start()
        if (session.available && !session.enabled) status.text = "NFC is turned off. Enable it in system settings."
        refreshCounts()
    }

    override fun onPause() { session.stop(); super.onPause() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    /** Reads the tag ourselves (reader mode skips the platform NDEF read) and hands it to dispatch. */
    private fun onTag(tag: Tag) {
        if (busy) return
        busy = true
        scope.launch(Dispatchers.IO) {
            try {
                val records = TagWriter.inspect(tag)?.existingRecords.orEmpty()
                val intent = Intent(this@TagToolsActivity, NfcDispatchActivity::class.java)
                    .setAction(NfcAdapter.ACTION_NDEF_DISCOVERED)
                    .putExtra(NfcAdapter.EXTRA_TAG, tag)
                if (records.isNotEmpty()) {
                    intent.putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf<Parcelable>(records.toNdefMessage()))
                }
                withContext(Dispatchers.Main) { startActivity(intent) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status.text = "Could not read the tag: ${e.message}" }
            } finally {
                busy = false
            }
        }
    }

    private fun refreshCounts() {
        scope.launch {
            counts.text = try {
                "${graph.assets.all().size} assets · ${graph.tags.all().size} tags · ${graph.links.all().size} links"
            } catch (e: Exception) {
                "counts unavailable: ${e.message}"
            }
        }
    }
}
```

- [ ] **Step 4: `ShareLinkActivity.kt`.**

```kotlin
package com.loosecannon.notenfc.ui.interim

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.loosecannon.notenfc.NoteNfcApp
import com.loosecannon.notenfc.core.links.LinkCheck
import com.loosecannon.notenfc.core.links.LinkLaunchPolicy
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.di.AppGraph
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Interim (Phase 1B) share-sheet entry: `ACTION_SEND text/plain` → first URI → policy → standalone
 * link → write screen, then back to the caller (the original one-tap flow, D13 §2 last row).
 * Phase 1C replaces this with the link card (D6 §8 / #6).
 */
class ShareLinkActivity : Activity() {

    private val scope = MainScope()
    private val graph: AppGraph get() = (application as NoteNfcApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = if (intent.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        val uri = LinkLaunchPolicy.extractUri(text)
        if (uri == null) {
            Toast.makeText(this, "No link found in the shared text.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        when (val check = LinkLaunchPolicy.check(uri)) {
            is LinkCheck.Rejected -> {
                Toast.makeText(this, "This link can't be saved: ${check.reason}", Toast.LENGTH_LONG).show()
                finish()
            }
            is LinkCheck.Accepted -> save(check.uri, labelFrom(text, check.uri), confirmedOther = false)
            is LinkCheck.NeedsConfirmation -> AlertDialog.Builder(this)
                .setTitle("Unknown link type")
                .setMessage("'${check.scheme}' links are not in noteNFC's list. It will open with whatever app claims that scheme. Save it anyway?\n\n${check.uri}")
                .setPositiveButton("Save") { _, _ -> save(check.uri, labelFrom(text, check.uri), confirmedOther = true) }
                .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun save(uri: String, label: String?, confirmedOther: Boolean) {
        scope.launch {
            try {
                val link = graph.saveLink.run(uri, label, confirmedOther)
                WriteTagActivity.start(this@ShareLinkActivity, TagTarget.LinkTarget(link.id), link.label)
            } catch (e: Exception) {
                Toast.makeText(this@ShareLinkActivity, "Could not save the link: ${e.message}", Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    /** The first line of the shared text that is not the URI itself, e.g. a note title. */
    private fun labelFrom(text: String?, uri: String): String? =
        text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() && !it.contains(uri) }?.take(80)
}
```

- [ ] **Step 5: The manifest.** Replace `app/src/main/AndroidManifest.xml` with:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.NFC" />
    <uses-feature android:name="android.hardware.nfc" android:required="true" />

    <!-- Handler-present checks for the outbound link allowlist (D3 §10; API 30+ package visibility). -->
    <queries>
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <data android:scheme="joplin" />
        </intent>
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <data android:scheme="obsidian" />
        </intent>
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <data android:scheme="logseq" />
        </intent>
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <data android:scheme="http" />
        </intent>
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <data android:scheme="https" />
        </intent>
    </queries>

    <application
        android:name="com.loosecannon.notenfc.NoteNfcApp"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name">

        <!-- Interim Phase 1B launcher: scan in-app, write tags. Replaced by the Compose shell in 1C. -->
        <activity
            android:name="com.loosecannon.notenfc.ui.interim.TagToolsActivity"
            android:exported="true"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Share a note link → save it → write it to a tag → back to the caller. -->
        <activity
            android:name="com.loosecannon.notenfc.ui.interim.ShareLinkActivity"
            android:exported="true"
            android:excludeFromRecents="true">
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        </activity>

        <!-- The only NFC-exported component (D3 §9). No TECH_DISCOVERED catch-all. -->
        <activity
            android:name="com.loosecannon.notenfc.nfc.NfcDispatchActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:label="@string/dispatch_title">
            <intent-filter>
                <action android:name="android.nfc.action.NDEF_DISCOVERED" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:scheme="vnd.android.nfc" android:host="ext" android:path="/com.loosecannon.notenfc:tag" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.nfc.action.NDEF_DISCOVERED" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:scheme="vnd.android.nfc" android:host="ext" android:path="/com.loosecannon.notenfc:md5_short" />
            </intent-filter>
            <!-- notenfc://tag/<uuid>: resolves exactly like a scan; navigation only. -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="notenfc" android:host="tag" />
            </intent-filter>
        </activity>

        <activity
            android:name="com.loosecannon.notenfc.ui.interim.WriteTagActivity"
            android:exported="false"
            android:label="@string/write_title" />

    </application>
</manifest>
```

- [ ] **Step 6: Build both variants and run everything.**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`
Expected: BUILD SUCCESSFUL; `:core` 105, `:app` 33. Then confirm the merged manifest has no `TECH_DISCOVERED` and exactly one NFC-exported activity:

```bash
grep -c "TECH_DISCOVERED" app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml   # expect 0
grep -c "NDEF_DISCOVERED" app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml   # expect 2
```

(If the merged-manifest path differs under AGP 9.4, `find app/build -name AndroidManifest.xml -path '*release*'` and grep the one under `merged_manifest`.)

- [ ] **Step 7: Commit.**

```bash
git add app/src
git commit -m "nfc dispatch activity for :tag, md5_short and notenfc://tag; interim launcher + share entry; queries"
```

---

### Task 8: Design-doc corrections, evidence document, device checklist, final gate

**Files:**
- Modify: `docs/design/04-domain-data-model.md` (§3: AAR package line), `docs/design/03-target-architecture.md` (§9: `LegacyKey` mention; "re-link by sharing the note" bullet; module tree line `nfc/ TagPayload.kt · NdefCodec.kt · LegacyKey.kt`; §2 diagram line mentioning `LegacyKey`), `docs/design/issues/new-tag-payload-v1-legacy-resolver.md` (AAR package line; note that `LegacyKey` was removed by D13), `docs/design/README.md` (index row for the evidence doc)
- Create: `docs/design/phase-1b-evidence.md`

**Interfaces:** none (documentation).

- [ ] **Step 1: Correct D4 §3.** In the payload format block replace `record 1: Android Application Record for com.looseCannon.noteNFC` with `record 1: Android Application Record for com.loosecannon.notenfc (the applicationId, D13 §4)`. Make the same replacement in `docs/design/issues/new-tag-payload-v1-legacy-resolver.md`, and under its "The codec (D3 §9)" heading replace the sentence beginning "`LegacyKey.compute` is preserved verbatim" with: "> Policy update (D13, 2026-09-14): `LegacyKey` was removed in Phase 1B; the MD5 vectors remain in D1/D6 as protocol documentation."

- [ ] **Step 2: Correct D3.** In §9: change "`NdefCodec` and `LegacyKey` live in `:core`" to "`NdefCodec`, `OverwritePolicy` and `TagRoute` live in `:core`"; replace the last bullet ("Unknown-tag resolutions offer: bind to an existing asset/link, create an asset, or (legacy key) "re-link by sharing the note" (D6).") with "Unknown-tag resolutions offer: bind to an existing asset/link, create an asset, or (legacy tag) bind as-is / rewrite in payload format v1 (D13 §3). Re-link was dropped by D13."; in the §2 component diagram change `· LegacyKey (MD5[0:8])` to `· OverwritePolicy · TagRoute`; in the module tree change `nfc/           TagPayload.kt · NdefCodec.kt · LegacyKey.kt` to `nfc/           NdefCodec.kt (TagPayload) · OverwritePolicy.kt · TagRoute.kt`. Verify with `grep -n LegacyKey docs/design/03-target-architecture.md` → no output.

- [ ] **Step 3: Write `docs/design/phase-1b-evidence.md`** with these sections, filled from the actual branch (run the commands; paste real numbers):

```markdown
# Phase 1B evidence — tag payload format v1, resolver, reader mode, safe writer

Branch `phase-1b` from master `498a0e8`. Date 2026-09-14.

## 1. Exit criteria (D7 §1B) → evidence

| # | Criterion | Evidence | Status |
|---|---|---|---|
| 1 | An NTAG213 holds the v1 message and reads back byte-identical | `WriteTagActivity` read-back compare (`TagWriter.write` → `VerifyMismatch` unless `readBack == intended`); `NdefCodecV1Test.exactByteLayout/roundTrips/fitsAnNtag213`; device run: §4 row 1 | JVM-proven; device: see §4 |
| 2 | Foreign NDEF content (incl. an old `md5_short` tag) triggers the confirmation and is not written without it; a legacy tag is recognised as such | `OverwritePolicyTest` (every non-empty/non-same payload → `Confirm`); `WriteTagActivity.confirm` writes only from the dialog's positive button; `NdefCodecV1Test.legacyRecordStillDecodes`, `ResolveTagTest.unknownV1AndLegacyAreDistinct`; device: §4 rows 2–3 | JVM-proven; device: see §4 |
| 3 | Scanning with the app closed opens it through `NfcDispatchActivity` | manifest: two `NDEF_DISCOVERED` filters, no `TECH_DISCOVERED`; AAR pins `com.loosecannon.notenfc`; device: §4 row 4 | device: see §4 |
| 4 | (optional) an old-APK tag is recognised as legacy on the device | device: §4 row 5 | optional |

## 2. What shipped (by commit)
<git log --oneline master..HEAD, one line each with a sentence>

## 3. Tests
| Module | Class | Tests |
<table from the Gradle XML reports: `grep -h -o 'tests="[0-9]*"' core/build/test-results/test/*.xml app/build/test-results/testDebugUnitTest/*.xml`>
Totals: :core N, :app M.

## 4. Device checklist (owner's NFC phone; old `com.looseCannon.noteNFC` app uninstalled first — D13 §4)
| # | Step | Expected | Result |
|---|---|---|---|
| 1 | Tools → Write a new tag… → New asset "Hot tub" → hold a blank NTAG213 | "Written and read back byte-identical", Tag id shown; Tools counts show 1 asset / 1 tag | |
| 2 | Write again for the same target → hold the tag from row 1 | dialog "The tag already holds a different noteNFC tag (…)"; choose Keep it → "Not written" | |
| 3 | Hold an old `md5_short` tag on the Write screen | dialog names "a legacy noteNFC tag (xxxxxxxx)"; Keep it → not written | |
| 4 | Close the app (swipe from recents) → tap the tag from row 1 | app opens on the noteNFC tag screen: "Asset: Hot tub" | |
| 5 | (optional) tap an old `md5_short` tag with the app closed | "Legacy noteNFC tag (xxxxxxxx)" with Bind / Rewrite | |
| 6 | Joplin → share a note's external link → noteNFC → hold a blank tag → Done | link saved, tag written; back in Joplin | |
| 7 | Close the app → tap the tag from row 6 | Joplin opens the note, noteNFC shows no screen | |
| 8 | `adb shell am start -a android.intent.action.VIEW -d notenfc://tag/<id from row 1>` | same screen as row 4 | |
| 9 | `adb shell am start -a android.intent.action.VIEW -d notenfc://tag/nope` | "Unreadable noteNFC record: not a tag id" — no crash | |
| 10 | Tap a blank/foreign tag with the app closed | nothing happens (no `TECH_DISCOVERED` filter): noteNFC is not offered | |
| 11 | Debug build: Backup → Export; wipe; Import → tap the tag from row 1 | resolves to "Hot tub" with the same tag id (identity survives) | |

Result column: filled in by whoever runs the phone session (see §5).

## 5. Status of the device proof
<either the results, or: "No device was attached to the build machine during Phase 1B; rows 1–11 are pending the owner's phone session. Everything above the device line is JVM-proven.">

## 6. Rulings made during execution
<copy from the SDD ledger>

## 7. Deferred to Phase 1C (bind/rebind UX, Compose)
- interim `ui.interim` screens replaced by Compose routes; asset screen; revoke/re-activate; retiring a bound `LEGACY_MD5` row when its tag is rewritten in v1; link card on share; `lastOpenedAt` display; targetSdk 37 `DISPATCH_NFC_MESSAGE` (Phase 7).

## 8. Cutover reminder
Install the new package, uninstall `com.looseCannon.noteNFC` (both match the `md5_short` filter until then), back up `~/.config/notenfc/notenfc-release.jks` off-machine.
```

- [ ] **Step 4: README index.** Add to the table in `docs/design/README.md` after the `13-compatibility-policy.md` row:

```markdown
| [phase-1b-evidence.md](phase-1b-evidence.md) | Phase 1B evidence: payload format v1, resolver, reader mode, safe writer; device checklist |
```

(Add a `phase-1a-evidence.md` row too if it is missing.)

- [ ] **Step 5: Final gate.**

Run: `./gradlew clean :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`
Expected: BUILD SUCCESSFUL; record the APK sizes (`ls -l app/build/outputs/apk/*/*.apk`) in the evidence doc §2. `grep -rn "LegacyKey\|LegacyLinkPolicy\|TECH_DISCOVERED\|looseCannon" app/src core/src` prints nothing.

- [ ] **Step 6: Commit.**

```bash
git add docs/design
git commit -m "phase 1b evidence + fix the AAR package in D4/#31, drop LegacyKey from D3"
```

---

## Self-review

- **Spec coverage.** D7 §1B source areas: v1 codec (T2), reader-mode session + writer with confirm/read-back/AAR (T5, T6), `NfcDispatchActivity` with both filters and no `TECH_DISCOVERED` (T7), `ResolveTag`/`BindTag` (T4), `LinkLaunchPolicy` + `<queries>` (T3, T7), unknown/legacy flows per D13 §3 (T7 dispatch actions), `notenfc://tag` (T3 `TagRoute`, T7 filter), delete legacy activities and `LegacyKey`/`LegacyLinkPolicy` (T1). D7 §1B tests: v1 round-trip/layout/unknown version/malformed (T2), `ResolveTagTest` every resolution incl. unknown legacy (T4), `LinkLaunchPolicyTest` (T3). Exit criteria 1–4 map to the evidence table (T8). Security rows: NFC payload validation (T2), dispatch namespacing + AAR (T2/T7), tag writing (T6), deep links navigation-only (T3/T7), outbound URI policy at save and launch (T3), exported components limited to launcher/share/dispatch (T7).
- **Placeholders.** None: every step carries its code; the evidence doc's angle-bracket lines are instructions to paste measured output, not TBDs.
- **Type consistency.** `TagWriter.inspect` returns `TagInspection?` and `write` returns `WriteResult` (T5) as consumed in T6/T7; `WriteTagActivity.start(activity, target, label)` (T6) as called in T7; `TargetPicker.show(activity, graph, scope, allowNone, onPicked)` (T6) as called in T7; `Resolution` variants (T4) exhaustively matched in T7; `OpenLink.Outcome` (T3) matched in T7; `ProvisionTag.begin/complete/abandon` (T4) as used in T5 test and T6; `Intent.ndefRecords()` nullable (T5) used with `.orEmpty()` in T7; `AppGraph` members (T5) as referenced in T6/T7.
