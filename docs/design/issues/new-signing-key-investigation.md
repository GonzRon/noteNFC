---
action: create
title: "[MVP] Investigate the installed APK's signing certificate and upgrade path"
milestone: "Phase 0 — Foundation"
labels: []
---

Identified in D2 §4 item 4 (Foundational: signing-key and in-place-upgrade risk). Not present in issues #1–#16.

## Goal

Establish, non-destructively, whether the installed noteNFC can be updated in place, and record the answer — because it determines whether the legacy Joplin mapping survives the first install of the new app.

This is ruling **R-1**: plan for no signing key; investigate anyway; an in-place migration is a bonus, never an assumption.

## Why this matters

The committed release APK is signed v2-only with certificate `CN=fillMateAndroid, OU=dev, O=FillMate, ST=NH, C=US` (SHA-256 `e18854af…9a69`). No keystore, `signingConfig` or `keystore.properties` exists anywhere in the repository, and `~/.android/debug.keystore` is absent from the build machine (D1 §6, CURRENT-VERIFIED). A rebuild therefore produces a different signature, and Android refuses to update an installed package whose signing certificate differs — so the first install becomes an **uninstall + reinstall**, which deletes `noteNFCURLs`. Android Auto Backup restore is signature-bound too, so it is no help. Which of the two shipped APKs is actually on the phone is currently UNKNOWN.

This is risk 1 in D8 (High × High).

## Scope — investigation only, nothing destructive

1. Identify the certificate of the **installed** package on the phone (`adb shell pm` / `apksigner verify --print-certs` against the pulled APK) and compare its SHA-256 with the repository's release APK.
2. Determine whether it is the release certificate or a debug certificate.
3. Search the author's machines and backups for the `fillMateAndroid` keystore and for a matching `debug.keystore`.
4. Record the outcome in D8 R-1 and state which of D6 §2's two situations applies:
   - **Situation A** — the key is held: in-place update, `noteNFCURLs` intact, automatic migration runs (D6 §3).
   - **Situation B** — the key is gone: uninstall + reinstall, prefs lost, tags recovered through the deterministic re-link path (D6 §5).
5. Do **not** uninstall, reinstall, wipe data, or overwrite the installed app during the investigation.

## What this issue does not do

It does not change the migration plan. D6 already handles both situations, and the re-link path exists regardless of the outcome; a found key only downgrades risk 1. It does not introduce signing configuration for future releases beyond recording what must be decided.

## Acceptance criteria (D7 Phase 0)

1. The installed package's certificate fingerprint is recorded, and it is stated whether it matches the repository's release APK.
2. The search for the keystore is recorded as found (with its location) or not found.
3. D8 R-1 is updated with the result and the applicable D6 situation.
4. The installed app is still present and its data intact at the end of the investigation.

## Design references

D2 §4 item 4 · D1 §6 (the shipped binaries and the upgrade constraint) · D6 §2 (two starting situations), §3 (automatic migration), §5 (re-linking) · D7 Phase 0 prerequisites and exit criterion 4 · D8 ruling R-1, risk 1.
