package com.meticulouscreations.homesafe.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SavedNavigationTest {
    @Test
    fun whatIsPackedUnderAnEpochComesBackUnderIt() {
        val parts = listOf<Any>("a", listOf("b", "c"))
        assertEquals(parts, SavedNavigation.unpack(SavedNavigation.pack("e1", parts), "e1"))
    }

    @Test
    fun aSaveFromAnotherSessionIsNotRestored() {
        assertNull(SavedNavigation.unpack(SavedNavigation.pack("old-process", listOf("a")), "this-process"))
    }

    @Test
    fun aSaveOfAnotherVersionIsNotRestored() {
        assertNull(SavedNavigation.unpack(listOf("0", "e1", "a"), "e1"))
    }

    @Test
    fun anythingThatIsNotASaveIsNotRestored() {
        assertNull(SavedNavigation.unpack(null, "e1"))
        assertNull(SavedNavigation.unpack("shell", "e1"))
        assertNull(SavedNavigation.unpack(42, "e1"))
        assertNull(SavedNavigation.unpack(emptyList<Any>(), "e1"))
        assertNull(SavedNavigation.unpack(listOf(SavedNavigation.VERSION), "e1"))
        assertNull(SavedNavigation.unpack(listOf(SavedNavigation.VERSION, "e1", null), "e1"))
    }
}
