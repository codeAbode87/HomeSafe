package com.meticulouscreations.homesafe.appicon

import com.meticulouscreations.homesafe.PlatformContext
import com.meticulouscreations.homesafe.ui.screens.InnerApp

private object UnsupportedAppShortcutPinner : AppShortcutPinner {
    override val supported: Boolean = false
    override suspend fun pin(app: InnerApp): Boolean = false
}

actual fun createAppShortcutPinner(context: PlatformContext): AppShortcutPinner = UnsupportedAppShortcutPinner
