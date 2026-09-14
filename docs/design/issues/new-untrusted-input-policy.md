---
action: create
title: "[MVP] Untrusted input policy: tag payloads, deep links, stored URIs"
milestone: "Phase 1 — Tag survival (M1)"
labels: []
---

Identified in D2 §4 item 16 (Security / privacy: untrusted input policy — tag payloads, deep links, stored URIs, Todoist content). Not present in issues #1–#16.

## Goal

One stated policy, applied consistently, for every input that comes from outside the app: an NFC tag, an incoming intent, a stored URI, a shared text, and (later) remote task content.

## Threat model in one sentence

The adversary is anyone who can present an NFC tag or send an intent to the phone, any app on the device that can register a custom scheme or start an exported component, whoever obtains a backup file, and the user's own mistakes. There is no server and no account, so network-facing risk is limited to the optional Todoist client.

## Policy

### Tag payloads (Phase 1)

Tags carry an identifier and nothing else. `NdefCodec` validates TNF, record type, exact payload length (18 bytes), version byte, zero flags, and — for legacy — the `^[0-9a-f]{8}$` charset. Anything else is `Malformed` or `Foreign`: it is **shown, never acted on**. Parsing is bounded; extra records are ignored. **No string read from a tag is ever used as a URI.**

### NFC dispatch (Phase 1, hardened Phase 7)

External-type filters are namespaced by our domain and the AAR pins the package, so a competing claim can only produce a chooser. The `TECH_DISCOVERED` catch-all is removed. On targetSdk 37 the dispatch activity is protected by `android.permission.DISPATCH_NFC_MESSAGE` (Phase 7).

### Deep links (Phase 1)

`notenfc://` is navigation-only; ids are validated by shape and existence; extras are ignored. Detail is `new-deeplink-contract`.

### Stored and outbound URIs (Phase 1)

`LinkLaunchPolicy`: scheme allowlist (`joplin`, `obsidian`, `logseq`, `http`, `https`), one-time confirmation for any other scheme, and a hard block list (`javascript`, `file`, `content`, `intent`, `android-app`, `tel`, `sms`, `mailto`). `<queries>` for handler checks; `ActivityNotFoundException` always caught. Shared text is parsed for its first URI token; the raw text is never stored as a URI. Detail is #6.

### Exported components (Phase 1 and 3)

Only the launcher/deep-link host and the NFC dispatch activity are exported. The quick-action receiver and the boot/time receivers are **not** exported, are addressed with explicit intents, and use `FLAG_IMMUTABLE` PendingIntents. There is no exported ContentProvider.

### Notification actions (Phase 3)

Every action carries a random per-notification nonce, stored in-process/DataStore and checked by the receiver, so a forged broadcast cannot complete a schedule.

### Remote provider content (Phase 5)

Todoist content is data: displayed as text, never linkified inside noteNFC, never interpreted as a command. Only `checked` / activity-log completions, `due.date`, the recurrence string (verified, never adopted) and `is_deleted` are interpreted. A remote due date that disagrees with the canonical computation is corrected, not trusted.

### Logging (all phases)

No URIs, serial numbers or tokens in logcat at INFO or above; debug logging is behind `BuildConfig.DEBUG`.

## Acceptance criteria

1. A malformed, foreign, truncated, over-long or wrong-version NDEF payload each produces an explanatory screen and no navigation, no launch and no write.
2. A blocked scheme cannot be saved as a link, and an unknown scheme requires explicit confirmation.
3. An intent sent to a non-exported receiver from another app has no effect; an exported-component audit lists exactly the launcher/deep-link host and the NFC dispatch activity.
4. A notification action broadcast with a stale or absent nonce creates nothing.
5. No URI, serial number or token appears in logcat at INFO or above in a release build.

## Design references

D2 §4 item 16 · security doc (threat model; "NFC payload", "NFC dispatch", "Tag writing", "Deep links", "Outbound URI launching", "Exported components", "Notification actions", "Todoist content", "Logging") · D3 §9 (codec boundary), §10 (`LinkLaunchPolicy`), §13 (deep links) · D6 §6 (error handling) · D7 Phases 1, 3, 5, 7.
