# Phase 4A evidence — attachments, the SAF-tree managed store, schema v5, the backup set split

Branch `phase-4a` from master `41af038`. Date 2026-09-16. Version `2.4` / `versionCode` 6.

Phase 4A gives the assets their paperwork. An `Attachment` row names a file; the bytes live in a
folder the owner chose through the system file picker, as ordinary documents a sync tool can
replicate, never inside the app. `:core` gains the model, its three pure rules, the
`AttachmentStore` / `AttachmentStorage` ports and the use cases; `:app` gains one store
implementation over a `DocumentFile` tree, thumbnails without a new image library, a DOCUMENTS
section on asset detail and on event detail, a Settings section that owns the folder, Room v5 and
a backup that is no longer one file but a **set of two** — `noteNFC-data-<stamp>.zip` (format 5,
rows only) beside `noteNFC-artifacts-<stamp>.zip` (artifact format 1, bytes) under one
`backupSetId`. What 4A deliberately is not is a second store, a store-location migration, or a
reference to a document the app does not own: `StorageProvider` has no `LOCAL` member at all, and
`SAF_DOCUMENT` is reserved for 4B (spec §2, §11.8).

**Read the status first (§5).** The JVM suites are green — `:core` **346**, `:app` **210** — and
the instrumented suite is **58 tests, 0 failures, 0 skipped** on **`emulator-5554`** (API 37.1, no
NFC), which is the only instrumented target this phase ever used: the owner's phone holds real
data and an instrumented run wipes it. **The phone half of the proof is not in this document yet.**
Spec §12's last bullet — the §10 SPA import, the v4→v5 upgrade in place over the owner's real
install, the two-file export into the synced folder — is the owner's own run and its rows below
say `PENDING — phone step, controller`, with the procedure spelled out so they can be filled in
where they stand.

The spike behind the store choice is `docs/design/spikes/S5-saf-tree-provider.md` and is not
repeated here.

## 1. Exit criteria (spec §12) → evidence

Spec §12 is five proof bullets and a gate. The rows below take them clause by clause, so every
claim names the test that makes it or the phone step that still owes it.

| # | Criterion (spec §12) | Evidence | Status |
|---|---|---|---|
| 1 | `:core` JVM — locator and extension rules; kind inference | `AttachmentRulesTest` (7): `locatorUsesTheOwnerDirectoryAndTheAttachmentId`, `locatorShapeIsCheckedAgainstItsOwnerAndId`, `theExtensionComesFromTheNameThenTheMimeTypeThenBin`, `kindIsInferredAndIsOnlyADefault`, `isImageIsTheOnlyThingTheThumbnailPathAsks`, `mimeTypesKnowsTheSevenExtensionsAndTheFourCompressedTypes`, `mimeForExtensionIsTheInverseOfExtensionFor` | **PROVEN on the JVM.** The locator is `assets/<asset-id>/<id>.<ext>` or `events/<event-id>/<id>.<ext>`, the extension falls back name → mime → `bin`, and the inferred kind is only a default an explicit choice beats |
| 2 | `:core` JVM — `AddAttachment`: store first, row second, bytes deleted when the row write fails; refusals for blank name, missing owner, size guard, no store | `AttachmentUseCasesTest` (15): `addPutsTheBytesFirstThenTheRow`, `aFailedRowWriteDeletesTheBytesItHadAlreadyWritten`, `addRefusesABlankNameAMissingOwnerAndNoStore`, `addRefusesAnOversizeFileBeforeCopyingAnyByte`, `addRefusesAnOversizeFileTheProviderNeverDeclared`, `anExplicitKindBeatsTheInference`, `aCameraCaptureIsAPhotoWhateverTheMimeSays`, `addToAnEventUsesTheEventDirectory`, plus the three cleanup-integrity tests of §6 ruling 6 (`aCleanupDeleteThatThrowsDoesNotHideTheRowWriteFailure`, `anOversizeRefusalSurvivesACleanupDeleteThatThrows`, `aCancelledSweepIsNotSwallowed`) | **PROVEN on the JVM.** The 256 MiB guard is tested twice — once on a size the provider declared (nothing is copied) and once on a size only the store could report (a camera capture; the bytes are deleted again) |
| 3 | `:core` JVM — `UpdateAttachment` incl. `Unchanged`; `DeleteAttachment` | `AttachmentUseCasesTest`: `updateChangesMetadataAndNeverTheLocator`, `updateRefusesBlankUnchangedAndUnknown`, `savingNothingIsSilent` (`:app` `AttachmentsSectionViewModelTest`), `deleteRemovesTheRowThenTheBytes`, `aStoreThatThrowsOnDeleteDoesNotHoldOntoTheRow` | **PROVEN on the JVM.** A rename never moves bytes, and a delete whose store throws still removes the row rather than stranding it |
| 4 | `:core` JVM — `DeleteAsset` and `DeleteEvent` remove the bytes of the asset's own and its events' attachments | `AttachmentUseCasesTest` (the delete-cascade cases) with `AttachmentDaoTest.deletingTheAssetAlsoTakesItsEventsAttachments` and `.deletingTheEventCascadesItsAttachmentRows` on the Room side | **PROVEN on the JVM.** The FK cascade takes the rows; the use case takes the bytes, through the one `AttachmentSweep` helper (§6 ruling 6) |
| 5 | `:core` JVM — format 5 round trip with attachments; a format 4 file decodes with none; the reader refuses unknown owner / bad sha / duplicate locator | `BackupCodecTest` (55): `formatFiveRoundTripsAttachmentsOnBothOwners`, `formatFourFileStillDecodesWithNoAttachments`, `anAttachmentWithNoOwnerOrTwoOwnersIsCorrupt`, `anAttachmentPointingAtARowThatIsNotInTheFileIsCorrupt`, `aBadShaABadLocatorANegativeSizeAndADuplicateLocatorAreAllCorrupt`, `aFormatFiveFileWithNoSetIdIsCorrupt`, `aReferenceRowIsNotCountedAsAnArtifact`, `encodeIsStillReproducibleAndAttachmentsAreSortedById`; `BackupUseCasesTest`: `attachment rows are written after the owners they point at`, `attachment rows survive the round trip and are reported` | **PROVEN on the JVM.** `formatVersion` keeps its name (spec §11.4) and `BackupCodec.FORMAT_VERSION` is 5; formats 1–4 still decode, and an exactly-one-owner row is enforced in the reader because there is no SQL `CHECK` (spec §11.5) |
| 6 | `:core` JVM — `ArtifactsCodec` round trip; `STORED` for compressed types; refuses a newer format; a sha mismatch is skipped and reported | `ArtifactsCodecTest` (16): `manifestIsFirstAndEveryEntryRoundTrips`, `compressedTypesAreStoredAndEverythingElseIsDeflated`, `aNewerArtifactFormatIsRefusedBeforeAnyEntryIsRead`, `missingBytesAndDriftedBytesAreLeftOutAndReported`, `theReaderNamesManifestEntriesWithNoBytesAndBytesTheManifestDoesNotName`, `entryNamesTakeTheirExtensionFromTheLocator`, `anEmptyPlanStillWritesAManifestOnlyArchive`, plus the five corruption cases (`anEmptyArchiveIsCorrupt`, `aTruncatedArchiveIsCorrupt`, `garbageBytesAreCorrupt`, `anUnreadableManifestIsCorrupt`, `anArchiveThatDoesNotStartWithTheManifestIsCorrupt`) and the three between-passes drift cases | **PROVEN on the JVM.** `ARTIFACT_FORMAT_VERSION` is 1, the manifest is the first entry, nothing is buffered whole, and a source that changes between the two passes fails the write loudly rather than shipping a bad entry (§6 ruling 8) |
| 7 | `:core` JVM — `RestoreArtifacts`: a set mismatch is refused, missing rows counted, bytes land at the row's locator | `ArtifactsUseCasesTest` (12): `aSetMismatchIsRefusedAndWritesNothing`, `restoreWritesTheBytesAtTheRowsOwnLocator`, `anEntryWithNoRowIsCountedAndNamed`, `aManifestEntryWithNoBytesIsNamedRatherThanIgnored`, `anEntryWhoseShaDisagreesWithTheRowIsSkippedAndLeavesNoBytes`, `bytesThatDoNotHashToWhatTheManifestClaimsAreDeletedNotLeftBehind`, `aPutThatDiesMidCopyLeavesNoBytesBehindAndStillFailsTheRestore`, `restoreWithNoStoreConfiguredIsAnIoFailureNotAPartialRestore`, `aSweepTheStoreRefusesStillCountsTheSkipAndKeepsGoing` | **PROVEN on the JVM.** A refused restore writes nothing, and every skip leaves no bytes rather than half a file |
| 8 | `:core` JVM — `ExportBackupSet`'s plan lists every managed row exactly once; `ArtifactsWritten.covers(plan)` is false on any missing, mismatched, short count or short byte total | `ArtifactsUseCasesTest`: `thePlanListsEveryManagedRowExactlyOnceAndNoReferenceRow`, `coversIsTrueOnlyWhenEveryPlannedRowLandedWithItsSize`, `anInstallWithNoAttachmentsStillExportsAPlanAndAnArchive` | **PROVEN on the JVM.** This is the all-or-nothing rule of spec §11.14: `covers(plan)` false means the set is not a backup |
| 9 | `:app` JVM (Backup ViewModel) — `missingManagedAttachmentMakesExportFailAndLeavesNoArchives`, `mismatchedManagedAttachmentMakesExportFailAndLeavesNoArchives`, `midArtifactsWriteRemovesPartialArtifactsAndDataZip`; emulator: the real SAF writer removes the document it created when the body throws | All three named tests are present and green in `BackupViewModelTest` (15), together with `cancellingMidExportStillTakesBothFilesBack` (§6 ruling 14). The emulator half is `AttachmentsDeviceProofTest.safWriterRemovesTheDocumentItCreatedWhenTheBodyThrows` | **PROVEN, both halves** — the JVM ones over a fake sink, the emulator one over the production `SafBackupSetWriter` on a real `DocumentFile` tree |
| 10 | `:app` JVM — migration 4→5 from `4.json`; `RoomAttachmentRepository` DAO round trip incl. cascade on asset and event delete and `observeForOwner` | `Migration4To5Test.addsTheAttachmentTableAndLeavesEverythingElseAlone`, `Migration1To5Test.v1UpgradesThroughAllFourMigrations`, `AttachmentDaoTest` (7: `roundTripsARowOnAnAssetAndOnAnEvent`, the three cascade tests, `twoRowsCannotClaimTheSameProviderAndLocator`, `aRowWithBothOwnersOrNeitherIsRefusedByTheMapper`, `observeForOwnerEmitsOnEveryWriteAndIsOrderedByName`) | **PROVEN on the JVM.** The v1→v5 chain is green; the table arrives with the unique `(storage_provider, storage_locator)` index and nothing else in the database moves. The **in-place** upgrade over the owner's real v4 install is §4 row P1 |
| 11 | `:app` JVM — `AttachmentStorage.state()` over a fake resolver; `Thumbnails` cache naming | `SafAttachmentStorageTest` (4: `noPreferenceIsNotConfigured`, `aResolvableWritableGrantedTreeIsReady`, `aRevokedGrantAnUnresolvableTreeAndAReadOnlyTreeAreAllAccessLost`, `clearingThePreferenceGoesBackToNotConfigured`), `ThumbnailsTest` (5: `theCacheNameCarriesTheIdAndTheShaPrefix`, `bytesThatChangedCannotBeServedFromTheOldThumbnail`, `noFolderMeansNoThumbnail`, `sampleSizeBringsTheLongEdgeToTwoFiftySixOrBelow`, `theSampledLongEdgeIsNeverAboveTwoFiftySix`) | **PROVEN on the JVM** — the state machine is unit-testable at all because the seam sits one level up, at `AttachmentRoot`, rather than at `DocumentFile` (§6 ruling 3) |
| 12 | `:app` JVM — ViewModel tests for the DOCUMENTS section states (ready / not configured / access lost / missing bytes) | `AttachmentsSectionViewModelTest` (17): `aFreshInstallSaysTheStoreIsNotConfiguredAndListsNothing`, `withAFolderChosenAddedFilesAppearAsRowsOrderedByName`, `anAccessLostStoreRefusesAddAndSaysWhyOnce`, `aRowWhoseBytesAreGoneIsMarkedNotPresent`, `comingBackFromSettingsWithAFolderChosenFlipsTheSectionOver`, `aResubscriptionAlsoReReadsTheFolder`, `theProgressLineNamesTheFileNumberAndTheTotal`, `addingSeveralFilesReportsProgressAndKeepsGoingPastAFailure`, `anEventOwnerSeesOnlyItsOwnFiles`, the four sheet/save/delete failure tests, `capturedOnDefaultsToTodayForAPickedFile`, `aCameraCaptureIsAPhotoWhateverElseItLooksLike`; plus `DocumentsSectionTest` (2) for the size and kind labels | **PROVEN on the JVM.** All four states, and the two lifecycle cases that matter: coming back from Settings flips the section over, and a resubscription re-reads the folder |
| 13 | Emulator — `SafTreeAttachmentStore` contract over a `fromFile` tree | `attachments.SafTreeAttachmentStoreContractTest`, **9 tests**, on `emulator-5554`: the six claims `:core`'s `AttachmentStoreContractTest` makes about the in-memory fake, plus the three only a real provider can be asked — `aDocumentTheProviderRenamedIsStillFoundByItsId`, `aSourceThatThrowsMidCopyLeavesNoDocument`, `puttingTwiceAtOneLocatorReplacesTheDocument` | **PROVEN on the emulator** (§4 rows E1, E12). `put` runs a 200 000-byte payload so the 64 KiB copy loop iterates; the rename test *observes* the provider's own renaming rather than simulating it (§6 ruling 20) |
| 14 | Emulator — `AttachmentsDeviceProofTest`: configure a tree, add from a content URI, see row and bytes, sheet rename, delete, export a set into a test tree and find two stamped files, wipe, restore data ("Not on this device"), restore artifacts, set mismatch refused | `ui.AttachmentsDeviceProofTest`, **12 tests**, on `emulator-5554` — the ten lettered scenarios of spec §12 plus the SAF writer's delete-on-throw and the `ACTION_VIEW` refusal. Scenario-by-scenario in §4 rows E2–E11 | **PROVEN on the emulator.** Every body is assertions; no scenario is a comment, and no test sleeps |
| 15 | Emulator — existing smoke suites still green | The whole connected suite is **58/58** on `emulator-5554`: the two new suites (21) plus the eleven pre-existing ones (37), unchanged in count from 2B-2 | **PROVEN** (§3, §4 row E13) |
| 16 | Phone (real data, no instrumented runs) — the §10 SPA import procedure | The eight files, the folder chosen once, `Add file` multi-select, the kinds they land as | **PENDING — phone step, controller.** Procedure in §4 rows P2–P4 |
| 17 | Phone — a real 2B-2 install migrates v4→v5 in place | `adb install -r` of the 2.4 debug build over the owner's live 2.3 install, then open it and read the Assets list, the journal and the tags back | **PENDING — phone step, controller.** Procedure in §4 row P1 |
| 18 | Phone — export a set; the folder shows two files; the sync tool picks them up | `Export backup set` into a picked folder; two same-stamped ZIPs; `assets/<asset>/` with eight files; the sync tool replicating them | **PENDING — phone step, controller.** Procedure in §4 row P5 |
| 19 | CI green; evidence file `docs/design/phase-4a-evidence.md` with the nine standard sections | The final gate of §9 (`:core:test :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin`, then `:app:connectedDebugAndroidTest` on the emulator) and this document | **PROVEN for the no-device gate and the emulator suite** (§9). `:app:assembleRelease` is the controller's step — see §9 |

## 2. What shipped (by commit)

```
c30f737  plan: fix two design doc filenames in task 11
a6085c3  attachment model, locator and mime rules
9b4a076  attachment store port and in-memory fakes
3e48664  add/update/delete attachment, byte cleanup on asset and event delete
192fd05  byte sweep in one place, and it only swallows store failures
bebcf82  backup format 5: attachment rows
bca6128  artifacts archive, restore artifacts, export backup set
0c7f79a  write failures get their own type, zip damage reads as corrupt, sweep after a failed put
aa6ef97  room v5: attachment table and migration
d282d55  bridge :app onto the renamed export and the grown deletes
f5bc0f3  saf tree attachment store, storage resolver, thumbnails
d297479  thumbnails actually hit 256, one mime table instead of two
2823d97  documents section on asset and event detail
5460e78  documents section: re-read the folder on resume, guard the writes, keep a refused sheet open
7a6d97a  attachment storage settings, backup set export and restore
98fe599  cancelled export cleans up too, damaged files archive says so
b9ac047  attachments device proof on the emulator
37f9c81  settings barrier that only settings satisfies, and the replace-a-document claim
```

Eighteen commits — eleven tasks, six of which needed one review fix round each, plus one plan-text
correction — 9 179 insertions and 240 deletions across 84 files. The eleven-task plan's shape
survives in them: the odd-looking pairs (`3e48664`+`192fd05`, `bca6128`+`0c7f79a`,
`aa6ef97`+`d282d55`, `f5bc0f3`+`d297479`, `2823d97`+`5460e78`, `7a6d97a`+`98fe599`,
`b9ac047`+`37f9c81`) are each a task and its review round.

- **`a6085c3` — the model and its three pure rules.** `AttachmentId`, the three enums, the
  `AttachmentOwner` sealed interface, `Attachment` with `isImage`, the closed `AttachmentProblem`
  list, `MAX_ATTACHMENT_BYTES` (256 MiB), and the two pure owners: `AttachmentLocator` (the
  owner-directory shape and its check) and `MimeTypes` / `AttachmentKinds` (one extension table,
  the kind inference that is only a default). `StorageProvider` has no `LOCAL` member.
- **`9b4a076` — the store boundary.** `AttachmentStore` (`put`/`open`/`exists`/`delete`),
  `ByteSource`, `StoredBytes`, `StoreIoException`, `StoreState`
  (`NotConfigured`/`Ready`/`AccessLost`), `AttachmentStorage`, `AttachmentRepository`, the
  in-memory fakes, and `AttachmentStoreContractTest` — the six claims every store must satisfy,
  written once against the fake and re-run against the real one on the emulator (§3).
- **`3e48664` + `192fd05` — the use cases.** `AttachmentResult<out T>` (`Ok`/`Refused`) rather than
  a two-parameter `Result` the language cannot express; `AddAttachment` puts bytes first and the row
  second inside one `uow.write`; `UpdateAttachment` never touches the locator; `DeleteAttachment` is
  a silent no-op on a row that is not there. `DeleteAsset` and `DeleteEvent` grew the byte sweep the
  FK cascade cannot do. The fix round folded three sweep spellings into one `AttachmentSweep`
  helper, guarded the cleanup deletes so a throwing delete cannot hide the row-write failure, and
  made every best-effort sweep rethrow `CancellationException`.
- **`bebcf82` — backup format 5.** The manifest gains `backupSetId`, `artifactFormatVersion`,
  `artifactCount` and `artifactBytes`, all defaulted so a format ≤ 4 manifest still decodes;
  `AttachmentDto` carries the owner as a nullable `assetId` / `eventId` pair and the reader refuses
  a row with neither or both; `ImportBackupReplace` writes attachment rows after the owners they
  point at.
- **`bca6128` + `0c7f79a` — the artifacts archive.** `ArtifactsCodec` (format 1): a manifest-first,
  two-pass, never-buffered writer that `STORED`s already-compressed types and computes each CRC up
  front; `ExportBackupSet` producing `BackupSet(data, plan)`; `ArtifactsWritten.covers(plan)` as the
  all-or-nothing test; `RestoreArtifacts` landing bytes at each row's own locator and refusing a
  foreign set. The fix round gave export failures one type (`ArtifactsWriteFailed`), translated
  `ZipException`/`EOFException` into `BackupCorrupt` on the read side without a blanket catch, and
  made every partial-byte delete guarded.
- **`aa6ef97` + `d282d55` — Room v5.** `AttachmentEntity`, `AttachmentDao`,
  `RoomAttachmentRepository`, `MIGRATION_4_5` (create the table and its three indexes, touch
  nothing else), `AppGraph.SCHEMA_VERSION = 5`, and the minimal bridge that kept `:app` compiling
  on the renamed export until Task 9 replaced it.
- **`f5bc0f3` + `d297479` — the SAF tree store.** `AttachmentRoot` and `DocumentTreeRoot` (the seam
  that makes the storage state unit-testable), `SafTreeAttachmentStore` with the `<id>.` prefix
  fallback for a provider that renames, `SafAttachmentStorage` with `viewUri`, `Thumbnails`,
  the two preferences, `FileProvider` + `file_paths.xml` + `<queries>`, and one new dependency:
  `androidx.documentfile:documentfile:1.1.0`. The fix round brought the thumbnail long edge to
  ≤ 256 px as the spec asks and replaced the store's private mime lookup with
  `MimeTypes.mimeForExtension` in `:core`, so there is one table, not two.
- **`2823d97` + `5460e78` — the DOCUMENTS section.** `AttachmentsSectionViewModel`,
  `DocumentsSection`, `AttachmentEditSheet`, `AttachmentPickers` (multi-select, camera, viewer), on
  asset detail and event detail, reusing `Route.Settings` and adding no new route. The fix round
  re-read the store state on resubscription so returning from Settings flips the section over,
  moved add/save/delete off the main thread, gave save and delete the same failure guard as add,
  and kept a refused sheet open with the typed edits intact.
- **`7a6d97a` + `98fe599` — Settings and the Backup screen.** Settings → **Attachment storage**
  (folder, provider, `Choose folder`, the same-folder repair), and a Backup screen that exports a
  set and restores in two steps. The fix round put the export's cleanup under `NonCancellable` so a
  cancelled export takes both files back, and made a damaged artifacts archive say what it did not
  carry and what it carried unasked.
- **`b9ac047` + `37f9c81` — the device proof.** The two new instrumented suites, on the emulator
  only. The fix round replaced scenario (a)'s post-navigation barrier with two that only Settings
  can satisfy, and added the replace-a-document contract claim.

## 3. Tests

### JVM (`./gradlew :core:test :app:testDebugUnitTest`)

Totals from the JUnit XML: **`:core` 346 tests, 0 failures, 0 skipped**; **`:app` 210 tests, 0
failures, 0 skipped**. Master `41af038` finished at `:core` **273** / `:app` **158**, so 4A added
**73** and **52**.

| Module | Class | Tests | |
|---|---|---|---|
| `:core` | `AttachmentRulesTest` | 7 | new |
| `:core` | `AttachmentStoreContractTest` | 6 | new — the store contract, against the fake |
| `:core` | `AttachmentUseCasesTest` | 15 | new |
| `:core` | `ArtifactsCodecTest` | 16 | new |
| `:core` | `ArtifactsUseCasesTest` | 12 | new |
| `:core` | `BackupCodecTest` | 55 | 46 → 55 (format 5) |
| `:core` | `BackupUseCasesTest` | 18 | 14 → 18 |
| `:core` | `EventUseCasesTest` | 18 | 16 → 18 (`DeleteEvent`'s byte sweep) |
| `:core` | `RetireDeleteAssetTest` | 7 | 5 → 7 (`DeleteAsset`'s byte sweep) |
| `:core` | (24 classes unchanged from 2B-2) | 192 | |
| `:app` | `AttachmentDaoTest` | 7 | new |
| `:app` | `Migration4To5Test` | 1 | new |
| `:app` | `Migration1To5Test` | 1 | new |
| `:app` | `SafAttachmentStorageTest` | 4 | new |
| `:app` | `ThumbnailsTest` | 5 | new |
| `:app` | `AttachmentsSectionViewModelTest` | 17 | new |
| `:app` | `DocumentsSectionTest` | 2 | new |
| `:app` | `BackupViewModelTest` | 15 | 2 → 15 (the set: export, both restores, every failure path) |
| `:app` | `AppPrefsTest` | 6 | 4 → 6 (the two new preferences) |
| `:app` | (23 classes unchanged from 2B-2) | 152 | |

### The two contract suites, as a pair

The store contract is written once and asked twice. `:core`'s **`AttachmentStoreContractTest`** (6)
makes the six claims against the in-memory fake:
`putThenOpenRoundTripsTheBytes`, `putReturnsTheShaAndSizeTheStoreItselfSaw`,
`aLocatorWithTwoDirectoryLevelsIsCreated`, `openOfAnAbsentLocatorIsNull`, `existsAnswersForBothCases`,
`deleteOfAnAbsentLocatorIsSilent`. `:app`'s instrumented
**`SafTreeAttachmentStoreContractTest`** (9) makes the same six against the real
`SafTreeAttachmentStore` over `DocumentFile.fromFile(getExternalFilesDir/attachments-contract)`,
and adds the three that only a real provider can be asked: the provider-rename fallback, the
failed-write cleanup, and the replace-at-one-locator path. A store that passes the first and fails
the second is exactly what the pair is for.

### Instrumented (`ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest`)

**58 tests, 0 failures, 0 skipped** on **`emulator-5554`** (API 37.1, no NFC), 2026-09-16. The
device is quoted from the generated results:
`app/build/outputs/androidTest-results/connected/debug/TEST-emulator-5554 - 17.xml`, carrying
`<property name="device" value="emulator-5554" />` for every suite, and Gradle's own line
`Running tests on devices: emulator-5554 - 17`.

| Suite | Tests | Time | |
|---|---|---|---|
| `attachments.SafTreeAttachmentStoreContractTest` | 9 | 0.04s | new |
| `ui.AttachmentsDeviceProofTest` | 12 | 15.94s | new |
| `ui.AppSmokeTest` | 5 | 9.08s | `clearInstall` grew two lines |
| `ui.AssetModelDeviceProofTest` | 8 | 19.95s | |
| `ui.JournalDeviceProofTest` | 8 | 21.52s | |
| `ui.EditorsDeviceProofTest` | 6 | 16.47s | |
| `ui.components.ComponentsSmokeTest` | 4 | 4.32s | |
| `ui.nav.NavigationSmokeTest` | 3 | 3.28s | |
| `ui.JournalSmokeTest` | 1 | 1.90s | |
| `ui.ShareActivitySmokeTest` | 1 | 1.15s | |
| `ui.DeepLinkSmokeTest` | 1 | 1.02s | |

2B-2's 37 are all still there and still 37; 4A adds the 21 of the two new suites. There is no
manual instrumented row and no test is skipped or ignored.

**Two defects the first emulator run found, both in the plan's own test code, neither in the app.**
`RawDocumentFile.createFile` appends the extension it derives from the mime type, so asking for
`att-1.pdf` with `application/pdf` leaves `att-1.pdf.pdf` on disk. That broke the rename test
outright (the file it tried to rename did not exist) and made the export scenario's on-disk name
assertion wrong (`noteNFC-data-<stamp>.zip.zip`). Both were rewritten to assert what the writer
*reported* plus the folder's contents by prefix — the same discipline `SafTreeAttachmentStore`
itself uses, and the reason the `<id>.` prefix fallback exists in production at all.

## 4. Device proof

Two tables: what ran on the emulator, and what only the owner's phone can run. The phone rows name
no model and no serial, and the phone was never a connected-test target in this phase —
`ANDROID_SERIAL=emulator-5554` was pinned on every Gradle invocation that can reach a device and
`-s emulator-5554` on every `adb` command.

### Emulator (`emulator-5554`, API 37.1, no NFC) — automated, 2026-09-16

| # | Scenario (spec §12) | Test | What it actually asserts |
|---|---|---|---|
| E1 | The store contract against a real `DocumentFile` tree | `SafTreeAttachmentStoreContractTest` (9) | The six fake-store claims, plus: a document the provider renamed is still found by its `<id>.` prefix and still round-trips its bytes; a source that throws mid-copy leaves the owner directory **empty**, not merely no file at the locator; a second `put` at one locator leaves exactly one document holding the second payload's bytes, sha and (different) length |
| E2 | (a) No folder chosen → the section points at Settings | `AttachmentsDeviceProofTest.withNoFolderTheDocumentsSectionPointsAtSettings` | `DOCUMENTS`, `Attachment storage not set up`, `Choose a folder in Settings`; `Add file` / `Take photo` / `No documents yet` all **absent**; `Open settings` lands on Settings, proven by two barriers the asset screen cannot satisfy — `Choose folder` present and `Choose a folder in Settings` gone (§6 ruling 21) |
| E3 | (b) An added file is a row and a file in the tree | `.anAddedFileIsARowAndAFileInTheTree` | The document on disk is named `<attachment-id>.…` and its bytes equal the source byte-for-byte; `viewUri(locator)` is non-null; `DOCUMENTS · 1`; the row node carries the name **and** `Document · 2.0 KB · <today>` |
| E4 | (c) The sheet renames and re-kinds | `.theSheetRenamesAndReKindsTheRow` | Overflow → sheet → new name, `Manual` chip, `Save`; the row redraws, the old name is gone, the sheet closes — **and** the document's name, its bytes and the row's `storageLocator` are unchanged. A rename never moves bytes |
| E5 | (d) Delete removes the row and the file | `.deletingARowRemovesTheRowAndTheFile` | The exact line `Delete <name>? The file is removed from your attachment folder.`; afterwards `No documents yet`, the owner directory empty, `attachments.get(id)` null |
| E6 | (e) Thumbnails for images, a glyph for the rest | `.anImageRowGetsAThumbnailAndADocumentRowGetsAGlyph` | `thumbnails.cacheFileFor(photo)` becomes a non-empty file; `cacheFileFor(manual)` never exists |
| E7 | (f) Export writes two stamped files into the picked folder | `.exportingWritesTwoStampedFilesIntoThePickedFolder` | Both names match `^noteNFC-(data\|artifacts)-\d{8}-\d{6}\.zip$` with **one shared stamp**; two non-empty documents; the artifacts zip starts with `manifest.json` and carries exactly one `artifacts/` entry; the Backup screen then shows a formatted `Last backup` instead of `Never` |
| E8 | (g) Data-only restore leaves the rows saying "Not on this device" | `.restoringDataAloneLeavesTheRowsSayingNotOnThisDevice` | `ImportReport.attachments == 1`, a non-empty set id, **nothing** in the tree, and the row node carrying the name together with `Not on this device` |
| E9 | (h) Restoring the files archive brings the bytes back | `.restoringTheFilesArchiveBringsTheBytesBack` | `ArtifactsReport(restored = 1, skipped = 0)` with no missing or unexpected entries; the bytes under the locator equal the original; the thumbnail decode reappears after the wipe; `Not on this device` is gone |
| E10 | (i) A files archive from another set is refused | `.aFilesArchiveFromAnotherSetIsRefused` | Two sets exported, both ids learned from their **own** `ImportReport`s rather than from the error being asserted; the refusal is exactly `Those files belong to backup set <found8>, not <expected8>`; the store gained nothing; the row still reads `Not on this device` |
| E11 | (j) An event's files are its own and go when the entry does | `.anEventsFilesAreItsOwnAndGoWhenTheEntryDoes` | The asset's own DOCUMENTS never mentions the file; the ledger entry shows `DOCUMENTS · 1`; after `Delete this entry?` the event's directory is empty and `attachments.forOwner(event)` is empty |
| E12 | The SAF writer cleans up after itself; a file nothing can open says so | `.safWriterRemovesTheDocumentItCreatedWhenTheBodyThrows`, `.tappingARowNothingCanOpenSaysSo` | A body that throws leaves the folder empty; a tap with no viewer visible raises `No app can open this file` |
| E13 | The eleven pre-existing suites are still green with the two new ones in the same process | The whole connected run | **58/58**, 0 failures, 0 skipped |

### The owner's phone — PENDING, phone step, controller

Every row below is the controller's and the owner's to run and to fill in. Nothing here has been
run: this document's implementer half never touched the phone. The phone is the release-shaped
**debug** build and no instrumented suite is ever run against it (spec §3) — an instrumented run
clears app data, and the phone holds the migrated Joplin logs.

| # | Step | Expected | Result |
|---|---|---|---|
| P1 | **v4 → v5 in place over the real 2B-2 install.** With the phone the only device targeted for this step and the emulator stopped first: `adb install -r app/build/outputs/apk/debug/app-debug.apk` (versionCode 6 / 2.4) over the live 2.3 install. Then, by hand: open the app from the launcher | It starts; the Assets list is intact; the journal and the tag bindings are still there. That is `MIGRATION_4_5` having run in place on real data. `dumpsys package` reports `versionCode=6 versionName=2.4`; `logcat` `FATAL EXCEPTION` count 0 | **PENDING — phone step, controller** |
| P2 | **Choose the attachment folder, once.** Settings → Attachment storage → `Choose folder` → pick the sync-replicated folder on primary storage | The Folder line names it and the Provider line names an authority | **PENDING — phone step, controller** |
| P3 | **Push the eight SPA files.** They are already extracted from the Joplin export and renamed to their link text (spec §10.1). With the extraction directory as `~/spa-import/`: `adb shell mkdir -p /sdcard/Download/spa`, then `adb push` each of — `1-2-3 Easy Installation Guide.pdf`, `SPA Water Chemistry.pdf`, `SPA Water Chemistry.docx`, `BP Troubleshooting Manual 60Hz.pdf`, `5169797_Replacement_Cartridge_for_Bullfrog_at-ease.zip`, `4649576_Bullfrog_Spa_Headrest_Clip-_2016_to_present_Version.zip`, `4226467_Bullfrog_Spa_Headrest_Clip.zip`, `2018-Bullfrog-Owners-Manual-rev3.1.8-web.pdf` — into `/sdcard/Download/spa/`, then `adb shell ls -l /sdcard/Download/spa` | Eight files listed, with exactly those names | **PENDING — phone step, controller** |
| P4 | **Import them through the product's own picker.** One action by the owner: open the Hot tub asset → DOCUMENTS → `Add file` → in the picker, navigate to `Download/spa` and multi-select all eight → confirm | The progress line counts up to `Adding 8 of 8…`, then DOCUMENTS reads `Documents · 8`. The five PDFs land as `DOCUMENT`, the `.docx` and the three `.zip`s as `OTHER` (spec §10.3). Re-kinding the three manuals to `MANUAL` from the sheet is optional and the owner's call. This is the picker path the product ships, so it is also 4A's real-data proof; no instrumented code runs on the phone | **PENDING — phone step, controller** |
| P5 | **Export a set, confirm two files, then clean up.** Settings → Backup → `Export backup set` → pick a folder. Then `adb shell rm -rf /sdcard/Download/spa` | The folder holds `noteNFC-data-<stamp>.zip` and `noteNFC-artifacts-<stamp>.zip` with the **same** stamp, and the artifacts file is roughly the size of the eight documents. The attachment folder holds `assets/<the asset>/` with eight files in it, and the sync tool has picked them up | **PENDING — phone step, controller** |

## 5. Status of the device proof

**Device-proven on the emulator; the phone half is the owner's own run and is still open.**

**Automated and passing on `emulator-5554` (§4 rows E1–E13), 2026-09-16.** All ten lettered
scenarios of spec §12 drive the real screens: the section pointing at Settings with no folder, an
added file being both a row and a file whose bytes match, a sheet rename that provably does not
move bytes, a delete that empties the owner directory, a thumbnail for an image and none for a
document, an export writing two same-stamped files, a data-only restore reading "Not on this
device", an artifacts restore bringing the bytes and the thumbnail back, a foreign set refused by
its short id, and an event's files living and dying with the entry. The store contract is asked of
the real `DocumentFile` store, not only of the fake. With the 37 from 1C, 2A, 2B-1 and 2B-2 the
instrumented suite is **58 tests, 0 failures, 0 skipped**.

**What is proven only on the emulator, and why.** This list is Task 10's verbatim in substance;
none of it is a failure, and each is a claim the emulator structurally cannot make.

1. **The SAF picker itself** — `OpenDocumentTree` for the attachment folder and for an export
   destination, `OpenMultipleDocuments` for `Add file`, `OpenDocument` for the two restores: that
   the chooser returns, that the URI is persisted (or deliberately not), and that
   `AttachmentPickers.pickedFile` reads the provider's real `DISPLAY_NAME` / `SIZE`.
2. **A real document provider's naming and grants** — a `content://` tree from the primary-storage
   provider or a sync-tool-backed folder, including whether it renames or normalises names, and the
   `AccessLost` state after a revoked or lost persisted grant (Settings' *access lost — choose the
   folder again* line, and the wrong-folder repair snackbar).
3. **The camera** — `Take photo`, the `FileProvider` capture URI, the temp file being consumed by
   the copy, and the `No camera app on this device` branch. This emulator has no camera app, so
   only the negative branch is even reachable and the suite does not claim it.
4. **A viewer actually opening an attachment** — `ACTION_VIEW` on a `content://` document with the
   one-shot read grant, landing in a real PDF or image viewer and coming back. The emulator *does*
   ship a PDF viewer, but the test tree hands back `file://` URIs while the manifest's `<queries>`
   entry declares `scheme="content"`, so the tap takes the refusal branch for a reason unrelated to
   whether a viewer exists. The **positive** launch path is phone-only.
5. **The Backup screen's own wiring of the three pickers**, and the refusal and report snackbars
   rendering in *its* snackbar host: each button opens a system picker, so the suite calls the
   production ViewModel with the production sink and IO and asserts on the screen or on the
   ViewModel's own message flow. That the buttons hand the picker's result to those calls is not
   proved here.
6. **NFC** — the emulator has none, so no Phase 4A path that touches a tag is exercised.
7. **Real-size payloads** — the suite's largest attachment is 200 KB. The 256 MiB guard against a
   real file, a multi-megabyte copy's timing, and a thumbnail decode on a real photo are phone-only.

**`DocumentFile.fromFile` is not a document provider.** It is the only tree an instrumented test
can build, and it differs from a real provider in at least one visible way — it appends the mime
type's extension on create. Both places that bit are covered by the production `<id>.` prefix
fallback, which is what the store was written for; a provider that normalises names some *third*
way is still only covered by the §4 P-rows.

**What the phone still owes (§4 rows P1–P5).** The in-place v4 → v5 upgrade over real data, the
§10 SPA import through the shipped picker, the two-file export into the owner's synced folder, and
the sync tool replicating it. Until those rows carry results, Phase 4A's real-data proof is the
emulator's and spec §12's last bullet is unanswered. The rows are written so the controller can
fill them in where they stand.

## 6. Rulings made during execution

Every ruling in the SDD ledger, in plain words, with why.

1. **Spec §6's `Result<Attachment, AttachmentProblem>` became a sealed `AttachmentResult<out T>`
   (`Ok`, `Refused`).** `kotlin.Result` takes one type parameter and cannot express the spec's
   two, so the shape follows `OpenLink.Outcome`, which the codebase already has. Every use-case
   signature the spec names is otherwise verbatim.
2. **`AddAttachmentCommand` gained `sizeBytes: Long?`.** Spec §6's field list omits a size, but
   §8.1 says the picker passes one and §11.11 requires the 256 MiB guard to refuse "before any byte
   is copied". The nullable field is checked before `put` when the provider declared a size, and
   checked again against the `StoredBytes` the store returns when it did not (a camera capture) —
   deleting the bytes if it went over.
3. **Spec §5.3's `rootResolver: (treeUri) -> DocumentFile?` became `(String) -> AttachmentRoot?`.**
   A `DocumentFile` cannot be faked in a plain JVM unit test, which §12 nevertheless asks for
   ("`AttachmentStorage.state()` over a fake resolver"). The seam moved one level up:
   `AttachmentRoot` is the interface, `DocumentTreeRoot` is production's `DocumentFile`
   implementation, and the instrumented suite substitutes one over `DocumentFile.fromFile`.
   `grantCheck` is unchanged.
4. **`DeleteAsset` and `DeleteEvent` take `AttachmentStorage`, not `AttachmentStore`.** Spec §6's
   column says "+ `attachments`, `store`", but a store can be `NotConfigured` or `AccessLost` and
   neither delete may fail for that reason. The parameter is named `storage` and its KDoc says why.
5. **`DeleteAttachment` returns `Unit`, not a refusal.** Spec §4's problem list is closed and has no
   member for "no such attachment", and the store's own contract already says an absent target is
   not an error. `UpdateAttachment` reuses `OwnerMissing` for a row that has gone, and its KDoc says
   that is what the member means there — the UI copy for that case never says "owner".
6. **Cleanup deletes are guarded and cancellation is rethrown, in one helper.** A throwing cleanup
   delete must never hide the row-write failure it was cleaning up after, or turn a refusal into a
   throw; and a best-effort sweep that swallows `CancellationException` breaks structured
   concurrency. The three sweep spellings became one `AttachmentSweep` helper that catches
   `StoreIoException` only. Cost if wrong: one small diff to revert.
7. **Export-side failures got one type, `ArtifactsWriteFailed(cause)`.** A vanished source, a size
   drift between the passes and a raw `ZipException` are all the same thing to a caller — the set
   cannot be written — and giving them one type is what lets the Backup screen delete both files on
   any of them. `BackupSetIncomplete` stays for the different case of `covers(plan) == false`.
8. **Pass-two size verification throws rather than reporting.** When pass two's copied byte count
   disagrees with the plan's size, the archive already contains the bad entry; all-or-nothing means
   fail loudly, not report and ship. Cost if wrong: an export that might have been salvaged fails.
9. **The artifacts reader translates zip damage into `BackupCorrupt`, never with a blanket catch.**
   `ZipException` and `EOFException` are wrapped; `StoreIoException` (an `IOException`) and
   `ArtifactsSetMismatch` must pass through untouched, so a catch-all would have undone both.
10. **Thumbnails are ≤ 256 px on the long edge, and the plan's test values were wrong.** Spec §8.2
    beats the plan: the sample-size loop is `while (edge > maxEdgePx)`, which makes the expectations
    400×300 → 2 and 4032×3024 → 16. Cost if wrong: slightly smaller thumbnails.
11. **One mime table, not two.** The SAF store needed extension → mime, the inverse of `:core`'s
    existing table. Rather than a second copy in `:app`, `MimeTypes.mimeForExtension(ext)` was added
    in `:core` — a small `:core` touch inside an `:app` task, accepted deliberately.
12. **Settings' `Choose folder` is disabled only when attachment rows exist AND the store is
    `Ready`.** Spec §5.3 blocks a store change once rows exist (§11.12), but a data-only restore
    onto a fresh install produces rows with **no** store — and that install must still be able to
    pick a folder or the artifacts can never be restored. A deviation from the spec's letter in
    service of its §11.3 ruling that data-only restore is first-class.
13. **The Backup screen says "Restore files", not the spec's "Restore artifacts".** "Artifacts" is
    the format's word, not the owner's. The wording is a one-line change if the owner prefers the
    spec's.
14. **The export's cleanup runs under `withContext(NonCancellable)`.** Otherwise cancelling an
    export mid-write left the data archive behind, because the cleanup itself was cancelled. The
    invariant now names cancellation explicitly and a test drives it with a suspending fake delete
    on a cancelled job.
15. **A restore message names missing and unexpected entries.** A damaged artifacts archive used to
    report unqualified success. It now appends the counts when they are non-zero — "N listed files
    were not in the archive" — so a half-restore cannot read as a whole one. The wording is the
    implementer's; the owner may adjust it.
16. **A tap on a row whose bytes are gone raises "Not on this device".** Spec §8.1 does not say what
    happens; this resolves it as a snackbar rather than a silent no-op or an error dialog.
17. **`AccessLost` shows the section's "not set up" block.** Settings distinguishes the two states
    and is where the fix is; giving the section its own copy for `AccessLost` is a nicety — recorded
    in §7.
18. **`:app` stopped compiling between Tasks 3 and 5, deliberately.** The grown `DeleteAsset` /
    `DeleteEvent` constructors and the renamed export land in `:core` before `:app` has anything to
    wire them to. Tasks 3–5 gated on `:core:test` only; Task 6 was the first `:app` compile and
    fixed it with a minimal bridge that Task 9 deleted. Cost if wrong: one extra compile-fix round.
19. **`BackupIO.write(bytes)` was removed.** With the 1C single-file export gone (spec §7.3) nothing
    called it: a destination is now a folder, expressed as `BackupSetSink`. A port method with no
    caller is worse than a two-line removal.
20. **`RawDocumentFile.createFile` appends the mime type's extension.** Discovered by running, not
    guessed. The store already resolved a locator through the `<id>.` prefix when the exact name was
    absent, so production was correct and the *tests* were wrong; the rename test now observes the
    provider's behaviour instead of simulating it (§3).
21. **Scenario (a)'s navigation barrier had to be a node only Settings renders.** Both screens
    render the uppercased `ATTACHMENT STORAGE`, so the original `awaitText` was satisfied by the
    screen the click was leaving. It now waits for `Choose folder` to arrive **and** the section's
    own `Choose a folder in Settings` to go, because `NavDisplay` can have both entries composed
    during a push and `onNodeWithText` demands exactly one match.

**Design-time deviations from the older design docs (spec §11), now recorded in the docs
themselves.** `formatVersion` keeps its name in the data manifest where D7 said
`dataFormatVersion` (the artifacts manifest uses `dataFormatVersion` for the cross-reference); there
is **no SQL `CHECK`** for exactly-one-owner — the mapper and the backup reader enforce it; there is
**no `LOCAL` provider** at all, absent rather than reserved; restore is **two steps** because
`OpenDocument` cannot see a sibling file, and a data-only restore is a first-class outcome; and a
store change is blocked once rows exist, with the migration UX left to 4B. D4 §11's table and D7's
Phase 4 block now say all five.

## 7. Deferred

Nothing here blocks the phase; all of it is written down so the next phase does not rediscover it.

**Minors the reviews parked for the final wave or for 4B.**

- **A `CancellationException` from `AddAttachment`'s cleanup is recorded as suppressed rather than
  rethrown.** A narrow race inside the row-write failure path; nothing is dropped.
- **`:core`'s store contract has no empty-payload case**, and `failOnUpsert` is written inline in
  the fakes rather than through an `UpsertRig` (both brief-verbatim). The overwrite case the review
  asked for *was* added — on the emulator, as
  `SafTreeAttachmentStoreContractTest.puttingTwiceAtOneLocatorReplacesTheDocument`.
- **Four `BackupCodec` test and shape minors:** the duplicate-locator test should also assert that
  "duplicate id" fires; there is no test that a *failed* import sweeps nothing (rollback == 1,
  deletes == 0, bytes still present); `eventIds` is recomputed instead of reusing what `uniqueIds`
  returned; and `validateGraph` is around 225 lines and wants its own `BackupValidation.kt`.
- **A `ZipException` from `zos.close()` on the artifacts writer's success path escapes raw**, and
  `deleteBestEffort` guards `IOException` only.
- **Three Room-side test minors:** `observeForOwner`'s test proves the query, not live emission (it
  should collect, or be renamed); the unique-index test accepts any exception rather than the
  constraint one; and `Migration1To4Test`'s name understates what it covers, as it has since 2B-2.
- **`MimeTypes.mimeForExtension("jpeg")` falls back to `application/octet-stream`** — the alias map
  (`jpeg`, `tif`, `htm`) is a small `:core` addition nobody has made yet.
- **`SafTreeAttachmentStore.store()` and `viewUri()` each resolve the tree twice**, and `open()` can
  still surface a raw `FileNotFoundException` rather than a `StoreIoException`.
- **Four DOCUMENTS-section minors:** the picked-file mapping runs in a `rememberCoroutineScope` and
  can drop a pick if the section leaves composition mid-flight; two writers share `storeState` (a
  comment, not a race); the ViewModel does one provider round trip on the main thread at
  construction; and the `messages` flow's `buffer = 1` can drop back-to-back refusals — which is
  house-wide, not 4A's.
- **`AccessLost` has no distinct copy in the DOCUMENTS section** (§6 ruling 17), and a camera
  capture's temp file is orphaned on the `OwnerMissing` refusal path.
- **Settings releases the old persisted grant before the new take is known to have succeeded** — the
  wrong order if the new take fails — and `lastRestoredBackupSetId` is carried in the Backup
  screen's state but never rendered.
- **`wipedDir("set-<nanoTime>")` leaves one empty directory per run** of the writer test under the
  app's external files directory. App-private; it goes with the app.
- **Task 1's brief asked for no tests of a name ending in `file.` or of `extensionFor("")`.** The
  code is correct by inspection; the cases are unwritten.

**Out of scope by the spec (spec §2, "Not in 4A", 4B after 3R), verbatim.** REFERENCE-mode
attachments (`SAF_DOCUMENT` pointers), changing the store folder once attachments exist (migration
UX), an orphan sweep / storage health job, attaching from the event entry form, attachment search,
EXIF capture dates, video.

**Still deferred from earlier phases and untouched by 4A.** The automatic pre-delete snapshot D4 §13
mentions (Phase 3R); the month-day picker's discarded year; cross-field date ordering; and the 2B-2
minors no 4A task happened to touch.

## 8. What 4A changed for Phase 3, 3R and 4B

1. **A completion flow can attach a photo without inventing anything.** `AddAttachment` takes an
   `AttachmentOwner.OfEvent` and `DeleteEvent` already sweeps the bytes, so a Phase 3 completion
   form that writes an event can hand the same command the same picker result. The spec left
   attaching from the *entry form* to 4B, but the use case, the store and the section are not what
   is missing — only the button is.
2. **`DeleteEvent` and `DeleteAsset` now own byte cleanup, in one place.** The FK cascade takes the
   rows; `AttachmentSweep` takes the bytes, catching only `StoreIoException` and rethrowing
   cancellation. Any Phase 3 table that hangs off an asset or an event inherits a delete path that
   already knows a store can be absent, broken, or slow.
3. **A backup is a set, so 3R must write two files, not one.** `ExportBackupSet` returns
   `BackupSet(data, plan)` and the writer is a `BackupSetSink` over a folder — a remembered
   destination in 3R is a persisted tree URI handed to the same sink, with no format change. The
   all-or-nothing rule (`ArtifactsWritten.covers(plan)`) is where 3R's "was that backup good?" check
   already lives, and the cleanup is `NonCancellable`, which is what a cancelled worker needs.
4. **Restore is two steps and says so, which is what makes an automatic backup safe to restore
   from.** A data-only restore is a first-class outcome: the rows come back reading "Not on this
   device" and the matching artifacts archive completes the set, refused by `backupSetId` if it is
   the wrong one. 3R can restore the database without waiting on hundreds of megabytes of bytes.
5. **The store is behind a port with a real contract, asked twice.** `AttachmentStore`'s six claims
   are tested against the in-memory fake in `:core` and against the real `DocumentFile` store on the
   emulator. 4B's second provider — a `SAF_DOCUMENT` reference store, or the store-location
   migration's copy loop — is a new implementation of an interface that already has its acceptance
   test written.
6. **Locators are provider-relative, so the root is one setting.** `assets/<id>/<id>.<ext>` says
   nothing about which tree it is in, which is precisely what 4B's store-location change needs: the
   copy loop moves bytes and leaves every row alone.
7. **`StoreState` is a visible state, never a crash.** `NotConfigured`, `Ready` and `AccessLost` are
   rendered in Settings and in the section, and the same-folder repair already exists. 4B's grant
   health finding and the orphan sweep have a state machine to hang off rather than one to invent.

## 9. Final gate

### No device

```
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin
```

→ **BUILD SUCCESSFUL**, 60 actionable tasks. Test totals from the JUnit XML: **`:core` 346 tests,
0 failures, 0 errors, 0 skipped**; **`:app` 210 tests, 0 failures, 0 errors, 0 skipped**
(per-class breakdown in §3). Re-run with `clean` in front after the documentation was written — 62
actionable tasks, same totals, byte-identical debug APK.

Debug APK:

```
-rw-r--r--. 13774485  app/build/outputs/apk/debug/app-debug.apk
```

(≈ 13 452 KiB, up 194 KiB from 2B-2's 13 575 589 for the model, the five use cases, the artifacts
codec, the v5 migration, the SAF store, thumbnails, the DOCUMENTS section and the Settings section.)
`versionCode` **6**, `versionName` **`2.4`**.

**`:app:assembleRelease` was not run in this half of Task 11.** It reads the release keystore, which
the implementer's sandbox refuses to touch; the signed release build and its
`apksigner verify --print-certs` line belong with the controller's phone step (§4 row P1 installs
the debug build, as spec §3 requires). Every prior phase's gate recorded `Signer #1 certificate DN:
CN=noteNFC, O=GonzRon`, and nothing in 4A changes the signing configuration.

### On the emulator only

```
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

→ **BUILD SUCCESSFUL** in 1m 37s. **58 tests, 0 failures, 0 skipped**, on the device named by
Gradle as `Running tests on devices: emulator-5554 - 17` and by the results XML
`app/build/outputs/androidTest-results/connected/debug/TEST-emulator-5554 - 17.xml`, whose every
suite carries `<property name="device" value="emulator-5554" />`.

Two devices were attached to `adb` throughout: the emulator and the owner's phone. Every Gradle
invocation that can reach a device carried `ANDROID_SERIAL=emulator-5554`; every `adb` invocation
carried `-s emulator-5554` (the exception is `adb devices`, which targets nothing). No `install`,
`uninstall`, `pm clear` or `force-stop` was issued against any other serial at any point in this
phase.

### Privacy grep

Patterns are written with placeholders; the private strings themselves are never in this file.

```
git grep -nIE 'GonzRon|/home/|/Users/|emulator-[0-9]+|[0-9A-F]{8}:[0-9A-F]{2}' \
  -- . ':!docs/design/phase-4a-evidence.md' ':!docs/superpowers/plans'
git grep -nIiF -- '<the phone's serial>'
git grep -nIiF -- '<the phone's model>'
git grep -nIiF -- '<the phone's codename>'
git grep -nIiE '/home/[a-z]+|/Users/[a-z]+' -- . ':!.superpowers'
```

→ **clean.** The first prints only `GonzRon` as the *project identity*, which is deliberate and
pre-dates this phase: the project URL in `SettingsScreen.kt`, the release certificate's `O=` in
`README.md` and in five earlier evidence files, and the GitHub issue links in
`docs/design/issues/applied.md`. No `/home/…` or `/Users/…` path and no tag-UID-shaped string
appears in any tracked file; the only `emulator-NNNN` strings anywhere in the tree are in **this
file**, which the grep excludes on purpose because naming the device the suite ran on is the point
of §4 and §9 — and an emulator serial is not owner data. The three device patterns — taken from
`adb getprop` through shell variables so the real words never reach a file — match **nothing**; the
fourth pattern, the phone's *manufacturer*, is a generic platform-vendor word that occurs 13 times
across the design docs, `gradle/libs.versions.toml` and `settings.gradle.kts`, and every hit is
about the Android platform, the Maven repository or the KSP plugin id, never about a device. Same
false-positive class as 2B-1's and 2B-2's. The owner's sync-folder names appear nowhere: the only
hits for the two sync tools' names are the tool names themselves, in the Settings quiet line ("a
sync tool such as Syncthing owns any off-device copy"), the S5 spike, D7, D8 and the spec — all of
which pre-date or are the design record, and none of which names a folder.

No note id and no tag UID is written anywhere; the emulator suites seed assets called "Well pump"
and "Mower" with files `pump-manual.pdf`, `label.jpg` and `receipt.pdf`, and the SPA filenames in
§4 row P3 are the manufacturer's own public document names, which spec §10.1 already carries.

### Taxonomy grep

```
git grep -nI 'LOCAL' -- 'core/src/main/kotlin/**/Attachment.kt'
```

→ **one hit, and it is the absence itself.** `Attachment.kt` line 11 is the comment
`/** No LOCAL member by the owner's ruling (spec §11.8): absent, not reserved. */`. There is no
`LOCAL` enum member, no branch on one and no code path to app-private storage; `StorageProvider` is
`SAF_TREE` and `SAF_DOCUMENT` only, and `SAF_DOCUMENT` has no store behind it until 4B. Exit
criterion: the grep's *intent* holds — the plan expected it to print nothing at all, and what it
prints is a KDoc line put there precisely so a future reader does not add the member back.

### Attribution grep

```
git log --format=%B 41af038..HEAD | grep -ciE 'co-authored|generated with'
```

→ **0.** No commit on this branch carries a `Co-Authored-By`, a `Generated-by` or any other
attribution trailer.
