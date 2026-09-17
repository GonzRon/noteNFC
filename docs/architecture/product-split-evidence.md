# Product-split evidence

Commits, builds, test runs and device observations, one section per phase. Fingerprints only —
no secrets, no physical-device ids, no owner paths.

## Phase D — ServiceTag identity conversion (§A.1)

**Commits.** Phase D runs from `1a93b55` through `4c79418` on `product-split` — twenty-four
commits, printed in order by `git log --oneline 1a93b55^..4c79418` (this fix round and the
controller's plan amendments follow that endpoint). Of those, **six are the
controller's plan work** (`1a93b55` added the 2,418-line phase plan; `d17355c`, `4bbf9b4`,
`8241d07`, `8e83a36` and `094b023` amended it as runs contradicted it) and **eighteen are
implementation**. Of those eighteen, fifteen are §A.1's task commits, two are review fix rounds
(`3dd8b33` for Task 5, `8de824e` for Task 6) and one is this regression pass.

First task commit: `8af14eb` "root project name is ServiceTag". Last identity commit before the
regression pass: `cffe8b7` "readme points at the fingerprint instead of printing it". Last commit
of the phase: `4c79418`, "phase d regression pass on the emulator"; its review fix round `a0e4580`
and the controller's plan-amendment commits follow that endpoint. The eleven commits between the `pre-split-checkpoint` tag
(`ac523d7`) and `1a93b55` are the split's archaeology and target-architecture documents, not
Phase D.

**Task 16 made no commit.** Its whole-phase verification ran against `cffe8b7` and reported from
artefacts; the controller recorded the result and amended the plan in `094b023`. So Phase D has
fifteen task commits, two fix rounds, one regression pass, six plan commits — not the
"fifteen plus two verification commits" the plan's template guessed at.

**Identity, read off the built debug APK** (`aapt2 dump badging`, build-tools 36.0.0):
`package: name='com.loosecannon.servicetag' versionCode='7' versionName='2.5'`,
`application-label:'ServiceTag'`. The two launchable activities are `MainActivity` and the
debug-only `DebugBackupActivity` (`label='ServiceTag Backup (debug)'`); the third
`uses-permission` line, `com.loosecannon.servicetag.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, is
AGP-injected and self-scoped to the new applicationId.

**Merged manifest.** One `NDEF_DISCOVERED` filter; `android:path="/com.loosecannon.servicetag:tag"`
resolved from the single Gradle-owned value; `android:scheme="servicetag"`; zero occurrences of the
retired package name. Read from
`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml` — the
`debugAndroidTest` merged manifest has no NFC filter and is the wrong file to grep (Task 16's
finding; the plan was amended for it).

**The C9 binding.** `TagIdentityBindingTest` (JVM, 4 tests / 8 assertions) and
`TagIdentityDispatchTest` (emulator, 3 tests) both green: the identity comes from one Gradle value,
the manifest carries only the placeholder, and `queryIntentActivities` for
`vnd.android.nfc://ext/com.loosecannon.servicetag:tag` resolves to exactly one activity — this
package's `NfcDispatchActivity`. The retired external type resolves to nothing of ours.

**Ambient NFC as the normal read path.** `NfcIdentityDeviceProofTest` (emulator, 4 tests). The
intent is **implicit** — action, `EXTRA_NDEF_MESSAGES` and the data URI
`vnd.android.nfc://ext/<externalType>` built from `BuildConfig.NDEF_EXTERNAL_DOMAIN` and
`BuildConfig.NDEF_TYPE_NAME`, with no component and no class — so the chain that actually ran is:

> `startActivity` → **the merged manifest's one `NDEF_DISCOVERED` filter** resolves it →
> `NfcDispatchActivity` → real `NdefCodec` → `ResolveTag` → real Room database → hand-off extras →
> `MainActivity` → `TagResultSheet`.

The filter link is therefore exercised here as well as asserted by `TagIdentityDispatchTest`. Two
limits of the model, stated plainly: the data URI is what the *filter* matches while the *records*
are what the codec judges (`NfcDispatchActivity.payloadOf` reads only `EXTRA_NDEF_MESSAGES` for
this action), so the two not-ours rows arrive **through our own filter carrying somebody else's
record** — the untrusted-input shape the trampoline is written for, not a claim that a sibling
*tag* can reach us. That a sibling tag cannot is `TagIdentityDispatchTest`'s claim.

| Test | What it proves |
|---|---|
| `ourTagOpensTheAssetItIsBoundTo` | a bound ServiceTag tag opens its asset with no chooser and no decision to make — there **is** a sheet: `TagResultSheet`'s `OpensAsset` branch names the asset and navigates in the same composition, and the nav root drops the sheet entry as it pushes the detail screen. The name alone would be satisfied by that transient sheet, so the row waits for the name **twice** (app-bar title + identity-plate model line), which only the detail screen carries |
| `ourTagWithNoRowSaysSo` | a ServiceTag tag with no row is named (`UNREGISTERED TAG` / "This ServiceTag tag is not in this phone's records."), not treated as damage |
| `aNoteTagRecordIsNotOurs` | a `com.loosecannon.notetag:tag` record is foreign **even carrying a byte-identical ServiceTag v1 body** (the body is our own `v1Record`'s payload, re-typed) — the type gate runs before any body parse (C8, invariant 1). Reason asserted: `not a ServiceTag tag: tnf=4 type=com.loosecannon.notetag:tag` |
| `ourTypeWithAShortBodyIsUnreadable` | our own type with an unparsable body is **ours**, and refused as unreadable rather than foreign. Reason asserted: `unreadable ServiceTag record: payload is 3 bytes, expected 18` |

Both not-ours rows assert the sheet's uppercased eyebrow (`NfcSheet` calls `eyebrow.uppercase()`,
G1 §1.4) and its shared sentence, because that is what the tree carries — and then the distinct
`QuietLine(result.reason)` prose, which is the only thing that tells "foreign" from "ours and
malformed" apart on screen. The JVM halves of those two claims are
`NdefEnvelopeIsolationTest.aSiblingRecordCarryingOurOwnPayloadIsStillForeign` and
`NdefEnvelopeIsolationTest.ourTypeWithASiblingBodyIsOursAndMalformed`.

**Record size (H2).** The `:tag` record is 51 B (3 + 30 + 18) and the AAR 44 B (3 + 15 + 26): a
**95 B** NDEF message, pinned byte for byte by `NdefCodecV1Test`. Capacity is compared
message-size to message-size against `Ndef.maxSize`, with no TLV framing (G1). The NTAG213
limits-test constant is `137` (`NdefCodecV1Test.NTAG213_MAX_MESSAGE_BYTES`, and
`TagWriteControllerTest`'s default `maxSize`) — **`[unobserved]` provisional seed**, to be
re-pinned from the measured `Ndef.maxSize` in §D Session 1 (H8).

**Legacy format.** `md5_short` is gone: no constant, no decoder, no manifest filter, no sheet, no
enum member (`PayloadFormat` has one member, `V1`). `git grep -i md5 -- app core gradle
settings.gradle.kts README.md` returns exactly one line — `TagResultWireTest`'s negative case
`assertNull(TagResultWire.payloadOf("LEGACY_MD5", "63b37acf"))`, which asserts that an unknown
format word resolves to nothing. The string `md5_short` itself survives only in
`docs/architecture/product-split-archaeology.md`, as history. Recorded as a **deliberate reversal
of D6's "kept permanently" promise** under O2/O3. The live database carries `nfc_tag = 0` rows
(arch §7.5, archaeology §"nfc_tag = 0"), so no row of the owner's data named it.

**Room.** Schemas re-exported under `app/schemas/com.loosecannon.servicetag.data.room.AppDatabase/`
by `git mv` plus a re-export — that is the only schema directory present. `5.json`'s
`identityHash` is `157988f1aada363f37590a6735a3be36`, **unchanged**. Every migration hop and the
full 1→5 run green. One archaeology artefact to know about: `AppDatabase.kt` is only **41 %**
similar across Task 3's package move, so at git's default 50 % rename threshold `abd0df1` shows it
as a delete plus an add (`git show --stat abd0df1`) rather than as a rename; the history is intact
and `git log --follow -M20%` walks it back through `aa6ef97` (room v5) to `3f6e2ec` (room v2).

**Launcher icon.** The owner's finished pack, staged at
`~/Documents/Projects/AndroidStudioProjects/split-assets/ServiceTag/` and copied into the module:
an ivory `#F7F5EF` ground and a text-free mark in `#1F4E78` with `#52606A` and `#FFFFFF` detail
(the Apollo Service Binder palette), adaptive foreground/background under `mipmap-anydpi-v26/`
(`ic_launcher.xml` and `ic_launcher_round.xml`), a dedicated `<monochrome>` layer for Android 13+
themed icons, and PNG rasters at five densities for pre-adaptive launchers. The inherited
`mipmap-anydpi/` XML pair and all ten `*.webp` rasters are deleted — the APK contains no `.webp`
under `ic_launcher*` (`unzip -l | grep -ci 'ic_launcher.*\.webp'` = 0) — and `<application>` now
declares `android:roundIcon` as well as `android:icon`. No placeholder: the coexistence requirement
is met with shipped artwork. Design masters (`source/`, `preview/`) stay in the staging area and
are not tracked here.

**Inventory addition found in this phase.** `ui/settings/SettingsScreen.kt:64`
`PROJECT_URL = "https://github.com/GonzRon/ServiceTag"` — an identity carrier absent from arch
§4.6 and from §A.1. It points at the post-rename name, which resolves from §B.2 onward (this code
is pushed at §B.3a, after it).

**Suites** (this pass, after `./gradlew clean`): `:core:test` **350** tests in 34 classes;
`:app:testDebugUnitTest` **223** in 34 classes; `:app:connectedDebugAndroidTest` **66** in 13
classes, on the emulator (`ANDROID_SERIAL=emulator-5554`) — 0 failures, 0 errors, 0 skipped in all
three. The instrumented total is Task 16's 62 plus this phase's four new ambient rows. **No
instrumented run touched the phone in this phase**: a physical device was attached throughout and
every `adb` call and every connected/install task was pinned to `emulator-5554`. Both APKs build;
the release APK is `app-release-unsigned.apk`, because the signing config's
`~/.config/servicetag/keystore.properties` does not exist yet — the key is generated in a later
phase and §12's signed-release proof is discharged there. The **retired 2024 signing key's
SHA-256 fingerprint is recorded once**, in `docs/design/phase-1a-evidence.md`; the README points
there rather than reprinting it, and nothing in this file reproduces it.

**After the reviews.** The Task 17 fix round (`a0e4580`) and the whole-branch review's fix round (`b4b016b`) added tests without changing any identity fact above: at `b4b016b` the suites are `:core:test` 354, `:app:testDebugUnitTest` 225 and `:app:connectedDebugAndroidTest` 66 in 13 classes, all 0 failures / 0 errors / 0 skipped, the connected suite re-run in full on the emulator at that commit.

**Clean checkout.** `git clone --no-local --branch product-split` into a directory that never held
the project, then `:core:test :app:testDebugUnitTest :app:assembleDebug` — green (Task 16, against
`cffe8b7`; the clone was deleted afterwards). One caveat worth carrying: `local.properties` is
intentionally gitignored, so a fresh clone has no SDK path and needs `ANDROID_HOME` supplied out of
band. That is not something Phase D introduced; it will reproduce for any fresh clone, and §B.6's
URL clone and second-workstation build must expect it.

**Repository-wide identity grep** (Task 16, `git grep -lIE
'com\.loosecannon\.notenfc|notenfc://|md5_short|noteNFC|NoteNfc' -- app core gradle
settings.gradle.kts README.md`). The only **text** hits are two deliberate negative cases:
`app/src/test/.../ui/backup/BackupViewModelTest.kt` (the `noteNFC-*` archive names inside
`aPreservedRetiredPrefixArchiveStillImports`, which proves the owner's pre-split export set is
still importable) and `app/src/androidTest/.../nfc/TagIdentityDispatchTest.kt:46` (the retired
external type `vnd.android.nfc://ext/com.loosecannon.notenfc:tag` inside
`theRetiredExternalTypeResolvesToNothingOfOurs`, which asserts nothing of ours still claims it,
O3). The only **binary** hit is the kept 2024 `app/release/app-release.apk`, untouched. There is
no remaining live reference to the retired identity.

**§21 regression — where every row is discharged.** §21's list, with the suite that answers it.
All of the JVM and emulator suites named below ran green in this pass; the physical rows are named
as not attempted and why.

| §21 row | Discharged by |
|---|---|
| the core maintenance product (assets, journal, measurements, profiles, templates) | `:core:test` + `:app:testDebugUnitTest` + `AssetModelDeviceProofTest`, `JournalDeviceProofTest`, `EditorsDeviceProofTest` |
| NFC asset binding, ambient resolution, standalone links | `NfcIdentityDeviceProofTest` + `TagUseCasesRoomTest` + `ResolveTagTest.boundToALinkLaunchesIt` (`:core:test`) |
| foreign / malformed safety | `NfcIdentityDeviceProofTest` + `NdefEnvelopeIsolationTest` + `NdefCodecTest` |
| intentional write, capacity, read-back | `TagWriteControllerTest` off-device with the capacity seed; the physical rows are §D Session 1 |
| attachments | `AttachmentsDeviceProofTest`, `SafTreeAttachmentStoreContractTest` |
| backup: all-or-nothing export, data-only restore, artifacts restore, set mismatch, missing/hash drift | `BackupViewModelTest`, `RestoreProofTest`, `BackupUseCasesTest`, `AttachmentsDeviceProofTest` |
| UX: Apollo theme, Dashboard, Assets, two-tab navigation, Read/inspect tag, ambient NFC as the normal read path | `NavigationSmokeTest`, `AppSmokeTest`, `ComponentsSmokeTest`, `ContrastTest`, and the ambient row by `NfcIdentityDeviceProofTest` |

**Caveat, stated once.** Everything above is **debug-build evidence**. §12's signed-release
requirement is discharged separately and only as a build-verified claim; no device row in this
file comes from a release build.

**Not attempted in this phase, and why.** Formatting, the measured `Ndef.maxSize`, capacity
refusal, read-back and lock-last need a radio and a physical NTAG213: §D Session 1's four owner
taps. Coexistence needs two installed products: §E Session 2's eight. The emulator has no NFC
radio, so the ambient path is proved with synthetic `NDEF_DISCOVERED` intents through the real
dispatch activity, codec and database (G4's reassignment).

## Phase E — NoteTag reconstruction (§A.2)

**Commits.** Phase E is the whole of the NoteTag repository's own work: `c84b881..9ff1d65` on `master`
— **17 commits**, printed in order by `git log --oneline c84b881..9ff1d65`, **0 merges**
(`git rev-list --merges --count HEAD` = 0). The ancestry is the original product's, not a fresh
history: the root commit is `5fb6aed` "working!" and `git rev-list --count HEAD` is **47**, so the
30 commits before `c84b881` are the noteNFC line, untouched. First commit of the phase: `8865990`
"start the narrow product from its own history"; last: `9ff1d65` "review round: an unverified format
is not a write, and the claims match the code". The tree was **45 files** after that first commit
(the deletion of the sibling's documents and the 2024 APK) and is **85** now
(`git ls-files | wc -l`). This repository has no remote yet and nothing is pushed in this phase.

**After the whole-branch review.** The reconstruction was reviewed as a whole once it was finished,
and the seven things it found that mattered are fixed in one commit, `9ff1d65` — the last of the
phase, and the tree every number below was measured at. (1) An **unverified `Written` is no longer a
write**: the interim adapter's `NdefFormatable` path returns `Written(verified = false)` after a
format, and the controller used to confirm the store row, report "Written" and set `done`, which
dropped the very next tap — the one that measures, capacity-checks, writes and verifies; it now says
"Formatted the tag. Hold it to the phone again to finish writing the link.", confirms nothing, keeps
a `LOCAL_REF` mapping persisted-unconfirmed exactly as for any other ambiguous outcome, and leaves
the task open. (2) The writer's read failure says **one fixed NoteTag sentence** — "Could not read
the tag. Hold it still and try again." — instead of leaking a platform exception's message, with the
single-flight guard proved released by the tap that follows. (3) `WritePlanner` **refuses at plan
time** a scheme that is neither blocked nor on the allowlist (`WritePlan.Refused("NoteTag does not
open <scheme> links.")`), because `ResolveTap` answers such a link with a sentence and would never
open the tag it had just written. (4) A tag list this phone **cannot read** is no longer reported as
"written on another phone" — a false statement about the tag — but as "This phone's tag list could
not be read.", with `CancellationException` rethrown. (5) The manifest names its three components
**namespace-relatively** (`.NoteTagApp`, `.MainActivity`, `.nfc.NfcDispatchActivity`) and
`MainActivity.EXTRA_MESSAGE` is built from `BuildConfig.APPLICATION_ID`, so the binding test's claim
of no second copy of the identity *including in the manifest* is now literally true and asserted
(`theManifestNamesNoComponentByItsPackageRoot`). (6) `tags.json` is **excluded from Android's cloud
backup and from device-to-device transfer** by `res/xml/data_extraction_rules.xml`, because
`allowBackup="false"` alone would have let a transfer carry the local map to a new phone and
contradict what the README says a `LOCAL_REF` tag is. (7) `FsyncRename` **fsyncs the containing
directory** after the atomic rename (target §4.9, amended in the same round): without it the rename
itself can be lost to a power cut although the bytes were durable. Six smaller items travelled with
them: the toast removed from the copied `LinkLauncher` (NoteTag speaks on the card, not in a toast),
"49 bytes as an NDEF message" in the README, no `!!` on `writtenAt` in the list row,
`CancellationException` rethrown before the pre-write persist's catch-all, a `values-night`
platform theme so a dark cold start does not flash the light ground, and the two list chips made
non-interactive labels so a screen reader does not announce them as buttons — the words unchanged.

The scoped re-review of that round found every item addressed and one residual worth recording:
on the interim adapter's `NdefFormatable` path the format tap persists a `LOCAL_REF` row that the
second tap never confirms or forgets, because that tap plans a fresh uuid. The row is unconfirmed,
so it is invisible in the list and irrelevant to resolution; it is parked to Phase G, which
replaces that adapter (ledger ruling R1).

**Identity, read off the built debug APK** (`aapt2 dump badging`, build-tools 36.0.0):
`package: name='com.loosecannon.notetag' versionCode='3' versionName='2.0'`,
`application-label:'NoteTag'`, and **one** launchable activity,
`com.loosecannon.notetag.MainActivity`. The version numbers are past the 2024 app's 2 / "1.1", so
an installed build updates rather than collides.

**Merged manifest.** Read from
`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`:
exactly **one** `NDEF_DISCOVERED` filter, with `android:path="/com.loosecannon.notetag:tag"`
resolved from the single Gradle-owned value; **no** `TECH_DISCOVERED` filter and no
`nfc_tech_filter` resource; **no** `android:scheme="notetag"` anywhere (ratified P4 — the scheme is
reserved for #6/#36 and declares nothing); zero occurrences of the retired identity. The only
extra `uses-permission` is AGP's `com.loosecannon.notetag.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`,
self-scoped to this applicationId.

**The C9 binding.** `TagIdentityBindingTest` (JVM, 6 tests) and `TagIdentityDispatchTest` (emulator,
4 tests) both green. The identity is typed once in `app/build.gradle.kts`, the manifest carries only
the placeholder, and `BuildConfig.NDEF_AAR_PACKAGE` is **null** — NoteTag writes no Application
Record (O13), asserted in the binding test and in the build script's own text.
`queryIntentActivities` for `vnd.android.nfc://ext/com.loosecannon.notetag:tag` resolves to exactly
one activity, this package's `NfcDispatchActivity`
(`ourExternalTypeResolvesToOurDispatchActivity`); the retired
`com.loosecannon.notenfc:md5_short` and the sibling `com.loosecannon.servicetag:tag` resolve to
nothing of ours (`theRetiredExternalTypeResolvesToNothingOfOurs`,
`theSiblingExternalTypeResolvesToNothingOfOurs`), and `notetag://` resolves to nothing of ours
either (`thereIsNoNoteTagUrlSchemeToResolve`).

**Ambient NFC as the normal read path.** `AmbientDispatchDeviceProofTest` (emulator, **6 tests**).
The intent is **implicit** — the action, `EXTRA_NDEF_MESSAGES` and the data URI
`vnd.android.nfc://ext/<externalType>` built from `BuildConfig`, with no component and no class —
so the chain that actually ran is:

> `startActivity` → **the merged manifest's one `NDEF_DISCOVERED` filter** resolves it →
> `NfcDispatchActivity` → real `NoteTagCodec` → `ResolveTap` → real `JsonFileTagStore` →
> `MainActivity`'s result card.

The six rows, each asserting one sentence verbatim: a `JOPLIN_NOTE` tap reaches the launcher and is
told no app can open `joplin://x-callback-url/openNote?id=…`, with the id in the 32 lower-case hex
the codec re-renders; a sibling record carrying a plausible 18-byte ServiceTag body is answered
"This tag belongs to ServiceTag, not NoteTag."; a `LOCAL_REF` with no mapping on a fresh install is
answered "This tag was written on another phone, so this phone cannot open it."; a `javascript:`
link is refused by `LinkLaunchPolicy` before the launcher, with an `ActivityMonitor` proving **no**
`ACTION_VIEW` left the process (and the same monitor then counting a probe `ACTION_VIEW`, so the
zero is a refusal and not a blind spot); our own type with a one-byte body is "This NoteTag tag is
unreadable (payload is 1 bytes, header needs 3)."; and an intent through our own filter carrying
**no** `EXTRA_NDEF_MESSAGES` is "Nothing to resolve." — the trampoline's null-records path, added
with `26baa46`, the same commit that makes the outcome guard rethrow `CancellationException` so a
cancelled tap stays cancelled instead of being reported as an unreadable tag.

**Record size.** A `JOPLIN_NOTE` message is **49 B** — 3 B of record framing, the 27-byte external
type `com.loosecannon.notetag:tag`, and a 19-byte body (`version | kind | flags` + the note's 16 id
bytes) — pinned in `:core` by `NdefSizeTest` and `NoteTagCodecTest.aJoplinNoteRoundTripsAsOneRecordOf49Bytes`.
`NdefSizeDeviceTest` (emulator, 4 tests) is the pin that matters: `NdefSize.serialisedSize` is
compared against `NdefMessage.toByteArray().size` for the 49 B message, for a 255-byte payload and
for a 300-byte payload — across the short-record boundary where the length field grows from one
byte to four — and for a two-record message. Green on the emulator, so the planner's off-device
arithmetic is the platform's. Capacity is message bytes against the tag's measured `Ndef.maxSize` —
**for the `Ndef` path**: no TLV allowance, no character count. On the interim `NdefFormatable` path
there is **no measured `maxSize` and so no comparison at all**: the planner treats that first tap as
unmeasured and the writer's own pre-write check is the only refusal, until Phase F/G's
`format(null)` two-step measures the tag before writing it. The controller now treats that path's
unverified result as "hold it again", never as a write.

**The store and the LOCAL_REF invariant.** `JsonFileTagStore` is one JSON file rewritten whole
behind a `Mutex` and replaced atomically (temp file → `fsync` → `ATOMIC_MOVE` rename → `fsync` of
the **containing directory**, so the rename has its own durability barrier, with the temp file
deleted if any step fails and the directory sync wrapped because some filesystems refuse to open a
directory as a channel); P19 holds — no Room. The file is `tags.json` in `filesDir`, and it is
**excluded from Android's cloud backup and from device-to-device transfer** by
`res/xml/data_extraction_rules.xml`: `allowBackup="false"` governs only the cloud on targetSdk 36,
and a transfer that carried the map to a new phone would contradict the README's statement that a
`LOCAL_REF` tag does not survive a move. `TagEntry.writtenAt` is nullable and
carries the invariant: a `LOCAL_REF` mapping is persisted **before** the write, so `get` resolves a
retained entry (a live tag may exist), while `list` — the write history the UI shows — returns only
confirmed entries. **Retained is not written.** The three failure injections, in
`NoteTagWriteControllerTest` (JVM): `aFailedWriteRetainsTheLocalRefMappingUnconfirmed` (the write
fails after the mapping was persisted: the entry stays, `writtenAt` stays null, and it is absent
from the history), `aStoreThatCannotKeepTheMappingWritesNothingToTheTag` (the store refuses first:
0 write attempts, nothing on the tag, no entry) and
`aTooSmallTagRemovesTheMappingNoBytesCanHaveReachedIt` (the writer's own pre-write capacity check
refuses: the mapping is removed, because no bytes can have reached the tag). Portable kinds —
`JOPLIN_NOTE` and `URI` — never require the store at all.

**Sibling isolation, both ways.** ServiceTag's direction is `NdefEnvelopeIsolationTest`
(`core/src/test/kotlin/com/loosecannon/servicetag/core/nfc/NdefEnvelopeIsolationTest.kt`, Phase D).
NoteTag's direction is `NoteTagCodecTest.aServiceTagRecordIsForeignEvenWithAPlausibleBody`: a
`com.loosecannon.servicetag:tag` record whose body would parse perfectly as a ServiceTag body is
`Foreign("tnf=4 type=com.loosecannon.servicetag:tag")` **before any body parse happens**, and the
description names the type rather than guessing at damage. On the device the same claim is
`AmbientDispatchDeviceProofTest.aServiceTagRecordIsNamedAsServiceTags`, whose sentence is "This tag
belongs to ServiceTag, not NoteTag." (§23: named, not called damage), with
`TagIdentityDispatchTest.theSiblingExternalTypeResolvesToNothingOfOurs` proving a sibling *tag*
never reaches us through the filter in the first place.

**Suites** (at `9ff1d65`, after `./gradlew clean`): `:core:test` **78** tests in 9 classes —
`LinkLaunchPolicyTest` 11, `NdefEnvelopeTest` 7, `NdefSizeTest` 5, `OverwriteWordingTest` 9,
`ResolveTapTest` 14, `JsonFileTagStoreTest` 12, `JoplinIdTest` 4, `NoteTagCodecTest` 10,
`WritePlannerTest` 6; `:app:testDebugUnitTest` **26** in 3 classes — `TagIdentityBindingTest` 6,
`MainViewModelTest` 4, `NoteTagWriteControllerTest` 16; `:app:connectedDebugAndroidTest` **19** in
5 classes on the emulator (`ANDROID_SERIAL=emulator-5554`) — `AmbientDispatchDeviceProofTest` 6,
`NdefSizeDeviceTest` 4, `TagIdentityDispatchTest` 4, `AppSmokeTest` 3,
`WriteScreenDeviceBoundTest` 2. **0 failures, 0 errors, 0 skipped** in all three. The same command
line also built `:app:assembleDebug`, `:app:assembleRelease` and `:app:compileDebugAndroidTestKotlin`.
**No instrumented run touched the phone in this phase**: every connected task was pinned to
`emulator-5554`.

**Clean checkout.** `git clone --no-local` from the repository into a scratch directory that never
held the project; the clone's `git rev-parse --short HEAD` is **`9ff1d65`**, the same commit every
number above was measured at. Then, with the Gradle build cache disabled so the suites really ran
there, `:core:test :app:testDebugUnitTest :app:assembleDebug` — 78 and 26 tests green in the clone
and `app-debug.apk` produced. The clone was deleted afterwards. The Phase D caveat reproduces:
`local.properties` is gitignored, so a fresh clone needs `ANDROID_HOME` supplied out of band.

**Signing.** `:app:assembleRelease` produces `app-release.apk` — **signed**, not
`app-release-unsigned.apk` — because the signing config's `~/.config/notenfc/keystore.properties`
exists on this workstation with all four values. Release signed with the existing key —
fingerprint **matches** the record in `docs/design/phase-1a-evidence.md`, compared without
printing, not reproduced. The comparison wrote both digests to files, normalised them and returned
one word; neither digest was printed, echoed or passed as an argument, and the NoteTag README points
at that record rather than reprinting it.

**The look.** The owner's blue palette, read off the finished icon pack and approved 2026-09-17
(derived from Joplin's palette, not a copy of it). Seven tokens come off the pack — **Frost**
`#F4F7FB`, **Paper** `#FFFFFF`, **Azure** `#1F5FA8`, **Navy** `#0B3A6E`, **Slate** `#2B3038`,
**Mist** `#8A96A6` and **Sky** `#5DA6F5` — with four derived from them for contrast (`Steel`,
`SkyTint`, `Ink`, `Haze`). Both Material 3 schemes, **light and dark**, are built from those
tokens, with no dynamic colour; `res/values/colors.xml` names six of them for the platform side
(the window background). The launcher mark itself appears as a **watermark**: the adaptive icon's
foreground layer, at 7 % alpha, bottom-end aligned and clipped behind both screens, decorative
only. The first attempt (`5687fae`, ivory/amber/umber) was replaced by the owner's revision
(`9e30bb5`) in the same task; no screen and no sentence changed in either.

**Caveat, stated once.** Everything above is **debug-build and emulator evidence** — the only
release-build claim in this section is the signing one, and it is a build-and-verify claim, not a
device row. **No physical tag was touched**: the emulator has no NFC radio, so every tap is a
synthetic `NDEF_DISCOVERED` intent through the real filter, activity, codec, store and screens.
The NDEF adapter under the codec (`NdefBridge`, `TagWriter`, including `NdefFormatable.format(message)`
at `TagWriter.kt:98`) is an **interim copy of ServiceTag's**, and that shape is superseded by
`nfc-tag-core` in Phase F/G. The two screens' visual check — palette, watermark, light and dark —
was made by screenshot during Task 9b; the screenshots were deleted and are not evidence here.

**Not attempted in this phase, and why.** Adopting `nfc-tag-core` is §A.2 row 10 and belongs to
**Phase G**, so the format, the codec and the NDEF adapter live in this repository for now. CI on a
runner is **§B.4** — there is no remote yet, and the same Gradle line that CI will run gated every
task locally. The physical session is still owed: a real tap, the measured `Ndef.maxSize`, a
capacity refusal, a read-back and a lock all need a radio and an NTAG213.

**Phase E local reconstruction complete; Gate 6 pending its deferred prerequisites (row 10 /
Phase G, §B.4 CI, the physical session).**
