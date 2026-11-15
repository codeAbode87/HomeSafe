package com.meticulouscreations.homesafe.ui.screens

import androidx.compose.runtime.saveable.SaverScope
import com.meticulouscreations.homesafe.navigation.SavedNavigation
import com.meticulouscreations.homesafe.navigation.TopLevelRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ShellNavigationSaverTest {
    private val epoch = "session-1"

    private val anyValue = SaverScope { true }

    private fun snapshot(
        tab: TopLevelRoute = TopLevelRoute.Home,
        home: List<Any> = listOf(CameraListRoute),
        settings: List<Any> = listOf(SettingsHomeRoute),
        drawerOpen: Boolean = false,
        app: InnerApp? = null,
        sidebarHidden: Boolean = false,
    ) = ShellNavigationSnapshot(tab, home, settings, drawerOpen, app, sidebarHidden)

    private fun roundTrip(snapshot: ShellNavigationSnapshot): ShellNavigationSnapshot? =
        ShellNavigationCodec.decode(ShellNavigationCodec.encode(snapshot))

    @Test
    fun everyHomePageComesBackAsItWas() {
        val pages = listOf(
            CameraListRoute,
            CameraDetailRoute("Front Door", warmStreamUrl = null, warmPosterUrl = null),
            CameraDetailRoute("Back Yard", null, null, openAtEpochSeconds = 1_700_000_000.5, openedEventId = "1700000000.5-abc", tagCarOnOpen = true),
            NestCameraRoute("nest_porch", null, null, null),
            DetectionZonesRoute("Driveway"),
            CarTaggingRoute("Driveway"),
            ClipEditorRoute("Driveway", anchorEpochSeconds = 1_700_000_123.25, originX = 0.25f, originY = 0.75f),
            DoorsHomeRoute,
            CarsHomeRoute,
        )
        for (page in pages) {
            val restored = roundTrip(snapshot(home = listOf(CameraListRoute, page)))
            assertEquals(listOf(CameraListRoute, page), restored?.homePages, "$page")
        }
        assertEquals(pages, roundTrip(snapshot(home = pages))?.homePages)
    }

    @Test
    fun everySettingsPageComesBackAsItWas() {
        val pages = listOf(
            SettingsHomeRoute,
            NotificationsRoute,
            AwayRoute,
            ClassifierRoute("known_cars"),
            FacesRoute,
            ServerRoute,
            UptimeRoute,
            DoorsRoute,
        )
        assertEquals(pages, roundTrip(snapshot(settings = pages))?.settingsPages)
    }

    @Test
    fun theTabTheDrawerAndTheOpenAppComeBack() {
        for (tab in listOf(TopLevelRoute.Home, TopLevelRoute.Moments, TopLevelRoute.Settings)) {
            assertEquals(tab, roundTrip(snapshot(tab = tab))?.tab)
        }
        for (app in InnerApp.entries) {
            assertEquals(app, roundTrip(snapshot(app = app))?.app)
        }
        val restored = roundTrip(snapshot(drawerOpen = true, sidebarHidden = true))
        assertEquals(true, restored?.drawerOpen)
        assertEquals(true, restored?.sidebarHidden)
        assertNull(restored?.app)
    }

    @Test
    fun aCamerasPageIsRestoredWithoutWhatItsCardWasPlaying() {
        val warm = CameraDetailRoute("Front Door", warmStreamUrl = "http://go2rtc/stream", warmPosterUrl = "http://frigate/latest.jpg")
        val restored = roundTrip(snapshot(home = listOf(CameraListRoute, warm)))?.homePages?.last()
        assertEquals(CameraDetailRoute("Front Door", warmStreamUrl = null, warmPosterUrl = null), restored)
        val nest = NestCameraRoute("porch", "http://s", "http://w", "http://p")
        assertEquals(NestCameraRoute("porch", null, null, null), roundTrip(snapshot(home = listOf(CameraListRoute, nest)))?.homePages?.last())
    }

    @Test
    fun aPageThatCannotBeRebuiltDropsBackToTheOneBelowIt() {
        val framing = CameraFramingRoute("Front Door", "http://s", null, "http://p", posterIsLastSeen = false, videoFit = null)
        val detail = CameraDetailRoute("Front Door", null, null)
        val restored = roundTrip(snapshot(home = listOf(CameraListRoute, detail, framing, DoorsHomeRoute)))
        assertEquals(listOf(CameraListRoute, detail), restored?.homePages)
    }

    @Test
    fun aPageNobodyKnowsEndsTheStackThereWhenReading() {
        val parts = ShellNavigationCodec.encode(snapshot()).toMutableList()
        parts[4] = listOf(listOf("list"), listOf("detail-from-the-future", "x"), listOf("doors"))
        assertEquals(listOf(CameraListRoute), ShellNavigationCodec.decode(parts)?.homePages)
    }

    @Test
    fun malformedPartsAreNoSnapshot() {
        val good = ShellNavigationCodec.encode(snapshot())
        assertNotNull(ShellNavigationCodec.decode(good))
        assertNull(ShellNavigationCodec.decode(emptyList()))
        assertNull(ShellNavigationCodec.decode(good.dropLast(1)))
        assertNull(ShellNavigationCodec.decode(good + "extra"))
        assertNull(ShellNavigationCodec.decode(good.toMutableList().also { it[0] = "tablet" }))
        assertNull(ShellNavigationCodec.decode(good.toMutableList().also { it[0] = 3 }))
        assertNull(ShellNavigationCodec.decode(good.toMutableList().also { it[1] = "yes" }))
        assertNull(ShellNavigationCodec.decode(good.toMutableList().also { it[2] = "TELEVISION" }))
        assertNull(ShellNavigationCodec.decode(good.toMutableList().also { it[3] = "" }))
        assertNull(ShellNavigationCodec.decode(good.toMutableList().also { it[4] = "list" }))
        assertNull(ShellNavigationCodec.decode(good.toMutableList().also { it[4] = listOf(1, 2) }))
        assertNull(ShellNavigationCodec.decode(good.toMutableList().also { it[4] = listOf(listOf(1)) }))
        assertNull(ShellNavigationCodec.decode(good.toMutableList().also { it[5] = emptyList<Any>() }))
    }

    @Test
    fun aStackThatDoesNotStartAtItsRootIsNoSnapshot() {
        val parts = ShellNavigationCodec.encode(snapshot()).toMutableList()
        parts[4] = listOf(listOf("doors"))
        assertNull(ShellNavigationCodec.decode(parts))
        parts[4] = listOf(listOf("list"))
        parts[5] = listOf(listOf("server"))
        assertNull(ShellNavigationCodec.decode(parts))
    }

    @Test
    fun badValuesInAPageAreNotARoute() {
        assertNull(ShellNavigationCodec.decodeHomeRoute(listOf("detail", "Cam", "not-a-time", "", "0")))
        assertNull(ShellNavigationCodec.decodeHomeRoute(listOf("detail", "Cam", "", "", "maybe")))
        assertNull(ShellNavigationCodec.decodeHomeRoute(listOf("detail", "Cam")))
        assertNull(ShellNavigationCodec.decodeHomeRoute(listOf("clip", "Cam", "1.0", "x", "0.5")))
        assertNull(ShellNavigationCodec.decodeHomeRoute(listOf("list", "extra")))
        assertNull(ShellNavigationCodec.decodeHomeRoute(emptyList()))
        assertNull(ShellNavigationCodec.decodeSettingsRoute(listOf("classifier")))
        assertNull(ShellNavigationCodec.decodeSettingsRoute(listOf("elsewhere")))
        assertNull(ShellNavigationCodec.decodeSettingsRoute(emptyList()))
    }

    @Test
    fun theNavigationSurvivesTheSaver() {
        val nav = ShellNavigation()
        nav.selectTab(TopLevelRoute.Settings)
        nav.settingsBackStack.add(ClassifierRoute("known_cars"))
        nav.homeBackStack.add(CameraDetailRoute("Front Door", "http://warm", null))
        nav.drawerOpen = true
        val saver = shellNavigationSaver(epoch)
        val saved = with(saver) { anyValue.save(nav) }
        assertNotNull(saved)

        val restored = assertNotNull(saver.restore(saved))
        assertEquals(TopLevelRoute.Settings, restored.selectedTab)
        assertEquals(listOf(SettingsHomeRoute, ClassifierRoute("known_cars")), restored.settingsBackStack.toList())
        assertEquals(listOf(CameraListRoute, CameraDetailRoute("Front Door", null, null)), restored.homeBackStack.toList())
        assertEquals(true, restored.drawerOpen)
    }

    @Test
    fun anOpenAppIsStillOpenOnTheTabItWasOver() {
        val nav = ShellNavigation()
        nav.openApp(InnerApp.FITNESS, androidx.compose.ui.geometry.Offset(10f, 10f), TopLevelRoute.Home)
        val saver = shellNavigationSaver(epoch)
        val restored = assertNotNull(saver.restore(with(saver) { anyValue.save(nav) }!!))
        assertEquals(true, restored.appOpen)
        assertEquals(InnerApp.FITNESS, restored.app)
        assertEquals(TopLevelRoute.Home, restored.appHost)
        assertEquals(false, restored.drawerOpen)
    }

    @Test
    fun aSaveFromAnotherProcessStartsAtHome() {
        val nav = ShellNavigation()
        nav.selectTab(TopLevelRoute.Settings)
        val saved = with(shellNavigationSaver("dead-process")) { anyValue.save(nav) }!!
        assertNull(shellNavigationSaver(epoch).restore(saved))
        assertNull(shellNavigationSaver(epoch).restore(listOf("garbage")))
        assertNull(shellNavigationSaver(epoch).restore(SavedNavigation.pack(epoch, listOf("not", "a", "shell"))))
    }
}
