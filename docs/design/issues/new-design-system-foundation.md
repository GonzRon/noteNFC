---
action: create
title: "[MVP] Phase 1C: Compose design system foundation (Apollo Service Binder)"
milestone: "Phase 1 — Tag survival (M1)"
labels: []
---

Identified in D12 ("Scope rule"): Phase 1C implements the foundational Compose design system that every later phase reuses. D12 was supplied by the product owner and recorded as approved design authority; it reopens no architectural decision in D3/D4/D5 and does not change D7's sequence.

**Prerequisite: Gate G1 (D8 §2) — representative-screen review before implementation.** Review 3–4 representative screens (asset detail, dashboard, structured water-test entry, NFC scan/write) to confirm the written system works as a real Android interface and that the Apollo influence stays restrained. Those mockups are not produced in Phase 0 unless separately requested.

## Goal

One theme layer, created once in Phase 1C and consumed — never redefined — by every later feature lane, so operational state has a single visual vocabulary.

## Scope

### Two layers, kept separate (D12 §15)

Material answers "how should a generic component look"; noteNFC semantics answer "what does this operational state mean".

- `NoteNfcTheme(darkTheme, dynamicColor, content)` resolving the Material 3 `ColorScheme` and providing the semantic layer through `LocalNoteNfcSemanticColors`.
- `NoteNfcSemanticColors` (`@Immutable`) holding a `StatusColor(foreground, container)` per operational state: maintenance OK, due soon, due, overdue, season inactive, paused, measurement low / in range / high, reminder healthy / attention / failure, sync problem, destructive action.
- Material 3 light and dark schemes (D12 §3, §4), typography (D12 §6 — system sans for prose, monospace for identifiers, serials, tag ids, voltages and meter readings; never for paragraphs or button labels), shapes (D12 §7 — 2 / 4 / 8 / 12 / 16 dp).
- Placement per D3 §3: `ui/theme/` — `Color.kt`, `SemanticColors.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`.

### Domain mapping

The semantic states map onto the existing vocabulary, not a parallel one: `OK`, `DUE_SOON`, `DUE`, `OVERDUE`, `INACTIVE_SEASON`, `PAUSED`, `NO_DATA` from D5 §1 (`NO_DATA` renders with the season-inactive treatment plus the wording **NO BASELINE**); measurement low / in range / high from `measurement_definition.range_low` / `range_high` (D4 §6); reminder failure and sync problem from the health findings of D3 §7.3.

### Appearance setting (D12 §13, recommendation C)

Apollo Service Binder is the default palette; Material You dynamic colour is an optional setting alongside a system/light/dark mode choice. Dynamic colour may replace primary, secondary, tertiary, ordinary containers and selected navigation. It must **never** recolour due, overdue, measurement low/high, reminder failure, sync failure or destructive actions.

### Shell

The app shell and navigation surfaces, plus the visual treatment needed by Phase 1C's asset, link and NFC screens (D12 §8 identity-plate skeleton, §11 NFC states).

## Out of scope

Phase 0 — no colours, typography, shapes, components, mockups or screen redesigns land there (D12 "Scope rule"). The Asset Identity Plate, Service Ledger, Instrument Measurement and entry forms are refined in Phase 2 (#3, #13); the dashboard attention hierarchy and reminder surfaces in Phase 3 (#5, #14, `new-reminder-health`, `new-local-reminder-provider`). `Apollo Service Binder` is the internal theme name, not a public subtitle or brand.

## Acceptance criteria

1. **No feature code references a raw `Color` value for operational state** — a lint or test sweep finds no `Color.Red`-style construct outside `ui/theme/`.
2. Status is **grayscale-obvious**: with colour removed, every state is still identifiable from position, wording and icon; red-vs-green distinction is never required.
3. Every semantic foreground/container pair meets at least **4.5:1** contrast in both light and dark themes.
4. Enabling dynamic colour changes primary/secondary/tertiary and ordinary containers, and leaves due, overdue, measurement low/high, reminder failure, sync failure and destructive actions unchanged.
5. Every D5 §1 status value and every measurement classification has exactly one semantic token; no screen invents its own.
6. Gate G1's representative-screen review is recorded as passed before implementation begins.

## Design references

D12 "Scope rule", "Signature devices", "Non-negotiables", §3 (light scheme), §4 (dark scheme), §5 (semantic system), §6 (typography), §7 (shape), §13 (dynamic colour decision), §15 (Compose architecture) · D3 §4 (Compose, the UI toolkit this is built on), §3 (package layout), §7.3 (health findings rendered) · D4 §6 (ranges driving LOW / IN RANGE / HIGH) · D5 §1 (status vocabulary) · D7 Phase 1C · D8 §2 gate G1.
