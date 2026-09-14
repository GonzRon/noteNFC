# D13 — Compatibility policy change and package identity (2026-09-14, after Phase 0)

Status: **binding ruling from the product owner**, recorded after Phase 0 completed. It changes
the governing principle of D6, relaxes parts of D1 §9, D7 Phase 1, D8, and the issue set. Nothing
in Phase 0 is reopened; the archaeology and characterisation remain the record of what existed.

## 1. Governing rule (replaces "preserve compatibility unless impossible")

> Preserve legacy compatibility when it is inexpensive and does not constrain the new design. If
> compatibility conflicts with a cleaner architecture, safer implementation, better UX, or a
> materially simpler system, favour the new design and make the breaking change.

Production requirement going forward:

> Newly provisioned noteNFC assets and tags must survive phone replacement, application
> reinstall, and backup/restore with stable identity. Legacy installations and tags do not need to
> constrain that design.

Facts behind the ruling: there are no production users whose installed data or tags need
protecting; the owner intends to replace the existing tags with hardened outdoor tags provisioned
in payload format v1; the historical signing key is absent and a clean reinstall is the expected
cutover.

## 2. What each legacy mechanism becomes

| Mechanism (where it was specified) | Cost to keep | Constrains the new design? | Decision |
|---|---|---|---|
| Legacy `md5_short` **decode** in `NdefCodec` (`:core`, D6 §4, shipped in Phase 0) | ~20 lines + 5 tests, isolated | No | **Retain** — best-effort recognition so a scanned old tag shows "Legacy tag: rewrite in format v1 or bind" instead of "foreign tag" |
| `md5_short` `NDEF_DISCOVERED` intent filter (D1 §9) | one manifest line | No | **Retain** while the decoder exists |
| `nfc_tag.payload_format = LEGACY_MD5` rows (D4 §3) | one enum value | No | **Retain** — lets a user bind an old tag without rewriting it, if they choose; not required by any flow |
| `TECH_DISCOVERED` catch-all filter (D1 §2 item 8) | — | Yes (chooser noise, silent finish) | **Drop** in Phase 1B (already planned) |
| Automatic `SharedPreferences("noteNFCURLs")` migration (D6 §3, D7 1A) | migration code, report UI, fixtures, snapshot, deletion timeline | Yes (Phase 1A scope) and **impossible after the package rename** (a new package cannot read the old app's data directory) | **Drop** |
| Re-link an unknown legacy tag by re-sharing the note; bulk "Recover legacy tags" (D6 §5) | `LegacyKey`, matching logic, pending-scan memory, a screen, `RelinkTest` | Yes (Phase 1B scope; dictates share-flow behaviour) | **Drop** |
| `LegacyKey.compute` in `:core` (Phase 0) | already written, 6 tests | No, but dead once re-link is dropped | **Remove in Phase 1B** together with `LegacyLinkPolicy` (replaced by `LinkLaunchPolicy`); the vectors stay in D1/D6 as protocol documentation |
| Legacy-key collision handling (D6 §6) | branching in share/bind flows | Yes | **Drop** (no legacy keys are ever generated again) |
| Prefs retention/deletion timeline (D6 §9) | releases-long bookkeeping | Yes | **Drop** |
| Rewrite a legacy tag in payload format v1 (D6 §7) | reuses the normal writer with a confirmation | No | **Retain** as the "Legacy tag" action |
| Situation A / in-place update path; R-1 keystore work (D6 §2, D8 R-1) | — | Was shaping Phase 0/1 | **Closed** — historical information only; clean install is the cutover |
| On-device "old-APK tag on new build / new-build tag on old APK" check (D7 Phase 0/1B exits) | a phone session | Yes (was a release gate) | **Optional sanity check**, not a gate |
| Evernote-era record types (R-10) | — | — | unchanged: ignored |
| Share → noteNFC → write-tag workflow; "tag carries identity, not state"; lightweight external links | — | No (good ideas) | **Retain** as design principles (D3 §9–§10) |

Net effect on Phase 1: 1A loses the migration component and its tests; 1B loses the re-link
flow, `RelinkTest`, and the recovery screen; 1C loses the migration report; the "no backup yet"
nudge stays because it serves the *new* production requirement.

## 3. Legacy tag behaviour that remains (best-effort)

```
scan → NdefCodec.decode → LegacyMd5(key)
     → nfc_tag row with payload_format = LEGACY_MD5 exists?  yes → resolve as any tag
                                                            no  → "Legacy tag" screen:
                                                                  [Rewrite in format v1 for asset/link …]  [Bind as-is]  [Ignore]
```

No lookup of old data is attempted; there is none to look up.

## 4. Package and application identity: normalise to `com.loosecannon.notenfc`

Ruling: **normalise now**, as the first commit of Phase 1A, before the first Room schema, backup
manifest, deep-link host, or AAR is written. Consequences enumerated:

| Surface | Effect of `com.looseCannon.noteNFC` → `com.loosecannon.notenfc` | Action |
|---|---|---|
| Android package identity | A different package: the new app installs **beside** the old one; it cannot update it and cannot read its data directory. Both stay installed until the old one is uninstalled | Cutover = install new, uninstall old. Document in the evidence/cutover note |
| `applicationId` / `namespace` / `R` class | Both change; Kotlin sources under `app/src/main/java/com/looseCannon/noteNFC` move to `app/src/main/kotlin/com/loosecannon/notenfc/…` (`:core` already uses that root) | One mechanical commit; `git mv` |
| NFC external record domain | Already lower-case `com.loosecannon.notenfc` on the wire (the framework lower-cases); the `:tag` and `md5_short` types are unaffected | none |
| Android Application Record (AAR) | Carries the package name; new tags get `com.loosecannon.notenfc`; old tags have no AAR | none |
| NFC dispatch during coexistence | Both apps match `md5_short`; Android shows a chooser until the old app is removed; `:tag` records match only the new app | uninstall old app at cutover |
| Deep links `notenfc://` | Scheme-based, package-independent | none |
| App Links / OAuth (if ever, R-4) | `assetlinks.json` binds to the new package + new signing cert | future |
| Backups | Manifest records app version; no package coupling | none |
| Signing | A **new** release keystore is created for the new package and kept outside the repo (document location in the README); the `fillMateAndroid` key is irrelevant | Phase 1A prelude |
| Auto Backup | Per-package; nothing from the old app restores (irrelevant) | none |
| Distribution | Conventional lower-case id; if Play/F-Droid ever, it is a new listing | none |
| SharedPreferences / DataStore names | Free to choose; no legacy names | none |
| Version numbers | Restart at `versionCode 1`, `versionName "2.0"` for the new package (the "1.1" from Phase 0 belonged to the old package) | Phase 1A prelude |

Rejected alternative: keep `com.looseCannon.noteNFC` to allow in-place update — worthless
without the signing key, and it perpetuates an accidental mixed-case identifier.

## 5. Document and issue changes made under this ruling

- D1 §9: constraints 1–5 marked relaxed; only the record-type facts remain.
- D3: §14 rewritten (best-effort legacy, package identity); §15 applicationId row; ledger A21
  added (package identity), A3/A4 annotated.
- D4 §3: `LEGACY_MD5` retained as best-effort; §15 version plan unchanged.
- D6: banner marking §3, §5, §6, §9 dropped and §4/§7 reduced; kept for the record.
- D7: Phase 0 exit (2) and Phase 1A/1B/1C criteria rewritten; 1A prelude = package rename.
- D8: R-1 closed; risk 1 downgraded to historical; rulings R-14 (policy) and R-15 (package id)
  recorded; §5 updated.
- Testing doc: `LegacyPrefsMigrationTest`/`RelinkTest` removed; legacy codec tests kept.
- Evidence doc: on-device legacy check re-labelled optional.
- GitHub: #2 (no prefs migration), #31 (legacy resolver best-effort, no re-link), #30 (no re-link
  flow), #1 (policy note), #29 closed as completed (investigation done; outcome no longer a
  blocker) — each with a dated "Policy update" section, originals in edit history.
