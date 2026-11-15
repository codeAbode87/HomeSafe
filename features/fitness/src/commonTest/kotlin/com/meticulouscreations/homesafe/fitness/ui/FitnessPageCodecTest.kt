package com.meticulouscreations.homesafe.fitness.ui

import com.meticulouscreations.homesafe.navigation.SavedPages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FitnessPageCodecTest {
    private val every: List<FitnessPage> = listOf(
        FitnessPage.Workout,
        FitnessPage.Lift("squat"),
        FitnessPage.Edit("squat"),
        FitnessPage.Import,
        FitnessPage.Heart,
        FitnessPage.Session(42L, 20000L),
        FitnessPage.Session(null, 20000L),
        FitnessPage.Runs,
        FitnessPage.Run("r-1"),
        FitnessPage.Recording,
    )

    private fun save(pages: List<FitnessPage>, epoch: String = "e1") = SavedPages.save(epoch, pages, FitnessPageCodec::encode)

    private fun restore(saved: Any?, epoch: String = "e1") = SavedPages.restore(saved, epoch, FitnessPageCodec::decode)

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
        assertNull(restore(good + listOf(listOf("session", "x", "1"))))
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
