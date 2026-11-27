package com.meticulouscreations.homesafe.appicon

import com.meticulouscreations.homesafe.PlatformContext
import com.meticulouscreations.homesafe.ui.screens.InnerApp

/**
 * Pins one of the apps inside PercySafe (Weather, Fitness, Finance, Atlas, Packages, Charging)
 * directly to the device's home screen as a one-tap shortcut icon. Supported on Android launchers
 * that accept pinned shortcuts; false elsewhere.
 */
interface AppShortcutPinner {
    /** Whether this device and launcher allow pinning shortcuts to the home screen. */
    val supported: Boolean

    /** Asks the launcher to pin [app] to the home screen. True when the request was accepted. */
    suspend fun pin(app: InnerApp): Boolean
}

expect fun createAppShortcutPinner(context: PlatformContext): AppShortcutPinner
