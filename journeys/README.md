# Journey model

`nines.yaml` and the files it includes describe the app as a graph: its screens (nodes), what can differ on entering
each (parameters), and the actions between them (edges). nines (a separate repository, on this Mac at `~/nines`) counts from that
graph everything that should be exercised, plans the cheapest walks that cover it, runs them on apksim against the
fake Frigate, and reports coverage per level (J1 screens, J2 states, J3 transitions, up to J7).

```bash
./gradlew :androidApp:assembleDebug :fake-frigate:installDist
nines check journeys/nines.yaml                      # the model loads; how many obligations it has
nines test journeys/nines.yaml --out build/nines     # plan, run, report: build/nines/coverage.html
nines look journeys/nines.yaml --at settings         # walk to a node and print what is on screen there
```

The scene starts the fake Frigate on `127.0.0.1:8971` and signs in as `admin` or `viewer` with the household or the
quiet data set, with or without the fake relay (`relay` context, `--relay`: Packages, Charging and Atlas show content with it and their "can't reach the server" screens without); the app runs with `--no-network`, as everywhere else on apksim. Nothing here signs in to the real
server.

To add a screen: `nines look` at the node it is reached from, write its node and the edge in (and Back out), run
`nines test`. The briefs in nines' `agents/` folder say how, one screen at a time.

## What counts, and how it is kept honest

The scenario count is every screen, every entry state, every transition and every screen under each disturbance
(rotation, recreation, process death, dark mode, large text…), plus the pairs a screen declares as interacting
(`interactions:`). A screen or edge that no walk can reach or recognise yet is `pending: <why>`: it stays in the count
and against the coverage figure until someone makes it real.

`inventory.json` is the code's own list of what the model must hold: every drawer app, tab, page, route, dialog,
sheet, menu, back-owning overlay, `*Screen`, state type and deep link the `commonMain` sources declare, extracted by
text analysis from `inventory.yaml` (no Gradle, no language model):

```bash
nines inventory --config journeys/inventory.yaml --out journeys/inventory.json   # regenerate after changing UI code
nines audit journeys/nines.yaml --inventory journeys/inventory.json              # what no screen of the model covers
```

## Pull requests and nights

```bash
scripts/journeys.sh build     # the APK and the fake Frigate
scripts/journeys.sh pr        # what a pull request must pass
scripts/journeys.sh nightly   # everything, plus a depth sample that changes each night
```

`pr` fails when the inventory is stale, when a walk the change can reach fails, or when `nines gate` finds a new page,
dialog or state the model does not claim, a new surface that is only `pending`, a changed UI file no screen's
`sources` names, or a changed screen whose scenarios are not covered. Its report says what to add. `nightly` runs the
whole core plan with re-planning rounds, samples the deeper levels with the day as its seed, audits the model against
the inventory, and compares coverage with the night before. Neither uses a language model: a run replays planned
walks. They are not part of CI yet: nines is a local checkout (`NINES_HOME`, default `~/nines`).
