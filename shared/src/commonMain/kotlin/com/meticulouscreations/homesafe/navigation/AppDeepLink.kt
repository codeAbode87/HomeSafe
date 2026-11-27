package com.meticulouscreations.homesafe.navigation

import com.meticulouscreations.homesafe.finance.ui.FinanceTab
import com.meticulouscreations.homesafe.ui.screens.InnerApp
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A deep link to an app from the drawer that has no more specific link of its own yet:
 * `homesafe://atlas` and `homesafe://charging`, beside [WeatherDeepLink], [FitnessDeepLink],
 * [FinanceDeepLink], and [PackageDeepLink]. Pinned home-screen shortcuts and launcher actions
 * use these so every app inside PercySafe can be opened directly from outside the app.
 */
data class AppDeepLink(val app: InnerApp) {
    fun toUri(): String = uriFor(app)

    companion object {
        const val SCHEME = MomentDeepLink.SCHEME
        const val HOST_ATLAS = "atlas"
        const val HOST_CHARGING = "charging"

        /** The canonical `homesafe://` URI that opens [app] from outside the app. */
        fun uriFor(app: InnerApp): String = when (app) {
            InnerApp.WEATHER -> WeatherDeepLink().toUri()

            // Opens the workout in progress if one is running, or Fitness's Today tab otherwise.
            InnerApp.FITNESS -> FitnessDeepLink.Workout.toUri()

            InnerApp.FINANCE -> FinanceDeepLink(FinanceTab.WALLET).toUri()

            InnerApp.PACKAGES -> PackageDeepLink().toUri()

            InnerApp.ATLAS -> "$SCHEME://$HOST_ATLAS"

            InnerApp.CHARGING -> "$SCHEME://$HOST_CHARGING"
        }

        /** From `homesafe://atlas` or `homesafe://charging`; null for anything else. */
        fun fromUri(uri: String?): AppDeepLink? {
            val url = uri?.let { runCatching { Url(it) }.getOrNull() } ?: return null
            if (url.protocol.name != SCHEME) return null
            return when (url.host) {
                HOST_ATLAS -> AppDeepLink(InnerApp.ATLAS)
                HOST_CHARGING -> AppDeepLink(InnerApp.CHARGING)
                else -> null
            }
        }
    }
}

/**
 * The hand-off between a platform that heard a shortcut or link for Atlas or Charging and the
 * Compose shell that opens it: a state rather than an event, so a tap that cold-starts the app
 * waits until a screen is up to act on it.
 */
object AppDeepLinks {
    private val _pending = MutableStateFlow<AppDeepLink?>(null)
    val pending: StateFlow<AppDeepLink?> = _pending.asStateFlow()

    fun open(link: AppDeepLink) {
        _pending.value = link
    }

    /** Clears [link] once it has been acted on; a newer one that arrived meanwhile stays for the next look. */
    fun consume(link: AppDeepLink) {
        _pending.compareAndSet(link, null)
    }
}

/**
 * Whether any link or share for one of the server-free apps (Weather, Fitness, Finance, Packages,
 * Atlas, Charging) is waiting to be opened. The sign-in screen checks this on launch so tapping
 * a home-screen shortcut or notification for one of those apps opens straight into it without
 * interrupting the user with the camera server's biometric sign-in prompt first.
 */
fun hasPendingServerFreeAppLink(): Boolean =
    WeatherDeepLinks.pending.value != null ||
        FitnessDeepLinks.pending.value != null ||
        FinanceDeepLinks.pending.value != null ||
        PackageDeepLinks.pending.value != null ||
        AppDeepLinks.pending.value != null ||
        FitnessShares.pending.value != null
