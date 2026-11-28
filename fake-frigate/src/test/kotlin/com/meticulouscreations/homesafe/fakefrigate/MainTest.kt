package com.meticulouscreations.homesafe.fakefrigate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MainTest {
    @Test
    fun nowStartsTheClockAtTheInstantAndItRunsOn() {
        var nanos = 5_000_000_000L
        val clock = clockStartingAt(epochSecondsOf("2026-01-05T09:00:00Z")) { nanos }
        assertEquals(1_767_603_600.0, clock())
        nanos += 90_000_000_000L
        assertEquals(1_767_603_690.0, clock())
    }

    @Test
    fun nowTakesEpochSeconds() {
        assertEquals(1_767_603_600.0, epochSecondsOf("1767603600"))
    }

    @Test
    fun nowRefusesAnythingElse() {
        assertFailsWith<IllegalArgumentException> { epochSecondsOf("yesterday") }
    }

    @Test
    fun aPinnedHouseholdIsTheSameOnEveryRun() {
        val now = 1_767_603_600.0
        val a = FakeRelayState.household(now) { now }
        val b = FakeRelayState.household(now) { now }
        assertEquals(a.packagesBody("admin"), b.packagesBody("admin"))
        assertEquals("2026-01-05", a.packagesBody("admin")["today"].toString().trim('"'))
        assertEquals(
            FakeFrigateState.household(now).events.map { it.id to it.startTime },
            FakeFrigateState.household(now).events.map { it.id to it.startTime },
        )
    }
}
