# Issue #5: [MVP] Add asset dashboard with maintenance status
state=open created=2026-09-13T23:27:08Z updated=2026-09-13T23:27:08Z labels= milestone=none comments=0

Add a simple home/dashboard view so the app remains useful even when the user does not scan a tag.

## Requirements
- List/search all active assets.
- Group/filter by category and maintenance status.
- Surface `overdue`, `due`, and `due soon` tasks prominently.
- Show next maintenance item and due date/value on each asset row/card.
- Tap an asset to open its detail/history page.
- Provide quick actions for `scan tag`, `add asset`, and `log maintenance`.
- Archived assets stay out of the default view but retain their history.

## Keep it intentionally simple
This is not intended to become a fleet-management analytics dashboard in the first release. Its job is to answer:

1. What needs attention?
2. Which physical asset is it?
3. When was it last serviced?
4. What should I do next?

