# noteNFC
** evernote support removed

similar to the previously available touchanote app (no relation)
written from scratch, accomplishes the basic functionality of allowing you to share a note with this app,
a link to which is then written to an NFC tag.  You can then scan the NFC tag and immediately have the note
popup in joplin.

Write NFC Tag:
- inside of joplin, select note
- context menu for note, select copy external link
- share link with noteNFC app
- noteNFC app presents write dialog to user
- user scans NFC tag with phone
- phone writes NFC Tag with joplin note link

Read NFC Tag:
- user scans NFC tag with phone
- tag is recognized as an Joplin Note Link
- android OS launches joplin app with deep link for document, opening note directly


## Building

```bash
git clone <this repo> && cd noteNFC
./gradlew :app:assembleDebug
```

Needs a `local.properties` with `sdk.dir` pointing at an Android SDK (compileSdk 37,
build-tools 36.0.0). Everything else — Gradle 9.7.1, AGP 9.4.0 with its built-in Kotlin,
KSP, Room 3 — comes down through the wrapper and the version catalog.

Unit tests: `./gradlew :core:test :app:testDebugUnitTest`.

## Debug backup screen

Debug builds only, from `app/src/debug/`: a second launcher icon, **noteNFC Backup (debug)**,
with four buttons — Seed sample, Export, Import (replace), Wipe — and a live `assets / tags /
links` count. Export writes a `notenfc-backup-<yyyyMMdd-HHmm>.zip` through the Storage Access
Framework; Import replaces everything in the database with the contents of the file you pick.
It is a harness for the Phase 1A restore proof, not product UI, and the release APK contains
neither the activity nor its manifest entry (see `docs/design/phase-1a-evidence.md` §7).

## Signing

Release builds pick up `~/.config/notenfc/keystore.properties` if it exists; when it is
absent the release build is simply unsigned and everything else still works. The file is
plain `storeFile` / `storePassword` / `keyAlias` / `keyPassword` and points at
`~/.config/notenfc/notenfc-release.jks`. Neither file is ever in the repo (`.gitignore`
covers `keystore.properties`, `*.jks`, `*.keystore`).

Release key, alias `notenfc`, `CN=noteNFC, O=GonzRon`:

```
SHA-256: 09:02:D3:B0:F8:26:38:19:05:C6:D8:25:4F:DA:F3:67:56:92:4D:80:DA:7C:19:B9:0A:08:93:0B:33:27:7A:9F
```

Back the keystore up somewhere outside the repo. Lose it and the app can never be updated
in place again — a new key means a new install for every user.

## Cutover from the old package

From v2.0 the app ships as `com.loosecannon.notenfc`. The pre-2.0 builds were
`com.looseCannon.noteNFC`, a different package as far as Android is concerned, so the two
install side by side and both answer the legacy NFC tag filter. **Uninstall the old app
before testing the new one on a device**, otherwise tag scans raise a disambiguation
dialog and the wrong copy may win. There is no data migration path between the two: the
old app kept its links in SharedPreferences and the old install is expected to be thrown
away (see `docs/design/13-compatibility-policy.md` §4).
