---
action: create
title: "[MVP] Bind, rebind, revoke, and unknown-tag flows"
milestone: "Phase 1 — Tag survival (M1)"
labels: []
---

Split from #2 (D2 §5: "#2 (Room/Asset · tag payload format v1 + legacy resolver · bind UX)").

## Goal

The user-facing half of tag identity: binding a tag to an asset or link, replacing a lost tag without losing history, revoking the old one, and recovering a tag whose mapping the app no longer has.

## Scope

### Bind

- Bind a blank or `UNBOUND` tag to an existing asset or link, or create an asset from the bind flow.
- Pre-write a batch of blank tags as `UNBOUND` rows and bind them on first scan.
- Multiple tags per asset (the unit plus the breaker panel), each with its own `label` and scan history.
- Retarget an existing tag from a link to an asset by updating its row — no rewrite needed (D4 §3).

### Rebind and revoke

Replacing a lost or damaged tag must not lose history, which is what the binding table makes possible: the old row goes to `status = LOST` (scanning it says "revoked", with a re-activate option) and a new row is created for the new tag. The asset and its journal are untouched. `UNIQUE(payload_format, payload_key)` prevents two rows claiming the same identity.

### Unknown tag

Scanning a tag with no matching row offers: bind to an existing asset or link, create an asset, or — for a legacy key — **re-link by sharing the note** (D6 §5).

### Re-link (the tag-survival path)

Because the legacy key is `MD5(text)[0:8]`, a wiped mapping is rebuildable:

1. Scan → "Unknown legacy tag `3f9a1c2e`. If this tag pointed at a Joplin note, share that note's external link to noteNFC and the tag will be recognised automatically."
2. On a share, the key is computed for **both** the raw shared text and the extracted URI (the old app hashed the raw text, which may or may not have included a title) and compared against the remembered recent unknown scans.
3. On a match, the link and the `LEGACY_MD5` row are created exactly as the automatic migration would have.
4. On no match, the share creates a normal standalone link that the user can bind the unknown tag to manually; the row then stores the **scanned** key and the tag is not rewritten.

**Bulk "Recover legacy tags"** screen: share several notes in a row; each computes its key and is stored immediately, whether or not that tag has been scanned yet. This is the recommended first step after a reinstall (situation B, D6 §2) and the practical mitigation for D8 risk 1.

### Error and collision surfaces (D6 §6)

Two different links sharing an 8-hex prefix: the `UNIQUE` constraint refuses the second row, the share flow explains that this note cannot be recovered onto a legacy key, and offers to write a new v1 tag instead. A stored value that is not a URI is flagged `NEEDS_REVIEW` and shows a link card instead of launching. A missing handler app shows "no app can open this link" with the URI. An Evernote-era record type is `Foreign`; the app offers to write a new `:tag` record over it (ruling R-10: no special handling beyond that).

## Out of scope

The record format, codec and resolver are `new-tag-payload-v1-legacy-resolver`. The automatic first-launch migration of `noteNFCURLs` is #2 (D6 §3). Link validation is #6.

## Visual design

D12 §8 Asset Identity Plate, §11 NFC states — an unknown tag is **not an error** (neutral/amber informational treatment, never error red), and a legacy tag reads as a **migration opportunity**, not damaged data.

## Acceptance criteria

1. A blank tag binds to an existing asset and resolves to it on the next scan.
2. Marking a tag lost and binding a replacement leaves the asset's history intact; scanning the old tag says "revoked" and offers re-activation.
3. An unknown legacy tag plus a share of the original Joplin note recreates the mapping, with and without a title in the shared text (`RelinkTest`).
4. The bulk recovery screen creates `LEGACY_MD5` rows for notes whose tags have not been scanned yet, and those tags resolve on first scan.
5. A legacy-key collision is refused with an explanation and an offer to write a v1 tag.
6. An asset can carry two tags, each separately labelled and revocable.

## Design references

D2 §5 (split of #2) · D4 §3 (`nfc_tag`, binding rationale), §13 (lose tag, unbind tag) · D6 §2 (situations), §4 (resolutions), §5 (re-linking and bulk recovery), §6 (collision and error handling) · D7 Phase 1B, 1C · D8 risk 1, risk 11, rulings R-2, R-10 · D12 §8, §11.
