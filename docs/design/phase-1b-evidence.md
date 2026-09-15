# Phase 1B evidence — tag payload format v1, resolver, reader mode, safe writer

Branch `phase-1b` from master `498a0e8`. Date 2026-09-14.

## 1. Exit criteria (D7 §1B) → evidence

| # | Criterion | Evidence | Status |
|---|---|---|---|
| 1 | An NTAG213 holds the v1 message and reads back byte-identical | `WriteTagActivity` read-back compare (`TagWriter.write` → `VerifyMismatch` unless `readBack == intended`); `NdefCodecV1Test.exactByteLayout/roundTrips/fitsAnNtag213`; device run: §4 row 1 | JVM-proven; device pending |
| 2 | Foreign NDEF content (incl. an old `md5_short` tag) triggers the confirmation and is not written without it; a legacy tag is recognised as such | `OverwritePolicyTest` (every non-empty/non-same payload → `Confirm`); `WriteTagActivity.confirm` writes only from the dialog's positive button; `NdefCodecV1Test.legacyRecordStillDecodes`, `ResolveTagTest.unknownV1AndLegacyAreDistinct`; device: §4 rows 2–4 | JVM-proven; device pending |
| 3 | Scanning with the app closed opens it through `NfcDispatchActivity` | manifest: two `NDEF_DISCOVERED` filters (`:tag`, `:md5_short`), no `TECH_DISCOVERED`; AAR pins `com.loosecannon.notenfc`; device: §4 row 5 | device pending |
| 4 | (optional) an old-APK tag is recognised as legacy on the device | device: §4 row 6 | optional; device pending |

## 2. What shipped (by commit)

- `fc34d58` — phase 1b plan: payload v1, resolver, reader mode, safe writer. The task plan for the whole slice.
- `123a765` — drop the 2024 legacy activities, `LegacyKey` and the tech-discovered catch-all. Clears the ground before the new codec lands.
- `27c7e91` — tag payload format v1: codec, AAR, overwrite policy. `NdefCodec` (`TagPayload` incl. `V1`/`LegacyMd5`/`Foreign`/`Malformed`/`NewerVersion`), the AAR record, `OverwritePolicy`.
- `db780dc` — link launch policy, `notenfc://tag` route, save/open link use cases. `LinkLaunchPolicy`, `TagRoute`, `SaveLink`/`OpenLink`.
- `05e8ec3` — resolve/bind/provision tag use cases + create asset. `ResolveTag`, `BindTag`, `ProvisionTag`, `CreateAsset`.
- `e70592a` — resolve tag: look the row up inside the transaction. Closes a lost-update window found in review (ruling 6, §6).
- `ebae784` — nfc adapter: ndef bridge, reader-mode session, tag writer with read-back; wire the use cases. `NfcReaderModeSession`, `TagWriter`, `TagWriter.inspect`/`write`.
- `6bd28c7` — tag writer: say which calls throw; make the scan-clock assertions mean something. Documents `IOException`/`TagLostException` on `inspect`; `write` never throws for tag I/O (ruling 7, §6).
- `6318223` — interim write-tag screen: read first, confirm overwrite, write, read back, optional lock. `WriteTagActivity` (View-based, ruling 1, §6).
- `dcf3dee` — write screen: don't leave the lock armed on back; picker survives db errors. `TargetPicker` hardening.
- `c45c33d` — nfc dispatch activity for `:tag`, `md5_short` and `notenfc://tag`; interim launcher + share entry; queries. `NfcDispatchActivity`, `TagToolsActivity`, `ShareLinkActivity`, manifest `<queries>`.
- `14956ec` — dispatch/share: survive hostile extras, rotation and spanned share text. Defensive parsing on `EXTRA_TEXT`, config-change handling.

## 3. Tests

| Module | Class | Tests |
|---|---|---|
| `:core` | `BackupCodecTest` | 23 |
| `:core` | `LinkLaunchPolicyTest` | 9 |
| `:core` | `NdefCodecTest` | 8 |
| `:core` | `NdefCodecV1Test` | 14 |
| `:core` | `OverwritePolicyTest` | 8 |
| `:core` | `TagRouteTest` | 3 |
| `:core` | `BackupUseCasesTest` | 8 |
| `:core` | `LinkUseCasesTest` | 9 |
| `:core` | `ResolveTagTest` | 8 |
| `:core` | `TagBindingUseCasesTest` | 14 |
| `:app` | `RestoreProofTest` | 2 |
| `:app` | `AssetDaoTest` | 5 |
| `:app` | `ExternalLinkDaoTest` | 5 |
| `:app` | `NfcTagDaoTest` | 7 |
| `:app` | `RoomRepositoriesTest` | 11 |
| `:app` | `TagUseCasesRoomTest` | 3 |

Totals: `:core` 104, `:app` 33.

## 4. Device checklist (owner's NFC phone; old `com.looseCannon.noteNFC` app uninstalled first — D13 §4)

| # | Step | Expected | Result |
|---|---|---|---|
| 1 | Tools → Write a new tag… → New asset "Hot tub" → hold a blank NTAG213 | "Written and read back byte-identical", Tag id shown; Tools counts show 1 asset / 1 tag | |
| 2 | Write again for the same target → hold the tag from row 1 | dialog "The tag already holds a different noteNFC tag (…)"; choose Keep it → "Not written" — then hold the tag on the Tools screen: it still resolves to the original id (nothing was written) | |
| 3 | Hold an old `md5_short` tag on the Write screen | dialog names "a legacy noteNFC tag (xxxxxxxx)"; Keep it → not written — then hold the tag on the Tools screen: it still resolves to the original legacy key (nothing was written) | |
| 4 | Hold a tag carrying a URL or text record (any commercial NFC sticker, or one written by another app) on the Write screen | dialog names "foreign NDEF content (tnf=1 …)"; choose Keep it → tapping the tag with noteNFC closed still opens it in the phone's default handler (not written) | |
| 5 | Close the app (swipe from recents) → tap the tag from row 1 | app opens on the noteNFC tag screen: "Asset: Hot tub" | |
| 6 | (optional) tap an old `md5_short` tag with the app closed | "Legacy noteNFC tag (xxxxxxxx)" with Bind / Rewrite | |
| 7 | Joplin → share a note's external link → noteNFC → hold a blank tag → Done | link saved, tag written; back in Joplin | |
| 8 | Close the app → tap the tag from row 7 | Joplin opens the note, noteNFC shows no screen | |
| 9 | `adb shell am start -a android.intent.action.VIEW -d notenfc://tag/<id from row 1>` | same screen as row 5 | |
| 10 | `adb shell am start -a android.intent.action.VIEW -d notenfc://tag/nope` | "Unreadable noteNFC record: not a tag id" — no crash | pass on the attached phone (a0680e5): "Unreadable noteNFC record: not a tag id: 'nope'", no crash |
| 11 | Tap a blank/foreign tag with the app closed | nothing happens (no `TECH_DISCOVERED` filter): noteNFC is not offered | |
| 12 | Debug build: Backup → Export; wipe; Import → tap the tag from row 1 | resolves to "Hot tub" with the same tag id (identity survives) | |
| 13 | `adb shell am force-stop com.loosecannon.notenfc` → tap a written tag | expected on Android 17: **no** dispatch until the app is launched once (platform rule, D3 §9). Record the observed behaviour; it is a result, not a defect. | |

Note: Android 17 (the attached phone) does not deliver NFC intents to a package in the *stopped*
state: a fresh `adb install` leaves noteNFC stopped until it is launched once, so run the
Tools-screen rows (1–4) before the close-the-app tap rows (5, 6, 8, 11). Swiping the app from
recents is *not* the stopped state.

Result column: filled in by whoever runs the phone session (see §5).

## 5. Status of the device proof

A the owner's Android 17 (SDK 37) phone is attached to the build machine, and the debug
APK from `a0680e5` was installed on it. Two things were proven there:

- `dumpsys package com.loosecannon.notenfc` showed exactly the two `NDEF_DISCOVERED` literal
  filters (`:tag` and `:md5_short`), the browsable `notenfc://tag` `VIEW` filter, and no
  `TECH_DISCOVERED` filter anywhere in the package.
- §4's malformed deep-link row (row 10) passed on the device: "Unreadable noteNFC record: not a
  tag id: 'nope'", no crash.

Every tap-based row of §4 remains pending. NFC was off on the phone during that session, and the
reader-mode flag defect (B-1, fixed in this commit) would have blocked writing in any case: with
`FLAG_READER_SKIP_NDEF_CHECK` set the platform never marks a delivered tag as NDEF, so
`Ndef.get(tag)` is null and there is nothing to write to. The installed build must be refreshed
from this commit before the tap rows are run. No tap result is claimed anywhere in this document.

## 6. Rulings made during execution

- The interim screens are plain Views in the main source set (not debug-only) because the exit
  criteria need a release-buildable writer and dispatch path; Phase 1C replaces them with
  Compose.
- Link classification is by scheme only; D3 §10's finer Joplin/Obsidian shape checks are
  deferred to the 1C link card.
- Retiring a bound `LEGACY_MD5` row when its tag is rewritten in v1 is 1C scope; 1B offers
  "rewrite" only for unknown legacy/foreign tags.
- GitHub issue #31 still names the old AAR package; only the local copy is corrected in 1B, the
  owner decides on editing GitHub.
- `testImplementation(libs.kotlinx.coroutines.test)` was added to `:core` (test-scoped, catalog
  alias) for `runTest`.
- `ResolveTag.known` looks the row up inside the write transaction (review finding: lost-update
  window); unknown scans open a no-op transaction.
- `TagWriter.inspect` propagates `IOException`/`TagLostException` (documented); `write` never
  throws for tag I/O.
- Final review B-1: `FLAG_READER_SKIP_NDEF_CHECK` removed from the reader-mode session — with
  it set the platform never marks a tag as NDEF, so `Ndef.get()` is null and nothing can be
  written; the flag is only for raw-technology access. S-1: on the `NdefFormatable` path the tag
  is formatted unlocked and locked only after the second-tap read-back verifies the bytes.
- Review-driven hardening beyond the plan: lock-warning cancel listener, `TargetPicker` catches
  repository failures, `configChanges` on `WriteTagActivity` and `ShareLinkActivity`,
  `LinkLauncher` catches `SecurityException`, `NfcDispatchActivity` guards `payloadOf` and
  `openLink` against exceptions, `ShareLinkActivity` reads `EXTRA_TEXT` as `CharSequence`.

## 7. Deferred to Phase 1C (bind/rebind UX, Compose)

**Must be explicit 1C work items (owner ruling at 1B closure), not buried minors:**

- Broad `catch (e: Exception)` blocks inside coroutine scopes also swallow `CancellationException`
  (`WriteTagActivity`, `NfcDispatchActivity`, `TargetPicker`). Clean this up when the Compose /
  session layer is built; do not copy the pattern into ViewModels.
- The "tag write succeeded but the database completion failed" window (`WriteTagActivity.finishWrite`
  → `ProvisionTag.complete` throws after a verified read-back; `onDestroy` then abandons the row):
  1C needs an explicit recovery path. The intended shape is that the next scan of such a tag
  resolves as an unknown v1 tag (`Resolution.UnknownV1`) that can be safely rebound — which is
  already what `ResolveTag` + `BindTag` do today, so the state is recoverable, not lost — and the
  write screen should say so instead of reporting a bare failure.

- interim `ui.interim` screens replaced by Compose routes; asset screen; revoke/re-activate;
  retiring a bound `LEGACY_MD5` row when its tag is rewritten in v1; link card on share;
  `lastOpenedAt` display; targetSdk 37 `DISPATCH_NFC_MESSAGE` (Phase 7).

### Review notes carried forward

- `LinkLaunchPolicy.extractUri` doesn't exclude curly quotes and can trim a legitimate trailing
  `)`.
- `BindTag` doesn't trim `label` while `ProvisionTag` does.
- Silent `locked = false` when `canMakeReadOnly()` is false.
- `WriteTagActivity` orphan window if `complete()` throws after a verified write.
- `busy` flags are non-atomic test-and-set.
- `catch (Exception)` inside coroutine scopes also catches `CancellationException`.
- `EXTRA_TAG` is carried by the in-app scan but never read (`Intent.nfcTag()` unused).
- In-app scan renders unparsable/unsupported tags as "Empty tag".
- A malformed deep link still offers the write action.
- `ShareLinkActivity` has a blank window behind its dialog.

## 8. Cutover reminder

Install the new package, uninstall `com.looseCannon.noteNFC` (both match the `md5_short` filter
until then), back up `~/.config/notenfc/notenfc-release.jks` off-machine.

## 9. Final gate

`./gradlew clean :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` →
**BUILD SUCCESSFUL** (98 actionable tasks: 52 executed, 45 from cache, 1 up-to-date).

APK sizes:

```
-rw-r--r--. 1 root root 3323952 Sep 14 20:37 app/build/outputs/apk/debug/app-debug.apk
-rw-r--r--. 1 root root 2474341 Sep 14 20:37 app/build/outputs/apk/release/app-release.apk
```

(debug ≈ 3246 KiB, release ≈ 2416 KiB). `~/.config/notenfc/keystore.properties` exists on this
machine, and the release APK is signed with the real `noteNFC` release cert (verified with
`apksigner verify --print-certs`, DN `CN=noteNFC, O=GonzRon`) — not debug-signed.

`grep -rn "LegacyKey\|LegacyLinkPolicy\|TECH_DISCOVERED\|looseCannon" app/src core/src` finds one
line, a test method name, not the removed class:

```
core/src/test/kotlin/com/loosecannon/notenfc/core/nfc/NdefCodecTest.kt:11:    @Test fun decodesValidLegacyKey() {
```

`decodesValidLegacyKey` exercises `NdefCodec` decoding a legacy `md5_short` record into
`TagPayload.LegacyMd5`; it is a substring match on the pattern, not a reference to the deleted
`LegacyKey` class, which does not exist anywhere in `app/src` or `core/src` (confirmed:
`grep -rln "class LegacyKey\|object LegacyKey" app/src core/src` → no output). No other pattern
(`LegacyLinkPolicy`, `TECH_DISCOVERED`, `looseCannon`) appears anywhere in either source tree.
