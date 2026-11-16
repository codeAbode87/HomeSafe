package com.meticulouscreations.homesafe.fakefrigate

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A stand-in for the HomeSafe relay (`relay/relay.py`, port 8787 next to Frigate), so the apps that
 * live on it (Packages, Charging, Atlas) show content in journeys instead of "can't reach the
 * server". It is a second Ktor server in the fake Frigate's process, off unless asked for
 * (`--relay`, or `FakeFrigateServer.start(relayPort = …)`), because the app's "no relay" paths are
 * worth testing too.
 *
 * It authenticates as the app expects: the Frigate session cookie, which the app's shared client
 * attaches to the relay too because cookies don't care about the port. [userFor] turns that cookie
 * into the account the fake Frigate issued it to (the relay checks it with Frigate the same way);
 * no or an unknown cookie is a 401. The routes the app also calls with a device secret
 * (`Authorization: Bearer`) are not modelled. The location history is answered to an admin or to
 * the accounts in [FakeRelayState.timelineUsers], and to nobody else (403, as the relay does).
 *
 * Anything else answers 404 and is listed in [unhandled]. Pictures are the one tiny PNG.
 */
class FakeRelayServer(val state: FakeRelayState = FakeRelayState.household(), private val userFor: (String?) -> FakeUser?) : AutoCloseable {

    private var server: EmbeddedServer<*, *>? = null

    /** The port the server listens on; valid after [start]. */
    var port: Int = 0
        private set

    private val log = CopyOnWriteArrayList<RecordedRequest>()
    private val notFound = CopyOnWriteArrayList<RecordedRequest>()

    /** Every request received so far, oldest first. */
    val requests: List<RecordedRequest> get() = log.toList()

    /** Requests nothing here modelled (answered 404). */
    val unhandled: List<RecordedRequest> get() = notFound.toList()

    fun start(port: Int = RELAY_PORT, host: String = "127.0.0.1"): FakeRelayServer {
        check(server == null) { "Already started" }
        val engine = embeddedServer(CIO, port = port, host = host) {
            routing { route("{...}") { handle { answer(call) } } }
        }
        engine.start(wait = false)
        server = engine
        this.port = runBlocking { engine.engine.resolvedConnectors().first().port }
        return this
    }

    override fun close() {
        server?.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
        server = null
    }

    private sealed interface Reply {
        class Json(val body: JsonObject, val status: HttpStatusCode = HttpStatusCode.OK) : Reply
        object Image : Reply
        object NotFound : Reply
    }

    private suspend fun answer(call: ApplicationCall) {
        val method = call.request.httpMethod
        val path = call.request.path().trimEnd('/')
        val query = call.request.queryParameters.entries().associate { (key, values) -> key to values }
        val body = if (method == HttpMethod.Post || method == HttpMethod.Put || method == HttpMethod.Patch) call.receiveText() else ""
        val request = RecordedRequest(method.value, path, query, body)
        log += request
        val user = userFor(call.request.cookies[FakeFrigateServer.SESSION_COOKIE])
            ?: return call.respondText("""{"detail":"Unauthorized"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
        when (val reply = synchronized(state) { dispatch(request, user) }) {
            is Reply.Json -> call.respondText(reply.body.toString(), ContentType.Application.Json, reply.status)

            Reply.Image -> call.respondBytes(FakeFrigateServer.PIXEL_PNG, ContentType.Image.PNG, HttpStatusCode.OK)

            Reply.NotFound -> {
                notFound += request
                call.respondText("""{"detail":"Not Found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
            }
        }
    }

    private fun json(text: String): JsonObject? = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()

    private fun error(status: HttpStatusCode, detail: String) = Reply.Json(Json.parseToJsonElement("""{"detail":"$detail"}""").jsonObject, status)

    private fun dispatch(request: RecordedRequest, user: FakeUser): Reply {
        val parts = request.path.trim('/').split('/')
        val method = request.method
        return when (parts[0]) {
            "packages" -> packages(parts, method, request, user)
            "charging" -> charging(parts, method, request)
            "timeline" -> timeline(parts, method, user)
            else -> Reply.NotFound
        }
    }

    // ---- /packages ----------------------------------------------------------------------------

    private fun packages(parts: List<String>, method: String, request: RecordedRequest, user: FakeUser): Reply {
        val viewer = user.username
        val fields = json(request.body)
        fun list() = Reply.Json(state.packagesBody(viewer))
        fun withParcel(id: String, change: (FakeParcel) -> FakeParcel?): Reply {
            val at = state.parcels.indexOfFirst { it.id == id }
            if (at < 0) return error(HttpStatusCode.NotFound, "No such package")
            change(state.parcels[at])?.let { state.parcels[at] = it } ?: state.parcels.removeAt(at)
            return list()
        }
        return when {
            parts.size == 1 && method == "GET" -> list()

            parts.size == 1 && method == "POST" -> {
                val tracking = fields?.get("tracking")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return error(HttpStatusCode.BadRequest, "Not a tracking number")
                val now = state.nowSeconds()
                state.parcels += FakeParcel(
                    id = "p${state.parcels.size + 1}-${tracking.hashCode().toUInt()}", nickname = fields["nickname"]?.jsonPrimitive?.contentOrNull ?: tracking,
                    carrier = "Example Post", tracking = tracking, stage = "ordered", stageAt = now, owner = fields["owner"]?.jsonPrimitive?.contentOrNull,
                    surprise = fields["surprise"]?.jsonPrimitive?.booleanOrNull ?: false, timeline = listOf(now to "ordered"),
                )
                list()
            }

            parts.size == 2 && parts[1] == "sync" && method == "POST" -> list()

            parts.size == 2 && parts[1] == "phone" && method == "PUT" -> list()

            parts.size == 2 && parts[1] == "settings" && method == "PUT" -> list()

            parts.size == 3 && parts[1] == "porch" && parts[2] == "now" && method == "GET" -> Reply.Image

            parts.size == 4 && parts[2] == "frames" && method == "GET" -> Reply.Image

            parts.size == 2 && method == "PATCH" -> withParcel(parts[1]) { p ->
                p.copy(
                    nickname = fields?.get("nickname")?.jsonPrimitive?.contentOrNull ?: p.nickname,
                    notes = fields?.get("notes")?.jsonPrimitive?.contentOrNull ?: p.notes,
                    owner = fields?.get("owner")?.jsonPrimitive?.contentOrNull ?: p.owner,
                    surprise = fields?.get("surprise")?.jsonPrimitive?.booleanOrNull ?: p.surprise,
                    pinned = fields?.get("pinned")?.jsonPrimitive?.booleanOrNull ?: p.pinned,
                    archived = fields?.get("archived")?.jsonPrimitive?.booleanOrNull ?: p.archived,
                    stage = fields?.get("stage")?.jsonPrimitive?.contentOrNull ?: p.stage,
                )
            }

            parts.size == 2 && method == "DELETE" -> withParcel(parts[1]) { null }

            else -> Reply.NotFound
        }
    }

    // ---- /charging ----------------------------------------------------------------------------

    private fun charging(parts: List<String>, method: String, request: RecordedRequest): Reply = when {
        parts.size == 1 && method == "GET" -> Reply.Json(state.chargingBody())

        parts.size == 2 && parts[1] == "sessions" && method == "GET" ->
            Reply.Json(state.chargesBefore(request.query["before"]?.firstOrNull()?.toDoubleOrNull() ?: Double.MAX_VALUE))

        parts.size == 2 && parts[1] == "plan" && method == "POST" -> Reply.Json(state.chargingBody())

        parts.size == 3 && parts[1] == "sessions" -> {
            val id = parts[2].toLongOrNull()
            if (method == "POST" && id != null) {
                val fields = json(request.body)
                val at = state.charges.indexOfFirst { it.id == id }
                if (at >= 0 && fields != null) {
                    val cost = fields["cost"]?.jsonPrimitive?.doubleOrNull
                    state.charges[at] = state.charges[at].copy(cost = cost, costIs = if (cost != null) "typed" else null, note = fields["note"]?.jsonPrimitive?.contentOrNull)
                }
            }
            if (method == "GET" || method == "POST") {
                id?.let { state.chargeDetail(it) }?.let { Reply.Json(it) } ?: error(HttpStatusCode.NotFound, "no such charge")
            } else {
                Reply.NotFound
            }
        }

        else -> Reply.NotFound
    }

    // ---- /timeline ----------------------------------------------------------------------------

    private fun timeline(parts: List<String>, method: String, user: FakeUser): Reply {
        val known = parts.size == 1 || parts.size == 2 && parts[1] in setOf("sync", "places") || parts.size == 3 && parts[1] == "year"
        if (!known) return Reply.NotFound
        if (user.role != "admin" && user.username !in state.timelineUsers) return error(HttpStatusCode.Forbidden, "Not your history")
        return when {
            parts.size == 1 && method == "GET" -> Reply.Json(state.timelineBody(user.username))
            parts.size == 2 && parts[1] == "sync" && method == "POST" -> Reply.Json(state.timelineBody(user.username))
            parts.size == 2 && parts[1] == "places" && method == "GET" -> Reply.Json(state.timelinePlaces())
            parts.size == 3 && method == "GET" -> parts[2].toIntOrNull()?.let { Reply.Json(state.timelineYear(it)) } ?: Reply.NotFound
            else -> Reply.NotFound
        }
    }

    companion object {
        /** The relay's port: the app talks to this one on whatever host it signed in to. */
        const val RELAY_PORT = 8787
    }
}
