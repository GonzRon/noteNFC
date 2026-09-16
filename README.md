# ServiceTag

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

A single-screen Compose app — Dashboard and Assets along the bottom, everything else one push
deep — around the original one-tap flow, which is still the spine: tap a tag anywhere and the
phone opens the right place with no screen to hunt for; Read / inspect tag, for the rare
deliberate look, lives under Settings.

- **Dashboard** — the assets in service, and, until the first export succeeds, a card that says
  there is no backup yet and offers to take one.
- **Assets** — a list you can filter to include archived ones, an asset screen built around the
  identity plate (category, name, description, tags and links), a create/edit form, and archive
  rather than delete.
- **Describe equipment fully: make, model, serial, purchase and warranty, location, parts of a
  larger system, seasons** — a grouped asset editor behind the plate and a DETAILS section that
  renders what is filled in; a component names the system it is part of and the system lists its
  components; a season window says OUT OF SEASON while today falls outside it; and retirement is
  a date you pick, independent of archiving, with a service event offered afterwards rather than
  demanded.
- **Log maintenance** — log maintenance events with typed readings and materials used; current
  readings and a service record per asset. Five starter templates (hot tub, power equipment, UPS,
  RO water, generic) seed the readings and the quick actions; nothing about them is hard-coded.
- **Define your own readings and service forms per asset; derived readings such as RO rejection** —
  a Readings & actions screen per asset with an editor for each: a reading has a label, unit,
  target, decimals and a meter flag, an action names the readings it asks for and the materials it
  suggests, and a derived reading is computed from two of the asset's own readings on the same
  entry rather than entered.
- **Write a tag** — share a note's external link (Joplin *Copy external link*, an Obsidian or
  Logseq URI, or any `https://` page) to ServiceTag and you get a card naming the kind of link and
  showing the URI; write it to a blank tag and you are back in the notes app. The writer reads
  the tag first, asks before overwriting anything, checks capacity, and reads the tag back to
  verify it. An asset's own screen can write a tag the same way.
- **Scan a tag** — tapping a tag opens it whether the app is running or not: a link tag opens the
  note directly with no screen in between, an asset tag opens the asset, and unknown, legacy or
  foreign tags are recognised as such and offered a bind or a rewrite, never an error. Read /
  inspect tag, under Settings, is the same reader kept as a utility for a deliberate look.
- **Links** — saved note links with their kind and host, openable and deletable (unless a tag
  still points at one).
- **Attach photos, manuals and receipts** — pick a folder once in Settings (any folder a
  document provider exposes, so a sync tool can replicate it) and a DOCUMENTS section on any asset
  or ledger entry takes files from the picker or the camera, keeps the bytes in that folder as
  ordinary documents, shows thumbnails for images, opens anything with the system viewer, and lets
  you rename, re-kind, date or delete each one. Nothing is hidden inside the app.
- **Back up and restore** — a Backup screen (from the dashboard's nudge, or the backup action on
  any asset) exports a backup *set* into a folder you pick: one ZIP holding every asset, tag
  binding, link and attachment record with its original id, and a second holding the attachment
  bytes. A restored phone resolves the same tags; restoring the data alone works and marks the
  files "not on this device" until you restore the second ZIP. Import replaces everything on the
  phone and makes you type `REPLACE` first.
- **Settings** — appearance (system / light / dark), the palette's name, the build's version and
  a link to the project.

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
git clone <this repo> && cd ServiceTag
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

Debug builds only, from `app/src/debug/`: a second launcher icon, **ServiceTag Backup (debug)**,
with four buttons — Seed sample, Export, Import (replace), Wipe — and a live `assets / tags /
links` count. **Wipe is the reason it still exists**: emptying the database without touching the
tags is how the restore proof stands in for a second phone, and it is deliberately not offered
anywhere in the app. It is a harness, not product UI, and the release APK contains neither the
activity nor its manifest entry (see `docs/design/phase-1a-evidence.md` §7 and
`docs/design/phase-1c-evidence.md` §9).

## Signing

Release builds pick up `~/.config/servicetag/keystore.properties` if it exists; when it is
absent the release build is simply unsigned and everything else still works. The file is
plain `storeFile` / `storePassword` / `keyAlias` / `keyPassword` and points at a keystore
outside the repository. Neither file is ever in the repo (`.gitignore` covers
`keystore.properties`, `*.jks`, `*.keystore`).

The release certificate's SHA-256 fingerprint is recorded once, in
`docs/design/phase-1a-evidence.md`. It is not reproduced here: a fingerprint is a
public key, but a repository's front page is not where a signer's identity belongs.

Back the keystore up somewhere outside the repo. Lose it and the app can never be updated
in place again — a new key means a new install for every user.

## Where this app came from

This repository was a combined note-utility and maintenance product before the 2026 product
split; the maintenance product kept the history and became ServiceTag, and the note utility was
reconstructed as its own project. What moved, what stayed, what the identities are now and how
the data migrated are all in `docs/architecture/product-split-migration.md`. Everything under
`docs/design/` predates the split and is history.
