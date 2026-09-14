---
action: rewrite
number: 6
title: "[MVP] Generalize external note/deep-link support beyond Joplin"
milestone: "Phase 1 — Tag survival (M1)"
labels: []
---

> Revised 2026-09-14 per the approved design package (`docs/design/`, review 1). Original text is preserved in this issue's edit history and in `docs/design/issues/original/issue_6.md`. Design references: D2 §1 (row 6), D2 §3 (C7), D2 §5; D3 §10; D4 §4; D6 §8; security doc ("Outbound URI launching").

Kept, re-tiered to MVP-1, with "validate" defined concretely and the standalone-link question decided.

## Goal

Preserve noteNFC's original purpose while making external-link handling generic instead of Joplin-specific.

## Requirements (unchanged)

- An asset can have zero or more named external links.
- Keep Joplin support and migrate existing saved mappings (the migration itself is #2 / D6 §3).
- Support generic Android `ACTION_VIEW` URIs so other note apps work without hard-coding each provider.
- Validate and preview a URI before saving it.
- An explicit "Open external note" action from an asset.
- Keep the lightweight original workflow: share a compatible URI to noteNFC → write it to a tag → scan → launch the target app.

## "Validate" defined (D3 §10, security doc)

`LinkLaunchPolicy` lives in `:core` and runs at save time and at launch time:

1. **Scheme allowlist** — `joplin`, `obsidian`, `logseq`, `http`, `https` are accepted directly. Any other scheme requires a one-time explicit user confirmation before it can be saved.
2. **Hard block list** — `javascript`, `file`, `content`, `intent`, `android-app`, `tel`, `sms`, `mailto` are rejected at save time and never launched.
3. **Handler-present check** — the manifest declares `<queries>` for `joplin`, `obsidian`, `logseq`, `http`, `https` (required on API 30+), so the app can say "no app installed for this link" instead of failing opaquely. `ActivityNotFoundException` is caught regardless.
4. **Share-text parsing** — the first URI token is extracted from shared text (Joplin's share may include a title); the raw text is **never** stored as the URI.

Kind detection at save time (editable afterwards): `JOPLIN` (scheme `joplin`, host `x-callback-url`, path `/openNote` with a 32-hex id), `OBSIDIAN` (action `open` with `vault` + `file`/`path`), `LOGSEQ` (host `graph`), `WEB` (`http`/`https`), `OTHER`.

The broader policy for untrusted input across tags, deep links and stored URIs is `new-untrusted-input-policy`.

## Standalone links are first-class tag targets (contradiction C7)

`external_link` may have `asset_id = NULL`; such a link is a standalone record and a tag can bind to it directly (D4 §3, §4). Scanning a link tag **launches the link immediately**, with no interstitial — the original product behaviour — with a preference to show a card first instead (ruling R-7). Attaching a standalone link to an asset later just sets `asset_id`; tags bound to the link keep working and may optionally be retargeted.

This is why #1's and #2's "a tag points to an Asset" is now "a tag points to an asset **or** a link".

## Share-sheet entry point (D6 §8)

The `ACTION_SEND text/plain` filter stays. Behaviour becomes: extract the URI, classify it, show a small card ("Joplin note — write to a tag / attach to an asset / keep as link") whose default action is "write to a new tag", preserving the one-tap flow, and return to the caller after the write.

## Acceptance criteria

1. A `joplin://` URI, an `obsidian://` URI and an `https://` URI all save, preview and launch.
2. A `javascript:` or `intent:` URI is refused at save time with an explanation.
3. An unknown scheme is saved only after an explicit confirmation.
4. With the target app uninstalled, launching shows "no app can open this link" and the URI, and does not crash.
5. Shared text containing a title plus a URI stores only the URI.
6. Scanning a tag bound to a standalone link launches it immediately and returns to the caller.

## Visual design

D12 (Apollo Service Binder) §11 NFC states, §8 Asset Identity Plate — a link tag launches immediately with no interstitial, and the link card (share-sheet entry, needs-review links, missing-handler messages) uses informational treatment, never error red, for anything that is merely unrecognised. Acceptance: link surfaces use the semantic tokens from `new-design-system-foundation` and stay readable in grayscale.

## Design references

D2 §1 row 6 · D2 §3 C7 · D2 §5 · D3 §10 (external links, `LinkLaunchPolicy`, `<queries>`) · D4 §3 (binding), §4 (`external_link`) · D6 §8 (share-sheet entry point) · security doc, "Outbound URI launching" · D8 ruling R-7.
