package com.meticulouscreations.homesafe.fakefrigate

import java.time.Instant

/**
 * Runs the fake Frigate on its own, for driving the real app by hand or from an Android CLI
 * journey on an emulator (which reaches the host machine at 10.0.2.2):
 *
 *     ./gradlew :fake-frigate:run --args="--port 8971 --host 0.0.0.0 --advertise 10.0.2.2"
 *
 * Sign in with `admin` / `correct-horse` (or the read-only `viewer` / `just-looking`). Add
 * `--quiet` for a server with cameras but no history, and `--relay` (with `--relay-port`, default 8787,
 * on the same host) for the fake relay behind Packages, Charging and Atlas.
 *
 * Its data is placed relative to the moment it starts ("someone at the front door minutes ago").
 * `--now 2026-01-05T09:00:00Z` (or epoch seconds) places it relative to that instant instead and holds
 * the clock there (every "now" the relay answers is that instant), so two runs on different days, or
 * two walks against one long-running fake, serve the same data: give the app the same instant
 * (apksim's `--clock`) and screens that show dates and times compare cleanly.
 */
fun main(args: Array<String>) {
    fun option(name: String): String? = args.indexOf("--$name").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val port = option("port")?.toInt() ?: 8971
    val host = option("host") ?: "0.0.0.0"
    val advertise = option("advertise") ?: "127.0.0.1"
    val pinned = if ("--now" in args) epochSecondsOf(option("now")?.takeUnless { it.startsWith("--") } ?: "") else null
    val server = fakeServer(quiet = "--quiet" in args, pinnedNow = pinned).start(
        port = port,
        host = host,
        advertiseHost = advertise,
        relayPort = if ("--relay" in args) option("relay-port")?.toInt() ?: FakeRelayServer.RELAY_PORT else null,
    )
    Runtime.getRuntime().addShutdownHook(Thread { server.close() })
    println("Fake Frigate listening on $host:${server.port} — sign in at ${server.baseUrl} as ${FakeFrigateState.ADMIN.username} / ${FakeFrigateState.ADMIN.password}")
    server.relay?.let { println("Fake relay listening on $host:${it.port}") }
    pinned?.let { println("Its clock is held at ${Instant.ofEpochMilli((it * 1000).toLong())} (--now)") }
    Thread.currentThread().join()
}

/**
 * The household (or [quiet]) fake, placed relative to the real time, or with [pinnedNow] relative to
 * that instant with every later "now" the relay answers held there too.
 */
internal fun fakeServer(quiet: Boolean = false, pinnedNow: Double? = null): FakeFrigateServer {
    val now = pinnedNow ?: (System.currentTimeMillis() / 1000.0)
    val clock: () -> Double = pinnedNow?.let { { it } } ?: { System.currentTimeMillis() / 1000.0 }
    val state = if (quiet) FakeFrigateState.quiet(now) else FakeFrigateState.household(now)
    return FakeFrigateServer(state, relayState = FakeRelayState.household(now, clock))
}

/** [instant] in epoch seconds: an ISO-8601 instant (`2026-01-05T09:00:00Z`) or a number of epoch seconds. */
internal fun epochSecondsOf(instant: String): Double =
    (instant.toDoubleOrNull()?.takeIf { it.isFinite() } ?: runCatching { Instant.parse(instant).toEpochMilli() / 1000.0 }.getOrNull())
        ?: throw IllegalArgumentException("--now takes an ISO-8601 instant such as 2026-01-05T09:00:00Z, or epoch seconds: '$instant'")
