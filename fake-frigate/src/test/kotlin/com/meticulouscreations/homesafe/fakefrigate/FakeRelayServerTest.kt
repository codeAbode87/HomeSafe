package com.meticulouscreations.homesafe.fakefrigate

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The fake relay over plain HTTP: it starts only when asked, wants the Frigate session cookie, and answers what the app's DTOs parse. */
class FakeRelayServerTest {

    private val plain = FakeFrigateServer().start()
    private val withRelay = FakeFrigateServer().start(relayPort = 0)

    @AfterTest
    fun stop() {
        plain.close()
        withRelay.close()
    }

    private class Response(val status: Int, val body: String, val cookie: String? = null) {
        val json: JsonObject get() = Json.parseToJsonElement(body).jsonObject
    }

    private fun call(base: String, method: String, path: String, body: String? = null, cookie: String? = null): Response {
        val request = HttpRequest.newBuilder(URI("$base$path"))
            .method(method, if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json")
        cookie?.let { request.header("Cookie", it) }
        val response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString())
        return Response(response.statusCode(), response.body(), response.headers().firstValue("Set-Cookie").orElse(null)?.substringBefore(';'))
    }

    private fun signIn(user: FakeUser = FakeFrigateState.ADMIN): String =
        call(withRelay.baseUrl, "POST", "/api/login", """{"user":"${user.username}","password":"${user.password}"}""").cookie!!

    private fun relay(method: String, path: String, cookie: String? = signIn(), body: String? = null) =
        call("http://127.0.0.1:${withRelay.relay!!.port}", method, path, body, cookie)

    @Test
    fun `the relay is off unless asked for`() {
        assertNull(plain.relay)
        assertNotNull(withRelay.relay)
    }

    @Test
    fun `no session or an unknown one is refused`() {
        assertEquals(401, relay("GET", "/packages", cookie = null).status)
        assertEquals(401, relay("GET", "/charging", cookie = "frigate_token=nope").status)
    }

    @Test
    fun `the session of either account is accepted`() {
        assertEquals(200, relay("GET", "/packages", signIn(FakeFrigateState.VIEWER)).status)
    }

    @Test
    fun `packages list the household's parcels with the fields the app reads`() {
        val body = relay("GET", "/packages?device=d1").json
        assertEquals("admin", body["viewer"]!!.jsonPrimitive.content)
        assertTrue(body["mail_on"]!!.jsonPrimitive.boolean)
        val packages = body["packages"]!!.jsonArray
        assertEquals(listOf("out_for_delivery", "shipped", "delivered"), packages.map { it.jsonObject["stage"]!!.jsonPrimitive.content })
        val first = packages.first().jsonObject
        listOf("id", "nickname", "carrier", "tracking", "stage_at", "timeline").forEach { assertNotNull(first[it], it) }
    }

    @Test
    fun `packages can be added, changed and forgotten`() {
        val cookie = signIn()
        val added = relay("POST", "/packages?device=d1", cookie, """{"tracking":"EX42","nickname":"Rug"}""").json["packages"]!!.jsonArray
        assertEquals(4, added.size)
        val id = added.last().jsonObject["id"]!!.jsonPrimitive.content
        val pinned = relay("PATCH", "/packages/$id", cookie, """{"pinned":true,"nickname":"Big rug"}""").json["packages"]!!.jsonArray.last().jsonObject
        assertTrue(pinned["pinned"]!!.jsonPrimitive.boolean)
        assertEquals("Big rug", pinned["nickname"]!!.jsonPrimitive.content)
        assertEquals(3, relay("DELETE", "/packages/$id", cookie).json["packages"]!!.jsonArray.size)
        assertEquals(404, relay("DELETE", "/packages/$id", cookie).status)
        assertEquals(400, relay("POST", "/packages", cookie, """{}""").status)
    }

    @Test
    fun `charging has the summary, a page of older charges and a charge's detail`() {
        val cookie = signIn()
        val summary = relay("GET", "/charging", cookie).json
        assertEquals("flat_demo", summary["plan"]!!.jsonPrimitive.content)
        assertEquals(2, summary["now"]!!.jsonArray.size)
        assertEquals(2, summary["months"]!!.jsonArray.size)
        assertEquals(6, summary["sessions"]!!.jsonArray.size)
        val id = summary["sessions"]!!.jsonArray.first().jsonObject["id"]!!.jsonPrimitive.content
        val detail = relay("GET", "/charging/sessions/$id", cookie).json
        assertTrue(detail["hours"]!!.jsonArray.isNotEmpty())
        assertTrue(detail["plans"]!!.jsonObject.isNotEmpty())
        assertEquals(0, relay("GET", "/charging/sessions?before=1", cookie).json["sessions"]!!.jsonArray.size)
        val saved = relay("POST", "/charging/sessions/$id", cookie, """{"cost":9.5,"note":"Typed"}""").json
        assertEquals("typed", saved["cost_is"]!!.jsonPrimitive.content)
        assertEquals(404, relay("GET", "/charging/sessions/999999", cookie).status)
    }

    @Test
    fun `the timeline answers an admin and the accounts named, with years and columns`() {
        val timeline = relay("GET", "/timeline").json
        val year = timeline["years"]!!.jsonArray.first().jsonObject["year"]!!.jsonPrimitive.int
        val body = relay("GET", "/timeline/year/$year").json
        val visits = body["visits"]!!.jsonObject
        assertEquals(9, visits["started"]!!.jsonArray.size)
        assertEquals(visits["started"]!!.jsonArray.size, visits["place"]!!.jsonArray.size)
        assertEquals(3, body["places"]!!.jsonArray.size)
        assertEquals(3, relay("GET", "/timeline/places").json["places"]!!.jsonArray.size)
        withRelay.relayState.edit { timelineUsers.clear() }
        assertEquals(403, relay("GET", "/timeline", signIn(FakeFrigateState.VIEWER)).status)
    }

    @Test
    fun `anything else is a 404 and listed`() {
        assertEquals(404, relay("GET", "/devices").status)
        assertEquals(listOf("GET /devices"), withRelay.relay!!.unhandled.map { "${it.method} ${it.path}" })
    }
}
