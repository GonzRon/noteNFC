# Issue #1: [MVP] Evolve noteNFC into an NFC-first maintenance tracker
state=open created=2026-09-13T23:26:44Z updated=2026-09-13T23:26:44Z labels= milestone=none comments=0

## Goal
Preserve noteNFC's existing NFC-to-external-note behavior while adding an asset-centric maintenance system.

The core interaction should be:

> Scan the physical asset -> immediately see what it is, what has been done to it, and what needs to happen next.

## MVP product model
An NFC tag points to a stable internal `Asset` record. The asset may optionally also contain one or more external deep links (Joplin, Obsidian, Logseq, generic URI).

Each asset has:
- name / description
- category
- manufacturer / model / serial number (optional)
- purchase / in-service date (optional)
- NFC tag binding
- notes
- external links
- maintenance history
- maintenance schedules
- current due / overdue state

## Primary workflows
1. Create an asset and write/bind an NFC tag.
2. Scan a tag to open the asset directly.
3. Record a maintenance event in a few taps.
4. Define recurring maintenance by elapsed time and/or usage.
5. Receive local reminders when maintenance is due.
6. Mark maintenance complete and automatically calculate the next due date.
7. Launch an external note when desired, preserving the original noteNFC use case.

## Design constraints
- Local-first; useful with no account or server.
- NFC tag stores only a stable identifier, not mutable asset data.
- Maintenance history should be append-oriented/auditable rather than overwritten.
- Existing Joplin workflow must continue to work.
- Keep the initial version useful for UPS batteries, lawn equipment, generators, HVAC, vehicles, tools, etc.

## Out of scope for initial MVP
- cloud service / mandatory login
- live IoT telemetry
- predictive maintenance / AI diagnosis
- multi-user fleet management
- purchasing / accounting system
- large parts inventory system

This issue is the umbrella for the initial maintenance-tracking feature set.

