# Issue #6: [MVP] Generalize external note/deep-link support beyond Joplin
state=open created=2026-09-13T23:27:14Z updated=2026-09-13T23:27:14Z labels= milestone=none comments=0

Preserve noteNFC's original purpose while making external-link handling generic instead of Joplin-specific.

## Requirements
- An asset can have zero or more named external links.
- Keep Joplin support and migrate existing saved mappings where possible.
- Support generic Android `ACTION_VIEW` URIs so additional note apps can work without hard-coding each provider when their URI schemes are stable.
- Validate/preview a URI before saving it.
- Provide an explicit `Open external note` action from an asset.
- Continue supporting the lightweight original workflow: share a compatible URI to noteNFC -> write it to a tag -> scan tag -> launch target app.

## Candidate integrations
- Joplin external links
- Obsidian URI
- Logseq/deep-link support if a reliable Android URI contract is available
- arbitrary HTTPS/custom-scheme URI

## Architectural note
External links should be attachments/actions associated with an asset (or standalone link record), not the asset's primary identity.

