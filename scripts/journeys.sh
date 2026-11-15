#!/usr/bin/env bash
# The journey tests (journeys/README.md), as CI and a developer run them. No language model is involved: a run
# replays planned walks on apksim against the fake Frigate.
#
#   scripts/journeys.sh pr        what a pull request must pass: the inventory is current, the walks the change can
#                                 reach pass, and every new or changed screen has verified coverage (nines gate)
#   scripts/journeys.sh nightly   everything: the core plan, a depth sample that changes each night, the audit
#   scripts/journeys.sh build     the debug APK and the fake Frigate the two above need
#
# nines lives in its own repository; NINES_HOME says where (default ~/nines), and its virtualenv's `nines` is used
# unless one is already on the PATH.
set -euo pipefail
cd "$(dirname "$0")/.."

NINES_HOME="${NINES_HOME:-$HOME/nines}"
if ! command -v nines >/dev/null 2>&1; then
  [ -x "$NINES_HOME/.venv/bin/nines" ] || { echo "journeys: no nines on PATH and none at $NINES_HOME/.venv/bin/nines" >&2; exit 2; }
  PATH="$NINES_HOME/.venv/bin:$PATH"
fi

export NINES_MODEL=journeys/nines.yaml
export NINES_INVENTORY_CONFIG=journeys/inventory.yaml
export NINES_INVENTORY=journeys/inventory.json
export NINES_APK="${NINES_APK:-androidApp/build/outputs/apk/debug/androidApp-debug.apk}"
export NINES_JOBS="${NINES_JOBS:-3}"

case "${1:-}" in
  build)
    ./gradlew :androidApp:assembleDebug :fake-frigate:installDist
    ;;
  pr)
    export NINES_OUT="${NINES_OUT:-build/nines/pr}"
    exec "$NINES_HOME/ci/pr.sh"
    ;;
  nightly)
    export NINES_OUT="${NINES_OUT:-build/nines/nightly}"
    export NINES_BASELINE_DIR="${NINES_BASELINE_DIR:-build/nines/baseline}"
    exec "$NINES_HOME/ci/nightly.sh"
    ;;
  *)
    sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
    ;;
esac
