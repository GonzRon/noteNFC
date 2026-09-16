# Product-split archaeology — ServiceTag / noteNFC / nfc-tag-core

Canonical read-only record of what this repository *is* at the pre-split checkpoint, written so that
an engineer who has never opened it can design the split without re-deriving any of it.

---

## 1. Purpose and status

**Status: read-only findings as of 2026-09-16, at the pre-split checkpoint `ac523d7`** ("evidence:
the two ci follow-ups"). Nothing in this document proposes a change. It consolidates four parallel
read-only investigations — `.superpowers/split/archaeology-repo-build-identity.md`,
`archaeology-nfc.md`, `archaeology-data.md`, `archaeology-history.md` — plus the Phase A checkpoint
facts in `.superpowers/split/ledger.md`. Surprising claims were re-verified against the source with
`git show` / `git grep` / `git rev-list`; §9 lists every re-verification and the four places a report
disagreed with the source or with another report.

The repository currently carries **three products in one tree**. The split exists to separate them:

| Product | One sentence |
|---|---|
| **noteNFC** | The narrow share→write→tap utility: receive a shared note link, write an identifier to an NFC tag, and on a later tap open that note or web page — nothing else. |
| **ServiceTag** | The physical-asset service/maintenance product that grew out of noteNFC between 2026-09-14 and 2026-09-16 (Phases 0 → 4A): assets, journal, measurement definitions and profiles, attachments, backup/restore — plus schedules and reminders, which are designed but **not yet implemented** (Phase 3, future). |
| **nfc-tag-core** | The shared, product-neutral NFC mechanism both products need: NDEF envelope codec, reader-mode session, safe writer (read-before-write / capacity / read-back / lock-last), and the Android↔bytes bridge — with identity supplied as a parameter. |

**Conventions used throughout.** Evidence is cited as a repo-relative path, a commit SHA, or a
report section. Claims are tagged:

- **[code]** — read directly out of the tree at `ac523d7` (or at the named commit).
- **[JVM-proven]** — pinned by a unit test in `:core` or `:app`'s JVM suite.
- **[device-observed]** — recorded as a device row in a `docs/design/phase-*-evidence.md` file.
- **[platform-doc]** — quoted from Android documentation; **not** observed here.
- **[unobserved]** — nobody has run it; a design assumption until someone does.

No home paths (`~` only), no device serials, no phone model or codename, no note identifiers, no
folder names from the phone, and no secrets appear below. Certificate digests are permitted but are
referenced rather than reproduced.

---

## 2. The historical narrow endpoint

### 2.1 The chosen boundary

> **Narrow-product semantic endpoint: `c84b881` — "evidence: record the green ci run" (2026-09-14).**
> **First redesign commit after it: `63635be` — "new identity: com.loosecannon.notenfc, v2.0, room3/ksp/serialization plugins, release signing from `~/.config`" (2026-09-14).**

Both subjects and dates re-verified with `git log -1`. `c84b881` is an ancestor of `ac523d7`
(`git merge-base --is-ancestor` → true).

### 2.2 Era table

Master's history was rewritten with `git filter-repo` on 2026-09-14; every SHA below exists in the
*current* history. 175 commits total, 74 on the first-parent line.

| Era | Commits | Span | Package / identity | What the product was |
|---|---|---|---|---|
| **E0 — Evernote prototype** | `5fb6aed` … `0652023` (5) | 2023-08-05 → 2023-08-07 | `com.looseCannon.evernotenfc` | Share an Evernote note → write a random UUID-8 key to a tag as external record `com.loosecannon.evernotenfc:uuid8_link`; tap → look the key up in `SharedPreferences("EvernoteURLs")` → `ACTION_VIEW` the `evernote://` URL. |
| **E1 — Evernote, MD5 keying** | `d88b84d` … `88e400a` (7, incl. `f03d833` "renaming project") | 2023-08-07 → 2023-08-09 | renamed to `com.looseCannon.noteNFC` at `f03d833` | `d88b84d` replaces the random UUID with MD5-of-link, first 8 lowercase hex ("to avoid duplicate UUID's pointing to the same evernote link"); record type becomes `md5_short`. `f2c32aa` adds a `GetUIDActivity` that harvested Evernote user/shard ids, and fixes the stale-GUID-in-mutable-`PendingIntent` defect (re-verified: `GetUIDActivity` first appears in `f2c32aa`, dated 2023-08-08, inside E1 — not E0 as an earlier draft of this table had it). |
| **E2 — the narrow noteNFC product (Joplin)** | `abaa193`, `ef83179`, `3a3c69a` (3) | 2024-10-27 | `com.looseCannon.noteNFC` | `abaa193` "Removed Evernote NFC Note Linking support / Added Joplin Note Linking Support": deletes `GetUIDActivity`, swaps the link gate to "shared text contains `joplin`", adds the `nfc_tech_filter.xml` TECH catch-all. **This is the product being reconstructed.** `ef83179`/`3a3c69a` are README-only. |
| **E3 — Phase 0: modernise in place, behaviour frozen** | `7291615` … `c84b881` (15) | 2026-09-14 | still `com.looseCannon.noteNFC` | The redesign *programme* starts (`7291615` lands the design package) but for 15 commits `app/src/main` still expresses only the narrow product. The work is toolchain + hygiene + extraction: buildable-from-clone (`47fbe6f`), `:core` JVM module holding the legacy key/codec/link-policy pinned by tests (`76b751a`), activities routed through `:core` with no behaviour change (`52a1ff5`), GitHub Actions CI (`d5dcb6c`), green at `c84b881`. |
| **E4 — ServiceTag redesign proper** | `63635be` … `ac523d7` (145) | 2026-09-14 → 2026-09-16 | **`com.loosecannon.notenfc`**, v2.0 → 2.4 | `63635be` renames the package and adds Room 3 / KSP / serialization. `970c739` is the first commit where an asset/maintenance concept enters *source* (Room schema v1: `asset`, `nfc_tag`, `external_link`). Then backup format, tag payload v1, reader-mode writer, Compose/Nav3 shell, journal, editors, asset model, attachments. `26ec9d0` deletes the 2024 legacy activities and the TECH catch-all. |

**First-occurrence markers** (`archaeology-history.md` §1):

| Marker | Commit | Date |
|---|---|---|
| First `docs/design/` | `7291615` | 2026-09-14 |
| First `:core` module | `76b751a` | 2026-09-14 |
| First Room/Compose in a **spike** (report only) | `dfb8356` | 2026-09-14 |
| First Room/Compose plugins in the **real build** | `63635be` | 2026-09-14 |
| Package rename `com.looseCannon.noteNFC` → `com.loosecannon.notenfc` | `63635be` | 2026-09-14 |
| First asset/maintenance concept **in source** | `970c739` | 2026-09-14 |
| **Last commit whose `app/src/main` expresses only the narrow product** | `c84b881` | 2026-09-14 |

### 2.3 Candidate comparison

Three boundaries were defensible; all three are on the first-parent line with a narrow-only
`app/src/main`.

| | **A — `3a3c69a`** (2024-10-27) | **B — `52a1ff5`** (2026-09-14) | **C — `c84b881`** (2026-09-14, chosen) |
|---|---|---|---|
| Position | last commit before the redesign programme began | 7th commit of Phase 0 | 15th and last commit of Phase 0; its child renames the package |
| Ancestry size | 15 | 22 | **30** (re-verified: `git rev-list --count c84b881` = 30) |
| `applicationId` / `namespace` | `com.looseCannon.noteNFC` | same | same **[code]** |
| Activities in manifest | `MainActivity` (LAUNCHER + `ACTION_SEND text/plain`), `NFCHandlerActivity` (**no filter, not exported** — with no intent filter the platform default is `exported=false`; re-verified in the source at `3a3c69a`, which declares neither a filter nor `android:exported` on it), `LaunchNoteNFCLinkActivity` (`NDEF_DISCOVERED` ext filter + `TECH_DISCOVERED` catch-all) | same, plus `47fbe6f` makes the already-true default explicit with `exported="false"` on the handler, and the legacy `package=` attribute removed | same as B |
| NFC intent filters | `vnd.android.nfc://ext/com.loosecannon.notenfc:md5_short` + TECH catch-all (`NfcA`, `Ndef`) | same | same |
| Persistence | `SharedPreferences("noteNFCURLs")`, key → link | same | same **[code]** |
| MD5 key derivation | inline in `mainActivity.getShortHash` | `core…nfc.LegacyKey.compute` | same as B |
| Asset/maintenance notion in **source** | none | none | none |
| ServiceTag design docs in tree | none | ~9.6k lines under `docs/design/` + `docs/superpowers/plans/` | same, plus `phase-0-evidence.md`, `spikes/S1-toolchain.md` (70 files under `docs/`, re-verified) |
| Builds from a clone | **No** — no `settings.gradle.kts`, no wrapper tracked (re-verified: 0 matches at `3a3c69a`) | Yes | Yes (`settings.gradle.kts`, `gradle/wrapper/*`, `.github/workflows/ci.yml` all present) |
| Toolchain | AGP 8.7.1, Kotlin 1.8.0, compileSdk 34 / target 33 / min 26, jvmTarget 1.8 | AGP 9.4.0, Kotlin 2.4.20, compileSdk 37 / target 36 / min 26, JDK 17, version catalog, foojay resolver | same as B **[code]** |
| Tests | **none** | 3 JUnit 5 classes in `:core` | same, with `assert()` → `assertTrue` fixup (`0f5f6ad`) |
| CI | none | none | `.github/workflows/ci.yml`, **verified green** |
| Tracked build artefacts | debug **and** release APK (5 790 476 B + 4 740 933 B, re-verified) | release APK only | release APK only (4 740 933 B) — **the same git blob as at `3a3c69a`** (re-verified: identical blob hash), i.e. the 2024 shipped artifact carried forward unchanged |
| versionCode / versionName | 1 / 1.0 | 2 / 1.1 | 2 / 1.1 **[code]** |

**Why not A.** The tree is not a coherent buildable project: `settings.gradle.kts` and
`gradle/wrapper/` were never committed, so a clone cannot build it at all — that is exactly what
`47fbe6f` fixed. It also has zero tests and two tracked APKs. Branching from A means re-deriving the
legacy protocol by hand, which `76b751a` already did once with pinned vectors.

**Why not B.** `52a1ff5` is mid-phase. Test hygiene (`0f5f6ad`), the legacy-compat policy ruling and
package-id normalisation that made the codec's wire bytes explicit (`b9f7e51`), and the two CI-fix
commits all land after it. At B nothing had been proven to build or pass on a runner — `4d2a7fc`
records that CI "has never run on a runner yet".

### 2.4 What the product did at `c84b881`

Three activities, self-contained apart from `:core` **[code]**
(`app/src/main/java/com/looseCannon/noteNFC/{mainActivity,NFCHandlerActivity,LaunchNoteNFCLinkActivity}.kt`):

1. **Receive** — `mainActivity` is both LAUNCHER and `ACTION_SEND` / `text/plain` receiver; reads `Intent.EXTRA_TEXT`.
2. **Gate** — accepts the shared text verbatim iff it contains the literal substring `joplin` (case-sensitive), anywhere. Otherwise a "No link received" toast. `LegacyLinkPolicy` preserves this "warts and all" and a test asserts the wart (`LegacyLinkPolicyTest`, `"My Note https://example.com not joplin"` passes the gate).
3. **Key** — `LegacyKey.compute(text)` = MD5 of the accepted text's UTF-8 bytes → lowercase hex → first 8 characters. Hashed over the *whole shared text*, not an extracted URI. The source carries the comment *"Never change this."*
4. **Remember** — `SharedPreferences("noteNFCURLs", MODE_PRIVATE).putString(key, text)`.
5. **Hand off** — starts `NFCHandlerActivity` with the key as a string extra, then `finish()`es so the user returns to Joplin (`0652023`'s fix).
6. **Write** — `NFCHandlerActivity` shows `write_nfc_link.xml`, arms `enableForegroundDispatch` with a `FLAG_MUTABLE` self-targeting `PendingIntent`, writes one NDEF message on the resulting intent, falls back to `NdefFormatable.format` for a blank tag. Success/failure is a toast, then `finish()`.
7. **Tap** — `LaunchNoteNFCLinkActivity` filters `NDEF_DISCOVERED` on the legacy ext URI, decodes the first record of the first message through `NdefCodec`, looks the 8-hex key up in the same prefs, and on a hit does `startActivity(Intent(ACTION_VIEW, Uri.parse(link)))`. On a miss: "Joplin Note Link not found."

### 2.5 What it did **not** do

- No database of any kind — no Room, no SQLite, no file store; one `SharedPreferences` map.
- No list, browse, search, edit or delete of bound tags. The binding is write-only and invisible.
- **No backup and no export.** The key→link map lives only in app-private prefs, so uninstall or "clear data" silently orphans every physical tag ever written.
- No asset, component, meter, schedule, reminder, journal, attachment or money concept — none of the ServiceTag domain.
- No Compose, no navigation framework, no design system; two XML layouts and the platform theme.
- No reader mode: foreground dispatch only, which the platform hands a tag to only after its own dispatch has fired (the "vibrate-then-dispatch" race).
- No read-before-write, no overwrite confirmation, no capacity check, no read-back verification, no tag locking, no rebind flow.
- No validation of the stored string before `ACTION_VIEW` and no `PackageManager` resolve check, so a missing Joplin means an `ActivityNotFoundException` crash.
- No unit tests of the app module, no instrumented tests, no lint gate.
- No ProGuard/R8 (`isMinifyEnabled = false`), no signing config in the build file.

### 2.6 Why `c84b881` is the semantic boundary

The redesign *programme* starts at `7291615`, but a programme's first commit is not the product's
semantic end. `7291615..c84b881` is a 15-commit window in which the redesign exists only as
**documents and infrastructure**: shipped behaviour stays that of 2024, while the repository gains
the four things a reconstruction cannot cheaply re-create — a buildable project, a modern toolchain,
characterisation tests that pin the legacy wire format with hand-computed vectors, and a CI workflow
proven green. `c84b881` is the last commit in that window; one commit later the product's identity
changes. Supporting evidence from `archaeology-history.md` §3:

- `git diff 3a3c69a c84b881 -- app/src` touches six files, **+18 / −63**: the manifest (drop the legacy `package=`, add `exported="false"`, delete a commented activity block), the three activities (imports swapped to `:core`, inline helpers deleted), two theme files. No new activity, filter, permission or screen.
- `git log 3a3c69a..970c739 -- app/src/main` lists exactly four commits: `47fbe6f`, `52a1ff5`, `63635be`, `970c739`. The first two are behaviour-neutral; the boundary sits between them and the last two.
- The three `:core` files at `c84b881` are *only* legacy (re-verified): `LegacyKey.kt`, `NdefCodec.kt`, `LegacyLinkPolicy.kt`, with three matching test classes. Nothing else exists in `:core`.
- No occurrence of asset, meter, schedule, journal, attachment, Room, Compose or Nav3 in any source file; those words appear only under `docs/`.

One deliberate behaviour refinement does exist inside the window and is named rather than hidden: at
`52a1ff5` the read path stopped doing `messages[0].records[0].payload` blind and started routing
through `NdefCodec.decode`, so a foreign, empty or malformed record now yields `null` and a "not
found" toast instead of an unchecked index access. That is a bug fix inside the narrow product's own
contract, not a redesign.

### 2.7 Lineage feasibility

Feasible with no rewriting. `c84b881` is on the first-parent line of `origin/master` with **30
ancestors and zero merge commits** (re-verified: `git rev-list --merges --count c84b881` = 0). A new
repository seeded by branching that commit keeps exact, verifiable ancestry from `5fb6aed` (2023) to
`c84b881` — the Evernote prototype, the MD5 switch, the Joplin conversion and all of Phase 0 — with
no grafting and no orphan root. ServiceTag continues on the existing master from `63635be`. The two
products then share history up to `c84b881` and diverge cleanly.

The one caveat is content, not Git: the `c84b881` tree carries the ServiceTag design package
(`docs/design/`, `docs/superpowers/plans/`, **70 files** under `docs/`, ~9.6k lines, re-verified).
Those documents describe the other product. Deleting them going forward does not remove them from
history; if the reconstructed repository is public and must not publish ServiceTag's design, the
alternative is to squash or scrub, at the cost of the clean ancestry.

**A second caveat is CI, not Git.** History was rewritten with `git filter-repo` on 2026-09-14
(§2.2), which changes every commit's hash. No CI run is recorded against `c84b881`'s *current* SHA —
only its pre-rewrite twin `ccdb9d3` (same subject, "evidence: record the green ci run", and the same
timestamp as `c84b881`) and that commit's parent's twin `12e2c09` ("ci: don't ask sdkmanager for the
dead 'tools' package") carry a recorded green run, and both hashes are gone from the current history.
**A repository branched from `c84b881` inherits no CI proof for that commit under its current
identity; CI must run again there before it can be trusted.**

### 2.8 The four files in the `c84b881` tree carrying personal data

The tree is 116 files, 46 outside `docs/` (re-verified). `git grep -E '/home/|/Users/|\.config/'`
over the whole tree returns **zero files**; no device model or codename, no serial, no
`ANDROID_SERIAL`, no `adb -s`, no email address, no LAN hostname or private IP, no real note
identifier. The only note ids present are a synthetic 32-hex placeholder used as a `:core` test
vector and quoted in two design docs. The tracked release APK was string-scanned for embedded build
paths and certificate subject fields: none found (v2/v3-signed only, so no `META-INF/*.RSA` block,
and no `CN=`/`O=` text recoverable).

Four files carry owner-identifying data and a new public repository must scrub them. Contents are
described, not reproduced.

| File at `c84b881` | What it carries | Re-verified |
|---|---|---|
| `docs/design/issues/applied.md` | 37 lines containing the owner's GitHub handle and issue URLs on their personal repository. Disclosure of a public identity, not a secret. | yes — 37 handle occurrences |
| `docs/superpowers/plans/2026-09-14-phase-0-foundation.md` | 5 lines with the same handle / repo URLs. Planning doc for the other product. | yes — 5 occurrences |
| `docs/design/phase-0-evidence.md` | 4 lines recording the **SHA-256 / SHA-1 / MD5 certificate digests of the shipped release APK's signer**, plus the debug signer's digest. Public-key fingerprints, not the private key; the repo contains no keystore. They identify the legacy signing identity and are of no use to a public reader. | yes — re-verified: 16 lines in the file mention a digest algorithm by name, of which 2 are the actual colon-separated digest values (the signer block is the concern, not every algorithm mention) |
| `app/release/app-release.apk` | 4 740 933 B, tracked, signed with the owner's personal key. Hygiene, not leakage. | yes |

**A fifth carrier, not a file.** All 30 preserved commits' author name and author email (§2.7) are
personal data too, carried in commit metadata rather than in a tracked file — visible via `git log`,
invisible to a file-content grep. This is already public in this repository today; preserving the
exact ancestry (§2.7) necessarily preserves it unchanged, and only a further history rewrite would
change it, at the cost of the clean ancestry the reconstruction exists to keep.

**Checked and cleared as false positives:** `gradle/gradle-daemon-jvm.properties` (foojay
toolchain-id hashes), `gradlew` (an upstream Gradle commit SHA),
`core/src/test/.../LegacyKeyTest.kt`, `LegacyLinkPolicyTest.kt`,
`docs/design/06-legacy-compatibility.md`, `docs/design/issues/rewrite-06.md`,
`docs/design/issues/new-deeplink-contract.md` (the literal scheme `joplin://` and the synthetic
placeholder id only).

**Not scrubbable, and should not be:** the string `looseCannon` appears in 12 files (re-verified) —
package directory names, `namespace`, `applicationId`, and the legacy record type's derivation. It
is the owner's long-published pseudonymous vendor name and it is load-bearing for legacy tag
compatibility: the ext-type bytes on every existing tag derive from it.

### 2.9 The legacy protocol at the boundary

Exactly **one NDEF record, in one NDEF message. No AAR, no URI record, no Text record, no second
record.**

```
TNF     : TNF_EXTERNAL_TYPE (0x04)
TYPE    : "com.loosecannon.notenfc:md5_short"   (US-ASCII, 33 bytes)
PAYLOAD : 8 bytes, US-ASCII, /^[0-9a-f]{8}$/

payload = hex( MD5( sharedText.toByteArray(UTF_8) ) ).lowercase().substring(0, 8)
```

`sharedText` is the raw `Intent.EXTRA_TEXT` that passed the `contains("joplin")` gate — title and
surrounding prose included, not a normalised URI. **[JVM-proven]** by `LegacyKeyTest` at `c84b881`,
which pins two `joplin://x-callback-url/openNote?id=<32-hex placeholder>` forms (one bare, one with
a leading title) that hash *differently* — direct proof that the hash covers the whole shared text —
plus a non-ASCII vector proving UTF-8 rather than the platform default charset.

Two facts about the type string matter for compatibility:

1. The 2024 source called `NdefRecord.createExternal("com.looseCannon.noteNFC", "md5_short", …)`. `createExternal` **lower-cases both the domain and the type** before joining them with `:`, so the bytes on every tag ever written are the lowercase `com.loosecannon.notenfc:md5_short`, even though the app's own `applicationId` was mixed-case. The 2024 manifest already used the lowercase form in its `pathPrefix`, which is why tapping worked.
2. `b9f7e51` normalised this in `:core` by declaring the legacy domain as a lowercase constant, so the encoder is explicit about the wire bytes rather than relying on the platform to lower-case for it. The emitted record is byte-identical to 2024; `NdefCodecTest.encodeLegacyRoundTrips` asserts the exact type and payload bytes.

**Resolution is local and non-portable.** The payload is only a lookup key into
`SharedPreferences("noteNFCURLs")` on the phone that wrote it. A physical tag written by the 2024 app
carries no link, so **tags are device-local**: a tag written on one device is meaningless on another.
Any reboot of the product must either import that prefs map or accept that pre-existing tags resolve
only on the original handset.

### 2.10 Identity and signing of the historical narrow app

- The historical narrow app's `applicationId` and `namespace` were the **mixed-case `com.looseCannon.noteNFC`** **[code]** at `c84b881`. Note the asymmetry inside that same tree: `:core` already used the lowercase package directory `com/loosecannon/notenfc/core/...` (from `b9f7e51`) while `:app` was still `com/looseCannon/noteNFC/...`.
- **The signing key for that identity is not available.** Its certificate fingerprints are recorded in `docs/design/phase-0-evidence.md`; the key itself is not in the repository and closed issue **#29** ("[MVP] Investigate the installed APK's signing certificate and upgrade path") is the historical record of that investigation. Because an in-place upgrade of the 2024 install is therefore impossible, Phase 1A minted a **new lowercase identity** `com.loosecannon.notenfc` with a new release key (`63635be`).
- **The 2023 draft release is Evernote-era, not a narrow-product release.** GitHub holds exactly one release: name `initial working`, `draft: true`, `prerelease: true`, `tag_name: ""` (empty — never tagged; GitHub only minted a synthetic placeholder tag for the `html_url`), `target_commitish: "master"`, `published_at: null`, no assets, body *"lots of improvements needed / to come, but generally, working!"*. Created 2023-08-06T15:44:31Z. The commit the repository stood at two minutes earlier is `707ca3f` "adding a debug apk" — an **E0 Evernote-era** commit: package `com.looseCannon.evernotenfc`, key = `UUID.randomUUID().toString().substring(0, 8)`, record type `com.loosecannon.evernotenfc:uuid8_link`, launching `evernote://share-note-via-link/...`. It predates MD5 keying (`d88b84d`), the `md5_short` type, the project rename (`f03d833`) and the Joplin conversion (`abaa193`) by over a year. Because it has no tag and targets the branch ref rather than a SHA, it is **unanchored** — publishing it today would tag whatever master then is. It carries no artefacts worth preserving and is not a lineage anchor for either product.

---

## 3. Evolution from noteNFC into ServiceTag

Phase landings, re-verified with `git log --first-parent`. Phase 0 and Phase 1A landed as ordinary
first-parent commits; Phases 1B onward landed as real merge commits (8 merge commits on master in
total — see §9, discrepancy D1).

| Phase | Landed at | Date | What it added |
|---|---|---|---|
| **0 — foundation** | `c84b881` (no merge commit; the phase is `7291615`…`c84b881`) | 2026-09-14 | Buildable-from-clone (`47fbe6f`: tracked wrapper + `settings.gradle.kts`, version catalog, AGP 9.4, JDK 17 targets, tracked debug APK dropped); `:core` pure-JVM module with the legacy key/codec/link-policy pinned by JUnit 5 tests (`76b751a`); activities routed through `:core` with no behaviour change (`52a1ff5`); GitHub Actions CI (`d5dcb6c`, fixed by `10984ee`); the design package (`7291615`) and issue restructuring (`f5cc089`, `a2580f9`); the legacy-compat/package-id policy ruling D13 (`b9f7e51`); spike S1 toolchain report (`dfb8356`). **Behaviour frozen at 2024.** |
| **1A — durable identity** | `63635be` … `30525c7` (no merge commit) | 2026-09-14 | New identity `com.loosecannon.notenfc`, v2.0, Room 3 / KSP / kotlinx-serialization plugins, release signing from `~/.config` (`63635be`); Room schema v1 — `asset`, `nfc_tag`, `external_link` + domain models, repositories, app graph (`970c739`); backup format v1 as a ZIP of manifest+data with export and replace-import use cases (`13a7d54`); SAF backup I/O, debug backup screen, restore proof test (`d2d2f03`); graph validation moved to decode (`12f7c5a`); single-read-transaction export (`30525c7`); `sqlite-bundled` dropped (`88061ac`). |
| **1B — NFC identity** | merge `19af5ae` | 2026-09-14 | The 2024 legacy activities, `LegacyKey` and the TECH catch-all deleted first (`26ec9d0`, re-verified as the phase's second commit, right after the plan); then tag payload format **v1**, the typed `TagPayload`, `OverwritePolicy`, the AAR (`f92a391`); `LinkLaunchPolicy` + `TagRoute` (`8a94872`); the safe Android adapter — `NdefBridge`, `NfcReaderModeSession`, `TagWriter` (`dc1bb1c`); throw contracts documented (`bdcc475`); the write sequence (`18534bd`); `NfcDispatchActivity` + manifest `<queries>` (`25026d7`); hostile-extras hardening (`54f9aea`); `DeepLinkRoute` (`a8204d4`); reader-mode NDEF-check and lock-last fixes (`e2cf1d0`); stale-handle consent (`0e1975f`). |
| **1C — Compose shell** | merge `a8d0094` | 2026-09-15 | Single-activity nav3 shell, Apollo Service Binder theme, asset/link/scan/backup screens, share host, UI-less dispatch trampoline, interim screens gone (`19042da`); write rules lifted behind the `TagIo` seam (`c808b49`); scan tab retired (`838d6a3`, `53f9cac`); G1 visual gate and D12 corrections (`4b6218b`, `17d6297`). |
| **2A — maintenance journal** | merge `7fe7079` | 2026-09-15 | Asset event journal with structured measurements and consumable usage; event profiles; the roadmap ruling that the product split is a pre-deployment convergence op (`f1b4e5e`). |
| **2B-1 — editors** | merge `8b8b721` | 2026-09-15 | Measurement-definition and profile editors; derived readings with prospective derived-graph checks on definition edits. |
| **2B-2 — physical asset model** | merge `b8fab0c` | 2026-09-15 | The full physical-asset model: manufacturer/model/serial, purchase and in-service dates, money in minor units, vendor, location, warranty, retirement, season windows; children-first asset wipe via `AssetTree`. |
| **4A — attachments** | merge `19213dd`, follow-ups `1b1bd3c`, `73fc463` | 2026-09-16 | Attachments with a SAF-tree managed store, the data/artifacts two-file backup split, spike S5 (`8ee4771`, `9adf5aa`), all-or-nothing backup-set export with partial-file cleanup (`41af038`); follow-ups gated the progress test on a latch (`4f497c5`) and guarded the documents scan (`ee8231e`). |
| **checkpoint** | `ac523d7` | 2026-09-16 | CI evidence only; code identical to `73fc463`. |

Phase 3 (scheduling) and Phase 3R (automatic versioned backup) are designed but not implemented;
the roadmap deferred 3R off the critical path (`94d4286`) and ordered 4A before 3 (`0f3ed3a`).

---

## 4. Present-day inventory (at `ac523d7`)

### 4.1 Repository, remote, refs

| Item | Value |
|---|---|
| Remote | single `origin` → GitHub, `GonzRon/noteNFC` (fetch + push). No Gitea or other remote. |
| Visibility / fork / issues | PUBLIC, not a fork, issues enabled, default branch `master` |
| Branches | `master` = `ac523d7` (tracks `origin/master`); `pre-split-master` = `ac523d7` (local + pushed recovery branch); `product-split` = `ac523d7` (the split worktree's branch) |
| Tags | exactly one: `pre-split-checkpoint` (annotated) → `ac523d7`, message *"pre-split checkpoint: noteNFC 2.4 (versionCode 6), phase 4a closed, ci green"*. **There is no 2023 or 2024 tag, locally or on origin.** |
| Worktrees | the primary checkout plus the split worktree. An earlier session's `noteNFC-phase2b2` checkout no longer exists. |
| Non-git siblings (prior split-prep residue, untracked, outside any working tree) | a pre-rewrite bundle, two rewrite-rule/map text files, a backups directory, and a releases directory holding the signed 2.4 rollback APK |

**Naming correction for the record.** `pre-split-master` is a **branch**, not a tag; `git tag`
lists only `pre-split-checkpoint`. Both recovery refs exist and both point at `ac523d7`, so the
intent (a safe rollback point) holds either way.

### 4.2 CI

`.github/` contains exactly one file, `.github/workflows/ci.yml`. No other workflow, no issue/PR
templates, no CODEOWNERS, no dependabot config.

- Triggers: `push` and `pull_request` (all branches).
- Runner `ubuntu-latest`; `actions/checkout@v4`; `actions/setup-java@v4` with **Temurin 17**; `android-actions/setup-android@v3` installing only `platform-tools`; `gradle/actions/setup-gradle@v4`.
- One build step: `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain`.
- `actions/upload-artifact@v4` with `if: always()` uploads `core/build/test-results` and `app/build/test-results`.
- **No instrumented/androidTest step** (correctly excluded — those need a device).
- **No `secrets.*` interpolation anywhere.** Release signing is entirely local, read from `~/.config/notenfc/keystore.properties`, and never touches CI.

Master's tip is green at `73fc463`/`ac523d7`. **Two** of the last runs failed, not one: the phase-4a
merge `19213dd` and its own follow-up merge `1b1bd3c` both failed — the checkpoint commit's own
subject, "evidence: the two ci follow-ups", names both — fixed respectively by `4f497c5` (gate the
progress test on a latch) and `ee8231e` (guard the documents scan, landed via the `73fc463` merge).

### 4.3 Hosting token capabilities

Two GitHub accounts are configured; one is active.

| Account | Active | Scopes |
|---|---|---|
| **GonzRon** | **yes** | `gist`, `read:org`, `repo`, `workflow` (confirmed server-side via the `X-Oauth-Scopes` response header) |
| (second account) | no | the same, plus `write:discussion` and **`delete_repo`** |

| Capability of the active token | Available? |
|---|---|
| **Rename** this repository | **yes** — `repo` scope covers the repo-settings PATCH |
| **Create** a new repository (a fresh `ServiceTag` or `noteNFC`) | **yes** — `repo` covers `POST /user/repos` |
| **Transfer issues** between two repositories both owned by this account | **yes**, functionally — transfer needs push/admin on source and destination, which `repo` provides. Not exercised (read-only investigation); no scope gap blocks it. |
| **Delete** a repository | **no** — `delete_repo` is absent from the active token. Only the inactive account holds it. Any delete/rename-and-discard step needs the other account made active or the active token re-authorised. |

### 4.4 Gradle and toolchain

`settings.gradle.kts`: `rootProject.name = "noteNFC"`; `include(":app", ":core")`; foojay resolver
convention 1.0.0; repositories `google()` + `mavenCentral()` only, `FAIL_ON_PROJECT_REPOS`.

Root `build.gradle.kts` declares (all `apply false`): `android.application`, `kotlin.jvm`, `ksp`,
`room3`, `kotlin.serialization`, `compose.compiler`.

`:core` — **pure-Kotlin JVM module, no Android plugin.** Plugins `kotlin.jvm` +
`kotlin.serialization`; JVM toolchain 17; JUnit 5 (`useJUnitPlatform()`) plus `kotlin.test` and
coroutines-test; dependencies only coroutines-core and kotlinx-serialization-json. **This is the
single most important structural fact for the split: the codec half of the NFC layer is already
Android-free and has no Room or Compose reach.**

`:app` — Android application module; plugins `android.application`, `ksp`, `room3`,
`compose.compiler`, `kotlin.serialization`; depends on `project(":core")`.

| Component | Version |
|---|---|
| Gradle wrapper | **9.7.1** (`validateDistributionUrl=true`) |
| AGP | 9.4.0 |
| Kotlin | 2.4.20 |
| KSP | 2.3.12 |
| Room 3 | 3.0.3 |
| Compose BOM | 2026.08.00 |
| Navigation 3 (runtime + ui) | 1.1.7 |
| Lifecycle | 2.10.0 |
| kotlinx.serialization / coroutines | 1.9.0 / 1.10.2 |
| JUnit 5 / JUnit 4 | 5.11.4 / 4.13.2 |
| Espresso | 3.7.0 |
| androidx.test runner / rules / ext-junit | 1.7.0 / 1.7.0 / 1.3.0 |

`gradle.properties`: `org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8`, `org.gradle.caching=true`,
`org.gradle.configuration-cache=true`, `android.useAndroidX=true`, `kotlin.code.style=official`.

`gradle/gradle-daemon-jvm.properties` pins the **Gradle daemon's own JVM** to `toolchainVendor=JETBRAINS`,
`toolchainVersion=25` (re-verified) — a different, newer JVM than the 17 the app/core modules compile
and test against; the daemon JVM and the module JVM toolchain are independent settings, and neither
constrains the other.

**What an extracted library changes for the build.** `.github/workflows/ci.yml`'s
`actions/checkout@v4` step declares no `submodules:` key (re-verified) — a `nfc-tag-core` dependency
wired in as a git submodule needs that key added before CI can even see the submodule's content. A
Gradle composite build (`includeBuild`) does not automatically share the including build's
`gradle/libs.versions.toml` version catalog or its `org.gradle.toolchains.foojay-resolver-convention`
settings plugin (`settings.gradle.kts:9`, above) — each included build resolves its own catalog and
toolchain unless the design deliberately wires them together, and `:core`'s own `build.gradle.kts`
already depends on the root catalog via `alias(libs.plugins.kotlin.jvm)` (re-verified), so a library
extracted to its own build needs an answer for where its plugin versions come from. Configuration
cache is already on (above) and stays a constraint on whatever wiring is chosen. Separately, at the
source level the extractable NFC subset is dependency-clean: `NdefCodec.kt` imports only `TagId` (the
app's own model), `java.nio.ByteBuffer` and `java.util.UUID` (re-verified) — no coroutines, no
serialization — even though `:core`'s `build.gradle.kts` declares coroutines-core and
kotlinx-serialization-json for the rest of the module. A `nfc-tag-core` JVM module built from just
that subset needs none of them.

### 4.5 Android module configuration (`:app`)

| Field | Value |
|---|---|
| `namespace` / `applicationId` | `com.loosecannon.notenfc` |
| `compileSdk` / `targetSdk` / `minSdk` | 37 / 36 / 26 |
| `versionCode` / `versionName` | 6 / `"2.4"` |
| `testInstrumentationRunner` | `androidx.test.runner.AndroidJUnitRunner` |
| Java/Kotlin target | 17 (both `compileOptions` and `kotlin { compilerOptions }`) |
| `buildFeatures` | `compose = true`, `buildConfig = true` |
| `buildTypes` | only `release` configured explicitly (`isMinifyEnabled = false`, `proguard-android-optimize.txt` + `proguard-rules.pro`, signed only if a signing config resolved). `debug` uses AGP's implicit default plus the `app/src/debug` source set. |
| Signing mechanism (mechanism only) | a `Properties` file is loaded at configuration time from `System.getProperty("user.home") + "/.config/notenfc/keystore.properties"`; if present and non-empty a `release` signing config is built from `storeFile`/`storePassword`/`keyAlias`/`keyPassword` and wired onto `release`. Absent → the release build type has no signing config. |
| Room schema export | Room 3.x DSL, `room3 { schemaDirectory("$projectDir/schemas") }`. `app/schemas/com.loosecannon.notenfc.data.room.AppDatabase/` holds `1.json` … `5.json` (re-verified) — one behind `versionCode` 6, consistent with 6 being a non-schema release. |
| Permissions | `android.permission.NFC` and `<uses-feature android:name="android.hardware.nfc" android:required="true" />` — **NFC is a hard requirement**. No other permission is declared (`POST_NOTIFICATIONS` is future work, issue #24). |
| `<queries>` | `VIEW` intents for schemes `joplin`, `obsidian`, `logseq`, `http`, `https`, plus a `content` + `*/*` `VIEW` query for "open with" on attachments. No `<package>` elements. |

The signed rollback artefact for this checkpoint (2.4 / versionCode 6, built from `ac523d7`) is
preserved outside the repository under `~/Documents/Projects/AndroidStudioProjects/noteNFC-releases/`;
its signer DN is `CN=noteNFC, O=GonzRon` and its certificate SHA-256 is recorded in the checkpoint
ledger. The keystore and its properties file live under `~/.config/notenfc/` at mode 0600, have never
been printed, committed or rotated.

### 4.6 Every identity-carrying location

**Scale.** `com.loosecannon.notenfc` occurs **2 028 times** across the `.kt`/`.xml`/`.kts`/`.toml`/`.pro`
files of `app/`, `core/` and `gradle/` (re-verified exactly; see §9 discrepancy D4 for the file
count). It is the namespace of the entire codebase — 233 Kotlin files plus both manifests and
`app/build.gradle.kts` — not a handful of call sites. Package roots:
`app/src/{main,debug,test,androidTest}/kotlin/com/loosecannon/notenfc/...` and
`core/src/{main,test}/kotlin/com/loosecannon/notenfc/core/...`.

| Carrier | Location | Value / note |
|---|---|---|
| **NDEF external type, v1** | `core/.../core/nfc/NdefCodec.kt` `V1_TYPE` | `com.loosecannon.notenfc:tag` — the bytes physical tags are written with |
| **NDEF external type, legacy** | same file, `LEGACY_TYPE` | `com.loosecannon.notenfc:md5_short` — decode-only |
| `NdefCodec.DOMAIN` | `NdefCodec.kt:37` | `"com.loosecannon.notenfc"` — the NFC Forum external-type **domain** |
| `NdefCodec.V1_TYPE_NAME` | `NdefCodec.kt:39` | `"tag"` |
| `NdefCodec.PACKAGE_NAME` | `NdefCodec.kt:46` | `"com.loosecannon.notenfc"` — the Android **applicationId** embedded in the AAR payload; doc comment says *"must equal the app's applicationId (D13 §4)"*, enforced by nothing but that comment |
| **Manifest path literals, unlinked** | `app/src/main/AndroidManifest.xml:87, :92` | `android:path="/com.loosecannon.notenfc:tag"` and `.../com.loosecannon.notenfc:md5_short`. **There is no build-time link between these strings and the Kotlin constants** — they are independent literals that must match byte for byte. |
| **Manifest `android:name` literals, fully qualified** | `app/src/main/AndroidManifest.xml` (4: the `<application>` element plus `MainActivity`, `ShareActivity`, `NfcDispatchActivity`) and `app/src/debug/AndroidManifest.xml` (1: `DebugBackupActivity`) — 5 total, re-verified | Every component name is spelled out in full (e.g. `com.loosecannon.notenfc.MainActivity`), never as a `namespace`-relative `.MainActivity` shorthand. None of the 5 is derived from `namespace`; a rename must edit all 5 literals by hand. |
| `rootProject.name` | `settings.gradle.kts:20` | `"noteNFC"` — a literal Gradle project name, independent of `applicationId`/`namespace`; not identity-linked and safe to rename separately |
| Launcher icon | `app/src/main/res/mipmap-*/ic_launcher*.webp`, `mipmap-anydpi/ic_launcher*.xml` (adaptive icon), `drawable/ic_launcher_{background,foreground}.xml` | A custom adaptive icon (not the stock Android robot); carries no textual identity, but is a **visual** identity carrier — see §8.1 Q1 on distinct launcher icons for coexistence. |
| **FileProvider authority** | manifest `:99` + `app/.../di/AppGraph.kt:140` | `android:authorities="${applicationId}.files"` / `"${BuildConfig.APPLICATION_ID}.files"` → `com.loosecannon.notenfc.files`. Both already derive from the applicationId, so this follows a rename automatically. `AppGraph.kt:140` is the **only** live use of `BuildConfig.APPLICATION_ID`. |
| **`notenfc://` scheme literal in source — 8 files** (re-verified) | `core/.../core/nfc/TagRoute.kt`, `core/.../core/links/DeepLinkRoute.kt`, and 6 `androidTest` files (`AppSmokeTest`, `AssetModelDeviceProofTest`, `AttachmentsDeviceProofTest`, `EditorsDeviceProofTest`, `JournalDeviceProofTest`, `JournalSmokeTest`) | plus the manifest's structured `<data android:scheme="notenfc" android:host="asset|link|tag" />` at `:56-58` |
| **`app_name`** | `app/src/main/res/values/strings.xml:2` | `<string name="app_name">noteNFC</string>` — the launcher label. One of only two user-facing "noteNFC" texts repo-wide; the other is `android:label="noteNFC Backup (debug)"` in `app/src/debug/AndroidManifest.xml`. |
| **`Theme.NoteNfc`** | `app/src/main/res/values/themes.xml:6` | parent `android:Theme.Material.NoActionBar`; referenced from both activities |
| **Two classes both named `NoteNfcApp`** | `app/src/main/kotlin/com/loosecannon/notenfc/NoteNfcApp.kt` (the `Application` subclass) and `app/src/main/kotlin/com/loosecannon/notenfc/ui/nav/NoteNfcApp.kt` (the nav-root composable) | Same simple name, different package and purpose — a naming collision to resolve during any rename |
| **`notenfc.db`** | `app/.../di/AppGraph.kt:201`, `const val DB_NAME = "notenfc.db"` | A literal, **not** derived from `applicationId`; opened via `getDatabasePath(DB_NAME)` |
| **Export file-name prefixes** | `app/.../backup/SafBackupSetIO.kt:65,67` (`BackupSetNames`) | `"noteNFC-data-$stamp.zip"` / `"noteNFC-artifacts-$stamp.zip"`. Cosmetic — never read back by the importer — but hard-coded in tests |
| **Schema export directory** | `app/schemas/com.loosecannon.notenfc.data.room.AppDatabase/` | Derived from the `AppDatabase` FQN; a renamed package exports to a different directory, so schema history does not carry over automatically |
| **The `"V1"` literal** | `app/.../MainActivity.kt:84` | `Route.TagResult("V1", payload.tagId.value)` — hard-codes the string rather than `PayloadFormat.V1.name`; the trampoline↔renderer wire vocabulary (`"V1"`, `"NONE"`, `"LEGACY_MD5"`) is string-typed |
| `shared_prefs` file name | `app/.../AppPrefs.kt:13` | `"notenfc"` → `shared_prefs/notenfc.xml`; package-scoped by Android regardless of the literal |
| `BackupManifest.appVersion` | `core/.../backup/BackupFormat.kt`, wired from `BuildConfig.VERSION_NAME` at `AppGraph.kt:151` | `"2.4"` — a bare semver string, **carries no package identity** |
| `BuildConfig.VERSION_NAME`, second use | `app/.../ui/settings/SettingsScreen.kt:241` (re-verified) | Displayed to the user as the "Version" row in Settings → About; same bare semver, no package identity — the only other live use of `BuildConfig.VERSION_NAME` besides `AppGraph.kt:151` |
| Signer identity in docs | `README.md:121-125` (re-verified) | Carries the release signer's DN (`CN=noteNFC, O=GonzRon`) and the full colon-separated SHA-256 certificate fingerprint, quoted rather than merely referenced (contrast `docs/design/phase-0-evidence.md`, §2.8) |
| Notification channels | — | **none exist yet**; no `NotificationChannel`/`CHANNEL_ID` anywhere. Issue #21's local-reminder feature is unimplemented, so there is no channel id to migrate. |
| `app/src/debug` source set | **3 files** (re-verified) — `AndroidManifest.xml`, `res/layout/activity_debug_backup.xml`, `kotlin/.../debug/DebugBackupActivity.kt` under `com.loosecannon.notenfc.debug` | debug-only exported launcher activity |
| androidTest package id | `app/src/androidTest/kotlin/com/loosecannon/notenfc/...` (9 test files) | no `applicationIdSuffix`, so the test APK is `com.loosecannon.notenfc.test` by AGP default, declared nowhere in source |

### 4.7 Issue backlog classification

36 issues, 35 open / 1 closed, **no labels applied to any of them**. Classification was done by
reading each title and, for the eleven ambiguous ones, the full body — not by keyword-matching
"noteNFC" in titles, because most ServiceTag issues still say "noteNFC" as the current app name.

**Counts: ServiceTag 27 · noteNFC 2 · shared-NFC-or-infra 6 · historical-closed 1. Total 36.**
(Corrected from an earlier 26/2/7/36 split: **#20** belongs to ServiceTag, not the shared bucket —
see below.)

| Bucket | Issues |
|---|---|
| **noteNFC (2)** | **#6** "[MVP] Generalize external note/deep-link support beyond Joplin" — body: *"Preserve noteNFC's original purpose while making external-link handling generic"*. **#36** "[FUTURE noteNFC] First-class deep-link support for Joplin, Obsidian, Logseq, Evernote, Notion, OneNote and Todoist" — body is the explicit ownership statement: *"This issue belongs to the future standalone noteNFC product, not ServiceTag. The current repository is temporarily carrying both lineages… move/transfer this issue to the standalone noteNFC repository."* This is also the primary-source confirmation that the maintenance tracker's product name is **ServiceTag**. |
| **shared-NFC, genuinely dual-purpose (4)** | **#1** "[EPIC] Evolve noteNFC into an NFC-first maintenance tracker" (umbrella); **#30** "Bind, rebind, revoke, and unknown-tag flows" (body covers binding a tag "to an asset **or link**"); **#31** "noteNFC tag payload format v1 and legacy md5_short resolver" (the payload format both lineages read); **#35** "Untrusted input policy: tag payloads, deep links, stored URIs" (spans both). |
| **shared, but generic build/test/design infra rather than NFC (2)** | **#23** "Phase 0: clone-buildable repo, AGP 9 toolchain, :core module, CI"; **#32** "Testing pyramid and CI gates". |
| **ServiceTag (27)** | #2–#5, #7–#18, #19, #20, #21, #22, #24–#28, #33, #34 — Room persistence, event journal, scheduling engine, dashboard, attachments, backup/export/import, Todoist integration and auth, notification quick actions, measurement/event profiles, seasonal windows, consumables, asset templates, calendar-date semantics, local reminder provider, meter model, platform permissions, provider selection, reminder fatigue, reminder health, `ReminderProvider` port. **#20** "Phase 1C: Compose design system foundation (Apollo Service Binder)" moves here — the Apollo Service Binder theme is ServiceTag's own Compose shell (§3, Phase 1C), not shared infra. |
| **closed (1)** | **#29** "[MVP] Investigate the installed APK's signing certificate and upgrade path" — the historical record of the unavailable legacy key. |

**"Shared" does not mean "belongs in `nfc-tag-core`".** Even the four genuinely dual-purpose issues
above are mixed policy/mechanism — #30's rebind flow and #31's payload-format-plus-legacy-resolver
both reach into ServiceTag-only concepts (§6.1). A shared issue is split between the two products'
trackers or stays in ServiceTag as history; being "shared" is a classification of the *issue*, not a
routing instruction to the library.

Two judgment calls worth carrying into the design:

- **#19** ("`notenfc://` deep-link contract") reads shared by name, but its scope is entirely `notenfc://asset/<uuid>`, `.../schedule/<uuid>`, `.../event/<uuid>` — ServiceTag domain objects. Classified ServiceTag despite the legacy scheme name.
- **#34** ("Todoist deep-link actions into noteNFC") says "noteNFC" in the title but its body is about a Todoist task landing on "the exact maintenance operation". ServiceTag.

### 4.8 Releases and tags

One GitHub release, the 2023 draft described in §2.10 (untagged, unanchored, no assets, never
published). One git tag, `pre-split-checkpoint` → `ac523d7` — a ServiceTag-state marker created for
this split, **not** a narrow-product marker.

### 4.9 Docs inventory

Everything under `docs/` is a historical design-process record of the **ServiceTag** line. It is
neither edited nor reinterpreted here. Shape only: `docs/design/` holds `README.md` plus the numbered
design documents D1–D13 (`01-current-state-archaeology.md` through
`13-compatibility-policy.md` — the last being "Compatibility policy change and package identity", the
direct precedent for a rename/split), eight per-phase evidence files
(`phase-{0,1a,1b,1c,2a,2b1,2b2,4a}-evidence.md`), a `g1/` visual-gate pair — including
`g1/00-source-data-inventory.md`, the census of the owner's eight physical legacy tags (§7.5) — two
`spikes/` reports (S1 toolchain, S5 SAF tree providers), and an `issues/` package (`applied.md`,
**19** new-issue drafts (re-verified; corrected from 17), 16 verbatim originals, 16 rewritten bodies).
`docs/superpowers/` holds 8 per-phase plans and 4 design specs. The repo-root `README.md` already
describes the **merged** noteNFC+ServiceTag scope, not a split state.

---

## 5. NFC behaviour inventory

### 5.1 Manifest NFC surface

`app/src/main/AndroidManifest.xml` is 108 lines and is the only manifest with NFC content (the debug
source set adds one MAIN/LAUNCHER activity and no NFC).

**Every NFC action filter in the app — two, both on one activity:**

```
:78-94  <activity android:name=".nfc.NfcDispatchActivity"
                  android:exported="true" android:launchMode="singleTop"
                  android:excludeFromRecents="true"
                  android:theme="@android:style/Theme.Translucent.NoTitleBar">
  :84-88  NDEF_DISCOVERED + category DEFAULT
          data scheme="vnd.android.nfc" host="ext" path="/com.loosecannon.notenfc:tag"
  :89-93  NDEF_DISCOVERED + category DEFAULT
          data scheme="vnd.android.nfc" host="ext" path="/com.loosecannon.notenfc:md5_short"
```

- `android:path` is an **exact** match, not `pathPrefix` as the Android docs' example uses. A type string that merely *starts with* `com.loosecannon.notenfc:tag` does not match, and the two filters are mutually exclusive. This is the stricter and correct choice.
- The design rule is stated in the manifest comment at `:76-77`: *"The only NFC-exported component (D3 section 9). No tech-discovered catch-all filter."*
- **No `TECH_DISCOVERED` filter, no `TAG_DISCOVERED` filter, no `nfc_tech_filter` resource.** `app/src/main/res/xml/` holds only `file_paths.xml`. The 2024 app had both a TECH catch-all and `res/xml/nfc_tech_filter.xml`; both were deleted in `26ec9d0` and D13 recorded the decision.
- **`DISPATCH_NFC_MESSAGE` is not declared** — the Android 17 requirement applies only when the app targets SDK > BAKLAVA, and `targetSdk = 36`. D3 §9 schedules it for targetSdk 37 (Phase 7).

The other two exported activities have no NFC filter: `MainActivity` (`singleTask`; MAIN/LAUNCHER
plus VIEW + DEFAULT + BROWSABLE on `notenfc://asset|link|tag`) and `ShareActivity` (`ACTION_SEND` +
`text/plain`, own task, `excludeFromRecents`).

### 5.2 Record formats — exact byte layouts

All in `core/src/main/kotlin/com/loosecannon/notenfc/core/nfc/NdefCodec.kt` (121 lines).

| Constant | Line | Value |
|---|---|---|
| `TNF_EXTERNAL_TYPE` | 34 | `0x04` |
| `DOMAIN` | 37 | `com.loosecannon.notenfc` |
| `V1_TYPE_NAME` / `V1_TYPE` | 39 / 40 | `tag` / `com.loosecannon.notenfc:tag` |
| `V1_VERSION` / `V1_FLAGS` / `V1_PAYLOAD_LENGTH` | 41 / 42 / 43 | `0x01` / `0x00` / `18` |
| `PACKAGE_NAME` / `AAR_TYPE` | 46 / 47 | `com.loosecannon.notenfc` / `android.com:pkg` |
| `LEGACY_TYPE_NAME` / `LEGACY_TYPE` | 50 / 51 | `md5_short` / `com.loosecannon.notenfc:md5_short` |

`DOMAIN` and `PACKAGE_NAME` are the **same literal today but are two different things** — an NFC
Forum external-type domain and an Android package name. They must be parameterised separately.

**Payload format v1:**

```
offset  size  content
0       1     version = 0x01
1       1     flags   = 0x00        (reserved; any non-zero value is Malformed)
2       8     UUID.mostSignificantBits   (big-endian, ByteBuffer.putLong)
10      8     UUID.leastSignificantBits  (big-endian)
                                     total = 18 bytes
```

Encoder `v1Record` (`:93-102`) emits `NdefRecordData(0x04, "com.loosecannon.notenfc:tag" ASCII,
payload)`. **[JVM-proven]** byte-for-byte by `NdefCodecV1Test` (tnf, type ASCII compare, payload size
18, `byteArrayOf(0x01, 0x00) + idBytes`). Decoder `decodeV1` (`:67-80`) checks, in order: empty →
`Malformed("empty :tag payload")`; `version > V1_VERSION` → `NewerVersion(version)`; `version !=
V1_VERSION` → `Malformed`; size != 18 → `Malformed`; flags != 0 → `Malformed`; else
`UUID(bb.long, bb.long)` → `TagPayload.V1(TagId(uuid.toString()))`. `requireCanonicalUuid`
(`:112-120`) refuses a non-UUID and refuses a UUID that is not canonical lower-case, *"so that
`nfc_tag.id == payload_key` (D4 §3)"*.

**Legacy `md5_short`:** the whole payload is the key. `decodeLegacy` (`:82-88`) does
`String(payload, UTF_8)` and requires `^[0-9a-f]{8}$`; anything else is `Malformed("legacy payload is
not 8 lowercase hex chars: '…'")`. **8 ASCII lower-case hex characters, 8 bytes, no version byte, no
flags, no framing. There is no encoder — the type is decode-only.**

**What each payload carries:**

| Era | Carries |
|---|---|
| Legacy (2023–2024) | `MD5(the shared text)[0:8]` — a hash of *the shared text*, not of a note id. If the share sheet sent "title + link", the hash covered both. |
| v1 (ServiceTag era) | **A random tag id and nothing else.** No asset id, no link id, no note id. `ProvisionTag` mints `id = ids.newId()` and sets `payloadFormat = V1`, `payloadKey = id`. |

The v1 tag is pure identity; what it *means* lives in the `nfc_tag` row (`TagBinding.target`). **That
is the property the whole split depends on: the tag format knows nothing about assets or links.**

**Every format that has ever been on the wire:**

| Format | Type string | Introduced | Status today |
|---|---|---|---|
| `uuid8_link` | `com.loosecannon.evernotenfc:uuid8_link` | `5fb6aed` (2023-08-05) | gone; decodes as `Foreign` |
| `md5_short` (Evernote domain) | `com.loosecannon.evernotenfc:md5_short` | `d88b84d` (2023-08-07) | `Foreign` — **[JVM-proven]** by `NdefCodecTest.evernoteEraTypeIsForeign` |
| `md5_short` (noteNFC domain) | `com.loosecannon.notenfc:md5_short` | `f03d833` (2023-08-09) → 2024 release | decode-only, retained |
| `:tag` v1 | `com.loosecannon.notenfc:tag` | `f92a391` (2026-09-14) | the only format written |

There is exactly one "later" format — v1. **There has never been a v2.**

`PayloadFormat { LEGACY_MD5, V1 }` (`core/.../core/model/TagBinding.kt:3`) is the **row** enum, not
the wire enum: it pairs with `payloadKey` to form the lookup identity, unique on
`(payload_format, payload_key)`. The **wire** vocabulary is the `TagPayload` sealed interface plus
the version byte: `V1(TagId)`, `LegacyMd5(key)`, `NewerVersion(version)`, `Foreign(description)`,
`Malformed(reason)`, `Empty`. `FORMAT_NONE = "NONE"` (`ScanViewModels.kt:41`) is a third, UI-only
value meaning *"this tag is not ours, and here is why"*, and when it is in play the accompanying
`key` is prose, not an id.

**Version negotiation** is by the version byte alone: `> 0x01` → `NewerVersion(version)`, never
parsed, any length; `== 0x01` → strict parse; otherwise `Malformed`. **[JVM-proven]** with `0x02`
(full 16-byte id), `0x7f` (3-byte payload) and `0xff` (1-byte payload) all yielding `NewerVersion`,
and version 0 yielding `Malformed`. `NewerVersion` surfaces as *"written by a newer noteNFC (payload
format n)"* and on the write screen as `Confirm("a noteNFC tag written by a newer app (format n)")`.
It is never adopted and never overwritten silently.

**Forward-compatibility rules actually implemented** (all **[JVM-proven]**):

1. **First record of the first message only** (`:56-57`), matching the platform. Extra records are ignored. This is what lets the AAR sit second and lets a future writer append records.
2. **TNF gate before the type switch** — a `:tag` type under the wrong TNF is `Foreign`, not `Malformed`.
3. **Unknown external type → `Foreign`, never an attempt to parse the body** (`:63`). *This is the single rule that will keep the two post-split products from adopting each other's tags.*
4. **Reserved flags byte** — a whole byte of headroom for a v1.x; non-zero today is `Malformed`.
5. **Exact length for v1** — 18 bytes or it is not a v1 tag.
6. **Encode strict, decode canonicalising** — encode refuses a non-canonical UUID; decode always yields the canonical form because it round-trips through `java.util.UUID`.

### 5.3 Android Application Record

- **An AAR is written, always, for v1.** `encodeV1` is the only message builder and returns `listOf(v1Record(tagId), applicationRecord())` — *"the `:tag` record first, the AAR second (D4 §3)"*.
- `applicationRecord()` = tnf `0x04`, type `android.com:pkg` ASCII, payload `com.loosecannon.notenfc` ASCII; byte-identical to `NdefRecord.createApplicationRecord(PACKAGE_NAME)`. **The AAR package equals the applicationId** — by doc comment only, not by build-time derivation.
- **Position: second**, **[JVM-proven]** (`messageIsTagRecordThenApplicationRecord`: size 2, `msg[0] == v1Record(id)`, `msg[1]` type `android.com:pkg` with the package payload). **[platform-doc]**: *"You do not want to use the first record of your NdefMessage, unless the AAR is the only record… because the Android system checks the first record… to determine the MIME type or URI of the tag."* Put the AAR first and the tag stops matching the `NDEF_DISCOVERED` filter.
- An AAR-only message decodes as `Foreign` **[JVM-proven]**.
- **[device-observed]** (1B row 5): with the task removed from recents and the process killed, the NFC service logged *"matched AAR to NDEF"* and started `NfcDispatchActivity`, which showed the asset — i.e. the filter-selected activity also matched the AAR and the normal intent path was used.
- **[unobserved]**: behaviour on a device *without* the app. **[platform-doc]** says the platform goes to Google Play for the AAR's package; `com.loosecannon.notenfc` is not published there, so the expected outcome is a Play page for a non-existent listing.

### 5.4 Deep links

Parsers live in `:core` and are Android-free.

- `core/.../core/nfc/TagRoute.kt` (22 lines): `SCHEME = "notenfc"`, `HOST = "tag"`, canonical-UUID regex. `parse(scheme, host, pathSegments)` returns **null** when it is not this route at all; `Malformed("notenfc://tag needs exactly one path segment, got n")` on a wrong segment count; `Malformed("not a tag id: '…'")` on a non-canonical UUID; else `TagPayload.V1(TagId(id))`. Doc: *"resolves exactly as if the tag had been scanned… Navigation only; the id is validated by shape here and by existence in `ResolveTag`."*
- `core/.../core/links/DeepLinkRoute.kt` (33 lines): `DeepLink` = `Asset(AssetId) | Link(LinkId) | Tag(TagPayload) | Malformed(reason)`; `SCHEME = "notenfc"`; returns null for a foreign scheme or unknown host; **delegates** the `tag` host to `TagRoute.parse`; `asset` and `link` each require exactly one canonical-UUID segment.
- Consumers: `MainActivity.routeFrom` reads the two trampoline extras **first**, then `ACTION_VIEW` + `intent.data` → `DeepLinkRoute.parse` → `Route.AssetDetail` / `Route.LinkDetail` / `Route.TagResult("V1", uuid)`; anything else emits the snackbar *"That link doesn't point at anything here."* and returns null. Wrapped in `safeRouteFrom` because an exported launcher activity can be handed hostile extras. `NfcDispatchActivity` also answers `ACTION_VIEW` but **only** through `TagRoute` — it has no `VIEW` filter, so it sees such an intent only if something targets it explicitly. The Compose shell receives routes only through `MainActivity.deepLinks`, a `MutableSharedFlow<Route>(replay = 1)`; the replay exists because `onCreate` emits before the first composition subscribes.

### 5.5 Reader mode

`app/.../nfc/NfcReaderModeSession.kt`, 39 lines, is the whole of it.

- **Flags: `FLAG_READER_NFC_A or _B or _F or _V`** (re-verified). **`FLAG_READER_SKIP_NDEF_CHECK` is deliberately absent** — re-verified: the string appears nowhere in `app/` or `core/`. The class doc states why: *"The platform's NDEF check is deliberately left ON: it is what makes `Ndef` and `NdefFormatable` available on the delivered `Tag`, so skipping it would leave nothing to write to."* **[platform-doc]** confirms the flag *"will prevent the `Ndef` tag technology from being enumerated on the tag, and… NDEF-based tag dispatch will not be functional."* It was present and removed in `e2cf1d0`, recorded as final review B-1 in `phase-1b-evidence.md` — **device-proven negatively** (with it set, `Ndef.get()` was null and nothing could be written).
- No extras `Bundle` is passed, so there is no presence-check delay tuning.
- Even with the platform check on, the writer does not trust the platform's cached message: `TagWriter.inspect` does a fresh `Ndef.getNdefMessage()`.
- `available` / `enabled` getters drive the availability sentence on both screens.
- **Lifecycle: per Compose screen, not per activity.** Both consumers use the identical shape — `remember(activity) { NfcReaderModeSession(host) { tag -> model.onTag(NfcTagHandle(tag)) } }` inside `LifecycleResumeEffect { session?.start(); onPauseOrDispose { session?.stop() } }`. Start on RESUMED, stop on PAUSE or dispose, because *"reader mode belongs to the resumed screen and to nothing else: leaving this screen hands NFC back to the system, which is what lets the background trampoline keep working."*
- The `ReaderCallback` lambda runs *"on a platform binder/background thread, never the main thread"*. The screen wraps the raw `Tag` in `NfcTagHandle`; the raw `android.nfc.Tag` comes back out in exactly one place, `RealTagIo`, which `error()`s on any other handle type.

### 5.6 Write path

Two layers, split on purpose.

**`app/.../nfc/TagWriter.kt` (126 lines)** — the blocking Android I/O. Its class doc states the
contract: read-first, write, read-back (D3 §9); every function blocks on tag I/O and must be called
from a worker thread; decisions (overwrite? which target?) are made by the caller between `inspect`
and `write`, while the tag stays in the field; and **the two halves report failure differently on
purpose** — `write` never throws for tag I/O and folds every such failure into `Failed`, while
`inspect` lets it propagate (made explicit in `bdcc475`).

- `TagInspection`: `uid` (lower-case hex or null), `existing: TagPayload`, `existingRecords: List<NdefRecordData>`, `maxSize` (`Ndef.maxSize`, or **-1** for a tag that still needs formatting — capacity unknown until then), `writable`, `needsFormat`, `canLock`.
- `WriteResult`: `Written(readBack, bytes, verified, locked)`, `TooSmall(maxSize, needed)`, `ReadOnly`, `Unsupported`, `VerifyMismatch(readBack)`, `Failed(reason)`.
- `inspect(tag)`: `Ndef.get` → `connect()` → fresh `ndef.ndefMessage`; a `FormatException` becomes `TagInspection(uid, Malformed("NDEF on tag could not be parsed"), …)` rather than an exception; `finally { runCatching { ndef.close() } }`. If not `Ndef`, `NdefFormatable.get(tag)` → an `Empty`/`needsFormat`/`maxSize = -1` inspection. Neither → `null`.

**`app/.../ui/scan/TagWriteController.kt` (299 lines)** — the decision layer behind the `TagIo` seam,
so that *"read first, ask before overwriting, verify, lock last"* is JVM-testable. `TagHandle`
exposes only `uid`, because reader mode hands out an `android.nfc.Tag`, which no JVM test can build.

**The sequence:**

1. **Tap arrives** on a binder thread → `onTag(handle)`. If `busy` or `done`, the tap is dropped (**single-flight**, `@Volatile busy`). Any escaping exception becomes `WriteState.Error`.
2. **Provision the row once**: `pending ?: provisionTag.begin(target, label)` — the same row is reused for every retry. `begin` validates that the target row exists, mints the id, and writes `payloadFormat = V1`, `payloadKey = id`, `status = ACTIVE` (or `UNBOUND` when the target is `None`).
3. **Encode the intended message**: `NdefCodec.encodeV1(row.id)` — `:tag` then AAR.
4. **Read before write**: `io.inspect(tag)` on the IO dispatcher. `null` → "does not support NDEF"; a pending `awaitingVerify` jumps to step 8; `!writable` → "read-only (locked). Nothing written."
5. **Honour a remembered consent** — the stale-handle protocol, below.
6. **Overwrite decision** via `OverwritePolicy.decide(existing, row.id)`: `Proceed` **only** for `Empty` or a `V1` payload whose `tagId` equals the intended id (a retry). Everything else is `Confirm(reason)`, the reason shown verbatim — *"a different noteNFC tag"*, *"a legacy noteNFC tag"*, *"written by a newer app (format n)"*, *"foreign NDEF content"*, *"unreadable NDEF content"*. On `Confirm` the controller parks a `PendingWrite` and hands ownership of `busy` to the sheet.
7. **Write** (`needed` = the **serialised message** size, not the payload). *`Ndef` path*: `connect`; `!isWritable` → `ReadOnly`; `maxSize < needed` → `TooSmall`; `writeNdefMessage`; **re-read**; `back != records` → `VerifyMismatch`; then and only then `if (lock && canMakeReadOnly()) makeReadOnly()`; `Written(verified = true)`. `TagLostException` → `Failed("tag left the field")`, `IOException`/`FormatException` → `Failed(...)`; `finally` closes. *`NdefFormatable` path*: `connect`; `format(message)`; **no verification and no lock**, because `Ndef.get(tag)` stays null until the tag is rediscovered — *"verification is the next tap's job — and so is the lock: the tag is formatted unlocked and `TagWriter.lock` is applied only after that second tap's verification, never blind."* → `Written(verified = false, locked = false)`. Neither tech → `Unsupported`.
8. **Second tap verifies a formatted tag**: `inspection.existingRecords == intended`; on a match, lock if armed and possible, then finish; on a mismatch, *"Read-back differs…"*
9. **Finish**: `provisionTag.complete(row.id, uid)` stamps `writtenAt` and the physical UID; `WriteState.Written(tagId, locked)`.
10. **Abandon**: `abandonIfUnwritten()` runs on an app-scoped coroutine that outlives the screen, and `ProvisionTag.abandon` deletes the row only when `writtenAt == null` — **so no phantom tag row survives a cancelled write.**

**The stale-`Tag`-handle protocol** (`confirmedOverwrite: TagPayload?`, commit `0e1975f`). The
handle captured before the confirmation sheet can go stale while the sheet is up — the NFC service
re-discovers the tag and then refuses the old handle with "Tag is out of date", seen on an Android 17
phone — so **a confirmation is remembered as consent for *that content*** and honoured on the next tap
of a tag carrying it. `confirmOverwrite()` records the consent and tries the parked handle
immediately; if that throws, the wording is *"Overwrite confirmed, but the tag was lost (…). Hold it
to the phone again to finish."* On the next tap the remembered consent is compared with the freshly
inspected content and the write proceeds without asking again; a *different* payload clears the
consent. `keepIt()` clears both — *"Not written. The tag was left as it was."* — which a dismissed
sheet also means. **[device-observed]** (`phase-1b-evidence.md`): on the pre-fix build the first
Overwrite failed with "Tag is out of date"; nothing was written and the provisioned row was
abandoned.

### 5.7 Capacity check

**Exactly one runtime check**, `TagWriter.kt:77`: `if (ndef.maxSize < needed) return
WriteResult.TooSmall(ndef.maxSize, needed)`, where `needed` is the **serialised NDEF message** size,
header and all. It is evaluated after `connect()` and after the `isWritable` check, so a read-only
tag reports `ReadOnly` rather than `TooSmall`.

**No capacity check on the `NdefFormatable` path — a real (small) gap.** Capacity is unknown until a
tag is formatted: `inspect` reports `maxSize = -1`, and `format(message)` either succeeds or throws,
folding into `Failed("tag could not be formatted: …")`. **A too-small unformatted tag therefore
surfaces as a generic failure, not as `TooSmall`.**

A design-time budget is asserted in the JVM suite rather than at runtime: `NdefCodecV1Test` computes
`sumOf { 3 + type.size + payload.size } + 3` and asserts `<= 144` (NTAG213). For v1 that is
`(3 + 27 + 18) + (3 + 15 + 23) + 3 = 92` bytes — 48 for the `:tag` record, 41 for the AAR, 3 for TLV
plus terminator — leaving ~52 bytes of headroom: enough for one more small record, not much more.

**Error mapping** (`TagWriteController.kt:229-244`):

| `WriteResult` | Message shown |
|---|---|
| `TooSmall(maxSize, needed)` | "Tag too small: it holds *maxSize* bytes, the message needs *needed*." |
| `ReadOnly` | "This tag is read-only (locked). Nothing written." |
| `Unsupported` | "This tag does not support NDEF." |
| `VerifyMismatch` | "Read-back differs from what was written. Nothing recorded — try again." |
| `Failed(reason)`, no consent pending | "Write failed: *reason*\nHold the tag still and try again." |
| `Failed(reason)`, after a confirmed overwrite | "Overwrite confirmed, but the write did not go through (*reason*).\nLift the tag off and hold it to the phone again to finish." |
| `Written(verified = false)` | `Verifying("Formatted and written (*n* bytes). Lift the tag off, then hold it again to verify the read-back.")` |
| `null` inspection | "This tag does not support NDEF. Use an NTAG213/215/216 or similar." |

### 5.8 Read-back verification — what is compared

**Structural record data, not raw tag bytes.** `val back = ndef.ndefMessage.toRecordData(); if (back
!= records) return WriteResult.VerifyMismatch(back)`. Both sides are `List<NdefRecordData>`, and
`NdefRecordData.equals` compares `tnf` plus `type.contentEquals` plus `payload.contentEquals`. So the
comparison covers the number and order of records, each record's TNF, each record's full type string
and each record's full payload bytes — the `:tag` record's 18 bytes *and* the AAR's package string,
in that order.

What is **not** compared:

- The NDEF record **id field** — `NdefBridge.toNdefMessage` always writes `ByteArray(0)` and `toRecordData` never reads it back, so an id a device invented would go unnoticed.
- The **TLV framing / terminator / chunking** — a controller that re-encoded a short record as a chunked one would still compare equal, because the comparison happens after the platform has re-parsed the message. This is the right level: the intent is "the tag holds the records I meant", not "the tag holds the bytes I sent".
- The **payload is not re-decoded** through `NdefCodec` for the comparison; raw bytes are compared. `TagPayload` is used only for the *pre*-write overwrite decision.

The second-tap verification of a formatted tag uses the same list equality against freshly inspected
records.

### 5.9 Resolver

`core/.../core/usecase/ResolveTag.kt` (58 lines). `Resolution` is *"Every way a scan can end (D3 §9).
The UI switches on this and nothing else."*: `OpenAsset`, `LaunchLink`, `Unbound`, `Revoked`,
`UnknownV1`, `UnknownLegacy`, `NeedsNewerApp`, `NotOurs`.

| Payload | Resolution |
|---|---|
| `V1(tagId)` | `known(V1, tagId.value)` else `UnknownV1(tagId)` |
| `LegacyMd5(key)` | `known(LEGACY_MD5, key)` else `UnknownLegacy(key)` |
| `NewerVersion(n)` | `NeedsNewerApp(n)` — no lookup |
| `Foreign` / `Malformed` / `Empty` | `NotOurs(payload)` — **no lookup, no write, no transaction** |

`known(format, key)` runs inside `uow.write { }`: look the row up **by `(format, key)`, never by row
id (D4 §3)**; stamp `lastScannedAt` and upsert — **a read scan performs a write**, so resolution is
transactional and Room-backed; `status == LOST || RETIRED` → `Revoked` (checked *before* the target);
`status == UNBOUND` → `Unbound`; else on `tag.target`: `AssetTarget` → `assets.get(id)?.let {
OpenAsset } ?: Unbound`, `LinkTarget` → `links.get(id)?.let { LaunchLink } ?: Unbound`, `None` →
`Unbound`. **A dangling target degrades to `Unbound`, not to an error.**

A legacy tag therefore resolves today to whatever its `TagBinding.target` says. **There is no
note-URL lookup and no legacy alias table** — D13: *"No lookup of old data is attempted; there is
none to look up."* With no row: `UnknownLegacy(key)` → the "Legacy tag" sheet (eyebrow *"Legacy
tag"*, sentence *"This tag uses the 2024 noteNFC identifier."*, actions **Rewrite in format v1** /
**Bind as-is** / Cancel) — *"a migration opportunity, not damaged data (D12 §11, D13 §3)."* "Bind
as-is" goes through `BindTag`, which skips the canonical-UUID check for non-V1 formats and mints a
fresh row id because an 8-hex key is not a UUID.

The live D6 contract: §3 (automatic prefs migration), §5 (re-link / bulk recovery), §6 (collision
handling) and §9 (timeline) are **dropped**; §1 (what is on physical tags), §4 (the resolver,
*"reduced to 'recognise and offer rewrite/bind'"*) and §7 (rewrite a legacy tag in v1) are **retained
as best-effort**. The still-live promise: *"The `NDEF_DISCOVERED` filter for
`vnd.android.nfc://ext/com.loosecannon.notenfc:md5_short` is kept permanently; there is no plan to
remove read support for legacy tags."* D13's cost table keeps the decoder (~20 lines + 5 tests), the
manifest line, and the `LEGACY_MD5` enum value.

### 5.10 Ambient dispatch — `NfcDispatchActivity`

119 lines; extends plain `android.app.Activity`, not `ComponentActivity` — no Compose, no lifecycle
library. Class doc: *"The one NFC-exported component (D3 §9, security doc 'NFC dispatch'). Background
scans arrive here through the two `NDEF_DISCOVERED` filters; only `EXTRA_NDEF_MESSAGES`, `EXTRA_TAG`
and the data URI are read — every other extra is ignored. It has no UI at all: a link tag launches
straight away (R-7) and everything else is handed to `MainActivity` as a (format, key) pair, so the
single activity owns every pixel the app draws. The translucent theme is what keeps a window from
flashing on the way through."*

- **Cold start**: `onCreate` → `handle(intent)`; `onNewIntent` → `setIntent` → `handle` (it is `singleTop`); `onDestroy` cancels the `MainScope`.
- **Hostile-extras guard** (`54f9aea`): *"A third-party app can aim any extras at an exported activity; on pre-33 devices the untyped `getParcelableArrayExtra` unparcels whatever it is handed, so a hostile or simply wrong bundle throws here rather than returning null. Treat it as 'nothing to resolve'."* → `Toast("Nothing to resolve.")` + `finish()`. `MainActivity.safeRouteFrom` does the same for the launcher.
- **Accepted actions — exactly two**: `ACTION_NDEF_DISCOVERED` → `NdefCodec.decode(...)`, and `ACTION_VIEW` → `TagRoute.parse(...)`. Anything else → the toast.
- **What is read**: `EXTRA_NDEF_MESSAGES`, first message only (with an SDK-33 typed/untyped split), and the data URI. `EXTRA_TAG` is *available* via `NdefBridge.nfcTag()` but **never called** — confirmed by grep and by `phase-1b-evidence.md`. **So the ambient path cannot write to a tag, by construction.**
- **UI shown: none.** Translucent theme, `excludeFromRecents`. The only visible artefact on any path is the *"Nothing to resolve."* toast.
- **Routing**: `LaunchLink` → `openLink.run(link.id)` → `LinkLauncher.open` → `finish()` — **no app screen at all (R-7)**; `Refused`/`Missing` hand off `(NONE, <prose reason>)`. Everything else → the shared `asTagResult` mapping → `handOff(format, key)`. A resolver exception → `handOff(FORMAT_NONE, "could not resolve this tag: <exception class>")` — the class name only, never the message. `handOff` is *"the trampoline's only exit"*: an explicit `Intent` to `MainActivity` with `EXTRA_TAG_FORMAT` + `EXTRA_TAG_KEY` and `FLAG_ACTIVITY_NEW_TASK`, then `finish()`. `MainActivity.routeFrom` reads those two extras **before** anything else, and the `replay = 1` flow delivers the resulting `Route.TagResult` to Compose even on a cold start. The two paths converge deliberately: *"everything else becomes the very route the foreground scanner would have produced, so the two paths say the same words about a tag."*
- **Stopped-state behaviour is contradictory and only half-resolved.** **[platform-doc]**: *"the system will not dispatch NFC intents to applications that are in a stopped state (e.g. if the application has never been launched by the user or has been force-stopped)."* **[device-observed]** (`phase-1c-evidence.md` row 16): after `am force-stop` + `pm clear`, with `dumpsys` reporting `stopped=true`, **the tap was dispatched**. The 1B observation (no dispatch after a fresh install until the first launch) therefore does not generalise to a force-stopped package on that phone; only the never-launched install was silent. 1B row 13 was inconclusive. D3 §9 records both. All at `targetSdk 36`.

### 5.11 Standalone links and the share entry point

- **`ExternalLink` can exist without an asset**: `assetId: AssetId? = null`, and `SaveLink` always creates it standalone — *"Creates a standalone link (asset attachment is a Phase 1C/2 concern). The policy gate runs here."*
- **Writing a link tag**: the link row is created first, then the write screen is entered with the link as target. `Route.WriteTag.target()` is *"the one place ids become a `TagTarget` again"*; `ProvisionTag` sets `status = ACTIVE` because the target is not `None`, and `requireTargetExists` refuses a link id that does not exist. **The tag still carries only its own UUID** — the link id never goes on the tag.
- **Resolving a link tag** → `LaunchLink`, and all three consumers show **no sheet**: ambient, foreground scan (`ScanEvent.Launch(uri)` → `LinkLauncher.open`), and the deep-link sheet route (renders nothing and dismisses). `asTagResult` deliberately `error()`s on `LaunchLink`: *"a link tag launches its note; it has no sheet (R-7)."*
- **The launch gate runs twice.** `LinkLaunchPolicy` is applied at save time by `SaveLink` and again at launch time by `OpenLink` — *"so a URI that arrives through a backup gets the same treatment as one typed in."* Allowlist `{joplin, obsidian, logseq, http, https}`; blocklist `{javascript, file, content, intent, android-app, tel, sms, mailto}`; whitespace and control characters rejected. At launch a `NeedsConfirmation` scheme is launched **only** if the stored row's kind is already `OTHER` (confirmed at save time), otherwise `Refused("scheme '…' was never confirmed")`. `LinkLauncher` catches `ActivityNotFoundException` **and** `SecurityException` and shows *"No app can open this link"* — never crashes.
- **The share entry point is `ShareActivity`**, not the 1B interim `ShareLinkActivity` (created in `25026d7`, deleted in `19042da`). `ACTION_SEND` + DEFAULT + `text/plain`, own task, `excludeFromRecents`. It reads `getCharSequenceExtra(EXTRA_TEXT)` — *"read as a `CharSequence` because that is what the contract promises; the styling is dropped, not trusted"* (`54f9aea`). `ShareFlow` hosts either `ShareCardScreen` or `WriteTagScreen` and every terminal state calls `onFinished` → `finish()`, so **Done returns to the calling app**. URI extraction is `LinkLaunchPolicy.extractUri`: the **first** `scheme://…` token in the shared text, trailing punctuation trimmed — *"the surrounding title/prose is never stored."* (Contrast the 2024 gate, which hashed the whole text — see Q8.)
- **[device-observed]** 1B rows 7–8, re-proved in 1C: share a note's external link → hold a tag → written; then, with the app closed, a tap resolved `LaunchLink` and launched the note's `VIEW` intent directly, no app screen, `last_opened_at` stamped; Done returned to the notes app.

### 5.12 Malformed and foreign handling

**Reader layer.** `inspect` turns a `FormatException` into `Malformed("NDEF on tag could not be
parsed")`, so a tag with unparseable NDEF is still inspectable and still
overwritable-with-confirmation. Neither `Ndef` nor `NdefFormatable` → `null`, which is an error on the
write screen but on the scan screen is substituted with `Malformed("this tag does not support NDEF")`
so the normal resolution path still produces a sheet. `IOException` / `TagLostException` propagate and
become *"Couldn't read that tag (<class>). Hold it still and try again."* or a `WriteState.Error`.

**Codec layer** — what the tests pin **[JVM-proven]**: empty message → `Empty`; non-external TNF →
`Foreign("tnf=… type=…")`; unknown external type → `Foreign` (including the Evernote-era type, and an
AAR-only message); `:tag` under the wrong TNF → `Foreign`, not `Malformed`; legacy key upper-case /
wrong length / non-hex → `Malformed`; v1 empty / wrong length / non-zero flags / version 0 →
`Malformed`; extra records ignored.

**Resolver layer.** `Foreign`, `Malformed` and `Empty` all collapse to `NotOurs(payload)` with no
repository lookup and no transaction. **A foreign tap never touches the database.**

**UI layer — the D12 §11 sheet set** (`TagResultSheet.kt`): *"eyebrow, one sentence, the mono
identifier, actions stacked with the filled one first. **None of them is an error**: an unregistered
tag, a legacy tag and a foreign tag are all offers, and only the wording and the glyph change."*

| `TagResult` | Sheet |
|---|---|
| `Loading` | *"Reading tag / Looking this tag up…"* |
| `OpensAsset` | *"Tag detected"*, asset name, identity line; auto-navigates via `LaunchedEffect` |
| `LaunchesLink` | **no sheet** — launches and dismisses (R-7) |
| `Unregistered` | *"Unregistered tag / This tag is not assigned to anything yet."* → Bind / Cancel |
| `Revoked` | *"Tag marked lost"* or *"Tag retired"* / *"…Binding it again puts it back to work."* → Bind / Cancel |
| `NotInRecords` | *"Unregistered tag / This noteNFC tag is not in this phone's records."* → Bind / Write a new tag over it / Cancel |
| `Legacy` | *"Legacy tag / This tag uses the 2024 noteNFC identifier."* → Rewrite in format v1 / Bind as-is / Cancel |
| `NotOurs` | *"Not a noteNFC tag / This tag holds something else."* + the reason as a quiet line → Write a new tag over it / Cancel |

Note the deliberate vocabulary collision: **two different states both say "Unregistered tag"**
(`NotInRecords` and `Unregistered`). They differ in whether a row exists, and therefore in whether
"Write a new tag over it" is offered.

Two contract details that matter for the split:

- **The `format == "NONE"` rule**: *"there is no identifier to show and `key` carries a prose reason the tag could not be read, so nothing may present it as an id or look it up as one"* — enforced in `ScanViewModels`, where an unrecognised format short-circuits to `NotOurs(key)` **without calling `ResolveTag`**.
- **The sheets are destinations, not overlays**: *"the trampoline can land on one with nothing behind it"*, so the panel is anchored to the bottom of the canvas rather than floating over a scrim. A direct consequence of the ambient-dispatch design.

**[unobserved]**: 1B row 11 (*"tap a blank/foreign tag with the app closed → nothing happens"*) was
**not run** — no foreign tag was available. The `Foreign → Confirm` branch is JVM-proven only.

### 5.13 NFC safety improvements added after the original narrow product

| Commit | Date | Improvement |
|---|---|---|
| `26ec9d0` | 2026-09-14 | **Dropped the `TECH_DISCOVERED` catch-all and `res/xml/nfc_tech_filter.xml`** (and the 2024 activities and `LegacyKey`). The old app appeared in the chooser for every `NfcA`/`Ndef` tag and then silently finished. Now the app is only offered for its own two record types. |
| `f92a391` | 2026-09-14 | **Payload format v1**: versioned, flagged, fixed-length payload; TNF gate; first-record-only dispatch; the typed `TagPayload` including `Foreign`/`Malformed`/`NewerVersion`; canonical-UUID enforcement on encode. Also **the AAR** (second, pinning the package) and **`OverwritePolicy`** (read-before-write consent). The 2024 app had no version byte, no TNF check, no type check, no AAR and no overwrite question. |
| `8a94872` | 2026-09-14 | **`LinkLaunchPolicy`** (scheme allowlist/blocklist, whitespace and control-character rejection, URI extraction instead of a `contains("joplin")` substring gate) and **`TagRoute`**. The 2024 app passed stored text straight to `startActivity` with no `try/catch`. |
| `dc1bb1c` | 2026-09-14 | **The whole safe adapter**: `NdefBridge` (the single Android↔bytes boundary), **`NfcReaderModeSession`** replacing the 2024 mutable-`PendingIntent` foreground dispatch, and **`TagWriter`** with the capacity check, read-back comparison, typed `WriteResult`s and `close()` in `finally` (the 2024 code leaked the connection when `isWritable` was false). |
| `bdcc475` | 2026-09-14 | **Documented which `TagWriter` calls throw** — `inspect` propagates tag-I/O failure, `write` folds it into `Failed` — so callers can be correct rather than lucky. |
| `18534bd` | 2026-09-14 | The write flow as a sequence: **read first, confirm overwrite, write, read back, optional lock.** |
| `485e110` | 2026-09-14 | **Don't leave the "lock permanently" switch armed on back**; the bind-target picker survives database errors. |
| `0e1975f` | 2026-09-14 | **Stale-handle fix**: a confirmed overwrite is remembered as consent for *that content* and honoured on the next tap. |
| `e2cf1d0` | 2026-09-14 | **Removed `FLAG_READER_SKIP_NDEF_CHECK`** (with it set, `Ndef.get()` returns null and nothing can be written), and **lock only after a verified read-back** — the `NdefFormatable` path formats unlocked and defers verification and locking to the second tap. |
| `25026d7` | 2026-09-14 | **`NfcDispatchActivity`** as the single NFC-exported component with exactly two `NDEF_DISCOVERED` filters, plus the manifest `<queries>` so handler-present checks work under API 30+ package visibility. |
| `54f9aea` | 2026-09-14 | **Survive hostile extras**: both the trampoline and the launcher wrap intent parsing in try/catch. Also rotation and spanned (styled) share text. |
| `a8204d4` | 2026-09-14 | **`DeepLinkRoute`** — shape validation for `notenfc://asset|link|tag` before anything is looked up. |
| `19042da` | 2026-09-15 | Single-activity shell; the dispatch activity becomes a **UI-less translucent trampoline** that hands off to `MainActivity`, so there is one place that renders tag results. |
| `c808b49` | 2026-09-15 | The 1B write rules lifted into **`TagWriteController` behind the `TagIo` seam** — the decision logic becomes JVM-testable without an `android.nfc.Tag`. |
| `838d6a3`, `53f9cac` | 2026-09-15 | **Scan tab retired**; reader mode is entered only from an intentional destination (Settings → Read / inspect tag, or the dashboard empty state), so an ambient read can never drift into write mode. |

Historical counterpoint: `f2c32aa` (2023-08-08) fixed the era bug where the note id was baked into a
mutable `PendingIntent`, so every subsequent share wrote the *first* note's id.

---

## 6. Mechanism versus policy

The strict rule applied by `archaeology-nfc.md`: **mechanism** only if a second real consumer (the
reconstructed narrow noteNFC) would use the same behaviour unchanged, given a parameter for identity.
Anything that encodes what a record *means*, what the product is called, or what the user is offered
is policy.

### 6.1 `:core` — the Android-free half

| Name | Path | What it does | Class | Forbidden dependency (today's reach) | Note |
|---|---|---|---|---|---|
| `NdefRecordData` | `core/.../core/nfc/NdefCodec.kt:8-12` | Android-free view of one NDEF record (tnf/type/payload) with value equality | **mechanism** | clean | The single most reusable type in the repo. Move verbatim. |
| `NdefCodec.decode` envelope rules | `NdefCodec.kt:56-65` | First-record-only dispatch; TNF gate; type switch; unknown type → `Foreign` | **mechanism** | clean, but the type literals it switches on are product identity | Extract as `decode(records, identity)`; the switch becomes "my type / not my type". |
| `NdefCodec.v1Record` / `encodeV1` | `NdefCodec.kt:90-102` | Builds an external-type record + AAR message for a UUID identity | **both-as-policy** over a mechanism helper | `TagId` (`core/.../core/model/Ids.kt`) | The *envelope* is mechanism; the 18-byte body is a payload scheme both products reuse. Extract as `VersionedUuidPayload(version, flags, uuid)` + `ExternalRecord(identity, payload)`; keep `TagId` in each app. |
| `NdefCodec.applicationRecord` | `NdefCodec.kt:104-109` | Byte-identical AAR for a package name | **mechanism** | the `PACKAGE_NAME` constant | Becomes `applicationRecord(packageName)`. `AAR_TYPE` is a platform constant and stays. |
| `NdefCodec.requireCanonicalUuid` | `NdefCodec.kt:111-120` | Refuses non-canonical UUID strings on encode | **mechanism** | `TagId` | Generalise to take a `String`; the `TagId` wrapper stays in the app. |
| `NdefCodec.DOMAIN`, `V1_TYPE*`, `PACKAGE_NAME` | `NdefCodec.kt:37-47` | The identity literals | **policy (each app)** | — | These are the parameters, not the library. See §6.3. |
| `NdefCodec` legacy decode (`LEGACY_TYPE*`, `legacyKeyPattern`, `decodeLegacy`) | `NdefCodec.kt:49-53, 82-88` | Recognises the 2024 8-hex record | **policy (belongs to narrow noteNFC)** | clean | ServiceTag has no legacy tags (`nfcTags = 0` live; legacy tags point at notes). Dropping `md5_short` from ServiceTag entirely — decoder, `PayloadFormat` value, manifest filter, `Legacy` sheet — also removes the only filter overlap between the two products. |
| `TagPayload.Foreign` / `.Malformed` / `.Empty` / `.NewerVersion` | `NdefCodec.kt:21-26` | "what is on this tag, classified" for content the app does not own | **mechanism** | clean | The library's return vocabulary. |
| `TagPayload.V1` / `.LegacyMd5` | `NdefCodec.kt:16-19` | Product-specific recognised payloads | **policy** (V1: both; `LegacyMd5`: noteNFC) | `TagId` | Library returns `Recognised(payloadBytes)`; each app parses its own body. |
| `OverwritePolicy` / `OverwriteDecision` | `core/.../core/nfc/OverwritePolicy.kt` | Read-before-write rule: write silently only over `Empty` or the same identity; everything else needs one confirmation | **mechanism**, after a signature change | `TagId`; the reason strings say "noteNFC" | Extract as `decide(existing, isSameIdentity): Decision` returning a **reason token** (`SameProduct`, `Legacy`, `NewerVersion`, `Foreign`, `Unreadable`, `Empty`), with the sentence built in each app. The *rule* is the valuable part and is identical for both. |
| `TagRoute` | `core/.../core/nfc/TagRoute.kt` | Parses `notenfc://tag/<uuid>` into a `TagPayload` | **policy (each app)** | `TagId` (re-verified import); plus the `notenfc` scheme is identity | The library must not own a scheme. The canonical-UUID regex is the only mechanism inside, and it is duplicated in three places. |
| `DeepLinkRoute` / `DeepLink` | `core/.../core/links/DeepLinkRoute.kt` | Parses `notenfc://asset|link|tag` | **policy (ServiceTag)**, with a `tag`-only variant in noteNFC | `AssetId`, `LinkId` | `asset` has no meaning in noteNFC. Looks similar to `TagRoute` but **delegates** to it. |
| `LinkLaunchPolicy` / `LinkCheck` | `core/.../core/links/LinkLaunchPolicy.kt` | Outbound-URI gate: scheme allow/blocklist, URI extraction, kind classification | **both-as-policy** | `LinkKind` | The allowlist is a **product decision**, not a mechanism. Duplicate; do not share. Nothing to do with NFC. |
| `ResolveTag` / `Resolution` | `core/.../core/usecase/ResolveTag.kt` | payload → row → asset/link, stamping `lastScannedAt` in a transaction | **policy (ServiceTag)**; noteNFC needs a narrower version | `TagRepository`, `AssetRepository`, `LinkRepository`, `UnitOfWork`, `Clock`, `TagBinding`, `PayloadFormat`, `TagStatus`, `TagTarget`, `Asset`, `ExternalLink` (re-verified against the file's imports; the domain model classes were missing from an earlier draft of this cell) | **The clearest boundary in the codebase: everything below is mechanism, this and above is policy.** noteNFC's version resolves to a link only, with no `OpenAsset`/`Revoked`. |
| `BindTag` | `core/.../core/usecase/BindTag.kt` | Binds a `(format, key)` to an asset or link, creating or retargeting a row | **policy (ServiceTag)**; noteNFC needs a link-only variant | Room-backed ports, `TagBinding`, `TagTarget`; calls `NdefCodec.requireCanonicalUuid` | The one place policy reaches *down* into the codec — and only for the UUID-shape check, which the library will still export. |
| `ProvisionTag` | `core/.../core/usecase/ProvisionTag.kt` | `begin` mints identity before the write, `complete` records the verified write, `abandon` cleans up | **policy (both apps, separately)** | Room-backed ports, `TagBinding`, `PayloadFormat` | The *protocol* (mint → write → verify → complete, else abandon) is mechanism-shaped and worth documenting in the library README; the rows are not. |
| `requireTargetExists` / `UnknownTarget` | `core/.../core/usecase/TagTargets.kt` | A binding may only point at a row that exists | **policy (ServiceTag)** | `AssetRepository`, `LinkRepository` | — |
| `TagBinding`, `PayloadFormat`, `TagStatus`, `TagTarget` | `core/.../core/model/TagBinding.kt` | The row model and its enums | **policy (ServiceTag)**; noteNFC needs a much smaller row | pure data, but asset/link-shaped | `PayloadFormat` is a *persistence* discriminator, not a wire format; `LEGACY_MD5` need not survive into ServiceTag. |
| `OpenLink` / `SaveLink` | `core/.../core/usecase/{OpenLink,SaveLink}.kt` | Save-time and launch-time halves of the link policy | **both-as-policy** | `LinkRepository`, `UnitOfWork`, `Clock`, `IdGenerator` | noteNFC's core product; ServiceTag's periphery. Duplicate. |
| `ExternalLink`, `LinkKind` | `core/.../core/model/ExternalLink.kt` | The link row | **both-as-policy** | `AssetId` (nullable) | noteNFC's copy drops `assetId`. |

### 6.2 `:app` — the Android half

| Name | Path | What it does | Class | Forbidden dependency (today's reach) | Note |
|---|---|---|---|---|---|
| `NdefBridge` (`toRecordData`, `toNdefMessage`, `toHexOrNull`, `Intent.ndefRecords`, `Intent.nfcTag`) | `app/.../nfc/NdefBridge.kt` | The only place `android.nfc` types meet the pure-bytes codec; includes the SDK-33 typed/untyped `getParcelable*` split | **mechanism** | clean — imports only `android.*` and `NdefRecordData` | Moves wholesale into an Android adapter artifact. `Intent.nfcTag()` is currently **dead code**. |
| `NfcReaderModeSession` | `app/.../nfc/NfcReaderModeSession.kt` | Reader-mode lifecycle: flags, start/stop, availability; the "never `FLAG_READER_SKIP_NDEF_CHECK`" rule and the threading contract | **mechanism** | clean | Move verbatim. **The doc comment is half the value — carry it across.** |
| `TagWriter` + `TagInspection` + `WriteResult` | `app/.../nfc/TagWriter.kt` | Read-first / write / read-back / capacity / `NdefFormatable` fallback / typed failures / connection hygiene | **mechanism** | clean — imports `NdefCodec.decode` only to classify what it read | Move wholesale; replace the `decode` call with an injected classifier so the library carries no product type strings. |
| `TagHandle` / `NfcTagHandle` / `TagIo` / `RealTagIo` | `app/.../ui/scan/TagWriteController.kt:32-65` | The testing seam keeping `android.nfc.Tag` out of decision logic | **mechanism** | clean | Belongs with `TagWriter`, not the UI. Currently in a UI file. |
| `WriteState` | `TagWriteController.kt:67-80` | `Idle`/`Confirm`/`Verifying`/`Written`/`Error`, each carrying a user-facing sentence | **policy** (states are mechanism, sentences are not) | — | Split: the library exposes the states; each app supplies the strings. |
| `TagWriteController` — the state machine | `TagWriteController.kt:90-299` | Single-flight; read-before-write; confirmation ownership of `busy`; the remembered-consent stale-handle protocol; format → second-tap verify → lock-last | **mechanism** (the protocol) entangled with **policy** (the rows and the wording) | `ProvisionTag`, `TagBinding`, `TagTarget`, `AppGraph`, every message string | The highest-value extraction after `TagWriter`, and the hardest. Target shape: `TagWriteSession(intended, isSameIdentity, io, onProvision, onVerified, onAbandon)`. Row minting and wording stay in the app. |
| `NfcDispatchActivity` | `app/.../nfc/NfcDispatchActivity.kt` | The one NFC-exported component: cold start, hostile-extras guard, two accepted actions, link-launch-without-UI, hand-off to the single activity | **policy (each app)** | `AppGraph`, `MainActivity`, `ExternalLink`, `OpenLink`, `Resolution`, `LinkLauncher`, `FORMAT_NONE` | The *shape* (translucent, UI-less, singleTop, hand off to one renderer) is a pattern worth documenting; the code is product-specific. |
| `MainActivity` | `app/.../MainActivity.kt` | Intent → route translation; `replay = 1` deep-link flow; `safeRouteFrom` | **policy (each app)** | Compose, `AppGraph`, `Route` | Also the home of the hard-coded `"V1"` literal. |
| `ShareActivity` / `ShareFlow` / `ShareCardScreen` | `app/.../ShareActivity.kt`, `ui/share/*` | `ACTION_SEND text/plain` entry, URI extraction, "Write to a new tag" / "Keep as link", return-to-caller | **both-as-policy** — and **noteNFC's core product** | Compose, `SaveLink`, `Route.WriteTag` | noteNFC's version is the whole app; ServiceTag's is one way to create a link. Duplicate. |
| `ScanViewModel` / `TagResultViewModel` / `WriteTagViewModel` | `app/.../ui/scan/ScanViewModels.kt` | Single-flight scan, resolution → sheet mapping, the `FORMAT_NONE` contract, bind action | **policy** | `ViewModel`, `AppGraph`, `BindTag`, `ResolveTag`, `OpenLink`, `Route`, asset/link repositories | `asTagResult` is the *shared wording* mapping between foreground and ambient paths — a pattern both products want, not shareable code. |
| `ScanScreen` / `WriteTagScreen` / `TagResultSheet` | `app/.../ui/scan/*` | Reader-mode lifecycle binding, the eight sheets, the lock warning, the availability line | **policy** | Compose, Material3, the app theme | The `LifecycleResumeEffect { start(); onPauseOrDispose { stop() } }` idiom is worth documenting in the library's README. |
| `LinkLauncher` | `app/.../links/LinkLauncher.kt` | `ACTION_VIEW` with `ActivityNotFoundException` **and** `SecurityException` caught | **both-as-policy** | `Toast` wording | Tiny; duplicate rather than share. Not NFC. |

**Where two files merely look similar** (do not "de-duplicate" these):

1. `TagRoute.parse` vs `DeepLinkRoute.parse` — same scheme literal, same UUID regex, similar signature, but `DeepLinkRoute` **delegates** to `TagRoute` and returns a different type. Both policy. What *is* duplicated is the canonical-UUID regex, in three places (`TagRoute.kt:13`, `DeepLinkRoute.kt:18`, and the check inside `requireCanonicalUuid`).
2. Two `describe(TagPayload)` functions (`ScanViewModels.kt:61-66`, `TagWriteController.kt:290-297`) — same signature, deliberately different wording for different screens. **Do not extract — the divergence is the feature.**
3. Two `@Volatile busy` single-flight guards (~4 lines each). Only worth extracting as part of `TagWriteSession`; on its own it is the wrong abstraction.
4. `Intent.ndefRecords()` vs `NfcDispatchActivity.payloadOf` — adjacent and easy to conflate. The former is mechanism (unparcel the extra); the latter is policy (which intent actions this product answers).
5. `Resolution` vs `TagResult` — near-parallel sealed hierarchies with a hand-written mapping. Both policy; the duplication is intentional layering (domain outcome vs sheet state).
6. `OverwritePolicy.decide` vs `TagWriteController.verify` — one compares *payloads* (pre-write consent), the other compares *records* (post-write verification). Different questions; keep both.

### 6.3 Identity strings in the codec, and the proposed parameterisation

| String | Where | What it really is |
|---|---|---|
| `com.loosecannon.notenfc` | `NdefCodec.kt:37` (`DOMAIN`) | NFC Forum external-type **domain**, lower-cased on the wire by the framework |
| `tag` | `NdefCodec.kt:39` (`V1_TYPE_NAME`) | external-type **name** |
| `com.loosecannon.notenfc:tag` | `NdefCodec.kt:40`, mirrored at manifest `:87` | the wire type **and** the manifest `android:path`. **Two unlinked literals today.** |
| `com.loosecannon.notenfc` | `NdefCodec.kt:46` (`PACKAGE_NAME`) | the Android **applicationId**, embedded in the AAR payload. Must equal the app's own applicationId or the AAR points at a different app; today only a doc comment enforces that. |
| `android.com:pkg` | `NdefCodec.kt:47` (`AAR_TYPE`) | **platform constant — not identity.** Stays inside the library. |
| `com.loosecannon.notenfc:md5_short` | `NdefCodec.kt:50-51`, manifest `:92` | legacy wire type |
| `notenfc` | `TagRoute.kt:10`, `DeepLinkRoute.kt:17`, manifest `:56-58` | deep-link **scheme** — never enters the library |
| `tag`, `asset`, `link` | `TagRoute.kt:11`, `DeepLinkRoute.kt:23-27`, manifest `:56-58` | deep-link **hosts** — app-level |
| `"V1"`, `"NONE"`, `"LEGACY_MD5"` | `MainActivity.kt:84`, `ScanViewModels.kt:41`, `PayloadFormat` | inter-activity wire strings between trampoline and renderer — app-level; `MainActivity.kt:84` hard-codes `"V1"` rather than `PayloadFormat.V1.name` |

**Proposed shape.** `NdefCodec` stops being an `object` and becomes a class (or top-level functions)
taking a value type:

```kotlin
data class TagIdentity(
    val externalDomain: String,   // NFC Forum external-type domain
    val typeName: String,         // "tag"
    val aarPackage: String?,      // the applicationId, or null for no AAR
)
```

`encode(identity, payload)` produces `[externalRecord, aar?]` in that order; `decode(identity,
records)` returns `Recognised(bytes) | Foreign(description) | Empty`, and the body parse (version /
flags / UUID) is a separate, reusable `VersionedUuidPayload` codec each app calls with its own version
constant. `aarPackage` is nullable because the 2024 tags had no AAR. The library should keep the full
offending type string in `Foreign(description)` — it already does — because that string is what lets
an app say "this is a ServiceTag tag" instead of "this is something else".

**Invariants the library must state.** Each is currently true in the code but guaranteed only by
ordering or by a doc comment; after extraction they need to be stated and tested per consumer.

1. **Type check before body parse.** A sibling product's tag must decode as `Foreign` and never as a recognised payload. Today the only thing enforcing this is the ordering at `NdefCodec.kt:59-64` (TNF gate → type switch → body parse). **This is the one real hazard of the split:** both products' payload *bodies* will be byte-identical (`01 00 || 16 UUID bytes`), so any future code path that read `payload` directly without checking the type first would parse a ServiceTag tag as a perfectly valid noteNFC v1 tag with an unknown id, show *"Unregistered tag"* with a **Bind** button, and let noteNFC silently adopt a live asset tag. Each consumer needs a test that the sibling's type decodes as `Foreign` — `NdefCodecTest.evernoteEraTypeIsForeign` is exactly the right template.
2. **AAR never first.** `[externalRecord, aar?]`, in that order, or the tag stops matching the `NDEF_DISCOVERED` filter (**[platform-doc]**, and **[JVM-proven]** today).
3. **Structural read-back equality.** Verify by comparing `List<NdefRecordData>` — record count, order, TNF, full type, full payload — not raw tag bytes, and not the record id field or the TLV framing.
4. **Never `FLAG_READER_SKIP_NDEF_CHECK`.** With it set, `Ndef.get()` returns null and nothing can be written. Device-proven negatively by `e2cf1d0`.

### 6.4 The legacy key lives only in the historical tree

**`LegacyKey.compute` no longer exists in modern code.** It was added in `76b751a` and **deleted** in
`26ec9d0`, per D13 §2: *"`LegacyKey.compute` in `:core` … Remove in Phase 1B together with
`LegacyLinkPolicy` (replaced by `LinkLaunchPolicy`); the vectors stay in D1/D6 as protocol
documentation."* Re-verified at `ac523d7`: a repo-wide grep for `MessageDigest` / `"MD5"` over
`core/src` and `app/src` finds only SHA-256 uses (`ArtifactsCodec`, `BackupCodec`,
`AttachmentSweep`, `SafTreeAttachmentStore`, plus test fakes). **There is no MD5 anywhere in the
codebase**, and the single `LegacyKey`-shaped grep hit is a test *name* (`decodesValidLegacyKey`), not
the object.

The algorithm survives as documentation (`docs/design/01-current-state-archaeology.md`,
`docs/design/06-legacy-compatibility.md`) and as working code **only in the `c84b881` core**, with
its hand-computed vectors in `LegacyKeyTest`. **Consequence for the design: noteNFC's legacy support
comes from the historical tree, not from ServiceTag.** The reconstruction inherits it by branching
`c84b881`; it cannot be copied out of modern master.

This also corrects a standing note. "Legacy tags recoverable via deterministic MD5 key" is true of
the **protocol** (the key is a pure function of the shared text, so it can be recomputed) but no
longer of the **implementation**: recovery in modern code would mean re-writing `LegacyKey` from D6
§5, and the re-link / bulk-recovery flow that would have used it was explicitly dropped
(D13 §2–§3).

### 6.5 What a sibling's tag looks like after the split

**A ServiceTag tag seen by noteNFC** holds `[tnf 0x04, type `com.loosecannon.servicetag:tag`, payload
`01 00 || 16 UUID bytes`]` then `[tnf 0x04, type `android.com:pkg`, payload
`com.loosecannon.servicetag`]`.

- **Ambient tap**: noteNFC's filter is `path="/com.loosecannon.notenfc:tag"` (exact), so it does not match; noteNFC has no `TECH_DISCOVERED` filter, so noteNFC is not offered at all. The AAR names ServiceTag, so ServiceTag starts. **Correct by construction, no new code.** **[unobserved]**
- **Inside noteNFC's reader mode** (its write/inspect screen, which overrides dispatch): `decode` sees `tnf == 0x04` with an unrecognised type and yields `Foreign("tnf=4 type=com.loosecannon.servicetag:tag")`. On the **write** screen → `Confirm("foreign NDEF content (…)")`: nothing is written without one explicit confirmation, and the reason string already names the other product. On the **read/inspect** screen → `NotOurs` → the *"Not a noteNFC tag"* sheet; **no row is created and no lookup happens.**

A noteNFC tag seen by ServiceTag is symmetric, with one asymmetry to design in: only noteNFC keeps
the `md5_short` filter and decoder. That yields the coexistence matrix D7 asks for: note tag →
noteNFC, asset tag → ServiceTag, link tag → ServiceTag, legacy tag → noteNFC, foreign tag → nothing
unsafe.

**What is worth adding is wording, not logic.** Because `Foreign(description)` carries the full type
string, each app can recognise its sibling and say *"This is a ServiceTag tag — overwriting it will
detach it from its machine"* instead of the generic *"foreign NDEF content"*. That is app-level
string work over an unchanged library return value.

---

## 7. Data and migration boundary

Evidence: `app/schemas/…/5.json`, the repo's codec sources, and the preserved pre-split set under
`~/Documents/Projects/AndroidStudioProjects/noteNFC-backups/pre-split-2026-09-16/` (a data archive of
9 419 B at format 5, an artifacts archive of 13 651 544 B at artifact format 1 sharing one
`backupSetId`, plus a `databases/` + `shared_prefs/` snapshot). No `adb` was run for this document.

### 7.1 Room v5 — 11 tables

| Table | PK | FK → onDelete | Indexes |
|---|---|---|---|
| `asset` | id | `parent_asset_id → asset(id)` RESTRICT | status, name, parent_asset_id |
| `nfc_tag` | id | `asset_id → asset(id)` SET NULL; `link_id → external_link(id)` SET NULL | (payload_format, payload_key), asset_id, link_id |
| `external_link` | id | `asset_id → asset(id)` CASCADE | asset_id |
| `measurement_definition` | id | `asset_id → asset(id)` CASCADE; `source_a_id`/`source_b_id → measurement_definition(id)` RESTRICT (self) | asset_id, (asset_id, key) unique, source_a_id, source_b_id |
| `event_profile` | id | `asset_id → asset(id)` CASCADE | asset_id |
| `profile_field` | id | `profile_id → event_profile(id)` CASCADE; `definition_id → measurement_definition(id)` CASCADE | (profile_id, definition_id) unique, definition_id |
| `profile_consumable` | id | `profile_id → event_profile(id)` CASCADE | profile_id |
| `asset_event` | id | `asset_id → asset(id)` CASCADE; `profile_id → event_profile(id)` SET NULL | (asset_id asc, occurred_on desc, created_at desc), (source, source_ref) unique, profile_id |
| `measurement` | id | `event_id → asset_event(id)` CASCADE; `definition_id → measurement_definition(id)` RESTRICT | (definition_id, event_id), event_id |
| `consumable_usage` | id | `event_id → asset_event(id)` CASCADE | event_id |
| `attachment` | id | `asset_id → asset(id)` CASCADE; `event_id → asset_event(id)` CASCADE | asset_id, event_id, (storage_provider, storage_locator) unique |

`@Database(version = 5, exportSchema = true)`. **`5.json`'s `identityHash` is
`157988f1aada363f37590a6735a3be36` (re-verified) and matches the phone's live
`room_master_table.identity_hash` exactly** — the phone database is a byte-faithful v5 Room database,
not drifted. `PRAGMA user_version` = 5.

**Migration ladder** (`app/.../data/room/Migrations.kt`, `MIGRATION_1_2` → `MIGRATION_4_5`): each
step's SQL is copied verbatim from the corresponding `N.json` `createSql`, so migration and compiled
schema share one source and Room validates the result on open. `MIGRATION_2_3` and `MIGRATION_3_4`
recreate `measurement_definition` and `asset` (SQLite cannot ALTER a column with a new FK) inside
Room's off/on `PRAGMA foreign_keys` window. `MIGRATION_4_5` is a pure `CREATE TABLE attachment` + 3
indexes, no row rewrite. Chain tests exist for every hop plus the full 1→5 run.

**Identity carried by the schema:** none at runtime. No table, column, or the `identityHash` encodes
the package name — the hash is a structural hash of the schema, independent of package. The
package-derived pieces are build/tooling only: the **schema export directory** (`app/schemas/<FQN of
AppDatabase>/`) and the **database file name** `notenfc.db`, which is a literal, not derived from
`applicationId`.

### 7.2 Backup format 5 and artifacts format 1

**`BackupManifest`**: `formatVersion`, `appVersion`, `schemaVersion`, `createdAt`,
`counts: Map<String,Int>`, `dataSha256`, `backupSetId` (default `""` for format ≤ 4),
`artifactFormatVersion`, `artifactCount`, `artifactBytes`.

**`BackupData`** is seven top-level lists — `assets`, `nfcTags`, `externalLinks`,
`measurementDefinitions`, `eventProfiles`, `assetEvents`, `attachments` — with journal fields nested
inside their parents. DTOs: `AssetDto`, `NfcTagDto`, `ExternalLinkDto`, `MeasurementDefinitionDto`,
`ProfileFieldDto`, `ProfileConsumableDto`, `EventProfileDto` (embeds fields + consumables),
`MeasurementDto`, `ConsumableUsageDto`, `AssetEventDto` (embeds measurements + consumables),
`AttachmentDto`.

**`BackupCodec`**: entries are exactly `manifest.json` + `data.json` in a ZIP. `encode()` sorts every
list by `id` (children by `sortOrder` within parent) so the same input always produces the same bytes;
`dataSha256` is the SHA-256 of the exact `data.json` bytes, checked on decode. **Newer formats are
refused before any content is touched** (`formatVersion > FORMAT_VERSION` → `BackupNewerFormat`). A
format-5 manifest with a blank `backupSetId` is refused as corrupt, because it could never pair with
an artifacts file. **Backward compatibility is by Kotlin default values on the DTOs**: format-1
(three original lists), format-2, format-3 and format-4 files all still decode without
version-specific code — the mechanism that lets a renamed importer accept an old file, as long as it
carries forward the *same* DTO defaults. `validateGraph()` re-checks every FK-shaped reference
*before* any transaction opens.

**`ImportBackupReplace`**: decode happens outside the transaction ("refuse before touching data").
Inside one write transaction: read the doomed attachment locators, delete in reference-clearing order
(`attachments → events → profiles → definitions → tags → links → assets`), then insert in reference
order (`assets` parents-first via `AssetTree.parentsFirst`, `measurementDefinitions`
ENTERED-then-DERIVED, `eventProfiles`, `externalLinks`, `nfcTags`, `assetEvents`, `attachments` last).
After commit, a best-effort sweep removes the bytes of replaced attachment rows whose locator is not
reused — a failure there leaves an orphan file, never a half-done import.

**`ExportBackupSet`**: one read transaction over every repository; mints a fresh `backupSetId` per
export (a pairing token, not an install or package identity); `appVersion = BuildConfig.VERSION_NAME`
(`"2.4"`), `schemaVersion = 5`.

**Artifacts format 1**: entry naming `artifacts/<attachment-id>.<ext>`, extension taken from the
*locator* so entry name and locator can never disagree. `ArtifactsManifest` carries
`artifactFormatVersion`, `dataFormatVersion` (cross-reference to the paired data archive),
`backupSetId`, `createdAt`, and per-entry `attachmentId`/`entryName`/`sha256`/`sizeBytes`/`mimeType`.
STORED for already-compressed types (jpeg/png/pdf/zip), DEFLATED otherwise; a two-pass write hashes
and sizes every source first and throws `ArtifactsWriteFailed` if a source drifted between passes.
On write, a row whose bytes are missing or whose live hash/size no longer match is left out of both
manifest and archive, and (owner's ruling) **the whole export fails if any row is left out**. On
read, each entry is verified **twice** — the manifest's `sha256` against the local row *before*
opening anything, and the store's own streaming digest *after* the write; a mismatch deletes what was
just written and counts the row `skipped`. `RestoreArtifacts.run(archive, expectedSetId)` throws
`ArtifactsSetMismatch` before touching any entry if the set ids disagree, and is idempotent (bytes
that already hash correctly are `alreadyPresent`, never rewritten).

### 7.3 No package identity inside the set

Grepping the real pulled files' `manifest.json` and `data.json`, and the artifacts `manifest.json`,
for `notenfc`, `loosecannon`, `com.loosecannon`, `NoteNFC`, `noteNFC`: **zero hits in any of the
three files.** `appVersion` is a bare semver string. **The backup content carries zero package or
product identity.**

The only place the old product name appears in the data path is the **exporter's chosen output file
name** (`noteNFC-data-<stamp>.zip` / `noteNFC-artifacts-<stamp>.zip`, from `SafBackupSetIO`'s
`BackupSetNames`), and **the import path never inspects a file name or extension for meaning** —
`restoreDataFrom(io)` / `restoreFilesFrom(io)` take an arbitrary SAF-picked document. The only
cross-check the importer performs is manifest-to-manifest `backupSetId` via
`AppPrefs.lastRestoredBackupSetId`. Consequence: **a renamed build with an unmodified codec layer can
import the two preserved real files with zero code changes.**

### 7.4 Ids, locators and grants

**All ids are verbatim UUID strings**, no numeric surrogate anywhere in the backup format, and every
relationship is **by id**:

| DTO | Relationships |
|---|---|
| `AssetDto` | `parentAssetId` → another asset (nullable, self-referencing hierarchy) |
| `NfcTagDto` | `assetId` **xor** `linkId` (nullable target) |
| `ExternalLinkDto` | `assetId` (nullable — a link can be unowned) |
| `MeasurementDefinitionDto` | `assetId`; `sourceAId`/`sourceBId` → other definitions, only when `kind = DERIVED` |
| `ProfileFieldDto` | `definitionId` |
| `EventProfileDto` | `assetId`; embeds `fields`/`consumables` by their own ids |
| `MeasurementDto` | `definitionId` |
| `AssetEventDto` | `assetId`; `profileId` (nullable); embeds `measurements`/`consumables` |
| `AttachmentDto` | owner is **exactly one of** `assetId` / `eventId` (XOR, enforced in `toDomain()` and by `validateGraph()`) |

No id is package-, device- or install-qualified. **An id that exists pre-split must exist, unchanged,
post-split** — that is the basis of the migration proof.

**Store-relative locators.** `AttachmentLocator.forOwner(...)` produces
`assets/<asset-id>/<attachment-id>.<ext>` or `events/<event-id>/<attachment-id>.<ext>`, with
`matchesShape` giving the exact regex `^<dir>/<id>\.[a-z0-9]{1,8}$` that `validateGraph()` uses to
prove a locator could only have been built for that row. Every locator is **relative to the store's
root** (the chosen SAF tree): no drive letter, authority, tree-URI fragment or package name appears in
any locator string, confirmed against the real set's 8 attachment rows. **Locators are therefore
package-agnostic and store-agnostic by construction** — the same locator resolves correctly under any
package's SAF-tree store pointed at any tree, including a fresh grant on the very folder already in
use.

The on-disk layout is walked segment-by-segment with `findFile`/`createDirectory`, and `documentIn()`
falls back from an exact name match to "the first child whose name starts with `<id>.`" because some
SAF providers append or normalise extensions and the attachment id is unique enough to make that
unambiguous. `put()` hashes while it streams (SHA-256, 64 KiB buffer) — **the only place an
attachment's `sha256` is ever computed** — deletes any stale document at the same locator first, and
deletes the partial document on any failure. `StoreState` is `NotConfigured | Ready(displayName,
authority) | AccessLost(displayName)`, derived purely from whether a tree URI is saved and whether it
still resolves and grants.

**Per-package SAF grants — ServiceTag must re-take its own.** Both call sites are in
`SettingsScreen.kt`'s `OpenDocumentTree` result handler. **Take**:
`takePersistableUriPermission(picked, FLAG_GRANT_READ | FLAG_GRANT_WRITE)`, wrapped in `runCatching`;
on failure the pref and any prior grant are untouched and the screen re-reads `state()` so it shows
what is actually true (some cloud-backed providers refuse a lasting grant). **Release**: after a
successful take, if the owner picked a *different* folder, the old grant is released (also
`runCatching`, because releasing a grant the system no longer holds throws and must not undo the take
that just succeeded; the comment cites spike S5 — grants otherwise accumulate). Only on a successful
take is the pref updated. **[platform-doc]**: a persisted grant is scoped to the *calling*
application; `getPersistedUriPermissions()` only ever returns the caller's own grants, and a different
`applicationId` — a genuinely different app to the OS, even from the same source with the same signing
key — holds none of another app's grants and must call `takePersistableUriPermission` itself through
its own `OpenDocumentTree` flow. **There is no way to carry the old grant across a rename.**

### 7.5 Exact-match counts on the live phone

The live database and the preserved set agree on **every table, 11 of 11**:

| Table | sqlite rows | backup `manifest.counts` | `data.json` list length |
|---|---|---|---|
| `asset` | 5 | assets: 5 | 5 |
| `nfc_tag` | **0** | nfcTags: 0 | 0 |
| `external_link` | **1** | externalLinks: 1 | 1 |
| `measurement_definition` | 13 | measurementDefinitions: 13 | 13 |
| `event_profile` | 15 | eventProfiles: 15 | 15 |
| `profile_field` | 22 | profileFields: 22 | (nested) |
| `profile_consumable` | 14 | profileConsumables: 14 | (nested) |
| `asset_event` | 26 | assetEvents: 26 | 26 |
| `measurement` | 68 | measurements: 68 | (nested) |
| `consumable_usage` | 4 | consumableUsages: 4 | (nested) |
| `attachment` | 8 | attachments: 8 | 8 |

The artifacts manifest carries 8 entries; the data manifest records `artifactCount = 8`. **The
preserved set is a faithful, complete snapshot.**

**Two facts about the live data dominate the migration plan:**

- **`nfc_tag = 0`.** The owner's real data currently has **no tag bindings at all** (tags were tested earlier on a wiped install). So the migration has no NFC-tag rewrite or rebind concern *for this dataset* — although the schema, DTO and codec must still carry the table correctly for whenever tags exist. It also means ServiceTag has no legacy bindings to lose by dropping `md5_short`.
- **One unowned external link.** `external_link` holds a single row, kind `JOPLIN`, `assetId = NULL` — an unowned link, not attached to any asset. It is the one row in the live data that must survive the split with its `id` and `kind` intact even though nothing else references it.

**Separately — not a database row, and not to be conflated with `nfc_tag = 0` above — eight physical
legacy tags exist in the field.** `docs/design/g1/00-source-data-inventory.md` records that the
owner read "eight notes in one notebook, each the target of a legacy noteNFC tag" (re-verified); one
of the eight is explicitly a standalone-link target, not an asset (item 4: *"this tag must stay a
standalone link to the note"*). These are physical `md5_short` tags written by the 2024 app; the live
`nfc_tag` table's zero rows describe the ServiceTag-era database only and say nothing about these —
the two facts are independent. **Verified fact (2026-09-16, read-only `pm list packages` on the
phone):** the 2024 mixed-case app `com.looseCannon.noteNFC` is **not** installed; only
`com.loosecannon.notenfc` is. Because each Android package's `SharedPreferences` is private to it,
the modern lowercase package never held the 2024 app's `noteNFCURLs` map (confirmed independently by
§7.5's own prefs listing below: only `notenfc.xml` and `storage_spike.xml` are present, no
`noteNFCURLs.xml`), and the app that owned that map is not even installed — so the eight tags'
key→link lookup map is gone, and the eight tags are unresolvable by any app installed on the phone
today.

**Prefs on the phone** (both under the app's private prefs dir):

- `shared_prefs/notenfc.xml` — `attachment_tree_uri` (a persisted SAF tree URI; summarised as "set", never quoted, since it embeds the owner's chosen folder) and `last_backup_at` (a long timestamp). **Not present**: `last_restored_backup_set_id` (no import has ever run against this install) and `appearance_mode` (never explicitly set, so it falls back to its `SYSTEM` default and is never written).
- `shared_prefs/storage_spike.xml` — a **second, separate** prefs file holding one key, `tree_uri` (also "set"). It is not part of the `AppPrefs` contract at all (different file, different key name) and reads as residue from spike S5. **Device-local residue; the migration need not preserve it** — flagged only so it is not mistaken for a second attachment-tree source of truth.

For the record on the prefs key family: the brief names a `last_backup_*` family, but the current code
has exactly **one** such key, `last_backup_at`. There is no `last_backup_count` / `last_backup_size`.

### 7.6 Preservation table

| What | Where it lives in the set | How to verify after restore into a different package | What may legitimately differ |
|---|---|---|---|
| **Asset identity & metadata** (id, name, description, category, notes, status, manufacturer, model, serial, purchase/in-service dates, price + currency, vendor, location, warranty, retirement, season window) | `data.json.assets[]` | `id`-set equality plus per-field equality asset-by-asset | none — every field is domain data |
| **Asset parent/child hierarchy** | `AssetDto.parentAssetId` | Rebuild the tree from ids and diff against the pre-restore tree (`AssetTree.parentsFirst` re-validates no-cycle on import) | none |
| **NFC tag bindings** | `data.json.nfcTags[]` — **none live today** | Compare `id`, `payloadKey`, `assetId`/`linkId`, `status`, `physicalUid` | none |
| **External links** | `data.json.externalLinks[]` | Compare `id`, `kind`, `uri`, `assetId` (nullable) | none |
| **Measurement definitions** (incl. DERIVED formula/sources) | `data.json.measurementDefinitions[]` | Compare `id`, `assetId`, `kind`, and for DERIVED rows `formula`/`sourceAId`/`sourceBId` | none |
| **Event profiles + fields/consumables** | `data.json.eventProfiles[].fields[]` / `.consumables[]` | Compare profile `id` and nested field/consumable `id`s and `definitionId`/values | none |
| **Journal events + measurements + consumable usages** | `data.json.assetEvents[].measurements[]` / `.consumables[]` | Compare event `id`, `assetId`, `profileId`, and **per-field equality** on `kind`, `title`, `occurredOn`, `occurredTime`, `tzId`, `notes`, `source`/`sourceRef` (the unique `(source, source_ref)` pairing, §7.1) and `createdAt`/`updatedAt` (re-verified against `AssetEventDto`); nested measurement `id`→`definitionId`, consumable `id` | none |
| **Attachment rows** (ids, owners, metadata, hashes, locators) | `data.json.attachments[]` | Compare `id`, owner (`assetId` xor `eventId`), `sha256`, `sizeBytes`, `storageLocator`, `mode` | none |
| **Attachment bytes** | artifacts `artifacts/<id>.<ext>` | SHA-256 of the restored file at its locator == `AttachmentDto.sha256` — exactly what `RestoreArtifacts` already checks twice | none |
| **`backupSetId` linkage** | both manifests | Run the artifacts restore with `expectedSetId` = the data restore's `lastRestoredBackupSetId`; `RestoreArtifacts` throws `ArtifactsSetMismatch` if they disagree | the set id is minted fresh per export — a pairing token, not a stable identity to preserve *across* exports |
| **Device-local, never in a backup** | `shared_prefs/notenfc.xml` | — | `attachment_tree_uri` (**must be re-chosen** by the new package — grants do not transfer), `last_backup_at`, `last_restored_backup_set_id`, `appearance_mode`; the thumbnail cache (`<cacheDir>/thumbs/<attachment-id>-<sha256-prefix>.jpg`, purely derived and regenerable, in a directory that "may vanish at any time"); and the Room database file itself (the new package gets a fresh, empty database populated by the *data* restore, not a copied file) |

### 7.7 Concrete data-layer touch points

1. **`app/build.gradle.kts`, `applicationId`** — the actual rename target. Everything below either follows from it or is independent of it.
2. **Export file-name prefixes** — `SafBackupSetIO.kt:60-67` (`BackupSetNames.data()` / `.artifacts()`). **Cosmetic**, never read back by the importer, but hard-coded in tests (`AppSmokeTest`, `AttachmentsDeviceProofTest`, `BackupViewModelTest`). Either choice is safe for the importer; only the exporter's output and the test fixtures change.
3. **`appVersion`** — `AppGraph.kt:151` passes `BuildConfig.VERSION_NAME`. Carries no package identity; no change needed for correctness.
4. **Room database file name** — `AppGraph.kt:201`, `DB_NAME = "notenfc.db"`. Not package-derived; safe to rename with zero effect on restore correctness, because a fresh install under a new package has no old database file to find.
5. **Schema export directory** — becomes `<new FQN>/` automatically once the package/class FQN changes and the schema is re-exported. A build artefact, not runtime state — but the migration-ladder tests that load `N.json` by path need their resource paths checked after the rename.
6. **FileProvider authority** — manifest `:99` and `AppGraph.kt:140` both already derive from `applicationId` / `BuildConfig.APPLICATION_ID`. **Follows the rename automatically; no code change.**
7. **Thumbnail cache dir** — `Thumbnails.kt:29` uses `Context.cacheDir`, package-scoped by Android. No literal; follows the rename automatically and is never restored.
8. **Persisted SAF grant** — not a code touch point but a **required runtime step**: the new package must drive its own `OpenDocumentTree` → `takePersistableUriPermission` before any artifacts restore can write.
9. **`shared_prefs` file name** — `AppPrefs.kt:13`'s `"notenfc"` literal. Private to the app's own package-scoped prefs dir; no cross-package leakage either way, so keeping it is purely cosmetic.

---

## 8. Open questions for the design and for on-device observation

Consolidated from all four reports. None of these is settled by the archaeology.

### 8.1 Coexistence and platform behaviour — only a device can settle these

| # | Question | What is known | What must be observed |
|---|---|---|---|
| **Q1** | **Chooser vs AAR with both apps installed.** | **[platform-doc]** C1: *"If more than one application can handle the intent, the Activity Chooser is presented."* **[platform-doc]** C2: with an AAR, the platform tries the intent filter first, and starts the AAR's app *"if the Activity that filters for the intent does not match the AAR, if multiple Activities can handle the intent, or if no Activity handles the intent."* D13 already anticipated a chooser for the old/new coexistence window: *"Both apps match `md5_short`; Android shows a chooser until the old app is removed."* With distinct domains the question should not arise; with a shared `md5_short` filter it will. **Whenever a chooser (or any side-by-side listing, e.g. Settings → Apps) can show both products, a distinct launcher icon and a distinct label are a coexistence requirement, not a cosmetic choice** — today's single app has one icon (§4.6) and one `app_name`; two installed products need visibly different ones so the owner can tell them apart at the moment of choosing. | (a) both installed, distinct domains, tap each product's tag; (b) both installed and both declaring `md5_short`, tap a legacy tag; (c) tap a tag whose AAR names an **uninstalled** app — does the Play page for a non-existent listing appear? |
| **Q2** | **Stopped-state dispatch (the contradiction).** | **[platform-doc]** C8 says a force-stopped app gets no NFC dispatch. **[device-observed]** 1C row 16 says a force-stopped + data-cleared package **was** dispatched; only the never-launched fresh install was silent. All at `targetSdk 36`. | Re-observe **per app** after the split, and again at `targetSdk 37` with `DISPATCH_NFC_MESSAGE` declared. If the doc is right for the never-launched case only, a freshly installed second product will look broken until its first launch — which needs a health-screen sentence in both apps. |
| **Q3** | **Android 16+ per-app NFC allowlist.** | **[platform-doc]** C9: from Android 16 the user is notified on an app's first NFC intent and can disallow further scanning; apps can check `NfcAdapter.isTagIntentAllowed()`; the list lives under Settings → Apps → Special app access → Launch via NFC. **Neither `isTagIntentAllowed()` nor `ACTION_CHANGE_TAG_INTENT_PREFERENCE` is used anywhere in this codebase** (verified by grep), so a denial is currently invisible to the user inside the app. | Does the first-scan notification appear once per app? Can a denial for one silently break the other's tags (they share the NFC service, not the allowlist entry)? Two installed apps means two allowlist entries. |
| **Q4** | **Reader-mode interception across products.** | **[platform-doc]** C6: reader mode overrides AARs and the intent dispatch system. Expected: yes, each app's write/inspect screen sees the sibling's tag. | Observe in both directions with both installed. It determines whether the *"this is the other product's tag"* wording is reachable at all. |
| **Q5** | **Foreign tag → nothing unsafe.** | **[platform-doc]** C5: with no `NDEF_DISCOVERED` handler the platform tries `TECH_DISCOVERED`, and *"if no applications filter for any of the intents, do nothing."* Neither product will have a TECH filter. **[unobserved]** — 1B row 11 was skipped for want of a foreign tag. | Tap a blank tag, a commercial sticker and an unrelated external-type tag with both apps installed. Note **[platform-doc]** C10: from Android 16 a web-link tag triggers `ACTION_VIEW` rather than `ACTION_NDEF_DISCOVERED`, and from Android 17 an "open link" notification — so the expected result for a commercial sticker is *a notification*, not *nothing*. |

### 8.2 Product decisions the archaeology surfaces but cannot make

| # | Question | The trade-off as the evidence frames it |
|---|---|---|
| **Q6** | **Keep or scrub the inherited design package in the new noteNFC repo.** | Branching `c84b881` inherits 70 ServiceTag design files. **Keep** → clean, verifiable ancestry (the provenance the reconstruction exists for), but ServiceTag's design becomes public if the new repo is public. **Scrub** → loses the clean ancestry (squash or rewrite). The four personal-data files in §2.8 must go either way. The history report's own recommendation is "keep the ancestry, delete forward". **Clarification (independent review):** "delete forward" means removing the design package and the four personal-data files at the new branch tip going forward — it explicitly does **not** scrub them from history. That material is a public handle and cert fingerprints (§2.8), not secrets, so leaving it recoverable in history is not a privacy problem, and no second history rewrite is planned. |
| **Q7** | **A one-time importer for the on-device prefs key→link map — the premise needs correcting.** | The modern lowercase package never inherited the 2024 mixed-case app's `noteNFCURLs` map — Android's per-package-private `SharedPreferences` means it structurally could not have, and the preserved snapshot proves it (§7.5): only `notenfc.xml` and `storage_spike.xml` exist, no `noteNFCURLs.xml`. The map belonged solely to the 2024 app, `com.looseCannon.noteNFC`, which is confirmed **not installed** on the phone (§7.5, 2026-09-16). So there is no surviving key→link map anywhere on the device for a one-time importer to read, from a device backup or otherwise; the eight physical legacy tags (§7.5) are unresolvable by any means already on the phone. The modern line has **no** importer and D6 §3 (automatic prefs migration) was explicitly dropped — consistent with there being nothing left to import. |
| **Q8** | **The `contains("joplin")` gate.** | Keep it as the compatibility path (so re-sharing an old note reproduces the same MD5 key and the same tag resolves) while adding a proper scheme allowlist alongside it, or replace it outright and accept that re-sharing an old note may hash differently? The 2024 gate hashes the **whole shared text**; the modern `LinkLaunchPolicy.extractUri` stores **only the first URI token**, which is a different string and therefore a different key. The eight physical tags this gate produced (§7.5) are already unresolvable today regardless of this choice (Q7), which weakens the case for keeping the gate purely for tag-resolution compatibility. |
| **Q9** | **The `noteNFC-*` export prefix and the `notenfc.db` name.** | Both are cosmetic and safe either way: the importer never reads a file name, and a renamed package gets a fresh empty database. Keeping them is harmless but confusing post-rename; changing them means touching the exporter and the test fixtures that hard-code the prefix, and — if the prefix changes — the importer must continue to accept both (it does today, because it checks nothing). |
| **Q10** | **Whether the debug source set and the `storage_spike` prefs residue matter.** | `app/src/debug` is **3 files** (`AndroidManifest.xml`, a layout, `DebugBackupActivity.kt`; re-verified, corrected from an earlier "one file") whose manifest label is one of only two user-facing "noteNFC" strings. `shared_prefs/storage_spike.xml` holds one `tree_uri` key outside the `AppPrefs` contract — device-local residue from spike S5. Neither is load-bearing; the decision is whether each product carries a debug backup activity forward, and whether the residue is worth clearing on the phone during the transition. |
| **Q11** | **Scope of `nfc-tag-core`.** | Should the library also own the link-launch policy and the `notenfc://tag` deep-link route (both ServiceTag-side today, from `8a94872`), or does each app keep its own launch policy while the library stays strictly NDEF? The mechanism/policy table argues for strictly-NDEF: the scheme allowlist is a product decision and the deep-link scheme is product identity. |
| **Q12** | **Where the new repository lives.** | A fresh repo for the reconstruction, or does `noteNFC` keep the existing remote while ServiceTag moves? This determines whether the 2023 draft release and the `pre-split-checkpoint` tag travel with noteNFC or with ServiceTag — and note the active token **cannot delete a repository**, so any plan must be rename/create/transfer-only. |
| **Q13** | **The `NdefFormatable` capacity gap.** | A too-small *unformatted* tag surfaces as a generic `Failed`, not as `TooSmall`, because capacity is unknown until the tag is formatted. Small, real, and shared by both products — so it is a library-level decision whether to fix it (e.g. by reporting capacity after the format on the second tap) or to document it. |
| **Q14** | **The manifest ↔ constant link.** | `android:path="/com.loosecannon.notenfc:tag"` and `NdefCodec.V1_TYPE` are two independent literals today. Any parameterised `TagIdentity` must decide how the manifest string is kept in step — a manifest placeholder from the Gradle script, a generated resource, or a test that asserts the two agree. **This is a design deliverable, not only a question**: the split's design must bind the manifest filter path and the app-side `TagIdentity` to one Gradle-owned identity value, or add a build/test assertion that the two agree; external domain and AAR package stay separate parameters even when their values happen to be equal. |
| **Q15** | **`applicationId` collision with the live install.** | If the reconstructed narrow product took `com.loosecannon.notenfc` — the identity documented throughout §4 as today's live, installed app — it would collide with the running install: the same `versionCode` ratchet (an older `versionCode` cannot install over a newer one), the same signing-identity requirement (a mismatched key refuses to update in place), the same per-package SAF grant slot (§7.4: a persisted grant is scoped to the calling application and does not transfer), the same `shared_prefs` file, the same FileProvider authority, and overlapping NDEF manifest filters. None of this is settled by the archaeology; it only surfaces the collision. **Resolved outside this document by the owner's ruling O1** (recorded in §10): the narrow product is now **NoteTag**, `com.loosecannon.notetag`; nothing reuses `com.loosecannon.notenfc` once the modern app is uninstalled. |

---

## 9. Verification log and report discrepancies

Re-verified read-only against the tree at `ac523d7` (and at the named historical commits) while
writing this document: the two boundary commit subjects and dates; `c84b881`'s ancestor relationship
to master, its 30-commit ancestry and its zero merge commits; the 8 merge commits and 74-commit
first-parent line on master; the full first-parent log and every phase-landing SHA in §3;
`c84b881`'s `:core` file set, `applicationId`/`namespace`/SDK levels/versionCode, `app/src/main` file
list, tracked APK size, 116-file / 46-non-docs / 70-docs counts, `LegacyKey.kt` body, the
`noteNFCURLs` prefs name and the `contains("joplin")` gate; `3a3c69a`'s two tracked APKs and absent
`settings.gradle.kts`/wrapper; the four personal-data files and the `looseCannon`-in-12-files figure;
all of `NdefCodec.kt:30-53`; the `"V1"` literal at `MainActivity.kt:84`; the manifest's two
`vnd.android.nfc` paths, three `notenfc` scheme hosts and FileProvider authority; `DB_NAME` and
`SCHEMA_VERSION`; `app_name` and `Theme.NoteNfc`; both `NoteNfcApp.kt` files; the `5.json`
`identityHash`; the export prefixes; the eight files containing `notenfc://`; the reader-mode flag set
and the total absence of `SKIP_NDEF_CHECK`; the absence of `LegacyKey.compute` and of any MD5 in
`app/src`/`core/src`; the `1.json`…`5.json` schema set; the NFC permission and hard `uses-feature`;
and the Gradle wrapper version.

Four discrepancies were found. None changes a conclusion; all are recorded so a later reader is not
misled.

| # | Discrepancy | Resolution |
|---|---|---|
| **D1** | `archaeology-history.md`'s opening line says master is *"175 commits, fully linear — zero merge commits in the ancestry of any commit discussed below"*. **Master is not linear**: `git rev-list --merges --count ac523d7` = **8** (the phase-1b, 1c, 2a, 2b-1, 2b-2 and 4a merges plus two 4a follow-up merges). | The *scoped* claim is correct and is the one the report relies on: `c84b881`'s own ancestry has zero merge commits (verified). The unqualified "fully linear" applies only to the pre-boundary history. The lineage conclusion stands unchanged. |
| **D2** | `archaeology-history.md` §4 says `nfc-tag-core` must own a reader-mode session *"with `FLAG_READER_SKIP_NDEF_CHECK` + platform-sound suppression"*. This **directly contradicts** `archaeology-nfc.md` §24 and the code: the flag was deliberately **removed** in `e2cf1d0` because with it set `Ndef.get()` returns null and nothing can be written, and the class doc states the platform NDEF check is *"deliberately left ON"*. | **The NFC report and the code are right.** The history report's phrasing is an error — it names the flag as if it were desirable. The library invariant is **never `FLAG_READER_SKIP_NDEF_CHECK`** (§6.3 invariant 4). "Platform-sound suppression" is likewise not implemented anywhere in the tree and should be treated as an unexplored idea, not an existing capability. |
| **D3** | `archaeology-history.md` §4 cites *"`17cbXXX`/`26ec9d0` era work"* for the unsafe-`ACTION_VIEW` fix. `17cbXXX` is not a SHA. | The commit is `17cb682` ("readme: say what the app is now, not what it was in 2024"), the first parent of the phase-1b merge `19af5ae` — a README commit, not a safety fix. The substantive fix is `8a94872` (`LinkLaunchPolicy`), already cited alongside it. Treat the placeholder as a typo with no evidentiary weight. |
| **D4** | `archaeology-repo-build-identity.md` §9 reports *"2,028 occurrences across 213 files"* for `com.loosecannon.notenfc`. | The **occurrence count is exact** (re-verified: 2 028). The **file count is 236**, not 213 — 233 `.kt` files plus both manifests and `app/build.gradle.kts`. The headline conclusion (the identity is the namespace of the entire codebase, not a handful of call sites) is unaffected. |

One further correction, already made in the reports and repeated here because it contradicts a
standing session note rather than another report: **"legacy tags recoverable via deterministic MD5
key" is true of the protocol but not of the modern implementation** — `LegacyKey.compute` was deleted
in `26ec9d0` and there is no MD5 anywhere at `ac523d7`. See §6.4.

**This verification log was itself not exhaustive.** An independent review (§10) found two further
miscounts this document had inherited or introduced: the `docs/design/issues/` new-issue-draft count
(this document said 17; re-verified at 19, §4.9) and the per-phase evidence-file count carried in an
underlying report (stated there as 7; re-verified at 8, matching what §4.9 already said). Neither
changes a conclusion; both are corrected in place rather than added as a fifth discrepancy row, since
they are miscounts in this document's own numbers rather than disagreements between two source
reports.

Finally, two scoping notes on what this document does *not* claim:

- **Nothing here was observed on a device for this document.** No `adb` was run; a physical phone holding the owner's real data is attached, and every device claim above is sourced from a `docs/design/phase-*-evidence.md` row recorded in an earlier phase and tagged **[device-observed]**.
- `archaeology-data.md` notes that its per-package-SAF-grant statement was written from stable public API documentation rather than a byte-for-byte fetch in that session. It is tagged **[platform-doc]** above; a spec that needs a verbatim citation should fetch one.

---

## 10. Review record

**Gate result: PASS with corrections (independent reviewer, §34 #2 of the split ledger), 2026-09-16.**

Corrections applied to this document by number, from the reviewer's "Corrections to apply" list
(1–19), the "Additions the design needs" (1, 3, 4, 5, 6, 8), and the owner's corrections under the
Archaeology gate (C1, C2, C6, C7 — C1/C2 duplicate corrections 1/2; C3–C5, C8, C9 are design-document
corrections out of this document's scope):

1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19; additions 1, 3, 4, 5, 6, 8; C1, C2,
C6, C7.

**A note on scope.** This document's own findings (§§1–9) stop at `ac523d7`, 2026-09-16, and are
deliberately not revised in light of what came after. The owner's rulings **O1–O14** — the NoteTag
rename and greenfield NFC compatibility, the hybrid tag format, no default AAR, and a conservative
`nfc-tag-core` scope, among others — were made *after* this archaeology, in response to it and to the
target/migration design drafts, and are recorded in the design documents
(`docs/architecture/product-split-target.md`, `product-split-migration.md`), not here. Where a
correction above notes that a §8 open question is "resolved by" an owner ruling (Q7, Q15), that
resolution is recorded as a pointer forward, not as a rewrite of the question: **§8's open questions
stand as the historical question record** — what was unknown at the archaeology gate — and the design
documents are where the answers now live.
