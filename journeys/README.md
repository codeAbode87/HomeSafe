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
