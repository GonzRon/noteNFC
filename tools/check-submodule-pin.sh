#!/usr/bin/env bash
# The shared library is consumed only at an exact nfc-tag-core-v* tag, initialised, clean, and on
# the same agp/kotlin pins as this app (target §6.3, §19). Exit 1 names the first thing that is not so.
set -euo pipefail
cd "$(dirname "$0")/.."
test -f libs/nfc-tag-core/nfc-core/build.gradle.kts \
  || { echo "libs/nfc-tag-core is not initialised"; exit 1; }
pinned=$(git ls-tree HEAD libs/nfc-tag-core | awk '{print $3}')
actual=$(git -C libs/nfc-tag-core rev-parse HEAD)
[ "$pinned" = "$actual" ] \
  || { echo "submodule is at $actual but this commit pins $pinned"; exit 1; }
git -C libs/nfc-tag-core fetch --tags --force --quiet || true
git -C libs/nfc-tag-core describe --exact-match --match 'nfc-tag-core-v*' --tags HEAD \
  || { echo "submodule is not at an exact nfc-tag-core-v* tag (mutable HEAD)"; exit 1; }
diff <(grep -E '^(agp|kotlin) =' gradle/libs.versions.toml) \
     <(grep -E '^(agp|kotlin) =' libs/nfc-tag-core/gradle/libs.versions.toml) \
  || { echo "library catalog pins a different agp/kotlin than this app"; exit 1; }
[ -z "$(git -C libs/nfc-tag-core status --porcelain)" ] \
  || { echo "submodule working tree is dirty"; exit 1; }
echo "submodule pin ok: $(git -C libs/nfc-tag-core describe --exact-match --match 'nfc-tag-core-v*' --tags HEAD)"
