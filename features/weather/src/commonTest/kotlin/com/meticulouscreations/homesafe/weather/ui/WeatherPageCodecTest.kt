package com.meticulouscreations.homesafe.weather.ui

import com.meticulouscreations.homesafe.navigation.SavedPages
import com.meticulouscreations.homesafe.weather.ski.domain.SkiResortRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeatherPageCodecTest {
    private val every: List<WeatherPage> = listOf(
        WeatherPage.Places,
        WeatherPage.Settings,
        WeatherPage.Alert("urn:x:1"),
        WeatherPage.SkiResort(SkiResortRef("vail", "colorado", "Vail Resort")),
        WeatherPage.SkiFind,
        WeatherPage.SkiRegion("tahoe"),
        WeatherPage.SkiSetup,
    )

    private fun save(pages: List<WeatherPage>, epoch: String = "e1") = SavedPages.save(epoch, pages, WeatherPageCodec::encode)

    private fun restore(saved: Any?, epoch: String = "e1") = SavedPages.restore(saved, epoch, WeatherPageCodec::decode)

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
        assertNull(restore(good + listOf(listOf("skiResort", "only-one"))))
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
