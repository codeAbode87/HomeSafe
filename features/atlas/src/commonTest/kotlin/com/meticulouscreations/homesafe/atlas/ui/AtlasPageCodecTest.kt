package com.meticulouscreations.homesafe.atlas.ui

import com.meticulouscreations.homesafe.navigation.SavedPages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AtlasPageCodecTest {
    private val every: List<AtlasPage> = listOf(
        AtlasPage.Day(20000),
        AtlasPage.PlaceDetail("p|1"),
        AtlasPage.TripDetail(1700000000L),
        AtlasPage.Sync,
    )

    private fun save(pages: List<AtlasPage>, epoch: String = "e1") = SavedPages.save(epoch, pages, AtlasPageCodec::encode)

    private fun restore(saved: Any?, epoch: String = "e1") = SavedPages.restore(saved, epoch, AtlasPageCodec::decode)

    @Test
    fun everyPageComesBackAsItWas() {
        for (page in every) assertEquals(listOf(page), restore(save(listOf(page))), "$page")
    }

    @Test
    fun aStackComesBackInOrder() {
        assertEquals(every, restore(save(every)))
    }

    @Test
    fun anEmptiedStackIsASaveOfNoPages() {
        assertEquals(emptyList(), restore(save(emptyList())))
    }

    @Test
    fun aSaveFromAnotherSessionRestoresNothing() {
        assertNull(restore(save(every, epoch = "old-process"), epoch = "this-process"))
    }

    @Test
    fun aPageThatIsNotUnderstoodMakesNoSave() {
        val good = save(every.take(1))
        assertNull(restore(good + listOf(listOf("nonsense"))))
        assertNull(restore(good + listOf(listOf("day", "not-a-number"))))
        assertNull(restore(good + listOf("not a list")))
        assertNull(restore(good + listOf(listOf(1))))
        assertNull(restore(good + listOf(emptyList<String>())))
    }

    @Test
    fun anythingThatIsNotASaveRestoresNothing() {
        assertNull(restore(null))
        assertNull(restore("shell"))
        assertNull(restore(listOf("0", "e1")))
        assertNull(restore(emptyList<Any>()))
    }
}
