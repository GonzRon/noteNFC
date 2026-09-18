# Versioning

ServiceTag's `versionName` follows **semantic versioning**, `MAJOR.MINOR.PATCH`, in its product sense (adopted 2026-09-18, owner ruling). Classify the change first; only then choose the number.

| Change | Bump | ServiceTag meaning |
|---|---|---|
| Bug fix or behaviour correction | PATCH | Something that was supposed to work already now does |
| Small UI correction | PATCH | No meaningful new capability |
| Security or reliability fix | PATCH | Existing behaviour made safer or correct |
| New user-facing capability | MINOR | A new feature, workflow, integration or substantial capability |
| Major compatible expansion | MINOR | For example a schedules and reminders subsystem |
| Breaking backup or data contract | MAJOR | Older clients or data no longer compatible without a migration |
| Breaking NFC record or protocol contract | MAJOR | An external contract intentionally made incompatible |
| Fundamental product-contract break | MAJOR | Documented behaviour intentionally broken |

Rules:

- A MINOR increment resets PATCH to 0. A MAJOR increment resets MINOR and PATCH to 0.
- Android's `versionCode` is independent: a monotonically increasing integer, +1 on every release whatever the `versionName` bump.
- A release is the tag `servicetag-v<versionName>` on the exact green commit; the release workflow refuses to publish when the APK's `versionName` differs from the tag.
- Released versions are never renamed or re-cut. The releases published before this policy stand as history and are read as `2.5.0`, `2.6.0` and `2.7.0` without their tags being rewritten.

Worked sequence:

| versionName | versionCode | what it was |
|---|---|---|
| 2.5 (= 2.5.0) | 7 | first ServiceTag release after the product split |
| 2.6 (= 2.6.0) | 8 | note-link functionality removed |
| 2.7 (= 2.7.0) | 9 | dashboard search and components; one activity-owned reader mode |
| 2.7.1 | 10 | #40 empty-store restore prompt, #41 inspect mode — corrective, so PATCH |
| next bug fix | 11 | 2.7.2 |
| schedules and reminders | 12 | 2.8.0 |
| an incompatible backup or protocol redesign | later | 3.0.0 |
