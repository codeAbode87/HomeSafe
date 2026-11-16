package com.meticulouscreations.homesafe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.meticulouscreations.homesafe.navigation.LocalNavigationEpoch
import com.meticulouscreations.homesafe.navigation.SavedNavigation
import com.meticulouscreations.homesafe.navigation.TopLevelRoute

/**
 * The shell's navigation, kept across the Activity's recreation (see [SavedNavigation]): the tab,
 * the pages pushed on Home's and Settings' stacks, the drawer and the app open from it, and the
 * sidebar being put away. Where it can't be restored (the sign-in it belonged to is gone, or what
 * was saved isn't understood), the shell starts at Home's list as a fresh one does.
 */
@Composable
internal fun rememberShellNavigation(): ShellNavigation {
    val epoch = LocalNavigationEpoch.current
    return rememberSaveable(epoch, saver = remember(epoch) { shellNavigationSaver(epoch) }) { ShellNavigation() }
}

internal fun shellNavigationSaver(epoch: String): Saver<ShellNavigation, Any> = Saver(
    save = { nav -> SavedNavigation.pack(epoch, ShellNavigationCodec.encode(nav.snapshot())) },
    restore = { saved ->
        SavedNavigation.unpack(saved, epoch)
            ?.let(ShellNavigationCodec::decode)
            ?.let { snapshot -> ShellNavigation().also { it.restore(snapshot) } }
    },
)

/** Where the shell was, as plain values: what [ShellNavigationCodec] turns into saveable strings and back. */
internal data class ShellNavigationSnapshot(
    val tab: TopLevelRoute,
    val homePages: List<Any>,
    val settingsPages: List<Any>,
    val drawerOpen: Boolean,
    /** The app from the drawer that is up, over the shell or beside its sidebar; null for none. */
    val app: InnerApp?,
    val sidebarHidden: Boolean,
)

internal object ShellNavigationCodec {
    fun encode(snapshot: ShellNavigationSnapshot): List<Any> = listOf(
        encodeTab(snapshot.tab),
        flag(snapshot.drawerOpen),
        snapshot.app?.name.orEmpty(),
        flag(snapshot.sidebarHidden),
        encodeStack(snapshot.homePages, ::encodeHomeRoute),
        encodeStack(snapshot.settingsPages, ::encodeSettingsRoute),
    )

    /** The snapshot [encode] made, or null where [parts] aren't one: the caller then starts afresh. */
    fun decode(parts: List<Any>): ShellNavigationSnapshot? {
        if (parts.size != 6) return null
        val tab = (parts[0] as? String)?.let(::decodeTab) ?: return null
        val drawerOpen = (parts[1] as? String)?.let(::decodeFlag) ?: return null
        val appName = parts[2] as? String ?: return null
        val app = if (appName.isEmpty()) null else InnerApp.entries.firstOrNull { it.name == appName } ?: return null
        val sidebarHidden = (parts[3] as? String)?.let(::decodeFlag) ?: return null
        val home = decodeStack(parts[4], root = CameraListRoute, ::decodeHomeRoute) ?: return null
        val settings = decodeStack(parts[5], root = SettingsHomeRoute, ::decodeSettingsRoute) ?: return null
        return ShellNavigationSnapshot(tab, home, settings, drawerOpen, app, sidebarHidden)
    }

    private fun flag(value: Boolean) = if (value) "1" else "0"

    private fun decodeFlag(text: String): Boolean? = when (text) {
        "1" -> true
        "0" -> false
        else -> null
    }

    private fun encodeTab(tab: TopLevelRoute): String = when (tab) {
        TopLevelRoute.Home -> "home"
        TopLevelRoute.Moments -> "moments"
        TopLevelRoute.Settings -> "settings"
    }

    private fun decodeTab(text: String): TopLevelRoute? = when (text) {
        "home" -> TopLevelRoute.Home
        "moments" -> TopLevelRoute.Moments
        "settings" -> TopLevelRoute.Settings
        else -> null
    }

    /**
     * The pages from the root up to the first one that can't be saved, which and whatever is
     * above it are left out: coming back one level short of a screen that can't be rebuilt.
     */
    private fun encodeStack(pages: List<Any>, encodeRoute: (Any) -> List<String>?): List<List<String>> {
        val encoded = ArrayList<List<String>>(pages.size)
        for (page in pages) encoded += encodeRoute(page) ?: break
        return encoded
    }

    /** The stack, which must start at [root]; a page that isn't understood ends it there. */
    private fun decodeStack(saved: Any, root: Any, decodeRoute: (List<String>) -> Any?): List<Any>? {
        val entries = saved as? List<*> ?: return null
        val pages = ArrayList<Any>(entries.size)
        for (entry in entries) {
            val parts = (entry as? List<*>)?.map { it as? String ?: return null } ?: return null
            pages += decodeRoute(parts) ?: break
        }
        return pages.takeIf { it.firstOrNull() == root }
    }

    /**
     * Home's pages. A camera's page is saved without what its card was playing (the warm stream
     * and poster, which are the pooled players of a session that is not this one's to resume): it
     * binds to its player the ordinary way, as a notification tap's does. The framing editor's
     * route carries what the card was showing, which can't be rebuilt, so it isn't saved.
     */
    fun encodeHomeRoute(route: Any): List<String>? = when (route) {
        CameraListRoute -> listOf("list")

        is CameraDetailRoute -> listOf(
            "detail",
            route.cameraName,
            route.openAtEpochSeconds?.toString().orEmpty(),
            route.openedEventId.orEmpty(),
            flag(route.tagCarOnOpen),
        )

        is NestCameraRoute -> listOf("nest", route.streamName)

        is DetectionZonesRoute -> listOf("zones", route.cameraName)

        is CarTaggingRoute -> listOf("tagging", route.cameraName)

        is ClipEditorRoute -> listOf("clip", route.cameraName, route.anchorEpochSeconds.toString(), route.originX.toString(), route.originY.toString())

        DoorsHomeRoute -> listOf("doors")

        CarsHomeRoute -> listOf("cars")

        else -> null
    }

    fun decodeHomeRoute(parts: List<String>): Any? = when (parts.firstOrNull()) {
        "list" -> CameraListRoute.takeIf { parts.size == 1 }

        "detail" -> if (parts.size != 5) {
            null
        } else {
            val openAt = if (parts[2].isEmpty()) null else parts[2].toDoubleOrNull() ?: return null
            val tagCar = decodeFlag(parts[4]) ?: return null
            CameraDetailRoute(
                cameraName = parts[1],
                warmStreamUrl = null,
                warmPosterUrl = null,
                openAtEpochSeconds = openAt,
                openedEventId = parts[3].ifEmpty { null },
                tagCarOnOpen = tagCar,
            )
        }

        "nest" -> if (parts.size == 2) NestCameraRoute(parts[1], warmStreamUrl = null, warmWebRtcSignalingUrl = null, warmStillUrl = null) else null

        "zones" -> if (parts.size == 2) DetectionZonesRoute(parts[1]) else null

        "tagging" -> if (parts.size == 2) CarTaggingRoute(parts[1]) else null

        "clip" -> if (parts.size == 5) {
            val anchor = parts[2].toDoubleOrNull()
            val x = parts[3].toFloatOrNull()
            val y = parts[4].toFloatOrNull()
            if (anchor == null || x == null || y == null) null else ClipEditorRoute(parts[1], anchor, x, y)
        } else {
            null
        }

        "doors" -> DoorsHomeRoute.takeIf { parts.size == 1 }

        "cars" -> CarsHomeRoute.takeIf { parts.size == 1 }

        else -> null
    }

    fun encodeSettingsRoute(route: Any): List<String>? = when (route) {
        SettingsHomeRoute -> listOf("home")
        NotificationsRoute -> listOf("notifications")
        AwayRoute -> listOf("away")
        is ClassifierRoute -> listOf("classifier", route.modelName)
        FacesRoute -> listOf("faces")
        ServerRoute -> listOf("server")
        UptimeRoute -> listOf("uptime")
        DoorsRoute -> listOf("doors")
        else -> null
    }

    fun decodeSettingsRoute(parts: List<String>): Any? = when (parts.firstOrNull()) {
        "home" -> SettingsHomeRoute.takeIf { parts.size == 1 }
        "notifications" -> NotificationsRoute.takeIf { parts.size == 1 }
        "away" -> AwayRoute.takeIf { parts.size == 1 }
        "classifier" -> if (parts.size == 2) ClassifierRoute(parts[1]) else null
        "faces" -> FacesRoute.takeIf { parts.size == 1 }
        "server" -> ServerRoute.takeIf { parts.size == 1 }
        "uptime" -> UptimeRoute.takeIf { parts.size == 1 }
        "doors" -> DoorsRoute.takeIf { parts.size == 1 }
        else -> null
    }
}
