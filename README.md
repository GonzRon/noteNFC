# noteNFC

An open-source, local-first Android app that turns NFC tags into durable handles for the
physical things you look after — the hot tub, the generator, the well pump, the bike — and for
the notes that describe them.

Stick a tag on the thing. Scan it and the phone opens the right place: a note in Joplin,
Obsidian or Logseq, a web page, or (from Phase 2 on) the asset's own record with its service
history, measurements and upcoming maintenance. The tag carries only a random identifier;
everything it means lives in a small SQLite database on the phone that you can back up and
restore with identities intact, so a tag keeps working across phone replacement, reinstall and
restore.

No accounts, no backend, no telemetry. Reminders are local first; Todoist is an optional,
later projection that never becomes the source of truth.

## What it does today

The original one-tap flow still works and is the spine of the app:

- **Write a tag** — share a note's external link (Joplin *Copy external link*, an Obsidian or
  Logseq URI, or any `https://` page) to noteNFC, hold a blank tag to the phone, done. The
  writer reads the tag first, asks before overwriting anything, checks capacity, and reads the
  tag back to verify it.
- **Scan a tag** — with the app closed, tap the tag: a link tag opens the note directly, an
  asset tag opens the asset. Unknown or foreign tags are recognised as such and offered a bind
  or a rewrite, never an error.
- **Back up and restore** — one ZIP holds every asset, tag binding and link with its original
  id, so a restored phone resolves the same tags.

Tags written by the pre-2.0 app (`md5_short` records) are still recognised as legacy tags and
can be bound as-is or rewritten in the current payload format; there is no dependency on the
old app or its data.

## Where it is going

The design package under [`docs/design/`](docs/design/README.md) lays out the whole system and
the phase sequence: assets with a journal of events and typed measurements (Phase 2),
provider-neutral maintenance schedules with local reminders and a health screen (Phase 3),
attachments on a pluggable, cloud-agnostic store (Phase 4), an optional Todoist projection
(Phase 5), supplies and parts (Phase 6). Phases 0–1A are merged; Phase 1B (the NFC payload
format, resolver and safe writer) is in progress. Progress is tracked in the GitHub issues,
one milestone per phase.

Code shape: `:core` is pure Kotlin (domain model, NDEF codec, scheduling engine, backup format,
policies, ports) and is tested on the JVM; `:app` is the Android shell (Room 3, NFC reader
mode, later Compose/Material 3). No DI framework, no plugin system.

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
