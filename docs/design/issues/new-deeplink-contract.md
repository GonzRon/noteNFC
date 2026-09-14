---
action: create
title: "[MVP] notenfc:// deep-link contract (navigation-only, validated)"
milestone: "Phase 1 — Tag survival (M1)"
labels: []
---

Split from #9 (D2 §5: "#9 (OAuth/token · projection · deep links → foundational · provider UX)"). Also identified in D2 §4 item 3: the contract is consumed by local notifications long before Todoist exists, so it cannot live inside the Todoist issue.

## Goal

One versioned, validated, navigation-only deep-link contract for the app, owned centrally.

## Scope (D3 §13)

Scheme `notenfc://`, versioned by path shape:

| URI | Opens |
|---|---|
| `notenfc://asset/<uuid>` | asset detail |
| `notenfc://asset/<uuid>/schedule/<uuid>` and `notenfc://schedule/<uuid>` | schedule detail |
| `notenfc://event/<uuid>` | event detail |
| `notenfc://tag/<uuid>` | resolves as if the tag had been scanned |
| `notenfc://health` | reminder health |

### Rules

- **Navigation only.** No URI ever performs a mutation. Complete, snooze and postpone are taps inside the app, never link targets (security doc, "Deep links").
- UUIDs are validated by shape **and** existence; a malformed or unknown id shows a toast and lands on Home.
- Handled by the single-activity host through Navigation 3; deep-link **extras are ignored** — only the URI is read.
- Path shape is the version: a new shape is a new route, never a silently reinterpreted old one.

### Consumers, in order of arrival

1. Phase 1 — the tag route from `NfcDispatchActivity` and the asset/link routes from the Compose shell.
2. Phase 3 — local notification `Open` actions (#11) and the reminder-health route.
3. Phase 5 — Todoist task descriptions (#9) and `new-todoist-link-actions`.

### Known limitation

A custom scheme can be claimed by another app; the worst case is navigation hijack, and nothing sensitive is in a URL. HTTPS App Links would close this and make links tappable in any client, but require a domain and a hosted `assetlinks.json` — design for it, do not block on it (ruling R-4; ledger A17). Whether `notenfc://` links are even tappable inside Todoist is spike S3.

## Out of scope

**Outbound** link launching (`joplin://`, `obsidian://`, `https://`) is a separate path owned by #6's `LinkLaunchPolicy` and is unaffected by this contract.

## Acceptance criteria

1. Every route in the table above resolves to its screen from an external intent.
2. A malformed UUID, and a well-formed UUID that does not exist, both land on Home with a toast and no crash.
3. No `notenfc://` URI can complete, snooze, postpone, delete or otherwise mutate anything — asserted by test.
4. Extras attached to a deep-link intent are ignored.
5. `notenfc://tag/<uuid>` produces the same resolution as physically scanning that tag.

## Design references

D2 §4 item 3 · D2 §5 (split of #9) · D3 §13 (deep links), §9 (dispatch) · D7 Phase 1B, 1C · D8 ruling R-4, spike S3, ledger A17, risk 16 · security doc, "Deep links" and "Exported components".
