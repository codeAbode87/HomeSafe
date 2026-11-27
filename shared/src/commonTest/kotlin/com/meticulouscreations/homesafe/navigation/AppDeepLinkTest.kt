package com.meticulouscreations.homesafe.navigation

import com.meticulouscreations.homesafe.finance.ui.FinanceTab
import com.meticulouscreations.homesafe.ui.screens.InnerApp
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppDeepLinkTest {

    @AfterTest
    fun clearPendingLinks() {
        WeatherDeepLinks.pending.value?.let(WeatherDeepLinks::consume)
        FitnessDeepLinks.pending.value?.let(FitnessDeepLinks::consume)
        FinanceDeepLinks.pending.value?.let(FinanceDeepLinks::consume)
        PackageDeepLinks.pending.value?.let(PackageDeepLinks::consume)
        AppDeepLinks.pending.value?.let(AppDeepLinks::consume)
        FitnessShares.consume()
    }

    @Test
    fun parsesAtlasAndChargingUrisAndIgnoresOtherLinks() {
        assertEquals(AppDeepLink(InnerApp.ATLAS), AppDeepLink.fromUri("homesafe://atlas"))
        assertEquals(AppDeepLink(InnerApp.CHARGING), AppDeepLink.fromUri("homesafe://charging"))
        assertNull(AppDeepLink.fromUri("homesafe://weather"))
        assertNull(AppDeepLink.fromUri("https://atlas"))
        assertNull(AppDeepLink.fromUri(null))
    }

    @Test
    fun uriForEachAppRoundTripsThroughItsDeepLinkParser() {
        assertEquals(WeatherDeepLink(), WeatherDeepLink.fromUri(AppDeepLink.uriFor(InnerApp.WEATHER)))
        assertEquals(FitnessDeepLink.Workout, FitnessDeepLink.fromUri(AppDeepLink.uriFor(InnerApp.FITNESS)))
        assertEquals(FinanceDeepLink(FinanceTab.WALLET), FinanceDeepLink.fromUri(AppDeepLink.uriFor(InnerApp.FINANCE)))
        assertEquals(PackageDeepLink(), PackageDeepLink.fromUri(AppDeepLink.uriFor(InnerApp.PACKAGES)))
        assertEquals(AppDeepLink(InnerApp.ATLAS), AppDeepLink.fromUri(AppDeepLink.uriFor(InnerApp.ATLAS)))
        assertEquals(AppDeepLink(InnerApp.CHARGING), AppDeepLink.fromUri(AppDeepLink.uriFor(InnerApp.CHARGING)))
    }

    @Test
    fun pendingServerFreeCheckDetectsEveryAppLink() {
        assertFalse(hasPendingServerFreeAppLink())

        val fitness = FitnessDeepLink.Workout
        FitnessDeepLinks.open(fitness)
        assertTrue(hasPendingServerFreeAppLink())
        FitnessDeepLinks.consume(fitness)
        assertFalse(hasPendingServerFreeAppLink())

        val atlas = AppDeepLink(InnerApp.ATLAS)
        AppDeepLinks.open(atlas)
        assertTrue(hasPendingServerFreeAppLink())
        AppDeepLinks.consume(atlas)
        assertFalse(hasPendingServerFreeAppLink())
    }
}
