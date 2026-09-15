# Phase 1B evidence — tag payload format v1, resolver, reader mode, safe writer

Branch `phase-1b` from master `498a0e8`. Date 2026-09-14.

## 1. Exit criteria (D7 §1B) → evidence

| # | Criterion | Evidence | Status |
|---|---|---|---|
| 1 | An NTAG213 holds the v1 message and reads back byte-identical | `WriteTagActivity` read-back compare (`TagWriter.write` → `VerifyMismatch` unless `readBack == intended`); `NdefCodecV1Test.exactByteLayout/roundTrips/fitsAnNtag213`; device run: §4 row 1 | JVM-proven; device pending |
| 2 | Foreign NDEF content (incl. an old `md5_short` tag) triggers the confirmation and is not written without it; a legacy tag is recognised as such | `OverwritePolicyTest` (every non-empty/non-same payload → `Confirm`); `WriteTagActivity.confirm` writes only from the dialog's positive button; `NdefCodecV1Test.legacyRecordStillDecodes`, `ResolveTagTest.unknownV1AndLegacyAreDistinct`; device: §4 rows 2–3 | JVM-proven; device pending |
| 3 | Scanning with the app closed opens it through `NfcDispatchActivity` | manifest: two `NDEF_DISCOVERED` filters (`:tag`, `:md5_short`), no `TECH_DISCOVERED`; AAR pins `com.loosecannon.notenfc`; device: §4 row 4 | device pending |
| 4 | (optional) an old-APK tag is recognised as legacy on the device | device: §4 row 5 | optional; device pending |

## 2. What shipped (by commit)

- `fc34d58` — phase 1b plan: payload v1, resolver, reader mode, safe writer. The task plan for the whole slice.
- `123a765` — drop the 2024 legacy activities, `LegacyKey` and the tech-discovered catch-all. Clears the ground before the new codec lands.
- `27c7e91` — tag payload format v1: codec, AAR, overwrite policy. `NdefCodec` (`TagPayload` incl. `V1`/`LegacyMd5`/`Foreign`/`Malformed`/`NewerVersion`), the AAR record, `OverwritePolicy`.
- `db780dc` — link launch policy, `notenfc://tag` route, save/open link use cases. `LinkLaunchPolicy`, `TagRoute`, `SaveLink`/`OpenLink`.
- `05e8ec3` — resolve/bind/provision tag use cases + create asset. `ResolveTag`, `BindTag`, `ProvisionTag`, `CreateAsset`.
- `e70592a` — resolve tag: look the row up inside the transaction. Closes a lost-update window found in review (ruling 6, §6).
- `ebae784` — nfc adapter: ndef bridge, reader-mode session, tag writer with read-back; wire the use cases. `NfcReaderModeSession`, `NdefTagWriter`, `TagWriter.inspect`/`write`.
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

No device was attached to the build machine during Phase 1B (`adb devices` returned an empty
list throughout). Rows 1–11 of §4 are pending the owner's phone session. Everything above the
device line in §1 is JVM-proven, and the writer (`NdefTagWriter`/`TagWriter`) and dispatch
(`NfcDispatchActivity`) paths are compile-checked and code-reviewed but have not been exercised
against a real NFC radio or a real tag. No device result is claimed anywhere in this document.

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
- Review-driven hardening beyond the plan: lock-warning cancel listener, `TargetPicker` catches
  repository failures, `configChanges` on `WriteTagActivity` and `ShareLinkActivity`,
  `LinkLauncher` catches `SecurityException`, `NfcDispatchActivity` guards `payloadOf` and
  `openLink` against exceptions, `ShareLinkActivity` reads `EXTRA_TEXT` as `CharSequence`.

## 7. Deferred to Phase 1C (bind/rebind UX, Compose)

- interim `ui.interim` screens replaced by Compose routes; asset screen; revoke/re-activate;
  retiring a bound `LEGACY_MD5` row when its tag is rewritten in v1; link card on share;
  `lastOpenedAt` display; targetSdk 37 `DISPATCH_NFC_MESSAGE` (Phase 7).

### Review notes carried forward

- `LinkLaunchPolicy.extractUri` doesn't exclude curly quotes and can trim a legitimate trailing
  `)`.
- `BindTag` doesn't trim `label` while `ProvisionTag` does.
- Silent `locked = false` when `canMakeReadOnly()` is false.
- `"%02x".format` is locale-sensitive (house pattern, also in `BackupCodec`).
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
