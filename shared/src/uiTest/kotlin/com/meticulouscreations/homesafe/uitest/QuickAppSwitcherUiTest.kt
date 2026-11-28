package com.meticulouscreations.homesafe.uitest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.meticulouscreations.homesafe.ui.preview.FrigatePreview
import com.meticulouscreations.homesafe.ui.screens.DrawerQuickStrip
import com.meticulouscreations.homesafe.ui.screens.InnerApp
import com.meticulouscreations.homesafe.ui.screens.QUICK_APP_SWITCHER_TEST_TAG
import com.meticulouscreations.homesafe.ui.screens.QuickAppSwitcher
import com.meticulouscreations.homesafe.ui.screens.drawerQuickAppTestTag
import com.meticulouscreations.homesafe.ui.screens.quickAppTileTestTag
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class QuickAppSwitcherUiTest {

    @Test
    fun tappingAnAppTileOpensThatApp() = runComposeUiTest {
        val opened = mutableListOf<InnerApp>()
        setContent {
            FrigatePreview {
                QuickAppSwitcher(
                    open = true,
                    onOpenApp = { app, _ -> opened += app },
                    onOpenDrawer = {},
                    onClose = {},
                )
            }
        }

        onNodeWithTag(QUICK_APP_SWITCHER_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(quickAppTileTestTag(InnerApp.WEATHER)).performClick()
        onNodeWithTag(quickAppTileTestTag(InnerApp.FITNESS)).performClick()

        assertEquals(listOf(InnerApp.WEATHER, InnerApp.FITNESS), opened)
    }

    @Test
    fun allCardsOpensTheDrawerAndScrimClosesTheSwitcher() = runComposeUiTest {
        var open by mutableStateOf(true)
        var drawerOpened = 0
        setContent {
            FrigatePreview {
                QuickAppSwitcher(
                    open = open,
                    onOpenApp = { _, _ -> },
                    onOpenDrawer = {
                        open = false
                        drawerOpened++
                    },
                    onClose = { open = false },
                )
            }
        }

        onNodeWithTag("quick_switcher_drawer").performClick()
        waitForIdle()
        assertEquals(1, drawerOpened)
        onNodeWithTag(QUICK_APP_SWITCHER_TEST_TAG).assertDoesNotExist()

        open = true
        waitForIdle()
        onNodeWithTag(QUICK_APP_SWITCHER_TEST_TAG).assertIsDisplayed()
        onNodeWithTag("quick_app_switcher_scrim").performClick()
        waitForIdle()
        onNodeWithTag(QUICK_APP_SWITCHER_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun theQuickStripOpensWeatherAndFitnessDirectly() = runComposeUiTest {
        val opened = mutableListOf<InnerApp>()
        setContent {
            FrigatePreview {
                DrawerQuickStrip(onOpen = { app, _ -> opened += app }, animated = false)
            }
        }

        onNodeWithTag(drawerQuickAppTestTag(InnerApp.WEATHER)).performClick()
        onNodeWithTag(drawerQuickAppTestTag(InnerApp.FITNESS)).performClick()
        assertEquals(listOf(InnerApp.WEATHER, InnerApp.FITNESS), opened)
    }
}
