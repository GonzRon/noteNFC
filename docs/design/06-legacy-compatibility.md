# D6 — Legacy compatibility plan

Status: design-phase document, 2026-09-14. **Partly superseded by D13** (compatibility policy
change after Phase 0): §3 automatic migration, §5 re-link / bulk recovery, §6 collision
handling, and §9 timeline are **dropped**; §1, §4 (resolver, reduced to "recognise and offer
rewrite/bind"), and §7 (rewrite) are retained as best-effort behaviour; §2 situation A is closed.
Kept for the record of what was analysed. Original text follows. Moves the app from

```
8-hex key on tag  ──▶  SharedPreferences("noteNFCURLs")[key]  ──▶  Joplin URI  ──▶  ACTION_VIEW
```

to the D4 model without casually breaking a single existing tag.

---

## 1. What exists on physical tags today (D1 §4)

- One NDEF record: `TNF_EXTERNAL_TYPE`, type `com.loosecannon.notenfc:md5_short`, payload = 8
  ASCII lower-case hex characters = `MD5(shared text)[0:8]`.
- No AAR, no other records. Tags may be NTAG21x or anything NDEF-formattable.
- Possibly, but unverified, older tags with type `com.loosecannon.evernotenfc:*` from the
  2023 package; those were never resolvable by the current app and are out of scope unless you
  report they exist (ruling in D8).

## 2. Two starting situations

| Situation | Cause | What the new app can read |
|---|---|---|
| **A. In-place update** | you still hold the release keystore (`CN=fillMateAndroid`) or the debug keystore that signed the installed APK | `noteNFCURLs` is intact; automatic migration |
| **B. Reinstall** | signature differs → uninstall + reinstall (D1 §6); Auto Backup restore also requires a matching signature | prefs are gone; tags are "unknown legacy tags" until re-linked |

The plan handles both. Situation B is survivable only because the legacy key is deterministic.

## 3. Automatic migration (situation A)

Runs once, on first launch after upgrade, inside one Room transaction, after taking an automatic
snapshot of the prefs file (copied to `noBackupFilesDir/legacy/noteNFCURLs.<ts>.xml`).

```
for (key, value) in prefs("noteNFCURLs"):
    if key !~ /^[0-9a-f]{8}$/            → record in migration report as SKIPPED_BAD_KEY; continue
    uri = LinkLaunchPolicy.extractUri(value)     // first URI token; Joplin share text may contain a title
    if uri == null                       → link kind OTHER with raw value, flagged NEEDS_REVIEW
    link = ExternalLink(id = uuid(), asset_id = null, kind = detect(uri), label = defaultLabel(kind, uri), uri = uri)
    tag  = NfcTag(id = uuid(), payload_format = LEGACY_MD5, payload_key = key, link_id = link.id,
                  status = ACTIVE, label = "Legacy tag " + key)
    assert MD5(value)[0:8] == key         // sanity; mismatch → flagged (value was edited by hand?)
prefs file is left untouched; preference flag legacy_migrated_at set
```

The migration report is shown once ("12 links imported, 1 needs review") and stored in
preferences. The prefs file is deleted only two releases later, and only after a successful backup
export has been recorded at least once (belt and braces).

Duplicate values: two prefs keys cannot share a key, but two keys can map to the same URI only if
the stored text differed (e.g. once with a title). Both become separate links; the report notes
"2 links point to the same note" and offers to merge (tags are retargeted to one link).

## 4. Compatibility resolver (both situations, forever)

`NdefCodec.decode` recognises the legacy record type and yields `LegacyMd5(key)` after validating
`^[0-9a-f]{8}$`. `ResolveTag` looks up `nfc_tag WHERE payload_format = 'LEGACY_MD5' AND
payload_key = ?`:

| Row found | Resolution |
|---|---|
| bound to a link | launch the link (original behaviour, no UI) |
| bound to an asset | open the asset (the user retargeted the tag) |
| status LOST/RETIRED | "This tag was marked lost/retired" + re-activate option |
| UNBOUND | bind flow |
| **no row** | **Unknown legacy tag** screen (§5) |

The `NDEF_DISCOVERED` filter for `vnd.android.nfc://ext/com.loosecannon.notenfc:md5_short` is
kept permanently; there is no plan to remove read support for legacy tags. On targetSdk 37 the
dispatch activity additionally declares `android.permission.DISPATCH_NFC_MESSAGE`.

## 5. Re-linking an unknown legacy tag (situation B, and any lost mapping)

The key is `MD5(text)[0:8]` where `text` is exactly what was shared. So:

1. Scan → "Unknown legacy tag `3f9a1c2e`. If this tag pointed at a Joplin note, share that note's
   external link to noteNFC and the tag will be recognised automatically."
2. The user shares the link from Joplin. `LegacyKey.compute(sharedText)` is compared with every
   pending unknown key (the app remembers the last N unknown scans) **and** the share flow also
   computes the key for the raw text and for the extracted URI (the old app hashed the raw text,
   which may or may not have included a title; both candidates are tried).
3. On a match the app creates the link + `LEGACY_MD5` tag row exactly as §3 would have.
4. No match → the share creates a normal standalone link; the user can bind the unknown tag to it
   manually, in which case the row stores the *scanned* key (the tag is not rewritten).

Bulk variant: "Recover legacy tags" screen: share several notes in a row; each computes its key
and is stored as a `LEGACY_MD5` tag row immediately, whether or not that tag has been scanned yet.
Scanning later resolves it. This is the recommended first step after a reinstall.

The MD5 function is preserved verbatim in `:core` (`LegacyKey.compute`) and pinned by a test with
a known vector taken from the current implementation (`digest.fold("") { … "%02x" }.substring(0, 8)`
over UTF-8 bytes).

## 6. Collision and error handling

| Case | Old app | New app |
|---|---|---|
| Two different links with the same 8-hex prefix | silent overwrite; older tag opens newer note | `UNIQUE(payload_format, payload_key)` refuses the second row; the share flow detects the collision (`~1.2×10⁻⁴` at 1 000 links) and explains that this note cannot be recovered onto a legacy key; the user writes a new `:tag` record instead |
| Same link shared twice | same key, one entry | one `LEGACY_MD5` row; a second physical tag with the same key resolves to the same row (indistinguishable duplicates, as before) |
| Stored value not a URI | crash on scan | link flagged NEEDS_REVIEW; scan shows the link card instead of launching |
| Handler app missing (Joplin uninstalled) | crash | "No app can open joplin:// links" with the URI shown |
| Key with upper-case or wrong length on a tag | prefs miss → toast | `Malformed` payload → explanation screen |
| Evernote-era record type | silent finish | `Foreign` payload; offer to write a new `:tag` record over it |

## 7. Upgrading a legacy tag (optional, never required)

From the tag's row: "Rewrite this tag in payload format v1". The writer reads the tag, confirms it
carries the expected legacy key, writes the `:tag` + AAR message, reads back, then updates the row
in one transaction: `payload_format = V1`, `payload_key = <new uuid>`, `id` unchanged. The old key
is not kept as an alias because the old payload no longer exists on that tag. If the write fails
after the erase (rare), the row is left unchanged and the user is told to retry; the tag is
recoverable by rewriting.

## 8. What changes for the share-sheet entry point

The `ACTION_SEND text/plain` filter stays on the launcher activity. Behaviour becomes: extract the
URI, classify it, show a small card ("Joplin note — write to a tag / attach to an asset / keep as
link"), default action "write to a new tag" to preserve the one-tap flow. Return-to-caller after
writing is preserved (`finish()` after write, as `0652023` intended).

## 9. Timeline

| Release | Legacy behaviour |
|---|---|
| Phase 1 | automatic migration; resolver; re-link flow; prefs untouched |
| Phase 1 + 2 releases | prefs file deleted after a confirmed backup export exists |
| Forever | `md5_short` read support; `LegacyKey` retained |

## 10. Tests that guard this plan

- `LegacyKeyTest`: known vectors; UTF-8 handling; the exact truncation.
- `NdefCodecTest`: legacy record decode (valid, upper-case, wrong length, non-hex, extra records,
  empty message), v1 encode/decode round-trip, unknown version, malformed flags.
- `LegacyPrefsMigrationTest` (Robolectric, in-memory Room): sample prefs XML fixtures including a
  value with a title prefix, a non-URI value, and a bad key; asserts rows, report, and that the
  prefs file is untouched.
- `ResolveTagTest`: every row in §4's table.
- `RelinkTest`: share text with and without title recovers the scanned key; collision path.
- Manual device check (Phase 1 acceptance): a tag written by the *old* APK resolves on the new build
  in both situations A and B.
