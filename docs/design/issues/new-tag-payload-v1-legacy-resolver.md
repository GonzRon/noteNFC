---
action: create
title: "[MVP] noteNFC tag payload format v1 and legacy md5_short resolver"
milestone: "Phase 1 — Tag survival (M1)"
labels: []
---

Split from #2 (D2 §5: "#2 (Room/Asset · tag payload format v1 + legacy resolver · bind UX)"). Also identified in D2 §4 item 2 — nothing in the original issue set says how a scanner tells a legacy link tag from an asset tag.

## Goal

Specify and implement the **noteNFC tag payload format v1** — the new-generation replacement for the legacy `md5_short` record — and one resolver that handles both formats forever.

## Scope

### Payload format v1 (D4 §3)

```
NDEF message
  record 0: TNF_EXTERNAL_TYPE, type "com.loosecannon.notenfc:tag"
            payload (18 bytes):
              byte 0       format version = 0x01
              byte 1       flags          = 0x00 (reserved, must be 0)
              bytes 2..17  tag id, RFC 4122 byte order (16 bytes)
  record 1: Android Application Record for com.loosecannon.notenfc (the applicationId, D13 §4)
```

- Unknown version byte (> `0x01`) → "written by a newer noteNFC; update the app"; **never parsed**.
- Version `0x00`, wrong length, or non-zero flags → rejected as `Malformed`.
- ~95 bytes with the AAR and TLV overhead; fits an NTAG213 (144 B).
- "v1" is the payload version byte, never an application generation.

### The codec (D3 §9)

`core.nfc.NdefCodec` is pure Kotlin over `ByteArray` and lives in `:core`; Android `NdefRecord` objects are built only in the `nfc` adapter. It decodes to:

```
TagPayload = V1(tagId) | LegacyMd5(key) | Foreign(summary) | Malformed(reason) | NewerVersion(n)
```

> Policy update (D13, 2026-09-14): `LegacyKey` was removed in Phase 1B; the MD5 vectors remain in D1/D6 as protocol documentation.

### The resolver (D6 §4)

`ResolveTag(payload)` looks up `nfc_tag` by `(payload_format, payload_key)` and yields:

| Row | Resolution |
|---|---|
| bound to a link | launch the link immediately — original behaviour, no interstitial |
| bound to an asset | open the asset |
| `LOST` / `RETIRED` | "this tag was marked lost/retired" with a re-activate option |
| `UNBOUND` | bind flow |
| no row | unknown tag (legacy → re-link offer; v1 → bind offer) |

Legacy tags keep record type `com.loosecannon.notenfc:md5_short` and are **read-only aliases**. The `NDEF_DISCOVERED` filter for `vnd.android.nfc://ext/com.loosecannon.notenfc:md5_short` is kept permanently; there is no plan to remove legacy read support.

### Dispatch and writing (D3 §9)

- Add the `:tag` `NDEF_DISCOVERED` filter alongside the legacy one; **remove the `TECH_DISCOVERED` catch-all** (D1 §7: it was a workaround, not a feature).
- In-app scanning and writing use reader mode (`enableReaderMode`), not foreground dispatch; background scans still arrive through `NfcDispatchActivity`.
- Writer: read the tag first; confirm before overwriting a different noteNFC payload or foreign NDEF content; check `Ndef.maxSize`; write off the main thread; **read back and compare**; optional lock with an irreversible warning. Records: the `:tag` external record first, the AAR second.
- Optional legacy upgrade (never required): rewrite a legacy tag in payload format v1, then update the row to `payload_format = V1` with a new `payload_key` and the `id` unchanged, in one transaction (D6 §7).

## Out of scope

The bind / rebind / revoke / unknown-tag **UX** is `new-tag-bind-rebind-ux`. The `notenfc://tag/<uuid>` route is `new-deeplink-contract`. Input-validation policy across surfaces is `new-untrusted-input-policy`.

## Acceptance criteria (D7 Phase 1B)

1. A v1 payload round-trips with the exact byte layout asserted; unknown version, wrong length and non-zero flags each produce their distinct outcome.
2. Every row of D6 §4's resolution table is covered by `ResolveTagTest`.
3. An NTAG213 holds the v1 message and reads back byte-identical on the device.
4. A tag holding foreign NDEF content triggers the overwrite confirmation and is not written without it.
5. An old-APK tag resolves on the device in situation A and, after a deliberate data wipe, in situation B via re-link.
6. Scanning with the app closed opens it through `NfcDispatchActivity`.

## Design references

D2 §4 item 2 · D2 §5 (split of #2) · D3 §9 (codec and resolver boundary) · D4 §3 (`nfc_tag`, payload format v1) · D6 §1 (what is on tags today), §4 (resolver), §6 (collisions and errors), §7 (optional upgrade), §10 (tests) · D7 Phase 1B · D8 spike S2, ledger A4 · security doc, "NFC payload" and "NFC dispatch".
