---
action: create
title: "[NEXT] Todoist authentication: personal API token first, OAuth later"
milestone: "Phase 5 — Todoist"
labels: []
---

Split from #9 (D2 §5: "#9 (OAuth/token · projection · deep links → foundational · provider UX)"). The original issue specified OAuth; ruling **R-3** replaced that for the first release of the integration.

## Goal

Connect a Todoist account securely, with an authentication mechanism that works for a public Android client today and does not have to be rebuilt when OAuth becomes possible.

## Why not OAuth first (D3 §8, ruling R-4)

OAuth for a public client needs either an embedded client secret (which is not a secret in a distributed APK) or an HTTPS redirect domain with PKCE. noteNFC has no domain. **Personal API tokens are an officially supported alternative** for exactly this case, so the integration ships with them and OAuth is added later if a domain becomes available — the same prerequisite as HTTPS App Links (ruling R-4, revisit before Phase 5).

## Scope

- Todoist appears as an optional integration in Settings with **connect, connection status and disconnect** controls.
- **Personal API token** pasted from Todoist Settings → Integrations → Developer. The entry field is masked.
- The token is stored through `SecretStore`: an AES-GCM key in the Android Keystore (`setUserAuthenticationRequired` **off**, because background sync must work), ciphertext in `noBackupFilesDir`, excluded by `dataExtractionRules`, never in an export, never logged.
- `integration_account` (D4 §10) carries the account identity plus `auth_kind`, so OAuth can be added behind the same field without touching the provider.
- Token validity is verified at connect time, and a `401`/`477` during sync surfaces `TODOIST_DISCONNECTED` (`new-reminder-health`) rather than failing silently.
- **Disconnect** deletes the token locally and tells the user to rotate it in Todoist; projections become `WITHDRAWN`, with the user choosing whether the remote tasks are deleted or left (#9).

## Out of scope

The projection, sync, transport and representation logic are #9 and #10. The provider choice UI is `new-provider-selection-ux`. OAuth itself, and the App Links work it shares a prerequisite with, are Phase 7 items contingent on ruling R-4.

## Acceptance criteria

1. A valid token connects and shows a connected status with the account identity; an invalid token is rejected at entry with a clear message.
2. The token is never present in a backup export, in logcat, or in the Room database.
3. Background sync works with the device locked.
4. A `401` during sync produces `TODOIST_DISCONNECTED` and stops the outbox rather than retrying blindly.
5. Disconnect removes the token and leaves the app fully functional on local reminders.
6. `integration_account.auth_kind` distinguishes token from OAuth, and nothing in the provider reads the token directly.

## Design references

D2 §5 (split of #9) · D3 §8 (authentication row, OAuth deferral, transport) · D4 §10 (`integration_account`) · D7 Phase 5 prerequisites · D8 rulings R-3, R-4, spike S6 · security doc, "Todoist token" · `11-review-1-changes.md` §2.
