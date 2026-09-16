# Spike S5 — Which SAF tree providers work on the owner's phone

Ran 2026-09-15 on the owner's phone (Android 17, hardened distribution), with a throwaway debug
activity installed over the real app (same package, key and versionCode; the database was left
untouched). The activity was never committed; its results file was read back over `adb`.

## Verdict

**A user-selected SAF tree is a usable managed attachment store on this phone.** The primary
external-storage provider (`com.android.externalstorage.documents`) exposed a writable tree,
`takePersistableUriPermission(READ|WRITE)` succeeded, the grant survived a `force-stop`, and a
fresh process ran the whole create / write / read-back / list / delete cycle against the stored
URI without showing the picker again.

**Google Drive is not installed on this phone**, so the "Drive first" preference in D7 could not
be exercised and no Drive-specific behaviour was observed. The owner then installed **Proton
Drive**, which on Android syncs an ordinary folder under `Documents/` rather than exposing a
DocumentsProvider tree; the owner picked that synced folder in the chooser and the whole probe
passed again, including the cold-process re-check (two persisted grants were then listed, the
Syncthing one and the Proton one — the product must release the previous grant when the folder
changes). Nothing in the Phase 4A architecture depends on which sync tool sits behind the
folder: the store is *a* SAF tree on the primary-storage provider, and Syncthing or Proton
Drive gives the off-device copy, exactly the replication model D7 §3R describes.

## What was observed

| Step | Result |
|---|---|
| `ACTION_OPEN_DOCUMENT_TREE` | Picker offered the local roots; the owner's first pick was refused by the system ("Can't use this folder — to protect your privacy, choose another folder"), which is the standard guard on the root of primary storage and on `Download/`. A sub-folder was accepted. |
| Authority / tree id | `com.android.externalstorage.documents`, `primary:Documents/<sync folder>/<app folder>` |
| `takePersistableUriPermission(READ or WRITE)` | OK; `getPersistedUriPermissions()` lists one entry, read + write |
| `DocumentFile.fromTreeUri` | exists, isDirectory, canRead, canWrite all true (≈30–50 ms) |
| `createDirectory` / `createFile(text/plain)` | OK (≈40–70 ms each) |
| write 64 KiB, read back | SHA-256 match |
| `length()`, `listFiles()` | correct |
| `delete` file / directory, `findFile` after | deleted, not found |
| whole cycle | ≈350–400 ms |
| `am force-stop`, cold launch, re-check with the stored URI (no picker) | grant still listed; full cycle passed again from a new pid — on both the Syncthing folder and, later, the Proton Drive folder |
| Reboot survival | not exercised (the phone was in use); Android persists these grants across reboot by design, and Phase 3R's exit criteria retest it |

Providers present on the phone that declare a documents authority: the external-storage,
downloads, media, MTP and shell providers plus a few third-party apps' private providers. No
cloud provider was installed at the time.

## Consequences for Phase 4A

- `AttachmentStore` is implemented once, over `DocumentFile` on a persisted tree URI; there is no
  app-private LOCAL store in 4A (owner's ruling: never silently primary).
- A lost grant is a state the app must show, not a crash: Settings → Attachment storage reports
  *not configured* / *ready* / *access lost — choose the folder again*.
- The spike's helper (`androidx.documentfile:documentfile`) is the right dependency for the store.
