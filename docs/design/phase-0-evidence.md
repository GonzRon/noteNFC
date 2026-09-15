# Phase 0 exit-criteria evidence

> D13 (2026-09-14, after Phase 0): the on-device old-APK tag check is now an optional sanity check, not a gate; R-1 is closed as historical.
> CI verified on GitHub 2026-09-14: run 34893509998 failed in `android-actions/setup-android@v3` (its default package list includes the removed `tools` package); fixed in 12e2c09 (`packages: platform-tools`); run 34893632165 on 12e2c09 passed every step (`:core:test :app:testDebugUnitTest :app:assembleDebug`).

Branch `phase-0-foundation`, HEAD `0cd2184` at the time this evidence was gathered
(2026-09-14). Exit criteria are D7's Phase 0 table (`docs/design/07-implementation-sequence.md`).

## Exit criteria

| # | Criterion (D7 Phase 0) | Evidence | Status |
|---|---|---|---|
| 1 | `git clone && ./gradlew :core:test :app:testDebugUnitTest` passes on a machine without Android Studio | Fresh clone of `phase-0-foundation` into a scratch directory, only `local.properties` written by hand, `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain -q && echo CLONE_BUILD_OK` printed `CLONE_BUILD_OK`. See "Clone-build proof" below. | **Pass** |
| 2 | A tag written by the shipped 1.0 APK resolves on a 1.1 build in situation A, or the legacy decode test proves the same bytes decode identically | `NdefCodecTest.decodesValidLegacyKey` / `encodeLegacyRoundTrips` and `LegacyKeyTest`'s vectors pin the decode. Argument below. | **Pass** (by equivalence proof; on-device situation-A check pending the user's phone, see Deviations) |
| 3 | `git status` clean after `assembleDebug` | Clone-check tree: `git status --short` empty apart from the ignored `local.properties`. | **Pass** |
| 4 | The keystore investigation result is recorded in D8 R-1 | R-1 investigation below. | **Pass** (investigation complete; conclusion is "situation B must be assumed") |

## CI status

The CI workflow (`.github/workflows/ci.yml`) has never actually executed — the plan forbade
pushing during this phase. It's committed and validated locally, running the identical Gradle
command line (`./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain`,
see the clone-build proof below), but it is **unverified on a GitHub runner until the first
push** — watch the first run. Its two external dependencies are the foojay JDK provisioning
(JetBrains JDK 25 daemon from `gradle/gradle-daemon-jvm.properties`, JDK 17 toolchain for `:core`)
and Android SDK component downloads via `android-actions/setup-android`.

## Clone-build proof

```
$ rm -rf .../scratchpad/clone-check
$ git clone --branch phase-0-foundation ~/Documents/Projects/AndroidStudioProjects/noteNFC-phase0 .../scratchpad/clone-check
Cloning into '.../clone-check'...
done.
$ cd .../clone-check
$ git log --oneline -1
0cd2184 spike s1: room 3 / compose / nav3 on agp 9.4, report
$ printf 'sdk.dir=~/Android/Sdk\n' > local.properties
$ ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain -q && echo CLONE_BUILD_OK
CLONE_BUILD_OK
$ git status --short
$ git check-ignore -v local.properties
.gitignore:11:local.properties	local.properties
```

`git status --short` printed nothing — `local.properties` is untracked but ignored (confirmed
with `git check-ignore -v`), so the tree is clean apart from the file the build itself requires
and that every developer machine writes locally.

**Test count.** `:core` has 3 JUnit Jupiter test classes under `core/build/test-results/test/*.xml`:

| Class | `tests` |
|---|---|
| `NdefCodecTest` | 9 |
| `LegacyKeyTest` | 6 |
| `LegacyLinkPolicyTest` | 5 |
| **Total** | **20** |

`:app:testDebugUnitTest` produced no `app/build/test-results` directory at all — `app/src` has no
test sources, so the task has nothing to run (Gradle reports it up to date / NO-SOURCE, not a
failure). Re-running `./gradlew :app:testDebugUnitTest --console=plain -q` alone exits 0 with no
output, confirming this is a no-op, not a silently-skipped suite.

`app/build/outputs/apk/debug/app-debug.apk` was produced by `:app:assembleDebug` (2,259,305 bytes).

## Legacy decode equivalence proof

The shipped 1.0 APK's `LaunchNoteNFCLinkActivity.onCreate` (from `master`,
`app/src/main/java/com/looseCannon/noteNFC/LaunchNoteNFCLinkActivity.kt`) did:

```kotlin
val customData = String(messages[0].records[0].payload)
val noteGuid = lookupNoteUrl(customData)   // SharedPreferences.getString(customData, null)
```

i.e. it took the first record's raw payload bytes, decoded them as a string with no check of
`tnf`/`type`, and used that string verbatim as the `SharedPreferences` lookup key.

The Phase 0 build's `LaunchNoteNFCLinkActivity` (same package/class, now in the `phase-0-foundation`
tree) instead calls `NdefCodec.decode(records)` and only looks up a key when the result is
`TagPayload.LegacyMd5`:

```kotlin
val noteGuid = when (val payload = NdefCodec.decode(records)) {
    is TagPayload.LegacyMd5 -> lookupNoteUrl(payload.key)
    else -> null
}
```

For every tag this app itself ever wrote, the record is `tnf = TNF_EXTERNAL_TYPE (0x04)`,
`type = "com.loosecannon.notenfc:md5_short"`, `payload = <8 lowercase hex chars>` (this is exactly
what `NdefCodec.encodeLegacy` produces, and `NdefCodecTest.encodeLegacyRoundTrips` proves the
round trip). `NdefCodec.decode` accepts that shape and returns `TagPayload.LegacyMd5(key)` with
`key` equal to `String(payload, Charsets.UTF_8)` — the same bytes the old code turned into a string
and looked up. `NdefCodecTest.decodesValidLegacyKey` pins this directly:
`NdefCodec.decode(listOf(legacy("63b37acf"))) == TagPayload.LegacyMd5("63b37acf")`.
`LegacyKeyTest`'s five vectors (`emptyString`, `abc`, `joplinExternalLink`,
`rawSharedTextWithTitleIsHashedAsAWhole`, `utf8BytesNotPlatformDefault`) plus
`alwaysEightLowercaseHexChars` pin `LegacyKey.compute` to the exact MD5-prefix values the old app
would have written into `SharedPreferences`, so the string that comes back out of `NdefCodec.decode`
is byte-for-byte what the old app wrote in and would have looked up.

**One deliberate difference.** A payload that is not 8 lowercase hex characters is now reported as
`TagPayload.Malformed` (`NdefCodecTest.uppercaseKeyIsMalformed`, `wrongLengthIsMalformed`,
`nonHexIsMalformed`) rather than being passed through to `lookupNoteUrl` as a raw string. The
user-visible outcome is unchanged either way: the old app would look up a string that, by
construction, could never have existed as a stored key (nothing this app ever wrote produces
anything but 8 lowercase hex chars as a key) and get `null`, showing "Joplin Note Link not found.";
the new app short-circuits to the same `null` → same toast. A record with a foreign `tnf`/`type`
(anything not written by this app's legacy encoder) is `TagPayload.Foreign`
(`evernoteEraTypeIsForeign`, `uriRecordIsForeign`), which also maps to `null` → the same toast — the
old code would have taken that record's payload as a string too and, not finding it in prefs,
shown the identical message.

## R-1 investigation (non-destructive)

**`adb devices -l`** — re-run today, no device attached in either toolchain copy:

```
$ /usr/sbin/adb devices -l
List of devices attached

$ ~/Android/Sdk/platform-tools/adb devices -l
List of devices attached

```

**Keystore search** (read-only):

```
$ find ~ /root -xdev \( -name '*.jks' -o -name '*.keystore' -o -name 'keystore.properties' \) \
    -not -path '*/.gradle/caches/*' -not -path '*/build/*' -not -path '*/node_modules/*' 2>/dev/null
/root/.android/debug.keystore
<an unrelated vendored Flutter checkout>/testing/android/native_activity/debug.keystore
<an unrelated project>/vertx.jks
~/.local/share/keyrings/user.keystore
```

None of these is an Android app-signing keystore for noteNFC:

- `/root/.android/debug.keystore` — this sandbox's own auto-generated debug key (see below).
- The Flutter-engine `debug.keystore` belongs to an unrelated vendored Flutter checkout
  (`native_activity` test scaffolding), not this project.
- `vertx.jks` belongs to an unrelated project (Vert.x TLS keystore, not an Android signing key).
- `~/.local/share/keyrings/user.keystore` is the GNOME/libsecret desktop keyring, not
  an Android keystore.

A grep for `fillmate` (the release cert's CN, see below) across `~/Documents` found
only an unrelated archived project outside this repository and this project's own
design docs — a lead for where the original `fillMateAndroid` keystore might live (another
machine), not evidence that it's present here.

**Certificate comparison:**

```
$ ~/Android/Sdk/build-tools/36.0.0/apksigner verify --print-certs app/release/app-release.apk
Signer #1 certificate DN: CN=fillMateAndroid, OU=dev, O=FillMate, ST=NH, C=US
Signer #1 certificate SHA-256 digest: e18854af4aeaca8ad985dfac831ee25ea9fcf3f44f0cecba2d7175fd44499a69
Signer #1 certificate SHA-1 digest: 8045441611523b65c8333d5a639cbb57da844702
Signer #1 certificate MD5 digest: 40fd330325ac3ed9d5f5c67cd5dcb3c0

$ /usr/sbin/keytool -list -v -keystore /root/.android/debug.keystore -storepass android
Alias name: androiddebugkey
Creation date: Feb 21, 2026
Owner: C=US, O=Android, CN=Android Debug
Certificate fingerprints:
  SHA1:   AC:CB:9C:F7:5C:94:A8:91:01:AE:D5:A9:E5:21:4D:5A:EB:B8:9F:1D
  SHA256: 72:71:1E:F0:EA:BA:B2:F6:C9:35:61:7B:AF:D0:E5:43:4C:3F:98:FC:A6:DA:7B:67:EE:97:14:27:C8:D6:0A:BD
```

Neither the sandbox's debug key (SHA-256 `7271...0abd`) nor its DN (`CN=Android Debug`) matches the
shipped release cert (SHA-256 `e18854af...9a69`, `CN=fillMateAndroid`).

The shipped **debug** APK, recovered from git history (not from a live install):

```
$ git show master:app/build/outputs/apk/debug/app-debug.apk > .../scratchpad/legacy-debug.apk
$ apksigner verify --print-certs .../scratchpad/legacy-debug.apk
Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
Signer #1 certificate SHA-256 digest: dfee74f5037d206044b2e975cd5c37ae147c62b3dd181d98bce29089fdbacb7d
```

That's a *different* auto-generated Android debug cert (SHA-256 `dfee74...bacb7d`) from this
sandbox's own debug key (`7271...0abd`) — expected, since Android debug keystores are generated
per-machine and aren't meant to be portable. It doesn't match the release cert either.

**Conclusion.** Situation B (reinstall) must be assumed; situation A remains possible only if the
user locates the `fillMateAndroid` keystore or the original debug keystore on another machine.

## Working-tree preservation check

The sibling checkout at `~/Documents/Projects/AndroidStudioProjects/noteNFC` (the one
the controller has been editing outside this task) is untouched by anything done for this task:

```
$ git -C ~/Documents/Projects/AndroidStudioProjects/noteNFC status --short
 M app/build.gradle.kts
 M build.gradle.kts
?? docs/
```

Same two modified Gradle files and untracked `docs/` as at the start of this session — nothing
here was read from, written to, or committed in that checkout.

## S1 verdict

Room 3.0.3 confirmed as the Phase 1A starting line — full report at
`docs/design/spikes/S1-toolchain.md`. Two points carry forward into Phase 1A: Room 3 names
transactions `withWriteTransaction {}` / `withReadTransaction {}` (not `withTransaction {}`), and
in-memory test databases must use `Room.inMemoryDatabaseBuilder<T>()`, not
`Room.databaseBuilder(name = ":memory:")`, which Room 3 rejects outright.

## Deviations from D7

- **The release APK stays tracked in git**, rather than being removed to a GitHub release as D7's
  "Source areas" row suggested. Controller ruling; `app/release/app-release.apk` remains committed.
- **No Robolectric characterisation test.** D7's "Tests" row calls for "a Robolectric test that
  `MainActivity` stores `MD5[0:8] → text`". Controller ruling: the pure `:core` extraction
  (`LegacyKeyTest`, `NdefCodecTest`) characterises that exact behaviour without needing Robolectric
  or an Android context, and does so faster and more precisely (fixed vectors, not an emulated
  `SharedPreferences`). No Robolectric dependency exists in the tree.
- **The never-referenced `Theme.EvernoteNFC` style entries were deleted**, in commit `15e4840`,
  when the unused Material dependency they existed for was dropped. Verified behaviour-neutral: a
  grep for `EvernoteNFC` across all `.xml`/`.kt`/`.gradle*` files in the current tree (excluding
  `build/`) returns no hits — nothing referenced the style.
- **The manual on-device legacy-tag check is pending the user's phone.** Exit criterion 2's
  on-device half ("resolves in situation A ... on a 1.1 build") cannot be exercised here — no
  device is attached (see R-1's `adb devices -l` above) — so this evidence relies on the equivalence
  proof instead. The device check is deferred to when the user's phone is available.
- **D3 §5's transaction wording needs updating in Phase 1A.** `docs/design/03-target-architecture.md`
  §5 currently says "one use case = one `withTransaction {}`"; S1 found Room 3 actually names these
  `withWriteTransaction {}` / `withReadTransaction {}` and has no bare `withTransaction {}`. The
  intent is unchanged, only the API name; D3 should be updated when Phase 1A starts using it.
- **Repo-wide testing rule: never expression-body a `@Test` over a value-returning assertion.**
  Three plan tests had to be converted from expression bodies (`@Test fun foo() = assertEquals(...)`)
  to block bodies (`@Test fun foo() { assertEquals(...) }`) because JUnit Jupiter silently skips a
  non-`void`/non-`Unit` `@Test` method — an expression-bodied test that returns a value is treated
  as not returning `Unit` and is skipped rather than run, with no failure reported. This is recorded
  here as a standing rule for the testing strategy going forward, not just a one-off fix.
- **CI follow-ups raised by the final reviewer, for Phase 1A:** (a) the daemon-JVM pin to
  JetBrains 25 is the likeliest CI flake, consider `toolchainVersion=17` with no vendor once the
  JDK-25 dev-box rationale is written down; (b) `on: push` + `pull_request` runs CI twice per PR
  branch push — restrict `push` to `master` later; (c) targetSdk 36 brings edge-to-edge
  enforcement to the two plain layouts — include them in the pending on-device check.

## Ready for Phase 1A: yes

All four D7 Phase 0 exit criteria have direct evidence and pass (criterion 2's on-device half is
explicitly deferred, per the deviation above, but the equivalence argument it depends on is
complete and test-backed). The R-1 investigation is complete with a definite conclusion. The
working tree outside this task's own worktree is confirmed untouched. Phase 1A can proceed on the
S1-confirmed Room 3.0.3 toolchain, with the two S1 carry-forwards (`withWriteTransaction {}` /
`withReadTransaction {}` naming, `inMemoryDatabaseBuilder<T>()`) and the D3 §5 wording update noted
above.
