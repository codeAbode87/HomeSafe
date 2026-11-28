package com.meticulouscreations.homesafe.fakefrigate

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A parcel the fake relay tracks; only what the app's screens show. */
data class FakeParcel(
    val id: String,
    val nickname: String,
    val carrier: String,
    val tracking: String,
    val stage: String,
    val stageAt: Double,
    val owner: String? = null,
    val surprise: Boolean = false,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val notes: String? = null,
    val shop: String? = null,
    val timeline: List<Pair<Double, String>> = emptyList(),
)

/** One charge of one car, in the relay's own fields (`relay.py`'s `/charging`). */
data class FakeCharge(
    val id: Long,
    val car: String,
    val name: String,
    val place: String,
    val started: Double,
    val ended: Double,
    val socStart: Int,
    val socEnd: Int,
    val kwh: Double,
    val peakKw: Double,
    val how: String = "power",
    val site: String? = null,
    val cost: Double? = null,
    val costIs: String? = null,
    val note: String? = null,
)

/** A stay in the made-up location history: lat/lon are on an invented grid in the open ocean. */
data class FakeVisit(val started: Long, val ended: Long, val lat: Double, val lon: Double, val place: String, val kind: String)

data class FakeMove(val started: Long, val ended: Long, val mode: String, val lat: Double, val lon: Double, val toLat: Double, val toLon: Double, val metres: Double)

/** A named place, as `/timeline/places` lists it. */
data class FakePlace(val id: String, val lat: Double, val lon: Double, val kind: String, val name: String, val city: String)

/**
 * What the fake relay holds: the scenario behind [FakeRelayServer], the way [FakeFrigateState] is
 * the one behind the fake Frigate. Everything is invented (people, shops, places on an ocean grid,
 * round amounts). Change it under [edit] to change what the app is told.
 */
class FakeRelayState(
    val people: MutableList<String> = mutableListOf(),
    val parcels: MutableList<FakeParcel> = mutableListOf(),
    val charges: MutableList<FakeCharge> = mutableListOf(),
    val visits: MutableList<FakeVisit> = mutableListOf(),
    val moves: MutableList<FakeMove> = mutableListOf(),
    val places: MutableList<FakePlace> = mutableListOf(),
    /** The Frigate accounts allowed to read the location history besides an admin, who always may. */
    val timelineUsers: MutableSet<String> = mutableSetOf(),
    val nowSeconds: () -> Double = { System.currentTimeMillis() / 1000.0 },
) {
    /** Runs [block] holding the state's lock, as the server does while it answers. */
    fun <T> edit(block: FakeRelayState.() -> T): T = synchronized(this) { block() }

    // ---- /packages ----------------------------------------------------------------------------

    fun packagesBody(viewer: String): JsonObject {
        val now = nowSeconds()
        return buildJsonObject {
            put("viewer", viewer)
            put("people", JsonArray(people.map { JsonPrimitive(it) }))
            put("now", now)
            put("today", Instant.ofEpochSecond(now.toLong()).atOffset(ZoneOffset.UTC).toLocalDate().toString())
            put("mail_on", true)
            put("synced", buildJsonObject { put("at", now - 600) })
            put("camera_on", false)
            put(
                "settings",
                buildJsonObject {
                    put("notify", buildJsonObject { put("shipped", true); put("out_for_delivery", true); put("delivered", true) })
                },
            )
            put("packages", JsonArray(parcels.filter { !it.surprise || it.owner == null || it.owner == viewer }.map(::parcelJson)))
        }
    }

    private fun parcelJson(p: FakeParcel): JsonObject = buildJsonObject {
        put("id", p.id)
        p.owner?.let { put("owner", it) }
        put("source", "mail")
        put("stage", p.stage)
        put("stage_at", p.stageAt)
        put("carrier", p.carrier)
        put("tracking", p.tracking)
        put("nickname", p.nickname)
        p.shop?.let { put("shop", it) }
        p.notes?.let { put("notes", it) }
        put("surprise", p.surprise)
        put("pinned", p.pinned)
        put("archived", p.archived)
        put("created", p.timeline.firstOrNull()?.first ?: p.stageAt)
        put("updated", p.stageAt)
        if (p.stage == "delivered") put("delivered_at", p.stageAt)
        put("timeline", buildJsonArray { p.timeline.forEach { (at, kind) -> add(buildJsonObject { put("at", at); put("kind", kind) }) } })
    }

    // ---- /charging ----------------------------------------------------------------------------

    private fun chargeJson(c: FakeCharge): JsonObject = buildJsonObject {
        put("id", c.id)
        put("car", c.car)
        put("name", c.name)
        put("place", c.place)
        put("source", "power")
        put("started", c.started)
        put("ended", c.ended)
        put("soc_start", c.socStart)
        put("soc_end", c.socEnd)
        put("kwh", c.kwh)
        put("peak_kw", c.peakKw)
        put("how", c.how)
        c.site?.let { put("site", it) }
        c.cost?.let { put("cost", it) }
        c.costIs?.let { put("cost_is", it) }
        c.note?.let { put("note", it) }
    }

    private fun monthOf(epochSeconds: Double): LocalDate = Instant.ofEpochSecond(epochSeconds.toLong()).atOffset(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1)

    fun chargingBody(): JsonObject {
        val now = nowSeconds()
        val sorted = charges.sortedByDescending { it.started }
        val thisMonth = monthOf(now)

        fun cars(month: LocalDate) = charges.groupBy { it.car }.map { (car, rows) ->
            val inMonth = rows.filter { monthOf(it.started) == month }
            val home = inMonth.filter { it.place == "home" }
            val fast = inMonth.filter { it.place != "home" }
            buildJsonObject {
                put("car", car)
                put("name", rows.first().name)
                put("home_kwh", home.sumOf { it.kwh })
                put("home_cost", home.sumOf { it.cost ?: (it.kwh * HOME_RATE) })
                put("fast_kwh", fast.sumOf { it.kwh })
                put("fast_cost", fast.sumOf { it.cost ?: 0.0 })
                put("sessions", inMonth.size)
                put("home_priced_kwh", home.sumOf { it.kwh })
                put("home_priced_cost", home.sumOf { it.cost ?: (it.kwh * HOME_RATE) })
                put("fast_priced_kwh", fast.sumOf { it.kwh })
                put("fast_priced_cost", fast.sumOf { it.cost ?: 0.0 })
                put("home_battery_kwh", home.sumOf { it.kwh * 0.9 })
                put("fast_battery_kwh", fast.sumOf { it.kwh * 0.9 })
            }
        }
        return buildJsonObject {
            put("provider", buildJsonObject { put("name", "Example Electric Co-op"); put("url", "https://example.com/rates") })
            put("plan", "flat_demo")
            put(
                "plans",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", "flat_demo"); put("name", "Flat Home"); put("kind", "flat"); put("current", true); put("effective", "2026-01-01")
                            put("periods", buildJsonArray { add(buildJsonObject { put("period", "off_peak"); put("rate", HOME_RATE); put("billed", HOME_RATE) }) })
                        },
                    )
                    add(
                        buildJsonObject {
                            put("id", "tou_demo"); put("name", "Time of Use"); put("kind", "tou"); put("effective", "2026-01-01")
                            put("peak_start", 16); put("peak_end", 21)
                            put(
                                "periods",
                                buildJsonArray {
                                    add(buildJsonObject { put("period", "peak"); put("rate", 0.40); put("billed", 0.40) })
                                    add(buildJsonObject { put("period", "off_peak"); put("rate", 0.10); put("billed", 0.10) })
                                },
                            )
                        },
                    )
                },
            )
            put("tax_percent", 0.0)
            put("gas", buildJsonObject { put("price", 4.0); put("mpg", 25.0) })
            put(
                "now",
                buildJsonArray {
                    charges.map { it.car to it.name }.distinct().forEach { (car, name) ->
                        add(buildJsonObject { put("car", car); put("name", name); put("battery", 80.0); put("limit", 90.0); put("range_mi", 240.0) })
                    }
                },
            )
            put(
                "months",
                buildJsonArray {
                    for (month in listOf(thisMonth, thisMonth.minusMonths(1))) {
                        add(
                            buildJsonObject {
                                put("month", month.toString().take(7))
                                put("cars", JsonArray(cars(month)))
                                put("plans", buildJsonObject { put("flat_demo", 12.0); put("tou_demo", 9.0) })
                            },
                        )
                    }
                },
            )
            put("by_hour", JsonArray(List(24) { JsonPrimitive(if (it in 0..5) 2.0 else 0.0) }))
            put("sessions", JsonArray(sorted.take(PAGE).map(::chargeJson)))
            put("more_sessions", sorted.size > PAGE)
            sorted.lastOrNull()?.let { put("first_session", it.started) }
            put("recording_from", thisMonth.minusMonths(1).toString().take(7))
            put("setup", buildJsonObject { put("rates", true); put("power_stream", true); put("receipts", false) })
        }
    }

    fun chargesBefore(before: Double): JsonObject {
        val older = charges.filter { it.started < before }.sortedByDescending { it.started }
        return buildJsonObject {
            put("sessions", JsonArray(older.take(PAGE).map(::chargeJson)))
            put("more_sessions", older.size > PAGE)
        }
    }

    /** A charge with its hour-by-hour prices, or null for one that isn't in the ledger. */
    fun chargeDetail(id: Long): JsonObject? = charges.firstOrNull { it.id == id }?.let { c ->
        val hours = buildJsonArray {
            var hour = c.started - c.started % 3600
            while (hour < c.ended) {
                add(buildJsonObject { put("hour", hour); put("kwh", c.kwh / 3); put("price", HOME_RATE); put("period", "off_peak") })
                hour += 3600
            }
        }
        JsonObject(
            chargeJson(c) + mapOf(
                "hours" to hours,
                "plans" to buildJsonObject { put("flat_demo", c.kwh * HOME_RATE); put("tou_demo", c.kwh * 0.10) },
            ),
        )
    }

    // ---- /timeline ----------------------------------------------------------------------------

    private fun yearOf(epochSeconds: Long) = Instant.ofEpochSecond(epochSeconds).atOffset(ZoneOffset.UTC).year

    private fun years() = (visits.map { yearOf(it.started) } + moves.map { yearOf(it.started) }).distinct().sorted()

    fun timelineBody(user: String): JsonObject {
        val all = visits.map { it.started } + moves.map { it.started }
        return buildJsonObject {
            put("user", user)
            put(
                "years",
                buildJsonArray {
                    years().forEach { y ->
                        add(
                            buildJsonObject {
                                put("year", y); put("etag", "demo-$y-${visits.size}-${moves.size}")
                                put("visits", visits.count { yearOf(it.started) == y }); put("moves", moves.count { yearOf(it.started) == y })
                            },
                        )
                    }
                },
            )
            all.minOrNull()?.let { put("first", it) }
            all.maxOrNull()?.let { put("last", it) }
            put("places", buildJsonObject { put("version", 1); put("total", places.size); put("named", places.size) })
            put("file", buildJsonObject { put("name", "timeline-demo.json"); put("ingested_at", nowSeconds() - 3600) })
        }
    }

    fun timelineYear(year: Int): JsonObject {
        val vs = visits.filter { yearOf(it.started) == year }.sortedBy { it.started }
        val ms = moves.filter { yearOf(it.started) == year }.sortedBy { it.started }
        val ids = vs.map { it.place }.distinct().sorted()

        fun <T> column(rows: List<T>, f: (T) -> JsonElement) = JsonArray(rows.map(f))
        return buildJsonObject {
            put("year", year)
            put("places", JsonArray(ids.map { JsonPrimitive(it) }))
            put(
                "visits",
                buildJsonObject {
                    put("started", column(vs) { JsonPrimitive(it.started) }); put("ended", column(vs) { JsonPrimitive(it.ended) })
                    put("offset", column(vs) { JsonPrimitive(0) }); put("lat", column(vs) { JsonPrimitive(it.lat) }); put("lon", column(vs) { JsonPrimitive(it.lon) })
                    put("place", column(vs) { JsonPrimitive(ids.indexOf(it.place)) }); put("kind", column(vs) { JsonPrimitive(it.kind) })
                },
            )
            put(
                "moves",
                buildJsonObject {
                    put("started", column(ms) { JsonPrimitive(it.started) }); put("ended", column(ms) { JsonPrimitive(it.ended) })
                    put("offset", column(ms) { JsonPrimitive(0) }); put("mode", column(ms) { JsonPrimitive(it.mode) })
                    put("lat", column(ms) { JsonPrimitive(it.lat) }); put("lon", column(ms) { JsonPrimitive(it.lon) })
                    put("to_lat", column(ms) { JsonPrimitive(it.toLat) }); put("to_lon", column(ms) { JsonPrimitive(it.toLon) })
                    put("metres", column(ms) { JsonPrimitive(it.metres) })
                },
            )
            // No recorded paths: a path is two packed strings the app decodes, and the visits and moves are what the screens list.
            put("paths", buildJsonObject {})
        }
    }

    fun timelinePlaces(): JsonObject = buildJsonObject {
        put("version", 1)
        put(
            "places",
            buildJsonArray {
                places.forEach { p ->
                    add(
                        buildJsonObject {
                            put("id", p.id); put("lat", p.lat); put("lon", p.lon); put("kind", p.kind); put("name", p.name)
                            put("city", p.city); put("region", "XX"); put("country", "Exampleland")
                        },
                    )
                }
            },
        )
    }

    companion object {
        private const val PAGE = 20
        private const val HOME_RATE = 0.20

        /**
         * Three parcels (two on the way, one delivered), two cars with a week of charges, and three days of an invented life,
         * placed relative to [nowSeconds]. [clock] is what the relay later reads as "now" (the packages' `now` and `today`);
         * give a clock that starts at [nowSeconds] when that is not the real time (`--now`).
         */
        fun household(
            nowSeconds: Double = System.currentTimeMillis() / 1000.0,
            clock: () -> Double = { System.currentTimeMillis() / 1000.0 },
        ): FakeRelayState {
            val hour = 3600.0
            val day = 86_400.0
            val now = nowSeconds
            val parcels = mutableListOf(
                FakeParcel(
                    "p1", "Garden hose", "Example Post", "EX0000000001", "out_for_delivery", now - 2 * hour, shop = "Maple Hardware",
                    timeline = listOf(now - 3 * day to "ordered", now - day to "shipped", now - 2 * hour to "out_for_delivery"),
                ),
                FakeParcel(
                    "p2", "Desk lamp", "Sample Express", "EX0000000002", "shipped", now - 5 * hour, shop = "Birch Lighting",
                    timeline = listOf(now - 2 * day to "ordered", now - 5 * hour to "shipped"),
                ),
                FakeParcel(
                    "p3", "Coffee beans", "Example Post", "EX0000000003", "delivered", now - 26 * hour, shop = "Cedar Roasters", pinned = true,
                    timeline = listOf(now - 5 * day to "ordered", now - 3 * day to "shipped", now - 26 * hour to "delivered"),
                ),
            )
            val charges = mutableListOf<FakeCharge>()
            for (i in 0 until 5) {
                val start = now - (i * 2 + 1) * day
                charges += FakeCharge(
                    id = 100L + i, car = "car_a", name = "Blue Hatch", place = "home", started = start, ended = start + 3 * hour,
                    socStart = 40, socEnd = 80, kwh = 24.0, peakKw = 7.2, cost = 4.8, costIs = "plan",
                )
            }
            charges += FakeCharge(
                id = 200, car = "car_b", name = "Red Wagon", place = "supercharger", started = now - 3 * day, ended = now - 3 * day + 0.5 * hour,
                socStart = 10, socEnd = 70, kwh = 40.0, peakKw = 150.0, how = "receipt", site = "Example Fast Charging", cost = 14.0, costIs = "receipt",
            )
            // An invented grid near (0, -30), the open Atlantic, a few hundredths of a degree apart.
            val dayStart = now.toLong() / 86_400 * 86_400
            val visits = mutableListOf<FakeVisit>()
            val moves = mutableListOf<FakeMove>()
            for (d in 1..3) {
                val base = dayStart - d * 86_400L
                visits += FakeVisit(base, base + 8 * 3600, 0.010, -30.010, "demo_home", "home")
                visits += FakeVisit(base + 9 * 3600, base + 17 * 3600, 0.040, -30.040, "demo_work", "work")
                visits += FakeVisit(base + 18 * 3600, base + 19 * 3600, 0.030, -30.020, "demo_cafe", "other")
                moves += FakeMove(base + 8 * 3600, base + 9 * 3600, "drive", 0.010, -30.010, 0.040, -30.040, 5_000.0)
                moves += FakeMove(base + 17 * 3600, base + 18 * 3600, "drive", 0.040, -30.040, 0.030, -30.020, 3_000.0)
            }
            val places = mutableListOf(
                FakePlace("demo_home", 0.010, -30.010, "home", "Maple Street Home", "Exampleton"),
                FakePlace("demo_work", 0.040, -30.040, "work", "Example Works", "Exampleton"),
                FakePlace("demo_cafe", 0.030, -30.020, "other", "Birch Cafe", "Exampleton"),
            )
            return FakeRelayState(
                people = mutableListOf("Alex", "Sam"), parcels = parcels, charges = charges, visits = visits, moves = moves, places = places,
                timelineUsers = mutableSetOf("viewer"), nowSeconds = clock,
            )
        }
    }
}
