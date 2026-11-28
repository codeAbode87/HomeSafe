---
name: apksim
description: Run, drive, screenshot and test HomeSafe's Android app on the Mac without an emulator, with apksim (github.com/aglose/apksim). Use when asked to run or try the Android app, to check a change in the real app rather than a preview, to tap through a flow, read the screen or logcat, or to run one device test (androidDeviceTest / androidTest) quickly.
---

# The Android app on apksim

apksim runs an APK as one process on the Mac: Android's framework on the JVM, no VM, no boot, no
install. It gives you an adb device, a control channel, and `--instrument` for device tests. Use it
instead of an emulator on this Mac. It is not a phone: see "Limits" before trusting it for timing.

## Install

```bash
brew tap aglose/apksim
brew install apksim        # Homebrew may first ask you to trust the tap: that is the user's call, ask them
apksim --version           # 0.67.0 or later (0.69.0 is current); else `brew update && brew upgrade --cask apksim`
apksim --train             # optional: builds the startup cache now (otherwise in the background on first launch)
```

CI pins its own image (`ghcr.io/aglose/apksim:…` in `.github/workflows/apksim.yml`), whatever is
installed here. The record, replay and crawl steps below need 0.67.0 (`--biometrics auto`).

An installed release keeps its state (app data, each instance's ports) in `~/.apksim`; a source
checkout of apksim keeps it in its own `work/`. Its helper scripts ship beside the launcher:

```bash
APKSIM_SCRIPTS="$(dirname "$(readlink -f "$(command -v apksim)")")/scripts"   # ctl.py, instrument.py, drive.py, crawl.py
```

## Build and launch

```bash
./gradlew :androidApp:assembleDebug        # a worktree also needs the gitignored androidApp/google-services.json
apksim --control auto --adb auto --no-network androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Run the launch in the background: it stays up until you quit it. It opens a window on the user's
screen; add `--headless` when nobody is watching. The first frame takes ~4 s. Each instance writes
its ports to `~/.apksim/tmp/<pid>/adb.port` and `control.port`; `python3 "$APKSIM_SCRIPTS/ctl.py" list`
lists running instances.

**Always pass `--no-network`.** It lets the app reach only localhost. The debug build has the
household server's LAN address compiled in and probes it while signing in, so on the home Wi-Fi
the app would take that route and show the real cameras. Never sign in to the real server, never
use the `homesafe-signin` credentials or the autofill file.

## Sign in to the fake Frigate

```bash
./gradlew :fake-frigate:run --args="--port 8971 --host 127.0.0.1 --advertise localhost --relay"   # in the background
```

`--relay` also serves the fake relay on 8787, which Packages, Charging and Atlas read; without it
those apps show their "unavailable" states. The fake's history is placed relative to the moment it
starts ("someone at the front door minutes ago"); `--now 2026-01-01T09:41:00Z` (or epoch seconds)
starts its clock at that instant instead, so two runs on different days serve the same data (see
"Record, replay and crawl").

Then sign in to `http://localhost:8971` as `admin` / `correct-horse` (`viewer` / `just-looking` is
read-only; `--quiet` gives cameras with no history). apksim reaches the Mac's own localhost
directly: there is no `10.0.2.2` (that is the emulator's alias; the CLI journeys type it, and
their runner rewrites it to `localhost` on apksim).

Why `127.0.0.1` and `localhost`: the app's API client reaches `localhost` on either loopback
address, but the video player (Media3's HTTP stack) connects to `127.0.0.1` for it, so with the
fake on `::1` alone every recording and clip says "Couldn't play this clip". apksim before 0.4.0
refused `127.0.0.1` under `--no-network` ("Operation not permitted"): upgrade rather than bind
`::1`. Leaving `--no-network` off is never the answer: then the LAN route above is open.

The household fake has three cameras: Front Door, Driveway, Back Yard. Anything else on Home
(a "Doorbell", say) is left over in the app's saved data, not the fake: see "App data".

## Drive it

```bash
adb connect localhost:PORT                 # PORT from adb.port; it shows as apksim_phone
export ANDROID_SERIAL=localhost:PORT       # so plain adb commands go to it
adb shell apksim nodes                     # accessibility tree: [l,t,r,b] text=… desc=… field clickable
adb shell input tap 411 702                # device pixels: 822×1782, density 2 (dp × 2)
adb shell "input text 'http://localhost:8971'"
adb shell apksim key A 1 ctrl; adb shell apksim key DEL 1    # select all and delete: clears a field
adb shell input keyevent KEYCODE_BACK
adb exec-out screencap -p > shot.png       # app pixels; open it with the Read tool
adb logcat -d -t 300                       # the app's log (-s TAG, *:E work too)
adb shell apksim quit
```

- Find what to tap in `nodes` and tap the centre of its bounds; positions move (an error message
  under a field pushes the button down), so read `nodes` again rather than reusing coordinates.
- Typing lands asynchronously: wait a second after `input text` before reading `nodes`, or you
  see half the text. Password fields always read `•••` in `nodes`; the screenshot shows the text
  once "Show password" is on.
- `adb shell apksim CMD` runs any of apksim's control commands. The useful ones here:
  `device phone|foldable|tablet|desktop` or `size WxH` in dp (the shell's sidebar starts at
  840dp wide; `config` shows what the app sees), `rotate left|right|portrait|landscape`, `appearance dark|light`, `fontscale 1.3`,
  `locale es-ES`, `keyboard soft on` (a drawn keyboard, so `imePadding()` is exercised), `back`,
  `home`/`resume`, `recreate`, `process-death`, `open URL` (deep link), `permission allow|deny`,
  `notifications [open N|last|clear]`, `push key=value…` (an FCM message), `location LAT LNG`,
  `biometrics enroll|match|nomatch`, `animscale 10|1|0`, `a11y` (unlabelled controls, small touch
  targets), `debug layout on`, `shot FILE`. The full list is in apksim's README (beside the launcher) and its AGENTS.md.
- From 0.5.0, `adb install` takes an instrumentation-only APK and `am instrument -w PKG/.Class &`
  runs it in the instance, so the Android CLI works (`android layout`, `input`, `screen capture`).
  Not supported: installing another app (one app per instance), `pm clear` (see "App data"), and
  running test APKs over adb (use `--instrument`). Back on the app's last screen quits the
  instance, and there is no soft keyboard unless you pass `--soft-keyboard`, so a Back meant to
  hide the keyboard quits the app. The drawn keyboard is in neither `screencap` nor
  `android layout`'s windows: only the page moving up shows it.
- The CLI journeys in `androidApp/src/journeysTest` run on apksim, an instance per journey:
  `APKSIM=apksim scripts/run-android-cli-journeys.sh` with the fake Frigate running (above).
  docs/testing.md has the rest.

## App data

App data persists between launches, per package (`--data-slots NAME` keeps a separate copy under
that name). A sign-in does not survive a cold start by itself: the debug build asks for the
password again on every launch unless fingerprint sign-in was turned on (the "Enable fingerprint
sign-in?" offer after a password sign-in), and then only with `--biometrics auto` or `biometrics
match` to answer the prompt. To start from a fresh install, quit the app, then:

```bash
python3 "$APKSIM_SCRIPTS/ctl.py" erase com.meticulouscreations.homesafe.debug   # refuses while it runs
```

## Record, replay and crawl

apksim's flight recorder (`--rec`), network recording and crawler (`crawl.py`) are its own docs
(`docs/flight-recorder.md`, `docs/replay.md`, `docs/network.md`, `docs/crawl.md` beside the
launcher). What matters for HomeSafe:

- **Use a mapped hostname, not `localhost`.** apksim's network tap never reads loopback, so a
  recording of a session signed in to `http://localhost:8971` holds no requests, and a crawl's
  `--freeze-network` freezes none. Start the fake with `--advertise frigate.test --relay` and add
  `--network-host frigate.test=127.0.0.1` beside `--no-network` (no port: the relay's 8787 is
  reached on the same name), then sign in to `http://frigate.test:8971`. `--no-network` still
  holds: the name goes to the Mac's loopback and nowhere else.
- **Recording a bug:** `apksim --headless --control auto --no-network --no-autofill --fresh
  --data-slots hsrec --network-host frigate.test=127.0.0.1 --rec all --rec-window 600 APK`, sign
  in, reproduce, then save it over the control port: `python3 "$APKSIM_SCRIPTS/drive.py" PORT
  'rec save bug.apkrec'`. Replay it with `apksim replay --headless --no-network --network-match
  method,url --network-ignore-query device [--apk FIXED.apk] bug.apkrec` (exit 0 matched, 3
  diverged): the recorder leaves the typed password out and replay types `x`, so the sign-in
  request matches only on method and URL, and the app makes a device id after the first
  checkpoint, which no recorded query has.
- **Crawling signed in:** prepare a slot once with fingerprint sign-in, then let the walk copy it:

  ```bash
  apksim --headless --control auto --no-network --no-autofill --fresh --data-slots hscrawl \
    --biometrics auto --network-host frigate.test=127.0.0.1 APK
  # sign in to http://frigate.test:8971 as admin, tap Enable on the fingerprint offer, quit
  python3 "$APKSIM_SCRIPTS/crawl.py" APK --out map --keep-data --record-deps -j 6 --max-depth 5 \
    -- --data-slots hscrawl --no-network --network-host frigate.test=127.0.0.1
  ```

  `--keep-data` passes `--biometrics auto`, so each copy unlocks itself. Never pass
  `crawl.py --autofill`: it reads the saved sign-in file, which is the real server's.
  Depth matters: a screen at `--max-depth` is found but not explored, so `crawl-select.py` cannot
  place a change on it; Apps → Charging → Charges → a charge needs depth 5.
- **Comparing two walks:** crawl.py sets the app's clock to `2026-01-01T09:41:00Z` (crawl.json's
  `clock`), so start the fake with `--now 2026-01-01T09:41:00Z` too and a baseline and a later
  `--diff-seed` walk see the same "minutes ago" and the same dates. Up to apksim 0.69.0,
  `--freeze-network` freezes the relay screens as 401s (the replayed sign-in's cookie is
  redacted); walk live against the fake instead.

## Device tests in seconds

Build the module's test APK, then run one class or one method. A library's `androidDeviceTest`
APK instruments itself, so it is passed as both APKs:

```bash
./gradlew :shared:assembleAndroidDeviceTest        # :app, :finance, :weather, :fitness, :atlas, :packages, :charging, :car3d
A=shared/build/outputs/apk/androidTest/shared-androidTest.apk   # features/X/build/outputs/apk/androidTest/X-androidTest.apk
apksim --instrument $A -e class com.meticulouscreations.homesafe.uitest.MomentsFeedUiTest $A                  # 17 tests, ~7 s
apksim --instrument $A -e class com.meticulouscreations.homesafe.uitest.MomentsFeedUiTest#theTypeChipPicksACategoryFromItsMenu $A   # ~4 s
```

- `-e package P`, `-e notPackage P` and `-e class A,B` work as AndroidJUnitRunner's arguments.
- The end-to-end tests (`:androidApp:assembleDebug :androidApp:assembleDebugAndroidTest`) are two
  APKs: `apksim --instrument androidApp-debug-androidTest.apk androidApp-debug.apk`.
- A whole suite with JUnit XML, one process per test as the Orchestrator does:
  `python3 "$APKSIM_SCRIPTS/instrument.py" APP TEST --isolate -j 4 --xml out.xml`.
  `scripts/ci/apksim-device-tests.py IMAGE OUT` runs every suite the way CI does, in apksim's
  Linux image (the version `.github/workflows/apksim.yml` pins); with apksim installed instead,
  `APKSIM_INSTRUMENT="python3 $APKSIM_SCRIPTS/instrument.py" scripts/ci/apksim-device-tests.py - build/apksim-results`.
- The whole-app journeys (`-e package com.meticulouscreations.homesafe.integration` of `:app`, 76
  tests, ~2.5 min) have a known flake when all of them run in one process on the Mac: one to four
  time out waiting for the Tailscale badge, with "Failed to connect to /127.0.0.1" in the log.
  Rerun those classes on their own (they pass), or use `--isolate`.

## Limits

- Tests run Robolectric-style on API 36 with a simulated clock, whatever the app targets. A pass
  here is what CI gates pull requests on; the nightly emulator run checks it against API 34.
- Timing is not a phone's. Keep the emulator for `startup-perf` and anything that measures a
  real device, and the user's Pixel only with their say-so.
- For how one composable looks in one state, `scripts/preview` (the `ui-previews` skill) is faster
  and needs no sign-in; use apksim for flows, navigation, Back, the keyboard and real data from the
  fake Frigate.
- Clips play, and a save fetches the clip but can't finish. The fake Frigate serves real video,
  and the app run by hand decodes it on a Mac (`adb shell apksim video` counts the pictures). A save
  fetches the clip with the app's own HTTP client, then fails putting it in Downloads: apksim's media
  store is Robolectric's, which takes only DCIM, Movies and Pictures (logcat `ClipDownloader`). In test
  runs (`--instrument`) no video decoder is listed, so `ClipJourneyTest` skips itself
  on apksim and is checked on CI's emulator (docs/testing.md, "Clips, end to end").
- iOS is not covered: build the iOS app only when asked or when the change is iOS-specific.
