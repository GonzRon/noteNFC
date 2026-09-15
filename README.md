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

A single-screen Compose app — Dashboard, Assets and Scan along the bottom, everything else one
push deep — around the original one-tap flow, which is still the spine:

- **Dashboard** — the assets in service, and, until the first export succeeds, a card that says
  there is no backup yet and offers to take one.
- **Assets** — a list you can filter to include archived ones, an asset screen built around the
  identity plate (category, name, description, tags and links), a create/edit form, and archive
  rather than delete.
- **Log maintenance** — log maintenance events with typed readings and materials used; current
  readings and a service record per asset. Five starter templates (hot tub, power equipment, UPS,
  RO water, generic) seed the readings and the quick actions; nothing about them is hard-coded.
- **Write a tag** — share a note's external link (Joplin *Copy external link*, an Obsidian or
  Logseq URI, or any `https://` page) to noteNFC and you get a card naming the kind of link and
  showing the URI; write it to a blank tag and you are back in the notes app. The writer reads
  the tag first, asks before overwriting anything, checks capacity, and reads the tag back to
  verify it. An asset's own screen can write a tag the same way.
- **Scan a tag** — in the app, the scan screen reads tags in the foreground; with the app closed,
  tapping a tag still opens it. A link tag opens the note directly with no screen in between; an
  asset tag opens the asset. Unknown, legacy or foreign tags are recognised as such and offered a
  bind or a rewrite, never an error.
- **Links** — saved note links with their kind and host, openable and deletable (unless a tag
  still points at one).
- **Back up and restore** — a Backup screen (from the dashboard's nudge, or the backup action on
  any asset) exports one ZIP holding every asset, tag binding and link with its original id, so a
  restored phone resolves the same tags. Import replaces everything on the phone and makes you
  type `REPLACE` first.
- **Settings** — appearance (system / light / dark), the palette's name, the build's version and
  a link to the project.

Tags written by the pre-2.0 app (`md5_short` records) are still recognised as legacy tags and
can be bound as-is or rewritten in the current payload format; there is no dependency on the
old app or its data.

## Where it is going

The design package under [`docs/design/`](docs/design/README.md) lays out the whole system and
the phase sequence: assets with a journal of events and typed measurements (Phase 2),
provider-neutral maintenance schedules with local reminders and a health screen (Phase 3),
attachments on a pluggable, cloud-agnostic store (Phase 4), an optional Todoist projection
(Phase 5), supplies and parts (Phase 6). Phases 0–1B are merged; Phase 1C (the Compose shell and
the asset/link UX) closes milestone M1. Progress is tracked in the GitHub issues, one milestone
per phase.

Code shape: `:core` is pure Kotlin (domain model, NDEF codec, scheduling engine, backup format,
policies, ports) and is tested on the JVM; `:app` is the Android shell (Room 3, NFC reader mode,
Compose + Material 3 + Navigation 3). No DI framework, no plugin system.

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

Backup and restore are product features now: the dashboard's nudge, or the backup action on an
asset, opens a real screen that exports a ZIP and imports one back after you type `REPLACE`. What
stays debug-only is the harness beside it.

Debug builds only, from `app/src/debug/`: a second launcher icon, **noteNFC Backup (debug)**,
with four buttons — Seed sample, Export, Import (replace), Wipe — and a live `assets / tags /
links` count. **Wipe is the reason it still exists**: emptying the database without touching the
tags is how the restore proof stands in for a second phone, and it is deliberately not offered
anywhere in the app. It is a harness, not product UI, and the release APK contains neither the
activity nor its manifest entry (see `docs/design/phase-1a-evidence.md` §7 and
`docs/design/phase-1c-evidence.md` §9).

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
