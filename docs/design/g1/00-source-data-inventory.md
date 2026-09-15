# G1 input — what the owner's existing NFC-tagged notes actually contain

Read on 2026-09-14 from the owner's phone (eight notes in one notebook, each the target of a
legacy noteNFC tag). This is an **abstract** inventory: no note text, values, dates, file names,
product identifiers or personal details are recorded here, only the shapes the app must be able
to represent. The G1 screens and the Phase 2 model are checked against this list.

## The eight notes, by shape

| # | Kind of thing | What the note holds | Shape |
|---|---|---|---|
| 1 | Under-sink reverse-osmosis water system | A multi-year log of TDS readings taken at three points in the system (pre-filter, post-membrane, output), one row per test, roughly monthly | **Time series of several numeric measurements per event**, same instrument each time, no treatment column. Dates in mixed hand-typed formats. Table wider than a phone screen. |
| 2 | Spa water chemistry | Reference material: attached manufacturer PDFs/docs/zips (owner's manual, chemistry guide, troubleshooting manual, replacement-part sheets) followed by long prose: sampling procedure, the order in which parameters are adjusted, then a section per parameter (total alkalinity, pH, calcium hardness, TDS, sanitiser types, stabiliser) with how each is tested, its endpoint, interferences, work-arounds, dosing rules | **Attachments + structured reference text**, not a log. Maps to asset attachments (Phase 4) plus per-measurement-definition help text / target ranges (Phase 2 profiles). |
| 3 | Hot tub | A treatment log: one row per action with date, the chemical/action taken, quantity (in ounces *or* ppm), the measurement that prompted it ("before"), and columns for a follow-up test date and "after" reading that are mostly empty; also bare measurement rows (a TDS value) with no treatment | **Interleaved measurement and consumable-usage events**, quantities in inconsistent units, before/after pairing intended but rarely completed. Exactly the D4 §7 event-with-measurements-and-usage shape; the "after" reading is simply the next measurement event. |
| 4 | A personal, non-equipment note | A short list of personal figures | **Not an asset**: this tag must stay a standalone link to the note. Confirms standalone links are a first-class tag target, not a legacy leftover. |
| 5 | Walk-behind mower | The note's own external link at the top, then a two-column table: date, free-text description of the service done (several items in one sentence) | **Free-text service ledger entries**, one date, multiple parts/actions in prose. |
| 6 | Lawn tractor | A photograph of the printed maintenance schedule page from the manual: a checklist matrix of tasks × service intervals (hours / seasons) with tick marks | **Manufacturer schedule template as an image**; the intended data is a task list with intervals — the Phase 2/3 schedule template and the Phase 4 attachment, in one. |
| 7 | Snowblower A | Title only, empty body | A **placeholder asset**: identity exists, no history yet. Common; the app must not make an empty asset look broken. |
| 8 | Snowblower B | Title only, empty body | Same. |

## What this tells the design

1. **Measurements come in sets.** A water test is 3–6 readings taken together; the RO log is three
   readings per event. The entry screen and the ledger must treat a *set* as one event, not one
   reading per row.
2. **Treatments and readings are interleaved in one chronological story.** The hot-tub log mixes
   "added 25 ppm of X" with "TDS was 1055". The Service Ledger must show both kinds in one list,
   distinguishable at a glance without colour.
3. **Units are messy at the source.** Ounces, ppm, unitless TDS, "non-existent". The measurement
   definitions (Phase 2) need a unit per definition and free-text notes; the entry screen must
   accept a value *or* a note.
4. **Before/after pairing is desired but rarely completed.** The design should not demand it; the
   ledger derives "after" from the next reading of the same parameter.
5. **Reference knowledge belongs to the asset, not the event.** Manuals and per-parameter
   procedures are attachments and definition help text. The asset detail screen needs a visible
   home for them (Phase 4), even if 1C shows only a count.
6. **Manufacturer schedules arrive as pictures.** A photo of the interval matrix is the seed for
   schedule templates (Phase 3). The asset screen must be able to hold an attachment now and a
   structured schedule later without redesign.
7. **Empty assets are normal.** Two of eight are title-only. Dashboard and asset detail must render
   an asset with no events, no measurements and no schedule as *quiet*, not as an error or an
   empty-state lecture.
8. **Dates were typed by hand in several formats.** The app owns the date picker; import (later)
   must tolerate mixed formats.
9. **One tag is deliberately a note, not an asset.** Standalone link tags stay first-class (R-7).

## Mapping to the four G1 screens

- Asset detail: shapes 5, 6, 7 (identity plate with sparse metadata; ledger of free-text entries;
  attachment count; an empty asset).
- Dashboard: shapes 1, 3, 7, 8 together (a monthly-tested system, a treated spa, two idle
  seasonal machines).
- Measurement entry: shapes 1 and 3 (a set of readings with configured ranges; a separate
  treatment/consumable entry).
- NFC interaction: shape 4 (a tag that opens a note, no interstitial) and the asset case.
