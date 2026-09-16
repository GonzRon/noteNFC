# Product-split evidence

Commits, builds, test runs and device observations, one section per phase. Fingerprints only —
no secrets, no device ids, no owner paths.

## Phase D — ServiceTag identity conversion (§A.1)

**Commits.** Phase D runs from `1a93b55` through this commit on `product-split` — twenty-four
commits, printed in order by `git log --oneline 1a93b55^..HEAD`. Of those, **six are the
controller's plan work** (`1a93b55` added the 2,418-line phase plan; `d17355c`, `4bbf9b4`,
`8241d07`, `8e83a36` and `094b023` amended it as runs contradicted it) and **eighteen are
implementation**. Of those eighteen, fifteen are §A.1's task commits, two are review fix rounds
(`3dd8b33` for Task 5, `8de824e` for Task 6) and one is this regression pass.

First task commit: `8af14eb` "root project name is ServiceTag". Last identity commit before the
regression pass: `cffe8b7` "readme points at the fingerprint instead of printing it". Last commit
of the phase: this one, "phase d regression pass on the emulator" — a commit cannot carry its own
hash, so it is named by subject. The eleven commits between the `pre-split-checkpoint` tag
(`ac523d7`) and `1a93b55` are the split's archaeology and target-architecture documents, not
Phase D.

**Task 16 made no commit.** Its whole-phase verification ran against `cffe8b7` and reported from
artefacts; the controller recorded the result and amended the plan in `094b023`. So Phase D has
fifteen task commits, two fix rounds, one regression pass, six plan commits — not the
"fifteen plus two verification commits" the plan's template guessed at.

**Identity, read off the built debug APK** (`aapt2 dump badging`, build-tools 35.0.0):
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

**Ambient NFC as the normal read path.** `NfcIdentityDeviceProofTest` (emulator, 4 tests) drives
the whole ambient chain — synthetic `NDEF_DISCOVERED` intent → `NfcDispatchActivity` → real
`NdefCodec` → `ResolveTag` → real Room database → `MainActivity`'s `TagResultSheet`:

| Test | What it proves |
|---|---|
| `ourTagOpensTheAssetItIsBoundTo` | a bound ServiceTag tag opens its asset, no chooser, no sheet |
| `ourTagWithNoRowSaysSo` | a ServiceTag tag with no row is named, not treated as damage |
| `aNoteTagRecordIsNotOurs` | a `com.loosecannon.notetag:tag` record is foreign **even carrying a valid v1 body** — the type gate runs before any body parse (C8, invariant 1) |
| `ourTypeWithAShortBodyIsUnreadable` | our own type with an unparsable body is ours, and refused |

The two not-ours rows assert the sheet's uppercased eyebrow (`NfcSheet` calls
`eyebrow.uppercase()`, G1 §1.4) plus its sentence, because that is what the tree carries.

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

**Caveat, stated once.** Everything above is **debug-build evidence**. §12's signed-release
requirement is discharged separately and only as a build-verified claim; no device row in this
file comes from a release build.

**Not attempted in this phase, and why.** Formatting, the measured `Ndef.maxSize`, capacity
refusal, read-back and lock-last need a radio and a physical NTAG213: §D Session 1's four owner
taps. Coexistence needs two installed products: §E Session 2's eight. The emulator has no NFC
radio, so the ambient path is proved with synthetic `NDEF_DISCOVERED` intents through the real
dispatch activity, codec and database (G4's reassignment).
