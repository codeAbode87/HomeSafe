package com.meticulouscreations.homesafe.fakefrigate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MainTest {
    private val pinned = 1_767_603_600.0 // 2026-01-05T09:00:00Z

    @Test
    fun nowTakesAnInstantOrEpochSeconds() {
        assertEquals(pinned, epochSecondsOf("2026-01-05T09:00:00Z"))
        assertEquals(pinned, epochSecondsOf("1767603600"))
    }

    @Test
    fun nowRefusesAnythingElse() {
        for (bad in listOf("yesterday", "", "NaN", "Infinity")) {
            assertFailsWith<IllegalArgumentException>(bad) { epochSecondsOf(bad) }
        }
    }

    @Test
    fun aPinnedServerPlacesItsHistoryAtTheInstantAndHoldsTheRelaysClockThere() {
        val server = fakeServer(pinnedNow = pinned)
        assertEquals(pinned, server.relayState.nowSeconds())
        Thread.sleep(20)
        assertEquals(pinned, server.relayState.nowSeconds())
        assertEquals("2026-01-05", server.relayState.packagesBody("admin")["today"].toString().trim('"'))
        assertEquals(pinned - 180, server.state.events.maxOf { it.startTime })
        assertEquals(fakeServer(pinnedNow = pinned).relayState.packagesBody("admin"), server.relayState.packagesBody("admin"))
    }

    @Test
    fun anUnpinnedServerFollowsTheRealClock() {
        val before = System.currentTimeMillis() / 1000.0
        val server = fakeServer()
        assertTrue(server.relayState.nowSeconds() >= before)
        assertTrue(fakeServer(quiet = true).state.events.isEmpty())
    }
}
