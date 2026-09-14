# D1 — Current-state archaeology

Status: design-phase document, 2026-09-14. Nothing in the repository was modified during this
audit (read-only gate). Every consequential claim carries an evidence label:

- **CURRENT-VERIFIED** — demonstrated from the working tree, HEAD, or the committed APKs today
- **HISTORICAL-VERIFIED** — demonstrated from git objects for an earlier commit
- **INFERRED** — strongly supported by evidence plus Android platform semantics, not executed
- **UNKNOWN** — cannot be established from the repository

Line references are to HEAD (`8fa5496`).

---

## 1. What the application is today

A three-activity Kotlin app, ~250 lines of Kotlin, that maps a short hash on an NFC tag to a
Joplin note link kept in SharedPreferences. It has no database, no tests, no CI, no license, and
no documentation beyond a 21-line README. CURRENT-VERIFIED.

| Fact | Value | Evidence |
|---|---|---|
| applicationId = namespace | `com.looseCannon.noteNFC` (mixed case) | `app/build.gradle.kts:6,10` |
| minSdk / targetSdk / compileSdk | 26 / 33 / 34 | `app/build.gradle.kts:7,11,12` |
| versionCode / versionName | 1 / "1.0" (never bumped) | `app/build.gradle.kts:13-14` |
| JVM target | 1.8 | `app/build.gradle.kts:29-35` |
| UI toolkit | XML Views; two `LinearLayout` layouts with one `TextView` each; activities extend `android.app.Activity`, not AppCompat | `res/layout/*.xml`, `mainActivity.kt:10` |
| Declared but unused dependencies | appcompat, material, constraintlayout (no AppCompat activity, no ConstraintLayout, the `Theme.EvernoteNFC` style is never referenced by the manifest) | `app/build.gradle.kts:41-44`, `AndroidManifest.xml:9-11` |
| Persistence | one SharedPreferences file `noteNFCURLs` | `mainActivity.kt:14`, `LaunchNoteNFCLinkActivity.kt:47` |
| Tests | none; `app/src/test` and `app/src/androidTest` do not exist and are gitignored | `.gitignore:20-21`, `app/.gitignore:6` |

## 2. Actual data flow

```
Joplin ─share "Copy external link"─▶ ACTION_SEND text/plain
        │
        ▼
MainActivity.onCreate                                   mainActivity.kt:17-31
  transformLink(text)  = text if text.contains("joplin") else null      :40-45
  id = MD5(text)  → lowercase hex → first 8 chars (32 bits)             :34-38
  prefs["noteNFCURLs"][id] = text            (overwrites silently)       :26
  startActivity(NFCHandlerActivity, extra uniqueId=id); finish()        :28-31
        │
        ▼
NFCHandlerActivity (write screen)                       NFCHandlerActivity.kt
  enableForegroundDispatch(MUTABLE PendingIntent, no filters, no tech lists)   :43-47,60
  onNewIntent → writeLinkToTag(tag):
     NdefRecord.createExternal("com.looseCannon.noteNFC", "md5_short", id.utf8)  :117-120
     Ndef.writeNdefMessage or NdefFormatable.format; toast; finish()             :123-151
        │
        ▼  (physical tag now holds a single external record, ~8-byte payload)
        │
LaunchNoteNFCLinkActivity (scan)                        LaunchNoteNFCLinkActivity.kt
  intent-filter NDEF_DISCOVERED  vnd.android.nfc://ext/com.loosecannon.notenfc:md5_short
  id = String(messages[0].records[0].payload)                                    :31
  url = prefs["noteNFCURLs"][id]                                                 :46-49
  startActivity(ACTION_VIEW, Uri.parse(url))   — uncaught if no handler          :35-36
  finish()
```

Observations that matter for the redesign, all CURRENT-VERIFIED unless labelled:

1. **The tag is identity-only already.** The payload is the 8-hex key; the mutable link lives on
   the phone. The new design keeps this principle and changes only the key format and the store.
2. **The key is deterministic**: `MD5(stored text)[0:8]`. The same Joplin link always yields the
   same key (`661f371`, 2023-08-07, "to avoid duplicate UUIDs pointing to the same evernote
   link"). Consequence: a lost mapping can be **rebuilt** by re-sharing the same link. This is the
   single most useful property for legacy recovery (D6).
3. **Silent collision**: `putString(id, link)` overwrites any existing entry with the same 32-bit
   prefix. Probability is ~n²/2³³ (≈1.2×10⁻⁴ for 1 000 links), but there is no detection; an
   older tag would silently open the newer note. INFERRED from `mainActivity.kt:26`.
4. **The mapping is written before the tag is**: cancelling the write screen leaves an orphan
   entry. Harmless, but the prefs file may hold keys never written to any tag.
5. **`transformLink` is a substring gate, not a parser.** Any shared text containing "joplin" is
   stored verbatim and later passed to `ACTION_VIEW`. If the share sheet sends title + link, the
   stored value is not a URI and the scan crashes on `startActivity` (no `try/catch`,
   `LaunchNoteNFCLinkActivity.kt:35-36`; INFERRED crash mode `ActivityNotFoundException`).
6. **Charset**: payload written as UTF-8, read with `String(ByteArray)` (platform default,
   UTF-8 on Android). Consistent for the ASCII hex keys in use.
7. **Record type on the wire is lower-case** `com.loosecannon.notenfc:md5_short`: the framework
   lower-cases domain and type in `createExternal` (INFERRED, framework semantics), which is why
   the manifest `pathPrefix` is lower-case and why the `65eb3f1`–`b545e7f` mixed-case filter
   plausibly never matched (HISTORICAL-VERIFIED filter text; INFERRED consequence).
8. **`TECH_DISCOVERED` fallback is a dead end.** The manifest also claims every `NfcA`/`Ndef`
   tag at tech level (`AndroidManifest.xml:45-52`), but the activity only acts on
   `ACTION_NDEF_DISCOVERED` and otherwise finishes silently. Net effect: noteNFC appears in the
   chooser for unrelated tags and then does nothing.
9. **Foreground dispatch writes to any tag presented**, including one that already holds another
   noteNFC record or foreign content, without confirmation. The `PendingIntent` is `FLAG_MUTABLE`
   with an explicit component; the earlier bug where extras were baked into the PendingIntent was
   fixed in `50e7018` by keeping the id in an activity field (HISTORICAL-VERIFIED).
10. **Ordering latent bug**: `NFCHandlerActivity.onCreate` checks `isNFCIntent(intent)` before
    `currentUniqueId` is assigned (`:29-33`). Unreachable today (the activity is not exported and
    has no NFC filter) but would misfire if either changed.
11. **Resource leak**: when `Ndef.isWritable` is false the connection is never closed before
    falling through (`:124-132`). Cosmetic.
12. **Launcher launch** shows the static instruction screen and never finishes; sharing is the only
    real entry point.

## 3. Persistence

- File: `noteNFCURLs` (`/data/data/com.looseCannon.noteNFC/shared_prefs/noteNFCURLs.xml`, INFERRED
  standard layout). Key = 8 lowercase hex chars; value = the shared text. No metadata (no title,
  no timestamp), no listing UI, no deletion, no export. CURRENT-VERIFIED.
- History of the file (HISTORICAL-VERIFIED, see the commit table below): the Evernote-era names
  (`EvernoteURLs`, `EvernotePrefs`) only ever existed under the *other* applicationId
  `com.looseCannon.evernotenfc` (renamed in `65eb3f1`, 2023-08-09, before any release APK). Inside
  a `com.looseCannon.noteNFC` install the mapping file has always been `noteNFCURLs`. The only
  possible stale content is an `EvernoteUserID` in `noteNFCPrefs` and `evernote:///view/…` values
  from debug builds in the `65eb3f1`–`b545e7f` window; neither is read today. INFERRED.
- `android:allowBackup` is not declared → defaults to `true`, so Android Auto Backup **may** be
  carrying the prefs file to the user's Google account. Whether backup is enabled on the device is
  UNKNOWN; restore additionally requires a matching signing certificate (INFERRED platform
  semantics), see §6.

## 4. NFC encoding and resolution

| Aspect | Current | Evidence |
|---|---|---|
| Record | `TNF_EXTERNAL_TYPE`, type `com.loosecannon.notenfc:md5_short`, payload = 8 ASCII hex chars | `NFCHandlerActivity.kt:117-121` |
| Message | single record; no Android Application Record (AAR) | `:121` |
| Write path | `Ndef.writeNdefMessage`, fallback `NdefFormatable.format`; no capacity check, no read-back, no lock | `:123-151` |
| Read dispatch | `NDEF_DISCOVERED` filter on the ext type + `TECH_DISCOVERED` (NfcA, Ndef) | `AndroidManifest.xml:40-53`, `res/xml/nfc_tech_filter.xml` |
| Read parsing | first record of first message, no TNF/type check | `LaunchNoteNFCLinkActivity.kt:31` |
| Historic types | `com.loosecannon.evernotenfc:uuid8_link` (`5fb6aed`), `…evernotenfc:md5_short` (`661f371`), `…notenfc:md5_short` (`65eb3f1`→HEAD) | HISTORICAL-VERIFIED |

Tags written by the Evernote-era package (`…evernotenfc:*`) are unresolvable by the current app
and would arrive via `TECH_DISCOVERED` and be dropped silently. Whether any such tags exist is
UNKNOWN.

## 5. Android technology level and toolchain

| Item | Committed (HEAD) | Working tree (uncommitted) | Last known-good build of the shipped APKs |
|---|---|---|---|
| AGP | 8.7.1 | **9.4.0** | 8.7.1 |
| Kotlin Gradle plugin | 1.8.0 (explicit) | removed (AGP 9 built-in Kotlin) | 1.8.0 |
| Gradle | not tracked | wrapper 9.7.1 (JVM toolchain: JetBrains JDK 25 via foojay) | 8.9 |
| `kotlinOptions` → `kotlin { compilerOptions {} }` | old DSL | new DSL inside `android {}` | old DSL |
| Manifest `package` attribute | present alongside `namespace` | same | same |

CURRENT-VERIFIED from `git diff`, `gradle/wrapper/gradle-wrapper.properties`,
`gradle/gradle-daemon-jvm.properties`, and APK metadata
(`META-INF/com/android/build/gradle/app-metadata.properties`, `kotlin-tooling-metadata.json`).

- The uncommitted diff has exactly the shape of Android Studio's AGP Upgrade Assistant migration
  to AGP 9 (drop standalone KGP, migrate `kotlinOptions`). All ignored scaffolding (`.idea/`,
  wrapper, daemon JVM file, `local.properties` header dated 2026-09-13) was generated the same
  day. INFERRED: IDE-driven, not hand-written.
- AGP 9.4.0 **is** resolved in the user's Gradle cache (`~/.gradle/caches/modules-2/files-2.1/com.android.tools.build/gradle/9.4.0`),
  alongside Kotlin Gradle plugin 2.4.20, so the IDE has synced this configuration at least once.
  CURRENT-VERIFIED (cache listing). Whether `compileDebugKotlin` succeeds is UNKNOWN (this
  session's offline attempt used a different Gradle home and could not resolve the plugin).
- **The repository is not buildable from a fresh clone**: `settings.gradle.kts`, `gradlew`,
  `gradle/`, `gradle.properties`, and both test directories are gitignored (`.gitignore:16-21`,
  `app/.gitignore`). A stray "```" line sits in `.gitignore:22`. Meanwhile a build output
  (`app/build/outputs/apk/debug/app-debug.apk`) **is** tracked. CURRENT-VERIFIED from
  `git ls-files`.
- Local SDK has platforms `android-34` and `android-37`, build-tools 34/36. Compose, Room, and
  WorkManager artifacts are not yet in the Gradle cache (fresh downloads will be needed).

## 6. The shipped binaries and the upgrade constraint

`app/release/app-release.apk` (committed in `d1d7df7`, 2024-10-27; 4.7 MB, unminified, single
dex) and `app/build/outputs/apk/debug/app-debug.apk` are both `versionCode 1`, package
`com.looseCannon.noteNFC`, min 26 / target 33 / compile 34, AGP 8.7.1. CURRENT-VERIFIED via
`aapt`, `apksigner`, `apkanalyzer`.

- Release is signed **v2-only** with certificate `CN=fillMateAndroid, OU=dev, O=FillMate, ST=NH,
  C=US` (SHA-256 `e18854af…9a69`). No keystore, `signingConfig`, or `keystore.properties` exists
  anywhere in the repository. The debug APK is signed with a debug key; `~/.android/debug.keystore`
  does not exist on this machine. CURRENT-VERIFIED.
- Consequence (INFERRED, platform semantics): a rebuild here produces a different signature.
  Android refuses to update an installed package with a different signing certificate, so the
  first install of the new app on the current phone is an **uninstall + reinstall**, which deletes
  `noteNFCURLs`. Auto Backup restore also requires a matching certificate. Unless you still hold
  the original keystore (**OPERATOR RULING REQUIRED**), the legacy plan must not depend on reading
  the prefs file in place; it must depend on the deterministic key derivation (§2 item 2) and on a
  "re-link legacy tag" flow (D6).
- Which of the two APKs is on the phone is UNKNOWN.

## 7. Git history as design intent (15 commits, single author)

| Commit | Date | Intent | Effect on the redesign |
|---|---|---|---|
| `5fb6aed` | 2023-08-05 | Initial working app; key = first 8 chars of a random UUID; type `uuid8_link` | Shows the key was never meant to carry meaning |
| `745590c` | 2023-08-07 | `finish()` after launching the write screen so the user returns to the note app | Preserve: the share→write flow should return to the caller |
| `661f371` | 2023-08-07 | Random key → `MD5(link)[0:8]` to avoid two keys for one link | Deterministic legacy key; recoverable (D6) |
| `783a052` | 2023-08-07 | Rewrite `evernote://` links to dodge a tracker call blocked by DNS filtering | Historical; the author runs DNS-level blocking — external links must fail gracefully |
| `50e7018` | 2023-08-08 | Fix: id baked into a cached mutable PendingIntent reused the first note's id for every later write | Use reader mode / explicit state, never data in a cached PendingIntent |
| `65eb3f1` | 2023-08-09 | Rename package to `com.looseCannon.noteNFC`; prefs → `noteNFCURLs` | applicationId is fixed forever from here |
| `d1d7df7` | 2024-10-27 | Remove Evernote; accept anything containing "joplin"; lower-case the ext filter; add `TECH_DISCOVERED`; commit release APK | Current behaviour; the tech filter is a workaround, not a feature |

HISTORICAL-VERIFIED (commit contents), intent INFERRED from messages and diffs.

## 8. Technical debt register

| # | Debt | Severity | Disposition in the plan |
|---|---|---|---|
| T1 | Not clone-buildable; wrapper/settings/tests gitignored; build output tracked | High | Phase 0 |
| T2 | Release signing key absent; in-place update impossible without it | High | Ruling + D6 recovery path |
| T3 | SharedPreferences as the only store; no listing, deletion, export | High | Phase 1 (Room + migration + backup) |
| T4 | 32-bit deterministic key with silent overwrite on collision | Medium | Phase 1: new UUID payload; legacy keys become read-only aliases |
| T5 | Uncaught `startActivity` on scan; no URI validation; substring gate | Medium | Phase 1: `LinkLaunchPolicy` with allowlist + handler check |
| T6 | Over-broad `TECH_DISCOVERED` claim with silent finish | Low | Phase 1: remove; handle unknown tags explicitly |
| T7 | Foreground dispatch with mutable PendingIntent; writes without confirmation | Medium | Phase 1: reader mode; confirm overwrite; read-back |
| T8 | targetSdk 33; AGP 9 bump unverified; JVM 1.8 target; unused deps; unused theme; `package` attribute in manifest | Medium | Phase 0 |
| T9 | No tests, no CI | High | Phase 0 (harness) then every phase |
| T10 | No Evernote-era tag support | Low | Out of scope unless you report such tags exist |

## 9. Compatibility constraints the new design must honour

1. `applicationId` stays `com.looseCannon.noteNFC` (mixed case is legal and irrelevant to the DNS-style NFC domain, which is lower-cased on the wire).
2. The `NDEF_DISCOVERED` filter for `vnd.android.nfc://ext/com.loosecannon.notenfc:md5_short` must remain, and its payload must be resolved through a legacy alias table.
3. The legacy key function (`MD5(text)` → hex → `[0:8]`) must be preserved verbatim in the new code, behind a test that pins a known vector, so re-shared links reproduce old keys.
4. The share-sheet entry point (`ACTION_SEND text/plain`) must keep working with the same return-to-caller behaviour.
5. Existing users may have `noteNFCURLs` restored by Auto Backup **only** if the signature matches; the plan must work when it does not.

## 10. What changes our assumptions

- We are not "modernising an app"; we are replacing ~250 lines whose only durable assets are the
  physical tags and the deterministic key function. A rewrite is cheaper than a refactor, and the
  UI-toolkit choice is unconstrained by existing UI.
- The single most valuable early deliverable is not features but **tag survival**: a store with
  stable IDs, a legacy resolver, and export/import. That is why backup (#8) moves into the first
  milestone (D7).
- The release-signing situation means "existing users" may effectively be a single user (the
  author) who will reinstall. The migration must still be automatic when the prefs file is present
  and self-service when it is not.
